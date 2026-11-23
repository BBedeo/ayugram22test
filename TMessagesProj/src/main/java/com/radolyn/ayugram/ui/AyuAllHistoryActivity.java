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
import com.radolyn.ayugram.proprietary.AyuMessageUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.SimpleActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.SimpleActionBarMenuOverlay;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.HeaderCell;
import tw.nekomimi.nekogram.ui.cells.NekoMessageCell;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.ItemOptions;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.SizeNotifierFrameLayout;
import org.telegram.ui.Components.BlurredRecyclerView;
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
public class AyuAllHistoryActivity extends BaseFragment {

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
    protected boolean useSimpleActionBar() {
        return true;
    }

    @Override
    public boolean isSupportEdgeToEdge() {
        return true;
    }

    @Override
    public View createView(Context context) {
        simpleActionBar.setBackButtonImage(R.drawable.ic_ab_back);
        simpleActionBar.setTitle(getString(R.string.OEGeneralAyuMoments));
        simpleActionBar.setSubtitle(getString(R.string.OEHistoryAllChats));

        SimpleActionBarMenu menu = simpleActionBar.createMenu();
        ActionBarMenuItem searchItem = menu.addItem(MENU_SEARCH, R.drawable.outline_header_search)
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

        simpleActionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
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

        SizeNotifierFrameLayout frameLayout = new SizeNotifierFrameLayout(context) {
            @Override
            protected boolean isActionBarVisible() {
                return false;
            }

            @Override
            protected boolean isStatusBarVisible() {
                return false;
            }

            @Override
            protected boolean useRootView() {
                return false;
            }
        };
        frameLayout.setOccupyStatusBar(false);
        frameLayout.setBackgroundImage(Theme.getCachedWallpaper(), Theme.isWallpaperMotion());
        fragmentView = frameLayout;

        listView = new BlurredRecyclerView(context);
        listView.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
        listView.setVerticalScrollBarEnabled(false);
        listView.setClipToPadding(false);
        listView.setSelectorType(9);
        listView.setSelectorDrawableColor(0);
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
        emptyView.setTextColor(getThemedColor(Theme.key_chat_serviceText));
        emptyView.setBackground(Theme.createRoundRectDrawable(dp(16), getThemedColor(Theme.key_chat_serviceBackground)));
        emptyView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        emptyView.setGravity(Gravity.CENTER);
        emptyView.setPadding(dp(18), dp(10), dp(18), dp(10));
        emptyView.setVisibility(View.GONE);
        frameLayout.addView(emptyView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 24, 0, 24, 0));

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
            simpleActionBar.setSubtitle(getString(R.string.OEHistoryAllChats));
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
                    simpleActionBar.setSubtitle(title);
                    loadHistoryAsync();
                });
            }
        }
        if (LiquidGlassTuning.isActive() && !M3SurfaceHelper.isMaterial3OpaqueChrome()) {
            SimpleActionBarMenuOverlay overlay = getSimpleMenuOverlay();
            if (overlay != null && io.openInOverlay(simpleActionBar, filterItem, overlay, null)) {
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
                FrameLayout pillContainer = new FrameLayout(context);
                pillContainer.setPadding(dp(12), dp(8), dp(12), dp(2));
                TextView pill = new TextView(context);
                pill.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12.5f);
                pill.setTypeface(AndroidUtilities.bold());
                pill.setTextColor(AyuAllHistoryActivity.this.getThemedColor(Theme.key_chat_serviceText));
                pill.setBackground(Theme.createRoundRectDrawable(dp(12), AyuAllHistoryActivity.this.getThemedColor(Theme.key_chat_serviceBackground)));
                pill.setPadding(dp(11), dp(4), dp(11), dp(4));
                pill.setSingleLine(true);
                pill.setEllipsize(TextUtils.TruncateAt.END);
                pillContainer.addView(pill, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));
                pillContainer.setTag(pill);
                view = pillContainer;
            } else {
                NekoMessageCell cell = new NekoMessageCell(context, currentAccount);
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
                TextView pill = (TextView) holder.itemView.getTag();
                String tag = getString(entry.isEdited ? R.string.OEHistoryEdited : R.string.OEHistoryDeleted);
                pill.setText(resolveDialogName(entry.dialogId) + " • " + tag);
            } else {
                NekoMessageCell cell = (NekoMessageCell) holder.itemView;
                cell.setMessageObject(entry.messageObject, null, false, false, false);
                cell.setId(idx);
            }
        }
    }
}
