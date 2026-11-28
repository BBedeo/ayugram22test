package app.exteraless.appearance.glass;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.SystemClock;
import android.util.LongSparseArray;
import android.util.SparseArray;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.animation.OvershootInterpolator;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.SharedConfig;
import org.telegram.ui.ActionBar.MessageDrawable;
import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.ui.Components.RecyclerListView;

import tw.nekomimi.nekogram.NekoConfig;

/**
 * Glass over selected chat messages, drawn by the chat list above its cells. It only reads the selection and the cells:
 * the message neither moves nor changes, and Telegram's own highlight and check marks stay.
 */
public final class SelectionGlassFrame {

    private static final long APPEAR_MS = 420;
    private static final long RING_MS = 560;
    private static final long LEAVE_MS = 220;

    private static final class Frame {
        final RectF rect = new RectF(); // albums only: the group background in list coordinates
        boolean selected;               // albums only: every message of the group is selected
        boolean on;
        long since;
        float alpha;                    // last drawn while on, where the fade starts
        int added;
        int seen;
    }

    private final LongSparseArray<Frame> messages = new LongSparseArray<>();
    private final LongSparseArray<Frame> albums = new LongSparseArray<>();
    // selected messages of the last draw; the value says whether that draw saw it for the first time
    private LongSparseArray<Boolean> current = new LongSparseArray<>();
    private LongSparseArray<Boolean> spare = new LongSparseArray<>();
    private int frame;

    // own instances: the themed bubble drawables are shared between cells
    private final MessageDrawable[] bubbles = new MessageDrawable[4];
    private final OvershootInterpolator pop = new OvershootInterpolator(2.4f);
    private final Path path = new Path();
    private final Path ringPath = new Path();
    private final RectF bounds = new RectF();
    private final Rect bubbleBounds = new Rect();
    private final Matrix matrix = new Matrix();
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final GlassRim rim = new GlassRim();
    private LinearGradient unit;
    private boolean unitDark;

    public SelectionGlassFrame() {
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(AndroidUtilities.dpf2(1));
    }

    public void beginFrame() {
        frame++;
    }

    public void addGroup(long groupId, RectF rect, boolean allSelected) {
        if (!GlassTouchSettings.selection()) {
            return;
        }
        Frame album = albums.get(groupId);
        if (album == null) {
            albums.put(groupId, album = new Frame());
        }
        album.rect.set(rect);
        album.selected = allSelected;
        album.added = frame;
    }

    public void draw(Canvas canvas, RecyclerListView list, SparseArray<MessageObject>[] selected) {
        if (!GlassTouchSettings.selection()) {
            current.clear();
            messages.clear();
            albums.clear();
            return;
        }
        if (track(selected) && !NekoConfig.disableVibration.Bool()) {
            list.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
        }
        final long now = SystemClock.uptimeMillis();
        boolean animating = false;
        for (int i = 0, count = list.getChildCount(); i < count; i++) {
            final View child = list.getChildAt(i);
            if (!(child instanceof ChatMessageCell) || child.getVisibility() != View.VISIBLE) {
                continue;
            }
            final ChatMessageCell cell = (ChatMessageCell) child;
            final MessageObject message = cell.getMessageObject();
            if (message == null) {
                continue;
            }
            final MessageObject.GroupedMessages group = cell.getCurrentMessagesGroup();
            if (group != null && group.messages.size() > 1) {
                final Frame album = albums.get(group.groupId);
                if (album == null || album.added != frame || album.seen == frame) {
                    continue;
                }
                boolean fresh = false;
                for (int a = 0, size = group.messages.size(); a < size; a++) {
                    fresh |= isFresh(selected, group.messages.get(a));
                }
                album.seen = frame;
                final float ringProgress = update(album, album.selected, fresh, now);
                if (ringProgress < 0) {
                    continue;
                }
                bounds.set(album.rect);
                album.rect.round(bubbleBounds);
                bubble(cell, message.isOutOwner());
                animating |= paint(canvas, album, cell.getAlpha(), ringProgress, now);
            } else {
                final long key = key(selected, message);
                final boolean on = key >= 0;
                final long id = ((long) message.getDialogId() << 32) ^ message.getId();
                Frame single = messages.get(id);
                if (single == null) {
                    if (!on) {
                        continue;
                    }
                    messages.put(id, single = new Frame());
                }
                single.seen = frame;
                final float ringProgress = update(single, on, on && current.get(key, false), now);
                if (ringProgress < 0 || !shape(cell, message)) {
                    continue;
                }
                canvas.save();
                // the transform the chat list uses for a bubble it draws itself
                canvas.translate(cell.getX(), cell.getY() + cell.getPaddingTop());
                canvas.scale(cell.getScaleX(), cell.getScaleY(), cell.getPivotX(), cell.getHeight() >> 1);
                animating |= paint(canvas, single, cell.getAlpha(), ringProgress, now);
                canvas.restore();
            }
        }
        // a frame that left the screen is forgotten, so it comes back without animation
        prune(messages);
        prune(albums);
        if (animating) {
            list.invalidate();
        }
    }

    /** Remembers the selection and says whether it gained a message since the last draw. */
    private boolean track(SparseArray<MessageObject>[] selected) {
        final LongSparseArray<Boolean> next = spare;
        next.clear();
        boolean added = false;
        for (int i = 0; i < selected.length; i++) {
            for (int a = 0, size = selected[i].size(); a < size; a++) {
                final long key = key(i, selected[i].keyAt(a));
                final boolean isNew = current.indexOfKey(key) < 0;
                added |= isNew;
                next.put(key, isNew);
            }
        }
        spare = current;
        current = next;
        return added;
    }

