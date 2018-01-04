package io.digitallibrary.reader.reader;

import android.graphics.Color;

/**
 * The selected color scheme for the reader.
 */

public enum ReaderColorScheme {
    /**
     * Black text on a beige backdrop.
     */
    SCHEME_BLACK_ON_BEIGE(Color.argb(0xff, 255, 242, 230), Color.argb(0xff, 108, 83, 60)),

    /**
     * Black text on a white backdrop.
     */
    SCHEME_BLACK_ON_WHITE(Color.WHITE, Color.argb(0xff, 68, 68, 68)),

    /**
     * White text on a black backdrop.
     */
    SCHEME_WHITE_ON_BLACK(Color.argb(0xff, 68, 68, 68), Color.argb(0xff, 248, 248, 248));

    private final int back;
    private final int fore;

    ReaderColorScheme(final int bg, final int fg) {
        this.back = bg;
        this.fore = fg;
    }

    /**
     * @return The background color as an ARGB value
     */
    public int getBackgroundColor() {
        return this.back;
    }

    /**
     * @return The foreground color as an ARGB value
     */
    public int getForegroundColor() {
        return this.fore;
    }
}
