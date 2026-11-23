package app.exteraless.updater;

import org.json.JSONArray;
import org.telegram.messenger.FileLog;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongArray;

import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Downloads the update APK over several connections at once (HTTP Range) and keeps what it
 * already has, so a dropped connection or a restart continues instead of starting over. When
 * the server does not support ranges it falls back to one plain stream. The result is written
 * to a `.part` file next to the target and renamed once every byte is in place.
 */
final class UpdateDownloader {

    /**
     * The file is cut into many small segments and a few workers take the next free one, so one slow
     * connection delays only its own small piece instead of a quarter of the file.
     */
    static final int SEGMENTS = 16;
    static final int WORKERS = 6;
    private static final long MIN_PARALLEL_BYTES = 4L * 1024 * 1024;
    private static final int SEGMENT_ATTEMPTS = 4;
    private static final long STATE_SAVE_INTERVAL_MS = 1500;

    interface Progress {
        void onProgress(long done, long total);
    }

    /** Handle for cancelling a running download from any thread. */
    static final class Job {
        private final List<Call> calls = new CopyOnWriteArrayList<>();
        private volatile boolean canceled;

        void cancel() {
            canceled = true;
            for (Call call : calls) {
                call.cancel();
            }
        }

        /** Stops the in-flight requests without marking the job as cancelled by the user. */
        void abortCalls() {
            for (Call call : calls) {
                call.cancel();
            }
        }

        boolean isCanceled() {
            return canceled;
        }

        Call track(Call call) {
            calls.add(call);
            if (canceled) {
                call.cancel();
            }
            return call;
        }

        void untrack(Call call) {
            calls.remove(call);
        }
    }

    private UpdateDownloader() {
    }

    /**
     * Downloads {@code url} into {@code target}. The first client is tried first (it may route
     * through the app proxy); if that fails before the download is finished the same transfer
     * is retried once with the direct client, continuing from the saved state.
     */
    static void download(OkHttpClient preferred, OkHttpClient direct, String url, long hintSize,
                         File target, Job job, Progress progress) throws IOException {
        try {
            run(preferred, url, hintSize, target, job, progress);
        } catch (IOException | RuntimeException first) {
            // Without a proxy a second identical attempt only doubles the wait.
            if (job.isCanceled() || UpdaterProxy.active() == null) {
                throw first;
            }
            FileLog.e("UpdateDownloader: retrying without proxy", first);
            run(direct, url, hintSize, target, job, progress);
        }
    }

    private static void run(OkHttpClient client, String url, long hintSize, File target, Job job,
                            Progress progress) throws IOException {
        long total = -1;
        boolean ranges = false;
        String finalUrl = url;

        Call probe = job.track(client.newCall(new Request.Builder().url(url)
                .header("User-Agent", "ExFullGram").header("Range", "bytes=0-0").build()));
        try (Response response = probe.execute()) {
            if (response.code() == 206) {
                String contentRange = response.header("Content-Range");
                int slash = contentRange == null ? -1 : contentRange.lastIndexOf('/');
                if (slash > 0) {
                    try {
                        total = Long.parseLong(contentRange.substring(slash + 1).trim());
                        ranges = total > 0;
                    } catch (NumberFormatException ignored) {
                    }
                }
                finalUrl = response.request().url().toString();
            } else if (response.isSuccessful()) {
                ResponseBody body = response.body();
                total = body != null && body.contentLength() > 0 ? body.contentLength() : hintSize;
            } else {
                throw new IOException("HTTP " + response.code());
            }
        } finally {
            job.untrack(probe);
        }
        if (job.isCanceled()) {
            throw new IOException("canceled");
        }
        if (ranges && total >= MIN_PARALLEL_BYTES) {
            // Over HTTP/2 every segment would share one TCP connection (and one throttle), so the
            // parallel segments would gain nothing. HTTP/1.1 gives each of them its own connection.
            OkHttpClient perSegment = client.newBuilder()
                    .protocols(java.util.Collections.singletonList(okhttp3.Protocol.HTTP_1_1)).build();
            parallel(perSegment, url, finalUrl, total, target, job, progress);
        } else {
            single(client, url, total > 0 ? total : hintSize, target, job, progress);
        }
    }

