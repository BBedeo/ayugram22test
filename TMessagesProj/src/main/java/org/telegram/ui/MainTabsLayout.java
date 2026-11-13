package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.lerp;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.RenderEffect;
import android.graphics.RenderNode;
import android.graphics.RuntimeShader;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.VelocityTracker;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.dynamicanimation.animation.DynamicAnimation;
import androidx.dynamicanimation.animation.FloatPropertyCompat;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;

import android.util.Log;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.LiteMode;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedLinearLayout;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.glass.GlassTabView;
import org.telegram.ui.Components.blur3.drawable.BlurredBackgroundDrawable;

import app.exteraless.appearance.MainTabsUiHelper;
import app.exteraless.appearance.ElasticPillGesture;
import app.exteraless.appearance.ElasticGlassPillRenderer;

import java.util.HashSet;
import java.util.Set;

import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.android.animator.ListAnimator;
import me.vkryl.android.util.ClickHelper;

import tw.nekomimi.nekogram.NekoConfig;
import xyz.nextalone.nagram.NaConfig;

@SuppressLint("ViewConstructor")
public class MainTabsLayout extends AnimatedLinearLayout {

    public static final int BOTTOM_NAVIGATION_MODE_SHOW = 0;
    public static final int BOTTOM_NAVIGATION_MODE_HIDE = 1;
    public static final int BOTTOM_NAVIGATION_MODE_FLOATING = 2;

    private static final String KEY_BOTTOM_NAVIGATION_FLOATING = "OEAppearanceBottomNavigationFloating";

    private static Boolean bottomNavigationFloating;

    public static int getBottomNavigationMode() {
        if (isBottomNavigationHidden()) {
            return BOTTOM_NAVIGATION_MODE_HIDE;
        }
        return isBottomNavigationFloating() ? BOTTOM_NAVIGATION_MODE_FLOATING : BOTTOM_NAVIGATION_MODE_SHOW;
    }

    public static void setBottomNavigationMode(int mode) {
        NaConfig.INSTANCE.getHideBottomNavigationBar().setConfigBool(mode == BOTTOM_NAVIGATION_MODE_HIDE);
        final boolean floating = mode == BOTTOM_NAVIGATION_MODE_FLOATING;
        bottomNavigationFloating = floating;
        final SharedPreferences preferences = getBottomNavigationPreferences();
        if (preferences != null) {
            preferences.edit().putBoolean(KEY_BOTTOM_NAVIGATION_FLOATING, floating).apply();
        }
    }

    public static boolean isBottomNavigationHidden() {
        return NaConfig.INSTANCE.getHideBottomNavigationBar().Bool();
    }

    public static boolean isBottomNavigationVisible() {
        return !isBottomNavigationHidden();
    }

    public static boolean isBottomNavigationFloating() {
        if (isBottomNavigationHidden()) {
            return false;
        }
        if (bottomNavigationFloating == null) {
            final SharedPreferences preferences = getBottomNavigationPreferences();
            if (preferences == null) {
                return false;
            }
            bottomNavigationFloating = preferences.getBoolean(KEY_BOTTOM_NAVIGATION_FLOATING, false);
        }
        return bottomNavigationFloating;
    }

    private static SharedPreferences getBottomNavigationPreferences() {
        return ApplicationLoader.applicationContext == null ? null : NekoConfig.getPreferences();
    }

    private final Theme.ResourcesProvider resourcesProvider;
    private final ElasticGlassPillRenderer elasticGlassPill = new ElasticGlassPillRenderer();
    private BlurredBackgroundDrawable elasticPillGlass;
    private float selectedTabDestinationX;
    private View elasticSelectedTab;
    private View elasticTouchTab;
    private float elasticDownX, elasticDownY, elasticTouchOffsetX;
    private boolean elasticDragging;
    private boolean elasticTouchStartedOnPill;
    private boolean elasticMenuShownDuringTouch;
    private int elasticTouchPointer = -1;
    private VelocityTracker elasticVelocityTracker;
    private float elasticReleaseVelocityX;

    /** A separate native backdrop lens is layered ABOVE the panel and BELOW the tab icons. */
    public void setElasticPillGlass(BlurredBackgroundDrawable glass) {
        if (elasticPillGlass != null) elasticPillGlass.setCallback(null);
        elasticPillGlass = glass;
        if (elasticPillGlass != null) elasticPillGlass.setCallback(this);
        if (glass != null) glass.setCallback(this);
        invalidate();
    }

    public void updateElasticPillColors() {
        if (elasticPillGlass != null) {
            elasticPillGlass.updateColors();
        }
        invalidate();
    }

    public MainTabsLayout(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;
    }

    private static final float[] PASS_TEXT_SIZES_DP = {12f, 12f, 10f};
    private static final int[] PASS_PADDINGS_DP = {16, 8, 4};

    private int maxWidthPx;

