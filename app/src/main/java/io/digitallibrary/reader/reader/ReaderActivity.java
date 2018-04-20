package io.digitallibrary.reader.reader;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.io7m.jfunctional.FunctionType;
import com.io7m.jfunctional.OptionType;
import com.io7m.jnull.NullCheck;
import com.io7m.jnull.Nullable;

import org.readium.sdk.android.Container;
import org.readium.sdk.android.Package;

import java.io.File;
import java.net.URI;
import java.util.List;

import io.digitallibrary.reader.Gdl;
import io.digitallibrary.reader.R;
import io.digitallibrary.reader.reader.ReaderPaginationChangedEvent.OpenPage;
import io.digitallibrary.reader.reader.ReaderReadiumViewerSettings.ScrollMode;
import io.digitallibrary.reader.reader.ReaderReadiumViewerSettings.SyntheticSpreadMode;
import io.digitallibrary.reader.reader.ReaderTOC.TOCElement;
import io.digitallibrary.reader.utilities.ErrorDialogUtilities;
import io.digitallibrary.reader.utilities.UIThread;

/**
 * The main reader activity for reading an EPUB.
 */

public final class ReaderActivity extends Activity
        implements ReaderHTTPServerStartListenerType, ReaderGestureFeedbackListenerType,
        ReaderReadiumFeedbackListenerType, ReaderReadiumEPUBLoadListenerType,
        ReaderCurrentPageListenerType, ReaderTOCSelectionListenerType, ReaderSettingsListenerType {
    private static final String TAG = "ReaderActivity";
    private static final String BOOK_ID = "io.digitallibrary.reader.ReaderActivity.book_id";
    private static final String FILE_ID = "io.digitallibrary.reader.ReaderActivity.file_id";
    private static final String FULLSCREEN = "io.digitallibrary.reader.ReaderActivity.fullscreen";

    private String book_id;
    private Container epub_container;
    private ReaderReadiumJavaScriptAPIType readium_js_api;
    private ReaderJavaScriptAPIType simplified_js_api;
    private ViewGroup view_hud;
    private ProgressBar view_loading;
    private TextView view_progress_text;
    private TextView view_title_text;
    private ImageView view_toc;
    private WebView view_web_view;
    private ReaderReadiumViewerSettings viewer_settings;
    private FrameLayout reader_background;
    private ImageView reader_settings;
    private ImageView reader_toc;
    private ImageView reader_back;
    private boolean web_view_resized;
    private boolean fullscreen;
    private ReaderBookLocation current_location;
    private ImageView view_settings;
    private SharedPreferences.OnSharedPreferenceChangeListener brightnessListner;

    private boolean canStartAnotherActivity = true;

    /**
     * Construct an activity.
     */
    public ReaderActivity() {}

    /**
     * Start a new reader for the given book.
     *
     * @param from   The parent activity
     * @param bookId The unique ID of the book
     * @param file   The actual EPUB file
     */
    public static void startActivity(final Activity from, final String bookId, final File file) {
        NullCheck.notNull(file);
        final Bundle b = new Bundle();
        b.putSerializable(ReaderActivity.BOOK_ID, bookId);
        b.putSerializable(ReaderActivity.FILE_ID, file);
        final Intent i = new Intent(from, ReaderActivity.class);
        i.putExtras(b);
        from.startActivity(i);
    }

    private void makeInitialReadiumRequest(final ReaderHTTPServerType hs) {
        final URI reader_uri = URI.create(hs.getURIBase() + "reader.html");
        final WebView wv = NullCheck.notNull(this.view_web_view);
        UIThread.runOnUIThread(new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "making initial reader request: " + reader_uri);
                wv.loadUrl(reader_uri.toString());
            }
        });
    }

    @Override
    protected void onActivityResult(final int request_code, final int result_code,
                                    final @Nullable Intent data) {
        super.onActivityResult(request_code, result_code, data);

        Log.d(TAG, "onActivityResult: " + request_code + " " + result_code + " " + data);

        if (request_code == ReaderTOCActivity.TOC_SELECTION_REQUEST_CODE) {
            if (result_code == Activity.RESULT_OK) {
                final Intent nnd = NullCheck.notNull(data);
                final Bundle b = NullCheck.notNull(nnd.getExtras());
                final TOCElement e =
                        NullCheck.notNull((TOCElement) b.getSerializable(ReaderTOCActivity.TOC_SELECTED_ID));
                this.onTOCSelectionReceived(e);
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        canStartAnotherActivity = true;
    }

    @Override
    protected void onCreate(final @Nullable Bundle state) {
        Log.d(TAG, "starting");

        setTheme(Gdl.Companion.getThemeId());

        super.onCreate(state);
        this.setContentView(R.layout.activity_reader);

        final Intent i = NullCheck.notNull(this.getIntent());
        final Bundle a = NullCheck.notNull(i.getExtras());

        this.fullscreen = state != null && state.getBoolean(FULLSCREEN);

        final File in_epub_file = NullCheck.notNull((File) a.getSerializable(ReaderActivity.FILE_ID));
        this.book_id = NullCheck.notNull((String) a.getSerializable(ReaderActivity.BOOK_ID));

        Log.d(TAG, "epub file: " + in_epub_file);
        Log.d(TAG, "book id:   " + this.book_id);

        final Gdl.ReaderAppServices rs = Gdl.Companion.getReaderAppServices();

        final ReaderSettingsType settings = rs.getSettings();
        settings.addListener(this);

        this.viewer_settings =
                new ReaderReadiumViewerSettings(SyntheticSpreadMode.SINGLE, ScrollMode.AUTO,
                        (int) settings.getFontScale(), 20);

        final ReaderReadiumFeedbackDispatcherType rd = ReaderReadiumFeedbackDispatcher.newDispatcher();
        final ReaderGestureFeedbackDispatcherType sd =
                ReaderGestureFeedbackDispatcher.newDispatcher();

        final ViewGroup in_hud =
                NullCheck.notNull((ViewGroup) this.findViewById(R.id.reader_hud_container));
        final ImageView in_toc = NullCheck.notNull((ImageView) in_hud.findViewById(R.id.reader_toc));
        final ImageView in_settings =
                NullCheck.notNull((ImageView) in_hud.findViewById(R.id.reader_settings));
        final TextView in_title_text =
                NullCheck.notNull((TextView) this.findViewById(R.id.reader_title_text));
        final TextView in_progress_text =
                NullCheck.notNull((TextView) this.findViewById(R.id.reader_position_text));
        final View in_progress_container =
                NullCheck.notNull((View) this.findViewById(R.id.reader_progress));
        final View in_progress_devider =
                NullCheck.notNull((View) this.findViewById(R.id.reader_progress_divider));

        final ProgressBar in_loading =
                NullCheck.notNull((ProgressBar) this.findViewById(R.id.reader_loading));
        final WebView in_webview = NullCheck.notNull((WebView) this.findViewById(R.id.reader_webview));

        this.reader_background = NullCheck.notNull((FrameLayout) this.findViewById(R.id.reader_background));
        this.reader_settings = NullCheck.notNull((ImageView) this.findViewById(R.id.reader_settings));
        this.reader_toc = NullCheck.notNull((ImageView) this.findViewById(R.id.reader_toc));
        this.reader_back = NullCheck.notNull((ImageView) this.findViewById(R.id.reader_back));

        in_loading.setVisibility(View.VISIBLE);
        in_progress_text.setVisibility(View.INVISIBLE);
        in_webview.setVisibility(View.INVISIBLE);
        in_hud.setVisibility(View.VISIBLE);

        this.view_loading = in_loading;
        this.view_progress_text = in_progress_text;
        this.view_title_text = in_title_text;
        this.view_web_view = in_webview;
        this.view_hud = in_hud;
        this.view_toc = in_toc;
        this.view_settings = in_settings;
        this.web_view_resized = true;

        View decorView = getWindow().getDecorView();
        if (fullscreen) {
            decorView.setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
            this.view_hud.setVisibility(View.INVISIBLE);
            this.view_title_text.setVisibility(View.VISIBLE);
            in_progress_devider.setVisibility(View.INVISIBLE);
            ViewGroup.LayoutParams lp = in_progress_container.getLayoutParams();
            lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            in_progress_container.setLayoutParams(lp);
        } else {
            decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
            this.view_hud.setVisibility(View.VISIBLE);
            this.view_title_text.setVisibility(View.INVISIBLE);
        }

        final WebChromeClient wc_client = new WebChromeClient() {
            @Override
            public void onShowCustomView(final @Nullable View view,
                                         final @Nullable CustomViewCallback callback) {
                super.onShowCustomView(view, callback);
                Log.d(TAG, "web-chrome: " + view);
            }
        };

        final WebViewClient wv_client = new ReaderWebViewClient(this, sd, this, rd, this);
        in_webview.setBackgroundColor(0x00000000);
        in_webview.setWebChromeClient(wc_client);
        in_webview.setWebViewClient(wv_client);
        in_webview.setOnLongClickListener(new OnLongClickListener() {
            @Override
            public boolean onLongClick(final @Nullable View v) {
                Log.d(TAG, "ignoring long click on web view");
                return true;
            }
        });

        view_settings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final @com.io7m.jnull.Nullable View v) {
                if (canStartAnotherActivity) {
                    Intent i = new Intent(ReaderActivity.this, ReaderSettingsActivity.class);
                    ReaderActivity.this.startActivity(i);
                    ReaderActivity.this.overridePendingTransition(0, 0);
                    canStartAnotherActivity = false;
                }
            }
        });

        // Allow the webview to be debuggable only if this is a dev build
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            if ((getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
                WebView.setWebContentsDebuggingEnabled(true);
            }
        }

        final WebSettings s = NullCheck.notNull(in_webview.getSettings());
        s.setAppCacheEnabled(false);
        s.setAllowFileAccess(false);
        s.setAllowFileAccessFromFileURLs(false);
        s.setAllowContentAccess(false);
        s.setAllowUniversalAccessFromFileURLs(false);
        s.setSupportMultipleWindows(false);
        s.setCacheMode(WebSettings.LOAD_NO_CACHE);
        s.setGeolocationEnabled(false);
        s.setJavaScriptEnabled(true);

        this.readium_js_api = ReaderReadiumJavaScriptAPI.newAPI(in_webview);
        this.simplified_js_api = ReaderJavaScriptAPI.newAPI(in_webview);

        in_title_text.setText("");

        final ReaderReadiumEPUBLoaderType pl = rs.getEpubLoader();
        pl.loadEPUB(in_epub_file, this);

        // set reader brightness.
        final int brightness = Gdl.Companion.getSharedPrefs().getInt("reader_brightness", 50);
        final float back_light_value = (float) brightness / 100;
        final WindowManager.LayoutParams layout_params = getWindow().getAttributes();
        layout_params.screenBrightness = back_light_value;
        getWindow().setAttributes(layout_params);

        brightnessListner = new SharedPreferences.OnSharedPreferenceChangeListener() {
            @Override
            public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
                if (key.equals("reader_brightness")) {
                    updateBrightness();
                }
            }
        };
        Gdl.Companion.getSharedPrefs().registerListener(brightnessListner);
        updateBrightness();
        updateColors(settings.getColorScheme());
    }

    private void updateBrightness() {
        final int brightness = Gdl.Companion.getSharedPrefs().getInt("reader_brightness", 50);
        final float back_light_value = (float) brightness / 100;
        final WindowManager.LayoutParams layout_params = getWindow().getAttributes();
        layout_params.screenBrightness = back_light_value;
        getWindow().setAttributes(layout_params);
    }

    @Override
    public void onCurrentPageError(final Throwable x) {
        Log.d(TAG, x.getMessage(), x);
    }

    @Override
    public void onCurrentPageReceived(final ReaderBookLocation l) {
        Log.d(TAG, "received book location: " + l);
        final Gdl.ReaderAppServices rs = Gdl.Companion.getReaderAppServices();
        final ReaderBookmarks bm = rs.getBookmarks();
        final String in_book_id = NullCheck.notNull(this.book_id);
        bm.setBookmark(in_book_id, l);
    }

    @Override
    protected void onPause() {
        super.onPause();

        final Gdl.ReaderAppServices rs = Gdl.Companion.getReaderAppServices();

        if (this.book_id != null && this.current_location != null) {
            rs.getBookmarks().setBookmark(this.book_id, this.current_location);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        Gdl.Companion.getSharedPrefs().unregisterListener(brightnessListner);

        final ReaderReadiumJavaScriptAPIType readium_js =
                NullCheck.notNull(ReaderActivity.this.readium_js_api);
        readium_js.getCurrentPage(ReaderActivity.this);

        final Gdl.ReaderAppServices rs = Gdl.Companion.getReaderAppServices();

        final ReaderSettingsType settings = rs.getSettings();
        settings.removeListener(this);
        //    System.exit(0);
    }

    @Override
    public void onEPUBLoadFailed(final Throwable x) {
        ErrorDialogUtilities
                .showErrorWithRunnable(this, TAG, "Could not load EPUB file", x,
                        new Runnable() {
                            @Override
                            public void run() {
                                ReaderActivity.this.finish();
                            }
                        });
    }

    @Override
    public void onEPUBLoadSucceeded(final Container c) {
        this.epub_container = c;
        final Package p = NullCheck.notNull(c.getDefaultPackage());

        final TextView in_title_text = NullCheck.notNull(this.view_title_text);

        UIThread.runOnUIThread(new Runnable() {
            @Override
            public void run() {
                in_title_text.setText(NullCheck.notNull(p.getTitle()));
            }
        });

        /*
         * Configure the TOC button.
         */

        final Gdl.ReaderAppServices rs = Gdl.Companion.getReaderAppServices();

        final View in_toc = NullCheck.notNull(this.view_toc);

        final ReaderTOC toc = ReaderTOC.fromPackage(p);

        in_toc.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(final @Nullable View v) {
                if (canStartAnotherActivity) {
                    ReaderTOCActivity.startActivityForResult(ReaderActivity.this, toc);
                    ReaderActivity.this.overridePendingTransition(0, 0);
                    canStartAnotherActivity = false;
                }
            }
        });

        /*
         * Get a reference to the web server. Start it if necessary (the callbacks
         * will still be executed if the server is already running).
         */

        final ReaderHTTPServerType hs = rs.getHttpServer();
        hs.startIfNecessaryForPackage(p, this);
    }

    @Override
    public void onReaderSettingsChanged(final ReaderSettingsType s) {
        Log.d(TAG, "reader settings changed");

        final ReaderReadiumJavaScriptAPIType js = NullCheck.notNull(this.readium_js_api);
        js.setPageStyleSettings(s);

        final ReaderColorScheme cs = s.getColorScheme();

        UIThread.runOnUIThreadDelayed(new Runnable() {
            @Override
            public void run() {
                final ReaderReadiumJavaScriptAPIType readium_js =
                        NullCheck.notNull(ReaderActivity.this.readium_js_api);
                readium_js.getCurrentPage(ReaderActivity.this);

                updateColors(cs);
            }
        }, 300L);
    }

    private void updateColors(final ReaderColorScheme cs) {
        reader_background.setBackgroundColor(cs.getBackgroundColor());
        view_title_text.setTextColor(cs.getForegroundColor());
        view_progress_text.setTextColor(cs.getForegroundColor());
        switch (cs) {
            case SCHEME_BLACK_ON_BEIGE:
                reader_settings.setImageResource(R.drawable.ic_font_download_dark_24dp);
                reader_toc.setImageResource(R.drawable.ic_format_list_numbered_dark_24dp);
                reader_back.setImageResource(R.drawable.ic_arrow_back_dark_24dp);
                break;
            case SCHEME_BLACK_ON_WHITE:
                reader_settings.setImageResource(R.drawable.ic_font_download_dark_24dp);
                reader_toc.setImageResource(R.drawable.ic_format_list_numbered_dark_24dp);
                reader_back.setImageResource(R.drawable.ic_arrow_back_dark_24dp);
                break;
            case SCHEME_WHITE_ON_BLACK:
                reader_settings.setImageResource(R.drawable.ic_font_download_light_24dp);
                reader_toc.setImageResource(R.drawable.ic_format_list_numbered_light_24dp);
                reader_back.setImageResource(R.drawable.ic_arrow_back_light_24dp);
                break;
        }
    }

    @Override
    public void onReadiumFunctionDispatchError(final Throwable x) {
        Log.d(TAG, x.getMessage(), x);
    }

    @Override
    public void onReadiumFunctionInitialize() {
        Log.d(TAG, "readium initialize requested");

        final Gdl.ReaderAppServices rs = Gdl.Companion.getReaderAppServices();

        final ReaderHTTPServerType hs = rs.getHttpServer();
        final Container c = NullCheck.notNull(this.epub_container);
        final Package p = NullCheck.notNull(c.getDefaultPackage());
        p.setRootUrls(hs.getURIBase().toString(), null);

        final ReaderReadiumViewerSettings vs = NullCheck.notNull(this.viewer_settings);
        final ReaderReadiumJavaScriptAPIType js = NullCheck.notNull(this.readium_js_api);

        /*
         * If there's a bookmark for the current book, send a request to open the
         * book to that specific page. Otherwise, start at the beginning.
         */

        final String in_book_id = NullCheck.notNull(this.book_id);

        final ReaderBookmarks bookmarks = rs.getBookmarks();
        final OptionType<ReaderBookLocation> mark = bookmarks.getBookmark(in_book_id);

        final OptionType<ReaderOpenPageRequestType> page_request =
                mark.map(new FunctionType<ReaderBookLocation, ReaderOpenPageRequestType>() {
                    @Override
                    public ReaderOpenPageRequestType call(final ReaderBookLocation l) {
                        ReaderActivity.this.current_location = l;
                        return ReaderOpenPageRequest.fromBookLocation(l);
                    }
                });
        // is this correct? inject fonts before book opens or after
        js.injectFonts();

        // open book with page request, vs = view settings, p = package , what is package actually ? page_request = idref + contentcfi
        js.openBook(p, vs, page_request);

        /*
         * Configure the visibility of UI elements.
         */

        final WebView in_web_view = NullCheck.notNull(this.view_web_view);
        final ProgressBar in_loading = NullCheck.notNull(this.view_loading);
        final TextView in_progress_text = NullCheck.notNull(this.view_progress_text);

        in_loading.setVisibility(View.GONE);
        in_web_view.setVisibility(View.VISIBLE);
        in_progress_text.setVisibility(View.INVISIBLE);

        final ReaderSettingsType settings = rs.getSettings();
        this.onReaderSettingsChanged(settings);
    }

    @Override
    public void onReadiumFunctionInitializeError(final Throwable e) {
        ErrorDialogUtilities
                .showErrorWithRunnable(this, TAG, "Unable to initialize Readium", e,
                        new Runnable() {
                            @Override
                            public void run() {
                                ReaderActivity.this.finish();
                            }
                        });
    }

    /**
     * {@inheritDoc}
     * <p>
     * When the device orientation changes, the configuration change handler
     * {@link #onConfigurationChanged(Configuration)} makes the web view invisible
     * so that the user does not see the now incorrectly-paginated content. When
     * Readium tells the app that the content pagination has changed, it makes the
     * web view visible again.
     */

    @Override
    public void onReadiumFunctionPaginationChanged(final ReaderPaginationChangedEvent e) {
        Log.d(TAG, "pagination changed: " + e);
        final WebView in_web_view = NullCheck.notNull(this.view_web_view);


        /*
         * Configure the progress bar and text.
         */

        final TextView in_progress_text = NullCheck.notNull(this.view_progress_text);

        final Container container = NullCheck.notNull(this.epub_container);
        final Package default_package = NullCheck.notNull(container.getDefaultPackage());

        UIThread.runOnUIThread(new Runnable() {
            @Override
            public void run() {
                final List<OpenPage> pages = e.getOpenPages();
                if (pages.isEmpty()) {
                    in_progress_text.setText("");
                } else {
                    final OpenPage page = NullCheck.notNull(pages.get(0));
                    in_progress_text.setText(NullCheck.notNull(String
                            .format("%d/%d - %s", page.getSpineItemPageIndex() + 1,
                                    page.getSpineItemPageCount(),
                                    default_package.getSpineItem(page.getIDRef()).getTitle())));
                }

                /*
                 * Ask for Readium to deliver the unique identifier of the current page,
                 * and tell Gdl that the page has changed and so any Javascript
                 * state should be reconfigured.
                 */
                UIThread.runOnUIThreadDelayed(new Runnable() {
                    @Override
                    public void run() {
                        final ReaderReadiumJavaScriptAPIType readium_js =
                                NullCheck.notNull(ReaderActivity.this.readium_js_api);
                        readium_js.getCurrentPage(ReaderActivity.this);
                    }
                }, 300L);
            }
        });

        final ReaderJavaScriptAPIType simplified_js =
                NullCheck.notNull(this.simplified_js_api);

        /*
         * Make the web view visible with a slight delay (as sometimes a
         * pagination-change event will be sent even though the content has not
         * yet been laid out in the web view). Only do this if the screen
         * orientation has just changed.
         */

        if (this.web_view_resized) {
            this.web_view_resized = false;
            UIThread.runOnUIThreadDelayed(new Runnable() {
                @Override
                public void run() {
                    in_web_view.setVisibility(View.VISIBLE);
                    in_progress_text.setVisibility(View.VISIBLE);
                    simplified_js.pageHasChanged();
                }
            }, 200L);
        } else {
            UIThread.runOnUIThread(new Runnable() {
                @Override
                public void run() {
                    simplified_js.pageHasChanged();
                }
            });
        }
    }

    @Override
    public void onReadiumFunctionPaginationChangedError(final Throwable x) {
        Log.d(TAG, x.getMessage(), x);
    }

    @Override
    public void onReadiumFunctionSettingsApplied() {
        Log.d(TAG, "received settings applied");
    }

    @Override
    public void onReadiumFunctionSettingsAppliedError(final Throwable e) {
        Log.d(TAG, e.getMessage(), e);
    }

    @Override
    public void onReadiumFunctionUnknown(final String text) {
        Log.d(TAG, "unknown readium function: " + text);
    }

    @Override
    public void onServerStartFailed(final ReaderHTTPServerType hs, final Throwable x) {
        ErrorDialogUtilities
                .showErrorWithRunnable(this, TAG, "Could not start http server.", x,
                        new Runnable() {
                            @Override
                            public void run() {
                                ReaderActivity.this.finish();
                            }
                        });
    }

    @Override
    public void onServerStartSucceeded(final ReaderHTTPServerType hs, final boolean first) {
        if (first) {
            Log.d(TAG, "http server started");
        } else {
            Log.d(TAG, "http server already running");
        }

        this.makeInitialReadiumRequest(hs);
    }

    @Override
    public void onGestureFunctionDispatchError(final Throwable x) {
        Log.d(TAG, x.getMessage(), x);
    }

    @Override
    public void onGestureFunctionUnknown(final String text) {
        Log.d(TAG, "unknown function: " + text);
    }

    @Override
    public void onGestureClickCenter() {
        UIThread.runOnUIThread(new Runnable() {
            @Override
            public void run() {
                fullscreen = !fullscreen;
                recreate();
            }
        });
    }

    @Override
    public void onGestureClickCenterError(final Throwable x) {
        Log.d(TAG, x.getMessage(), x);
    }

    @Override
    public void onGestureClickLeft() {
        final ReaderReadiumJavaScriptAPIType js = NullCheck.notNull(this.readium_js_api);
        js.pagePrevious();
    }

    @Override
    public void onGestureClickLeftError(final Throwable x) {
        Log.d(TAG, x.getMessage(), x);
    }

    @Override
    public void onGestureClickRight() {
        final ReaderReadiumJavaScriptAPIType js = NullCheck.notNull(this.readium_js_api);
        js.pageNext();
    }

    @Override
    public void onGestureClickRightError(final Throwable x) {
        Log.d(TAG, x.getMessage(), x);
    }

    @Override
    public void onGestureSwipeLeft() {
        final ReaderReadiumJavaScriptAPIType js = NullCheck.notNull(this.readium_js_api);
        js.pagePrevious();
    }

    @Override
    public void onGestureSwipeLeftError(Throwable x) {
        Log.d(TAG, x.getMessage(), x);
    }

    @Override
    public void onGestureSwipeRight() {
        final ReaderReadiumJavaScriptAPIType js = NullCheck.notNull(this.readium_js_api);
        js.pageNext();
    }

    @Override
    public void onGestureSwipeRightError(Throwable x) {
        Log.d(TAG, x.getMessage(), x);
    }

    @Override
    public void onTOCSelectionReceived(final TOCElement e) {
        Log.d(TAG, "received TOC selection: " + e);

        final ReaderReadiumJavaScriptAPIType js = NullCheck.notNull(this.readium_js_api);

        js.openContentURL(e.getContentRef(), e.getSourceHref());
    }

    public void onBackClicked(View view) {
        finish();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(FULLSCREEN, fullscreen);
    }
}
