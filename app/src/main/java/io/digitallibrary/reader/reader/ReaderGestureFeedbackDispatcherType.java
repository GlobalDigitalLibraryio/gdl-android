package io.digitallibrary.reader.reader;

import java.net.URI;

/**
 * The type of Gdl function dispatchers.
 *
 * A dispatcher expects non-hierarchical URIs that use a {@code simplified}
 * scheme.
 */

public interface ReaderGestureFeedbackDispatcherType
{
  /**
   * Dispatch the URI to the given listener.
   *
   * @param uri The URI
   * @param l   The receiving listener
   */

  void dispatch(
      URI uri,
      ReaderGestureFeedbackListenerType l);
}
