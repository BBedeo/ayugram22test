package app.exteraless.settings;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.annotation.SuppressLint;
import android.content.Context;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.SettingsSearchCell;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;

import tw.nekomimi.nekogram.helpers.SettingsHelper;
import tw.nekomimi.nekogram.helpers.SettingsSearchResult;
import tw.nekomimi.nekogram.settings.BaseNekoSettingsActivity;
import tw.nekomimi.nekogram.ui.cells.HeaderCell;

/**
 * Корневой экран раздела ExFullGram.
 * Структура повторяет настройки exteraGram: About-шапка + категории с иконками.
 * Открывается из главных настроек (SettingsActivity, id 102).
 */
public class OpenExteraSettingsActivity extends BaseNekoSettingsActivity {

    private static final int MENU_CLOUD = 1;
    private static final int MENU_SEARCH = 2;
    private static final int MENU_RESET_TEST = 3;

    private static final int TYPE_ABOUT = 100;

    private int aboutRow;

    private int categoriesHeaderRow;
    private int generalRow;
    private int appearanceRow;
    private int chatsRow;
    private int messageHistoryRow;
    private int accountRow;
    private int pluginsRow;
    private int otherRow;
    private int categoriesDividerRow;

    private int linksHeaderRow;
    private int channelRow;
    private int sourceRow;
    private int linksDividerRow;

    private int designHeaderRow;
    private int designerRow;
    private int designStudioRow;
    private int designDividerRow;

    private ActionBarMenuItem searchItem;
    private boolean inSearchMode;
    private String searchQuery = "";
    private ArrayList<SettingsSearchResult> allSearchResults;
    private final ArrayList<SettingsSearchResult> filteredSearchResults = new ArrayList<>();
    private InlineSearchAdapter inlineSearchAdapter;

    @Override
    protected void updateRows() {
        super.updateRows();

        aboutRow = addRow("about");

        categoriesHeaderRow = addRow("categoriesHeader");
        generalRow = addRow("general");
        appearanceRow = addRow("appearance");
        chatsRow = addRow("chats");
        messageHistoryRow = addRow("messageHistory", "ayuMoments");
        accountRow = addRow("accounts");
        pluginsRow = addRow("plugins");
        otherRow = addRow("other");
        categoriesDividerRow = addRow();

        linksHeaderRow = addRow("linksHeader");
        channelRow = addRow("channel");
        sourceRow = addRow("source");
        linksDividerRow = addRow();

        designHeaderRow = addRow("designHeader");
        designerRow = addRow("designer");
        designStudioRow = addRow("designStudio");
        designDividerRow = addRow();
    }

