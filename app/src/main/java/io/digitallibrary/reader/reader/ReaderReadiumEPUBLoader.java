package io.digitallibrary.reader.reader;

import android.content.Context;
import android.util.Log;

import com.io7m.jnull.NullCheck;
import com.io7m.jnull.Nullable;

import org.readium.sdk.android.Container;
import org.readium.sdk.android.ContentFilterErrorHandler;
import org.readium.sdk.android.EPub3;
import org.readium.sdk.android.Package;
import org.readium.sdk.android.SdkErrorHandler;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

/**
 * The default implementation of the {@link ReaderReadiumEPUBLoaderType}
 * interface.
 */
public final class ReaderReadiumEPUBLoader implements ReaderReadiumEPUBLoaderType {
    private static final String TAG = "EPUBLoader";

    private final ConcurrentHashMap<File, Container> containers;
    private final ExecutorService exec;
    private final Context context;

    private ReaderReadiumEPUBLoader(
            final Context in_context,
            final ExecutorService in_exec) {
        this.exec = NullCheck.notNull(in_exec);
        this.context = NullCheck.notNull(in_context);
        this.containers = new ConcurrentHashMap<>();
    }

    private static Container loadFromFile(final Context ctx, final File f)
            throws FileNotFoundException, IOException {

        /*
         * Readium will happily segfault if passed a filename that does not refer
         * to a file that exists.
         */

        if (!f.isFile()) {
            throw new FileNotFoundException("No such file");
        }

        /*
         * The majority of logged messages will be useless noise.
         */

        final SdkErrorHandler errors = new SdkErrorHandler() {
            @Override
            public boolean handleSdkError(final @Nullable String message, final boolean is_severe) {
                Log.d(TAG, message);
                return true;
            }
        };
        final ContentFilterErrorHandler content_filter_errors = new ContentFilterErrorHandler() {
            @Override
            public void handleContentFilterError(final @Nullable String filter_id,
                                                 final long error_code,
                                                 final @Nullable String message) {
                Log.d(TAG, filter_id + ":" + error_code + ": " + message);
            }
        };

        EPub3.setSdkErrorHandler(errors);
        EPub3.setContentFilterErrorHandler(content_filter_errors);
        final Container c = EPub3.openBook(f.toString());
        EPub3.setSdkErrorHandler(null);

        /*
         * Only the default package is considered important. If the package has no
         * spine items, then the package is considered to be unusable.
         */

        final Package p = c.getDefaultPackage();
        if (p.getSpineItems().isEmpty()) {
            throw new IOException("Loaded package had no spine items");
        }
        return c;
    }

    /**
     * Construct a new EPUB loader.
     *
     * @param in_context The application context
     * @param in_exec    An executor service
     * @return A new EPUB loader
     */
    public static ReaderReadiumEPUBLoaderType newLoader(final Context in_context, final ExecutorService in_exec) {
        return new ReaderReadiumEPUBLoader(in_context, in_exec);
    }

    @Override
    public void loadEPUB(final File f, final ReaderReadiumEPUBLoadListenerType l) {
        NullCheck.notNull(f);
        NullCheck.notNull(l);

        /*
         * This loader caches references to loaded containers. It's not actually
         * expected that there will be more than one container for the lifetime of
         * the process.
         */

        final ConcurrentHashMap<File, Container> cs = this.containers;
        this.exec.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    final Container c;
                    if (cs.containsKey(f)) {
                        c = NullCheck.notNull(cs.get(f));
                    } else {
                        c = ReaderReadiumEPUBLoader.loadFromFile(
                                ReaderReadiumEPUBLoader.this.context, f);
                        cs.put(f, c);
                    }

                    l.onEPUBLoadSucceeded(c);
                } catch (final Throwable x0) {
                    try {
                        l.onEPUBLoadFailed(x0);
                    } catch (final Throwable x1) {
                        Log.e(TAG, x1.getMessage(), x1);
                    }
                }
            }
        });
    }
}