    public void setMaxWidth(int maxWidthPx) {
        if (this.maxWidthPx != maxWidthPx) {
            this.maxWidthPx = maxWidthPx;
            requestLayout();
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        final int height = MeasureSpec.getSize(heightMeasureSpec);
        final int tabHeight = height - getPaddingTop() - getPaddingBottom();

        if (maxWidthPx > 0 && width > maxWidthPx) {
            width = maxWidthPx;
        }

        final boolean fillWidth = MainTabsUiHelper.isMaterial3NavigationBar() || MainTabsUiHelper.isIosNavigationBar();
        final int maxTotalWidthForTabs = width - getPaddingLeft() - getPaddingRight();
        final int minTotalWidthForTabs = fillWidth
                ? maxTotalWidthForTabs
                : Math.min(dp(320), maxTotalWidthForTabs);

        int chosenPass = PASS_TEXT_SIZES_DP.length - 1;
        float lastMeasuredTextSize = -1;
        for (int pass = 0; pass < PASS_TEXT_SIZES_DP.length; pass++) {
            if (PASS_TEXT_SIZES_DP[pass] != lastMeasuredTextSize) {
                measureTabTexts(PASS_TEXT_SIZES_DP[pass]);
                lastMeasuredTextSize = PASS_TEXT_SIZES_DP[pass];
            }
            final int padding = dp(PASS_PADDINGS_DP[pass]);
            float total = 0;
            for (int a = 0, N = getChildCount(); a < N; a++) {
                if (!isViewVisible(getChildAt(a))) continue;
                final float withMargin = tabsTextWidth[a] + padding * 2;
                total += withMargin;
            }
            final boolean fits = total <= maxTotalWidthForTabs;
            if (fits || pass == PASS_TEXT_SIZES_DP.length - 1) {
                chosenPass = pass;
                break;
            }
        }

        applyPassTextSize(chosenPass);

        final int tabPadding = dp(PASS_PADDINGS_DP[chosenPass]);
        final int maxTabTextWidthIfEq = (maxTotalWidthForTabs / Math.max(1, visibleChildCount)) - tabPadding * 2;

        float totalWidth = 0;
        int totalWeight = 0;
        for (int a = 0, N = getChildCount(); a < N; a++) {
            final View child = getChildAt(a);
            if (!isViewVisible(child)) {
                tabsTextWidth[a] = tabsTextWidthWithMargin[a] = 0;
                tabsWeight[a] = 0;
                continue;
            }

            tabsTextWidthWithMargin[a] = tabsTextWidth[a] + tabPadding * 2;
            tabsWeight[a] = tabsTextWidthWithMargin[a] > (maxTabTextWidthIfEq + tabPadding * 2) ? 0 : 1;

            totalWidth += tabsTextWidthWithMargin[a];
            totalWeight += tabsWeight[a];
        }

        if (totalWeight == 0) {
            for (int a = 0, N = getChildCount(); a < N; a++) {
                tabsWeight[a] = isViewVisible(getChildAt(a)) ? 1 : 0;
            }
            totalWeight = visibleChildCount;
        }

        if (totalWidth > maxTotalWidthForTabs) {
            final float m = maxTotalWidthForTabs / totalWidth;
            for (int a = 0, N = getChildCount(); a < N; a++) {
                tabsTextWidthWithMargin[a] *= m;
            }
        } else if (totalWidth < minTotalWidthForTabs) {
            final float growW = minTotalWidthForTabs - totalWidth;
            final float growP = growW / totalWeight;

            for (int a = 0, N = getChildCount(); a < N; a++) {
                tabsTextWidthWithMargin[a] += growP * tabsWeight[a];
            }
        }

        if (MainTabsUiHelper.isMaterial3NavigationBar() && visibleChildCount > 0) {
            final float share = (float) maxTotalWidthForTabs / visibleChildCount;
            boolean fitsEqually = true;
            for (int a = 0, N = getChildCount(); a < N; a++) {
                if (isViewVisible(getChildAt(a)) && tabsTextWidth[a] + tabPadding * 2 > share) {
                    fitsEqually = false;
                    break;
                }
            }
            if (fitsEqually) {
                for (int a = 0, N = getChildCount(); a < N; a++) {
                    if (isViewVisible(getChildAt(a))) {
                        tabsTextWidthWithMargin[a] = share;
                    }
                }
            }
        }

        int l = 0;
        for (int a = 0, N = getChildCount(); a < N; a++) {
            if (!isViewVisible(getChildAt(a))) {
                continue;
            }

            tabsWidth[a] = Math.round(tabsTextWidthWithMargin[a]);
            tabsLeftPos[a] = l;
            l += tabsWidth[a];
        }
        setMeasuredDimension(l + getPaddingLeft() + getPaddingRight(), height);
        for (int a = 0, N = getChildCount(); a < N; a++) {
            final View child = getChildAt(a);
            child.measure(
                MeasureSpec.makeMeasureSpec(tabsWidth[a], MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(tabHeight, MeasureSpec.EXACTLY));
        }

        calculateTotalSizesAfterMeasure();
    }

    public interface Tab {
        float measureTextWidth();
        default float measureTextWidth(float textSizeDp) { return measureTextWidth(); }
        default void setTextSizeDp(float textSizeDp) {}
    }



    // fills tabsTextWidth[] and return visible child count;

    private float[] tabsTextWidth;
    private float[] tabsTextWidthWithMargin;
    private int[] tabsWeight;
    private int[] tabsWidth;

    private int[] tabsLeftPos;


    private int visibleChildCount;
    private int biggestTabTextWidth;

    private void measureTabTexts(float textSizeDp) {
        final int childCount = getChildCount();
        if (tabsTextWidth == null || tabsTextWidth.length < childCount) {
            tabsTextWidth = new float[childCount];
            tabsTextWidthWithMargin = new float[childCount];
            tabsWeight = new int[childCount];
            tabsLeftPos = new int[childCount];
            tabsWidth = new int[childCount];
        }

        float maxTabWidthF = 0;
        int index = 0;

        for (int a = 0; a < childCount; a++) {
            final View child = getChildAt(a);
            if (!isViewVisible(child)) {
                tabsTextWidth[a] = -1;
                continue;
            }

            final float tabWidth;
            if (child instanceof MainTabsLayout.Tab) {
                tabWidth = ((MainTabsLayout.Tab) child).measureTextWidth(textSizeDp);
            } else {
                tabWidth = 0;
            }

            tabsTextWidth[a] = tabWidth;
            maxTabWidthF = Math.max(maxTabWidthF, tabWidth);
            index++;
        }

        biggestTabTextWidth = (int) Math.ceil(maxTabWidthF);
        visibleChildCount = index;
    }

    private void applyPassTextSize(int pass) {
        final float textSizeDp = PASS_TEXT_SIZES_DP[pass];
        for (int a = 0, N = getChildCount(); a < N; a++) {
            final View child = getChildAt(a);
            if (child instanceof MainTabsLayout.Tab) {
                ((MainTabsLayout.Tab) child).setTextSizeDp(textSizeDp);
            }
        }
    }

    @Override
    protected void setChildVisibilityFactor(View view, float factor) {
        final float s = lerp(0.7f, 1f, factor);
        view.setAlpha(factor);
        view.setScaleX(s);
        view.setScaleY(s);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        checkVisualWidth();
        if (ElasticPillGesture.enabled()) {
            setSkipDrawSelector(true);
            if (!isInLongPress) updateElasticGeometry();
        }
    }

    @Override
    protected void onItemsChanged() {
        super.onItemsChanged();
        checkVisualWidth();
        if (ElasticPillGesture.enabled()) {
            setSkipDrawSelector(true);
            if (!isInLongPress) updateElasticGeometry();
        }
    }

    private void checkVisualWidth() {
        for (int a = 0, N = getEntriesCount(); a < N; a++) {
            final ListAnimator.Entry<Holder> entry = getEntry(a);
            final float width = entry.getRectF().width();
            ((GlassTabView) entry.item.view).setVisualWidth(width);
        }
    }








    public void setTabSelected(View tab, boolean animated) {
        for (int a = 0, N = getChildCount(); a < N; a++) {
            final View child = getChildAt(a);
            if (child instanceof GlassTabView) {
                ((GlassTabView) child).setSelected(child == tab, animated);
            }
        }
        if (ElasticPillGesture.enabled()) setElasticSelectedTab(tab, animated);
    }

    /** The real selected tab is passed by MainTabsActivity, not inferred from
     *  GlassTabView's previous selection during its BoolAnimator transition. */
    public void setElasticSelectedTab(View tab, boolean animated) {
        if (!ElasticPillGesture.enabled()) return;
        final boolean changed = elasticSelectedTab != tab;
        elasticSelectedTab = tab;
        setSkipDrawSelector(true);
        // Balanced iOS-style spring: snappy travel with one subtle overshoot
        // and clean settle, without excessive gelatinous wobbling.
        selectedTabPositionX.getSpring()
                .setStiffness(340f)
                .setDampingRatio(.72f);
        if (!isInLongPress && (changed || !elasticPillInitialized)) {
            syncElasticPillToSelected(animated);
        }
    }

    private boolean elasticPillInitialized;
    private boolean elasticGlideAnimating;

    private void updateElasticGeometry() {
        final View selected = findSelectedTab();
        if (selected == null || selected.getWidth() <= 0) return;
        final float cx = getCenterX(selected);
        if (!elasticPillInitialized || Math.abs(cx - selectedTabDestinationX) > 1f) {
            // Tab positions can change with the number of tabs or device
            // rotation. Do not reset a running selection spring on every layout.
            syncElasticPillToSelected(false);
        }
    }

    private void syncElasticPillToSelected(boolean animated) {
        final View selected = findSelectedTab();
        if (selected == null || selected.getWidth() <= 0) return;
        final float cx = getCenterX(selected);
        final boolean hadPosition = elasticPillInitialized;
        selectedTabDestinationX = cx;
        if (!hadPosition || !animated) {
            selectedTabPositionX.cancel();
            selectedTabPositionOffsetX.cancel();
            elasticPressScale.cancel();
            elasticGlideAnimating = false;
            elasticPressValue = 0f;
            animatedLongSelectedViewCenterX = cx;
            animatedLongSelectedViewOffsetX = 0f;
            elasticPillInitialized = true;
        } else if (Math.abs(animatedLongSelectedViewCenterX - cx) > .5f) {
            selectedTabPositionOffsetX.cancel();
            animatedLongSelectedViewOffsetX = 0f;
            final float moveDistance = Math.abs(animatedLongSelectedViewCenterX - cx);
            // Preserve a little of the finger's release velocity, then let a
            // damped spring settle the lens into the destination slot. This
            // makes ACTION_UP visibly continuous instead of teleporting.
            selectedTabPositionX.setStartVelocity(elasticReleaseVelocityX);
            final float impulse = Math.max(1.4f, Math.min(4.2f,
                    Math.abs(elasticReleaseVelocityX) * .0035f
                    + moveDistance * .016f));
            elasticReleaseVelocityX = 0f;
            selectedTabPositionX.animateToFinalPosition(cx);
            elasticJellyBounce.setStartVelocity(impulse);
            elasticJellyBounce.animateToFinalPosition(0f);
            if (!elasticTouchStartedOnPill && !elasticDragging && moveDistance > dp(4f)) {
                elasticGlideAnimating = true;
                elasticPressScale.setStartVelocity(4.2f);
                elasticPressScale.animateToFinalPosition(1f);
            }
        } else {
            if (elasticReleaseVelocityX != 0f) {
                elasticJellyBounce.setStartVelocity(Math.max(1.4f, Math.min(3.0f,
                        Math.abs(elasticReleaseVelocityX) * .0035f)));
                elasticJellyBounce.animateToFinalPosition(0f);
            }
            elasticReleaseVelocityX = 0f;
        }
        invalidate();
    }

    private View findSelectedTab() {
        if (elasticSelectedTab != null
                && elasticSelectedTab.getParent() == this
                && elasticSelectedTab.getVisibility() == View.VISIBLE) {
            return elasticSelectedTab;
        }
        for (int a = 0, N = getChildCount(); a < N; a++) {
            final View child = getChildAt(a);
            if (child.getVisibility() != View.VISIBLE) {
                continue;
            }

            if (child instanceof GlassTabView) {
                if (((GlassTabView) child).isTabSelected()) {
                    return child;
                }
            }
        }
        return null;
    }

    private final Runnable restoreDrawSelector = () -> setSkipDrawSelector(ElasticPillGesture.enabled());

    private boolean drawCustomSelector;
    private void setSkipDrawSelector(boolean skipDrawSelector) {
        // The opt-in mode has ONE travelling selection pill, rather than
        // individually fading selected backgrounds in each tab.
        skipDrawSelector |= ElasticPillGesture.enabled();
        drawCustomSelector = skipDrawSelector;
        if (drawCustomSelector) {
            selectorPaint.setColor(Theme.multAlpha(Theme.getColor(Theme.key_glass_tabSelected, resourcesProvider), 0.09f));
        }
        for (int a = 0, N = getChildCount(); a < N; a++) {
            final View child = getChildAt(a);
            if (child.getVisibility() != View.VISIBLE) {
                continue;
            }

            if (child instanceof GlassTabView) {
                ((GlassTabView) child).setSkipDrawSelector(skipDrawSelector);
            }
        }
        invalidate();
    }







    @Override
    protected void dispatchDraw(@NonNull Canvas canvas) {
        if (ElasticPillGesture.enabled()) {
            drawElasticGlassSelector(canvas);
            if (drawRefractedTabsContent(canvas)) {
                if (drawTopDivider && MainTabsUiHelper.isMaterial3NavigationBar()) {
                    dividerPaint.setColor(Theme.getDividerColor(resourcesProvider));
                    canvas.drawLine(0, 1, getMeasuredWidth(), 1, dividerPaint);
                }
                return;
            }
        } else if (drawCustomSelector) {
            // Preserve the original long-press indicator when this optional
            // appearance feature is disabled.
            final float x = animatedLongSelectedViewCenterX + animatedLongSelectedViewOffsetX;
            final float sWidth = getInterpolatedWidthByX(x, this);
            if (MainTabsUiHelper.isMaterial3NavigationBar()) {
                MainTabsUiHelper.setMainTabSelectedIndicatorBounds(selectorRect, sWidth,
                        getHeight() - getPaddingTop() - getPaddingBottom());
                selectorRect.offset(x - sWidth / 2f, getPaddingTop());
                final float r = selectorRect.height() / 2f;
                canvas.drawRoundRect(selectorRect, r, r, selectorPaint);
            } else {
                final float sHeight = getHeight() - getPaddingTop() - getPaddingBottom();
                canvas.drawRoundRect(
                        x - sWidth / 2f, (getHeight() - sHeight) / 2f,
                        x + sWidth / 2f, (getHeight() + sHeight) / 2f,
                        sHeight / 2f, sHeight / 2f, selectorPaint);
            }
        }

        super.dispatchDraw(canvas);

        if (drawTopDivider && MainTabsUiHelper.isMaterial3NavigationBar()) {
            dividerPaint.setColor(Theme.getDividerColor(resourcesProvider));
            canvas.drawLine(0, 1, getMeasuredWidth(), 1, dividerPaint);
        }
    }

    /**
     * A large, clearly visible capsule similar to the reference: native
     * frosted/refractive surface, luminous rim, and a bridge that lengthens
     * between states. Width comes from actual child geometry (2..6 tabs).
     */
    private void drawElasticGlassSelector(Canvas canvas) {
        final View selected = findSelectedTab();
        if (selected == null || selected.getWidth() <= 0) return;
        if (!elasticPillInitialized) syncElasticPillToSelected(false);
        final float centerX = animatedLongSelectedViewCenterX + animatedLongSelectedViewOffsetX;
        final float frameDelta = Float.isNaN(elasticPrevCenterX) ? 0f : (centerX - elasticPrevCenterX);
        elasticPrevCenterX = centerX;
        elasticSmoothedDelta += (frameDelta - elasticSmoothedDelta) * .35f;
        // At rest the lens is EXACTLY the native selector. During live drag,
        // stretch responds to smoothed velocity (not distance from the old tab,
        // which would freeze an asymmetric wing at destination and snap on release).
        final float travelled = Math.abs(centerX - selectedTabDestinationX);
        if (elasticGlideAnimating) {
            if (elasticDragging) {
                elasticGlideAnimating = false;
            } else if (!selectedTabPositionX.isRunning() || travelled <= dp(3.5f)) {
                elasticGlideAnimating = false;
                elasticPressScale.animateToFinalPosition(0f);
            }
        }
        float minTabCenterX = Float.MAX_VALUE;
        float maxTabCenterX = -Float.MAX_VALUE;
        float leftNeighborX = -Float.MAX_VALUE;
        float rightNeighborX = Float.MAX_VALUE;
        for (int i = 0, n = getChildCount(); i < n; i++) {
            final View child = getChildAt(i);
            if (child != null && child.getVisibility() == View.VISIBLE) {
                final float cx = getCenterX(child);
                if (cx < minTabCenterX) minTabCenterX = cx;
                if (cx > maxTabCenterX) maxTabCenterX = cx;
                if (cx <= centerX && cx > leftNeighborX) leftNeighborX = cx;
                if (cx >= centerX && cx < rightNeighborX) rightNeighborX = cx;
            }
        }
        float interTabArch = 0f;
        if (leftNeighborX > -Float.MAX_VALUE && rightNeighborX < Float.MAX_VALUE
                && rightNeighborX - leftNeighborX > dp(8)) {
            final float frac = (centerX - leftNeighborX) / (rightNeighborX - leftNeighborX);
            interTabArch = (float) Math.sin(Math.PI * Math.max(0f, Math.min(1f, frac)));
        }

        final float extension = elasticDragging ? 0f : Math.min(dp(16), travelled * .17f);
        final float bridgeStretch = elasticDragging ? interTabArch * dp(10.0f) : 0f;
        final float absDelta = Math.max(0f, Math.abs(elasticSmoothedDelta) - dp(0.45f));
        final float velocityStretch = elasticDragging
                ? Math.min(dp(14), absDelta * 1.25f)
                : Math.min(dp(6.5f), absDelta * .42f);
        final float targetStretch = Math.min(dp(21), extension + bridgeStretch + velocityStretch);
        elasticSmoothedStretch += (targetStretch - elasticSmoothedStretch) * (elasticDragging ? .26f : .26f);
        if (Math.abs(elasticSmoothedStretch) < .02f && targetStretch == 0f) {
            elasticSmoothedStretch = 0f;
        }
        final float totalStretch = elasticSmoothedStretch;
        final float leftOverscroll = minTabCenterX < Float.MAX_VALUE ? Math.max(0f, minTabCenterX - centerX) : 0f;
        final float rightOverscroll = maxTabCenterX > -Float.MAX_VALUE ? Math.max(0f, centerX - maxTabCenterX) : 0f;
        final float edgeOverscroll = leftOverscroll + rightOverscroll;

        final float availableHeight = Math.max(1f, getHeight() - getPaddingTop() - getPaddingBottom());
        final float rawPullY = elasticDragging ? elasticPullY : 0f;
        final float deadzonedPullY = Math.signum(rawPullY) * Math.max(0f, Math.abs(rawPullY) - dp(3.0f));
        elasticSmoothedPullY += (deadzonedPullY - elasticSmoothedPullY) * (elasticDragging ? .20f : .28f);
        if (Math.abs(elasticSmoothedPullY) < .02f && deadzonedPullY == 0f) {
            elasticSmoothedPullY = 0f;
        }
        final float pullY = elasticSmoothedPullY;
        final float verticalPullSquish = Math.min(dp(4.2f), Math.abs(pullY) * .14f);
        final float pullShiftY = dp(2.2f) * (float) Math.tanh(pullY / dp(24f));
        final float glideLift = Math.min(1f, totalStretch / dp(7.0f));
        final float pressSwell = elasticPressValue * dp(7.5f) + glideLift * dp(2.4f);
        final float jellyX = elasticJellyValue * dp(3.5f) + pressSwell + verticalPullSquish * .85f;
        final float jellyY = -elasticJellyValue * dp(1.6f) + pressSwell * .88f
                + Math.min(dp(2.6f), edgeOverscroll * .18f)
                - verticalPullSquish
                - Math.min(dp(2.2f), totalStretch * .12f);
        final float verticalCompression = Math.min(dp(1.8f), totalStretch * .10f);
        final float height = Math.max(1f, availableHeight - verticalCompression + jellyY);
        final float widthAtX = getInterpolatedWidthByX(centerX, this);
        final float baseWidth = Math.max(dp(28), widthAtX + jellyX);
        final float cy = getPaddingTop() + availableHeight * .5f + pullShiftY;

        final float deadzonedSignedDelta = Math.signum(elasticSmoothedDelta) * absDelta;
        final float targetDir = elasticDragging
                ? Math.max(-1f, Math.min(1f, deadzonedSignedDelta / dp(3.8f)))
                : 0f;
        elasticSmoothedDir += (targetDir - elasticSmoothedDir) * .20f;
        if (Math.abs(elasticSmoothedDir) < .01f && targetDir == 0f) {
            elasticSmoothedDir = 0f;
        }
        final float direction = elasticDragging
                ? elasticSmoothedDir
                : (Math.abs(elasticSmoothedDelta) > .25f
                        ? Math.signum(elasticSmoothedDelta)
                        : Math.signum(selectedTabDestinationX - centerX));
        final float symStretch = Math.min(totalStretch, bridgeStretch);
        final float dirStretch = Math.max(0f, totalStretch - symStretch);
        final float trailingStretch = direction == 0f ? dirStretch * .5f : dirStretch * .78f;
        final float leadingStretch = dirStretch - trailingStretch;
        final float leadingPullSquish = elasticDragging
                ? (1f - interTabArch * .50f) * Math.min(dp(5.2f), absDelta * .52f) * Math.abs(direction)
                : 0f;
        final float leftStretch = symStretch * .5f + (elasticDragging
                ? (dirStretch * (.5f + .28f * direction) - (direction < 0f ? leadingPullSquish : 0f))
                : (direction < 0f ? leadingStretch : trailingStretch));
        final float rightStretch = symStretch * .5f + (elasticDragging
                ? (dirStretch * (.5f - .28f * direction) - (direction > 0f ? leadingPullSquish : 0f))
                : (direction > 0f ? leadingStretch : trailingStretch));

        // Softly absorb outer edge approach and compress horizontally when pushed against the left/right edges.
        final float edge = dp(0.8f);
        final float rawLeft = centerX - baseWidth * .5f;
        final float rawRight = centerX + baseWidth * .5f;
        final float leftHeadroom = Math.max(0f, rawLeft - edge);
        final float rightHeadroom = Math.max(0f, (getWidth() - edge) - rawRight);
        final float dampedLeftStretch = leftStretch >= 0f ? Math.min(leftStretch, leftHeadroom) : leftStretch;
        final float dampedRightStretch = rightStretch >= 0f ? Math.min(rightStretch, rightHeadroom) : rightStretch;
        final float leftSquish = leftOverscroll * 1.25f;
        final float rightSquish = rightOverscroll * 1.25f;
        final float clampedLeft = rawLeft < edge
                ? edge - (edge - rawLeft) * dp(2.2f) / ((edge - rawLeft) + dp(2.8f))
                : rawLeft - dampedLeftStretch;
        final float clampedRight = rawRight > getWidth() - edge
                ? (getWidth() - edge) + (rawRight - (getWidth() - edge)) * dp(2.2f)
                        / ((rawRight - (getWidth() - edge)) + dp(2.8f))
                : rawRight + dampedRightStretch;
        final float minPillWidth = Math.min(baseWidth, dp(48));
        final float finalLeft = rightSquish > 0f
                ? Math.min(clampedRight - minPillWidth, clampedLeft + rightSquish)
                : clampedLeft;
        final float finalRight = leftSquish > 0f
                ? Math.max(clampedLeft + minPillWidth, clampedRight - leftSquish)
                : clampedRight;

        selectorRect.set(
                finalLeft,
                cy - height * .5f,
                finalRight,
                cy + height * .5f);
        final float motionDir = leftOverscroll > .1f ? -1f : (rightOverscroll > .1f ? 1f : direction);
        final float motionMag = Math.min(1f, Math.max(totalStretch / dp(16), edgeOverscroll / dp(11)));
        elasticGlassPill.draw(canvas, selectorRect, elasticPillGlass,
                Theme.isCurrentThemeDark(), motionDir * motionMag,
                (elasticTouchStartedOnPill && !elasticMenuShownDuringTouch)
                        || ((elasticDragging || elasticGlideAnimating || elasticPressValue > 0.05f) && !elasticMenuShownDuringTouch));
        updateElasticTabLensScale(centerX, widthAtX, totalStretch);
        if (!elasticDragging && !elasticTouchStartedOnPill && !elasticGlideAnimating
                && !selectedTabPositionX.isRunning() && !elasticPressScale.isRunning()
                && (elasticGlassPill.getOpticalPress() > 0.01f
                        || Math.abs(elasticGlassPill.getLightPhase()) > 0.01f
                        || elasticSmoothedStretch > 0.05f)) {
            postInvalidateOnAnimation();
        }
    }

    private void updateElasticTabLensScale(float lensCenterX, float lensWidth, float totalStretch) {
        // Never shrink non-selected or departing tabs downward (< 1f) — deactivation is
        // conveyed smoothly via outline crossfade/tint without twitching the dock on taps.
        final boolean interactiveLens = elasticDragging || elasticGlideAnimating || elasticPressValue > .02f;
        final float boost = interactiveLens
                ? Math.max(0f, elasticPressValue * .042f + Math.min(.022f, totalStretch / Math.max(1f, dp(10)) * .022f))
                : 0f;
        final float span = Math.max(dp(28), lensWidth * .86f);
        for (int i = 0, n = getChildCount(); i < n; i++) {
            final View child = getChildAt(i);
            if (!(child instanceof GlassTabView) || child.getVisibility() != View.VISIBLE) {
                continue;
            }
            final float dist = Math.abs(getCenterX(child) - lensCenterX);
            final float overlap = Math.max(0f, Math.min(1f, 1f - dist / span));
            final float targetScale = 1f + overlap * boost;
            if (Math.abs(child.getScaleX() - targetScale) > .001f) {
                child.setScaleX(targetScale);
                child.setScaleY(targetScale);
            }
        }
    }

        private boolean drawTopDivider = true;

    public void setDrawTopDivider(boolean value) {
        if (drawTopDivider != value) {
            drawTopDivider = value;
            invalidate();
        }
    }

    final Paint dividerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    final Paint selectorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final android.graphics.RectF selectorRect = new android.graphics.RectF();
    private final Path pillClipPath = new Path();
    private RenderNode tabsContentNode;
    private RenderNode pillRefractNode;
    private RuntimeShader pillContentShader;
    private boolean pillContentShaderFailed;
    private float lastPillShaderCx = Float.NaN, lastPillShaderCy = Float.NaN;
    private float lastPillShaderW = -1f, lastPillShaderH = -1f;
    private float lastPillShaderPress = -1f, lastPillShaderMotion = Float.NaN;

    private boolean drawRefractedTabsContent(Canvas canvas) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                || LiteMode.isPowerSaverApplied()
                || pillContentShaderFailed
                || !canvas.isHardwareAccelerated()
                || getWidth() <= 0 || getHeight() <= 0
                || selectorRect.width() < 2f || selectorRect.height() < 2f) {
            return false;
        }
        try {
            if (tabsContentNode == null) {
                tabsContentNode = new RenderNode("ElasticTabsContent");
                tabsContentNode.setClipToBounds(false);
                pillRefractNode = new RenderNode("ElasticPillRefract");
                pillRefractNode.setClipToBounds(false);
                final String code = AndroidUtilities.readRes(R.raw.liquid_glass_pill_content_shader);
                pillContentShader = new RuntimeShader(code);
            }
            final int w = getWidth();
            final int h = getHeight();
            tabsContentNode.setPosition(0, 0, w, h);
            final Canvas contentCanvas = tabsContentNode.beginRecording();
            super.dispatchDraw(contentCanvas);
            tabsContentNode.endRecording();

            final float cx = selectorRect.centerX();
            final float cy = selectorRect.centerY();
            final float hw = selectorRect.width() * .5f;
            final float hh = selectorRect.height() * .5f;
            final float press = Math.max(elasticGlassPill.getOpticalPress(), elasticPressValue);
            final float motion = elasticGlassPill.getLightPhase();
            if (Math.abs(lastPillShaderCx - cx) > .15f
                    || Math.abs(lastPillShaderCy - cy) > .15f
                    || Math.abs(lastPillShaderW - hw) > .15f
                    || Math.abs(lastPillShaderH - hh) > .15f
                    || Math.abs(lastPillShaderPress - press) > .01f
                    || Math.abs(lastPillShaderMotion - motion) > .01f
                    || Float.isNaN(lastPillShaderCx)) {
                lastPillShaderCx = cx;
                lastPillShaderCy = cy;
                lastPillShaderW = hw;
                lastPillShaderH = hh;
                lastPillShaderPress = press;
                lastPillShaderMotion = motion;
                pillContentShader.setFloatUniform("center", cx, cy);
                pillContentShader.setFloatUniform("size", hw, hh);
                pillContentShader.setFloatUniform("radius", hh);
                pillContentShader.setFloatUniform("thickness", Math.min(dp(24), hh * .90f));
                pillContentShader.setFloatUniform("glass_press", press);
                pillContentShader.setFloatUniform("glass_motion", motion);
                pillRefractNode.setRenderEffect(
                        RenderEffect.createRuntimeShaderEffect(pillContentShader, "img"));
            }

            pillRefractNode.setPosition(0, 0, w, h);
            final Canvas refractCanvas = pillRefractNode.beginRecording();
            refractCanvas.drawRenderNode(tabsContentNode);
            pillRefractNode.endRecording();

            final float r = selectorRect.height() * .5f;
            pillClipPath.rewind();
            pillClipPath.addRoundRect(selectorRect, r, r, Path.Direction.CW);

            canvas.save();
            canvas.clipOutPath(pillClipPath);
            canvas.drawRenderNode(tabsContentNode);
            canvas.restore();

            canvas.save();
            canvas.clipPath(pillClipPath);
            canvas.drawRenderNode(pillRefractNode);
            canvas.restore();
            return true;
        } catch (Throwable ignore) {
            pillContentShaderFailed = true;
            return false;
        }
    }

