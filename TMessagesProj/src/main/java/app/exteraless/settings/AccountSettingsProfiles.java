package app.exteraless.settings;

import android.content.Context;
import android.content.SharedPreferences;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import app.exteraless.OpenExteraConfig;
import app.exteraless.appearance.AppearanceConfig;
import app.exteraless.chats.ChatsConfig;
import app.exteraless.demo.DemoBackend;
import app.exteraless.general.GeneralConfig;
import tw.nekomimi.nekogram.NekoConfig;
import xyz.nextalone.nagram.NaConfig;

/**
 * Account-scoped profiles for the nkmrcfg settings family (ExFullGram,
 * Nagram and Neko configuration). This never snapshots Telegram authorization,
 * session databases, passcodes, or per-account server settings.
 *
 * Existing settings stay global until the user enables per-account profiles.
 * On account switch the local preference values are swapped BEFORE Telegram
 * rebuilds the UI; cached ConfigItem values are reloaded immediately.
 */
public final class AccountSettingsProfiles {
    private static final String META_FILE = "exfullgram_account_profiles_meta";
    private static final String TEMPLATE_FILE = "exfullgram_account_profiles_template";
    private static final String PROFILE_PREFIX = "exfullgram_account_profile_";
    private static final String ENABLED = "enabled";
    private static final String ACTIVE_ID = "active_id";
    private static final String INITIALIZED_PREFIX = "initialized_";
    private static final Object LOCK = new Object();

    private static boolean initialized;
    private static volatile boolean applying;
    private static String activeId;

    private static final SharedPreferences.OnSharedPreferenceChangeListener ON_CHANGE = (prefs, key) -> {
        synchronized (LOCK) {
            if (!initialized || applying || !isEnabled() || key == null || activeId == null) return;
            Object value = prefs.getAll().get(key);
            SharedPreferences.Editor edit = profile(activeId).edit();
            put(edit, key, value);
            edit.apply();
        }
    };

    private AccountSettingsProfiles() { }

    private static Context context() {
        return ApplicationLoader.applicationContext;
    }

    private static SharedPreferences meta() {
        return context().getSharedPreferences(META_FILE, Context.MODE_PRIVATE);
    }

    private static SharedPreferences live() {
        return NekoConfig.getPreferences();
    }

    private static SharedPreferences profile(String id) {
        return context().getSharedPreferences(PROFILE_PREFIX + id, Context.MODE_PRIVATE);
    }

    private static SharedPreferences template() {
        return context().getSharedPreferences(TEMPLATE_FILE, Context.MODE_PRIVATE);
    }

    public static boolean isApplying() {
        return applying;
    }

    public static boolean isEnabled() {
        return context() != null && meta().getBoolean(ENABLED, false);
    }

    /** Identity includes the test/live distinction and the real user ID. */
    public static String idFor(int slot) {
        TLRPC.User user = UserConfig.getInstance(slot).getCurrentUser();
        long id = user == null ? 0 : user.id;
        String kind = DemoBackend.isTestAccount(slot) ? "test_" : "live_";
        // Uninitialized slots may be shown in the account chooser; never merge
        // their configuration with a signed-in account.
        return kind + (id != 0 ? Long.toString(id) : "slot_" + slot);
    }

    public static String labelFor(int slot) {
        TLRPC.User user = UserConfig.getInstance(slot).getCurrentUser();
        if (user == null) return "Аккаунт " + (slot + 1);
        String first = user.first_name == null ? "" : user.first_name.trim();
        String last = user.last_name == null ? "" : user.last_name.trim();
        String label = (first + " " + last).trim();
        if (label.isEmpty()) label = "Аккаунт " + (slot + 1);
        return DemoBackend.isTestAccount(slot) ? "Test · " + label : label;
    }

    public static int[] availableAccounts() {
        ArrayList<Integer> slots = new ArrayList<>();
        for (int i = 0; i < UserConfig.MAX_ACCOUNT_COUNT; i++) {
            if (UserConfig.getInstance(i).isClientActivated() || DemoBackend.isTestAccount(i)) {
                slots.add(i);
            }
        }
        if (!slots.contains(UserConfig.selectedAccount)) slots.add(UserConfig.selectedAccount);
        int[] result = new int[slots.size()];
        for (int i = 0; i < slots.size(); i++) result[i] = slots.get(i);
        return result;
    }

