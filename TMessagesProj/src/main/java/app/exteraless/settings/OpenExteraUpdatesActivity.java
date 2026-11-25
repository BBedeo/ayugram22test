package app.exteraless.settings;

import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.view.View;

import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.BuildVars;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Components.BulletinFactory;

import app.exteraless.general.GeneralConfig;
import app.exteraless.updater.GitHubUpdater;
import tw.nekomimi.nekogram.helpers.remote.UpdateHelper;
import tw.nekomimi.nekogram.settings.BaseNekoSettingsActivity;
import tw.nekomimi.nekogram.ui.cells.HeaderCell;
import xyz.nextalone.nagram.NaConfig;

/**
 * «Версия и обновления»: текущая версия, канал, автообновление и отложенное обновление.
 * Автообновление скачивает новую сборку заранее, пока приложение открыто, и только
 * предлагает установить её: Android всегда подтверждает установку сам.
 */
public class OpenExteraUpdatesActivity extends BaseNekoSettingsActivity {

    private static final int[] CHANNELS = {
            UpdateHelper.UPDATE_OFF, UpdateHelper.UPDATE_CHANNEL_RELEASE, UpdateHelper.UPDATE_CHANNEL_BETA};

    private int updatesHeaderRow;
    private int checkRow;
    private int channelRow;
    private int intervalRow;
    private int autoUpdateRow;
    private int wifiOnlyRow;
    private int postponedRow;
    private int releaseHistoryRow;
    private int allReleasesRow;
    private int dividerRow;

    public OpenExteraUpdatesActivity() {
        super();
    }

    @Override
    public boolean onFragmentCreate() {
        GeneralConfig.init();
        return super.onFragmentCreate();
    }

    @Override
    protected void updateRows() {
        super.updateRows();

        updatesHeaderRow = addRow("updatesHeader");
        checkRow = addRow("check");
        channelRow = addRow("channel");
        intervalRow = addRow("checkInterval");
        autoUpdateRow = addRow("autoUpdate");
        wifiOnlyRow = addRow("wifiOnly");
        postponedRow = addRow("postponed");
        releaseHistoryRow = addRow("releaseHistory");
        allReleasesRow = addRow("allReleases");
        dividerRow = addRow();
    }

    @Override
    protected String getActionBarTitle() {
        return getString(R.string.OEUpdatesTitle);
    }

    @Override
    public int getSearchGuid() {
        return 31000;
    }

    @Override
    public int getSearchIcon() {
        return R.drawable.msg_download;
    }

    @Override
    public String getSearchPrefix() {
        return "OEUpdates";
    }

    @Override
    protected String getKey() {
        return "exteraless_updates";
    }

    @Override
    protected BaseListAdapter createAdapter(Context context) {
        return new ListAdapter(context);
    }

    private String channelName() {
        int value = NaConfig.INSTANCE.getAutoUpdateChannel().Int();
        if (value == UpdateHelper.UPDATE_OFF) {
            return getString(R.string.OEUpdaterChannelOff);
        }
        return getString(value == UpdateHelper.UPDATE_CHANNEL_BETA
                ? R.string.OEUpdaterChannelBeta : R.string.OEUpdaterChannelStable);
    }

    private static final int[] INTERVAL_NAMES = {
            R.string.OEUpdatesIntervalEveryStart, R.string.OEUpdatesInterval1h, R.string.OEUpdatesInterval3h,
            R.string.OEUpdatesInterval6h, R.string.OEUpdatesInterval12h, R.string.OEUpdatesInterval24h};

    private String intervalName() {
        int index = Math.max(0, Math.min(INTERVAL_NAMES.length - 1, GeneralConfig.autoUpdateInterval.Int()));
        return getString(INTERVAL_NAMES[index]);
    }

    private void showIntervalSelector() {
        if (getParentActivity() == null) {
            return;
        }
        CharSequence[] items = new CharSequence[INTERVAL_NAMES.length];
        for (int i = 0; i < items.length; i++) {
            items[i] = getString(INTERVAL_NAMES[i]);
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(getString(R.string.OEUpdatesCheckInterval));
        builder.setItems(items, (dialog, which) -> {
            GeneralConfig.autoUpdateInterval.setConfigInt(which);
            if (listAdapter != null) {
                listAdapter.notifyItemChanged(intervalRow);
            }
        });
        builder.setNegativeButton(getString(R.string.Cancel), null);
        showDialog(builder.create());
    }

    private void showChannelSelector() {
        if (getParentActivity() == null) {
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(getString(R.string.OEUpdatesChannel));
        builder.setItems(new CharSequence[]{
                getString(R.string.OEUpdaterChannelOff),
                getString(R.string.OEUpdaterChannelStable),
                getString(R.string.OEUpdaterChannelBeta)}, (dialog, which) -> {
            NaConfig.INSTANCE.getAutoUpdateChannel().setConfigInt(CHANNELS[which]);
            if (listAdapter != null) {
                listAdapter.notifyItemChanged(channelRow);
            }
        });
        builder.setNegativeButton(getString(R.string.Cancel), null);
        showDialog(builder.create());
    }

    @Override
    protected void onItemClick(View view, int position, float x, float y) {
        if (position == checkRow) {
            GitHubUpdater.check(true);
        } else if (position == channelRow) {
            showChannelSelector();
        } else if (position == intervalRow) {
            showIntervalSelector();
        } else if (position == autoUpdateRow) {
            boolean enabled = GeneralConfig.autoUpdate.toggleConfigBool();
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(enabled);
            }
            if (listAdapter != null) {
                listAdapter.notifyItemChanged(wifiOnlyRow);
            }
        } else if (position == wifiOnlyRow) {
            boolean enabled = GeneralConfig.autoUpdateWifiOnly.toggleConfigBool();
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(enabled);
            }
        } else if (position == postponedRow) {
            if (GitHubUpdater.postponedTag() != null) {
                GitHubUpdater.clearPostponed();
                if (listAdapter != null) {
                    listAdapter.notifyItemChanged(postponedRow);
                }
                BulletinFactory.of(this).createSimpleBulletin(R.raw.done,
                        getString(R.string.OEUpdatesPostponedReset)).show();
            }
        } else if (position == releaseHistoryRow) {
            presentFragment(new OpenExteraReleaseHistoryActivity());
        } else if (position == allReleasesRow) {
            org.telegram.messenger.browser.Browser.openUrl(getParentActivity(), GitHubUpdater.RELEASES_URL);
        }
    }