    private static long key(int index, int messageId) {
        return ((long) index << 32) | (messageId & 0xFFFFFFFFL);
    }

    /** Key of a selected message, -1 when it is not selected; both lists count, message ids repeat across dialogs. */
    private static long key(SparseArray<MessageObject>[] selected, MessageObject message) {
        for (int i = 0; i < selected.length; i++) {
            final MessageObject found = selected[i].get(message.getId());
            if (found != null && found.getDialogId() == message.getDialogId()) {
                return key(i, message.getId());
            }
        }
        return -1;
    }

    private boolean isFresh(SparseArray<MessageObject>[] selected, MessageObject message) {
        final long key = key(selected, message);
        return key >= 0 && current.get(key, false);
    }

    /** Moves the frame to its new state; returns the ring progress (1 when none), or -1 when nothing is to be drawn. */
    private float update(Frame f, boolean on, boolean fresh, long now) {
        if (on != f.on) {
            f.on = on;
            f.since = on && !fresh ? now - RING_MS : now;
        }
        if (f.on) {
            return Math.min(1f, (now - f.since) / (float) RING_MS);
        }
        return f.since != 0 && now - f.since < LEAVE_MS ? 1f : -1f;
    }

    private void bubble(ChatMessageCell cell, boolean out) {
        final boolean media = cell.isGlassFrameMedia();
        final int slot = (media ? 1 : 0) + (out ? 2 : 0);
        MessageDrawable drawable = bubbles[slot];
        if (drawable == null) {
            bubbles[slot] = drawable = new MessageDrawable(media ? MessageDrawable.TYPE_MEDIA : MessageDrawable.TYPE_TEXT, out, false);
        }
        drawable.setTopBottomNear(cell.isPinnedTop(), cell.isPinnedBottom());
        drawable.setBotButtonsBottom(cell.hasInlineBotButtons());
        drawable.setBounds(bubbleBounds);
        path.set(drawable.makePath());
    }

    /** Outline of one message in cell coordinates: its bubble, or the media of a message without one. */
    private boolean shape(ChatMessageCell cell, MessageObject message) {
        if (cell.getGlassFrameBounds(bubbleBounds)) {
            bounds.set(bubbleBounds);
            bubble(cell, message.isOutOwner());
            return true;
        }
        final ImageReceiver photo = cell.getPhotoImage();
        if (message.type == MessageObject.TYPE_EMOJIS || photo.getImageWidth() <= 0 || photo.getImageHeight() <= 0) {
            return false;
        }
        bounds.set(photo.getImageX(), photo.getImageY(), photo.getImageX2(), photo.getImageY2());
        final float radius = message.isRoundVideo() ? Math.min(bounds.width(), bounds.height()) / 2f : AndroidUtilities.dp(SharedConfig.bubbleRadius);
        path.rewind();
        path.addRoundRect(bounds, radius, radius, Path.Direction.CW);
        return true;
    }

    /** Draws the current {@code path} inside {@code bounds}; returns whether it still animates. */
    private boolean paint(Canvas canvas, Frame f, float cellAlpha, float ringProgress, long now) {
        final long time = now - f.since;
        final float alpha;
        float scale = 1f;
        if (f.on) {
            final float t = Math.min(1f, time / (float) APPEAR_MS);
            alpha = f.alpha = Math.min(1f, t / 0.4f);
            scale = 0.9f + 0.1f * pop.getInterpolation(t);
        } else {
            alpha = f.alpha * (1f - time / (float) LEAVE_MS);
        }
        final float shown = Math.max(0f, Math.min(1f, alpha * cellAlpha));
        final boolean dark = GlassTokens.isDark();
        if (unit == null || unitDark != dark) {
            unitDark = dark;
            unit = new LinearGradient(0, 0, 1, 1,
                    dark ? Color.argb(44, 255, 255, 255) : Color.argb(8, 0, 0, 0),
                    dark ? Color.argb(6, 255, 255, 255) : Color.argb(28, 0, 0, 0),
                    Shader.TileMode.CLAMP);
            fill.setShader(unit);
        }
        matrix.setScale(bounds.width(), bounds.height());
        matrix.postTranslate(bounds.left, bounds.top);
        unit.setLocalMatrix(matrix);
        fill.setAlpha(Math.round(255 * shown));

        canvas.save();
        canvas.scale(scale, scale, bounds.centerX(), bounds.centerY());
        canvas.drawPath(path, fill);
        rim.draw(canvas, path, bounds, shown);
        canvas.restore();

        if (f.on && ringProgress < 1f && bounds.width() > 0 && bounds.height() > 0) {
            final float left = 1f - ringProgress;
            final float grow = AndroidUtilities.dpf2(14) * (1f - left * left);
            matrix.setScale(1f + 2f * grow / bounds.width(), 1f + 2f * grow / bounds.height(), bounds.centerX(), bounds.centerY());
            path.transform(matrix, ringPath);
            final int value = dark ? 255 : 0;
            ring.setColor(Color.argb(Math.round(190 * left * left * (dark ? 1f : 150 / 255f) * Math.max(0f, Math.min(1f, cellAlpha))), value, value, value));
            canvas.drawPath(ringPath, ring);
        }
        return !f.on || ringProgress < 1f;
    }

    private void prune(LongSparseArray<Frame> frames) {
        for (int i = frames.size() - 1; i >= 0; i--) {
            if (frames.valueAt(i).seen != frame) {
                frames.removeAt(i);
            }
        }
    }
}
