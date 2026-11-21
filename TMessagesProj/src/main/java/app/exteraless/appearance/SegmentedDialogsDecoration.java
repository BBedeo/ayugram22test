package app.exteraless.appearance;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.DialogCell;

/**
 * Material You segmented card decoration for chat lists (DialogsActivity).
 * Groups pinned chats and regular chats into distinct rounded card clusters
 * with 2dp segment gaps inside a cluster and a 10dp cluster break between
 * pinned and regular chats.
 */
public class SegmentedDialogsDecoration extends RecyclerView.ItemDecoration {

    private final Theme.ResourcesProvider resourcesProvider;
    private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint cardPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pinnedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF cardRect = new RectF();
    private final Path cardPath = new Path();
    private final float[] radii = new float[8];

    public SegmentedDialogsDecoration(Theme.ResourcesProvider resourcesProvider) {
        this.resourcesProvider = resourcesProvider;
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(dp(0.5f));
    }

    @Override
    public void getItemOffsets(
            @NonNull Rect outRect,
            @NonNull View view,
            @NonNull RecyclerView parent,
            @NonNull RecyclerView.State state
    ) {
        if (!AppearanceConfig.isSegmentedDialogsEnabled() || !(view instanceof DialogCell)) {
            return;
        }
        final int marginH = dp(12);
        outRect.left = marginH;
        outRect.right = marginH;

        final RecyclerView.ViewHolder vh = parent.getChildViewHolder(view);
        final RecyclerView.Adapter<?> adapter = parent.getAdapter();
        if (vh == null || adapter == null) {
            return;
        }
        final int pos = vh.getAdapterPosition();
        if (pos == RecyclerView.NO_POSITION) {
            return;
        }
        final boolean currPinned = ((DialogCell) view).getIsPinned();
        final RecyclerView.ViewHolder prevVh = pos > 0 ? parent.findViewHolderForAdapterPosition(pos - 1) : null;
        if (prevVh != null && prevVh.itemView instanceof DialogCell) {
            final boolean prevPinned = ((DialogCell) prevVh.itemView).getIsPinned();
            outRect.top = prevPinned != currPinned ? dp(10) : dp(2);
        } else {
            outRect.top = pos == 0 ? dp(6) : dp(4);
        }
        if (pos == adapter.getItemCount() - 1) {
            outRect.bottom = dp(12);
        }
    }

    @Override
    public void onDraw(@NonNull Canvas c, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
        if (!AppearanceConfig.isSegmentedDialogsEnabled()) {
            return;
        }
        final int bgColor = Theme.getColor(Theme.key_windowBackgroundGray, resourcesProvider);
        bgPaint.setColor(bgColor);
        c.drawRect(0, 0, parent.getWidth(), parent.getHeight(), bgPaint);

        final int cardColor = Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider);
        cardPaint.setColor(cardColor);
        final int pinnedOverlay = Theme.getColor(Theme.key_chats_pinnedOverlay, resourcesProvider);
        pinnedPaint.setColor(pinnedOverlay);
        final boolean isDark = Theme.isCurrentThemeDark();
        borderPaint.setColor(isDark ? 0x10FFFFFF : 0x0C000000);

        final float outerR = dp(Math.max(4, AppearanceConfig.sectionRadius()));
        final float innerR = dp(4);

        final int count = parent.getChildCount();
        for (int i = 0; i < count; i++) {
            final View child = parent.getChildAt(i);
            if (!(child instanceof DialogCell)) {
                continue;
            }
            final DialogCell cell = (DialogCell) child;
            final int pos = parent.getChildAdapterPosition(child);
            if (pos == RecyclerView.NO_POSITION) {
                continue;
            }
            final boolean currPinned = cell.getIsPinned();
            final RecyclerView.ViewHolder prevVh = pos > 0 ? parent.findViewHolderForAdapterPosition(pos - 1) : null;
            final RecyclerView.ViewHolder nextVh = parent.findViewHolderForAdapterPosition(pos + 1);

            final boolean isFirst = prevVh == null || !(prevVh.itemView instanceof DialogCell)
                    || ((DialogCell) prevVh.itemView).getIsPinned() != currPinned;
            final boolean isLast = nextVh == null || !(nextVh.itemView instanceof DialogCell)
                    || ((DialogCell) nextVh.itemView).getIsPinned() != currPinned;

            final float topR = isFirst ? outerR : innerR;
            final float botR = isLast ? outerR : innerR;
            radii[0] = radii[1] = radii[2] = radii[3] = topR;
            radii[4] = radii[5] = radii[6] = radii[7] = botR;

            final float tx = child.getTranslationX();
            final float ty = child.getTranslationY();
            cardRect.set(child.getLeft() + tx, child.getTop() + ty, child.getRight() + tx, child.getBottom() + ty);

            cardPath.reset();
            cardPath.addRoundRect(cardRect, radii, Path.Direction.CW);
            c.drawPath(cardPath, cardPaint);
            if (currPinned && (pinnedOverlay >>> 24) > 0) {
                c.drawPath(cardPath, pinnedPaint);
            }
            c.drawPath(cardPath, borderPaint);
        }
    }
}
