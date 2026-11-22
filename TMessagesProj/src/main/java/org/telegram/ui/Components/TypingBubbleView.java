package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.animation.OvershootInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.blur3.BlurredBackgroundDrawableViewFactory;
import org.telegram.ui.Components.blur3.drawable.BlurredBackgroundDrawable;
import org.telegram.ui.Components.blur3.drawable.color.BlurredBackgroundColorProvider;

import app.exteraless.appearance.LiquidGlassTuning;
import app.exteraless.appearance.M3SurfaceHelper;

/**
 * Compact incoming-style bubble shown above the composer/keyboard when the peer is typing,
 * with style-adaptive Liquid Glass / Material / Classic background and morph-into-message exit.
 *
 * <p>Also renders the live typing draft (ExFullGram «живое превью печати»): {@link #showDraft}
 * shows the actual text the peer is typing, growing letter by letter as new characters arrive.
 */
public class TypingBubbleView extends LinearLayout {

    private static final int DRAFT_MAX_WIDTH_DP = 252;
    private static final int DRAFT_MAX_LINES = 2;

    private final Theme.ResourcesProvider resourcesProvider;
    private final BlurredBackgroundDrawableViewFactory glassFactory;
    private final BlurredBackgroundColorProvider glassColorProvider;

    private final ImageView iconView;
    private final TextView labelView;
    private final StatusDrawable[] statusDrawables = new StatusDrawable[5];

    private int activeType = -1;
    private boolean showing;
    private boolean morphingOut;
    private float baseTranslationY;
    private float animOffsetY;
    private ValueAnimator visibilityAnimator;
    private Runnable onHiddenListener;

    /** Кэш последнего применённого стиля: пересобирать фон на каждый кадр незачем. */
    private int lastBubbleColor;
    private int lastTextColor;
    private int lastAccentColor;
    private boolean lastUseGlass;
    private boolean lastUseM3;
    private boolean styleApplied;

    /** Режим живого черновика: многострочный текст вместо короткой строки «печатает…». */
    private boolean draftMode;
    /** Сколько символов черновика уже показано — дорисовываем только новый хвост. */
    private CharSequence displayedDraft;
    private ValueAnimator draftRevealAnimator;

    public TypingBubbleView(
            Context context,
            @Nullable Theme.ResourcesProvider resourcesProvider,
            @Nullable BlurredBackgroundColorProvider glassColorProvider,
            @Nullable BlurredBackgroundDrawableViewFactory glassFactory) {
        super(context);
        this.resourcesProvider = resourcesProvider;
        this.glassColorProvider = glassColorProvider;
        this.glassFactory = glassFactory;

        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setPadding(dp(11), dp(7), dp(12), dp(7));
        setVisibility(GONE);
        setAlpha(0f);

        statusDrawables[0] = new TypingDotsDrawable(false);
        statusDrawables[1] = new RecordStatusDrawable(false);
        statusDrawables[2] = new SendingFileDrawable(false);
        statusDrawables[3] = new PlayingGameDrawable(false, resourcesProvider);
        statusDrawables[4] = new RoundStatusDrawable(false);
        for (StatusDrawable d : statusDrawables) {
            if (d != null) {
                d.setIsChat(true);
            }
        }

        iconView = new ImageView(context);
        iconView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        addView(iconView, LayoutHelper.createLinear(20, 16, Gravity.CENTER_VERTICAL, 0, 0, 5, 0));

        labelView = new TextView(context);
        labelView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        labelView.setSingleLine(true);
        labelView.setEllipsize(TextUtils.TruncateAt.END);
        labelView.setMaxWidth(dp(220));
        addView(labelView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL));