    private static void single(OkHttpClient client, String url, long total, File target, Job job,
                               Progress progress) throws IOException {
        File part = new File(target.getPath() + ".part");
        Call call = job.track(client.newCall(new Request.Builder().url(url)
                .header("User-Agent", "ExFullGram").build()));
        try (Response response = call.execute()) {
            ResponseBody body = response.body();
            if (!response.isSuccessful() || body == null) {
                throw new IOException("HTTP " + response.code());
            }
            long size = body.contentLength() > 0 ? body.contentLength() : total;
            try (InputStream input = body.byteStream(); FileOutputStream output = new FileOutputStream(part)) {
                byte[] buffer = new byte[64 * 1024];
                long done = 0;
                int read;
                while ((read = input.read(buffer)) > 0) {
                    output.write(buffer, 0, read);
                    done += read;
                    progress.onProgress(done, size);
                }
            }
        } finally {
            job.untrack(call);
        }
        finish(part, target);
    }

    private static void parallel(OkHttpClient client, String originalUrl, String resolvedUrl, long total,
                                 File target, Job job, Progress progress) throws IOException {
        File part = new File(target.getPath() + ".part");
        File stateFile = new File(target.getPath() + ".state");
        final long segmentSize = (total + SEGMENTS - 1) / SEGMENTS;
        final AtomicLongArray done = new AtomicLongArray(SEGMENTS);
        boolean resumed = loadState(stateFile, total, segmentSize, done) && part.length() == total;
        if (!resumed) {
            for (int i = 0; i < SEGMENTS; i++) {
                done.set(i, 0);
            }
        }
        final AtomicLong overall = new AtomicLong();
        for (int i = 0; i < SEGMENTS; i++) {
            overall.addAndGet(done.get(i));
        }

        try (RandomAccessFile file = new RandomAccessFile(part, "rw")) {
            if (file.length() != total) {
                file.setLength(total);
            }
            final FileChannel channel = file.getChannel();
            final AtomicBoolean failed = new AtomicBoolean();
            final IOException[] error = new IOException[1];
            final long[] lastSave = {System.currentTimeMillis()};
            final java.util.concurrent.atomic.AtomicInteger nextSegment = new java.util.concurrent.atomic.AtomicInteger();
            Thread[] threads = new Thread[WORKERS];
            for (int i = 0; i < WORKERS; i++) {
                threads[i] = new Thread(() -> {
                    try {
                        int index;
                        while (!failed.get() && (index = nextSegment.getAndIncrement()) < SEGMENTS) {
                            final long start = index * segmentSize;
                            final long end = Math.min(total, start + segmentSize) - 1;
                            if (start > end) {
                                continue;
                            }
                            segment(client, resolvedUrl, originalUrl, index, start, end,
                                    channel, done, overall, total, job, failed, progress, stateFile, segmentSize, lastSave);
                        }
                    } catch (Throwable t) {
                        final IOException e = t instanceof IOException ? (IOException) t : new IOException(t);
                        synchronized (error) {
                            if (error[0] == null) {
                                error[0] = e;
                            }
                        }
                        failed.set(true);
                        job.abortCalls();
                    }
                }, "gh-updater-part-" + i);
                threads[i].start();
            }
            boolean interrupted = false;
            for (Thread thread : threads) {
                while (true) {
                    try {
                        thread.join();
                        break;
                    } catch (InterruptedException e) {
                        interrupted = true;
                        job.cancel();
                    }
                }
            }
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
            channel.force(false);
            saveState(stateFile, total, done);
            if (error[0] != null) {
                throw error[0];
            }
            if (job.isCanceled()) {
                throw new IOException("canceled");
            }
        }
        for (int i = 0; i < SEGMENTS; i++) {
            long length = Math.min(total, (i + 1) * segmentSize) - i * segmentSize;
            if (done.get(i) != length) {
                throw new IOException("incomplete segment " + i);
            }
        }
        stateFile.delete();
        finish(part, target);
    }

