package app.exteraless.settings;

import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.text.TextUtils;
import android.view.View;

import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Components.BulletinFactory;

import java.text.DateFormat;
import java.util.Date;
import java.util.List;

import app.exteraless.updater.GitHubUpdater;
import app.exteraless.utils.text.TranslatorUtils;
import tw.nekomimi.nekogram.settings.BaseNekoSettingsActivity;

/**
 * «История версий»: все релизы с GitHub, по тапу — описание. Описания пишутся по-русски;
 * в других языках в диалоге есть «Перевести» через переводчик форка.
 */
public class OpenExteraReleaseHistoryActivity extends BaseNekoSettingsActivity {

    private List<GitHubUpdater.Release> releases;
    private boolean failed;
    private final DateFormat dateFormat = DateFormat.getDateInstance(DateFormat.MEDIUM);

    @Override
    public boolean onFragmentCreate() {
        GitHubUpdater.fetchAll((list, error) -> {
            releases = list;
            failed = error && list.isEmpty();
            if (listAdapter != null) {
                updateRows();
                listAdapter.notifyDataSetChanged();
            }
        });
        return super.onFragmentCreate();
    }

    @Override
    protected void updateRows() {
        super.updateRows();
        // rows are one per release, so none of them is a searchable settings row
        rowCount += releases == null ? 1 : Math.max(1, releases.size());
        rowCount++; // trailing info/divider
    }

    @Override
    protected String getActionBarTitle() {
        return getString(R.string.OEUpdatesReleaseHistory);
    }

    @Override
    protected String getKey() {
        return "exteraless_release_history";
    }

    @Override
    public int getSearchGuid() {
        return 31001;
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
    protected BaseListAdapter createAdapter(Context context) {
        return new ListAdapter(context);
    }

    @Override
    protected void onItemClick(View view, int position, float x, float y) {
        if (releases == null || position >= releases.size()) {
            return;
        }
        showNotes(releases.get(position));
    }

    private void showNotes(GitHubUpdater.Release release) {
        if (getParentActivity() == null) {
            return;
        }
        final String notes = GitHubUpdater.notesOf(release);
        final AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(release.displayVersion());
        builder.setMessage(notes);
        builder.setPositiveButton(getString(R.string.Close), null);
        final String target = TranslatorUtils.getResolvedTargetLanguageCode();
        if (target != null && !target.toLowerCase().startsWith("ru")) {
            builder.setNeutralButton(getString(R.string.TranslateMessage), (d, w) ->
                    TranslatorUtils.translate(notes, "ru", target, null, new TranslatorUtils.TranslateCallback() {
                        @Override
                        public void onFailed() {
                            BulletinFactory.of(OpenExteraReleaseHistoryActivity.this)
                                    .createSimpleBulletin(R.raw.error, getString(R.string.ErrorOccurred)).show();
                        }

                        @Override
                        public void onSuccess(String text) {
                            if (getParentActivity() == null || TextUtils.isEmpty(text)) {
                                return;
                            }
                            AlertDialog.Builder translated = new AlertDialog.Builder(getParentActivity());
                            translated.setTitle(release.displayVersion());
                            translated.setMessage(text);
                            translated.setPositiveButton(getString(R.string.Close), null);
                            showDialog(translated.create());
                        }
                    }));
        }
        showDialog(builder.create());
    }

    private class ListAdapter extends BaseListAdapter {

        ListAdapter(Context context) {
            super(context);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position, boolean partial) {
            if (holder.getItemViewType() == TYPE_TEXT) {
                TextCell cell = (TextCell) holder.itemView;
                cell.setColors(Theme.key_windowBackgroundWhiteGrayIcon, Theme.key_windowBackgroundWhiteBlackText);
                if (releases == null || releases.isEmpty()) {
                    cell.setText(getString(releases == null ? R.string.Loading
                            : failed ? R.string.OEUpdateCheckFailed : R.string.OEUpdatesPostponedNone), false);
                } else {
                    GitHubUpdater.Release release = releases.get(position);
                    cell.setTextAndValue(release.displayVersion(),
                            release.published > 0 ? dateFormat.format(new Date(release.published)) : "",
                            position < releases.size() - 1);
                }
            } else if (holder.getItemViewType() == TYPE_INFO_PRIVACY) {
                TextInfoPrivacyCell cell = (TextInfoPrivacyCell) holder.itemView;
                cell.setText(getString(R.string.OEUpdatesReleaseHistoryInfo));
                cell.setBackground(Theme.getThemedDrawable(mContext,
                        R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
            }
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return holder.getItemViewType() == TYPE_TEXT && releases != null && !releases.isEmpty();
        }

        @Override
        public int getItemViewType(int position) {
            return position == rowCount - 1 ? TYPE_INFO_PRIVACY : TYPE_TEXT;
        }
    }
}
