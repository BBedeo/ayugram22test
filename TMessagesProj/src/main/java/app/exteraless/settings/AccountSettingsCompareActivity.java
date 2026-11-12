package app.exteraless.settings;

import static org.telegram.messenger.LocaleController.getString;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Animated, side-by-side comparison of two account settings profiles.
 *
 * A single recycler synchronizes both columns; select the LEFT or RIGHT
 * value in any row to use it as the winning value when merging. This prevents
 * changes that were not explicitly chosen from being silently overwritten.
 */
public class AccountSettingsCompareActivity extends BaseFragment {
    private int leftSlot;
    private int rightSlot;
    private final ArrayList<AccountSettingsProfiles.Difference> differences = new ArrayList<>();
    private final HashMap<String, Integer> choices = new HashMap<>();

    private TextView leftSelector;
    private TextView rightSelector;
    private TextView emptyView;
    private TextView countView;
    private TextView applyButton;
    private RecyclerListView listView;
    private CompareAdapter adapter;

    public AccountSettingsCompareActivity() {
        leftSlot = UserConfig.selectedAccount;
        rightSlot = leftSlot;
        for (int slot : AccountSettingsProfiles.availableAccounts()) {
            if (slot != leftSlot) {
                rightSlot = slot;
                break;
            }
        }
    }

