package io.digitallibrary.reader;

import android.app.Application;
import android.arch.persistence.room.Room;
import android.content.Context;
import android.content.res.Resources;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Environment;
import android.os.Process;
import android.util.DisplayMetrics;
import android.util.Log;

import com.io7m.jnull.NullCheck;
import com.io7m.jnull.Nullable;

import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;

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
import io.digitallibrary.reader.utilities.LanguageUtil;

import static io.digitallibrary.reader.GdlActivity.MENU_CHOICE_PREF;

/**
 * Global application state.
 */

public final class Gdl extends Application {
    private static final String TAG = "Gdl";
    private static volatile @Nullable Gdl INSTANCE;
    private static final String LIBRARY_ID = "0";
    private @Nullable CatalogAppServices app_services;
    private @Nullable ReaderAppServices reader_services;
    private CatalogDatabase database;


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

    public CatalogDatabase getDatabase() {
        return database;
    }

    /**
     * @return The application services provided to the Catalog.
     */
    public static CatalogAppServices getCatalogAppServices() {
        final Gdl i = Gdl.checkInitialized();
        return i.getActualAppServices();
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
                t.setName(String.format("simplified-%s-tasks-%d", base, this.id));
                ++this.id;
                return t;
            }
        };

        final ExecutorService pool = Executors.newFixedThreadPool(count, named);
        return NullCheck.notNull(pool);
    }

    // TODO
//    public static BooksType getBooks(final Context context) {
//
//    }

    private synchronized CatalogAppServices getActualAppServices() {
        if (this.app_services == null) {
            this.app_services =
                    new CatalogAppServices(null, this,
                            NullCheck.notNull(this.getResources()), LIBRARY_ID);
        }
        return this.app_services;
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
    }

    private static final class CatalogAppServices {
        private static final String TAG = "CatalogAppServices";

        private final Context context;
        private final ExecutorService exec_books;
        private final ExecutorService exec_catalog_feeds;
        private final ExecutorService exec_covers;
        private final ExecutorService exec_downloader;
        private URI feed_initial_uri;
        private final ScreenSizeController screen;
        private final AtomicBoolean synced;

        private CatalogAppServices(final Application in_app, final Context in_context,
                                   final Resources rr, final String in_library) {
            NullCheck.notNull(rr);

            this.context = NullCheck.notNull(in_context);
            this.screen = new ScreenSizeController(rr);
            this.exec_catalog_feeds = Gdl.namedThreadPool(1, "catalog-feed", 19);
            this.exec_covers = Gdl.namedThreadPool(2, "cover", 19);
            this.exec_downloader = Gdl.namedThreadPool(4, "downloader", 19);
            this.exec_books = Gdl.namedThreadPool(1, "books", 19);

           /*
            * Application paths.
            */

            File accounts_dir = this.context.getFilesDir();

            final File base_dir = Gdl.getDiskDataDir(in_context);

            final File downloads_dir = new File(base_dir, "downloads");
            final File books_dir = new File(base_dir, "books");

           /*
            * Make sure the required directories exist. There is no sane way to
            * recover if they cannot be created!
            */
            if (!downloads_dir.isDirectory()) {
                boolean res = downloads_dir.mkdirs();
                if (!res) {
                    Log.d(TAG,"could not create directories: " + downloads_dir.getAbsolutePath());
                    throw new IllegalStateException();
                }
            }
            if (!books_dir.isDirectory()) {
                boolean res = books_dir.mkdirs();
                if (!res) {
                    Log.d(TAG,"could not create directories: " + downloads_dir.getAbsolutePath());
                    throw new IllegalStateException();
                }
            }

            Log.d(TAG, "base:                " + base_dir);
            Log.d(TAG, "base_accounts_dir:   " + accounts_dir);
            Log.d(TAG, "downloads:           " + downloads_dir);
            Log.d(TAG, "books:               " + books_dir);

           /*
            * Catalog URIs.
            */
            final BooksControllerConfiguration books_config =
                    new BooksControllerConfiguration();

            this.feed_initial_uri = books_config.getCurrentRootFeedURI();

           /*
            * Feed loaders and parsers.
            */

            // TODO create parser
            // TODO databases?
            // TODO downloader

            // TODO some kind of books controller

           /*
            * Configure cover provider.
            */
//            this.cover_provider = BookCoverProvider
//                    .newCoverProvider(in_context, this.books.bookGetDatabase(), this.exec_covers);

           /*
            * Has the initial sync operation been carried out?
            */
            this.synced = new AtomicBoolean(false);
        }

        public void reloadCatalog(final boolean delete_books) {
            final Gdl i = Gdl.checkInitialized();
            i.getActualAppServices();
        }
        public void destroyDatabase() {

        }

        public boolean isNetworkAvailable() {
            final NetworkInfo info =
                    ((ConnectivityManager) this.context.getSystemService(Context.CONNECTIVITY_SERVICE))
                            .getActiveNetworkInfo();
            return info != null && info.isConnected();
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

        public void syncInitial() {
            if (this.synced.compareAndSet(false, true)) {
                Log.d(TAG, "performing initial sync");
                // TODO load books
            } else {
                Log.d(TAG, "initial sync already attempted, not syncing again");
            }
        }
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

        public double screenDPToPixels(final int dp) {
            final float scale = this.resources.getDisplayMetrics().density;
            return ((double) (dp * scale) + 0.5);
        }

        public double screenGetDPI() {
            final DisplayMetrics metrics = this.resources.getDisplayMetrics();
            return (double) metrics.densityDpi;
        }

        public int screenGetHeightPixels() {
            final Resources rr = NullCheck.notNull(this.resources);
            final DisplayMetrics dm = rr.getDisplayMetrics();
            return dm.heightPixels;
        }

        public int screenGetWidthPixels() {
            final Resources rr = NullCheck.notNull(this.resources);
            final DisplayMetrics dm = rr.getDisplayMetrics();
            return dm.widthPixels;
        }

    }

    private final static class BooksControllerConfiguration {
        private static final String CATALOG_URL_ROOT =
                "https://opds.staging.digitallibrary.io/"; // eng/root.xml";

        BooksControllerConfiguration() {}

        public synchronized URI getCurrentRootFeedURI() {
            try {
                return new URI(CATALOG_URL_ROOT + LanguageUtil.getCurrentLanguage() + "/root.xml");
            } catch (URISyntaxException e) {
                e.printStackTrace();
                return null;
            }
        }
    }
}
