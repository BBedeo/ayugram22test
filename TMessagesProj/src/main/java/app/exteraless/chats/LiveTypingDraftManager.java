package app.exteraless.chats;

import android.text.TextUtils;
import android.util.LongSparseArray;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BaseController;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

import app.exteraless.demo.DemoBackend;

/**
 * Живое превью печати: обмен «живыми черновиками» (live drafts, {@code sendMessageTextDraftAction})
 * между клиентами, которые их поддерживают.
 *
 * <p>Исходящая сторона перехватывает текст композера и с троттлингом отправляет
 * {@code messages.setTyping} с действием {@code sendMessageTextDraftAction}.
 * Серверные лимиты: 20 вызовов / 5 с и 40 / 30 с на один peer, поэтому мы шлём не чаще,
 * чем раз в MIN_SEND_INTERVAL_MS и не больше MAX_SENDS_PER_WINDOW за 30 секунд.
 *
 * <p>Входящая сторона хранит последний черновик по диалогу и отдаёт его UI
 * ({@link org.telegram.ui.Components.TypingBubbleView}) на message_typing_draft_ttl секунд.
 *
 * <p>Работает только в личных чатах с пользователями: сервер принимает live drafts только для юзеров,
 * а в секретных чатах messages.setTyping не используется вовсе.
 */
public class LiveTypingDraftManager extends BaseController {

    /** Минимальный интервал между двумя исходящими черновиками в один диалог. */
    private static final long MIN_SEND_INTERVAL_MS = 1200L;
    /** Пауза после последнего нажатия, чтобы не отправлять на каждый символ. */
    private static final long SEND_DEBOUNCE_MS = 260L;
    /** Сколько символов черновика максимум уходит на сервер. */
    private static final int MAX_DRAFT_CHARS = 800;
    private static final long RATE_WINDOW_MS = 30000L;
    private static final int MAX_SENDS_PER_WINDOW = 16;
    /** Насколько раньше TTL сервера убирать превью локально. */
    private static final long TTL_SAFETY_MARGIN_MS = 400L;

    private static volatile LiveTypingDraftManager[] Instance = new LiveTypingDraftManager[UserConfig.MAX_ACCOUNT_COUNT];
    private static final Object sync = new Object();

    public static LiveTypingDraftManager getInstance(int num) {
        LiveTypingDraftManager localInstance = Instance[num];
        if (localInstance == null) {
            synchronized (sync) {
                localInstance = Instance[num];
                if (localInstance == null) {
                    Instance[num] = localInstance = new LiveTypingDraftManager(num);
                }
            }
        }
        return localInstance;
    }

    private static class IncomingDraft {
        CharSequence text;
        long receivedAt;
        Runnable expireRunnable;
    }

    private static class OutgoingDraft {
        CharSequence pendingText;
        Runnable pendingRunnable;
        long randomId;
        long lastSentAt;
        CharSequence lastSentText;
        long windowStart;
        int windowCount;
    }

    private final LongSparseArray<IncomingDraft> incomingDrafts = new LongSparseArray<>();
    private final LongSparseArray<OutgoingDraft> outgoingDrafts = new LongSparseArray<>();

    public LiveTypingDraftManager(int num) {
        super(num);
    }

    // ------------------------------------------------------------------ incoming

    /**
     * @return true, если черновик обработан нами и официальный bot-forum-пайплайн трогать не нужно.
     */
    public boolean onIncomingDraft(long dialogId, long userId, TLRPC.TL_sendMessageTextDraftAction action) {
        if (action == null) {
            return false;
        }
        if (!ChatsConfig.liveTypingPreview()) {
            return false;
        }
        if (!isSupportedDialog(dialogId)) {
            return false;
        }
        if (UserObject.isBotForum(getMessagesController().getUser(userId))) {
            // официальный рендер стриминговых черновиков в bot forums оставляем как есть
            return false;
        }
        final String text = action.text != null ? action.text.text : null;
        final long now = System.currentTimeMillis();
        if (TextUtils.isEmpty(text)) {
            removeIncoming(dialogId, true);
            return true;
        }
        IncomingDraft draft = incomingDrafts.get(dialogId);
        if (draft == null) {
            draft = new IncomingDraft();
            incomingDrafts.put(dialogId, draft);
        }
        draft.text = text;
        draft.receivedAt = now;
        if (draft.expireRunnable != null) {
            AndroidUtilities.cancelRunOnUIThread(draft.expireRunnable);
        }
        final IncomingDraft finalDraft = draft;
        draft.expireRunnable = () -> {
            if (incomingDrafts.get(dialogId) == finalDraft) {
                removeIncoming(dialogId, true);
            }
        };
        AndroidUtilities.runOnUIThread(draft.expireRunnable, getDraftTtlMs());
        notifyDraftChanged(dialogId);
        return true;
    }

    /** Пользователь перестал печатать (пришёл sendMessageCancelAction) — превью больше не актуально. */
    public void onPeerTypingStopped(long dialogId) {
        if (incomingDrafts.get(dialogId) != null) {
            removeIncoming(dialogId, true);
        }
    }

    public CharSequence getDraftText(long dialogId) {
        IncomingDraft draft = incomingDrafts.get(dialogId);
        if (draft == null) {
            return null;
        }
        if (System.currentTimeMillis() - draft.receivedAt > getDraftTtlMs()) {
            removeIncoming(dialogId, true);
            return null;
        }
        return draft.text;
    }

    public boolean hasDraft(long dialogId) {
        return getDraftText(dialogId) != null;
    }

    public void clearDraft(long dialogId) {
        removeIncoming(dialogId, true);
    }

