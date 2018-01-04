package io.digitallibrary.reader.reader;

import android.util.Log;

import com.io7m.jnull.NullCheck;

import org.json.JSONObject;

import java.net.URI;
import java.net.URLDecoder;

/**
 * The default implementation of the {@link ReaderReadiumFeedbackDispatcherType}
 * interface.
 */
public final class ReaderReadiumFeedbackDispatcher implements ReaderReadiumFeedbackDispatcherType {
    private static final String TAG = "ReadiumFeedbackDispatch";

    private ReaderReadiumFeedbackDispatcher() {}

    /**
     * @return A new dispatcher
     */
    public static ReaderReadiumFeedbackDispatcherType newDispatcher() {
        return new ReaderReadiumFeedbackDispatcher();
    }

    private static void onInitialize(final ReaderReadiumFeedbackListenerType l) {
        try {
            l.onReadiumFunctionInitialize();
        } catch (final Throwable e) {
            try {
                l.onReadiumFunctionInitializeError(e);
            } catch (final Throwable x1) {
                Log.e(TAG, x1.getMessage(), x1);
            }
        }
    }

    private static void onPaginationChanged(final ReaderReadiumFeedbackListenerType l, final String[] parts) {
        try {
            if (parts.length < 2) {
                throw new IllegalArgumentException("Expected pagination data, but got nothing");
            }

            final String encoded = NullCheck.notNull(parts[1]);
            final String decoded = NullCheck.notNull(URLDecoder.decode(encoded, "UTF-8"));
            final JSONObject json = new JSONObject(decoded);
            final ReaderPaginationChangedEvent e = ReaderPaginationChangedEvent.fromJSON(json);

            l.onReadiumFunctionPaginationChanged(e);
        } catch (final Throwable e) {
            try {
                l.onReadiumFunctionPaginationChangedError(e);
            } catch (final Throwable x1) {
                Log.e(TAG, x1.getMessage(), x1);
            }
        }
    }

    private static void onSettingsApplied(final ReaderReadiumFeedbackListenerType l) {
        try {
            l.onReadiumFunctionSettingsApplied();
        } catch (final Throwable e) {
            try {
                l.onReadiumFunctionSettingsAppliedError(e);
            } catch (final Throwable x1) {
                Log.e(TAG, x1.getMessage(), x1);
            }
        }
    }

    @Override
    public void dispatch(final URI uri, final ReaderReadiumFeedbackListenerType l) {
        NullCheck.notNull(uri);
        NullCheck.notNull(l);

        Log.d(TAG, "dispatching: " + uri);

        /*
         * Note that all exceptions are caught here, as any exceptions raised
         * inside a callback called from a WebView tend to segfault the WebView.
         */

        try {
            final String data = NullCheck.notNull(uri.getSchemeSpecificPart());
            final String[] parts = NullCheck.notNull(data.split("/"));

            if (parts.length >= 1) {
                final String function = NullCheck.notNull(parts[0]);
                if ("initialize".equals(function)) {
                    ReaderReadiumFeedbackDispatcher.onInitialize(l);
                    return;
                }

                if ("pagination-changed".equals(function)) {
                    ReaderReadiumFeedbackDispatcher.onPaginationChanged(l, parts);
                    return;
                }

                if ("settings-applied".equals(function)) {
                    ReaderReadiumFeedbackDispatcher.onSettingsApplied(l);
                    return;
                }
            }

            l.onReadiumFunctionUnknown(NullCheck.notNull(uri.toString()));
        } catch (final Throwable x) {
            try {
                l.onReadiumFunctionDispatchError(x);
            } catch (final Throwable x1) {
                Log.e(TAG, x1.getMessage(), x1);
            }
        }
    }
}
