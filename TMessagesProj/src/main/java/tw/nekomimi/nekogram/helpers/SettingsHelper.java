package tw.nekomimi.nekogram.helpers;

import static org.telegram.messenger.LocaleController.getString;
import static org.telegram.ui.ProfileActivity.sendLogs;

import android.app.Activity;
import android.net.Uri;
import android.text.TextUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.LiteModeSettingsActivity;
import org.telegram.ui.PrivacySettingsActivity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import app.exteraless.ai.ui.AiSettingsActivity;
import app.exteraless.icons.IconPacksActivity;
import app.exteraless.pillstack.pills.weather.WeatherSettingsActivity;
import app.exteraless.settings.OpenExteraCloudActivity;
import app.exteraless.settings.OpenExteraGlyphActivity;
import app.exteraless.settings.OpenExteraUpdatesActivity;
import app.exteraless.pillstack.PillStackSettingsActivity;
import app.exteraless.plugins.ui.PluginsActivity;
import app.exteraless.settings.AccountSettingsActivity;
import app.exteraless.settings.OpenExteraAppNavigationActivity;
import app.exteraless.settings.OpenExteraAppearanceActivity;
import app.exteraless.settings.OpenExteraAyuMomentsActivity;
import app.exteraless.settings.OpenExteraChatsActivity;
import app.exteraless.settings.OpenExteraGeneralActivity;
import app.exteraless.settings.OpenExteraOtherActivity;
import app.exteraless.settings.OpenExteraSettingsActivity;
import tw.nekomimi.nekogram.settings.BaseNekoSettingsActivity;
import tw.nekomimi.nekogram.settings.BaseNekoXSettingsActivity;
import tw.nekomimi.nekogram.settings.NekoAboutActivity;
import tw.nekomimi.nekogram.settings.NekoEmojiSettingsActivity;
import tw.nekomimi.nekogram.settings.NekoPasscodeSettingsActivity;
import tw.nekomimi.nekogram.settings.NekoTranslatorSettingsActivity;

public class SettingsHelper {

    private static final String HOST_NAGRAM = "nasettings";
    private static final String HOST_EXTERALESS = "exteraless";

    private static final Map<String, String> SEARCH_TITLE_ALIASES = new HashMap<>();
    private static final Map<String, String> TRANSLATOR_ROWS = new HashMap<>();
    private static final Map<String, String> NAGRAM_ROWS = new HashMap<>();
    private static final Map<String, String> MOVED_ROWS = new HashMap<>();