    private long getDraftTtlMs() {
        final long serverTtl = Math.max(2000L, getAppGlobalConfig().messageTypingDraftTtl.get(TimeUnit.MILLISECONDS));
        return Math.max(1500L, serverTtl - TTL_SAFETY_MARGIN_MS);
    }

    private void removeIncoming(long dialogId, boolean notify) {
        IncomingDraft draft = incomingDrafts.get(dialogId);
        if (draft == null) {
            return;
        }
        if (draft.expireRunnable != null) {
            AndroidUtilities.cancelRunOnUIThread(draft.expireRunnable);
        }
        incomingDrafts.remove(dialogId);
        if (notify) {
            notifyDraftChanged(dialogId);
        }
    }

    private void notifyDraftChanged(long dialogId) {
        AndroidUtilities.runOnUIThread(() ->
                getNotificationCenter().postNotificationName(NotificationCenter.liveTypingDraftUpdate, dialogId));
    }

    // ------------------------------------------------------------------ outgoing

    /** Вызывается из ChatActivity на каждое изменение текста в поле ввода. */
    public void onComposerTextChanged(long dialogId, long threadId, CharSequence text, boolean editing) {
        if (editing) {
            return;
        }
        if (!ChatsConfig.liveTypingPreview() || !isSupportedDialog(dialogId)) {
            return;
        }
        final String draft = text == null ? "" : text.toString();
        OutgoingDraft state = outgoingDrafts.get(dialogId);
        if (state == null) {
            state = new OutgoingDraft();
            state.randomId = org.telegram.messenger.Utilities.random.nextLong();
            outgoingDrafts.put(dialogId, state);
        }
        state.pendingText = draft;
        if (state.pendingRunnable != null) {
            AndroidUtilities.cancelRunOnUIThread(state.pendingRunnable);
            state.pendingRunnable = null;
        }
        if (TextUtils.isEmpty(draft)) {
            // Пустой черновик не отправляем: превью у собеседника истечёт по TTL.
            return;
        }
        final OutgoingDraft finalState = state;
        final long dialogIdF = dialogId;
        final long threadIdF = threadId;
        state.pendingRunnable = () -> {
            finalState.pendingRunnable = null;
            flushOutgoing(dialogIdF, threadIdF, finalState);
        };
        AndroidUtilities.runOnUIThread(state.pendingRunnable, SEND_DEBOUNCE_MS);
    }

    private void flushOutgoing(long dialogId, long threadId, OutgoingDraft state) {
        final CharSequence text = state.pendingText;
        if (TextUtils.isEmpty(text)) {
            return;
        }
        if (TextUtils.equals(text, state.lastSentText)) {
            return;
        }
        final long now = System.currentTimeMillis();
        if (now - state.lastSentAt < MIN_SEND_INTERVAL_MS) {
            if (state.pendingRunnable != null) {
                AndroidUtilities.cancelRunOnUIThread(state.pendingRunnable);
            }
            final long delay = MIN_SEND_INTERVAL_MS - (now - state.lastSentAt) + 10;
            state.pendingRunnable = () -> {
                state.pendingRunnable = null;
                flushOutgoing(dialogId, threadId, state);
            };
            AndroidUtilities.runOnUIThread(state.pendingRunnable, delay);
            return;
        }
        if (now - state.windowStart > RATE_WINDOW_MS) {
            state.windowStart = now;
            state.windowCount = 0;
        }
        if (state.windowCount >= MAX_SENDS_PER_WINDOW) {
            FileLog.d("LiveTypingDraft: local rate limit for " + dialogId);
            return;
        }
        state.windowCount++;
        state.lastSentAt = now;
        state.lastSentText = text;

        String payload = text.toString();
        if (payload.length() > MAX_DRAFT_CHARS) {
            payload = payload.substring(0, MAX_DRAFT_CHARS);
        }
        sendDraft(dialogId, threadId, state.randomId, payload);
    }

    private void sendDraft(long dialogId, long threadId, long randomId, String text) {
        if (DemoBackend.isTestAccount(currentAccount)) {
            DemoBackend.sendLocalDraft(currentAccount, dialogId, threadId, text);
            return;
        }
        TLRPC.InputPeer peer = getMessagesController().getInputPeer(dialogId);
        if (peer == null) {
            return;
        }
        TLRPC.TL_messages_setTyping req = new TLRPC.TL_messages_setTyping();
        req.peer = peer;
        if (threadId != 0) {
            req.top_msg_id = (int) threadId;
            req.flags |= 1;
        }
        TLRPC.TL_sendMessageTextDraftAction action = new TLRPC.TL_sendMessageTextDraftAction();
        action.random_id = randomId;
        action.text = new TLRPC.TL_textWithEntities();
        action.text.text = text;
        action.text.entities = new ArrayList<>();
        req.action = action;
        getConnectionsManager().sendRequest(req, (response, error) -> {
            if (error != null) {
                FileLog.d("LiveTypingDraft: setTyping failed " + error.code + " " + error.text);
            }
        });
    }

    /** Чат закрыт: отменяем отложенные отправки. */
    public void onChatClosed(long dialogId) {
        OutgoingDraft state = outgoingDrafts.get(dialogId);
        if (state != null) {
            if (state.pendingRunnable != null) {
                AndroidUtilities.cancelRunOnUIThread(state.pendingRunnable);
                state.pendingRunnable = null;
            }
            state.pendingText = null;
        }
    }

    private boolean isSupportedDialog(long dialogId) {
        if (!DialogObject.isUserDialog(dialogId) || DialogObject.isEncryptedDialog(dialogId)) {
            return false;
        }
        long selfId = UserConfig.getInstance(currentAccount).getClientUserId();
        return dialogId != 0 && dialogId != selfId;
    }
}
