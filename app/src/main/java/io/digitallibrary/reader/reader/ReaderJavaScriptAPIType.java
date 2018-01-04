package io.digitallibrary.reader.reader;

/**
 * The type of the JavaScript API exposed by Gdl.
 */

public interface ReaderJavaScriptAPIType
{
  /**
   * Notify the Javascript code that the page has changed in some way and
   * therefore new event listeners should be registered.
   */

  void pageHasChanged();
}
