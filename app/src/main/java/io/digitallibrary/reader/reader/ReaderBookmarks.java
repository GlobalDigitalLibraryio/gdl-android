package io.digitallibrary.reader.reader;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.util.Log;

import com.io7m.jfunctional.Option;
import com.io7m.jfunctional.OptionType;
import com.io7m.jfunctional.Some;
import com.io7m.jnull.NullCheck;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Timer;
import java.util.TimerTask;

/**
 * <p>This implementation uses the Android `SharedPreferences` class to
 * serialize bookmarks.</p>
 */

public final class ReaderBookmarks {
    private static final String TAG = "ReaderBookmarks";
    private Timer write_timer = new Timer();

    private final SharedPreferences bookmarks;

    private ReaderBookmarks(final Context cc) {
        NullCheck.notNull(cc);
        this.bookmarks = NullCheck.notNull(cc.getSharedPreferences("reader-bookmarks", 0));
    }

    /**
     * Open the bookmarks database.
     *
     * @param cc The application context
     * @return A bookmarks database
     */
    public static ReaderBookmarks openBookmarks(final Context cc) {
        return new ReaderBookmarks(cc);
    }

    public OptionType<ReaderBookLocation> getBookmark(final String id) {
        NullCheck.notNull(id);
        final String key = NullCheck.notNull(id);

        try {
            if (this.bookmarks.contains(key)) {
                final String text = this.bookmarks.getString(key, null);
                if (text != null) {
                    final JSONObject o = new JSONObject(text);
                    return Option.some(ReaderBookLocation.fromJSON(o));
                }
            }
            return Option.none();
        } catch (final JSONException e) {
            Log.e(TAG, "unable to deserialize bookmark: " + e.getMessage(), e);
            return Option.none();
        }
    }

    public void setBookmark(final String id, final ReaderBookLocation bookmark) {
        NullCheck.notNull(id);
        NullCheck.notNull(bookmark);

        write_timer.cancel();
        write_timer = new Timer();
        write_timer.schedule(new TimerTask() {
            @Override
            public void run() {
                try {
                    Log.d(TAG, "saving bookmark for book " + id + ": " + bookmark);
                    // save to server
                    if (!"null".equals(((Some<String>) bookmark.getContentCFI()).get())) {
                        final JSONObject o = NullCheck.notNull(bookmark.toJSON());
                        final String text = NullCheck.notNull(o.toString());
                        final String key = NullCheck.notNull(id.toString());
                        final Editor e = ReaderBookmarks.this.bookmarks.edit();
                        e.putString(key, text);
                        e.apply();
                    }
                } catch (final JSONException e) {
                    Log.e(TAG, "unable to serialize bookmark: " + e.getMessage(), e);
                }
                Log.d(TAG, "CurrentPage timer run ");
            }
        }, 3000L);
    }
}
