package io.digitallibrary.reader.reader;

import io.digitallibrary.reader.reader.ReaderTOC.TOCElement;

/**
 * A listener that receives the results of TOC item selection.
 */

public interface ReaderTOCViewSelectionListenerType
{
  /**
   * No TOC item was selected.
   */

  void onTOCBackSelected();

  /**
   * The given TOC item was selected.
   *
   * @param e The selected item
   */

  void onTOCItemSelected(
      TOCElement e);
}
