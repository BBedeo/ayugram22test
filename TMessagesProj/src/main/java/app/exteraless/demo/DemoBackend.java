package app.exteraless.demo;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_update;
import org.telegram.ui.ChatActivity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

/**
 * Local-only Test Backend for ExFullGram.
 *
 * The old class name is intentionally kept for source compatibility with the
 * first Test Mode commits. User-visible wording is "Test", not "Demo".
 */
public final class DemoBackend {
    private static final String PREFS = "exfullgram_demo";
    private static final String KEY_NEXT_MESSAGE_ID = "next_message_id";
    private static final String KEY_NEXT_PEER_ID = "next_peer_id";
    private static final String KEY_SHARED_CHATS = "shared_test_chats";

    public static final int TYPE_PRIVATE = 0;
    public static final int TYPE_GROUP = 1;
    public static final int TYPE_CHANNEL = 2;

    public static final int PRESET_CUSTOM = 0;
    public static final int PRESET_EMPTY = 1;
    public static final int PRESET_BUSY_GROUP = 2;
    public static final int PRESET_LARGE_CHANNEL = 3;
    public static final int PRESET_LONG_CONTENT = 4;
    public static final int PRESET_FLOOD = 5;

    private static final long SELF_ID_BASE = 9_900_000_000L;
    private static final long GENERATED_PEER_ID_BASE = 9_910_000_000L;

    // Compatibility with the first single-account Test Mode implementation.
    public static final long SELF_ID = SELF_ID_BASE + 1L;

    private static final long ALICE_ID = 9_900_000_101L;
    private static final long MAX_ID = 9_900_000_102L;
    private static final long QA_ID = 9_900_000_103L;

    private DemoBackend() {}

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static long selfIdForAccount(int account) {
        return SELF_ID_BASE + 1L + Math.max(0, account);
    }

    public static boolean isEnabled() {
        for (int account = 0; account < UserConfig.MAX_ACCOUNT_COUNT; account++) {
            if (isTestAccount(account)) {
                return true;
            }
        }
        return false;
    }

    public static void setEnabled(boolean enabled) {
        // Kept for binary/source compatibility with the first implementation.
        // Test accounts are now detected by their reserved local user IDs.
    }

    public static boolean isTestUserId(long userId) {
        return userId > SELF_ID_BASE && userId <= SELF_ID_BASE + UserConfig.MAX_ACCOUNT_COUNT;
    }

    public static boolean isTestAccount(int account) {
        if (account < 0 || account >= UserConfig.MAX_ACCOUNT_COUNT) return false;
        TLRPC.User user = UserConfig.getInstance(account).getCurrentUser();
        return user != null && isTestUserId(user.id);
    }

    public static boolean isDemoAccount(int account) {
        return isTestAccount(account);
    }

    private static String defaultAccountName(int account) {
        return LocaleController.formatString(R.string.ExFullGramTestAccountDefaultName, account + 1);
    }

    public static TLRPC.TL_user createSelf() {
        return createSelf(0, defaultAccountName(0));
    }

    public static TLRPC.TL_user createSelf(int account, String name) {
        TLRPC.TL_user user = createUser(
                selfIdForAccount(account),
                TextUtils.isEmpty(name) ? defaultAccountName(account) : name.trim(),
                LocaleController.getString(R.string.ExFullGramDemoAliceLastName),
                true
        );
        user.phone = "99900000" + String.format(java.util.Locale.US, "%02d", account);
        user.flags |= 16; // phone
        return user;
    }

    private static TLRPC.TL_user createUser(long id, String firstName, String lastName) {
        return createUser(id, firstName, lastName, false);
    }

    private static TLRPC.TL_user createUser(long id, String firstName, String lastName, boolean self) {
        TLRPC.TL_user user = new TLRPC.TL_user();
        user.id = id;
        user.first_name = firstName == null ? "" : firstName;
        user.flags |= 2; // first_name
        if (!TextUtils.isEmpty(lastName)) {
            user.last_name = lastName;
            user.flags |= 4; // last_name
        }
        user.self = self;

        TLRPC.TL_userStatusOnline status = new TLRPC.TL_userStatusOnline();
        status.expires = (int) (System.currentTimeMillis() / 1000L) + 3600;
        user.status = status;
        user.flags |= 64; // status
        return user;
    }

    private static TLRPC.TL_user peerCopy(TLRPC.User source) {
        if (source == null) return null;
        TLRPC.TL_user user = createUser(source.id, source.first_name, source.last_name, false);
        user.phone = source.phone;
        if (!TextUtils.isEmpty(user.phone)) user.flags |= 16;
        return user;
    }

    private static int nextMessageId() {
        synchronized (DemoBackend.class) {
            int next = prefs().getInt(KEY_NEXT_MESSAGE_ID, 10_000) + 1;
            prefs().edit().putInt(KEY_NEXT_MESSAGE_ID, next).apply();
            return next;
        }
    }

    private static long nextPeerId() {
        synchronized (DemoBackend.class) {
            int next = prefs().getInt(KEY_NEXT_PEER_ID, 0) + 1;
            prefs().edit().putInt(KEY_NEXT_PEER_ID, next).apply();
            return GENERATED_PEER_ID_BASE + next;
        }
    }

    public static void reset(int account) {
        TLRPC.User self = UserConfig.getInstance(account).getCurrentUser();
        if (self == null || !isTestUserId(self.id)) {
            self = createSelf(account, defaultAccountName(account));
            UserConfig.getInstance(account).setCurrentUser(self);
            UserConfig.getInstance(account).saveConfig(true);
            ConnectionsManager.getInstance(account).setUserId(self.id);
        }

        MessagesController.getInstance(account).cleanup();
        MessagesStorage.getInstance(account).cleanup(false);
        MessagesController.getInstance(account).putUser(self, false);
        seed(account, self);
        installSavedSharedChats(account);
        linkAccountToOtherTestAccounts(account);
    }

