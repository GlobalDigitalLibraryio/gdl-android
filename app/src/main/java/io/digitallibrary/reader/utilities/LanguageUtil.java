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

    public static String getLangPrefKey() {
        return PREF_ID_CURRENT_LANG;
    }

    /**
     * Get the currently selected languageLink. Defaults to English if none is selected.
     *
     * @return The root rootLink to the currently selected languageLink.
     */
    public static String getCurrentLanguage() {
        Prefs p = Gdl.Companion.getSharedPrefs();
        String currentLanguageRootLink = p.getString(PREF_ID_CURRENT_LANG);
        if (currentLanguageRootLink != null && currentLanguageRootLink.length() > 0) {
            return currentLanguageRootLink;
        } else {
            return OpdsParser.INITIAL_LANGUAGE;
        }
    }

    /**
     * Set the current book languageLink. This will trigger a new parse operation on the
     * new languageLink.
     *
     * @param languageRootLink The root rootLink to the wanted languageLink.
     */
    public static void setLanguage(String languageRootLink) {
        Prefs p = Gdl.Companion.getSharedPrefs();
        p.putString(PREF_ID_CURRENT_LANG, languageRootLink);
    }
}