        updateStyle();
    }

    public void updateStyle() {
        final int bubbleColor = Theme.getColor(Theme.key_chat_inBubble, resourcesProvider);
        final int textColor = Theme.getColor(Theme.key_chat_messageTextIn, resourcesProvider);
        final int accentColor = Theme.getColor(Theme.key_chat_inTimeText, resourcesProvider);
        final boolean useGlass = LiquidGlassTuning.isActive()
                && !M3SurfaceHelper.isMaterial3OpaqueChrome()
                && glassFactory != null
                && glassColorProvider != null;
        final boolean useM3 = !useGlass && M3SurfaceHelper.isMaterial3SurfaceMode();
        if (styleApplied
                && lastBubbleColor == bubbleColor
                && lastTextColor == textColor
                && lastAccentColor == accentColor
                && lastUseGlass == useGlass
                && lastUseM3 == useM3) {
            return;
        }
        styleApplied = true;
        lastBubbleColor = bubbleColor;
        lastTextColor = textColor;
        lastAccentColor = accentColor;
        lastUseGlass = useGlass;
        lastUseM3 = useM3;

        labelView.setTextColor(textColor);
        for (StatusDrawable d : statusDrawables) {
            if (d != null) {
                d.setColor(accentColor);
            }
        }

        if (useGlass) {
            final BlurredBackgroundDrawable bg = glassFactory.create(this, glassColorProvider);
            bg.setRadius(dp(16), dp(16), dp(16), dp(6));
            setBackground(bg);
            setElevation(0f);
        } else if (useM3) {
            final GradientDrawable bg = new GradientDrawable();
            bg.setColor(M3SurfaceHelper.getCardColor(resourcesProvider));
            bg.setCornerRadii(new float[]{
                    dp(18), dp(18),
                    dp(18), dp(18),
                    dp(18), dp(18),
                    dp(6), dp(6)
            });
            bg.setStroke(dp(1), M3SurfaceHelper.getOutlineVariantColor(resourcesProvider));
            setBackground(bg);
            setElevation(dp(1));
        } else {
            final GradientDrawable bg = new GradientDrawable();
            bg.setColor(bubbleColor);
            bg.setCornerRadii(new float[]{
                    dp(16), dp(16),
                    dp(16), dp(16),
                    dp(16), dp(16),
                    dp(6), dp(6)
            });
            bg.setStroke(dp(1), Theme.multAlpha(accentColor, 0.14f));
            setBackground(bg);
            setElevation(dp(2));
        }
    }

    /** Вызывается, когда пузырь полностью погас — чтобы вернуть чату нижний отступ. */
    public void setOnHiddenListener(Runnable listener) {
        onHiddenListener = listener;
    }

    public void setBaseTranslationY(float y) {
        if (baseTranslationY != y) {
            baseTranslationY = y;
            setTranslationY(baseTranslationY + animOffsetY);
        }
    }

    public boolean isShowingOrMorphing() {
        return showing || morphingOut;
    }

    /**
     * Живой черновик собеседника: многострочный пузырь с реальным текстом.
     * Новые символы дорисовываются «печатной машинкой», поэтому пузырь растёт на глазах.
     */
    public void showDraft(CharSequence text, float baseY) {
        final String next = text == null ? "" : text.toString();
        if (next.isEmpty()) {
            hideTyping();
            return;
        }
        draftMode = true;
        applyDraftLabelStyle();
        ensureShown(0, baseY);

        final String previous = displayedDraft == null ? "" : displayedDraft.toString();
        cancelDraftReveal();
        if (next.equals(previous)) {
            return;
        }
        if (next.startsWith(previous) && next.length() > previous.length()) {
            startDraftTypewriter(previous, next);
        } else {
            displayedDraft = next;
            labelView.setText(next);
            labelView.setAlpha(0.35f);
            labelView.animate().alpha(1f).setDuration(150).setInterpolator(CubicBezierInterpolator.EASE_OUT).start();
        }
    }

    public void showTyping(CharSequence text, int type, float baseY) {
        draftMode = false;
        displayedDraft = null;
        cancelDraftReveal();
        applyTypingLabelStyle();
        final CharSequence clean = text != null
                ? TextUtils.replace(text, new String[]{"..."}, new String[]{""}).toString().trim()
                : "";
        if (!TextUtils.isEmpty(clean)) {
            labelView.setText(clean);
        }
        ensureShown(type, baseY);
    }

    private void applyTypingLabelStyle() {
        labelView.setSingleLine(true);
        labelView.setMaxLines(1);
        labelView.setEllipsize(TextUtils.TruncateAt.END);
        labelView.setMaxWidth(dp(220));
        labelView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        labelView.setAlpha(1f);
        labelView.setLineSpacing(0f, 1f);
    }

    private void applyDraftLabelStyle() {
        labelView.setSingleLine(false);
        labelView.setMaxLines(DRAFT_MAX_LINES);
        labelView.setEllipsize(TextUtils.TruncateAt.END);
        labelView.setMaxWidth(dp(DRAFT_MAX_WIDTH_DP));
        labelView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        labelView.setLineSpacing(dp(1), 1f);
    }

    private void startDraftTypewriter(String previous, String next) {
        final int added = Math.max(1, next.length() - previous.length());
        final long duration = Math.min(340L, Math.max(90L, added * 16L));
        final ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(duration);
        animator.setInterpolator(CubicBezierInterpolator.EASE_OUT);
        animator.addUpdateListener(animation -> {
            final float p = (float) animation.getAnimatedValue();
            int shown = previous.length() + (int) (added * p);
            if (shown < previous.length()) {
                shown = previous.length();
            }
            if (shown > next.length()) {
                shown = next.length();
            }
            if (shown > 0 && shown < next.length() && Character.isLowSurrogate(next.charAt(shown))) {
                shown++;
            }
            displayedDraft = next.substring(0, Math.min(shown, next.length()));
            labelView.setText(displayedDraft);
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                displayedDraft = next;
                labelView.setText(next);
            }
        });
        draftRevealAnimator = animator;
        animator.start();
    }

    private void cancelDraftReveal() {
        if (draftRevealAnimator != null) {
            draftRevealAnimator.removeAllListeners();
            draftRevealAnimator.cancel();
            draftRevealAnimator = null;
        }
    }

    private void ensureShown(int type, float baseY) {
        setBaseTranslationY(baseY);

        final int safeType = (type >= 0 && type < statusDrawables.length && statusDrawables[type] != null) ? type : 0;
        if (activeType != safeType) {
            stopStatusDrawables();
            activeType = safeType;
            iconView.setImageDrawable(statusDrawables[activeType]);
        }
        if (statusDrawables[activeType] != null) {
            statusDrawables[activeType].start();
        }

        if (showing && !morphingOut) {
            return;
        }
        showing = true;
        morphingOut = false;
        cancelVisibilityAnimator();

        setVisibility(VISIBLE);
        setPivotX(0f);
        setPivotY(getMeasuredHeight() > 0 ? getMeasuredHeight() : dp(30));

        final float startAlpha = getAlpha();
        final float startScale = getScaleX() > 0.1f && startAlpha > 0.05f ? getScaleX() : 0.86f;
        final float startOffsetY = startAlpha > 0.05f ? animOffsetY : dp(9);

        visibilityAnimator = ValueAnimator.ofFloat(0f, 1f);
        visibilityAnimator.setDuration(200);
        visibilityAnimator.setInterpolator(new OvershootInterpolator(0.85f));
        visibilityAnimator.addUpdateListener(anim -> {
            final float p = (float) anim.getAnimatedValue();
            final float s = AndroidUtilities.lerp(startScale, 1f, p);
            setAlpha(AndroidUtilities.lerp(startAlpha, 1f, Math.min(1f, p * 1.25f)));
            setScaleX(s);
            setScaleY(s);
            animOffsetY = AndroidUtilities.lerp(startOffsetY, 0f, p);
            setTranslationY(baseTranslationY + animOffsetY);
        });
        visibilityAnimator.start();
    }

    public void hideTyping() {
        if (!showing || morphingOut) {
            return;
        }
        showing = false;
        animateOut(CollapseKind.OUT);
    }

    /**
     * Сообщение пришло: пузырь не «улетает» вверх на него, а быстро стекает вниз и схлопывается,
     * освобождая место — сообщение появляется уже на чистом месте, со своей нативной анимацией.
     */
    public void morphIntoMessage() {
        if (!showing && getVisibility() != VISIBLE) {
            return;
        }
        showing = false;
        morphingOut = true;
        cancelDraftReveal();
        animateOut(CollapseKind.INTO_MESSAGE);
    }

    private enum CollapseKind { OUT, INTO_MESSAGE }

    private void animateOut(CollapseKind kind) {
        cancelVisibilityAnimator();
        final boolean intoMessage = kind == CollapseKind.INTO_MESSAGE;
        setPivotX(0f);
        setPivotY(getMeasuredHeight() > 0 ? getMeasuredHeight() : dp(30));

        final float startAlpha = getAlpha();
        final float startScaleX = getScaleX();
        final float startScaleY = getScaleY();
        final float startOffsetY = animOffsetY;

        // В сообщение: короткое «стекание» вниз, чтобы не перекрывать пришедший бабл.
        // Обычное скрытие: мягкий уход вниз.
        final float targetScaleX = intoMessage ? 0.55f : 0.78f;
        final float targetScaleY = intoMessage ? 0.72f : 0.86f;
        final float targetOffsetY = intoMessage ? dp(6) : dp(8);

        visibilityAnimator = ValueAnimator.ofFloat(0f, 1f);
        visibilityAnimator.setDuration(intoMessage ? 140 : 160);
        visibilityAnimator.setInterpolator(intoMessage
                ? CubicBezierInterpolator.EASE_IN
                : CubicBezierInterpolator.EASE_OUT);
        visibilityAnimator.addUpdateListener(anim -> {
            final float p = (float) anim.getAnimatedValue();
            setAlpha(AndroidUtilities.lerp(startAlpha, 0f, p));
            setScaleX(AndroidUtilities.lerp(startScaleX, targetScaleX, p));
            setScaleY(AndroidUtilities.lerp(startScaleY, targetScaleY, p));
            animOffsetY = AndroidUtilities.lerp(startOffsetY, targetOffsetY, p);
            setTranslationY(baseTranslationY + animOffsetY);
        });
        visibilityAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                morphingOut = false;
                if (!showing) {
                    setVisibility(GONE);
                    setAlpha(0f);
                    setScaleX(1f);
                    setScaleY(1f);
                    animOffsetY = 0f;
                    setTranslationY(baseTranslationY);
                    stopStatusDrawables();
                    draftMode = false;
                    displayedDraft = null;
                    cancelDraftReveal();
                    applyTypingLabelStyle();
                    if (onHiddenListener != null) {
                        onHiddenListener.run();
                    }
                }
            }
        });
        visibilityAnimator.start();
    }

    private void cancelVisibilityAnimator() {
        if (visibilityAnimator != null) {
            visibilityAnimator.removeAllListeners();
            visibilityAnimator.cancel();
            visibilityAnimator = null;
        }
    }

    private void stopStatusDrawables() {
        for (StatusDrawable d : statusDrawables) {
            if (d != null) {
                d.stop();
            }
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        cancelVisibilityAnimator();
        cancelDraftReveal();
        stopStatusDrawables();
    }
}
