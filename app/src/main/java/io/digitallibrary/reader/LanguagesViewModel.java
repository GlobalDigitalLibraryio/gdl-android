package io.digitallibrary.reader;


import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.MutableLiveData;
import android.arch.lifecycle.ViewModel;
import android.content.SharedPreferences;
import android.os.AsyncTask;

import java.lang.ref.WeakReference;
import java.util.List;

import io.digitallibrary.reader.catalog.Language;
import io.digitallibrary.reader.catalog.OpdsParser;
import io.digitallibrary.reader.utilities.LanguageUtil;

public class LanguagesViewModel extends ViewModel {

    private MutableLiveData<String> currentLanguage;
    private LiveData<List<Language>> languages;

    private SharedPreferences.OnSharedPreferenceChangeListener langListener;

    public LiveData<List<Language>> getLanguages() {
        if (languages == null) {
            languages = Gdl.Companion.getDatabase().languageDao().getLiveLanguages();
        }
        return languages;
    }

    public LiveData<String> getCurrentLanguageText() {
        return currentLanguage;
    }

    // Can't access database in main thread
    private static class UpdateLanguageText extends AsyncTask<Void, Void, Void> {
        private WeakReference<LanguagesViewModel> viewModel;

        UpdateLanguageText(LanguagesViewModel viewModel) {
            this.viewModel = new WeakReference<>(viewModel);
        }

        @Override
        protected Void doInBackground(Void... voids) {
            String langKey = LanguageUtil.getCurrentLanguage();
            Language language = Gdl.Companion.getDatabase().languageDao().getLanguage(langKey);
            LanguagesViewModel vm = viewModel.get();
            if (vm != null) {
                if (language != null) {
                    vm.currentLanguage.postValue(language.getLanguageName());
                } else {
                    vm.currentLanguage.postValue(OpdsParser.INITIAL_LANGUAGE_TEXT);
                }
            }
            return null;
        }
    }

    private void updateLanguageText() {
        UpdateLanguageText u = new UpdateLanguageText(this);
        u.execute();
    }

    public LanguagesViewModel() {
        langListener = new SharedPreferences.OnSharedPreferenceChangeListener() {
            @Override
            public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
                if (key.equals(LanguageUtil.getLangPrefKey())) {
                    updateLanguageText();
                }
            }
        };
        Gdl.Companion.getSharedPrefs().registerListener(langListener);
        currentLanguage = new MutableLiveData<>();
        updateLanguageText();
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        Gdl.Companion.getSharedPrefs().unregisterListener(langListener);
    }
}