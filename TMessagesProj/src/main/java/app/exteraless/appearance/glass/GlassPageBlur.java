package app.exteraless.appearance.glass;

import android.os.Build;
import android.view.View;
import android.view.ViewTreeObserver;

import androidx.core.util.Supplier;

/**
 * A folder page arrives through glass (Android 12+). It reads the page's shift before every draw, whoever writes it (the
 * finger, the release animator or the tab strip), and never writes it.
 */
public final class GlassPageBlur implements ViewTreeObserver.OnPreDrawListener, View.OnAttachStateChangeListener {

    private final View host;
    private final Supplier<View> incoming;
    private final GlassBackBlur veil = new GlassBackBlur();
    private boolean running;

    public GlassPageBlur(View host, Supplier<View> incoming) {
        this.host = host;
        this.incoming = incoming;
    }

    /** Called when a page starts to arrive; it stops by itself once that page is no longer shown. */
    public void start() {
        if (running || Build.VERSION.SDK_INT < 31 || !GlassTouchSettings.pages()) {
            return;
        }
        running = true;
        host.getViewTreeObserver().addOnPreDrawListener(this);
        host.addOnAttachStateChangeListener(this);
    }

    @Override
    public boolean onPreDraw() {
        final View page = incoming.get(); // looked up each time: a re-grabbed swipe swaps the pages
        if (page == null || page.getVisibility() != View.VISIBLE) {
            stop();
        } else {
            veil.set(page, GlassTouchMath.pageStrength(page.getTranslationX(), page.getWidth()));
        }
        return true;
    }

    @Override
    public void onViewAttachedToWindow(View v) {
    }

    @Override
    public void onViewDetachedFromWindow(View v) {
        stop();
    }

    private void stop() {
        running = false;
        host.getViewTreeObserver().removeOnPreDrawListener(this);
        host.removeOnAttachStateChangeListener(this);
        veil.disarm();
    }
}
