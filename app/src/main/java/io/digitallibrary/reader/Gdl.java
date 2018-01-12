package io.digitallibrary.reader;

import android.app.Application;
import android.arch.persistence.room.Room;
import android.content.Context;
import android.content.res.Resources;
import android.os.Environment;
import android.os.Process;
import android.util.DisplayMetrics;
import android.util.Log;

import com.io7m.jnull.NullCheck;
import com.io7m.jnull.Nullable;

import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

import io.digitallibrary.reader.catalog.Callback;
import io.digitallibrary.reader.catalog.CatalogDatabase;
import io.digitallibrary.reader.reader.ReaderBookmarks;
import io.digitallibrary.reader.reader.ReaderHTTPMimeMap;
import io.digitallibrary.reader.reader.ReaderHTTPMimeMapType;
import io.digitallibrary.reader.reader.ReaderHTTPServerAAsync;
import io.digitallibrary.reader.reader.ReaderHTTPServerType;
import io.digitallibrary.reader.reader.ReaderReadiumEPUBLoader;
import io.digitallibrary.reader.reader.ReaderReadiumEPUBLoaderType;
import io.digitallibrary.reader.reader.ReaderSettings;
import io.digitallibrary.reader.reader.ReaderSettingsType;

import static io.digitallibrary.reader.GdlActivity.MENU_CHOICE_PREF;
import static io.digitallibrary.reader.catalog.Opds_parserKt.fetchFeed;

/**
 * Global application state.
 */

public final class Gdl extends Application {
    private static final String TAG = "Gdl";
    private static volatile @Nullable Gdl INSTANCE;
    private @Nullable ReaderAppServices reader_services;
    private static CatalogDatabase database;

    /**
     * Construct the application.
     */
    public Gdl() {}

    private static Gdl checkInitialized() {
        final Gdl i = Gdl.INSTANCE;
        if (i == null) {
            throw new IllegalStateException("Application is not yet initialized");
        }
        return i;
    }

    public static CatalogDatabase getDatabase() {
        Callback c = new Callback() {
            @Override
            public void done() {

            }
        };
        return database;
    }


    public static void fetch(final Callback callback) {
        fetchFeed(callback);
    }

    /**
     * @return Shared Preferences
     */
    public static Prefs getSharedPrefs() {
        final Gdl i = Gdl.checkInitialized();
        return new Prefs(i.getApplicationContext());
    }

    static File getDiskDataDir(final Context context) {
        /*
         * If external storage is mounted and is on a device that doesn't allow
         * the storage to be removed, use the external storage for data.
         */

        if (Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())) {

            Log.d(TAG,"trying external storage");
            if (!Environment.isExternalStorageRemovable()) {
                final File r = context.getExternalFilesDir(null);
                Log.d(TAG,"external storage is not removable, using it (" + r + ")");
                return NullCheck.notNull(r);
            }
        }

        /*
         * Otherwise, use internal storage.
         */