    static {
        SEARCH_TITLE_ALIASES.put("OEGeneral:botPrivacyAccount", "OEBotPrivacyAccount");
        SEARCH_TITLE_ALIASES.put("OEAyu:ayuGhost", "GhostMode");
        SEARCH_TITLE_ALIASES.put("OEAyu:ayuDisableAll", "OEGeneralAyuMomentsDisableAll");
        SEARCH_TITLE_ALIASES.put("OEAyu:ayuClearDatabase", "ClearMessageDatabase");
        SEARCH_TITLE_ALIASES.put("OEAppearance:appNavigation", "OEAppearanceNavigation");
        SEARCH_TITLE_ALIASES.put("OEAppearance:designPreset", "OEAppearancePreset");
        SEARCH_TITLE_ALIASES.put("OEAppearance:maxIosGlassPreset", "OEAppearancePresetMaxIosGlass");
        SEARCH_TITLE_ALIASES.put("OEChats:hideStories", "OEAppearanceStories");
        SEARCH_TITLE_ALIASES.put("OEChats:hideActionBarStatus", "OEAppearanceHideActionBarStatus");
        SEARCH_TITLE_ALIASES.put("OEChats:hideFloatingButton", "OEAppearanceHideFloatingButton");
        SEARCH_TITLE_ALIASES.put("OEChats:hideSearchBar", "OEAppearanceHideSearchBar");
        SEARCH_TITLE_ALIASES.put("OEChats:hideAllChats", "HideAllTab");
        SEARCH_TITLE_ALIASES.put("OEChats:hideShareButton", "HideShareButtonInChannel");
        SEARCH_TITLE_ALIASES.put("OEChats:hideAi", "OEAppearanceHideAi");
        SEARCH_TITLE_ALIASES.put("OEChats:hideAiEditor", "OEAppearanceHideAiEditor");
        SEARCH_TITLE_ALIASES.put("OEChats:hideAiSummary", "OEAppearanceHideAiSummary");
        SEARCH_TITLE_ALIASES.put("OEChats:hideAiIv", "OEAppearanceHideAiIv");
        SEARCH_TITLE_ALIASES.put("OEChats:hideSettingsSections", "OEAppearanceHideSettingsSections");
        SEARCH_TITLE_ALIASES.put("OEChats:disableGreeting", "OEChatsDisableGreetingSticker");
        SEARCH_TITLE_ALIASES.put("OEChats:hideKeyboardOnScroll", "HideKeyboardOnChatScroll");
        SEARCH_TITLE_ALIASES.put("OEChats:dismissMenuOnDragOut", "OEChatsDismissMenuOnDragOut");
        SEARCH_TITLE_ALIASES.put("OEChats:liveTypingPreview", "OEChatsLiveTypingPreview");
        SEARCH_TITLE_ALIASES.put("OEChats:transcribeProvider", "PremiumPreviewVoiceToText");
        SEARCH_TITLE_ALIASES.put("OEGeneral:translator", "TranslatorSettings");
        SEARCH_TITLE_ALIASES.put("OEGeneral:translateInSheet", "OEChatsTranslateInSheet");
        SEARCH_TITLE_ALIASES.put("OEGeneral:glyph", "OEGlyphTitle");
        SEARCH_TITLE_ALIASES.put("OpenExtera:channel", "ProfileChannel");
        // Строка списка называется «История сообщений» (R.string.OEGeneralAyuMoments), а ключ
        // messageHistory — не имя ресурса, поэтому без алиаса поиск показывал «Message History».
        SEARCH_TITLE_ALIASES.put("OpenExtera:messageHistory", "OEGeneralAyuMoments");
        SEARCH_TITLE_ALIASES.put("OEGeneral:downloadSpeed", "OEGeneralSpeedHeader");
        SEARCH_TITLE_ALIASES.put("OEAppearance:md3Styles", "OEAppearanceMaterialDesign3");
        SEARCH_TITLE_ALIASES.put("OEAppearance:md3Loading", "OEAppearanceNewLoadingStyle");
        SEARCH_TITLE_ALIASES.put("OEAppearance:md3Slider", "OEAppearanceSliderStyle");
        SEARCH_TITLE_ALIASES.put("OEAppearance:md3Switch", "OEAppearanceSwitchStyle");
        SEARCH_TITLE_ALIASES.put("OEAppearance:md3NavBar", "OEAppearanceNewNavigationBarStyle");
        SEARCH_TITLE_ALIASES.put("OEAppearance:md3ListItems", "OEAppearanceM3ListItems");
        SEARCH_TITLE_ALIASES.put("OEAppearance:iosStyles", "OEAppearanceIosDesign");
        SEARCH_TITLE_ALIASES.put("OEAppearance:iosLoading", "OEAppearanceNewLoadingStyle");
        SEARCH_TITLE_ALIASES.put("OEAppearance:iosSlider", "OEAppearanceSliderStyle");
        SEARCH_TITLE_ALIASES.put("OEAppearance:iosSwitch", "OEAppearanceSwitchStyle");
        SEARCH_TITLE_ALIASES.put("OEAppearance:iosChatHeader", "OEAppearanceIosChatHeader");
        SEARCH_TITLE_ALIASES.put("OEAppearance:iosNavBar", "OEAppearanceIosNavigationBarStyle");
        SEARCH_TITLE_ALIASES.put("OEAppearance:iosListItems", "OEAppearanceM3ListItems");
        SEARCH_TITLE_ALIASES.put("OEAppearance:iosPlayer", "OEAppearanceMd3Player");
        SEARCH_TITLE_ALIASES.put("OEAppearance:iosMiniPlayer", "OEAppearanceIosMiniPlayer");
        SEARCH_TITLE_ALIASES.put("OEChats:doubleTapReaction", "DoubleTapSetting");
        SEARCH_TITLE_ALIASES.put("OEChats:quickTransition", "OEChatsQuickTransitions");
        SEARCH_TITLE_ALIASES.put("OEChats:inlineMathResult", "OEChatsInlineMath");
        SEARCH_TITLE_ALIASES.put("OEChats:searchHashtagChat", "SearchHashtagDefaultPageChat");
        SEARCH_TITLE_ALIASES.put("OEChats:searchHashtagChannel", "SearchHashtagDefaultPageChannel");
        SEARCH_TITLE_ALIASES.put("OEChats:replaceEdited", "OEChatsReplaceEditedWithIcon");
        SEARCH_TITLE_ALIASES.put("OEChats:videoMessagesCamera", "CameraInVideoMessages");
        SEARCH_TITLE_ALIASES.put("OEChats:voskModels", "VoskModelsShort");
        // Строки, у которых имя ресурса не совпадает со схемой «<prefix><Key>».
        // Правило проекта: каждая настройка обязана находиться поиском,
        // полноту проверяет scripts/check_settings_search.py.
        SEARCH_TITLE_ALIASES.put("OEAppearance:typingBubbleAtBottom", "OEAppearanceTypingBubbleAtBottom");
        SEARCH_TITLE_ALIASES.put("OEAppearance:startupFolder", "OEStartupFolder");
        SEARCH_TITLE_ALIASES.put("OEAppearance:playerStyle", "OEPlayerStyleTitle");
        SEARCH_TITLE_ALIASES.put("OEChats:inlineMathSettings", "OEChatsInlineMath");
        SEARCH_TITLE_ALIASES.put("OEChats:forwardMultiSelect", "ExFullGramForwardMultiSelect");
        SEARCH_TITLE_ALIASES.put("OECloud:cloudStatus", "OECloudStatus");
        SEARCH_TITLE_ALIASES.put("OECloud:cloudActions", "OECloudActions");
        SEARCH_TITLE_ALIASES.put("OECloud:cloudUpload", "OECloudUpload");
        SEARCH_TITLE_ALIASES.put("OECloud:cloudRestore", "OECloudRestore");
        SEARCH_TITLE_ALIASES.put("OECloud:cloudClear", "OECloudClear");
        SEARCH_TITLE_ALIASES.put("OECloud:cloudOptions", "OECloudOptions");
        SEARCH_TITLE_ALIASES.put("OECloud:cloudAutoSync", "OECloudAutoSync");
        SEARCH_TITLE_ALIASES.put("OEGlyph:glyphPreview", "OEGlyphPreview");
        SEARCH_TITLE_ALIASES.put("EFGAccounts:current", "EFGAccountsCurrent");
        SEARCH_TITLE_ALIASES.put("EFGAccounts:separateSettings", "EFGAccountsSeparateSettings");
        SEARCH_TITLE_ALIASES.put("EFGAccounts:compare", "EFGAccountsCompare");
        MOVED_ROWS.put("chats:translateInSheet", "general");
        MOVED_ROWS.put("other:unlimitedPinnedDialogs", "general");
        MOVED_ROWS.put("other:glyph", "general");
        moveRows("chats", "menus", "bottomButton", "adminShortcuts", "chatMenu", "messageMenu",
                "groupedMessageMenu", "textStyle", "TextStyle", "mediaViewerMenu", "actionBarButtons",
                "defaultDeleteMenu", "DefaultDeleteMenu");
        moveRows("chats", "media", "cameraType", "extendedSettings", "videoMessagesCamera",
                "rememberLastUsedCamera", "zoomSlider", "staticZoom", "alwaysSendInHD", "hdrPhotos",
                "disableInstantCamera", "DisableInstantCamera", "doubleTapSeekDuration", "preferOriginalQuality",
                "videoPlayerDecoder", "VideoPlayerDecoder", "swipeToPip", "unmuteWithVolumeButtons",
                "showSmallGIF", "ShowSmallGIF", "dontAutoPlayNextVoice", "DontAutoPlayNextVoice",
                "disableProximityEvents", "DisableProximityEvents", "pauseOnMinimize", "transcribeProvider",
                "TranscribeProviderShort", "cloudflareCredentials", "CloudflareCredentials", "llmProviderGeminiKey",
                "LlmProviderGeminiKey", "transcribeProviderOpenAI", "TranscribeProviderOpenAI", "voskModels",
                "VoskModelsShort");
        moveRows("chats", "hiding", "disableTrending", "DisableTrending", "hideGroupSticker", "hideReactions",
                "disableGreeting", "hideSendAsPeer", "hideShareButton", "hideGiftButton", "hideSearchButton",
                "hideCameraTile", "premiumElements", "PremiumElements");
        moveRows("other", "media", "noiseSuppressAndVoiceEnhance", "NoiseSuppressAndVoiceEnhance",
                "enhancedVideoBitrate", "EnhancedVideoBitrate");
        moveRows("appearance", "hiding", "hideActionBarStatus", "hideStories", "HideStoriesFromHeader",
                "DisableStories", "hideFloatingButton", "hideSearchBar", "hideAllChats", "hideAi", "hideAiEditor",
                "hideAiSummary", "hideAiIv", "hideSettingsSections", "hidePremiumSection", "HidePremiumSection",
                "hideHelpSection", "HideHelpSection");
        TRANSLATOR_ROWS.put("translateButton", "showTranslate");
        TRANSLATOR_ROWS.put("translateChatButton", "TelegramUIAutoTranslate");
        TRANSLATOR_ROWS.put("translationProvider", "translationProvider");
        TRANSLATOR_ROWS.put("translateToLang", "TranslateTo");
        TRANSLATOR_ROWS.put("doNotTranslate", "DoNotTranslate");
        NAGRAM_ROWS.put("HidePremiumSection", "appearance");
        NAGRAM_ROWS.put("HideHelpSection", "appearance");
        NAGRAM_ROWS.put("HideStoriesFromHeader", "appearance");
        NAGRAM_ROWS.put("DisableStories", "appearance");
        NAGRAM_ROWS.put("MainTabsHideTitles", "navigation");
        NAGRAM_ROWS.put("MainTabsHideContacts", "navigation");
        NAGRAM_ROWS.put("MainTabsHideCallsSettings", "navigation");
        NAGRAM_ROWS.put("MainTabsHideProfile", "navigation");
        NAGRAM_ROWS.put("AlwaysShowDownloadIcon", "navigation");
        NAGRAM_ROWS.put("DisableSystemAccount", "privacy");
        NAGRAM_ROWS.put("PerformanceClass", "powersaving");
        NAGRAM_ROWS.put("VideoPlayerDecoder", "chats");
    }