    private SensorManager pillTiltManager;
    private Sensor pillTiltSensor;
    private float pillTiltSmoothed;
    private float pillTiltQuantized;
    private boolean pillTiltRegistered;

    private final SensorEventListener pillTiltListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            if (!ElasticPillGesture.enabled() || LiteMode.isPowerSaverApplied()) {
                return;
            }
            final float x = event.values[0];
            final float y = event.values[1];
            final float target = Math.max(-.30f, Math.min(.30f, x * .032f - y * .014f));
            pillTiltSmoothed += (target - pillTiltSmoothed) * .14f;
            final float quantized = Math.round(pillTiltSmoothed * 40f) / 40f;
            if (Math.abs(quantized - pillTiltQuantized) >= .024f) {
                pillTiltQuantized = quantized;
                elasticGlassPill.setTiltOffset(quantized);
                invalidate();
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {}
    };

    private void updatePillTiltSensor(boolean active) {
        final boolean shouldListen = active
                && ElasticPillGesture.enabled()
                && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && !LiteMode.isPowerSaverApplied()
                && getWindowVisibility() == View.VISIBLE;
        if (shouldListen == pillTiltRegistered) {
            return;
        }
        if (shouldListen) {
            if (pillTiltManager == null) {
                pillTiltManager = (SensorManager) getContext().getSystemService(Context.SENSOR_SERVICE);
                if (pillTiltManager != null) {
                    pillTiltSensor = pillTiltManager.getDefaultSensor(Sensor.TYPE_GRAVITY);
                    if (pillTiltSensor == null) {
                        pillTiltSensor = pillTiltManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
                    }
                }
            }
            if (pillTiltManager != null && pillTiltSensor != null) {
                pillTiltManager.registerListener(pillTiltListener, pillTiltSensor, SensorManager.SENSOR_DELAY_UI);
                pillTiltRegistered = true;
            }
        } else {
            if (pillTiltManager != null) {
                pillTiltManager.unregisterListener(pillTiltListener);
            }
            pillTiltRegistered = false;
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        updatePillTiltSensor(true);
    }

    @Override
    protected void onDetachedFromWindow() {
        updatePillTiltSensor(false);
        super.onDetachedFromWindow();
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        updatePillTiltSensor(visibility == View.VISIBLE);
    }
    final SpringAnimation scaleX = new SpringAnimation(this, DynamicAnimation.SCALE_X, 1f);
    final SpringAnimation scaleY = new SpringAnimation(this, DynamicAnimation.SCALE_Y, 1f);

    final SpringAnimation selectedTabPositionOffsetX = new SpringAnimation(this, new FloatPropertyCompat<MainTabsLayout>("selectedTabPositionOffsetX") {
        @Override
        public float getValue(MainTabsLayout object) {
            return object.animatedLongSelectedViewOffsetX;
        }

        @Override
        public void setValue(MainTabsLayout object, float value) {
            object.animatedLongSelectedViewOffsetX = value;
            object.invalidate();
        }
    });
    final SpringAnimation selectedTabPositionX = new SpringAnimation(this, new FloatPropertyCompat<MainTabsLayout>("selectedTabPositionX") {
        @Override
        public float getValue(MainTabsLayout object) {
            return object.animatedLongSelectedViewCenterX;
        }

        @Override
        public void setValue(MainTabsLayout object, float value) {
            object.animatedLongSelectedViewCenterX = value;
            object.invalidate();
        }
    });

    {
        selectedTabPositionOffsetX.setSpring(new SpringForce(1)
            .setStiffness(SpringForce.STIFFNESS_MEDIUM)
            .setDampingRatio(SpringForce.DAMPING_RATIO_LOW_BOUNCY));
        scaleX.setSpring(new SpringForce(1f)
            .setStiffness(250)
            .setDampingRatio(0.25f));
        scaleY.setSpring(new SpringForce(1f)
            .setStiffness(250)
            .setDampingRatio(0.25f));
        selectedTabPositionX.setSpring(new SpringForce(1f)
            .setStiffness(SpringForce.STIFFNESS_MEDIUM)
            .setDampingRatio(SpringForce.DAMPING_RATIO_LOW_BOUNCY));
    }

    private float animatedLongSelectedViewCenterX;
    private float animatedLongSelectedViewOffsetX;
    private float elasticPrevCenterX = Float.NaN;
    private float elasticSmoothedDelta;
    private float elasticSmoothedStretch;
    private float elasticJellyValue;
    private float elasticPressValue;
    private float elasticPullY;
    private float elasticSmoothedPullY;
    private float elasticSmoothedDir;

    final SpringAnimation elasticJellyBounce = new SpringAnimation(this, new FloatPropertyCompat<MainTabsLayout>("elasticJellyBounce") {
        @Override
        public float getValue(MainTabsLayout object) {
            return object.elasticJellyValue;
        }

        @Override
        public void setValue(MainTabsLayout object, float value) {
            object.elasticJellyValue = value;
            object.invalidate();
        }
    }, 0f);

    final SpringAnimation elasticPressScale = new SpringAnimation(this, new FloatPropertyCompat<MainTabsLayout>("elasticPressScale") {
        @Override
        public float getValue(MainTabsLayout object) {
            return object.elasticPressValue;
        }

        @Override
        public void setValue(MainTabsLayout object, float value) {
            object.elasticPressValue = value;
            object.invalidate();
        }
    }, 0f);

    {
        elasticJellyBounce.setMinimumVisibleChange(0.002f);
        elasticJellyBounce.setSpring(new SpringForce(0f)
                .setStiffness(300f)
                .setDampingRatio(0.62f));
        elasticPressScale.setMinimumVisibleChange(0.002f);
        elasticPressScale.setSpring(new SpringForce(0f)
                .setStiffness(360f)
                .setDampingRatio(0.65f));
        selectedTabPositionX.addEndListener((animation, canceled, value, velocity) -> {
            if (!canceled && elasticGlideAnimating) {
                elasticGlideAnimating = false;
                elasticPressScale.animateToFinalPosition(0f);
                invalidate();
            }
        });
    }

    private boolean isInLongPress;
    private float lastLongSelectedViewCenterX;
    private float lastLongSelectedViewWidth;
    private View lastLongSelectedView;




    public static View findChildUnder(ViewGroup parent, float x, float y) {
        for (int i = parent.getChildCount() - 1; i >= 0; i--) {
            View child = parent.getChildAt(i);

            if (child.getVisibility() != View.VISIBLE) continue;

            if (x >= child.getLeft() && x <= child.getRight()
                    && y >= child.getTop() && y <= child.getBottom()) {
                return child;
            }
        }
        return null;
    }

    private void checkLongMove(float x_, float y, boolean start, boolean end) {
        final float x = clampXToChildrenCenters(x_, this);
        final View found = findNearestVisibleChildByX(x, this);
        if (start && ElasticPillGesture.enabled()) {
            // Start directly under the finger. Upstream's offset spring was
            // originally designed for a *hidden* 9%-alpha selector; reusing
            // it makes a real glass pill appear to jump when first held.
            selectedTabPositionX.cancel();
            selectedTabPositionOffsetX.cancel();
            animatedLongSelectedViewOffsetX = 0f;
            animatedLongSelectedViewCenterX = x;
            invalidate();
        } else if (start) {
            View selected = findSelectedTab();
            if (selected != null) {
                animatedLongSelectedViewCenterX = selected.getX() + selected.getWidth() / 2f;
                animatedLongSelectedViewOffsetX = animatedLongSelectedViewCenterX - x;
                selectedTabPositionOffsetX.animateToFinalPosition(0);
                if (!ElasticPillGesture.enabled() && selected != found && found != null) {
                    found.performClick();
                }
            }
            selectedTabPositionX.cancel();
        }

        if (!end) {
            animatedLongSelectedViewCenterX = x;
            invalidate();
        }

        if (found != null) {
            lastLongSelectedView = found;
            // Do not navigate or animate the tab's icon while the pill is held.
            // Commit the nearest tab only on ACTION_UP via performClick().
            if (!ElasticPillGesture.enabled()) setTabSelected(found, true);

            if (end) {
                final float vw = found.getWidth();
                final float cx = found.getX() + vw / 2f;
                if (lastLongSelectedViewWidth != vw || lastLongSelectedViewCenterX != cx) {
                    selectedTabPositionX.animateToFinalPosition(cx);
                }
            }
        }
    }

    private final Set<View> tabsWithIgnoreClick = new HashSet<>();
    public void addTabToIgnoreClick(View v) {
        tabsWithIgnoreClick.add(v);
    }

    private final BoolAnimator animatorIsScaled = new BoolAnimator(0, (a, factor, c, g) -> {
        setScaleX(lerp(1, 1.019f, factor));
        setScaleY(lerp(1, 1.019f, factor));
    }, CubicBezierInterpolator.EASE_OUT_QUINT, 380);

    private final ClickHelper clickHelper = new ClickHelper(new ClickHelper.Delegate() {
        @Override
        public boolean needClickAt(View view, float x, float y) {
            lastLongSelectedView = null;
            final View found = findChildUnder(MainTabsLayout.this, x, y);
            // ExFullGram explicitly ignores upstream long-press click logic
            // for these tabs. Opt-in drag needs to recognise them again.
            return found != null && (ElasticPillGesture.enabled()
                    || !tabsWithIgnoreClick.contains(found));
        }

        @Override
        public void onClickAt(View view, float x, float y) {
        }

        @Override
        public boolean needLongPress(float x, float y) {
            return true;
        }

        @Override
        public boolean needCancelTouchBySlopMove() {
            return false;
        }


        @Override
        public boolean onLongPressRequestedAt(View view, float x, float y) {
            checkPivot(view, x, y);
            isInLongPress = true;
            AndroidUtilities.cancelRunOnUIThread(restoreDrawSelector);
            setSkipDrawSelector(true);
            checkLongMove(x, y, true, false);
            invalidate();
            longTouchStart();
            return true;
        }

        @Override
        public void onLongPressMove(View view, MotionEvent e, float x, float y, float startX, float startY) {
            checkPivot(view, x, y);
            checkLongMove(x, y, false, false);
            invalidate();
        }

        @Override
        public long getLongPressDuration() {
            return ClickHelper.Delegate.super.getLongPressDuration() * 750 / 1000;
        }

        @Override
        public void onLongPressFinish(View view, float x, float y) {
            checkPivot(view, x, y);
            checkLongMove(x, y, false, true);
            isInLongPress = false;
            AndroidUtilities.runOnUIThread(restoreDrawSelector, 450);
            if (lastLongSelectedView != null) {
                lastLongSelectedView.performClick();
            }
            lastLongSelectedView = null;
            invalidate();
            longTouchEnd();
        }

        @Override
        public void onLongPressCancelled(View view, float x, float y) {
            checkPivot(view, x, y);
            if (ElasticPillGesture.enabled()) {
                // The page never changed during drag, so a cancelled gesture
                // must spring to the ACTUAL selected tab, not the last hover.
                isInLongPress = false;
                syncElasticPillToSelected(true);
            } else {
                checkLongMove(x, y, false, true);
                isInLongPress = false;
            }
            AndroidUtilities.runOnUIThread(restoreDrawSelector, 450);
            lastLongSelectedView = null;
            invalidate();
            longTouchEnd();
        }

        private void longTouchStart() {
            if (!ElasticPillGesture.enabled()) animatorIsScaled.setValue(true, true);

            /*
            if (!scaleX.isRunning()) {
                scaleX.setStartVelocity(-0.45f);
                scaleY.setStartVelocity(-0.45f);
            }
            scaleX.animateToFinalPosition(1.012f);
            scaleY.animateToFinalPosition(1.012f);
            */
        }

        private void longTouchEnd() {
            if (!ElasticPillGesture.enabled()) animatorIsScaled.setValue(false, true);

            /*
            if (!scaleX.isRunning()) {
                scaleX.setStartVelocity(0.25f);
                scaleY.setStartVelocity(0.25f);
            }
            scaleX.animateToFinalPosition(1f);
            scaleY.animateToFinalPosition(1f);
            */
        }
    });

    @Override
    public void setScaleY(float scaleY) {
        super.setScaleY(scaleY);
        checkLayerType();
    }

    @Override
    public void setScaleX(float scaleX) {
        super.setScaleX(scaleX);
        checkLayerType();
    }

    private void checkLayerType() {
        final int layerType = Math.abs(getScaleX() - 1f) < 0.0001f && Math.abs(getScaleY() - 1f) < 0.0001f ?
            View.LAYER_TYPE_NONE : View.LAYER_TYPE_HARDWARE;

        if (getLayerType() != layerType) {
            setLayerType(layerType, null);
            invalidate();
        }
    }


    private void checkPivot(View view, float x, float y) {
        float w = view.getWidth();
        float h = view.getHeight();

        if (w <= 0f || h <= 0f) {
            return;
        }

        float cx = w * 0.5f;
        float cy = h * 0.5f;

        float dx = x - cx;
        float dy = y - cy;

        float halfW = w * 0.5f;
        float halfH = h * 0.5f;

        float nx = dx / halfW;
        float ny = dy / halfH;

        float r = (float) Math.sqrt(nx * nx + ny * ny);

        float pivotX;
        float pivotY;

        if (r > 1e-4f) {
            float mappedR = 1.5f * r / (r + 0.5f);

            float scale = mappedR / r;
            pivotX = cx + dx * scale;
            pivotY = cy + dy * scale;
        } else {
            pivotX = cx;
            pivotY = cy;
        }

        pivotX = lerp(cx, pivotX, 1f);
        pivotY = lerp(cy, pivotY, 3f);

        view.setPivotX(pivotX);
        view.setPivotY(pivotY);
    }

    /**
     * The tab's own stationary long-press listener opened its menu.
     * This finger is no longer eligible to start an elastic drag.
     */
    public void onElasticTabLongPressed() {
        if (!ElasticPillGesture.enabled()) return;
        elasticMenuShownDuringTouch = true;
        elasticTouchStartedOnPill = false;
        elasticPressScale.animateToFinalPosition(0f);
        if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
    }

    /**
     * Only touching INSIDE the visible active pill can begin a drag.
     * Normal taps and stationary holds remain owned by original tab views.
     */
    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (!ElasticPillGesture.enabled()) {
            clickHelper.onTouchEvent(this, ev);
            return super.dispatchTouchEvent(ev);
        }

        final int action = ev.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            elasticTouchTab = findChildUnder(this, ev.getX(), ev.getY());
            elasticTouchPointer = ev.getPointerId(0);
            elasticDownX = ev.getX();
            elasticDownY = ev.getY();
            elasticPullY = 0f;
            elasticSmoothedPullY = 0f;
            elasticSmoothedDir = 0f;
            elasticDragging = false;
            elasticMenuShownDuringTouch = false;
            elasticReleaseVelocityX = 0f;
            if (elasticVelocityTracker != null) elasticVelocityTracker.recycle();
            elasticVelocityTracker = VelocityTracker.obtain();
            elasticVelocityTracker.addMovement(ev);
            // Do not treat every tab rectangle as a draggable surface.
            elasticTouchStartedOnPill = elasticTouchTab instanceof GlassTabView
                    && findSelectedTab() != null
                    && selectorRect.contains(elasticDownX, elasticDownY);
            if (elasticTouchStartedOnPill) {
                elasticGlideAnimating = false;
                elasticPressScale.animateToFinalPosition(1f);
            }
            if (elasticTouchStartedOnPill) invalidate();
            // Preserve the finger's offset from the visible pill, so the
            // glass does not jump when the user begins dragging.
            elasticTouchOffsetX = animatedLongSelectedViewCenterX - elasticDownX;
            if (elasticTouchTab instanceof GlassTabView && getParent() != null) {
                // A touch on a navbar button never swipes the page.
                getParent().requestDisallowInterceptTouchEvent(true);
            }
            return super.dispatchTouchEvent(ev);
        }

