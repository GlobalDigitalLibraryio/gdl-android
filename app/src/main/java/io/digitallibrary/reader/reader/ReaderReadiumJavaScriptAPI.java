package io.digitallibrary.reader.reader;

import android.util.Log;
import android.webkit.ValueCallback;
import android.webkit.WebView;

import com.io7m.jfunctional.OptionType;
import com.io7m.jfunctional.Some;
import com.io7m.jnull.NullCheck;
import com.io7m.jnull.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import io.digitallibrary.reader.reader.ReaderReadiumViewerSettings.ScrollMode;
import io.digitallibrary.reader.reader.ReaderReadiumViewerSettings.SyntheticSpreadMode;
import io.digitallibrary.reader.utilities.TextUtilities;
import io.digitallibrary.reader.utilities.UIThread;

/**
 * The default implementation of the {@link ReaderReadiumJavaScriptAPIType}
 * interface.
 */

public final class ReaderReadiumJavaScriptAPI implements ReaderReadiumJavaScriptAPIType {
    private static final String TAG = "RRJavaScriptAPI";

    private final WebView web_view;

    private ReaderReadiumJavaScriptAPI(final WebView wv) {
        this.web_view = NullCheck.notNull(wv);
    }

    /**
     * Construct a new JavaScript API.
     *
     * @param wv A web view
     * @return A new API
     */
    static ReaderReadiumJavaScriptAPIType newAPI(
            final WebView wv) {
        return new ReaderReadiumJavaScriptAPI(wv);
    }

    private void evaluate(final String script) {
        Log.d(TAG, "sending javascript: " + script);

        UIThread.runOnUIThread(new Runnable() {
            @Override
            public void run() {
                ReaderReadiumJavaScriptAPI.this.web_view.evaluateJavascript(script, null);
            }
        });
    }

    private void evaluateWithResult(final String script, final ValueCallback<String> callback) {
        Log.d(TAG, "sending javascript: " + script);

        UIThread.runOnUIThread(new Runnable() {
            @Override
            public void run() {
                ReaderReadiumJavaScriptAPI.this.web_view.evaluateJavascript(script, callback);
            }
        });
    }

    @Override
    public void getCurrentPage(final ReaderCurrentPageListenerType l) {
        NullCheck.notNull(l);

        this.evaluateWithResult("ReadiumSDK.reader.bookmarkCurrentPage()", new ValueCallback<String>() {
            @Override
            public void onReceiveValue(final @Nullable String value) {
                try {
                    final JSONObject o =
                            new JSONObject(TextUtilities.unquote(NullCheck.notNull(value)));
                    final ReaderBookLocation loc = ReaderBookLocation.fromJSON(o);
                    l.onCurrentPageReceived(loc);
                } catch (final Throwable x) {
                    try {
                        l.onCurrentPageError(x);
                    } catch (final Throwable x1) {
                        Log.e(TAG, x1.getMessage(), x1);
                    }
                }
            }
        });
    }

    @Override
    public void mediaOverlayNext() {
        this.evaluate("ReadiumSDK.reader.nextMediaOverlay();");
    }

    @Override
    public void mediaOverlayPrevious() {
        this.evaluate("ReadiumSDK.reader.previousMediaOverlay();");
    }

    @Override
    public void mediaOverlayToggle() {
        this.evaluate("ReadiumSDK.reader.toggleMediaOverlay();");
    }

    @Override
    public void openBook(final org.readium.sdk.android.Package p, final ReaderReadiumViewerSettings vs,
                         final OptionType<ReaderOpenPageRequestType> r) {
        try {
            final JSONObject o = new JSONObject();
            o.put("package", p.toJSON());
            o.put("settings", vs.toJSON());

            if (r.isSome()) {
                final Some<ReaderOpenPageRequestType> some = (Some<ReaderOpenPageRequestType>) r;
                o.put("openPageRequest", some.get().toJSON());
            }

            this.evaluate(NullCheck.notNull(String.format("ReadiumSDK.reader.openBook(%s)", o)));
        } catch (final JSONException e) {
            throw new IllegalArgumentException(e);
        }
    }

