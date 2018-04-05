package io.digitallibrary.reader.utilities;

import org.jetbrains.annotations.Nullable;

import io.digitallibrary.reader.Gdl;
import io.digitallibrary.reader.Prefs;

/**
 * Utility functions to keep track of currently selected language and category.
 */
public class SelectionsUtil {
    private static final String TAG = "SelectionsUtil";

    private static final String PREF_ID_CURRENT_LANG = "io.digitallibrary.reader.LANG";
    private static final String PREF_ID_CURRENT_LANG_DISP_TXT = "io.digitallibrary.reader.LANG_DISP_TXT";
    private static final String PREF_ID_CURRENT_CATEGORY = "io.digitallibrary.reader.CATEGORY";
    private static final String PREF_ID_CURRENT_CATEGORY_DISP_TXT = "io.digitallibrary.reader.CATEGORY_DISP_TXT";

    /**
     * Get the Pref key you can listen to for language changes
     *
     * @return Language pref key
     */
    public static String getLangPrefKey() {
        return PREF_ID_CURRENT_LANG;
    }

    /**
     * Get the Pref key you can listen to for category changes
     *
     * @return Category pref key
     */
    public static String getCategoryPrefKey() {
        return PREF_ID_CURRENT_CATEGORY;
    }

    private static String getPref(String prefKey) {
        Prefs p = Gdl.Companion.getSharedPrefs();
        String value = p.getString(prefKey);
        if (value != null && value.length() > 0) {
            return value;
        } else {
            return null;
        }
    }

    /**
     * Get the currently selected language link. Defaults to English if none is selected.
     *
     * @return The root link to the currently selected language, or null if not set
     */
    @Nullable
    public static String getCurrentLanguageLink() {
        return getPref(PREF_ID_CURRENT_LANG);
    }

    /**
     * Get the currently selected language display text.
     *
     * @return The display text for the currently selected language, or null if not set
     */
    public static String getCurrentLanguageDisplayText() {
        return getPref(PREF_ID_CURRENT_LANG_DISP_TXT);
    }

    /**
     * Set the current book language. This will trigger a new parse operation on the
     * new language.
     *
     * @param languageLink The link to the wanted language
     * @param languageDisplayText The display text for the language
     */
    public static void setLanguage(String languageLink, String languageDisplayText) {
        Prefs p = Gdl.Companion.getSharedPrefs();
        p.putString(PREF_ID_CURRENT_LANG_DISP_TXT, languageDisplayText);
        p.putString(PREF_ID_CURRENT_LANG, languageLink);
    }

    /**
     * Get the currently selected category link.
     *
     * @return The link to the currently selected category, or null if not set
     */
    public static String getCurrentCategoryLink() {
        return getPref(PREF_ID_CURRENT_CATEGORY);
    }

    /**
     * Get the currently selected category display text.
     *
     * @return The display text for the currently selected category, or null if not set
     */
    public static String getCurrentCategoryDisplayText() {
        return getPref(PREF_ID_CURRENT_CATEGORY_DISP_TXT);
    }

    /**
     * Set the current selected category.
     *
     * @param categoryLink The link to the category
     * @param categoryDisplayText The display text for the category
     */
    public static void setCategory(String categoryLink, String categoryDisplayText) {
        Prefs p = Gdl.Companion.getSharedPrefs();
        p.putString(PREF_ID_CURRENT_CATEGORY_DISP_TXT, categoryDisplayText);
        p.putString(PREF_ID_CURRENT_CATEGORY, categoryLink);
    }
}