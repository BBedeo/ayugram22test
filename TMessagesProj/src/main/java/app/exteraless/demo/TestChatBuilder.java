package app.exteraless.demo;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.ColorDrawable;
import android.text.InputType;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;

/**
 * Small local-only chat generator used by ExFullGram Test Mode.
 */
public final class TestChatBuilder {
    private TestChatBuilder() {}

    public static void show(BaseFragment fragment, int account) {
        if (fragment == null || fragment.getParentActivity() == null || !DemoBackend.isTestAccount(account)) {
            return;
        }

        final Context context = fragment.getParentActivity();
        final LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        int pad = AndroidUtilities.dp(20);
        content.setPadding(pad, AndroidUtilities.dp(4), pad, AndroidUtilities.dp(8));

        final Spinner preset = createSpinner(context, new String[]{
                LocaleController.getString(R.string.ExFullGramTestPresetCustom),
                LocaleController.getString(R.string.ExFullGramTestPresetEmpty),
                LocaleController.getString(R.string.ExFullGramTestPresetBusyGroup),
                LocaleController.getString(R.string.ExFullGramTestPresetLargeChannel),
                LocaleController.getString(R.string.ExFullGramTestPresetLongContent),
                LocaleController.getString(R.string.ExFullGramTestPresetFlood)
        });
        addLabel(content, context, LocaleController.getString(R.string.ExFullGramTestPreset));
        content.addView(preset, rowParams());

        final Spinner type = createSpinner(context, new String[]{
                LocaleController.getString(R.string.ExFullGramTestTypePrivate),
                LocaleController.getString(R.string.ExFullGramTestTypeGroup),
                LocaleController.getString(R.string.ExFullGramTestTypeChannel)
        });
        addLabel(content, context, LocaleController.getString(R.string.ExFullGramTestChatType));
        content.addView(type, rowParams());

        final EditText name = createTextField(context, LocaleController.getString(R.string.ExFullGramTestChatName), false);
        content.addView(name, rowParams());

        final EditText description = createTextField(context, LocaleController.getString(R.string.ExFullGramTestChatDescription), false);
        description.setMinLines(2);
        description.setMaxLines(4);
        content.addView(description, rowParams());

        final EditText participants = createTextField(context, LocaleController.getString(R.string.ExFullGramTestParticipants), true);
        participants.setText("20");
        content.addView(participants, rowParams());

        final EditText messages = createTextField(context, LocaleController.getString(R.string.ExFullGramTestMessages), true);
        messages.setText("20");
        content.addView(messages, rowParams());

        final EditText unread = createTextField(context, LocaleController.getString(R.string.ExFullGramTestUnreadMessages), true);
        unread.setText("5");
        content.addView(unread, rowParams());

        preset.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                applyPreset(position, type, name, description, participants, messages, unread);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        type.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                participants.setEnabled(position != DemoBackend.TYPE_PRIVATE);
                if (position == DemoBackend.TYPE_PRIVATE) {
                    participants.setText("2");
                } else if (parseInt(participants.getText().toString(), 2) <= 2) {
                    participants.setText(position == DemoBackend.TYPE_CHANNEL ? "1000" : "20");
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        ScrollView scroll = new ScrollView(context);
        scroll.addView(content);

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle(LocaleController.getString(R.string.ExFullGramTestChatBuilderTitle))
                .setView(scroll)
                .setNegativeButton(LocaleController.getString(R.string.Cancel), null)
                .setPositiveButton(LocaleController.getString(R.string.ExFullGramTestCreate), (d, which) -> {
                    int selectedType = type.getSelectedItemPosition();
                    int selectedPreset = preset.getSelectedItemPosition();
                    int participantCount = parseInt(participants.getText().toString(), selectedType == DemoBackend.TYPE_PRIVATE ? 2 : 20);
                    int messageCount = parseInt(messages.getText().toString(), 20);
                    int unreadCount = parseInt(unread.getText().toString(), 0);
                    long dialogId = DemoBackend.createTestChat(
                            account,
                            selectedType,
                            name.getText().toString(),
                            description.getText().toString(),
                            participantCount,
                            messageCount,
                            unreadCount,
                            selectedPreset
                    );
                    if (dialogId != 0) {
                        Toast.makeText(context, LocaleController.getString(R.string.ExFullGramTestCreated), Toast.LENGTH_SHORT).show();
                    }
                })
                .create();

        fragment.showDialog(dialog);
    }

    private static void applyPreset(
            int preset,
            Spinner type,
            EditText name,
            EditText description,
            EditText participants,
            EditText messages,
            EditText unread
    ) {
        switch (preset) {
            case DemoBackend.PRESET_EMPTY:
                messages.setText("0");
                unread.setText("0");
                participants.setText(type.getSelectedItemPosition() == DemoBackend.TYPE_PRIVATE ? "2" : "5");
                break;
            case DemoBackend.PRESET_BUSY_GROUP:
                type.setSelection(DemoBackend.TYPE_GROUP);
                participants.setText("250");
                messages.setText("100");
                unread.setText("25");
                if (name.length() == 0) {
                    name.setText(LocaleController.getString(R.string.ExFullGramTestPresetBusyGroupName));
                }
                break;
            case DemoBackend.PRESET_LARGE_CHANNEL:
                type.setSelection(DemoBackend.TYPE_CHANNEL);
                participants.setText("50000");
                messages.setText("50");
                unread.setText("10");
                if (name.length() == 0) {
                    name.setText(LocaleController.getString(R.string.ExFullGramTestPresetLargeChannelName));
                }
                break;
            case DemoBackend.PRESET_LONG_CONTENT:
                type.setSelection(DemoBackend.TYPE_GROUP);
                participants.setText("42");
                messages.setText("30");
                unread.setText("8");
                if (name.length() == 0) {
                    name.setText(LocaleController.getString(R.string.ExFullGramTestPresetLongContentName));
                }
                if (description.length() == 0) {
                    description.setText(LocaleController.getString(R.string.ExFullGramTestPresetLongContentDescription));
                }
                break;
            case DemoBackend.PRESET_FLOOD:
                type.setSelection(DemoBackend.TYPE_GROUP);
                participants.setText("1000");
                messages.setText("500");
                unread.setText("100");
                if (name.length() == 0) {
                    name.setText(LocaleController.getString(R.string.ExFullGramTestPresetFloodName));
                }
                break;
            case DemoBackend.PRESET_CUSTOM:
            default:
                break;
        }
    }

    private static Spinner createSpinner(Context context, String[] values) {
        Spinner spinner = new Spinner(context);
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(context, android.R.layout.simple_spinner_item, values) {
            @Override
            public View getView(int position, View convertView, android.view.ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                styleSpinnerText(view, false);
                return view;
            }

            @Override
            public View getDropDownView(int position, View convertView, android.view.ViewGroup parent) {
                View view = super.getDropDownView(position, convertView, parent);
                styleSpinnerText(view, true);
                return view;
            }
        };
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setPopupBackgroundDrawable(new ColorDrawable(Theme.getColor(Theme.key_dialogBackground)));
        return spinner;
    }

    private static void styleSpinnerText(View view, boolean dropdown) {
        if (!(view instanceof TextView)) {
            return;
        }
        TextView textView = (TextView) view;
        textView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        textView.setTextSize(16);
        textView.setGravity(android.view.Gravity.CENTER_VERTICAL);
        textView.setMinHeight(AndroidUtilities.dp(48));
        textView.setPadding(
                AndroidUtilities.dp(dropdown ? 20 : 8),
                0,
                AndroidUtilities.dp(16),
                0
        );
        if (dropdown) {
            textView.setBackgroundColor(Theme.getColor(Theme.key_dialogBackground));
        }
    }

    private static EditText createTextField(Context context, String hint, boolean numeric) {
        EditText editText = new EditText(context);
        editText.setHint(hint);
        editText.setTextSize(16);
        editText.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        editText.setHintTextColor(Theme.getColor(Theme.key_dialogTextHint));
        editText.setBackgroundTintList(new ColorStateList(
                new int[][]{
                        new int[]{android.R.attr.state_focused},
                        new int[]{}
                },
                new int[]{
                        Theme.getColor(Theme.key_dialogInputFieldActivated),
                        Theme.getColor(Theme.key_dialogInputField)
                }
        ));
        editText.setSingleLine(numeric);
        if (numeric) {
            editText.setInputType(InputType.TYPE_CLASS_NUMBER);
        } else {
            editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        }
        return editText;
    }

    private static void addLabel(LinearLayout parent, Context context, String value) {
        TextView label = new TextView(context);
        label.setText(value);
        label.setTextSize(13);
        label.setTextColor(Theme.getColor(Theme.key_dialogTextHint));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.topMargin = AndroidUtilities.dp(10);
        parent.addView(label, params);
    }

    private static LinearLayout.LayoutParams rowParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.topMargin = AndroidUtilities.dp(4);
        return params;
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception e) {
            return fallback;
        }
    }
}