    private class ListAdapter extends BaseListAdapter {

        public ListAdapter(Context context) {
            super(context);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position, boolean partial) {
            switch (holder.getItemViewType()) {
                case TYPE_HEADER: {
                    HeaderCell cell = (HeaderCell) holder.itemView;
                    if (position == updatesHeaderRow) {
                        cell.setText(getString(R.string.OEUpdatesTitle));
                    }
                    break;
                }
                case TYPE_CHECK: {
                    TextCheckCell cell = (TextCheckCell) holder.itemView;
                    if (position == autoUpdateRow) {
                        cell.setEnabled(true);
                        cell.setTextAndCheck(getString(R.string.OEUpdatesAutoUpdate),
                                GeneralConfig.autoUpdate.Bool(), true);
                    } else if (position == wifiOnlyRow) {
                        cell.setTextAndCheck(getString(R.string.OEUpdatesWifiOnly),
                                GeneralConfig.autoUpdateWifiOnly.Bool(), true);
                        cell.setEnabled(GeneralConfig.autoUpdate.Bool());
                    }
                    break;
                }
                case TYPE_TEXT: {
                    TextCell cell = (TextCell) holder.itemView;
                    cell.setColors(Theme.key_windowBackgroundWhiteGrayIcon, Theme.key_windowBackgroundWhiteBlackText);
                    if (position == checkRow) {
                        cell.setTextAndValueAndIcon(getString(R.string.OEUpdatesCheck),
                                BuildVars.BUILD_VERSION_STRING, R.drawable.msg_download, true);
                    } else if (position == channelRow) {
                        cell.setTextAndValueAndIcon(getString(R.string.OEUpdatesChannel),
                                channelName(), R.drawable.msg_channel, true);
                    } else if (position == intervalRow) {
                        cell.setTextAndValueAndIcon(getString(R.string.OEUpdatesCheckInterval),
                                intervalName(), R.drawable.msg_clear_recent, true);
                    } else if (position == postponedRow) {
                        String postponed = GitHubUpdater.postponedTag();
                        cell.setTextAndValueAndIcon(getString(R.string.OEUpdatesPostponed),
                                postponed == null ? getString(R.string.OEUpdatesPostponedNone)
                                        : (postponed.startsWith("v") ? postponed.substring(1) : postponed),
                                R.drawable.msg_reset, true);
                    } else if (position == releaseHistoryRow) {
                        cell.setTextAndIcon(getString(R.string.OEUpdatesReleaseHistory), R.drawable.msg_reset, true);
                    } else if (position == allReleasesRow) {
                        cell.setTextAndIcon(getString(R.string.OEUpdatesAllReleases), R.drawable.msg_link, false);
                    }
                    break;
                }
                case TYPE_INFO_PRIVACY: {
                    TextInfoPrivacyCell cell = (TextInfoPrivacyCell) holder.itemView;
                    if (position == dividerRow) {
                        cell.setText(getString(R.string.OEUpdatesInfo));
                        cell.setBackground(Theme.getThemedDrawable(mContext,
                                R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                    }
                    break;
                }
            }
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            int position = holder.getAdapterPosition();
            if (position == wifiOnlyRow) {
                return GeneralConfig.autoUpdate.Bool();
            }
            return holder.getItemViewType() == TYPE_TEXT || holder.getItemViewType() == TYPE_CHECK;
        }

        @Override
        public int getItemViewType(int position) {
            if (position == updatesHeaderRow) {
                return TYPE_HEADER;
            } else if (position == checkRow || position == channelRow || position == intervalRow || position == postponedRow
                    || position == releaseHistoryRow || position == allReleasesRow) {
                return TYPE_TEXT;
            } else if (position == dividerRow) {
                return TYPE_INFO_PRIVACY;
            }
            return TYPE_CHECK;
        }
    }
}