        final File r = context.getFilesDir();
        Log.d(TAG,"no non-removable external storage, using internal storage (" + r + ")");
        return NullCheck.notNull(r);
    }

    /**
     * @return The application services provided to the Reader.
     */
    public static ReaderAppServices getReaderAppServices() {
        final Gdl i = Gdl.checkInitialized();
        return i.getActualReaderAppServices();
    }

    private static ExecutorService namedThreadPool(final int count, final String base,
                                                   final int priority) {
        final ThreadFactory tf = Executors.defaultThreadFactory();
        final ThreadFactory named = new ThreadFactory() {
            private int id;

            @Override
            public Thread newThread(final @Nullable Runnable r) {
                /*
                 * Apparently, it's necessary to use {@link android.os.Process} to set
                 * the thread priority, rather than the standard Java thread
                 * functions.
                 */

                final Thread t = tf.newThread(new Runnable() {
                    @Override
                    public void run() {
                        android.os.Process.setThreadPriority(priority);
                        NullCheck.notNull(r).run();
                    }
                });
                t.setName(String.format("gdl-%s-tasks-%d", base, this.id));
                ++this.id;
                return t;
            }
        };

        final ExecutorService pool = Executors.newFixedThreadPool(count, named);
        return NullCheck.notNull(pool);
    }

    private ReaderAppServices getActualReaderAppServices() {
        if (this.reader_services == null) {
            this.reader_services =
                    new ReaderAppServices(null, this, NullCheck.notNull(this.getResources()));
        }
        return this.reader_services;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG,"starting app: pid " + Process.myPid());
        Gdl.INSTANCE = this;
        getSharedPrefs().putInt(MENU_CHOICE_PREF, GdlActivity.MenuChoices.getDefault().ordinal());

        database = Room.databaseBuilder(getApplicationContext(), CatalogDatabase.class, "catalog_db").build();

        // Opds_parserKt.fetchFeed();
    }

    public static final class ReaderAppServices  {
        private final ReaderBookmarks bookmarks;
        private final ExecutorService epub_exec;
        private final ReaderReadiumEPUBLoaderType epub_loader;
        private final ReaderHTTPServerType httpd;
        private final ReaderHTTPMimeMapType mime;
        private final ScreenSizeController screen;
        private final ReaderSettingsType settings;

        private ReaderAppServices(final ExecutorService in_epub_exec, final Context context,
                                  final Resources rr) {
            this.screen = new ScreenSizeController(rr);

            this.mime = ReaderHTTPMimeMap.newMap("application/octet-stream");

            // Fallback port
            Integer port = 8080;
            try {
                final ServerSocket s = new ServerSocket(0);
                port = s.getLocalPort();
                s.close();
            } catch (IOException e) {
                // Ignore
            }

            this.httpd = ReaderHTTPServerAAsync.newServer(context.getAssets(), this.mime, port);

            if (in_epub_exec == null) {
                this.epub_exec = Gdl.namedThreadPool(1, "epub", 19);
            } else {
                this.epub_exec = in_epub_exec;
            }

            this.epub_loader = ReaderReadiumEPUBLoader.newLoader(context, this.epub_exec);

            this.settings = ReaderSettings.openSettings(context);
            this.bookmarks = ReaderBookmarks.openBookmarks(context);
        }

        public ReaderBookmarks getBookmarks() {
            return this.bookmarks;
        }

        public ReaderReadiumEPUBLoaderType getEPUBLoader() {
            return this.epub_loader;
        }

        public ReaderHTTPServerType getHTTPServer() {
            return this.httpd;
        }

        public ReaderSettingsType getSettings() {
            return this.settings;
        }

        public double screenDPToPixels(final int dp) {
            return this.screen.screenDPToPixels(dp);
        }

        public double screenGetDPI() {
            return this.screen.screenGetDPI();
        }

        public int screenGetHeightPixels() {
            return this.screen.screenGetHeightPixels();
        }

        public int screenGetWidthPixels() {
            return this.screen.screenGetWidthPixels();
        }

    }

    private static final class ScreenSizeController {
        private final Resources resources;

        private ScreenSizeController(final Resources rr) {
            this.resources = NullCheck.notNull(rr);

            final DisplayMetrics dm = this.resources.getDisplayMetrics();
            final float dp_height = (float) dm.heightPixels / dm.density;
            final float dp_width = (float) dm.widthPixels / dm.density;
            Log.d(TAG, "screen (" + dp_width + " x " + dp_height + ")");
            Log.d(TAG, "screen (" + dm.widthPixels + " x " + dm.heightPixels + ")");
        }

        double screenDPToPixels(final int dp) {
            final float scale = this.resources.getDisplayMetrics().density;
            return ((double) (dp * scale) + 0.5);
        }

        double screenGetDPI() {
            final DisplayMetrics metrics = this.resources.getDisplayMetrics();
            return (double) metrics.densityDpi;
        }

        int screenGetHeightPixels() {
            final Resources rr = NullCheck.notNull(this.resources);
            final DisplayMetrics dm = rr.getDisplayMetrics();
            return dm.heightPixels;
        }

        int screenGetWidthPixels() {
            final Resources rr = NullCheck.notNull(this.resources);
            final DisplayMetrics dm = rr.getDisplayMetrics();
            return dm.widthPixels;
        }
    }
}
