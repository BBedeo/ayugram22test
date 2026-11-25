package app.exteraless.chats;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * Local folders inside the archive: named tabs over the archived chats. Everything lives on the device
 * (never sent to Telegram), keyed by account slot + Telegram user id so a reused slot does not inherit them.
 * The archive list itself is the normal DialogsActivity list: it only asks {@link #filter} for the visible chats.
 */
public final class ArchiveFolders {

    public static final class Folder {
        public final long id;
        public String name;

        Folder(long id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    private static final class State {
        final ArrayList<Folder> folders = new ArrayList<>();
        final HashMap<Long, Long> assignment = new HashMap<>();
        long selected;
        boolean loaded;
    }

    private static final State[] STATES = new State[UserConfig.MAX_ACCOUNT_COUNT];

    private ArchiveFolders() {
    }

    public static boolean enabled() {
        return ChatsConfig.archiveFolders();
    }

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences("exteraless_archive_folders", Context.MODE_PRIVATE);
    }

    private static String key(int account) {
        return "u" + UserConfig.getInstance(account).getClientUserId();
    }

    private static State state(int account) {
        State s = STATES[account];
        if (s == null) {
            s = new State();
            STATES[account] = s;
        }
        if (!s.loaded) {
            s.loaded = true;
            try {
                final String raw = prefs().getString(key(account), null);
                if (raw != null) {
                    final JSONObject o = new JSONObject(raw);
                    final JSONArray f = o.optJSONArray("f");
                    for (int i = 0; f != null && i < f.length(); i++) {
                        final JSONObject j = f.getJSONObject(i);
                        s.folders.add(new Folder(j.getLong("i"), j.getString("n")));
                    }
                    final JSONObject a = o.optJSONObject("a");
                    if (a != null) {
                        final java.util.Iterator<String> it = a.keys();
                        while (it.hasNext()) {
                            final String k = it.next();
                            s.assignment.put(Long.parseLong(k), a.getLong(k));
                        }
                    }
                }
            } catch (Exception ignore) {
                // a corrupted blob only loses the local folders, never the archive itself
            }
        }
        return s;
    }

    private static void save(int account) {
        final State s = state(account);
        try {
            final JSONObject o = new JSONObject();
            final JSONArray f = new JSONArray();
            for (Folder folder : s.folders) {
                f.put(new JSONObject().put("i", folder.id).put("n", folder.name));
            }
            final JSONObject a = new JSONObject();
            for (java.util.Map.Entry<Long, Long> e : s.assignment.entrySet()) {
                a.put(String.valueOf(e.getKey()), e.getValue());
            }
            o.put("f", f).put("a", a);
            prefs().edit().putString(key(account), o.toString()).apply();
        } catch (Exception ignore) {
        }
    }

    public static ArrayList<Folder> folders(int account) {
        return state(account).folders;
    }

    public static long selected(int account) {
        final State s = state(account);
        if (s.selected != 0) {
            for (Folder f : s.folders) {
                if (f.id == s.selected) {
                    return s.selected;
                }
            }
            s.selected = 0;
        }
        return 0;
    }

    /** Chats of the archive that belong to the selected folder; the source list itself when no folder is selected. */
    public static ArrayList<TLRPC.Dialog> filter(int account, ArrayList<TLRPC.Dialog> source) {
        final long sel = selected(account);
        if (sel == 0) {
            return source;
        }
        final HashMap<Long, Long> map = state(account).assignment;
        final ArrayList<TLRPC.Dialog> out = new ArrayList<>();
        for (int i = 0, n = source.size(); i < n; i++) {
            final TLRPC.Dialog d = source.get(i);
            final Long f = map.get(d.id);
            if (f != null && f == sel) {
                out.add(d);
            }
        }
        return out;
    }

    private static void changed(int account) {
        save(account);
        NotificationCenter.getInstance(account).postNotificationName(NotificationCenter.dialogsNeedReload);
    }

    public static void select(int account, long folderId) {
        state(account).selected = folderId;
        NotificationCenter.getInstance(account).postNotificationName(NotificationCenter.dialogsNeedReload);
    }

    private static void create(int account, String name) {
        final State s = state(account);
        final Folder f = new Folder(System.currentTimeMillis(), name);
        s.folders.add(f);
        s.selected = f.id;
        changed(account);
    }

    private static void rename(int account, Folder f, String name) {
        f.name = name;
        changed(account);
    }

    private static void delete(int account, Folder f) {
        final State s = state(account);
        s.folders.remove(f);
        s.assignment.values().removeIf(v -> v == f.id);
        if (s.selected == f.id) {
            s.selected = 0;
        }
        changed(account);
    }

    public static void assign(int account, ArrayList<Long> dialogIds, long folderId) {
        final State s = state(account);
        for (Long id : dialogIds) {
            if (folderId == 0) {
                s.assignment.remove(id);
            } else {
                s.assignment.put(id, folderId);
            }
        }
        changed(account);
    }

    private static String str(int res) {
        return LocaleController.getString(res);
    }

    private static void askName(BaseFragment fragment, String title, String initial, Runnable2 done) {
        if (fragment.getParentActivity() == null) {
            return;
        }
        final EditText edit = new EditText(fragment.getParentActivity());
        edit.setSingleLine(true);
        edit.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        edit.setText(initial);
        edit.setSelection(edit.getText().length());
        edit.setHint(str(R.string.OEArchiveFolderName));
        edit.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, fragment.getResourceProvider()));
        edit.setHintTextColor(Theme.getColor(Theme.key_dialogTextHint, fragment.getResourceProvider()));
        final FrameLayout box = new FrameLayout(fragment.getParentActivity());
        box.addView(edit, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 24, 0, 24, 0));
        final AlertDialog.Builder b = new AlertDialog.Builder(fragment.getParentActivity(), fragment.getResourceProvider());
        b.setTitle(title);
        b.setView(box);
        b.setPositiveButton(str(R.string.Save), (d, w) -> {
            final String name = edit.getText().toString().trim();
            if (!name.isEmpty()) {
                done.run(name);
            }
        });
        b.setNegativeButton(str(R.string.Cancel), null);
        fragment.showDialog(b.create());
    }

    private interface Runnable2 {
        void run(String name);
    }

    /** Folder picker for the selected archived chats ("Move to folder"). */
    public static void showMoveDialog(BaseFragment fragment, int account, ArrayList<Long> dialogIds) {
        if (fragment.getParentActivity() == null) {
            return;
        }
        final ArrayList<Folder> list = folders(account);
        final ArrayList<CharSequence> names = new ArrayList<>();
        names.add(str(R.string.OEArchiveFolderNone));
        for (Folder f : list) {
            names.add(f.name);
        }
        names.add("+ " + str(R.string.OEArchiveFolderNew));
        final ArrayList<Long> ids = new ArrayList<>(dialogIds);
        final AlertDialog.Builder b = new AlertDialog.Builder(fragment.getParentActivity(), fragment.getResourceProvider());
        b.setTitle(str(R.string.OEArchiveFolderMoveTo));
        b.setItems(names.toArray(new CharSequence[0]), (d, which) -> {
            if (which == 0) {
                assign(account, ids, 0);
            } else if (which <= list.size()) {
                assign(account, ids, list.get(which - 1).id);
            } else {
                askName(fragment, str(R.string.OEArchiveFolderNew), "", name -> {
                    create(account, name);
                    assign(account, ids, selected(account));
                });
            }
        });
        b.setNegativeButton(str(R.string.Cancel), null);
        fragment.showDialog(b.create());
    }

    /** The chip row shown above the archive list: "All" + folders + "+". Rebuilt on every change. */
    public static final class Bar extends FrameLayout implements NotificationCenter.NotificationCenterDelegate {

        private final int account;
        private final BaseFragment fragment;
        private final LinearLayout row;

        public Bar(Context context, BaseFragment fragment, int account) {
            super(context);
            this.fragment = fragment;
            this.account = account;
            final HorizontalScrollView scroll = new HorizontalScrollView(context);
            scroll.setHorizontalScrollBarEnabled(false);
            scroll.setOverScrollMode(OVER_SCROLL_NEVER);
            row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(8), 0, dp(8), 0);
            scroll.addView(row, new FrameLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT));
            addView(scroll, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
            rebuild();
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(dp(44), MeasureSpec.EXACTLY));
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            NotificationCenter.getInstance(account).addObserver(this, NotificationCenter.dialogsNeedReload);
            rebuild();
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            NotificationCenter.getInstance(account).removeObserver(this, NotificationCenter.dialogsNeedReload);
        }

        @Override
        public void didReceivedNotification(int id, int account, Object... args) {
            if (id == NotificationCenter.dialogsNeedReload) {
                rebuild();
            }
        }

        private void rebuild() {
            row.removeAllViews();
            final long sel = selected(account);
            addChip(str(R.string.OEArchiveFolderAll), sel == 0, v -> select(account, 0), null);
            for (Folder f : folders(account)) {
                addChip(f.name, sel == f.id, v -> select(account, f.id), v -> {
                    showFolderMenu(f);
                    return true;
                });
            }
            addChip("+", false, v -> askName(fragment, str(R.string.OEArchiveFolderNew), "", name -> create(account, name)), null);
        }

        private void showFolderMenu(Folder f) {
            if (fragment.getParentActivity() == null) {
                return;
            }
            final AlertDialog.Builder b = new AlertDialog.Builder(fragment.getParentActivity(), fragment.getResourceProvider());
            b.setTitle(f.name);
            b.setItems(new CharSequence[]{str(R.string.OEArchiveFolderRename), str(R.string.Delete)}, (d, which) -> {
                if (which == 0) {
                    askName(fragment, str(R.string.OEArchiveFolderRename), f.name, name -> rename(account, f, name));
                } else {
                    delete(account, f);
                }
            });
            b.setNegativeButton(str(R.string.Cancel), null);
            fragment.showDialog(b.create());
        }

        private interface LongClick {
            boolean on(View v);
        }

        private void addChip(String text, boolean active, OnClickListener click, LongClick longClick) {
            final TextView chip = new TextView(getContext());
            chip.setText(text);
            chip.setSingleLine(true);
            chip.setGravity(Gravity.CENTER);
            chip.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            chip.setTypeface(active ? AndroidUtilities.bold() : Typeface.DEFAULT);
            chip.setPadding(dp(14), 0, dp(14), 0);
            final int accent = Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4);
            chip.setTextColor(active ? accent : Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            final GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(dp(16));
            bg.setColor(active ? (accent & 0x00FFFFFF) | 0x26000000 : 0);
            chip.setBackground(bg);
            chip.setOnClickListener(click);
            if (longClick != null) {
                chip.setOnLongClickListener(longClick::on);
            }
            row.addView(chip, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 32, Gravity.CENTER_VERTICAL, 2, 0, 2, 0));
        }
    }
}
