package io.digitallibrary.reader.utilities;

import android.content.Context;
import android.util.Log;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import io.digitallibrary.reader.Prefs;

import java.util.HashMap;
import java.util.Map;

import io.digitallibrary.reader.Gdl;

public class LanguageUtil {
    public static final String TAG = "LANG SET";

    private static final String API_URL = "https://api.staging.digitallibrary.io/book-api/v1/languages";
    private static final String FALLBACK_LANG = "eng";
    private static final String FALLBACK_LANG_TEXT = "English";
    private static final String PREF_ID_CURRENT_LANG = "io.digitallibrary.reader.LANG";
    private static final String PREF_ID_CURRENT_LANG_TEXT = "io.digitallibrary.reader.LANG_TEXT";

    public static void getLanguageListFromServer(Context ctx, final Callback callback) {
        final RequestQueue queue = Volley.newRequestQueue(ctx);
        JsonArrayRequest jar = new JsonArrayRequest(Request.Method.GET, API_URL, null, new Response.Listener<JSONArray>() {
            @Override
            public void onResponse(JSONArray jsonArray) {
                Map<String, String> m = new HashMap<>();
                try {
                    for (int i = 0; i != jsonArray.length(); ++i) {
                        JSONObject lang = jsonArray.getJSONObject(i);
                        m.put(lang.getString("name"), lang.getString("code"));
                    }
                    Log.d(TAG, "Resp: " + m);
                    callback.onSuccess(m);
                } catch (JSONException e) {
                    e.printStackTrace();
                    callback.onError();
                }
            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError volleyError) {
                callback.onError();
            }
        });
        queue.add(jar);
    }

    public static String getLangPrefKey() {
        return PREF_ID_CURRENT_LANG;
    }

    public static String getCurrentLanguage() {
        Prefs p = Gdl.getSharedPrefs();
        String current_lang = p.getString(PREF_ID_CURRENT_LANG);
        if (current_lang != null && current_lang.length() > 0) {
            return current_lang;
        } else {
            return FALLBACK_LANG;
        }
    }

    public static String getCurrentLanguageText() {
        Prefs p = Gdl.getSharedPrefs();
        String current_lang = p.getString(PREF_ID_CURRENT_LANG_TEXT);
        if (current_lang != null && current_lang.length() > 0) {
            return current_lang;
        } else {
            return FALLBACK_LANG_TEXT;
        }
    }

    public static void setLanguage(String lang, String langText) {
        Prefs p = Gdl.getSharedPrefs();
        p.putString(PREF_ID_CURRENT_LANG, lang);
        p.putString(PREF_ID_CURRENT_LANG_TEXT, langText);
    }

    public interface Callback {
        void onSuccess(Map<String, String> languages);
        void onError();
    }
}