    private static void put(SharedPreferences.Editor editor, String key, Object value) {
        if (value == null) editor.remove(key);
        else if (value instanceof Boolean) editor.putBoolean(key, (Boolean) value);
        else if (value instanceof Integer) editor.putInt(key, (Integer) value);
        else if (value instanceof Long) editor.putLong(key, (Long) value);
        else if (value instanceof Float) editor.putFloat(key, (Float) value);
        else if (value instanceof String) editor.putString(key, (String) value);
        else if (value instanceof Set) {
            HashSet<String> copy = new HashSet<>();
            for (Object item : (Set<?>) value) if (item instanceof String) copy.add((String) item);
            editor.putStringSet(key, copy);
        }
    }

    private static boolean replaceAll(SharedPreferences target, Map<String, ?> values) {
        SharedPreferences.Editor editor = target.edit().clear();
        for (Map.Entry<String, ?> e : values.entrySet()) {
            put(editor, e.getKey(), e.getValue());
        }
        return editor.commit();
    }

    private static void ensureProfile(String id) {
        if (meta().getBoolean(INITIALIZED_PREFIX + id, false)) return;
        // Accounts created after opt-in inherit the initial template, then
        // diverge independently. They never inherit the previous active
        // account's modifications.
        if (!replaceAll(profile(id), template().getAll())) {
            FileLog.e("ExFullGram: failed to initialize settings profile " + id);
            return;
        }
        meta().edit().putBoolean(INITIALIZED_PREFIX + id, true).commit();
    }

    private static void saveCurrent(String id) {
        if (id != null && !replaceAll(profile(id), live().getAll())) {
            FileLog.e("ExFullGram: failed to save settings profile " + id);
        }
    }

    /**
     * Startup recovery: keep the live preference file on cold launch when it
     * belongs to the same account. This also recovers changes made just before
     * Android killed the app. Reinitialize cached settings only on a switch.
     */
    public static void initialize(int selectedSlot) {
        synchronized (LOCK) {
            if (context() == null) return;
            if (!initialized) {
                live().registerOnSharedPreferenceChangeListener(ON_CHANGE);
                initialized = true;
            }
            if (!isEnabled()) return;
            String target = idFor(selectedSlot);
            String last = meta().getString(ACTIVE_ID, null);
            if (last == null) {
                // Legacy installation with profiles enabled but no active ID.
                saveCurrent(target);
                meta().edit().putBoolean(INITIALIZED_PREFIX + target, true)
                        .putString(ACTIVE_ID, target).commit();
                activeId = target;
            } else if (last.equals(target)) {
                saveCurrent(target);
                activeId = target;
            } else {
                switchInternal(last, target);
            }
        }
    }

    public static void setEnabled(boolean enabled, int selectedSlot) {
        synchronized (LOCK) {
            initialize(selectedSlot);
            if (enabled == isEnabled()) return;
            if (enabled) {
                String id = idFor(selectedSlot);
                if (!meta().contains("template_initialized")) {
                    replaceAll(template(), live().getAll());
                    meta().edit().putBoolean("template_initialized", true).commit();
                }
                saveCurrent(id);
                meta().edit().putBoolean(ENABLED, true)
                        .putBoolean(INITIALIZED_PREFIX + id, true)
                        .putString(ACTIVE_ID, id).commit();
                activeId = id;
            } else {
                if (activeId != null) saveCurrent(activeId);
                meta().edit().putBoolean(ENABLED, false).commit();
            }
        }
    }

    /** Called before UserConfig.selectedAccount is updated by LaunchActivity. */
    public static void beforeAccountSwitch(int oldSlot, int newSlot) {
        synchronized (LOCK) {
            initialize(oldSlot);
            if (!isEnabled()) return;
            String oldId = idFor(oldSlot);
            String nextId = idFor(newSlot);
            if (oldId.equals(nextId)) return;
            switchInternal(oldId, nextId);
        }
    }

    private static void switchInternal(String oldId, String targetId) {
        if (!isEnabled()) return;
        applying = true;
        try {
            saveCurrent(oldId);
            ensureProfile(targetId);
            if (!replaceAll(live(), profile(targetId).getAll())) {
                FileLog.e("ExFullGram: could not apply account settings " + targetId);
                // Keep the old active ID; retry can occur on next initialization.
                return;
            }
            activeId = targetId;
            meta().edit().putString(ACTIVE_ID, targetId).commit();
            reloadConfigCaches();
        } finally {
            applying = false;
        }
    }

    private static void reloadConfigCaches() {
        NekoConfig.loadConfig(true);
        NaConfig.INSTANCE.loadConfig(true);
        OpenExteraConfig.INSTANCE.loadConfig(true);
        AppearanceConfig.INSTANCE.loadConfig(true);
        ChatsConfig.INSTANCE.loadConfig(true);
        GeneralConfig.INSTANCE.loadConfig(true);
    }

