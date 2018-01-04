package io.digitallibrary.reader.reader;

import android.content.res.AssetManager;
import android.util.Log;

import com.io7m.jnull.NullCheck;
import com.io7m.jnull.Nullable;
import com.koushikdutta.async.AsyncServer;
import com.koushikdutta.async.AsyncServerSocket;
import com.koushikdutta.async.AsyncSocket;
import com.koushikdutta.async.callback.ListenCallback;
import com.koushikdutta.async.http.Headers;
import com.koushikdutta.async.http.server.AsyncHttpServer;
import com.koushikdutta.async.http.server.AsyncHttpServerRequest;
import com.koushikdutta.async.http.server.AsyncHttpServerResponse;
import com.koushikdutta.async.http.server.HttpServerRequestCallback;

import org.readium.sdk.android.Package;
import org.readium.sdk.android.util.ResourceInputStream;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The AndroidAsync implementation of the {@link ReaderHTTPServerType}
 * interface.
 */

public final class ReaderHTTPServerAAsync implements ReaderHTTPServerType, HttpServerRequestCallback {
    private static final String TAG = "ReaderHTTPServerAAsync";

    private final URI base;
    private final ReaderHTTPMimeMapType mime;
    private final int port;
    private final AsyncServer server;
    private final AtomicBoolean started;
    private final AsyncHttpServer server_http;
    private final AssetManager assets;
    private @Nullable
    Package epub_package;
    private @Nullable
    AsyncServerSocket socket;

    private ReaderHTTPServerAAsync(final AssetManager in_assets, final ReaderHTTPMimeMapType in_mime,
                                   final int in_port) {
        assets = NullCheck.notNull(in_assets);
        mime = NullCheck.notNull(in_mime);
        port = in_port;
        base = NullCheck.notNull(URI.create("http://127.0.0.1:" + in_port + "/"));
        server = new AsyncServer();
        server_http = new AsyncHttpServer();
        started = new AtomicBoolean(false);

        /*
         * This looks like it will make a request to the server, but it actually
         * registers an action that will be performed for GET requests.
         */
        server_http.get(".*", this);
    }

    /**
     * Create a new HTTP server.
     *
     * @param in_assets The assets
     * @param mime      The server mime map
     * @param port      The port
     * @return A new HTTP server
     */
    public static ReaderHTTPServerType newServer(final AssetManager in_assets,
                                                 final ReaderHTTPMimeMapType mime, final int port) {
        return new ReaderHTTPServerAAsync(in_assets, mime, port);
    }

    @Override
    public URI getURIBase() {
        return this.base;
    }

    @Override
    public void startIfNecessaryForPackage(final Package p, final ReaderHTTPServerStartListenerType s) {
        final ReaderHTTPServerAAsync as = this;

        if (this.started.compareAndSet(false, true)) {
            try {
                final InetSocketAddress host = new InetSocketAddress("127.0.0.1", this.port);
                final ListenCallback http_callback = this.server_http.getListenCallback();

                this.socket = this.server.listen(
                        host.getAddress(), this.port, new ListenCallback() {
                            @Override
                            public void onAccepted(final AsyncSocket in_socket) {
                                Log.d(TAG, "accept: " + in_socket);
                                http_callback.onAccepted(in_socket);
                            }

                            @Override
                            public void onListening(final AsyncServerSocket in_socket) {
                                Log.d(TAG, "listening: " + in_socket);
                                as.setPackage(p);
                                s.onServerStartSucceeded(as, true);
                                http_callback.onListening(in_socket);
                            }

                            @Override
                            public void onCompleted(final Exception ex) {
                                Log.d(TAG, "completed: " + ex);
                                http_callback.onCompleted(ex);
                            }
                        });
            } catch (final Throwable e) {
                s.onServerStartFailed(this, e);
                this.started.set(false);
            }
        } else {
            Log.d(TAG, "server already running");
            as.setPackage(p);
            s.onServerStartSucceeded(as, true);
        }
    }

    @Override
    public void onRequest(final AsyncHttpServerRequest request, final AsyncHttpServerResponse response) {
        try {
            final ReaderNativeCodeReadLock read_lock = ReaderNativeCodeReadLock.get();

            /*
             * Determine if the current request is a range request.
             */

            final Boolean range_opt = haveRange(request.getHeaders());

            /*
             * Guess the mime type.
             */
            final String path = request.getPath();
            final String type = mime.guessMimeTypeForURI(path);

            /*
             * First, try looking at the available Android assets.
             */
            {
                try {
                    String asset_path = path.replaceFirst("^/+", "");
                    if (asset_path.contains("OpenDyslexic")) {
                        asset_path = "OpenDyslexic3-Regular.ttf";
                    }
                    Log.d(TAG, "opening asset: " + asset_path);

                    final InputStream stream =
                            this.assets.open(asset_path, AssetManager.ACCESS_STREAMING);

                    response.code(200);
                    response.setContentType(type);
                    response.sendStream(stream, stream.available());
                    Log.d(TAG, "request: (asset) " + response.code() + " " +  path);
                    return;
                } catch (final FileNotFoundException e) {
                    Log.d(TAG, "asset not found: " + path);
                }
            }

            /*
             * Otherwise, try looking at the included Java resources. This includes
             * all of the readium shared javascript content. For resources served
             * in this manner, range requests are ignored and the full entity is
             * always served.
             */
            {
                final InputStream stream = ReaderHTTPServerAAsync.class.getResourceAsStream(path);

                if (stream != null) {
                    response.code(200);
                    response.setContentType(type);
                    response.sendStream(stream, stream.available());
                    Log.d(TAG, "request: (resource) " + response.code() + " " + path);
                    return;
                }
            }

            /*
             * Otherwise, try serving it from the package. Range requests are
             * respected iff they are satisfiable.
             */
            {
                final Package pack = NullCheck.notNull(this.epub_package);
                final String relative = path.replaceFirst("^[/]+", "");

                Log.d(TAG, "request: trying package path: " + relative);

                final int size;
                synchronized (read_lock) {
                    size = pack.getArchiveInfoSize(relative);
                }

                if (size >= 0) {

                    /*
                     * Return a byte range stream that allows for very fine-grained
                     * locking (locks are acquired during read operations and released
                     * afterwards).
                     */
                    final InputStream response_stream;

                    synchronized (read_lock) {
                        final ResourceInputStream stream = NullCheck.notNull(
                                (ResourceInputStream) pack.getInputStream(relative, range_opt));
                        response_stream =
                                new ReaderHTTPByteRangeInputStream(stream, range_opt, read_lock);
                    }

                    response.code(range_opt ? 206 : 200);
                    response.setContentType(type);
                    response.sendStream(response_stream, response_stream.available());
                    Log.d(TAG, "request: (package) " + response.code() + " " + path);
                    return;
                }
            }

            /*
             * Otherwise, give up.
             */

            response.code(404);
            response.setContentType("text/plain");
            response.send("NOT FOUND");
            Log.d(TAG, "request: " + response.code() + " " + path);

        } catch (final Throwable e) {
            Log.d(TAG, "error: : " + request.getPath(), e);
            response.code(500);
            response.setContentType("text/plain");
            response.send(e.getMessage());
        } finally {
            Log.d(TAG, "request: done {}" + request.getPath());
        }
    }

    private Boolean haveRange(final Headers headers) {
        if (headers.get("range") != null) {
            final String range_text = NullCheck.notNull(headers.get("range"));
            return !range_text.equals("");
        } else {
            return false;
        }
    }

    private void setPackage(final Package p) {
        this.epub_package = NullCheck.notNull(p);
    }
}
