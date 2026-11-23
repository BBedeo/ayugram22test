package app.exteraless.updater;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.app.Activity;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.LaunchActivity;

import app.exteraless.appearance.SheetSurfaceMode;
import app.exteraless.backup.MigrationBackup;

/**
 * Shown once on a fresh install: if a settings backup made by the previous install lies in
 * Downloads, offer to restore it. Styled by the active preset like the update sheet.
 */
public class RestoreBackupSheet extends BottomSheet {

    /** One search per process: onResume fires often and the query touches storage. */
    private static boolean searching;

    private final Activity activity;
    private final MigrationStepView step;
    private final MigrationBackup.Candidate candidate;

    private RestoreBackupSheet(Activity activity, Theme.ResourcesProvider resourcesProvider, MigrationBackup.Candidate candidate) {
        super(activity, false, resourcesProvider);
        this.activity = activity;
        this.candidate = candidate;
        fixNavigationBar();

        final boolean m3 = SheetSurfaceMode.isM3(resourcesProvider);
        final int accent = SheetSurfaceMode.accent(resourcesProvider);
        final float buttonRadius = SheetSurfaceMode.buttonRadiusDp(resourcesProvider);

        LinearLayout container = new LinearLayout(activity);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dp(m3 ? 24 : 18), dp(m3 ? 12 : 10), dp(m3 ? 24 : 18), dp(m3 ? 20 : 14));

        LinearLayout header = new LinearLayout(activity);
        header.setGravity(Gravity.CENTER_VERTICAL);
        ImageView icon = new ImageView(activity);
        icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        icon.setImageResource(R.drawable.msg_download);
        icon.setColorFilter(new PorterDuffColorFilter(
                m3 ? accent : SheetSurfaceMode.onAccent(resourcesProvider), PorterDuff.Mode.SRC_IN));
        icon.setBackground(Theme.createRoundRectDrawable(dp(m3 ? 14 : 20),
                m3 ? ColorUtils.setAlphaComponent(accent, 0x1F) : accent));
        icon.setPadding(dp(8), dp(8), dp(8), dp(8));
        header.addView(icon, LayoutHelper.createLinear(40, 40, Gravity.CENTER_VERTICAL, 0, 0, 12, 0));
        TextView title = new TextView(activity);
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17);
        title.setTypeface(AndroidUtilities.bold());
        title.setTextColor(getThemedColor(Theme.key_dialogTextBlack));
        title.setText(getString(R.string.OEMigRestoreTitle));
        header.addView(title, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
        container.addView(header, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        String when = DateUtils.formatDateTime(activity, candidate.modified,
                DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_SHOW_TIME | DateUtils.FORMAT_ABBREV_MONTH);
        step = new MigrationStepView(activity, resourcesProvider, "",
                getString(R.string.OEMigRestoreFound),
                LocaleController.formatString(R.string.OEMigRestoreDesc, when, candidate.name),
                getString(R.string.OEMigRestoreButton));
        step.setOnAction(v -> restore());
        container.addView(step, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 14, 0, 10));

        LinearLayout buttons = new LinearLayout(activity);
        TextView choose = secondary(resourcesProvider, accent, buttonRadius, getString(R.string.OEMigRestoreChoose));
        choose.setOnClickListener(v -> {
            MigrationActions.pickFile(activity);
            dismiss();
        });
        buttons.addView(choose, LayoutHelper.createLinear(0, m3 ? 48 : 46, 1f, Gravity.NO_GRAVITY, 0, 0, 8, 0));
        TextView later = secondary(resourcesProvider, accent, buttonRadius, getString(R.string.OEMigRestoreNotNow));
        later.setOnClickListener(v -> dismiss());
        buttons.addView(later, LayoutHelper.createLinear(0, m3 ? 48 : 46, 1f));
        container.addView(buttons, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        setCustomView(container);
    }

    private TextView secondary(Theme.ResourcesProvider provider, int accent, float radius, CharSequence text) {
        TextView view = new TextView(activity);
        view.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        view.setTypeface(AndroidUtilities.bold());
        view.setGravity(Gravity.CENTER);
        view.setSingleLine(true);
        view.setEllipsize(TextUtils.TruncateAt.END);
        view.setPadding(dp(8), 0, dp(8), 0);
        view.setTextColor(SheetSurfaceMode.secondaryButtonText(provider, accent));
        view.setBackground(Theme.AdaptiveRipple.filledRect(SheetSurfaceMode.secondaryButtonFill(provider, accent), radius));
        view.setText(text);
        return view;
    }

    private void restore() {
        step.setBusy(getString(R.string.OEMigRestoring), -1f);
        new Thread(() -> {
            boolean ok;
            try {
                ok = MigrationActions.restore(activity, MigrationBackup.read(activity, candidate));
            } catch (Throwable e) {
                FileLog.e("RestoreBackupSheet: read failed", e);
                ok = false;
            }
            final boolean success = ok;
            AndroidUtilities.runOnUIThread(() -> {
                if (success) {
                    step.setDone(getString(R.string.OEMigRestored));
                } else {
                    step.reset(getString(R.string.OEMigRestoreButton));
                    step.setDescription(getString(R.string.OEMigRestoreFailed));
                }
            });
        }, "migration-restore").start();
    }

    /** Result of the "choose another file" picker, forwarded from LaunchActivity.onActivityResult. */
    public static boolean onActivityResult(Activity activity, int requestCode, int resultCode, android.content.Intent data) {
        return MigrationActions.onPicked(activity, requestCode, resultCode, data);
    }

    /** Called when the main activity resumes; does nothing unless this is a fresh, signed-out install with a backup. */
    public static void maybeOffer(Activity activity) {
        if (activity == null || !MigrationBackup.shouldOfferRestore(activity)) {
            return;
        }
        if (UserConfig.getActivatedAccountsCount() > 0) {
            MigrationBackup.markRestoreChecked(activity);
            return;
        }
        if (LaunchActivity.getSafeLastFragment() == null) {
            return;
        }
        if (searching) {
            return;
        }
        searching = true;
        new Thread(() -> {
            MigrationBackup.Candidate found = MigrationBackup.findLatest(activity);
            if (found == null) {
                // Not found now (e.g. storage not readable yet): try again on a later start, not on every resume.
                return;
            }
            AndroidUtilities.runOnUIThread(() -> {
                BaseFragment fragment = LaunchActivity.getSafeLastFragment();
                if (fragment == null || fragment.getParentActivity() != activity || activity.isFinishing()) {
                    searching = false;
                    return;
                }
                MigrationBackup.markRestoreChecked(activity);
                new RestoreBackupSheet(activity, fragment.getResourceProvider(), found).show();
            });
        }, "migration-find").start();
    }
}