    private GradientDrawable rounded(int color, int borderColor, int radiusDp) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(dp(radiusDp));
        if (borderColor != 0) shape.setStroke(dp(1), borderColor);
        return shape;
    }

    private int color(int key) {
        return getThemedColor(key);
    }

    private int accentFade() {
        int accent = color(Theme.key_windowBackgroundWhiteBlueText);
        return Color.argb(34, Color.red(accent), Color.green(accent), Color.blue(accent));
    }

    private TextView text(Context ctx, int size, boolean bold, int colorKey) {
        TextView view = new TextView(ctx);
        view.setTextSize(TypedValue.COMPLEX_UNIT_DIP, size);
        view.setTextColor(color(colorKey));
        if (bold) view.setTypeface(AndroidUtilities.bold());
        return view;
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle(getString(R.string.EFGAccountsComparisonTitle));
        actionBar.setActionBarMenuOnItemClick(new org.telegram.ui.ActionBar.ActionBar.ActionBarMenuOnItemClick() {
            @Override public void onItemClick(int id) {
                if (id == -1) finishFragment();
            }
        });

        FrameLayout root = new FrameLayout(context);
        root.setBackgroundColor(color(Theme.key_windowBackgroundGray));
        fragmentView = root;

        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(dp(14), dp(10), dp(14), dp(12));
        root.addView(column, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        TextView eyebrow = text(context, 13, true, Theme.key_windowBackgroundWhiteBlueText);
        eyebrow.setText("ExFullGram");
        eyebrow.setPadding(dp(6), dp(2), dp(6), dp(6));
        column.addView(eyebrow);

        TextView hint = text(context, 13, false, Theme.key_windowBackgroundWhiteGrayText);
        hint.setText(getString(R.string.EFGAccountsSelectHint));
        hint.setLineSpacing(dp(2), 1f);
        hint.setPadding(dp(6), 0, dp(6), dp(12));
        column.addView(hint);

        LinearLayout selectors = new LinearLayout(context);
        selectors.setOrientation(LinearLayout.HORIZONTAL);
        column.addView(selectors, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        leftSelector = makeAccountSelector(context, true);
        rightSelector = makeAccountSelector(context, false);
        LinearLayout.LayoutParams leftParams = new LinearLayout.LayoutParams(0, dp(76), 1f);
        leftParams.rightMargin = dp(6);
        selectors.addView(leftSelector, leftParams);
        LinearLayout.LayoutParams rightParams = new LinearLayout.LayoutParams(0, dp(76), 1f);
        rightParams.leftMargin = dp(6);
        selectors.addView(rightSelector, rightParams);

        FrameLayout content = new FrameLayout(context);
        LinearLayout.LayoutParams contentParams =
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        contentParams.topMargin = dp(12);
        column.addView(content, contentParams);

        listView = new RecyclerListView(context);
        listView.setLayoutManager(new LinearLayoutManager(context));
        listView.setClipToPadding(false);
        listView.setPadding(0, 0, 0, dp(10));
        listView.setItemAnimator(new DefaultItemAnimator());
        adapter = new CompareAdapter();
        listView.setAdapter(adapter);
        content.addView(listView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        emptyView = text(context, 15, false, Theme.key_windowBackgroundWhiteGrayText);
        emptyView.setText(getString(R.string.EFGAccountsNoDifferences));
        emptyView.setGravity(Gravity.CENTER);
        emptyView.setPadding(dp(22), dp(24), dp(22), dp(24));
        emptyView.setVisibility(View.GONE);
        content.addView(emptyView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout footer = new LinearLayout(context);
        footer.setOrientation(LinearLayout.VERTICAL);
        footer.setPadding(dp(2), dp(8), dp(2), 0);
        column.addView(footer);

        countView = text(context, 13, false, Theme.key_windowBackgroundWhiteGrayText);
        countView.setGravity(Gravity.CENTER);
        footer.addView(countView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(28)));

        applyButton = text(context, 15, true, Theme.key_windowBackgroundWhiteBlueText);
        applyButton.setGravity(Gravity.CENTER);
        applyButton.setBackground(rounded(color(Theme.key_windowBackgroundWhite),
                color(Theme.key_windowBackgroundWhiteBlueText), 18));
        applyButton.setText(getString(R.string.EFGAccountsApply));
        applyButton.setOnClickListener(v -> confirmApply());
        footer.addView(applyButton, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(54)));

        refresh();
        return fragmentView;
    }

    private TextView makeAccountSelector(Context context, boolean left) {
        TextView selector = text(context, 15, true, Theme.key_windowBackgroundWhiteBlackText);
        selector.setPadding(dp(12), dp(9), dp(12), dp(9));
        selector.setGravity(Gravity.CENTER_VERTICAL);
        selector.setMaxLines(3);
        selector.setEllipsize(TextUtils.TruncateAt.END);
        selector.setBackground(rounded(color(Theme.key_windowBackgroundWhite),
                color(Theme.key_divider), 17));
        selector.setOnClickListener(v -> chooseAccount(left));
        return selector;
    }

    private void chooseAccount(boolean left) {
        if (getParentActivity() == null) return;
        int[] slots = AccountSettingsProfiles.availableAccounts();
        CharSequence[] names = new CharSequence[slots.length];
        for (int i = 0; i < slots.length; i++) names[i] = AccountSettingsProfiles.labelFor(slots[i]);
        showDialog(new AlertDialog.Builder(getParentActivity(), getResourceProvider())
                .setTitle(getString(R.string.EFGAccountsPickSide))
                .setItems(names, (dialog, which) -> {
                    if (left) leftSlot = slots[which];
                    else rightSlot = slots[which];
                    choices.clear();
                    refresh();
                })
                .setNegativeButton(getString(R.string.Cancel), null)
                .create());
    }

    private void refresh() {
        if (leftSelector == null || rightSelector == null) return;
        leftSelector.setText(getString(R.string.EFGAccountsLeft) + "\n" +
                AccountSettingsProfiles.labelFor(leftSlot) + "  ▾");
        rightSelector.setText(getString(R.string.EFGAccountsRight) + "\n" +
                AccountSettingsProfiles.labelFor(rightSlot) + "  ▾");
        differences.clear();
        if (leftSlot != rightSlot && AccountSettingsProfiles.isEnabled()) {
            differences.addAll(AccountSettingsProfiles.compare(leftSlot, rightSlot));
        }
        HashMap<String, Integer> old = new HashMap<>(choices);
        choices.clear();
        for (AccountSettingsProfiles.Difference diff : differences) {
            if (old.containsKey(diff.key)) choices.put(diff.key, old.get(diff.key));
        }
        if (adapter != null) adapter.notifyDataSetChanged();
        if (emptyView != null) {
            emptyView.setVisibility(differences.isEmpty() ? View.VISIBLE : View.GONE);
            if (leftSlot == rightSlot)
                emptyView.setText(getString(R.string.EFGAccountsPickSide));
            else if (!AccountSettingsProfiles.isEnabled())
                emptyView.setText(getString(R.string.EFGAccountsEnableFirst));
            else emptyView.setText(getString(R.string.EFGAccountsNoDifferences));
        }
        updateFooter();
    }

    private void updateFooter() {
        if (countView == null || applyButton == null) return;
        countView.setText(choices.size() + " / " + differences.size());
        applyButton.setEnabled(!choices.isEmpty());
        applyButton.setAlpha(choices.isEmpty() ? .55f : 1f);
    }

    private void select(String key, int side, int position, View target) {
        Integer old = choices.get(key);
        if (old != null && old == side) choices.remove(key);
        else choices.put(key, side);
        target.animate().cancel();
        target.animate().scaleX(1.035f).scaleY(1.035f).setDuration(95)
                .withEndAction(() -> target.animate().scaleX(1f).scaleY(1f).setDuration(130).start())
                .start();
        adapter.notifyItemChanged(position);
        updateFooter();
    }

    private void confirmApply() {
        if (choices.isEmpty()) return;
        if (getParentActivity() == null) return;
        int count = choices.size();
        String message = String.format(Locale.getDefault(),
                getString(R.string.EFGAccountsApplyConfirm), count);
        showDialog(new AlertDialog.Builder(getParentActivity(), getResourceProvider())
                .setTitle(getString(R.string.EFGAccountsApply))
                .setMessage(message)
                .setPositiveButton(getString(R.string.OK), (dialog, which) -> {
                    AccountSettingsProfiles.mergeSelected(leftSlot, rightSlot, new HashMap<>(choices));
                    choices.clear();
                    refresh();
                })
                .setNegativeButton(getString(R.string.Cancel), null)
                .create());
    }

    private final class CompareAdapter extends RecyclerView.Adapter<CompareHolder> {
        @NonNull
        @Override public CompareHolder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
            return new CompareHolder(new DiffRow(parent.getContext()));
        }

        @Override public void onBindViewHolder(@NonNull CompareHolder holder, int position) {
            AccountSettingsProfiles.Difference diff = differences.get(position);
            holder.row.bind(diff, choices.getOrDefault(diff.key, 0), position);
        }

        @Override public int getItemCount() {
            return differences.size();
        }
    }

    private static final class CompareHolder extends RecyclerView.ViewHolder {
        final DiffRow row;
        CompareHolder(DiffRow row) { super(row); this.row = row; }
    }

    private final class DiffRow extends LinearLayout {
        private final TextView title;
        private final TextView left;
        private final TextView right;

        DiffRow(Context context) {
            super(context);
            setOrientation(VERTICAL);
            setPadding(dp(12), dp(12), dp(12), dp(12));
            GradientDrawable background = rounded(color(Theme.key_windowBackgroundWhite),
                    0, 18);
            setBackground(background);

            title = text(context, 13, true, Theme.key_windowBackgroundWhiteGrayText);
            title.setMaxLines(2);
            title.setEllipsize(TextUtils.TruncateAt.END);
            addView(title, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            LinearLayout values = new LinearLayout(context);
            values.setOrientation(HORIZONTAL);
            LinearLayout.LayoutParams valuesParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            valuesParams.topMargin = dp(8);
            addView(values, valuesParams);

            left = makeValue(context);
            right = makeValue(context);
            LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            l.rightMargin = dp(5);
            values.addView(left, l);
            LinearLayout.LayoutParams r = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            r.leftMargin = dp(5);
            values.addView(right, r);
        }

        private TextView makeValue(Context context) {
            TextView value = text(context, 14, false, Theme.key_windowBackgroundWhiteBlackText);
            value.setGravity(Gravity.CENTER_VERTICAL);
            value.setMinHeight(dp(50));
            value.setPadding(dp(10), dp(7), dp(10), dp(7));
            value.setMaxLines(3);
            value.setEllipsize(TextUtils.TruncateAt.END);
            return value;
        }

        void bind(AccountSettingsProfiles.Difference diff, int choice, int position) {
            title.setText(diff.getLabel());
            left.setText(AccountSettingsProfiles.Difference.valueLabel(diff.key, diff.left));
            right.setText(AccountSettingsProfiles.Difference.valueLabel(diff.key, diff.right));

            left.setBackground(rounded(choice == 1 ? accentFade() :
                    color(Theme.key_windowBackgroundGray),
                    choice == 1 ? color(Theme.key_windowBackgroundWhiteBlueText) : 0, 13));
            right.setBackground(rounded(choice == 2 ? accentFade() :
                    color(Theme.key_windowBackgroundGray),
                    choice == 2 ? color(Theme.key_windowBackgroundWhiteBlueText) : 0, 13));

            left.setTextColor(color(choice == 1 ? Theme.key_windowBackgroundWhiteBlueText
                    : Theme.key_windowBackgroundWhiteBlackText));
            right.setTextColor(color(choice == 2 ? Theme.key_windowBackgroundWhiteBlueText
                    : Theme.key_windowBackgroundWhiteBlackText));

            left.setOnClickListener(v -> select(diff.key, 1, position, v));
            right.setOnClickListener(v -> select(diff.key, 2, position, v));

            // The native RecyclerView item animator fades/repositions rows
            // after merges; value selection also has a subtle scale motion.
            RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = dp(8);
            setLayoutParams(lp);
        }
    }
}
