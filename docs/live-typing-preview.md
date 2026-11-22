# Живое превью печати (live typing drafts)

Настройка: **ExFullGram → Чаты → Поле ввода → «Живое превью печати»** (`OEChatsLiveTypingPreview`, по умолчанию **выключено**).
Участвует в поаккаунтных профилях (`ConfigItem` в `ChatsConfig`), ищется по строке «печати / typing / draft».

## Что делает

Пока вы печатаете в личном чате, собеседник видит **реальный текст** вашего черновика в пузыре над полем ввода, а не только
анимированные точки. Когда печатает он — вы видите его текст так же: новые символы дорисовываются «печатной машинкой»,
пузырь растёт на глазах, при отправке сообщения — превращается в него (`morphIntoMessage()`), при остановке — уезжает вниз и гаснет.

## Транспорт

Используется официальный механизм **live drafts**:

```
sendMessageTextDraftAction#3630b85a flags:# can_stop:flags.0?true keep_on_stop:flags.1?true
    random_id:long text:TextWithEntities = SendMessageAction;
messages.setTyping peer:InputPeer top_msg_id:flags.0?int action:SendMessageAction = Bool;
updateUserTyping flags:# user_id:long top_msg_id:flags.0?int action:SendMessageAction = Update;
```

- Отправка: `LiveTypingDraftManager.sendDraft()` → `messages.setTyping` c `sendMessageTextDraftAction`.
- Приём: `MessagesController.processUpdateArray()` → `LiveTypingDraftManager.onIncomingDraft()` (до bot-forum-пайплайна,
  чтобы не ломать стриминговые черновики ботов).
- Жизнь превью: `message_typing_draft_ttl` из `AppGlobalConfig` (минус 400 мс запаса), плюс мгновенное удаление
  по `sendMessageCancelAction` от собеседника и по любому новому сообщению в чате.

## Лимиты

Сервер режет `messages.setTyping` на **20 вызовов / 5 с** и **40 / 30 с** на peer (`FLOOD_WAIT_%d`).
Поэтому в менеджере: дебаунс 260 мс после последнего нажатия, не чаще одного апдейта в **1.2 с** на диалог,
не больше **16** апдейтов за 30 секунд, текст обрезается до 800 символов, повтор одного и того же текста не отправляется.

Ghost Mode (`NekoConfig.sendUploadProgress == false`) блокирует `messages.setTyping` на уровне `ConnectionsManager`,
поэтому живые черновики автоматически не уходят в призрачном режиме — отдельной проверки не требуется.

## Ограничения

- Только личные чаты с пользователями. Группы/каналы — нет: сервер принимает live drafts только для юзеров.
- Секретные чаты — нет: там `messages.setEncryptedTyping` с булевым флагом, payload не ходит.
- Собеседник увидит текст, только если его клиент умеет рендерить `sendMessageTextDraftAction`
  (ExFullGram, официальные tdesktop/iOS/Android поддерживают этот конструктор).
- Время жизни превью задаёт сервер (`message_typing_draft_ttl`), клиент его не продлевает.

## Test Mode

Серверного транспорта в Test Mode нет, поэтому `DemoBackend.sendLocalDraft()` заворачивает текст в тот же
`TL_sendMessageTextDraftAction` и прогоняет через тот же `MessagesController.processUpdateArray()` —
рендер, TTL и анимации те же, что в live-аккаунте. Чтобы фичу было видно на одном устройстве,
Test-«собеседник» повторяет текст, который вы печатаете: это демонстрация рендера, а не чужой черновик.
Отдельного Test-UI нет.

## Проверено

- `scripts/check_*.py` — все 9 скриптов, код 0.
- Сборка `:TMessagesProj:assembleRelease` и установка на устройство.
- Визуальная проверка на живом чате — за пользователем (второй клиент с этой же сборкой).

## Связанная настройка оформления

Индикатор «печатает» внизу — это отдельная, уже существующая опция
**Оформление → «Печатает» пузырём у клавиатуры** (`OEAppearanceTypingBubbleAtBottom`).
Она отвечает за *место* индикатора, живое превью — за *содержимое*; при включённой опции
верхний подзаголовок «печатает…» скрывается (`ChatAvatarContainer`), дублирования нет.
Пузырь перекрашивается под текущую тему: Liquid Glass (нативная рефракция), Material 3
(`M3SurfaceHelper.getCardColor` + `getOutlineVariantColor`) или классическая тема
(`chat_inBubble` / `chat_messageTextIn` / `chat_inTimeText`).
