package com.radolyn.ayugram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.radolyn.ayugram.database.entities.DeletedMessageFull;
import com.radolyn.ayugram.database.entities.EditedMessage;
import com.radolyn.ayugram.messages.AyuMessagesController;
import com.radolyn.ayugram.proprietary.AyuHistoryHook;
import com.radolyn.ayugram.utils.AyuMessageUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.SimpleActionBarMenuOverlay;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.ItemOptions;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.blur3.BlurredRecyclerView;
import org.telegram.utils.glass.BaseFragmentSimpleGlass;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import app.exteraless.appearance.LiquidGlassTuning;
import app.exteraless.appearance.M3SurfaceHelper;
import app.exteraless.settings.OpenExteraAyuMomentsActivity;

/**
 * Filterable Message History screen showing saved deleted & edited messages across all chats,
 * opened by long-pressing the Chats tab or from menus.
 */
public class AyuAllHistoryActivity extends BaseFragmentSimpleGlass {

    private static final int MENU_FILTER = 1;
    private static final int MENU_SEARCH = 2;
    private static final int MENU_SETTINGS = 3;

    private static final int VIEW_TYPE_HEADER = 0;
    private static final int VIEW_TYPE_MESSAGE = 1;

    private static class HistoryEntry {
        final long dialogId;
        final int messageId;
        final int timestamp;
        final boolean isEdited;
        final MessageObject messageObject;

        HistoryEntry(long dialogId, int messageId, int timestamp, boolean isEdited, MessageObject messageObject) {
            this.dialogId = dialogId;
            this.messageId = messageId;
            this.timestamp = timestamp;
            this.isEdited = isEdited;
            this.messageObject = messageObject;
        }
    }

    private BlurredRecyclerView listView;
    private TextView emptyView;
    private ListAdapter adapter;
    private ActionBarMenuItem filterItem;

    private final ArrayList<HistoryEntry> allEntries = new ArrayList<>();
    private final ArrayList<HistoryEntry> filteredEntries = new ArrayList<>();
    private final ArrayList<Long> availableDialogIds = new ArrayList<>();

    private long selectedDialogId = 0L;
    private String searchQuery = "";

