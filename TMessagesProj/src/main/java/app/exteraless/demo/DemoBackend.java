package app.exteraless.demo;

import android.content.Context;
import android.content.SharedPreferences;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ChatActivity;

import java.util.ArrayList;

/**
 * Local-only account data for ExFullGram development.
 *
 * Normal Telegram UI is backed by Telegram's real local storage/controllers,
 * but demo actions never leave the device.
 */
public final class DemoBackend {
    private static final String PREFS = "exfullgram_demo";
    private static final String KEY_ENABLED = "enabled";
    private static final String KEY_NEXT_MESSAGE_ID = "next_message_id";

    public static final long SELF_ID = 9_900_000_001L;
    private static final long ALICE_ID = 9_900_000_101L;
    private static final long MAX_ID = 9_900_000_102L;
    private static final long QA_ID = 9_900_000_103L;

    private DemoBackend() {}

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static boolean isEnabled() {
        return prefs().getBoolean(KEY_ENABLED, false);
    }

    public static boolean isDemoAccount(int account) {
        return isEnabled() && UserConfig.getInstance(account).getClientUserId() == SELF_ID;
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

    private static int nextMessageId() {
        int next = prefs().getInt(KEY_NEXT_MESSAGE_ID, 10_000) + 1;
        prefs().edit().putInt(KEY_NEXT_MESSAGE_ID, next).apply();
        return next;
    }

    public static void reset(int account) {
        TLRPC.User self = UserConfig.getInstance(account).getCurrentUser();
        if (self == null || self.id != SELF_ID) {
            self = createSelf();
            UserConfig.getInstance(account).setCurrentUser(self);
            UserConfig.getInstance(account).saveConfig(true);
        }
        prefs().edit().putBoolean(KEY_ENABLED, true).putInt(KEY_NEXT_MESSAGE_ID, 10_000).apply();
        MessagesController.getInstance(account).cleanup();
        MessagesStorage.getInstance(account).cleanup(false);
        MessagesController.getInstance(account).putUser(self, false);
        seed(account, self);
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
        seedUserDialog(account, self, alice, now - 40, new String[]{
                "Привет. Это локальный Demo Backend ExFullGram.",
                "Можешь спокойно ломать интерфейс — эти сообщения никуда не отправляются.",
                "Попробуй написать сюда: исходящие сообщения уже сохраняются локально."
        });
        seedUserDialog(account, self, max, now - 240, new String[]{
                "Тестовый чат №2.",
                "Здесь можно проверять список диалогов, длинные сообщения и ответы.",
                "Следом добавим полноценные edit/delete/reactions и сетевую симуляцию."
        });
        seedUserDialog(account, self, qa, now - 1800, new String[]{
                "ExFullGram QA",
                "Этот аккаунт нужен для проверки интерфейса без настоящего Telegram.",
                "Demo-данные можно сбросить из настроек ExFullGram."
        });

        AndroidUtilities.runOnUIThread(() ->
                NotificationCenter.getInstance(account).postNotificationName(NotificationCenter.dialogsNeedReload), 700);
    }

    private static void seedUserDialog(int account, TLRPC.User self, TLRPC.User peer, int newestDate, String[] texts) {
        TLRPC.TL_messages_messages container = new TLRPC.TL_messages_messages();
        container.users.add(self);
        container.users.add(peer);
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
            message.send_state = MessageObject.MESSAGE_SEND_STATE_SENT;
            message.dialog_id = peer.id;
            message.peer_id = new TLRPC.TL_peerUser();
            message.peer_id.user_id = outgoing ? peer.id : self.id;
            message.from_id = new TLRPC.TL_peerUser();
            message.from_id.user_id = outgoing ? self.id : peer.id;
            container.messages.add(message);
        }
        MessagesStorage.getInstance(account).putMessages(
                container, peer.id, 0, 0, true, ChatActivity.MODE_DEFAULT, 0);
    }

    public static boolean sendLocalMessage(int account, SendMessagesHelper.SendMessageParams params) {
        if (!isDemoAccount(account) || params == null) return false;

        final long peer = params.peer;
        if (peer <= 0) return true;

        String text = params.message;
        if (text == null || text.isEmpty()) text = params.caption;
        if (text == null || text.isEmpty()) {
            if (params.photo != null) text = "🖼 Фото (Demo)";
            else if (params.document != null) text = "📎 Файл (Demo)";
            else if (params.location != null) text = "📍 Геопозиция (Demo)";
            else if (params.poll != null) text = "📊 Опрос (Demo)";
            else if (params.todo != null) text = "✅ Задача (Demo)";
            else if (params.game != null) text = "🎮 Игра (Demo)";
            else if (params.invoice != null) text = "🧾 Счёт (Demo)";
            else text = "Demo message";
        }

        TLRPC.TL_message message = new TLRPC.TL_message();
        message.id = nextMessageId();
        message.local_id = message.id;
        message.date = (int) (System.currentTimeMillis() / 1000L);
        message.message = text;
        message.flags = 259;
        message.media = new TLRPC.TL_messageMediaEmpty();
        message.out = true;
        message.send_state = MessageObject.MESSAGE_SEND_STATE_SENT;
        message.dialog_id = peer;
        message.peer_id = new TLRPC.TL_peerUser();
        message.peer_id.user_id = peer;
        message.from_id = new TLRPC.TL_peerUser();
        message.from_id.user_id = SELF_ID;

        if (params.replyToMsg != null) {
            message.reply_to = new TLRPC.TL_messageReplyHeader();
            message.reply_to.flags |= 16;
            message.reply_to.reply_to_msg_id = params.replyToMsg.getId();
            message.flags |= 8;
        }

        ArrayList<TLRPC.Message> raw = new ArrayList<>();
        raw.add(message);
        MessagesStorage.getInstance(account).putMessages(raw, false, true, false, 0, ChatActivity.MODE_DEFAULT, 0);

        ArrayList<MessageObject> objects = new ArrayList<>();
        objects.add(new MessageObject(account, message, true, true));
        MessagesController.getInstance(account).updateInterfaceWithMessages(peer, objects, ChatActivity.MODE_DEFAULT);
        NotificationCenter.getInstance(account).postNotificationName(NotificationCenter.dialogsNeedReload);
        return true;
    }
}