    private static void moveRows(String from, String to, String... rows) {
        for (String row : rows) {
            MOVED_ROWS.put(from + ":" + row, to);
        }
    }

    private static final Set<String> EXTERALESS_SCREENS = new HashSet<>(Arrays.asList(
            "settings", "general", "appearance", "chats", "plugins", "pillstack", "other",
            "ayumoments", "navigation", "menus", "media", "hiding"));

    public static boolean isDeepLink(String path) {
        if (path == null) {
            return false;
        }
        if (path.startsWith(HOST_NAGRAM + "/")) {
            return true;
        }
        if (!path.startsWith(HOST_EXTERALESS + "/")) {
            return false;
        }
        return EXTERALESS_SCREENS.contains(path.substring(HOST_EXTERALESS.length() + 1));
    }

    public static String linkPathFor(String key) {
        if (key == null) {
            return null;
        }
        switch (key) {
            case "exteraless":
                return HOST_EXTERALESS + "/settings";
            case "exteraless_general":
                return HOST_EXTERALESS + "/general";
            case "exteraless_appearance":
                return HOST_EXTERALESS + "/appearance";
            case "exteraless_chats":
                return HOST_EXTERALESS + "/chats";
            case "exteraless_menus":
                return HOST_EXTERALESS + "/menus";
            case "exteraless_media":
                return HOST_EXTERALESS + "/media";
            case "exteraless_hiding":
                return HOST_EXTERALESS + "/hiding";
            case "exteraless_other":
                return HOST_EXTERALESS + "/other";
            case "exteraless_ayumoments":
                return HOST_EXTERALESS + "/ayumoments";
            case "pillstack":
                return HOST_EXTERALESS + "/pillstack";
            case "exteraless_navigation":
                return HOST_EXTERALESS + "/navigation";
            default:
                return HOST_NAGRAM + "/" + key;
        }
    }

