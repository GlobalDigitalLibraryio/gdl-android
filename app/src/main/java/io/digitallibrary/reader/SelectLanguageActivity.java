package io.digitallibrary.reader;

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;

import io.digitallibrary.reader.utilities.LanguageUtil;

public class SelectLanguageActivity extends AppCompatActivity {
    public static final String TAG = "SelectLanguageActivity";
    private RelativeLayout background;
    private LinearLayout languageDrawer;
    private RelativeLayout spinnerAndErrorContainer;
    private ProgressBar spinner;
    private TextView errorMsg;
    private ArrayAdapter<String> langItemsAdapter;
    private ListView langList;
    private int selected;

    @Override
    protected void onCreate(final Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.select_language);
        background = findViewById(R.id.background);
        languageDrawer = findViewById(R.id.language_drawer);
        spinnerAndErrorContainer = findViewById(R.id.spinner_and_error_msg);
        spinner = findViewById(R.id.spinner);
        errorMsg = findViewById(R.id.error_msg);
        langList = findViewById(R.id.languages);
        overridePendingTransition(0,0);
        ViewTreeObserver vto = languageDrawer.getViewTreeObserver();
        background.setAlpha(0f);
        vto.addOnGlobalLayoutListener (new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                animateIn();
            }
        });

        fetchLanguages();
    }

    private void fetchLanguages() {
        LanguageUtil.getLanguageListFromServer(this, new LanguageUtil.Callback() {
            @Override
            public void onSuccess(final Map<String, String> languages) {
                final ArrayList<String> langArray = new ArrayList<>(languages.keySet());
                Collections.sort(langArray);
                selected = -1;
                for (int i=0; i != langArray.size(); ++i) {
                    Log.i(TAG, "i " + i);
                    if (langArray.get(i).equals(LanguageUtil.getCurrentLanguageText())) {
                        selected = i;
                        continue;
                    }
                }
                Log.i(TAG, "Selected is: " + selected);
                langItemsAdapter = new ArrayAdapter<String>(SelectLanguageActivity.this, R.layout.language_row, R.id.language_name, langArray) {
                    @NonNull
                    @Override
                    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
                        View v = super.getView(position, convertView, parent);
                        if (position == selected) {
                            v.setSelected(true);
                            v.findViewById(R.id.checked).setVisibility(View.VISIBLE);
                            v.setBackgroundResource(R.drawable.nav_menu_background_pressed);
                        } else {
                            v.findViewById(R.id.checked).setVisibility(View.INVISIBLE);
                            v.setBackgroundResource(R.drawable.nav_menu_background_default);
                        }
                        return v;
                    }
                };
                spinnerAndErrorContainer.setVisibility(View.GONE);
                langList.setVisibility(View.VISIBLE);
                langList.setAdapter(langItemsAdapter);
                langList.setItemChecked(selected, true);
                langList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                    @Override
                    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                        Log.i(TAG, "onItemClick: " + position);
                        selected = position;
                        String newLang = langArray.get(position);
                        String newLangCode = languages.get(newLang);
                        String oldLang = LanguageUtil.getCurrentLanguage();
                        LanguageUtil.setLanguage(newLangCode, newLang);
                        view.findViewById(R.id.checked).setVisibility(View.VISIBLE);
                        langItemsAdapter.notifyDataSetChanged();
                        if (!oldLang.equals(newLang)) {
                            Gdl.fetch();
                        }
                    }
                });
            }

            @Override
            public void onError() {
                spinner.setVisibility(View.GONE);
                errorMsg.setVisibility(View.VISIBLE);
            }
        });
    }

    private void animateIn() {
        final float scale = getApplication().getResources().getDisplayMetrics().density;
        languageDrawer.animate().x(47f * scale);
        background.animate().alpha(0.3f);
    }

    private void animateOut() {
        final float scale = getApplication().getResources().getDisplayMetrics().density;
        languageDrawer.animate().x(-267f * scale);
        background.animate().alpha(0f);
    }

    @Override
    public void onBackPressed() {
        animateOut();
        languageDrawer.animate().withEndAction(new Runnable() {
            @Override
            public void run() {
                SelectLanguageActivity.super.onBackPressed();
                SelectLanguageActivity.this.overridePendingTransition(0,0);
            }
        });
    }

    public void onCloseLanguagesClicked(View view) {
        animateOut();
        languageDrawer.animate().withEndAction(new Runnable() {
            @Override
            public void run() {
                SelectLanguageActivity.this.finish();
                SelectLanguageActivity.this.overridePendingTransition(0,0);
            }
        });
    }

    public void onBackgroundClicked(View view) {
        animateOut();
        languageDrawer.animate().withEndAction(new Runnable() {
            @Override
            public void run() {
                SelectLanguageActivity.this.finish();
                SelectLanguageActivity.this.overridePendingTransition(0,0);
            }
        });
    }
}