    public static void seed(int account, TLRPC.User self) {
        TLRPC.TL_user alice = createUser(
                ALICE_ID,
                LocaleController.getString(R.string.ExFullGramDemoAliceFirstName),
                LocaleController.getString(R.string.ExFullGramDemoAliceLastName)
        );
        TLRPC.TL_user max = createUser(
                MAX_ID,
                LocaleController.getString(R.string.ExFullGramDemoMaxFirstName),
                LocaleController.getString(R.string.ExFullGramDemoMaxLastName)
        );
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
                LocaleController.getString(R.string.ExFullGramDemoAliceMessage1),
                LocaleController.getString(R.string.ExFullGramDemoAliceMessage2),
                LocaleController.getString(R.string.ExFullGramDemoAliceMessage3)
        });
        seedUserDialog(account, self, max, now - 240, new String[]{
                LocaleController.getString(R.string.ExFullGramDemoMaxMessage1),
                LocaleController.getString(R.string.ExFullGramDemoMaxMessage2),
                LocaleController.getString(R.string.ExFullGramDemoMaxMessage3)
        });
        seedUserDialog(account, self, qa, now - 1800, new String[]{
                LocaleController.getString(R.string.ExFullGramDemoQaMessage1),
                LocaleController.getString(R.string.ExFullGramDemoQaMessage2),
                LocaleController.getString(R.string.ExFullGramDemoQaMessage3)
        });

        AndroidUtilities.runOnUIThread(() ->
                NotificationCenter.getInstance(account).postNotificationName(NotificationCenter.dialogsNeedReload), 500);
    }

    private static void seedUserDialog(int account, TLRPC.User self, TLRPC.User peer, int newestDate, String[] texts) {
        TLRPC.TL_messages_messages container = new TLRPC.TL_messages_messages();
        container.users.add(self);
        container.users.add(peer);

        for (int i = texts.length - 1; i >= 0; i--) {
            boolean outgoing = (i % 2) != 0;
            int messageId = nextMessageId();
            TLRPC.TL_message message = createPrivateMessage(
                    messageId,
                    self.id,
                    peer.id,
                    outgoing ? self.id : peer.id,
                    outgoing,
                    texts[i],
                    newestDate - (texts.length - 1 - i) * 55
            );
            container.messages.add(message);
        }

        MessagesStorage.getInstance(account).putMessages(
                container, peer.id, 0, 0, true, ChatActivity.MODE_DEFAULT, 0);
    }

    private static TLRPC.TL_message createPrivateMessage(
            int id,
            long selfId,
            long peerId,
            long senderId,
            boolean outgoing,
            String text,
            int date
    ) {
        TLRPC.TL_message message = new TLRPC.TL_message();
        message.id = id;
        message.local_id = id;
        message.date = date;
        message.message = text == null ? "" : text;
        message.flags = 256; // from_id
        message.out = outgoing;
        message.send_state = MessageObject.MESSAGE_SEND_STATE_SENT;
        message.dialog_id = peerId;

        message.peer_id = new TLRPC.TL_peerUser();
        message.peer_id.user_id = outgoing ? peerId : selfId;

        message.from_id = new TLRPC.TL_peerUser();
        message.from_id.user_id = senderId;
        return message;
    }

    private static TLRPC.TL_message createChannelMessage(
            int id,
            long chatId,
            long senderId,
            boolean outgoing,
            boolean broadcast,
            String text,
            int date
    ) {
        TLRPC.TL_message message = new TLRPC.TL_message();
        message.id = id;
        message.local_id = id;
        message.date = date;
        message.message = text == null ? "" : text;
        message.flags = 256; // from_id
        message.out = outgoing;
        message.post = broadcast;
        message.send_state = MessageObject.MESSAGE_SEND_STATE_SENT;
        message.dialog_id = -chatId;

        message.peer_id = new TLRPC.TL_peerChannel();
        message.peer_id.channel_id = chatId;

        if (broadcast) {
            message.from_id = new TLRPC.TL_peerChannel();
            message.from_id.channel_id = chatId;
        } else {
            message.from_id = new TLRPC.TL_peerUser();
            message.from_id.user_id = senderId;
        }
        return message;
    }

    private static void putSingleMessage(int account, long dialogId, TLRPC.Message message) {
        ArrayList<TLRPC.Message> raw = new ArrayList<>();
        raw.add(message);
        MessagesStorage.getInstance(account).putMessages(
                raw, false, true, false, 0, ChatActivity.MODE_DEFAULT, 0);

        ArrayList<MessageObject> objects = new ArrayList<>();
        objects.add(new MessageObject(account, message, true, true));
        MessagesController.getInstance(account).updateInterfaceWithMessages(
                dialogId, objects, ChatActivity.MODE_DEFAULT);
        NotificationCenter.getInstance(account).postNotificationName(NotificationCenter.dialogsNeedReload);
    }

    public static long createTestChat(
            int account,
            int type,
            String name,
            String description,
            int participants,
            int messageCount,
            int unreadCount,
            int preset
    ) {
        if (!isTestAccount(account)) return 0;

        type = Math.max(TYPE_PRIVATE, Math.min(TYPE_CHANNEL, type));
        participants = Math.max(type == TYPE_PRIVATE ? 2 : 1, Math.min(participants, 1_000_000_000));
        messageCount = Math.max(0, Math.min(messageCount, 5000));
        unreadCount = Math.max(0, Math.min(unreadCount, messageCount));

        String title = name == null ? "" : name.trim();
        if (TextUtils.isEmpty(title)) {
            title = type == TYPE_PRIVATE
                    ? LocaleController.getString(R.string.ExFullGramTestTypePrivate)
                    : type == TYPE_GROUP
                    ? LocaleController.getString(R.string.ExFullGramTestTypeGroup)
                    : LocaleController.getString(R.string.ExFullGramTestTypeChannel);
        }
        String about = description == null ? "" : description.trim();
        long id = nextPeerId();

        if (type == TYPE_PRIVATE) {
            installPrivateTestChat(account, id, title, about, messageCount, unreadCount, preset);
            return id;
        }

        saveSharedChatSpec(id, type, title, about, participants, messageCount, unreadCount, preset);
        for (int slot = 0; slot < UserConfig.MAX_ACCOUNT_COUNT; slot++) {
            if (isTestAccount(slot)) {
                installChannelTestChat(slot, id, type, title, about, participants, messageCount, unreadCount, preset);
            }
        }
        return -id;
    }

    private static void installPrivateTestChat(
            int account,
            long userId,
            String title,
            String about,
            int messageCount,
            int unreadCount,
            int preset
    ) {
        TLRPC.TL_user peer = createUser(userId, title, "");
        ArrayList<TLRPC.User> users = new ArrayList<>();
        users.add(peer);
        MessagesController.getInstance(account).putUsers(users, false);
        MessagesStorage.getInstance(account).putUsersAndChats(users, null, true, true);

        if (!TextUtils.isEmpty(about)) {
            TLRPC.TL_userFull full = new TLRPC.TL_userFull();
            full.id = peer.id;
            full.user = peer;
            full.about = about;
            full.flags |= 2; // about
            full.settings = new TLRPC.TL_peerSettings();
            full.notify_settings = new TLRPC.TL_peerNotifySettings();
            full.common_chats_count = 0;
            MessagesStorage.getInstance(account).updateUserInfo(full, false);
            MessagesController.getInstance(account).processUserInfo(
                    peer, full, false, false, 0, null, null, 0, true);
        }

        TLRPC.User self = UserConfig.getInstance(account).getCurrentUser();
        if (messageCount <= 0) {
            installEmptyPrivateDialog(account, self, peer);
            return;
        }

        int count = messageCount;
        TLRPC.TL_messages_messages container = new TLRPC.TL_messages_messages();
        container.users.add(self);
        container.users.add(peer);
        int now = (int) (System.currentTimeMillis() / 1000L);

        int unreadRemaining = Math.min(unreadCount, count);
        for (int i = count - 1; i >= 0; i--) {
            boolean outgoing = i % 4 == 0;
            String text = generatedMessageText(i + 1, preset);
            TLRPC.TL_message generated = createPrivateMessage(
                    nextMessageId(),
                    self.id,
                    peer.id,
                    outgoing ? self.id : peer.id,
                    outgoing,
                    text,
                    now - (count - 1 - i) * 12
            );
            if (!outgoing && unreadRemaining > 0) {
                generated.unread = true;
                unreadRemaining--;
            }
            container.messages.add(generated);
        }

        MessagesStorage.getInstance(account).putMessages(
                container, peer.id, 0, 0, true, ChatActivity.MODE_DEFAULT, 0);
        AndroidUtilities.runOnUIThread(() ->
                NotificationCenter.getInstance(account).postNotificationName(NotificationCenter.dialogsNeedReload), 250);
    }

    private static void installChannelTestChat(
            int account,
            long chatId,
            int type,
            String title,
            String about,
            int participants,
            int messageCount,
            int unreadCount,
            int preset
    ) {
        boolean broadcast = type == TYPE_CHANNEL;
        TLRPC.TL_channel chat = new TLRPC.TL_channel();
        chat.id = chatId;
        chat.title = title;
        chat.photo = new TLRPC.TL_chatPhotoEmpty();
        chat.date = (int) (System.currentTimeMillis() / 1000L);
        chat.creator = true;
        chat.megagroup = !broadcast;
        chat.broadcast = broadcast;
        chat.participants_count = participants;
        chat.flags |= 131072; // participants_count

        ArrayList<TLRPC.Chat> chats = new ArrayList<>();
        chats.add(chat);
        MessagesController.getInstance(account).putChats(chats, false);
        MessagesStorage.getInstance(account).putUsersAndChats(null, chats, true, true);

        TLRPC.TL_channelFull full = new TLRPC.TL_channelFull();
        full.id = chatId;
        full.about = about;
        full.participants_count = participants;
        full.flags |= 1; // participants_count
        full.read_inbox_max_id = 0;
        full.read_outbox_max_id = 0;
        full.unread_count = Math.min(unreadCount, Math.max(1, messageCount));
        full.chat_photo = new TLRPC.TL_photoEmpty();
        full.chat_photo.id = 0;
        full.notify_settings = new TLRPC.TL_peerNotifySettings();
        full.pts = 1;
        MessagesStorage.getInstance(account).updateChatInfo(full, false);
        MessagesController.getInstance(account).putChatFull(full);

        TLRPC.User self = UserConfig.getInstance(account).getCurrentUser();
        ArrayList<TLRPC.User> users = createSampleParticipants(chatId);
        users.add(self);
        MessagesController.getInstance(account).putUsers(users, false);
        MessagesStorage.getInstance(account).putUsersAndChats(users, null, true, true);

        if (messageCount <= 0) {
            installEmptyChannelDialog(account, chat, users);
            return;
        }

        int count = messageCount;
        TLRPC.TL_messages_messages container = new TLRPC.TL_messages_messages();
        container.chats.add(chat);
        container.users.addAll(users);
        int now = (int) (System.currentTimeMillis() / 1000L);

        int unreadRemaining = Math.min(unreadCount, count);
        for (int i = count - 1; i >= 0; i--) {
            TLRPC.User sender = users.get(i % Math.max(1, users.size() - 1));
            TLRPC.TL_message generated = createChannelMessage(
                    nextMessageId(),
                    chatId,
                    broadcast ? chatId : sender.id,
                    false,
                    broadcast,
                    generatedMessageText(i + 1, preset),
                    now - (count - 1 - i) * 8
            );
            if (unreadRemaining > 0) {
                generated.unread = true;
                unreadRemaining--;
            }
            container.messages.add(generated);
        }

        MessagesStorage.getInstance(account).putMessages(
                container, -chatId, 0, 0, true, ChatActivity.MODE_DEFAULT, 0);
        AndroidUtilities.runOnUIThread(() -> {
            NotificationCenter.getInstance(account).postNotificationName(NotificationCenter.chatInfoDidLoad, full, 0, false, false);
            NotificationCenter.getInstance(account).postNotificationName(NotificationCenter.dialogsNeedReload);
        }, 250);
    }

    private static void installEmptyPrivateDialog(int account, TLRPC.User self, TLRPC.User peer) {
        TLRPC.TL_dialog dialog = new TLRPC.TL_dialog();
        dialog.peer = new TLRPC.TL_peerUser();
        dialog.peer.user_id = peer.id;
        dialog.top_message = 0;
        dialog.read_inbox_max_id = 0;
        dialog.read_outbox_max_id = 0;
        dialog.unread_count = 0;
        dialog.unread_mentions_count = 0;
        dialog.unread_reactions_count = 0;
        dialog.unread_poll_votes_count = 0;
        dialog.notify_settings = new TLRPC.TL_peerNotifySettings();

        TLRPC.TL_messages_dialogs dialogs = new TLRPC.TL_messages_dialogs();
        dialogs.dialogs.add(dialog);
        dialogs.users.add(self);
        dialogs.users.add(peer);

        MessagesController.getInstance(account).processLoadedDialogs(
                dialogs, null, null, 0, 0, 1, 3, false, false, false);
    }

    private static void installEmptyChannelDialog(int account, TLRPC.Chat chat, ArrayList<TLRPC.User> users) {
        TLRPC.TL_dialog dialog = new TLRPC.TL_dialog();
        dialog.peer = new TLRPC.TL_peerChannel();
        dialog.peer.channel_id = chat.id;
        dialog.top_message = 0;
        dialog.read_inbox_max_id = 0;
        dialog.read_outbox_max_id = 0;
        dialog.unread_count = 0;
        dialog.unread_mentions_count = 0;
        dialog.unread_reactions_count = 0;
        dialog.unread_poll_votes_count = 0;
        dialog.notify_settings = new TLRPC.TL_peerNotifySettings();
        dialog.flags |= 1; // pts
        dialog.pts = 1;

        TLRPC.TL_messages_dialogs dialogs = new TLRPC.TL_messages_dialogs();
        dialogs.dialogs.add(dialog);
        dialogs.chats.add(chat);
        dialogs.users.addAll(users);

        MessagesController.getInstance(account).processLoadedDialogs(
                dialogs, null, null, 0, 0, 1, 3, false, false, false);
    }

    private static ArrayList<TLRPC.User> createSampleParticipants(long chatId) {
        ArrayList<TLRPC.User> users = new ArrayList<>();
        long base = GENERATED_PEER_ID_BASE + 500_000L + (chatId % 100_000L) * 10L;
        users.add(createUser(base + 1, "Alex", "Test"));
        users.add(createUser(base + 2, "Mia", "QA"));
        users.add(createUser(base + 3, "Sam", "Debug"));
        users.add(createUser(base + 4, "Nora", "Tester"));
        users.add(createUser(base + 5, "Leo", "Local"));
        return users;
    }

    private static String generatedMessageText(int index, int preset) {
        if (preset == PRESET_LONG_CONTENT && index % 3 == 0) {
            return LocaleController.getString(R.string.ExFullGramTestGeneratedLongMessage);
        }
        return LocaleController.formatString(R.string.ExFullGramTestGeneratedMessage, index);
    }

    private static String sharedPrefix(long chatId) {
        return "shared_chat_" + chatId + "_";
    }

    private static void saveSharedChatSpec(
            long chatId,
            int type,
            String title,
            String about,
            int participants,
            int messageCount,
            int unreadCount,
            int preset
    ) {
        Set<String> ids = new HashSet<>(prefs().getStringSet(KEY_SHARED_CHATS, new HashSet<>()));
        ids.add(Long.toString(chatId));
        String p = sharedPrefix(chatId);
        prefs().edit()
                .putStringSet(KEY_SHARED_CHATS, ids)
                .putInt(p + "type", type)
                .putString(p + "title", title)
                .putString(p + "about", about)
                .putInt(p + "participants", participants)
                .putInt(p + "messages", Math.min(messageCount, 250))
                .putInt(p + "unread", Math.min(unreadCount, Math.min(messageCount, 250)))
                .putInt(p + "preset", preset)
                .apply();
    }

    private static void installSavedSharedChats(int account) {
        Set<String> ids = prefs().getStringSet(KEY_SHARED_CHATS, null);
        if (ids == null) return;
        for (String raw : new HashSet<>(ids)) {
            try {
                long chatId = Long.parseLong(raw);
                String p = sharedPrefix(chatId);
                int type = prefs().getInt(p + "type", TYPE_GROUP);
                String title = prefs().getString(p + "title", LocaleController.getString(R.string.ExFullGramTestTypeGroup));
                String about = prefs().getString(p + "about", "");
                int participants = prefs().getInt(p + "participants", 20);
                int messages = prefs().getInt(p + "messages", 10);
                int unread = prefs().getInt(p + "unread", 0);
                int preset = prefs().getInt(p + "preset", PRESET_CUSTOM);
                installChannelTestChat(account, chatId, type, title, about, participants, messages, unread, preset);
            } catch (Exception ignore) {
            }
        }
    }

    public static int addTestAccount(String name) {
        int free = -1;
        for (int account = 0; account < UserConfig.MAX_ACCOUNT_COUNT; account++) {
            if (!UserConfig.getInstance(account).isClientActivated()) {
                free = account;
                break;
            }
        }
        if (free < 0) return -1;

        UserConfig config = UserConfig.getInstance(free);
        config.clearConfig();
        TLRPC.TL_user self = createSelf(free, name);
        config.syncContacts = false;
        config.setCurrentUser(self);
        config.loginTime = (int) (System.currentTimeMillis() / 1000L);
        config.saveConfig(true);
        ConnectionsManager.getInstance(free).setUserId(self.id);

        MessagesController.getInstance(free).cleanup();
        MessagesStorage.getInstance(free).cleanup(false);
        MessagesController.getInstance(free).putUser(self, false);
        seed(free, self);
        installSavedSharedChats(free);
        linkAccountToOtherTestAccounts(free);
        return free;
    }

    private static int accountForTestUser(long userId) {
        for (int account = 0; account < UserConfig.MAX_ACCOUNT_COUNT; account++) {
            if (!isTestAccount(account)) continue;
            if (UserConfig.getInstance(account).getClientUserId() == userId) return account;
        }
        return -1;
    }

    private static void linkAccountToOtherTestAccounts(int account) {
        if (!isTestAccount(account)) return;
        for (int other = 0; other < UserConfig.MAX_ACCOUNT_COUNT; other++) {
            if (other == account || !isTestAccount(other)) continue;
            ensureTestAccountsKnowEachOther(account, other);
        }
    }

    private static void ensureTestAccountsKnowEachOther(int a, int b) {
        TLRPC.User selfA = UserConfig.getInstance(a).getCurrentUser();
        TLRPC.User selfB = UserConfig.getInstance(b).getCurrentUser();
        if (selfA == null || selfB == null) return;

        TLRPC.TL_user peerB = peerCopy(selfB);
        TLRPC.TL_user peerA = peerCopy(selfA);

        ArrayList<TLRPC.User> usersA = new ArrayList<>();
        usersA.add(peerB);
        MessagesController.getInstance(a).putUsers(usersA, false);
        MessagesStorage.getInstance(a).putUsersAndChats(usersA, null, true, true);

        ArrayList<TLRPC.User> usersB = new ArrayList<>();
        usersB.add(peerA);
        MessagesController.getInstance(b).putUsers(usersB, false);
        MessagesStorage.getInstance(b).putUsersAndChats(usersB, null, true, true);

        int id = nextMessageId();
        int date = (int) (System.currentTimeMillis() / 1000L);
        String text = LocaleController.getString(R.string.ExFullGramTestAccountsLinkedMessage);

        TLRPC.TL_messages_messages containerA = new TLRPC.TL_messages_messages();
        containerA.users.add(selfA);
        containerA.users.add(peerB);
        containerA.messages.add(createPrivateMessage(id, selfA.id, selfB.id, selfB.id, false, text, date));
        MessagesStorage.getInstance(a).putMessages(containerA, selfB.id, 0, 0, true, ChatActivity.MODE_DEFAULT, 0);

        TLRPC.TL_messages_messages containerB = new TLRPC.TL_messages_messages();
        containerB.users.add(selfB);
        containerB.users.add(peerA);
        containerB.messages.add(createPrivateMessage(id, selfB.id, selfA.id, selfB.id, true, text, date));
        MessagesStorage.getInstance(b).putMessages(containerB, selfA.id, 0, 0, true, ChatActivity.MODE_DEFAULT, 0);

        AndroidUtilities.runOnUIThread(() -> {
            NotificationCenter.getInstance(a).postNotificationName(NotificationCenter.dialogsNeedReload);
            NotificationCenter.getInstance(b).postNotificationName(NotificationCenter.dialogsNeedReload);
        }, 250);
    }

    public static boolean sendLocalMessage(int account, SendMessagesHelper.SendMessageParams params) {
        if (!isTestAccount(account) || params == null) return false;

        final long dialogId = params.peer;
        if (dialogId == 0) return true;

        String text = params.message;
        if (text == null || text.isEmpty()) text = params.caption;
        if (text == null || text.isEmpty()) {
            if (params.photo != null) text = LocaleController.getString(R.string.ExFullGramDemoMediaPhoto);
            else if (params.document != null) text = LocaleController.getString(R.string.ExFullGramDemoMediaFile);
            else if (params.location != null) text = LocaleController.getString(R.string.ExFullGramDemoMediaLocation);
            else if (params.poll != null) text = LocaleController.getString(R.string.ExFullGramDemoMediaPoll);
            else if (params.todo != null) text = LocaleController.getString(R.string.ExFullGramDemoMediaTask);
            else if (params.game != null) text = LocaleController.getString(R.string.ExFullGramDemoMediaGame);
            else if (params.invoice != null) text = LocaleController.getString(R.string.ExFullGramDemoMediaInvoice);
            else text = LocaleController.getString(R.string.ExFullGramDemoMessage);
        }

        TLRPC.User self = UserConfig.getInstance(account).getCurrentUser();
        if (self == null) return true;
        int messageId = nextMessageId();
        int date = (int) (System.currentTimeMillis() / 1000L);

        if (dialogId > 0) {
            TLRPC.TL_message message = createPrivateMessage(
                    messageId, self.id, dialogId, self.id, true, text, date);
            copyReply(params, message);
            putSingleMessage(account, dialogId, message);

            int recipientAccount = accountForTestUser(dialogId);
            if (recipientAccount >= 0 && recipientAccount != account) {
                TLRPC.User recipientSelf = UserConfig.getInstance(recipientAccount).getCurrentUser();
                TLRPC.TL_user senderPeer = peerCopy(self);
                ArrayList<TLRPC.User> users = new ArrayList<>();
                users.add(senderPeer);
                MessagesController.getInstance(recipientAccount).putUsers(users, false);
                MessagesStorage.getInstance(recipientAccount).putUsersAndChats(users, null, true, true);

                TLRPC.TL_message mirrored = createPrivateMessage(
                        messageId,
                        recipientSelf.id,
                        self.id,
                        self.id,
                        false,
                        text,
                        date
                );
                if (message.reply_to != null) {
                    mirrored.reply_to = message.reply_to;
                    mirrored.flags |= 8;
                }
                putSingleMessage(recipientAccount, self.id, mirrored);
            }
            return true;
        }

        long chatId = -dialogId;
        TLRPC.Chat chat = MessagesController.getInstance(account).getChat(chatId);
        boolean broadcast = chat != null && chat.broadcast && !chat.megagroup;

        TLRPC.TL_message message = createChannelMessage(
                messageId, chatId, self.id, true, broadcast, text, date);
        copyReply(params, message);
        putSingleMessage(account, dialogId, message);

        // Shared test groups/channels are mirrored to every local Test Account.
        for (int other = 0; other < UserConfig.MAX_ACCOUNT_COUNT; other++) {
            if (other == account || !isTestAccount(other)) continue;
            TLRPC.TL_message mirrored = createChannelMessage(
                    messageId, chatId, self.id, false, broadcast, text, date);
            if (message.reply_to != null) {
                mirrored.reply_to = message.reply_to;
                mirrored.flags |= 8;
            }

            TLRPC.TL_user senderPeer = peerCopy(self);
            ArrayList<TLRPC.User> users = new ArrayList<>();
            users.add(senderPeer);
            MessagesController.getInstance(other).putUsers(users, false);
            MessagesStorage.getInstance(other).putUsersAndChats(users, null, true, true);
            putSingleMessage(other, dialogId, mirrored);
        }
        return true;
    }


    public static boolean editLocalMessage(int account, MessageObject object) {
        if (!isTestAccount(account) || object == null || object.messageOwner == null) {
            return false;
        }

        CharSequence editing = object.editingMessage;
        if (editing != null) {
            object.messageOwner.message = editing.toString();
            object.messageOwner.edit_date = (int) (System.currentTimeMillis() / 1000L);
            object.messageOwner.flags |= 32768; // edit_date
            if (object.editingMessageEntities != null) {
                object.messageOwner.entities = new ArrayList<>(object.editingMessageEntities);
                if (!object.messageOwner.entities.isEmpty()) {
                    object.messageOwner.flags |= 128;
                }
            }
        }

        object.editingMessage = null;
        object.editingMessageEntities = null;
        object.messageOwner.send_state = MessageObject.MESSAGE_SEND_STATE_SENT;
        object.caption = null;
        object.resetLayout();
        object.checkLayout();

        ArrayList<TLRPC.Message> raw = new ArrayList<>();
        raw.add(object.messageOwner);
        MessagesStorage.getInstance(account).putMessages(raw, false, true, false, 0, 0, 0);

        ArrayList<MessageObject> objects = new ArrayList<>();
        objects.add(object);
        NotificationCenter.getInstance(account).postNotificationName(
                NotificationCenter.replaceMessagesObjects,
                object.getDialogId(),
                objects
        );

        mirrorEditedMessage(account, object.getDialogId(), object.messageOwner);
        return true;
    }

    private static void mirrorEditedMessage(int sourceAccount, long sourceDialogId, TLRPC.Message source) {
        if (source == null) return;

        if (sourceDialogId > 0) {
            int recipient = accountForTestUser(sourceDialogId);
            if (recipient < 0 || recipient == sourceAccount) return;
            long mirroredDialogId = UserConfig.getInstance(sourceAccount).getClientUserId();
            updateMirroredMessage(recipient, mirroredDialogId, source);
            return;
        }

        for (int other = 0; other < UserConfig.MAX_ACCOUNT_COUNT; other++) {
            if (other == sourceAccount || !isTestAccount(other)) continue;
            updateMirroredMessage(other, sourceDialogId, source);
        }
    }

    private static void updateMirroredMessage(int account, long dialogId, TLRPC.Message source) {
        TLRPC.Message target = tw.nekomimi.nekogram.helpers.MessageHelper.getInstance(account)
                .getMessage(dialogId, source.id);
        if (target == null) return;

        target.message = source.message;
        target.edit_date = source.edit_date;
        target.flags |= 32768;
        target.entities = source.entities == null
                ? new ArrayList<>()
                : new ArrayList<>(source.entities);
        if (!target.entities.isEmpty()) {
            target.flags |= 128;
        }

        ArrayList<TLRPC.Message> raw = new ArrayList<>();
        raw.add(target);
        MessagesStorage.getInstance(account).putMessages(raw, false, true, false, 0, 0, 0);

        ArrayList<MessageObject> objects = new ArrayList<>();
        objects.add(new MessageObject(account, target, true, true));
        AndroidUtilities.runOnUIThread(() ->
                NotificationCenter.getInstance(account).postNotificationName(
                        NotificationCenter.replaceMessagesObjects,
                        dialogId,
                        objects
                )
        );
    }

    public static boolean commitLocalReaction(int account, MessageObject object) {
        if (!isTestAccount(account) || object == null || object.messageOwner == null) {
            return false;
        }

        ArrayList<TLRPC.Message> raw = new ArrayList<>();
        raw.add(object.messageOwner);
        MessagesStorage.getInstance(account).putMessages(raw, false, true, false, 0, 0, 0);

        ArrayList<MessageObject> objects = new ArrayList<>();
        objects.add(object);
        NotificationCenter.getInstance(account).postNotificationName(
                NotificationCenter.replaceMessagesObjects,
                object.getDialogId(),
                objects
        );

        mirrorReactions(account, object.getDialogId(), object.messageOwner);
        return true;
    }

    private static void mirrorReactions(int sourceAccount, long dialogId, TLRPC.Message source) {
        if (source == null) return;

        if (dialogId > 0) {
            int recipient = accountForTestUser(dialogId);
            if (recipient >= 0 && recipient != sourceAccount) {
                long targetDialogId = UserConfig.getInstance(sourceAccount).getClientUserId();
                updateMirroredReactions(recipient, targetDialogId, source);
            }
            return;
        }

        for (int other = 0; other < UserConfig.MAX_ACCOUNT_COUNT; other++) {
            if (other == sourceAccount || !isTestAccount(other)) continue;
            updateMirroredReactions(other, dialogId, source);
        }
    }

    private static void updateMirroredReactions(int account, long dialogId, TLRPC.Message source) {
        TLRPC.Message target = tw.nekomimi.nekogram.helpers.MessageHelper.getInstance(account)
                .getMessage(dialogId, source.id);
        if (target == null) return;

        target.reactions = cloneReactionsForTarget(source.reactions, target.reactions);
        if (target.reactions != null) {
            target.flags |= 1048576; // reactions
        } else {
            target.flags &= ~1048576;
        }

        ArrayList<TLRPC.Message> raw = new ArrayList<>();
        raw.add(target);
        MessagesStorage.getInstance(account).putMessages(raw, false, true, false, 0, 0, 0);

        ArrayList<MessageObject> objects = new ArrayList<>();
        objects.add(new MessageObject(account, target, true, true));
        AndroidUtilities.runOnUIThread(() ->
                NotificationCenter.getInstance(account).postNotificationName(
                        NotificationCenter.replaceMessagesObjects,
                        dialogId,
                        objects
                )
        );
    }

    private static TLRPC.TL_messageReactions cloneReactionsForTarget(
            TLRPC.MessageReactions source,
            TLRPC.MessageReactions previousTarget
    ) {
        if (source == null) return null;

        TLRPC.TL_messageReactions result = new TLRPC.TL_messageReactions();
        result.flags = source.flags;
        result.min = source.min;
        result.can_see_list = source.can_see_list;
        result.reactions_as_tags = source.reactions_as_tags;

        for (TLRPC.ReactionCount sourceCount : source.results) {
            TLRPC.TL_reactionCount count = new TLRPC.TL_reactionCount();
            count.reaction = sourceCount.reaction;
            count.count = sourceCount.count;
            count.flags = sourceCount.flags & ~1;
            count.chosen = false;

            TLRPC.ReactionCount previous = findReactionCount(previousTarget, sourceCount.reaction);
            if (previous != null && previous.chosen) {
                count.chosen = true;
                count.chosen_order = previous.chosen_order;
                count.flags |= 1;
            }
            result.results.add(count);
        }

        result.recent_reactions = new ArrayList<>(source.recent_reactions);
        result.top_reactors = new ArrayList<>(source.top_reactors);
        return result;
    }

    private static TLRPC.ReactionCount findReactionCount(TLRPC.MessageReactions reactions, TLRPC.Reaction reaction) {
        if (reactions == null || reaction == null) return null;
        for (TLRPC.ReactionCount count : reactions.results) {
            if (sameReaction(count.reaction, reaction)) return count;
        }
        return null;
    }

    private static boolean sameReaction(TLRPC.Reaction a, TLRPC.Reaction b) {
        if (a == b) return true;
        if (a == null || b == null || a.getClass() != b.getClass()) return false;
        if (a instanceof TLRPC.TL_reactionEmoji) {
            return TextUtils.equals(
                    ((TLRPC.TL_reactionEmoji) a).emoticon,
                    ((TLRPC.TL_reactionEmoji) b).emoticon
            );
        }
        if (a instanceof TLRPC.TL_reactionCustomEmoji) {
            return ((TLRPC.TL_reactionCustomEmoji) a).document_id
                    == ((TLRPC.TL_reactionCustomEmoji) b).document_id;
        }
        return false;
    }

    public static void mirrorLocalDelete(int sourceAccount, long dialogId, ArrayList<Integer> messageIds) {
        if (!isTestAccount(sourceAccount) || messageIds == null || messageIds.isEmpty()) return;

        if (dialogId > 0) {
            int recipient = accountForTestUser(dialogId);
            if (recipient >= 0 && recipient != sourceAccount) {
                long recipientDialogId = UserConfig.getInstance(sourceAccount).getClientUserId();
                deleteFromAccount(recipient, recipientDialogId, messageIds);
            }
            return;
        }

        for (int other = 0; other < UserConfig.MAX_ACCOUNT_COUNT; other++) {
            if (other == sourceAccount || !isTestAccount(other)) continue;
            deleteFromAccount(other, dialogId, messageIds);
        }
    }

    private static void deleteFromAccount(int account, long dialogId, ArrayList<Integer> ids) {
        ArrayList<Integer> copy = new ArrayList<>(ids);
        long channelId = dialogId < 0 ? -dialogId : 0;
        MessagesStorage.getInstance(account).markMessagesAsDeleted(
                dialogId, copy, true, false, ChatActivity.MODE_DEFAULT, 0);
        MessagesStorage.getInstance(account).updateDialogsWithDeletedMessages(
                dialogId, channelId, copy, null);
        AndroidUtilities.runOnUIThread(() -> {
            NotificationCenter.getInstance(account).postNotificationName(
                    NotificationCenter.messagesDeleted,
                    copy,
                    channelId,
                    false,
                    false,
                    false,
                    0
            );
            NotificationCenter.getInstance(account).postNotificationName(NotificationCenter.dialogsNeedReload);
        });
    }

    public static boolean sendLocalTyping(int sourceAccount, long dialogId, long threadMsgId, int action) {
        if (!isTestAccount(sourceAccount) || dialogId == 0) return false;

        TLRPC.User self = UserConfig.getInstance(sourceAccount).getCurrentUser();
        if (self == null) return true;

        TLRPC.SendMessageAction typingAction = createTypingAction(action);
        if (typingAction == null) return true;

        if (dialogId > 0) {
            int recipient = accountForTestUser(dialogId);
            if (recipient >= 0 && recipient != sourceAccount) {
                ensurePeerKnown(recipient, self);
                TL_update.TL_updateUserTyping update = new TL_update.TL_updateUserTyping();
                update.user_id = self.id;
                update.action = typingAction;
                if (threadMsgId != 0) {
                    update.flags |= 1;
                    update.top_msg_id = (int) threadMsgId;
                }
                ArrayList<TLRPC.Update> updates = new ArrayList<>();
                updates.add(update);
                MessagesController.getInstance(recipient).processUpdateArray(
                        updates, null, null, false, (int) (System.currentTimeMillis() / 1000L));
            }
            return true;
        }

        long chatId = -dialogId;
        for (int other = 0; other < UserConfig.MAX_ACCOUNT_COUNT; other++) {
            if (other == sourceAccount || !isTestAccount(other)) continue;
            ensurePeerKnown(other, self);

            TL_update.TL_updateChannelUserTyping update = new TL_update.TL_updateChannelUserTyping();
            update.channel_id = chatId;
            update.from_id = new TLRPC.TL_peerUser();
            update.from_id.user_id = self.id;
            update.action = createTypingAction(action);
            if (threadMsgId != 0) {
                update.flags |= 1;
                update.top_msg_id = (int) threadMsgId;
            }
            ArrayList<TLRPC.Update> updates = new ArrayList<>();
            updates.add(update);
            MessagesController.getInstance(other).processUpdateArray(
                    updates, null, null, false, (int) (System.currentTimeMillis() / 1000L));
        }
        return true;
    }

    private static void ensurePeerKnown(int account, TLRPC.User source) {
        TLRPC.TL_user peer = peerCopy(source);
        if (peer == null) return;
        ArrayList<TLRPC.User> users = new ArrayList<>();
        users.add(peer);
        MessagesController.getInstance(account).putUsers(users, false);
        MessagesStorage.getInstance(account).putUsersAndChats(users, null, true, true);
    }

    private static TLRPC.SendMessageAction createTypingAction(int action) {
        switch (action) {
            case 0:
                return new TLRPC.TL_sendMessageTypingAction();
            case 1:
                return new TLRPC.TL_sendMessageRecordAudioAction();
            case 2:
                return new TLRPC.TL_sendMessageCancelAction();
            case 3:
                return new TLRPC.TL_sendMessageUploadDocumentAction();
            case 4:
                return new TLRPC.TL_sendMessageUploadPhotoAction();
            case 5:
                return new TLRPC.TL_sendMessageUploadVideoAction();
            case 6:
                return new TLRPC.TL_sendMessageGamePlayAction();
            case 7:
                return new TLRPC.TL_sendMessageRecordRoundAction();
            case 8:
                return new TLRPC.TL_sendMessageUploadRoundAction();
            case 9:
                return new TLRPC.TL_sendMessageUploadAudioAction();
            case 10:
                return new TLRPC.TL_sendMessageChooseStickerAction();
            default:
                return new TLRPC.TL_sendMessageTypingAction();
        }
    }

    public static boolean handleLocalRead(int account, long dialogId, int maxPositiveId) {
        if (!isTestAccount(account)) return false;

        if (dialogId > 0 && maxPositiveId > 0) {
            int senderAccount = accountForTestUser(dialogId);
            TLRPC.User reader = UserConfig.getInstance(account).getCurrentUser();
            if (senderAccount >= 0 && senderAccount != account && reader != null) {
                TL_update.TL_updateReadHistoryOutbox update = new TL_update.TL_updateReadHistoryOutbox();
                update.peer = new TLRPC.TL_peerUser();
                update.peer.user_id = reader.id;
                update.max_id = maxPositiveId;
                update.pts = 0;
                update.pts_count = 0;
                ArrayList<TLRPC.Update> updates = new ArrayList<>();
                updates.add(update);
                MessagesController.getInstance(senderAccount).processUpdateArray(
                        updates,
                        null,
                        null,
                        false,
                        (int) (System.currentTimeMillis() / 1000L)
                );
            }
        }
        return true;
    }

    public static boolean isTestAccountOnline(int account) {
        if (!isTestAccount(account)) return false;
        TLRPC.User user = UserConfig.getInstance(account).getCurrentUser();
        return user != null
                && user.status instanceof TLRPC.TL_userStatusOnline
                && user.status.expires > (int) (System.currentTimeMillis() / 1000L);
    }

    public static void setTestAccountOnline(int account, boolean online) {
        if (!isTestAccount(account)) return;
        TLRPC.User self = UserConfig.getInstance(account).getCurrentUser();
        if (self == null) return;

        int now = (int) (System.currentTimeMillis() / 1000L);
        if (online) {
            TLRPC.TL_userStatusOnline status = new TLRPC.TL_userStatusOnline();
            status.expires = now + 3600;
            self.status = status;
        } else {
            TLRPC.TL_userStatusOffline status = new TLRPC.TL_userStatusOffline();
            status.expires = now - 60;
            self.status = status;
        }
        self.flags |= 64;
        UserConfig.getInstance(account).saveConfig(true);

        ArrayList<TLRPC.User> own = new ArrayList<>();
        own.add(self);
        MessagesController.getInstance(account).putUsers(own, false);
        MessagesStorage.getInstance(account).putUsersAndChats(own, null, true, true);
        NotificationCenter.getInstance(account).postNotificationName(
                NotificationCenter.updateInterfaces,
                MessagesController.UPDATE_MASK_STATUS
        );

        for (int other = 0; other < UserConfig.MAX_ACCOUNT_COUNT; other++) {
            if (other == account || !isTestAccount(other)) continue;
            TLRPC.User peer = MessagesController.getInstance(other).getUser(self.id);
            if (peer == null) {
                peer = peerCopy(self);
            } else {
                peer.status = self.status;
                peer.flags |= 64;
            }
            ArrayList<TLRPC.User> users = new ArrayList<>();
            users.add(peer);
            MessagesController.getInstance(other).putUsers(users, false);
            MessagesStorage.getInstance(other).putUsersAndChats(users, null, true, true);
            NotificationCenter.getInstance(other).postNotificationName(
                    NotificationCenter.updateInterfaces,
                    MessagesController.UPDATE_MASK_STATUS
            );
        }
    }

    private static void copyReply(SendMessagesHelper.SendMessageParams params, TLRPC.TL_message message) {
        if (params.replyToMsg == null) return;
        message.reply_to = new TLRPC.TL_messageReplyHeader();
        message.reply_to.flags |= 16;
        message.reply_to.reply_to_msg_id = params.replyToMsg.getId();
        message.flags |= 8;
    }
}
