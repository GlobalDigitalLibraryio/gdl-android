package io.digitallibrary.reader;


import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.MutableLiveData;
import android.arch.lifecycle.ViewModel;
import android.content.SharedPreferences;

import java.util.List;

import io.digitallibrary.reader.catalog.Language;
import io.digitallibrary.reader.utilities.SelectionsUtil;

public class LanguagesViewModel extends ViewModel {

    private MutableLiveData<String> currentLanguageText;
    private String currentLanguageLink;
    private LiveData<List<Language>> languages;

    private SharedPreferences.OnSharedPreferenceChangeListener langListener;

    public LiveData<List<Language>> getLanguages() {
        if (languages == null) {
            languages = Gdl.Companion.getDatabase().languageDao().getLiveLanguages();
        }
        return languages;
    }

    public LiveData<String> getCurrentLanguageText() {
        return currentLanguageText;
    }

    public String getCurrentLanguageLink() {
        return currentLanguageLink;
    }

    private void updateLanguageText() {
        currentLanguageText.postValue(SelectionsUtil.getCurrentLanguageDisplayText());
        currentLanguageLink = SelectionsUtil.getCurrentLanguageLink();
    }

    public LanguagesViewModel() {
        langListener = new SharedPreferences.OnSharedPreferenceChangeListener() {
            @Override
            public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
                if (key.equals(SelectionsUtil.getLangPrefKey())) {
                    updateLanguageText();
                }
            }
        };
        Gdl.Companion.getSharedPrefs().registerListener(langListener);
        currentLanguageText = new MutableLiveData<>();
        updateLanguageText();
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        Gdl.Companion.getSharedPrefs().unregisterListener(langListener);
    }
}