    public static void processDeepLink(Activity activity, Uri uri, Callback callback, Runnable unknown) {
        if (uri == null) {
            unknown.run();
            return;
        }
        var segments = uri.getPathSegments();
        if (segments.isEmpty() || segments.size() > 2) {
            unknown.run();
            return;
        }
        final boolean exteraless = HOST_EXTERALESS.equals(segments.get(0));
        if (!exteraless && !HOST_NAGRAM.equals(segments.get(0))) {
            unknown.run();
            return;
        }
        if (exteraless && segments.size() < 2) {
            unknown.run();
            return;
        }
        var row = uri.getQueryParameter("r");
        if (TextUtils.isEmpty(row)) {
            row = uri.getQueryParameter("row");
        }
        BaseFragment fragment;
        BaseNekoSettingsActivity neko_fragment = null;
        BaseNekoXSettingsActivity nekox_fragment = null;
        OpenExteraAppNavigationActivity navigation_fragment = null;
        String screen = exteraless ? segments.get(1) : nagramScreen(segments.size() == 1 ? null : segments.get(1), row);
        if (screen == null && segments.size() == 2 && segments.get(1).startsWith("exteraless_")) {
            String base = segments.get(1).substring("exteraless_".length());
            if (row != null && MOVED_ROWS.containsKey(base + ":" + row)) {
                screen = base;
            }
        }
        if (screen != null && row != null) {
            String moved = MOVED_ROWS.get(screen + ":" + row);
            if (moved != null) {
                screen = moved;
            }
        }
        if (screen != null) {
            switch (screen) {
                case "settings":
                    fragment = neko_fragment = new OpenExteraSettingsActivity();
                    break;
                case "accounts":
                    fragment = neko_fragment = new AccountSettingsActivity();
                    break;
                case "general":
                    if (TRANSLATOR_ROWS.containsKey(row)) {
                        fragment = nekox_fragment = new NekoTranslatorSettingsActivity();
                        row = TRANSLATOR_ROWS.get(row);
                    } else {
                        fragment = neko_fragment = new OpenExteraGeneralActivity();
                    }
                    break;
                case "appearance":
                    fragment = neko_fragment = "useSystemEmoji".equals(row)
                            ? new NekoEmojiSettingsActivity() : new OpenExteraAppearanceActivity();
                    break;
                case "chats":
                    fragment = neko_fragment = new OpenExteraChatsActivity();
                    break;
                case "menus":
                    fragment = neko_fragment = new OpenExteraChatsActivity(OpenExteraChatsActivity.SCREEN_MENUS);
                    break;
                case "media":
                    fragment = neko_fragment = new OpenExteraChatsActivity(OpenExteraChatsActivity.SCREEN_MEDIA);
                    break;
                case "hiding":
                    fragment = neko_fragment = new OpenExteraChatsActivity(OpenExteraChatsActivity.SCREEN_HIDING);
                    break;
                case "other":
                    fragment = neko_fragment = new OpenExteraOtherActivity();
                    break;
                case "ayumoments":
                    fragment = neko_fragment = new OpenExteraAyuMomentsActivity();
                    break;
                case "pillstack":
                    fragment = neko_fragment = new PillStackSettingsActivity();
                    break;
                case "plugins":
                    fragment = new PluginsActivity();
                    break;
                case "navigation":
                    fragment = navigation_fragment = new OpenExteraAppNavigationActivity();
                    break;
                case "privacy":
                    fragment = new PrivacySettingsActivity();
                    break;
                case "powersaving":
                    fragment = new LiteModeSettingsActivity();
                    break;
                default:
                    unknown.run();
                    return;
            }
        } else if (PasscodeHelper.getSettingsKey().equals(segments.get(1))) {
            fragment = neko_fragment = new NekoPasscodeSettingsActivity();
        } else {
            switch (segments.get(1)) {
                case "about":
                    fragment = new NekoAboutActivity();
                    break;
                case "emoji":
                    fragment = neko_fragment = new NekoEmojiSettingsActivity();
                    break;
                case "translator":
                case "translate":
                case "t":
                    fragment = nekox_fragment = new NekoTranslatorSettingsActivity();
                    break;
                case "exteraless":
                    fragment = neko_fragment = new OpenExteraSettingsActivity();
                    break;
                case "exteraless_accounts":
                    fragment = neko_fragment = new AccountSettingsActivity();
                    break;
                case "exteraless_general":
                    if (TRANSLATOR_ROWS.containsKey(row)) {
                        fragment = nekox_fragment = new NekoTranslatorSettingsActivity();
                        row = TRANSLATOR_ROWS.get(row);
                    } else {
                        fragment = neko_fragment = new OpenExteraGeneralActivity();
                    }
                    break;
                case "exteraless_appearance":
                    fragment = neko_fragment = "useSystemEmoji".equals(row)
                            ? new NekoEmojiSettingsActivity() : new OpenExteraAppearanceActivity();
                    break;
                case "exteraless_chats":
                    fragment = neko_fragment = new OpenExteraChatsActivity();
                    break;
                case "exteraless_other":
                    fragment = neko_fragment = new OpenExteraOtherActivity();
                    break;
                case "exteraless_ayumoments":
                    fragment = neko_fragment = new OpenExteraAyuMomentsActivity();
                    break;
                case "pillstack":
                    fragment = neko_fragment = new PillStackSettingsActivity();
                    break;
                case "pillstackweather":
                    fragment = neko_fragment = new WeatherSettingsActivity();
                    break;
                case "iconpacks":
                    fragment = neko_fragment = new IconPacksActivity();
                    break;
                case "exteraless_ai":
                    fragment = neko_fragment = new AiSettingsActivity();
                    break;
                case "exteraless_cloud":
                    fragment = neko_fragment = new OpenExteraCloudActivity();
                    break;
                case "exteraless_glyph":
                    fragment = neko_fragment = new OpenExteraGlyphActivity();
                    break;
                case "exteraless_updates":
                    fragment = neko_fragment = new OpenExteraUpdatesActivity();
                    break;
                case "send_logs":
                    sendLogs(activity, false);
                    return;
                default:
                    unknown.run();
                    return;
            }
        }
        callback.presentFragment(fragment);
        var value = uri.getQueryParameter("v");
        if (TextUtils.isEmpty(value)) {
            value = uri.getQueryParameter("value");
        }
        if (!TextUtils.isEmpty(row)) {
            var rowFinal = row;
            if (neko_fragment != null) {
                BaseNekoSettingsActivity finalNeko_fragment = neko_fragment;
                AndroidUtilities.runOnUIThread(() -> finalNeko_fragment.scrollToRow(rowFinal, unknown));
            } else if (navigation_fragment != null) {
                OpenExteraAppNavigationActivity finalNavigation_fragment = navigation_fragment;
                AndroidUtilities.runOnUIThread(() -> finalNavigation_fragment.scrollToRow(rowFinal, unknown));
            } else if (nekox_fragment != null) {
                BaseNekoXSettingsActivity finalNekoX_fragment = nekox_fragment;
                if (!TextUtils.isEmpty(value)) {
                    String finalValue = value;
                    AndroidUtilities.runOnUIThread(() -> finalNekoX_fragment.importToRow(rowFinal, finalValue, unknown));
                } else {
                    AndroidUtilities.runOnUIThread(() -> finalNekoX_fragment.scrollToRow(rowFinal, unknown));
                }
            }
        }
    }