        if (elasticVelocityTracker != null) {
            elasticVelocityTracker.addMovement(ev);
        }

        if (!elasticTouchStartedOnPill || elasticMenuShownDuringTouch) {
            // Inactive tab buttons remain ordinary tap/long-press controls.
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                finishElasticTouch(false);
            }
            return super.dispatchTouchEvent(ev);
        }
        if (action == MotionEvent.ACTION_POINTER_DOWN) {
            finishElasticTouch(false);
            MotionEvent cancel = MotionEvent.obtain(ev);
            cancel.setAction(MotionEvent.ACTION_CANCEL);
            final boolean handled = super.dispatchTouchEvent(cancel);
            cancel.recycle();
            return handled;
        }
        final int index = ev.findPointerIndex(elasticTouchPointer);
        if (index < 0) {
            finishElasticTouch(false);
            return true;
        }

        if (action == MotionEvent.ACTION_MOVE) {
            final float x = ev.getX(index);
            final float y = ev.getY(index);
            final int slop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
            // Add tolerance for tiny finger movement on a stationary hold.
            if (!elasticDragging && !elasticMenuShownDuringTouch
                    && Math.abs(x - elasticDownX) > Math.max(dp(7), slop * 1.25f)
                    && Math.abs(x - elasticDownX) >= Math.abs(y - elasticDownY) * .90f) {
                elasticDragging = true;
                isInLongPress = true;
                selectedTabPositionX.cancel();
                selectedTabPositionOffsetX.cancel();
                animatedLongSelectedViewOffsetX = 0f;
                MotionEvent cancel = MotionEvent.obtain(ev);
                cancel.setAction(MotionEvent.ACTION_CANCEL);
                super.dispatchTouchEvent(cancel);
                cancel.recycle();
            }
            if (elasticDragging) {
                elasticPullY = y - elasticDownY;
                animatedLongSelectedViewCenterX = clampXToChildrenCenters(x + elasticTouchOffsetX, this);
                updateElasticSelectionPreview(animatedLongSelectedViewCenterX);
                invalidate();
                return true;
            }
        } else if (action == MotionEvent.ACTION_UP) {
            if (elasticDragging) {
                final View hovered = findNearestVisibleChildByX(
                        animatedLongSelectedViewCenterX, this);
                if (elasticVelocityTracker != null) {
                    elasticVelocityTracker.computeCurrentVelocity(1000);
                    final float rawVelocity = elasticVelocityTracker.getXVelocity(elasticTouchPointer);
                    final float maxVelocity = dp(1400);
                    elasticReleaseVelocityX = Math.max(-maxVelocity, Math.min(maxVelocity, rawVelocity)) * .38f;
                }
                finishElasticTouch(true, false);
                if (hovered instanceof GlassTabView) {
                    final boolean sameSelection = elasticSelectedTab == hovered;
                    hovered.performClick();
                    clearElasticSelectionPreview();
                    if (sameSelection) {
                        // Releasing inside the already-selected slot still has
                        // to settle from the finger position back to its center.
                        syncElasticPillToSelected(true);
                    } else if (elasticSelectedTab != hovered) {
                        // The child click was rejected (for example by an
                        // in-flight ViewPager touch). Return to the real page.
                        elasticReleaseVelocityX = 0f;
                        syncElasticPillToSelected(true);
                    }
                } else {
                    clearElasticSelectionPreview();
                    elasticReleaseVelocityX = 0f;
                    syncElasticPillToSelected(true);
                }
                return true;
            }
            finishElasticTouch(false);
            return super.dispatchTouchEvent(ev);
        } else if (action == MotionEvent.ACTION_CANCEL) {
            final boolean dragging = elasticDragging;
            finishElasticTouch(false);
            if (dragging) return true;
        }
        return super.dispatchTouchEvent(ev);
    }

    private void finishElasticTouch(boolean releasedOnTab) {
        finishElasticTouch(releasedOnTab, true);
    }

    private void finishElasticTouch(boolean releasedOnTab, boolean clearPreview) {
        final boolean hadDrag = elasticDragging;
        final boolean wasOnPill = elasticTouchStartedOnPill;
        elasticDragging = false;
        elasticPullY = 0f;
        elasticSmoothedPullY = 0f;
        elasticSmoothedDir = 0f;
        elasticTouchStartedOnPill = false;
        elasticGlideAnimating = false;
        elasticMenuShownDuringTouch = false;
        elasticPressScale.animateToFinalPosition(0f);
        elasticTouchTab = null;
        elasticTouchPointer = -1;
        if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
        if (clearPreview) {
            clearElasticSelectionPreview();
        }
        if (elasticVelocityTracker != null) {
            elasticVelocityTracker.recycle();
            elasticVelocityTracker = null;
        }
        if (hadDrag) {
            isInLongPress = false;
            if (!releasedOnTab) {
                elasticReleaseVelocityX = 0f;
                syncElasticPillToSelected(true);
            } else {
                // Soft iOS droplet settle even when releasing carefully on target tab.
                elasticJellyBounce.setStartVelocity(1.35f);
                elasticJellyBounce.animateToFinalPosition(0f);
            }
        } else {
            elasticReleaseVelocityX = 0f;
            if (wasOnPill) {
                // Releasing a tap or hold directly on the active tab still
                // settles with a subtle harmonious jelly pulse instead of snapping.
                elasticJellyBounce.setStartVelocity(1.5f);
                elasticJellyBounce.animateToFinalPosition(0f);
            }
        }
        invalidate();
    }

    /**
     * While dragging, only preview the visual selection. The actual ViewPager
     * page is still committed on ACTION_UP, but icon/text emphasis follows the
     * liquid lens continuously instead of leaving blue letters outside it.
     */
    private void updateElasticSelectionPreview(float x) {
        for (int i = 0, n = getChildCount(); i < n; i++) {
            final View child = getChildAt(i);
            if (!(child instanceof GlassTabView) || child.getVisibility() != View.VISIBLE) continue;

            final float center = getCenterX(child);
            float nearestSpacing = Float.MAX_VALUE;
            for (int j = 0; j < n; j++) {
                if (i == j) continue;
                final View other = getChildAt(j);
                if (other == null || other.getVisibility() != View.VISIBLE) continue;
                nearestSpacing = Math.min(nearestSpacing, Math.abs(center - getCenterX(other)));
            }
            if (nearestSpacing == Float.MAX_VALUE || nearestSpacing < 1f) {
                nearestSpacing = Math.max(1f, child.getWidth());
            }
            final float linear = Math.max(0f, Math.min(1f,
                    1f - Math.abs(center - x) / (nearestSpacing * .78f)));
            final float factor = linear * linear * (3f - 2f * linear);
            ((GlassTabView) child).setGestureSelectedOverride(factor, true);
        }
    }

    private void clearElasticSelectionPreview() {
        for (int i = 0, n = getChildCount(); i < n; i++) {
            final View child = getChildAt(i);
            if (child instanceof GlassTabView) {
                ((GlassTabView) child).setGestureSelectedOverride(0f, false);
            }
        }
    }


    private static float clampXToChildrenCenters(float x, ViewGroup parent) {
        if (parent == null || parent.getChildCount() == 0) {
            return x;
        }

        float min = Float.MAX_VALUE;
        float max = -Float.MAX_VALUE;
        boolean found = false;

        for (int i = 0; i < parent.getChildCount(); i++) {
            View view = parent.getChildAt(i);
            if (view == null || view.getVisibility() != View.VISIBLE) {
                continue;
            }

            float centerX = getCenterX(view);

            if (centerX < min) min = centerX;
            if (centerX > max) max = centerX;

            found = true;
        }

        if (!found) {
            return x;
        }

        if (ElasticPillGesture.enabled()) {
            // Soft iOS-like edge rubber-banding when pulling slightly past first/last tab.
            final float over = dp(14);
            final float soft = dp(32);
            if (x < min) return min - over * (float) Math.tanh((min - x) / soft);
            if (x > max) return max + over * (float) Math.tanh((x - max) / soft);
            return x;
        }
        if (x < min) return min;
        if (x > max) return max;
        return x;
    }

    @Nullable
    private static View findNearestVisibleChildByX(float x, ViewGroup parent) {
        if (parent == null || parent.getChildCount() == 0) {
            return null;
        }

        View nearest = null;
        float nearestDistance = Float.MAX_VALUE;

        for (int i = 0; i < parent.getChildCount(); i++) {
            View view = parent.getChildAt(i);
            if (view == null || view.getVisibility() != View.VISIBLE) {
                continue;
            }

            float centerX = getCenterX(view);
            float distance = Math.abs(centerX - x);

            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = view;
            }
        }

        return nearest;
    }

    private static float getInterpolatedWidthByX(float x, ViewGroup parent) {
        if (parent == null || parent.getChildCount() == 0) {
            return 0f;
        }

        View left = null;
        View right = null;

        for (int i = 0; i < parent.getChildCount(); i++) {
            View view = parent.getChildAt(i);
            if (view == null || view.getVisibility() != View.VISIBLE) {
                continue;
            }

            float centerX = getCenterX(view);

            if (centerX <= x && (left == null || centerX > getCenterX(left))) {
                left = view;
            }

            if (centerX >= x && (right == null || centerX < getCenterX(right))) {
                right = view;
            }
        }

        if (left == null && right == null) {
            return 0f;
        }

        if (left == null) {
            return getSelectorWidth(right);
        }

        if (right == null) {
            return getSelectorWidth(left);
        }

        float leftX = getCenterX(left);
        float rightX = getCenterX(right);

        if (left == right || leftX == rightX) {
            return getSelectorWidth(left);
        }

        float ratio = (x - leftX) / (rightX - leftX);
        return lerp(getSelectorWidth(left), getSelectorWidth(right), ratio);
    }

    private static float getSelectorWidth(View v) {
        if (v instanceof GlassTabView) {
            return ((GlassTabView) v).getSelectorVisualWidth();
        }
        return v.getWidth();
    }

    private static float getCenterX(View v) {
        return v.getX() + getSelectorWidth(v) * 0.5f;
    }
}
