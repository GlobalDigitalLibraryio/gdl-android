package io.digitallibrary.reader.reader;

import android.util.Log;
import android.webkit.WebView;

import com.io7m.jnull.NullCheck;

import io.digitallibrary.reader.utilities.UIThread;

/**
 * The default implementation of the {@link ReaderJavaScriptAPIType}
 * interface.
 */

public final class ReaderJavaScriptAPI implements ReaderJavaScriptAPIType {
    private static final String TAG = "ReaderJavaScriptAPI";

    private final WebView web_view;

    private ReaderJavaScriptAPI(final WebView wv) {
        this.web_view = NullCheck.notNull(wv);
    }

    /**
     * Construct a new JavaScript API.
     *
     * @param wv The web view
     * @return A new API
     */

    public static ReaderJavaScriptAPIType newAPI(final WebView wv) {
        return new ReaderJavaScriptAPI(wv);
    }

    private void evaluate(final String script) {
        Log.d(TAG, "sending javascript: " + script);

        UIThread.runOnUIThread(new Runnable() {
                    @Override
                    public void run() {
                        ReaderJavaScriptAPI.this.web_view.evaluateJavascript(script, null);
                    }
                });
    }

    @Override
    public void pageHasChanged() {
        this.evaluate("simplified.pageDidChange();");
    }
}
