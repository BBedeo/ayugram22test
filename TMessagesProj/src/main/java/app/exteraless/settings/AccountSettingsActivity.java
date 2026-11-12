package app.exteraless.settings;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.RecyclerListView;

import java.util.Locale;

import tw.nekomimi.nekogram.settings.BaseNekoSettingsActivity;
import tw.nekomimi.nekogram.ui.cells.HeaderCell;

/** Account settings, scoped to ExFullGram/Nagram/Neko preferences. */
public class AccountSettingsActivity extends BaseNekoSettingsActivity {
    private int currentHeader;
    private int currentRow;
    private int profilesHeader;
    private int separateRow;
    private int compareRow;
    private int resetRow;
    private int scopeInfoRow;

    @Override
    protected void updateRows() {
        super.updateRows();
        currentHeader = addRow("current");
        currentRow = addRow("activeProfile");
        profilesHeader = addRow("profiles");
        separateRow = addRow("separateSettings");
        compareRow = addRow("compare");
        resetRow = addRow("reset");
        scopeInfoRow = addRow();
    }

    @Override
    protected String getActionBarTitle() {
        return getString(R.string.EFGAccountsTitle);
    }

    @Override
    public int getSearchGuid() {
        return 24500;
    }

    @Override
    public int getSearchIcon() {
        return R.drawable.msg_settings_old;
    }

    @Override
    public String getSearchPrefix() {
        return "EFGAccounts";
    }

    @Override
    protected String getKey() {
        return "exteraless_accounts";
    }

    @Override
    protected BaseListAdapter createAdapter(Context context) {
        return new ListAdapter(context);
    }

    @Override
    protected void onItemClick(View view, int position, float x, float y) {
        if (position == separateRow) {
            boolean enabled = !AccountSettingsProfiles.isEnabled();
            AccountSettingsProfiles.setEnabled(enabled, UserConfig.selectedAccount);
            if (view instanceof TextCheckCell) ((TextCheckCell) view).setChecked(enabled);
            if (listAdapter != null) listAdapter.notifyDataSetChanged();
        } else if (position == compareRow) {
            if (!AccountSettingsProfiles.isEnabled()) {
                showDialog(new AlertDialog.Builder(getParentActivity(), getResourceProvider())
                        .setTitle(getString(R.string.EFGAccountsTitle))
                        .setMessage(getString(R.string.EFGAccountsEnableFirst))
                        .setPositiveButton(getString(R.string.OK), null).create());
                return;
            }
            presentFragment(new AccountSettingsCompareActivity());
        } else if (position == resetRow) {
            if (!AccountSettingsProfiles.isEnabled()) {
                showDialog(new AlertDialog.Builder(getParentActivity(), getResourceProvider())
                        .setTitle(getString(R.string.EFGAccountsTitle))
                        .setMessage(getString(R.string.EFGAccountsEnableFirst))
                        .setPositiveButton(getString(R.string.OK), null).create());
                return;
            }
            chooseResetAccount();
        }
    }

    private void chooseResetAccount() {
        int[] slots = AccountSettingsProfiles.availableAccounts();
        CharSequence[] names = new CharSequence[slots.length];
        for (int i = 0; i < slots.length; i++) names[i] = AccountSettingsProfiles.labelFor(slots[i]);
        showDialog(new AlertDialog.Builder(getParentActivity(), getResourceProvider())
                .setTitle(getString(R.string.EFGAccountsResetWhich))
                .setItems(names, (d, which) -> confirmReset(slots[which]))
                .setNegativeButton(getString(R.string.Cancel), null)
                .create());
    }

    private void confirmReset(int slot) {
        String message = String.format(Locale.getDefault(), getString(R.string.EFGAccountsResetConfirm),
                AccountSettingsProfiles.labelFor(slot));
        showDialog(new AlertDialog.Builder(getParentActivity(), getResourceProvider())
                .setTitle(getString(R.string.EFGAccountsReset))
                .setMessage(message)
                .setPositiveButton(getString(R.string.Reset), (d, which) -> {
                    AccountSettingsProfiles.resetProfile(slot);
                    if (listAdapter != null) listAdapter.notifyDataSetChanged();
                })
                .setNegativeButton(getString(R.string.Cancel), null)
                .create());
    }

    private class ListAdapter extends BaseListAdapter {
        ListAdapter(Context context) { super(context); }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            int position = holder.getAdapterPosition();
            return position == separateRow || position == compareRow || position == resetRow;
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position, boolean partial) {
            switch (holder.getItemViewType()) {
                case TYPE_HEADER: {
                    HeaderCell cell = (HeaderCell) holder.itemView;
                    cell.setText(position == currentHeader
                            ? getString(R.string.EFGAccountsCurrent)
                            : getString(R.string.EFGAccountsOptions));
                    break;
                }
                case TYPE_SETTINGS: {
                    TextSettingsCell cell = (TextSettingsCell) holder.itemView;
                    cell.setTextAndValue(getString(R.string.EFGAccountsCurrent),
                            AccountSettingsProfiles.labelFor(UserConfig.selectedAccount), false);
                    break;
                }
                case TYPE_CHECK: {
                    TextCheckCell cell = (TextCheckCell) holder.itemView;
                    cell.setTextAndValueAndCheck(
                            getString(R.string.EFGAccountsSeparateSettings),
                            getString(R.string.EFGAccountsSeparateHint),
                            AccountSettingsProfiles.isEnabled(), true, true);
                    break;
                }
                case TYPE_TEXT: {
                    TextCell cell = (TextCell) holder.itemView;
                    if (position == compareRow) {
                        cell.setColors(Theme.key_windowBackgroundWhiteBlueIcon, Theme.key_windowBackgroundWhiteBlackText);
                        cell.setTextAndIcon(getString(R.string.EFGAccountsCompare),
                                R.drawable.msg_groups, true);
                    } else if (position == resetRow) {
                        cell.setColors(Theme.key_text_RedRegular, Theme.key_text_RedRegular);
                        cell.setTextAndIcon(getString(R.string.EFGAccountsReset),
                                R.drawable.msg_retry_solar, false);
                    }
                    break;
                }
                case TYPE_INFO_PRIVACY: {
                    TextInfoPrivacyCell cell = (TextInfoPrivacyCell) holder.itemView;
                    cell.setText(getString(R.string.EFGAccountsScope) + "\n\n" +
                            getString(R.string.EFGAccountsRestartHint));
                    break;
                }
            }
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return super.onCreateViewHolder(parent, viewType);
        }

        @Override
        public int getItemViewType(int position) {
            if (position == currentHeader || position == profilesHeader) return TYPE_HEADER;
            if (position == currentRow) return TYPE_SETTINGS;
            if (position == separateRow) return TYPE_CHECK;
            if (position == scopeInfoRow) return TYPE_INFO_PRIVACY;
            return TYPE_TEXT;
        }
    }
}