    private static void segment(OkHttpClient client, String resolvedUrl, String originalUrl, int index,
                                long start, long end, FileChannel channel, AtomicLongArray done,
                                AtomicLong overall, long total, Job job, AtomicBoolean failed,
                                Progress progress, File stateFile, long segmentSize, long[] lastSave) throws IOException {
        int attempt = 0;
        while (true) {
            long position = start + done.get(index);
            if (position > end) {
                return;
            }
            String url = attempt == 0 ? resolvedUrl : originalUrl;
            Call call = job.track(client.newCall(new Request.Builder().url(url)
                    .header("User-Agent", "ExFullGram")
                    .header("Range", "bytes=" + position + "-" + end).build()));
            try (Response response = call.execute()) {
                ResponseBody body = response.body();
                if (response.code() != 206 || body == null) {
                    throw new IOException("HTTP " + response.code() + " for range");
                }
                String contentRange = response.header("Content-Range");
                if (contentRange == null || !contentRange.startsWith("bytes " + position + "-")
                        || !contentRange.endsWith("/" + total)) {
                    throw new IOException("unexpected Content-Range " + contentRange);
                }
                try (InputStream input = body.byteStream()) {
                    byte[] buffer = new byte[64 * 1024];
                    int read;
                    while ((read = input.read(buffer)) > 0) {
                        if (failed.get() || job.isCanceled()) {
                            throw new IOException("canceled");
                        }
                        long remaining = end - (start + done.get(index)) + 1;
                        int length = (int) Math.min(read, remaining);
                        ByteBuffer wrap = ByteBuffer.wrap(buffer, 0, length);
                        long at = start + done.get(index);
                        while (wrap.hasRemaining()) {
                            at += channel.write(wrap, at);
                        }
                        done.addAndGet(index, length);
                        progress.onProgress(overall.addAndGet(length), total);
                        maybeSave(channel, stateFile, total, done, lastSave);
                        if (length < read) {
                            break;
                        }
                    }
                }
                if (start + done.get(index) > end) {
                    return;
                }
                throw new IOException("segment " + index + " ended early");
            } catch (IOException e) {
                if (failed.get() || job.isCanceled() || ++attempt >= SEGMENT_ATTEMPTS) {
                    throw e;
                }
                FileLog.e("UpdateDownloader: segment " + index + " retry " + attempt, e);
                try {
                    Thread.sleep(400L * attempt);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IOException("canceled");
                }
            } finally {
                job.untrack(call);
            }
        }
    }

    private static void maybeSave(FileChannel channel, File stateFile, long total, AtomicLongArray done, long[] lastSave) {
        long now = System.currentTimeMillis();
        synchronized (lastSave) {
            if (now - lastSave[0] < STATE_SAVE_INTERVAL_MS) {
                return;
            }
            lastSave[0] = now;
        }
        try {
            channel.force(false);
        } catch (IOException ignored) {
        }
        saveState(stateFile, total, done);
    }

    private static synchronized void saveState(File stateFile, long total, AtomicLongArray done) {
        try (FileOutputStream out = new FileOutputStream(stateFile)) {
            JSONArray array = new JSONArray();
            array.put(total);
            for (int i = 0; i < done.length(); i++) {
                array.put(done.get(i));
            }
            out.write(array.toString().getBytes("UTF-8"));
        } catch (Exception e) {
            FileLog.e("UpdateDownloader: cannot save state", e);
        }
    }

    private static boolean loadState(File stateFile, long total, long segmentSize, AtomicLongArray done) {
        if (!stateFile.exists()) {
            return false;
        }
        try (InputStream in = new java.io.FileInputStream(stateFile)) {
            java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int read;
            while ((read = in.read(buffer)) > 0) {
                bytes.write(buffer, 0, read);
            }
            JSONArray array = new JSONArray(bytes.toString("UTF-8"));
            if (array.length() != SEGMENTS + 1 || array.getLong(0) != total) {
                return false;
            }
            for (int i = 0; i < SEGMENTS; i++) {
                long length = Math.min(total, (i + 1) * segmentSize) - i * segmentSize;
                long value = array.getLong(i + 1);
                if (value < 0 || value > length) {
                    return false;
                }
                done.set(i, value);
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static void finish(File part, File target) throws IOException {
        if (target.exists() && !target.delete()) {
            throw new IOException("cannot replace " + target);
        }
        if (!part.renameTo(target)) {
            throw new IOException("cannot rename " + part);
        }
    }
}
