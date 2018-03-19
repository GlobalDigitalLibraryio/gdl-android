package io.digitallibrary.reader.utilities;

import io.digitallibrary.reader.Gdl;
import io.digitallibrary.reader.Prefs;
import io.digitallibrary.reader.catalog.OpdsParser;

/**
 * Language selection utility functions.
 */
public class LanguageUtil {
    private static final String TAG = "LanguageUtil";

    private static final String PREF_ID_CURRENT_LANG = "io.digitallibrary.reader.LANG";
    private static final String PREF_ID_CURRENT_LANG_DISP_TXT = "io.digitallibrary.reader.LANG_DISP_TXT";

    /**
     * Get the Pref key you can listen to for language changes
     *
     * @return Language pref key
     */
    public static String getLangPrefKey() {
        return PREF_ID_CURRENT_LANG;
    }

    /**
     * Get the currently selected language link. Defaults to English if none is selected.
     *
     * @return The root link to the currently selected language.
     */
    public static String getCurrentLanguageLink() {
        Prefs p = Gdl.Companion.getSharedPrefs();
        String currentLanguageRootLink = p.getString(PREF_ID_CURRENT_LANG);
        if (currentLanguageRootLink != null && currentLanguageRootLink.length() > 0) {
            return currentLanguageRootLink;
        } else {
            return null;
        }
    }

    /**
     * Get the currently selected language display text. Defaults to English if none is selected.
     *
     * @return The display text for the currently selected language.
     */
    public static String getCurrentLanguageDisplayText() {
        Prefs p = Gdl.Companion.getSharedPrefs();
        String currentLanguageRootLink = p.getString(PREF_ID_CURRENT_LANG_DISP_TXT);
        if (currentLanguageRootLink != null && currentLanguageRootLink.length() > 0) {
            return currentLanguageRootLink;
        } else {
            return null;
        }
    }

    /**
     * Set the current book language. This will trigger a new parse operation on the
     * new language.
     *
     * @param languageRootLink The root link to the wanted language.
     */
    public static void setLanguage(String languageRootLink, String languageDisplayText) {
        Prefs p = Gdl.Companion.getSharedPrefs();
        p.putString(PREF_ID_CURRENT_LANG_DISP_TXT, languageDisplayText);
        p.putString(PREF_ID_CURRENT_LANG, languageRootLink);
    }
}