    public static void refreshCurrent() {
        synchronized (LOCK) {
            if (!isEnabled()) return;
            initialize(UserConfig.selectedAccount);
            String current = idFor(UserConfig.selectedAccount);
            saveCurrent(current);
        }
    }

    public static Map<String, Object> snapshot(int slot) {
        synchronized (LOCK) {
            if (!isEnabled()) return new HashMap<>(live().getAll());
            initialize(UserConfig.selectedAccount);
            if (slot == UserConfig.selectedAccount) refreshCurrent();
            String id = idFor(slot);
            ensureProfile(id);
            return new LinkedHashMap<>(profile(id).getAll());
        }
    }

    public static final class Difference {
        public final String key;
        public final Object left;
        public final Object right;

        Difference(String key, Object left, Object right) {
            this.key = key;
            this.left = left;
            this.right = right;
        }

        public String getLabel() {
            String value = key.replaceFirst("^OEAppearance", "Оформление · ")
                    .replaceFirst("^OEChats", "Чаты · ")
                    .replaceFirst("^OEGeneral", "Общие · ")
                    .replaceFirst("^OE", "ExFullGram · ");
            value = value.replaceAll("([a-z0-9])([A-Z])", "$1 $2")
                    .replaceAll("([A-Z])([A-Z][a-z])", "$1 $2")
                    .replace('_', ' ');
            return value;
        }

        public static String valueLabel(String key, Object value) {
            if (value == null) return "По умолчанию";
            String lower = key.toLowerCase(java.util.Locale.ROOT);
            if (lower.contains("token") || lower.contains("apikey") ||
                    lower.contains("api_key") || lower.contains("password") ||
                    lower.contains("secret") || lower.contains("auth")) {
                return "••••••";
            }
            if (value instanceof Boolean) return (Boolean) value ? "Включено" : "Выключено";
            if (value instanceof Set) return "Элементов: " + ((Set<?>) value).size();
            String s = String.valueOf(value);
            return s.length() > 80 ? s.substring(0, 77) + "…" : s;
        }
    }

    public static List<Difference> compare(int leftSlot, int rightSlot) {
        Map<String, Object> left = snapshot(leftSlot);
        Map<String, Object> right = snapshot(rightSlot);
        HashSet<String> keys = new HashSet<>(left.keySet());
        keys.addAll(right.keySet());
        ArrayList<String> ordered = new ArrayList<>(keys);
        Collections.sort(ordered, String.CASE_INSENSITIVE_ORDER);
        ArrayList<Difference> differences = new ArrayList<>();
        for (String key : ordered) {
            if (!Objects.equals(left.get(key), right.get(key))) {
                differences.add(new Difference(key, left.get(key), right.get(key)));
            }
        }
        return differences;
    }

    /**
     * A direction maps a key to 1 (left wins) or 2 (right wins).
     * Changes are applied in one transaction per target, leaving every other
     * key untouched. Live settings reload if the active account is affected.
     */
    public static int mergeSelected(int leftSlot, int rightSlot, Map<String, Integer> directions) {
        synchronized (LOCK) {
            if (!isEnabled() || leftSlot == rightSlot || directions.isEmpty()) return 0;
            Map<String, Object> left = snapshot(leftSlot);
            Map<String, Object> right = snapshot(rightSlot);
            SharedPreferences.Editor leftEdit = profile(idFor(leftSlot)).edit();
            SharedPreferences.Editor rightEdit = profile(idFor(rightSlot)).edit();
            int count = 0;
            for (Map.Entry<String, Integer> e : directions.entrySet()) {
                if (e.getValue() == 1) {
                    put(rightEdit, e.getKey(), left.get(e.getKey()));
                    count++;
                } else if (e.getValue() == 2) {
                    put(leftEdit, e.getKey(), right.get(e.getKey()));
                    count++;
                }
            }
            if (count == 0 || !leftEdit.commit() || !rightEdit.commit()) return 0;
            applyActiveAfterEdit(leftSlot, rightSlot);
            return count;
        }
    }

    public static boolean resetProfile(int slot) {
        synchronized (LOCK) {
            if (!isEnabled()) return false;
            initialize(UserConfig.selectedAccount);
            String id = idFor(slot);
            if (!profile(id).edit().clear().commit()) return false;
            meta().edit().putBoolean(INITIALIZED_PREFIX + id, true).commit();
            applyActiveAfterEdit(slot, slot);
            return true;
        }
    }

    private static void applyActiveAfterEdit(int slotA, int slotB) {
        int selected = UserConfig.selectedAccount;
        if (selected != slotA && selected != slotB) return;
        applying = true;
        try {
            if (replaceAll(live(), profile(idFor(selected)).getAll())) {
                reloadConfigCaches();
            }
        } finally {
            applying = false;
        }
    }
}
