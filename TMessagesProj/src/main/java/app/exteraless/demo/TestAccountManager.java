package app.exteraless.demo;

import android.content.Context;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.ItemOptions;

/**
 * Local Test Account controls for ExFullGram.
 */
public final class TestAccountManager {
    private TestAccountManager() {}

    public static void show(BaseFragment fragment, View anchor, int currentAccount) {
        if (fragment == null || anchor == null || !DemoBackend.isTestAccount(currentAccount)) {
            return;
        }

        ItemOptions options = ItemOptions.makeOptions(fragment, anchor);
        options.add(
                R.drawable.msg_addcontact,
                LocaleController.getString(R.string.ExFullGramTestAddAccount),
                () -> {
                    options.dismiss();
                    promptAdd(fragment);
                }
        );
        options.add(
                R.drawable.msg_retry_solar,
                LocaleController.getString(
                        DemoBackend.isTestAccountOnline(currentAccount)
                                ? R.string.ExFullGramTestGoOffline
                                : R.string.ExFullGramTestGoOnline
                ),
                () -> {
                    DemoBackend.setTestAccountOnline(
                            currentAccount,
                            !DemoBackend.isTestAccountOnline(currentAccount)
                    );
                    options.dismiss();
                }
        );
        options.show();
    }

    public static void promptAdd(BaseFragment fragment) {
        if (fragment == null || fragment.getParentActivity() == null) {
            return;
        }

        final Context context = fragment.getParentActivity();
        final EditText input = new EditText(context);
        input.setHint(LocaleController.getString(R.string.ExFullGramTestAccountName));
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        int padding = org.telegram.messenger.AndroidUtilities.dp(24);
        input.setPadding(padding, 0, padding, 0);

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle(LocaleController.getString(R.string.ExFullGramTestAddAccount))
                .setView(input)
                .setNegativeButton(LocaleController.getString(R.string.Cancel), null)
                .setPositiveButton(LocaleController.getString(R.string.Add), (d, which) -> {
                    int slot = DemoBackend.addTestAccount(input.getText().toString());
                    if (slot < 0) {
                        Toast.makeText(
                                context,
                                LocaleController.getString(R.string.ExFullGramTestNoFreeSlots),
                                Toast.LENGTH_SHORT
                        ).show();
                    } else {
                        Toast.makeText(
                                context,
                                LocaleController.getString(R.string.ExFullGramTestAccountCreated),
                                Toast.LENGTH_SHORT
                        ).show();
                    }
                })
                .create();

        fragment.showDialog(dialog);
    }
}