    private static String nagramScreen(String segment, String row) {
        if (segment == null) {
            return "settings";
        }
        String fallback;
        switch (segment) {
            case "general":
            case "g":
                fallback = "general";
                break;
            case "chat":
            case "chats":
            case "c":
                fallback = "chats";
                break;
            case "experimental":
            case "e":
                fallback = "other";
                break;
            default:
                return null;
        }
        String moved = row == null ? null : NAGRAM_ROWS.get(row);
        return moved != null ? moved : fallback;
    }

    /**
     * Имя строкового ресурса с заголовком строки настроек. Перебор идёт по всем известным схемам
     * имён, чтобы ни одна настройка не выпадала из поиска: alias из {@link #SEARCH_TITLE_ALIASES},
     * «<prefix><Key>», «<prefix><Key>Title», «<Key>», «<Key>Title» и сам ключ.
     * Возвращает null, если ни одна схема не сработала.
     */
    private static String rowTitleResource(BaseNekoSettingsActivity fragment, String key) {
        if (key == null || key.isEmpty()) {
            return null;
        }
        String capitalized = Character.toUpperCase(key.charAt(0)) + key.substring(1);
        String prefix = fragment.getSearchPrefix();
        ArrayList<String> candidates = new ArrayList<>(8);
        if (prefix != null) {
            String alias = SEARCH_TITLE_ALIASES.get(prefix + ":" + key);
            if (alias != null) {
                candidates.add(alias);
            }
            candidates.add(prefix + capitalized);
            candidates.add(prefix + capitalized + "Title");
        }
        String alias = SEARCH_TITLE_ALIASES.get(key);
        if (alias != null) {
            candidates.add(alias);
        }
        candidates.add(capitalized);
        candidates.add(capitalized + "Title");
        candidates.add(key);
        for (int i = 0; i < candidates.size(); i++) {
            String name = candidates.get(i);
            if (LocaleController.getStringResId(name) != 0) {
                return name;
            }
        }
        return null;
    }

