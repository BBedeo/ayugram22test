package app.exteraless.player;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewTreeObserver;

import androidx.annotation.NonNull;

import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.blur3.BlurredBackgroundDrawableViewFactory;
import org.telegram.ui.Components.blur3.drawable.BlurredBackgroundDrawable;
import org.telegram.utils.glass.source.GlassSourceBitmap;

import java.util.ArrayList;

import app.exteraless.appearance.GlassMenuHelper;
import app.exteraless.appearance.glass.GlassWindowBackdrop;

/**
 * Own lens glass for the player sheet (a trimmed copy of GlassWindowBackdrop): instead of a snapshot of the screen
 * behind the window it samples a small offscreen render of the sheet's own background (the blurred cover),
 * so the glass refracts what really is behind it and follows the background when it changes.
 * The page under the sheet is never captured.
 */
final class PlayerGlass {

    interface Painter {
        void paint(Canvas canvas, int width, int height);
    }

    private static final int DOWNSCALE = 5;

    private final View root;
    private final Painter painter;
    private final Theme.ResourcesProvider resourcesProvider;
    private final GlassSourceBitmap source = new GlassSourceBitmap();
    private final BlurredBackgroundDrawableViewFactory factory = new BlurredBackgroundDrawableViewFactory(source);
    private final Matrix matrix = new Matrix();
    private final ArrayList<Entry> entries = new ArrayList<>();
    private final int[] rootLocation = new int[2];
    private final int[] hostLocation = new int[2];
    private Bitmap bitmap;
    private Canvas bitmapCanvas;
    private boolean released;

    private static final class Entry {
        final View host;
        final BlurredBackgroundDrawable lens;
        float radius = -1f;

        Entry(View host, BlurredBackgroundDrawable lens) {
            this.host = host;
            this.lens = lens;
        }
    }

    private final ViewTreeObserver.OnPreDrawListener preDraw = () -> {
        sync();
        return true;
    };

    static boolean supported() {
        return GlassWindowBackdrop.supported();
    }

    PlayerGlass(View root, Theme.ResourcesProvider resourcesProvider, Painter painter) {
        this.root = root;
        this.resourcesProvider = resourcesProvider;
        this.painter = painter;
        root.getViewTreeObserver().addOnPreDrawListener(preDraw);
        root.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(@NonNull View v) {
                if (!released) {
                    v.getViewTreeObserver().addOnPreDrawListener(preDraw);
                }
            }

            @Override
            public void onViewDetachedFromWindow(@NonNull View v) {
                v.getViewTreeObserver().removeOnPreDrawListener(preDraw);
            }
        });
    }

    /**
     * A capsule/circle lens as the host's background. The header lens already draws its own Fresnel catchlights
     * (top-left and bottom-right), so no second rim goes on top of it: two different highlights on one glass
     * read as a double border.
     */
    Drawable attach(View host) {
        final BlurredBackgroundDrawable lens = factory.create(null, GlassMenuHelper.scrimMenuBackgroundFill(resourcesProvider));
        lens.setHeaderGlassLens(true);
        entries.add(new Entry(host, lens));
        return lens;
    }

    /** Re-renders the background into the sampled bitmap (colours or cover changed). */
    void refresh() {
        if (released || root.getWidth() <= 0 || root.getHeight() <= 0) {
            return;
        }
        final int w = Math.max(1, root.getWidth() / DOWNSCALE);
        final int h = Math.max(1, root.getHeight() / DOWNSCALE);
        if (bitmap == null || bitmap.getWidth() != w || bitmap.getHeight() != h) {
            bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            bitmapCanvas = new Canvas(bitmap);
            source.setBitmap(bitmap);
        }
        bitmapCanvas.save();
        bitmapCanvas.scale(w / (float) root.getWidth(), h / (float) root.getHeight());
        painter.paint(bitmapCanvas, root.getWidth(), root.getHeight());
        bitmapCanvas.restore();
        matrix.setScale(root.getWidth() / (float) w, root.getHeight() / (float) h);
        source.setMatrix(matrix);
        for (Entry e : entries) {
            e.lens.invalidateDisplayList();
            e.lens.invalidateSelf();
        }
    }

    private void sync() {
        if (released || root.getWidth() <= 0) {
            return;
        }
        if (bitmap == null || bitmap.getWidth() != Math.max(1, root.getWidth() / DOWNSCALE)
                || bitmap.getHeight() != Math.max(1, root.getHeight() / DOWNSCALE)) {
            refresh();
        }
        root.getLocationInWindow(rootLocation);
        for (Entry e : entries) {
            final View host = e.host;
            if (host.getWidth() <= 0 || host.getHeight() <= 0) {
                continue;
            }
            host.getLocationInWindow(hostLocation);
            final int x = hostLocation[0] - rootLocation[0];
            final int y = hostLocation[1] - rootLocation[1];
            boolean changed = false;
            if (e.lens.getSourceOffsetX() != x || e.lens.getSourceOffsetY() != y) {
                e.lens.setSourceOffset(x, y);
                changed = true;
            }
            final float radius = Math.min(host.getWidth(), host.getHeight()) / 2f;
            if (e.radius != radius) {
                e.radius = radius;
                e.lens.setRadius(radius);
                changed = true;
            }
            if (changed) {
                e.lens.invalidateSelf();
            }
        }
    }

    void release() {
        released = true;
        root.getViewTreeObserver().removeOnPreDrawListener(preDraw);
        entries.clear();
    }
}
