package io.digitallibrary.reader.reader;

import android.util.Log;

import com.io7m.jnull.NullCheck;

import java.net.URI;

/**
 * The default implementation of the {@link
 * ReaderGestureFeedbackDispatcherType}
 * interface.
 */

public final class ReaderGestureFeedbackDispatcher implements ReaderGestureFeedbackDispatcherType {
    private static final String TAG = "GestureFeedbackDisp";

    private ReaderGestureFeedbackDispatcher() {
    }

    /**
     * @return A new dispatcher
     */
    public static ReaderGestureFeedbackDispatcherType newDispatcher() {
        return new ReaderGestureFeedbackDispatcher();
    }

    private static void onClickCenter(final ReaderGestureFeedbackListenerType l) {
        try {
            l.onGestureClickCenter();
        } catch (final Throwable e) {
            try {
                l.onGestureClickCenterError(e);
            } catch (final Throwable x1) {
                Log.e(TAG, x1.getMessage(), x1);
            }
        }
    }

    private static void onClickLeft(final ReaderGestureFeedbackListenerType l) {
        try {
            l.onGestureClickLeft();
        } catch (final Throwable e) {
            try {
                l.onGestureClickLeftError(e);
            } catch (final Throwable x1) {
                Log.e(TAG, x1.getMessage(), x1);
            }
        }
    }

    private static void onClickRight(final ReaderGestureFeedbackListenerType l) {
        try {
            l.onGestureClickRight();
        } catch (final Throwable e) {
            try {
                l.onGestureClickRightError(e);
            } catch (final Throwable x1) {
                Log.e(TAG, x1.getMessage(), x1);
            }
        }
    }

    private static void onSwipeLeft(final ReaderGestureFeedbackListenerType l) {
        try {
            l.onGestureSwipeLeft();
        } catch (final Throwable e) {
            try {
                l.onGestureSwipeLeftError(e);
            } catch (final Throwable x1) {
                Log.e(TAG, x1.getMessage(), x1);
            }
        }
    }

    private static void onSwipeRight(final ReaderGestureFeedbackListenerType l) {
        try {
            l.onGestureSwipeRight();
        } catch (final Throwable e) {
            try {
                l.onGestureSwipeRightError(e);
            } catch (final Throwable x1) {
                Log.e(TAG, x1.getMessage(), x1);
            }
        }
    }

    @Override
    public void dispatch(final URI uri, final ReaderGestureFeedbackListenerType l) {
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
                if ("click-left".equals(function)) {
                    ReaderGestureFeedbackDispatcher.onClickLeft(l);
                    return;
                }
                if ("click-right".equals(function)) {
                    ReaderGestureFeedbackDispatcher.onClickRight(l);
                    return;
                }
                if ("click-center".equals(function)) {
                    ReaderGestureFeedbackDispatcher.onClickCenter(l);
                    return;
                }
                if ("swipe-left".equals(function)) {
                    ReaderGestureFeedbackDispatcher.onSwipeLeft(l);
                    return;
                }
                if ("swipe-right".equals(function)) {
                    ReaderGestureFeedbackDispatcher.onSwipeRight(l);
                    return;
                }
            }

            l.onGestureFunctionUnknown(NullCheck.notNull(uri.toString()));
        } catch (final Throwable x) {
            try {
                l.onGestureFunctionDispatchError(x);
            } catch (final Throwable x1) {
                Log.e(TAG, x1.getMessage(), x1);
            }
        }
    }
}