    private static String rowTitle(BaseNekoSettingsActivity fragment, String key) {
        String direct = getString(key);
        if (direct != null && !direct.isEmpty() && !direct.equals(key)) {
            return direct;
        }
        if (key == null || key.isEmpty()) {
            return null;
        }
        String name = rowTitleResource(fragment, key);
        String title = name == null ? null : resolved(name);
        if (title != null) {
            return title;
        }
        // Последний рубеж: ключ всё равно попадает в поиск, пусть и в виде «Sticker Size».
        return humanize(key);
    }

    private static String rowSubtitle(BaseNekoSettingsActivity fragment, String key) {
        if (key == null || key.isEmpty()) {
            return null;
        }
        String name = rowTitleResource(fragment, key);
        if (name != null) {
            String s = firstResolved(name + "Info", name + "Sub", name + "Desc");
            if (s != null) {
                return s;
            }
        }
        String capitalized = Character.toUpperCase(key.charAt(0)) + key.substring(1);
        String prefix = fragment.getSearchPrefix();
        if (prefix != null) {
            String s = firstResolved(prefix + capitalized + "Info", prefix + capitalized + "Sub",
                    prefix + capitalized + "Desc");
            if (s != null) return s;
        }
        return firstResolved(key + "Info", key + "Sub", key + "Desc",
                capitalized + "Info", capitalized + "Sub", capitalized + "Desc");
    }