    /**
     * Главный экран настроек ExFullGram: плавающий кристальный заголовок не рисуем,
     * так как логотип AboutHeaderCell однозначно идентифицирует раздел.
     */
    @Override
    public View createView(Context context) {
        View view = super.createView(context);
        // Test accounts are offline: opening mod settings must not initiate
        // Telegram server calls just to fetch unrelated content settings.
        if (!app.exteraless.demo.DemoBackend.isTestAccount(currentAccount)) {
            getMessagesController().getContentSettings(null);
        }
        if (actionBar != null) {
            actionBar.setTitle(null);
            actionBar.setDrawGlassTitle(false);
            if (actionBar.getTitleTextView() != null) {
                actionBar.getTitleTextView().setAlpha(0f);
                actionBar.getTitleTextView().setVisibility(View.GONE);
            }
            final org.telegram.ui.ActionBar.ActionBarMenu menu = actionBar.createMenu();
            searchItem = menu.addItem(MENU_SEARCH, R.drawable.outline_header_search, getResourceProvider())
                    .setIsSearchField(true)
                    .setActionBarMenuItemSearchListener(new ActionBarMenuItem.ActionBarMenuItemSearchListener() {
                        @Override
                        public void onSearchExpand() {
                            enterInlineSearch();
                        }

                        @Override
                        public void onSearchCollapse() {
                            exitInlineSearch();
                        }

                        @Override
                        public void onTextChanged(EditText editText) {
                            filterInlineSearch(editText.getText().toString());
                        }
                    });
            searchItem.setSearchFieldHint(getString(R.string.Search));
            menu.addItem(MENU_CLOUD, R.drawable.cloud_sync, getResourceProvider());
            if (app.exteraless.demo.DemoBackend.isDemoAccount(currentAccount)) {
                menu.addItem(MENU_RESET_TEST, R.drawable.msg_retry_solar, getResourceProvider());
            }
            actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
                @Override
                public void onItemClick(int id) {
                    if (id == -1) {
                        if (actionBar.isSearchFieldVisible()) {
                            actionBar.closeSearchField();
                        } else {
                            finishFragment();
                        }
                    } else if (id == MENU_CLOUD) {
                        presentFragment(new OpenExteraCloudActivity());
                    } else if (id == MENU_RESET_TEST) {
                        app.exteraless.demo.DemoBackend.reset(currentAccount);
                        showDialog(new org.telegram.ui.ActionBar.AlertDialog.Builder(getParentActivity())
                                .setTitle(getString(R.string.ExFullGramDemoResetTitle))
                                .setMessage(getString(R.string.ExFullGramDemoResetDone))
                                .setPositiveButton(getString(R.string.OK), null)
                                .create());
                    }
                }
            });
        }
        return view;
    }

    @Override
    protected int getListViewForSimpleGlassTopPadding() {
        return 0;
    }

    @Override
    protected String getActionBarTitle() {
        return getString(R.string.OpenExtera);
    }

    @Override
    public int getSearchGuid() {
        return 24000;
    }

    @Override
    public int getSearchIcon() {
        return R.drawable.msg_settings_old;
    }

    @Override
    public String getSearchPrefix() {
        return "OpenExtera";
    }

    @Override
    protected String getKey() {
        return "exteraless";
    }

    @Override
    protected BaseListAdapter createAdapter(Context context) {
        return new ListAdapter(context);
    }

    @Override
    protected void onItemClick(View view, int position, float x, float y) {
        if (inSearchMode) {
            int idx = position - 1;
            if (idx >= 0 && idx < filteredSearchResults.size()) {
                SettingsSearchResult r = filteredSearchResults.get(idx);
                if (actionBar != null && actionBar.isSearchFieldVisible()) {
                    actionBar.closeSearchField(false);
                }
                if (r.openRunnable != null) {
                    r.openRunnable.run();
                }
            }
            return;
        }
        if (position == generalRow) {
            presentFragment(new OpenExteraGeneralActivity());
        } else if (position == appearanceRow) {
            presentFragment(new OpenExteraAppearanceActivity());
        } else if (position == chatsRow) {
            presentFragment(new OpenExteraChatsActivity());
        } else if (position == messageHistoryRow) {
            presentFragment(new OpenExteraAyuMomentsActivity());
        } else if (position == accountRow) {
            presentFragment(new AccountSettingsActivity());
        } else if (position == pluginsRow) {
            presentFragment(new app.exteraless.plugins.ui.PluginsActivity());
        } else if (position == otherRow) {
            presentFragment(new OpenExteraOtherActivity());
        } else if (position == channelRow) {
            getMessagesController().openByUserName("exteraless", this, 1);
        } else if (position == sourceRow) {
            org.telegram.messenger.browser.Browser.openUrl(getParentActivity(),
                    "https://github.com/BBedeo/ayugram22test");
        } else if (position == designerRow) {
            getMessagesController().openByUserName("the8055u", this, 1);
        } else if (position == designStudioRow) {
            getMessagesController().openByUserName("BlueprintDsgn", this, 1);
        }
    }

    @Override
    public boolean onBackPressed(boolean invoked) {
        if (actionBar != null && actionBar.isSearchFieldVisible()) {
            if (invoked) {
                actionBar.closeSearchField();
            }
            return false;
        }
        return super.onBackPressed(invoked);
    }

    private void enterInlineSearch() {
        if (listView == null) {
            return;
        }
        inSearchMode = true;
        if (allSearchResults == null) {
            allSearchResults = SettingsHelper.onCreateSearchArray(fragment -> AndroidUtilities.runOnUIThread(() -> {
                try {
                    presentFragment(fragment);
                } catch (Exception ignore) {
                }
            }));
        }
        if (inlineSearchAdapter == null) {
            inlineSearchAdapter = new InlineSearchAdapter(getParentActivity());
        }
        filterInlineSearch("");
        listView.setAdapter(inlineSearchAdapter);
        listView.scrollToPosition(0);
    }

    private void exitInlineSearch() {
        if (!inSearchMode) {
            return;
        }
        inSearchMode = false;
        searchQuery = "";
        if (listView != null && listAdapter != null) {
            listView.setAdapter(listAdapter);
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private void filterInlineSearch(String query) {
        if (allSearchResults == null) {
            return;
        }
        String q = query == null ? "" : query.toLowerCase().trim();
        searchQuery = q;
        filteredSearchResults.clear();
        if (q.isEmpty()) {
            filteredSearchResults.addAll(allSearchResults);
        } else {
            String[] parts = q.split("\\s+");
            for (SettingsSearchResult item : allSearchResults) {
                String title = item.searchTitle == null ? "" : item.searchTitle.toLowerCase();
                String sub = item.searchSubtitle == null ? "" : item.searchSubtitle.toLowerCase();
                boolean ok = true;
                for (String p : parts) {
                    if (!title.contains(p) && !sub.contains(p)) {
                        ok = false;
                        break;
                    }
                }
                if (ok) {
                    filteredSearchResults.add(item);
                }
            }
        }
        if (inlineSearchAdapter != null) {
            inlineSearchAdapter.notifyDataSetChanged();
        }
    }

    private class InlineSearchAdapter extends RecyclerListView.SelectionAdapter {
        private static final int VIEW_TYPE_TOP_SPACE = 0;
        private static final int VIEW_TYPE_RESULT = 1;
        private final Context mContext;

        InlineSearchAdapter(Context context) {
            this.mContext = context;
        }

        @Override
        public int getItemCount() {
            return 1 + filteredSearchResults.size();
        }

        @Override
        public int getItemViewType(int position) {
            return position == 0 ? VIEW_TYPE_TOP_SPACE : VIEW_TYPE_RESULT;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return holder.getItemViewType() == VIEW_TYPE_RESULT;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            if (viewType == VIEW_TYPE_TOP_SPACE) {
                View space = new View(mContext);
                space.setTag(RecyclerListView.TAG_NOT_SECTION);
                int topH = AndroidUtilities.statusBarHeight + ActionBar.getCurrentActionBarHeight() + dp(8);
                space.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, topH));
                return new RecyclerListView.Holder(space);
            }
            SettingsSearchCell cell = new SettingsSearchCell(mContext);
            cell.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
            return new RecyclerListView.Holder(cell);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            if (holder.getItemViewType() != VIEW_TYPE_RESULT) {
                return;
            }
            int idx = position - 1;
            SettingsSearchResult r = filteredSearchResults.get(idx);
            SettingsSearchCell cell = (SettingsSearchCell) holder.itemView;
            String[] path = r.path2 != null ? new String[]{r.path1, r.path2} : new String[]{r.path1};
            CharSequence titleToSet = r.searchTitle == null ? "" : r.searchTitle;
            if (!searchQuery.isEmpty() && titleToSet.length() > 0) {
                SpannableStringBuilder ss = new SpannableStringBuilder(titleToSet);
                String lower = titleToSet.toString().toLowerCase();
                String[] parts = searchQuery.split("\\s+");
                int highlightColor = getThemedColor(Theme.key_windowBackgroundWhiteBlueText4);
                for (String p : parts) {
                    if (p.isEmpty()) continue;
                    int pos = 0;
                    while (true) {
                        int found = lower.indexOf(p, pos);
                        if (found == -1) break;
                        try {
                            ss.setSpan(new ForegroundColorSpan(highlightColor), found, found + p.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        } catch (Exception ignore) {
                        }
                        pos = found + p.length();
                    }
                }
                titleToSet = ss;
            }
            cell.setTextAndValueAndIcon(titleToSet, path, r.iconResId, idx < filteredSearchResults.size() - 1);
        }
    }

    private class ListAdapter extends BaseListAdapter {

        public ListAdapter(Context context) {
            super(context);
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            if (viewType == TYPE_ABOUT) {
                AboutHeaderCell view = new AboutHeaderCell(mContext);
                view.setOnLogoClickListener(v -> org.telegram.messenger.browser.Browser.openUrl(
                        getParentActivity(), "https://github.com/BBedeo/ayugram22test"));
                // У экстеры шапка лежит на фоне окна, а не в карточке-секции.
                view.setTag(RecyclerListView.TAG_NOT_SECTION);
                view.setLayoutParams(new RecyclerView.LayoutParams(
                        RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
                return new RecyclerListView.Holder(view);
            }
            return super.onCreateViewHolder(parent, viewType);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position, boolean partial) {
            switch (holder.getItemViewType()) {
                case TYPE_HEADER: {
                    HeaderCell cell = (HeaderCell) holder.itemView;
                    if (position == designHeaderRow) {
                        cell.setText(getString(R.string.OpenExteraDesignSection));
                    } else if (position == categoriesHeaderRow) {
                        cell.setText(getString(R.string.OpenExteraCategories));
                    } else if (position == linksHeaderRow) {
                        cell.setText(getString(R.string.OpenExteraLinks));
                    }
                    break;
                }
                case TYPE_TEXT: {
                    TextCell cell = (TextCell) holder.itemView;
                    if (position == generalRow) {
                        cell.setTextAndIcon(getString(R.string.OpenExteraGeneral), R.drawable.msg_media, true);
                    } else if (position == appearanceRow) {
                        cell.setTextAndIcon(getString(R.string.OpenExteraAppearance), R.drawable.msg_theme, true);
                    } else if (position == chatsRow) {
                        cell.setTextAndIcon(getString(R.string.OpenExteraChats), R.drawable.msg_discussion, true);
                    } else if (position == messageHistoryRow) {
                        cell.setTextAndIcon(getString(R.string.OEGeneralAyuMoments), R.drawable.msg_log, true);
                    } else if (position == accountRow) {
                        cell.setTextAndIcon(getString(R.string.OpenExteraAccounts), R.drawable.msg_settings_old, true);
                    } else if (position == pluginsRow) {
                        cell.setTextAndIcon(getString(R.string.OpenExteraPlugins), R.drawable.msg_plugins, true);
                    } else if (position == otherRow) {
                        cell.setTextAndIcon(getString(R.string.OpenExteraOther), R.drawable.msg_fave, false);
                    } else if (position == channelRow) {
                        cell.setTextAndValueAndIcon(getString(R.string.ProfileChannel),
                                getString(R.string.ExFullGramUpstream), R.drawable.msg_channel, true);
                    } else if (position == sourceRow) {
                        cell.setTextAndValueAndIcon(getString(R.string.OpenExteraSource),
                                "GitHub", R.drawable.msg_language, false);
                    } else if (position == designerRow) {
                        cell.setTextAndValueAndIcon(getString(R.string.OpenExteraDesigner),
                                "@the8055u", R.drawable.msg_theme, true);
                    } else if (position == designStudioRow) {
                        cell.setTextAndValueAndIcon(getString(R.string.OpenExteraDesignStudio),
                                "@BlueprintDsgn", R.drawable.msg_groups, false);
                    }
                    // ВАЖНО: только после setTextAndIcon* — они сбрасывают imageLeft в 16dp.
                    // Метрики сняты с 12.9.0 (420 dpi): иконка 88px от края экрана, текст 219px,
                    // то есть 21dp и 71dp вместо дефолтных 16dp и 58dp.
                    cell.setImageLeft(21);
                    cell.setOffsetFromImage(71);
                    break;
                }
                case TYPE_INFO_PRIVACY: {
                    TextInfoPrivacyCell cell = (TextInfoPrivacyCell) holder.itemView;
                    if (position == designDividerRow) {
                        cell.setText(null);
                        cell.setBackground(Theme.getThemedDrawable(mContext,
                                R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                    }
                    break;
                }
            }
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return holder.getItemViewType() == TYPE_TEXT;
        }

        @Override
        public int getItemViewType(int position) {
            if (position == aboutRow) {
                return TYPE_ABOUT;
            } else if (position == categoriesHeaderRow || position == linksHeaderRow
                    || position == designHeaderRow) {
                return TYPE_HEADER;
            } else if (position == categoriesDividerRow || position == linksDividerRow) {
                // Промежуток между секциями — тень фиксированной высоты. TextInfoPrivacyCell
                // здесь держал высоту под подпись, которой нет, и оставлял пустое поле.
                return TYPE_SHADOW;
            } else if (position == designDividerRow) {
                return TYPE_INFO_PRIVACY;
            }
            return TYPE_TEXT;
        }
    }
}