    @Override
    public void openContentURL(final String content_ref, final String source_href) {
        NullCheck.notNull(content_ref);
        NullCheck.notNull(source_href);

        this.evaluate(NullCheck.notNull(String.format("ReadiumSDK.reader.openContentUrl('%s','%s',null)",
                content_ref, source_href)));
    }

    @Override
    public void pageNext() {
        this.evaluate("ReadiumSDK.reader.openPageRight();");
    }

    @Override
    public void pagePrevious() {
        this.evaluate("ReadiumSDK.reader.openPageLeft();");
    }

    @Override
    public void setPageStyleSettings(final ReaderSettingsType r) {
        try {
            final ReaderColorScheme cs = r.getColorScheme();
            final String color = NullCheck.notNull(String.format("#%06x", cs.getForegroundColor() & 0xffffff));
            final String background = NullCheck.notNull(String.format("#%06x", cs.getBackgroundColor() & 0xffffff));

            final JSONObject decls = new JSONObject();
            decls.put("color", color);
            decls.put("backgroundColor", background);

            switch (r.getFontFamily()) {
                case READER_FONT_SANS_SERIF: {
                    decls.put("font-family", "sans-serif");
                    break;
                }
                case READER_FONT_OPEN_DYSLEXIC: {
                    /*
                     * This is defined as a custom CSS font family inside
                     * OpenDyslexic.css, which is referenced from the initially
                     * loaded reader.html file.
                     */
                    decls.put("font-family", "OpenDyslexic3");
                    break;
                }
                case READER_FONT_SERIF: {
                    decls.put("font-family", "serif");
                    break;
                }
            }

            final JSONObject o = new JSONObject();
            o.put("selector", "*");
            o.put("declarations", decls);

            final JSONArray styles = new JSONArray();
            styles.put(o);

            final StringBuilder script = new StringBuilder(256);
            script.append("ReadiumSDK.reader.setBookStyles(");
            script.append(styles);
            script.append("); ");
            script.append("document.body.style.backgroundColor = \"");
            script.append(background);
            script.append("\";");
            this.evaluate(script.toString());

            final ReaderReadiumViewerSettings vs = new ReaderReadiumViewerSettings(
                    SyntheticSpreadMode.SINGLE, ScrollMode.AUTO,
                    (int) r.getFontScale(),20);

            this.evaluate(NullCheck.notNull(String.format("ReadiumSDK.reader.updateSettings(%s);", vs.toJSON())));

        } catch (final JSONException e) {
            Log.e(TAG, "error constructing json: " + e.getMessage(), e);
        }
    }

    @Override
    public void injectFonts() {
        try {
            final JSONObject s = new JSONObject();
            s.put("truetype", "OpenDyslexic3-Regular.ttf");

            final JSONObject o = new JSONObject();
            o.put("fontFamily", "OpenDyslexic3");
            o.put("fontWeight", "normal");
            o.put("fontStyle", "normal");
            o.put("sources", s);

            final StringBuilder script = new StringBuilder(256);
            script.append("ReadiumSDK.reader.plugins.injectFonts.registerFontFace(");
            script.append(o);
            script.append(");");

            this.evaluate(script.toString());
        } catch (final JSONException e) {
            Log.e(TAG, "error constructing json: " + e.getMessage(), e);
        }
    }

    @Override
    public void mediaOverlayIsAvailable(final ReaderMediaOverlayAvailabilityListenerType l) {
        NullCheck.notNull(l);

        this.evaluateWithResult("ReadiumSDK.reader.isMediaOverlayAvailable()", new ValueCallback<String>() {
            @Override
            public void onReceiveValue(
                    final @Nullable String value) {
                try {
                    final boolean available = Boolean.valueOf(value);
                    l.onMediaOverlayIsAvailable(available);
                } catch (final Throwable x) {
                    try {
                        l.onMediaOverlayIsAvailableError(x);
                    } catch (final Throwable x1) {
                        Log.e(TAG, x1.getMessage(), x1);
                    }
                }
            }
        });
    }
}
