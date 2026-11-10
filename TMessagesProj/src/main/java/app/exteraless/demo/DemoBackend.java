package app.exteraless.demo;

import android.content.Context;
import android.content.SharedPreferences;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationCenter;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ChatActivity;

import java.util.ArrayList;

/**
 * Local-only account data for ExFullGram development.
 *
 * This intentionally uses Telegram's real MessagesStorage and TLRPC models,
 * so normal UI screens render demo data through the same paths as cached
 * production data. No demo message is sent to Telegram.
 */
public final class DemoBackend {
    private static final String PREFS = "exfullgram_demo";
    private static final String KEY_ENABLED = "enabled";

    public static final long SELF_ID = 9_900_000_001L;
    private static final long ALICE_ID = 9_900_000_101L;
    private static final long MAX_ID = 9_900_000_102L;
    private static final long QA_ID = 9_900_000_103L;

    private DemoBackend() {
    }

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static boolean isEnabled() {
        return prefs().getBoolean(KEY_ENABLED, false);
    }

    public static void setEnabled(boolean enabled) {
        prefs().edit().putBoolean(KEY_ENABLED, enabled).apply();
    }

    public static TLRPC.TL_user createSelf() {
        TLRPC.TL_user user = new TLRPC.TL_user();
        user.id = SELF_ID;
        user.first_name = "ExFullGram";
        user.last_name = "Demo";
        user.phone = "9990000000";
        user.self = true;
        return user;
    }

    private static TLRPC.TL_user createUser(long id, String firstName, String lastName) {
        TLRPC.TL_user user = new TLRPC.TL_user();
        user.id = id;
        user.first_name = firstName;
        user.last_name = lastName;
        return user;
    }

    public static void seed(int account, TLRPC.User self) {
        setEnabled(true);

        TLRPC.TL_user alice = createUser(ALICE_ID, "Alice", "Demo");
        TLRPC.TL_user max = createUser(MAX_ID, "Max", "Tester");
        TLRPC.TL_user qa = createUser(QA_ID, "ExFullGram", "QA");

        ArrayList<TLRPC.User> users = new ArrayList<>();
        users.add(self);
        users.add(alice);
        users.add(max);
        users.add(qa);

        MessagesController.getInstance(account).putUsers(users, false);
        MessagesStorage.getInstance(account).putUsersAndChats(users, null, true, true);

        int now = (int) (System.currentTimeMillis() / 1000L);

        seedUserDialog(account, self, alice, now - 40,
                new String[]{
                        "Привет. Это локальный Demo Backend ExFullGram.",
                        "Можешь спокойно ломать интерфейс — эти сообщения никуда не отправляются.",
                        "Попробуй открыть профиль, поиск по чату и меню сообщения."
                });

        seedUserDialog(account, self, max, now - 240,
                new String[]{
                        "Тестовый чат №2.",
                        "Здесь потом добавим редактирование, удаление, реакции и ответы.",
                        "После этого можно будет имитировать задержку сети и статус «печатает…»."
                });

        seedUserDialog(account, self, qa, now - 1800,
                new String[]{
                        "ExFullGram QA",
                        "Этот аккаунт нужен для проверки списка диалогов и длинных сообщений.",
                        "Demo-данные хранятся локально и могут быть сброшены без Telegram."
                });

        AndroidUtilities.runOnUIThread(() ->
                NotificationCenter.getInstance(account).postNotificationName(NotificationCenter.dialogsNeedReload), 700);
    }

    private static void seedUserDialog(
            int account,
            TLRPC.User self,
            TLRPC.User peer,
            int newestDate,
            String[] texts
    ) {
        TLRPC.TL_messages_messages container = new TLRPC.TL_messages_messages();
        container.users.add(self);
        container.users.add(peer);

        // MessagesStorage expects newest message first when createDialog=true.
        for (int i = texts.length - 1; i >= 0; i--) {
            boolean outgoing = (i % 2) != 0;
            TLRPC.TL_message message = new TLRPC.TL_message();
            message.id = i + 1;
            message.local_id = message.id;
            message.date = newestDate - (texts.length - 1 - i) * 55;
            message.message = texts[i];
            message.flags = 259;
            message.media = new TLRPC.TL_messageMediaEmpty();
            message.out = outgoing;
            message.dialog_id = peer.id;

            message.peer_id = new TLRPC.TL_peerUser();
            message.peer_id.user_id = outgoing ? peer.id : self.id;

            message.from_id = new TLRPC.TL_peerUser();
            message.from_id.user_id = outgoing ? self.id : peer.id;

            container.messages.add(message);
        }

        MessagesStorage.getInstance(account).putMessages(
                container,
                peer.id,
                0,
                0,
                true,
                ChatActivity.MODE_DEFAULT,
                0
        );
    }
}
