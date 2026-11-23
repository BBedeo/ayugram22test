package app.exteraless.updater;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.net.Uri;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

import app.exteraless.appearance.SheetSurfaceMode;

/**
 * Update that changes the signing key. Android cannot install such an APK over the installed
 * one, so the sheet walks the user through: back up settings, download the new installer into
 * Downloads, remove the old app. The new app then finds the backup by itself.
 */
public class MigrationSheet extends BottomSheet {

    private final Activity activity;
    private final GitHubUpdater.Release release;
    private final MigrationStepView exportStep;
    private final MigrationStepView downloadStep;
    private final MigrationStepView removeStep;

    public MigrationSheet(Activity activity, Theme.ResourcesProvider resourcesProvider,
                          GitHubUpdater.Release release, String subtitle, String notes) {
        super(activity, false, resourcesProvider);
        this.activity = activity;
        this.release = release;
        fixNavigationBar();

        final boolean m3 = SheetSurfaceMode.isM3(resourcesProvider);
        final int accent = SheetSurfaceMode.accent(resourcesProvider);
        final float buttonRadius = SheetSurfaceMode.buttonRadiusDp(resourcesProvider);
        Context context = activity;

        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dp(m3 ? 24 : 18), dp(m3 ? 12 : 10), dp(m3 ? 24 : 18), dp(m3 ? 20 : 14));

        LinearLayout header = new LinearLayout(context);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        ImageView icon = new ImageView(context);
        icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        icon.setImageResource(R.drawable.msg_download);
        icon.setColorFilter(new PorterDuffColorFilter(
                m3 ? accent : SheetSurfaceMode.onAccent(resourcesProvider), PorterDuff.Mode.SRC_IN));
        icon.setBackground(Theme.createRoundRectDrawable(dp(m3 ? 14 : 20),
                m3 ? ColorUtils.setAlphaComponent(accent, 0x1F) : accent));
        icon.setPadding(dp(8), dp(8), dp(8), dp(8));
        header.addView(icon, LayoutHelper.createLinear(40, 40, Gravity.CENTER_VERTICAL, 0, 0, 12, 0));
        LinearLayout titles = new LinearLayout(context);
        titles.setOrientation(LinearLayout.VERTICAL);
        TextView titleView = new TextView(context);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17);
        titleView.setTypeface(AndroidUtilities.bold());
        titleView.setTextColor(getThemedColor(Theme.key_dialogTextBlack));
        titleView.setSingleLine(true);
        titleView.setEllipsize(TextUtils.TruncateAt.END);
        titleView.setText(getString(R.string.OEMigTitle));
        titles.addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        TextView subtitleView = new TextView(context);
        subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        subtitleView.setTextColor(getThemedColor(Theme.key_dialogTextGray2));
        subtitleView.setSingleLine(true);
        subtitleView.setEllipsize(TextUtils.TruncateAt.END);
        subtitleView.setText(subtitle);
        titles.addView(subtitleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 1, 0, 0));
        header.addView(titles, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        container.addView(header, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);

        TextView intro = new TextView(context);
        intro.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        intro.setLineSpacing(dp(2), 1f);
        intro.setTextColor(getThemedColor(Theme.key_dialogTextBlack));
        intro.setText(UpdateSheet.format(TextUtils.isEmpty(notes) ? getString(R.string.OEMigIntro) : notes));
        content.addView(intro, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 12, 0, 10));

        exportStep = new MigrationStepView(context, resourcesProvider, "1",
                getString(R.string.OEMigExportTitle), getString(R.string.OEMigExportDesc), getString(R.string.OEMigExportButton));
        exportStep.setOnAction(v -> MigrationActions.export(activity, exportStep));
        content.addView(exportStep, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 8));

        String size = release.apkSize > 0 ? AndroidUtilities.formatFileSize(release.apkSize) : "";
        downloadStep = new MigrationStepView(context, resourcesProvider, "2",
                getString(R.string.OEMigDownloadTitle), getString(R.string.OEMigDownloadDesc),
                size.isEmpty() ? getString(R.string.OEMigDownloadButton)
                        : LocaleController.formatString(R.string.OEMigDownloadButtonSize, size));
        downloadStep.setOnAction(v -> startDownload(size));
        content.addView(downloadStep, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 8));

        removeStep = new MigrationStepView(context, resourcesProvider, "3",
                getString(R.string.OEMigRemoveTitle), getString(R.string.OEMigRemoveDesc), getString(R.string.OEMigRemoveButton));
        removeStep.setActionEnabled(false);
        removeStep.setOnAction(v -> removeOldApp());
        content.addView(removeStep, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        ScrollView scrollView = new ScrollView(context) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(
                        (int) (AndroidUtilities.displaySize.y * 0.68f), MeasureSpec.AT_MOST));
            }
        };
        scrollView.setVerticalScrollBarEnabled(false);
        scrollView.addView(content);
        container.addView(scrollView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        TextView later = new TextView(context);
        later.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        later.setTypeface(AndroidUtilities.bold());
        later.setGravity(Gravity.CENTER);
        later.setTextColor(SheetSurfaceMode.secondaryButtonText(resourcesProvider, accent));
        later.setBackground(Theme.AdaptiveRipple.filledRect(
                SheetSurfaceMode.secondaryButtonFill(resourcesProvider, accent), buttonRadius));
        later.setText(getString(R.string.OEUpdateLater));
        later.setOnClickListener(v -> dismiss());
        container.addView(later, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, m3 ? 48 : 46, 0, 12, 0, 0));

        setCustomView(container);
    }

    private void startDownload(String size) {
        if (GitHubUpdater.isDownloading()) {
            return;
        }
        downloadStep.setBusy(getString(R.string.OEMigDownloading), 0f);
        GitHubUpdater.downloadToDownloads(activity, release, new GitHubUpdater.DownloadListener() {
            @Override
            public void onProgress(long done, long total) {
                if (total > 0) {
                    downloadStep.setBusy(LocaleController.formatString(R.string.OEUpdateDownloadProgress,
                            AndroidUtilities.formatFileSize(done), AndroidUtilities.formatFileSize(total)), done / (float) total);
                }
            }

            @Override
            public void onFinished(boolean success, boolean canceled) {
                if (!success) {
                    downloadStep.reset(size.isEmpty() ? getString(R.string.OEMigDownloadButton)
                            : LocaleController.formatString(R.string.OEMigDownloadButtonSize, size));
                }
            }
        }, name -> {
            downloadStep.setDone(LocaleController.formatString(R.string.OEMigDownloaded, name));
            removeStep.setDescription(LocaleController.formatString(R.string.OEMigRemoveDescNamed, name));
            removeStep.setActionEnabled(true);
        });
    }

    private void removeOldApp() {
        try {
            Intent intent = new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + activity.getPackageName()));
            activity.startActivity(intent);
        } catch (Exception e) {
            FileLog.e("MigrationSheet: uninstall intent failed", e);
            removeStep.setDescription(getString(R.string.OEMigRemoveFailed));
        }
    }

    @Override
    public void dismiss() {
        if (GitHubUpdater.isDownloading()) {
            GitHubUpdater.cancelDownload();
        }
        super.dismiss();
    }
}