    @Override
    protected RecyclerListView getListViewForSimpleGlass() {
        return listView;
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(getString(R.string.OEGeneralAyuMoments));
        actionBar.setSubtitle(getString(R.string.OEHistoryAllChats));

        ActionBarMenu menu = actionBar.createMenu();
        ActionBarMenuItem searchItem = menu.addItem(MENU_SEARCH, R.drawable.ic_ab_search)
                .setIsSearchField(true)
                .setActionBarMenuItemSearchListener(new ActionBarMenuItem.ActionBarMenuItemSearchListener() {
                    @Override
                    public void onSearchCollapse() {
                        searchQuery = "";
                        applyFilter();
                    }

                    @Override
                    public void onTextChanged(EditText editText) {
                        searchQuery = editText.getText() == null ? "" : editText.getText().toString();
                        applyFilter();
                    }
                });
        searchItem.setSearchFieldHint(getString(R.string.Search));

        filterItem = menu.addItem(MENU_FILTER, R.drawable.msg_groups);
        menu.addItem(MENU_SETTINGS, R.drawable.msg_settings_old);

        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                } else if (id == MENU_FILTER) {
                    showChatFilterMenu();
                } else if (id == MENU_SETTINGS) {
                    presentFragment(new OpenExteraAyuMomentsActivity());
                }
            }
        });

        FrameLayout frameLayout = new FrameLayout(context);
        frameLayout.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundGray));
        fragmentView = frameLayout;

        listView = new BlurredRecyclerView(context);
        listView.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
        listView.setVerticalScrollBarEnabled(false);
        listView.setClipToPadding(false);
        adapter = new ListAdapter(context);
        listView.setAdapter(adapter);
        listView.setOnItemClickListener((view, position) -> {
            int msgIdx = position / 2;
            if (position % 2 == 1 && msgIdx >= 0 && msgIdx < filteredEntries.size()) {
                HistoryEntry entry = filteredEntries.get(msgIdx);
                Bundle args = new Bundle();
                if (entry.dialogId > 0) {
                    args.putLong("user_id", entry.dialogId);
                } else {
                    args.putLong("chat_id", -entry.dialogId);
                }
                if (entry.messageId > 0) {
                    args.putInt("message_id", entry.messageId);
                }
                presentFragment(new ChatActivity(args));
            }
        });
        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        emptyView = new TextView(context);
        emptyView.setText(getString(R.string.OEHistoryEmpty));
        emptyView.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText));
        emptyView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        emptyView.setGravity(Gravity.CENTER);
        emptyView.setPadding(dp(32), dp(16), dp(32), dp(16));
        emptyView.setVisibility(View.GONE);
        frameLayout.addView(emptyView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));

        loadHistoryAsync();
        return fragmentView;
    }

    private void loadHistoryAsync() {
        final long clientUserId = getUserConfig().getClientUserId();
        final long filterDialog = selectedDialogId;
        Utilities.globalQueue.postRunnable(() -> {
            AyuMessagesController ctrl = AyuMessagesController.getInstance();
            List<Long> dialogIds = ctrl.getHistoryDialogIds(clientUserId);
            List<DeletedMessageFull> deletedList = filterDialog == 0
                    ? ctrl.getAllDeletedLatest(clientUserId, 150)
                    : ctrl.getDeletedByDialog(clientUserId, filterDialog, 150);
            List<EditedMessage> editedList = filterDialog == 0
                    ? ctrl.getAllEditedLatest(clientUserId, 150)
                    : ctrl.getEditedByDialog(clientUserId, filterDialog, 150);

            ArrayList<HistoryEntry> loaded = new ArrayList<>();
            if (deletedList != null) {
                for (DeletedMessageFull full : deletedList) {
                    if (full == null || full.message == null) continue;
                    TLRPC.TL_message msg = AyuHistoryHook.map(full, currentAccount);
                    msg.ayuDeleted = true;
                    MessageObject mo = new MessageObject(currentAccount, msg, false, false);
                    loaded.add(new HistoryEntry(full.message.dialogId, full.message.messageId, full.message.entityCreateDate, false, mo));
                }
            }
            if (editedList != null) {
                for (EditedMessage em : editedList) {
                    if (em == null) continue;
                    TLRPC.TL_message msg = new TLRPC.TL_message();
                    AyuMessageUtils.map(em, msg, currentAccount);
                    AyuMessageUtils.mapMedia(em, msg, currentAccount);
                    msg.ayuDeleted = true;
                    msg.date = em.entityCreateDate;
                    msg.edit_hide = true;
                    MessageObject mo = new MessageObject(currentAccount, msg, false, false);
                    loaded.add(new HistoryEntry(em.dialogId, em.messageId, em.entityCreateDate, true, mo));
                }
            }
            Collections.sort(loaded, (a, b) -> Integer.compare(b.timestamp, a.timestamp));

            AndroidUtilities.runOnUIThread(() -> {
                if (getParentActivity() == null) return;
                availableDialogIds.clear();
                if (dialogIds != null) {
                    availableDialogIds.addAll(dialogIds);
                }
                allEntries.clear();
                allEntries.addAll(loaded);
                applyFilter();
            });
        });
    }

    @SuppressLint("NotifyDataSetChanged")
    private void applyFilter() {
        filteredEntries.clear();
        String q = searchQuery == null ? "" : searchQuery.trim().toLowerCase();
        for (HistoryEntry e : allEntries) {
            if (selectedDialogId != 0 && e.dialogId != selectedDialogId) {
                continue;
            }
            if (!q.isEmpty()) {
                CharSequence txt = e.messageObject.messageText;
                String dialogName = resolveDialogName(e.dialogId).toLowerCase();
                String msgStr = txt == null ? "" : txt.toString().toLowerCase();
                if (!msgStr.contains(q) && !dialogName.contains(q)) {
                    continue;
                }
            }
            filteredEntries.add(e);
        }
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
        if (emptyView != null) {
            emptyView.setVisibility(filteredEntries.isEmpty() ? View.VISIBLE : View.GONE);
        }
    }

    private void showChatFilterMenu() {
        if (filterItem == null || getParentActivity() == null) {
            return;
        }
        ItemOptions io = ItemOptions.makeOptions(this, filterItem);
        io.addChecked(selectedDialogId == 0, getString(R.string.OEHistoryAllChats), () -> {
            selectedDialogId = 0L;
            actionBar.setSubtitle(getString(R.string.OEHistoryAllChats));
            loadHistoryAsync();
        });
        if (!availableDialogIds.isEmpty()) {
            io.addGap();
            for (Long did : availableDialogIds) {
                if (did == null || did == 0L) continue;
                final long targetDid = did;
                final String title = resolveDialogName(targetDid);
                io.addChecked(selectedDialogId == targetDid, title, () -> {
                    selectedDialogId = targetDid;
                    actionBar.setSubtitle(title);
                    loadHistoryAsync();
                });
            }
        }
        if (LiquidGlassTuning.isActive() && !M3SurfaceHelper.isMaterial3OpaqueChrome()) {
            SimpleActionBarMenuOverlay overlay = getSimpleMenuOverlay();
            if (overlay != null && io.openInOverlay(actionBar, filterItem, overlay, null)) {
                return;
            }
        }
        io.show();
    }

    private String resolveDialogName(long dialogId) {
        Object peer = getMessagesController().getUserOrChat(dialogId);
        if (peer instanceof TLRPC.User) {
            TLRPC.User u = (TLRPC.User) peer;
            String name = UserObject.getUserName(u);
            if (!TextUtils.isEmpty(name)) return name;
            return ContactsController.formatName(u.first_name, u.last_name);
        } else if (peer instanceof TLRPC.Chat) {
            TLRPC.Chat c = (TLRPC.Chat) peer;
            if (!TextUtils.isEmpty(c.title)) return c.title;
        }
        return String.valueOf(dialogId);
    }

    private class ListAdapter extends RecyclerListView.SelectionAdapter {
        private final Context context;

        ListAdapter(Context context) {
            this.context = context;
        }

        @Override
        public int getItemCount() {
            return filteredEntries.size() * 2;
        }

        @Override
        public int getItemViewType(int position) {
            return position % 2 == 0 ? VIEW_TYPE_HEADER : VIEW_TYPE_MESSAGE;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return holder.getItemViewType() == VIEW_TYPE_MESSAGE;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view;
            if (viewType == VIEW_TYPE_HEADER) {
                HeaderCell header = new HeaderCell(context);
                header.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
                view = header;
            } else {
                AyuMessageHistory.NekoMessageCell cell = new AyuMessageHistory.NekoMessageCell(context, currentAccount);
                cell.setShowAyuDeletedMark(false);
                view = cell;
            }
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            int idx = position / 2;
            if (idx < 0 || idx >= filteredEntries.size()) return;
            HistoryEntry entry = filteredEntries.get(idx);
            if (holder.getItemViewType() == VIEW_TYPE_HEADER) {
                HeaderCell header = (HeaderCell) holder.itemView;
                String tag = getString(entry.isEdited ? R.string.EditsHistoryMenuText : R.string.Delete);
                header.setText(resolveDialogName(entry.dialogId) + " • " + tag);
            } else {
                AyuMessageHistory.NekoMessageCell cell = (AyuMessageHistory.NekoMessageCell) holder.itemView;
                cell.setMessageObject(entry.messageObject, null, false, false, false);
                cell.setId(idx);
            }
        }
    }
}
