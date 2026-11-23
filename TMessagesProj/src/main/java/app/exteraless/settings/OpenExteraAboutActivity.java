package app.exteraless.settings;

import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;

import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextInfoPrivacyCell;

import tw.nekomimi.nekogram.settings.BaseNekoSettingsActivity;
import tw.nekomimi.nekogram.ui.cells.HeaderCell;

/**
 * About ExFullGram: the idea of the app and what it brings together. Read-only: a header and a
 * paragraph for every block.
 */
public class OpenExteraAboutActivity extends BaseNekoSettingsActivity {

    private int ideaHeaderRow;
    private int ideaRow;
    private int pluginsHeaderRow;
    private int pluginsRow;
    private int clientHeaderRow;
    private int clientRow;
    private int betaHeaderRow;
    private int betaRow;

    @Override
    protected void updateRows() {
        super.updateRows();
        ideaHeaderRow = addRow("ideaHeader");
        ideaRow = addRow("idea");
        pluginsHeaderRow = addRow("pluginsHeader");
        pluginsRow = addRow("plugins");
        clientHeaderRow = addRow("clientHeader");
        clientRow = addRow("client");
        betaHeaderRow = addRow("betaHeader");
        betaRow = addRow("beta");
    }

    @Override
    protected String getActionBarTitle() {
        return getString(R.string.OEAboutTitle);
    }

    @Override
    public int getSearchGuid() {
        return 31001;
    }

    @Override
    public int getSearchIcon() {
        return R.drawable.msg_info;
    }

    @Override
    public String getSearchPrefix() {
        return "OEAbout";
    }

    @Override
    protected String getKey() {
        return "exteraless_about";
    }

    @Override
    protected BaseListAdapter createAdapter(Context context) {
        return new ListAdapter(context);
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
                    if (position == ideaHeaderRow) {
                        cell.setText(getString(R.string.OEAboutIdea));
                    } else if (position == pluginsHeaderRow) {
                        cell.setText(getString(R.string.OEAboutPlugins));
                    } else if (position == clientHeaderRow) {
                        cell.setText(getString(R.string.OEAboutClient));
                    } else if (position == betaHeaderRow) {
                        cell.setText(getString(R.string.OEAboutBeta));
                    }
                    break;
                }
                case TYPE_INFO_PRIVACY: {
                    TextInfoPrivacyCell cell = (TextInfoPrivacyCell) holder.itemView;
                    final boolean last = position == betaRow;
                    cell.setBackground(Theme.getThemedDrawable(mContext,
                            last ? R.drawable.greydivider_bottom : R.drawable.greydivider,
                            Theme.key_windowBackgroundGrayShadow));
                    if (position == ideaRow) {
                        cell.setText(getString(R.string.OEAboutIdeaText));
                    } else if (position == pluginsRow) {
                        cell.setText(getString(R.string.OEAboutPluginsText));
                    } else if (position == clientRow) {
                        cell.setText(getString(R.string.OEAboutClientText));
                    } else if (position == betaRow) {
                        cell.setText(getString(R.string.OEAboutBetaText));
                    }
                    break;
                }
            }
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return false;
        }

        @Override
        public int getItemViewType(int position) {
            if (position == ideaHeaderRow || position == pluginsHeaderRow
                    || position == clientHeaderRow || position == betaHeaderRow) {
                return TYPE_HEADER;
            }
            return TYPE_INFO_PRIVACY;
        }
    }
}