    /**
     * Второй сегмент подписи «раздел > экран». У корневого экрана раздела заголовок совпадает
     * с названием раздела (ExFullGram → ExFullGram), и подпись выходила эхом «ExFullGram > ExFullGram»:
     * такие пункты называют раздел один раз, как в поиске Telegram.
     */
    private static String parentPath(BaseNekoSettingsActivity fragment, String section, String fragmentTitle) {
        if (fragmentTitle == null || fragment instanceof OpenExteraSettingsActivity
                || TextUtils.equals(section, fragmentTitle)) {
            return null;
        }
        return fragmentTitle;
    }

    private static String humanize(String key) {
        StringBuilder sb = new StringBuilder(key.length() + 6);
        for (int i = 0; i < key.length(); i++) {
            char c = key.charAt(i);
            if (i == 0) {
                sb.append(Character.toUpperCase(c));
            } else if (Character.isUpperCase(c) && !Character.isUpperCase(key.charAt(i - 1))) {
                sb.append(' ').append(c);
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static String firstResolved(String... names) {
        for (String name : names) {
            String v = resolved(name);
            if (v != null) {
                return v;
            }
        }
        return null;
    }

    private static String resolved(String name) {
        if (LocaleController.getStringResId(name) == 0) {
            return null;
        }
        String value = getString(name);
        return value == null || value.isEmpty() ? null : value;
    }

    public interface Callback {
        void presentFragment(BaseFragment fragment);
    }

    public static ArrayList<SettingsSearchResult> onCreateSearchArray(Callback callback) {
        ArrayList<SettingsSearchResult> items = new ArrayList<>();
        ArrayList<BaseNekoXSettingsActivity> fragments = new ArrayList<>();
        fragments.add(new NekoTranslatorSettingsActivity());

        ArrayList<BaseNekoSettingsActivity> exteralessFragments = new ArrayList<>();
        exteralessFragments.add(new OpenExteraSettingsActivity());
        exteralessFragments.add(new AccountSettingsActivity());
        exteralessFragments.add(new OpenExteraGeneralActivity());
        exteralessFragments.add(new OpenExteraAppearanceActivity());
        exteralessFragments.add(new OpenExteraChatsActivity());
        exteralessFragments.add(new OpenExteraChatsActivity(OpenExteraChatsActivity.SCREEN_MENUS));
        exteralessFragments.add(new OpenExteraChatsActivity(OpenExteraChatsActivity.SCREEN_MEDIA));
        exteralessFragments.add(new OpenExteraChatsActivity(OpenExteraChatsActivity.SCREEN_HIDING));
        exteralessFragments.add(new OpenExteraOtherActivity());
        exteralessFragments.add(new OpenExteraAyuMomentsActivity());
        exteralessFragments.add(new PillStackSettingsActivity());
        exteralessFragments.add(new OpenExteraUpdatesActivity());
        exteralessFragments.add(new AiSettingsActivity());

        String e_title = getString(R.string.OpenExtera);
        for (BaseNekoSettingsActivity fragment : exteralessFragments) {
            try {
                fragment.buildRowsForSearch();
            } catch (Exception e) {
                continue;
            }
            int uid = fragment.getSearchGuid();
            int drawable = fragment.getSearchIcon();
            String f_title = fragment.getSearchTitle();
            for (Map.Entry<Integer, String> entry : fragment.getSearchRows().entrySet()) {
                String key = entry.getValue();
                if (key == null || key.endsWith("Header") || key.equals(String.valueOf(entry.getKey()))) {
                    continue;
                }
                String title = rowTitle(fragment, key);
                if (title == null || title.isEmpty()) {
                    continue;
                }
                Runnable open = () -> {
                    callback.presentFragment(fragment);
                    AndroidUtilities.runOnUIThread(() -> fragment.scrollToRow(key, null));
                };
                items.add(new SettingsSearchResult(
                        uid + entry.getKey(), title, e_title, parentPath(fragment, e_title, f_title), drawable, open)
                        .setSubtitle(rowSubtitle(fragment, key)));
            }
        }

        items.add(new SettingsSearchResult(29000, getString(R.string.OEAppearanceUseSystemEmoji), e_title,
                getString(R.string.EmojiSets), R.drawable.msg_emoji_smiles, () -> {
                    NekoEmojiSettingsActivity emoji = new NekoEmojiSettingsActivity();
                    callback.presentFragment(emoji);
                    AndroidUtilities.runOnUIThread(() -> emoji.scrollToRow("useSystemEmoji", null));
                }));

        String n_title = getString(R.string.Language);
        for (BaseNekoXSettingsActivity fragment: fragments) {
            int uid = fragment.getBaseGuid();
            int drawable = fragment.getDrawable();
            String f_title = fragment.getTitle();
            for (Map.Entry<Integer, String> entry : fragment.getRowMapReverse().entrySet()) {
                Integer i = entry.getKey();
                String key = entry.getValue();
                if (key.equals(String.valueOf(i))) {
                    continue;
                }
                int guid = uid + i;
                String title = getString(key);
                if (title == null || title.isEmpty()) {
                    continue;
                }
                Runnable open = () -> {
                    callback.presentFragment(fragment);
                    AndroidUtilities.runOnUIThread(() -> fragment.scrollToRow(key, null));
                };
                SettingsSearchResult result = new SettingsSearchResult(
                        guid, title, n_title, f_title, drawable, open
                ).setSubtitle(firstResolved(key + "Info", key + "Desc"));
                items.add(result);
            }
        }
        return items;
    }
}
