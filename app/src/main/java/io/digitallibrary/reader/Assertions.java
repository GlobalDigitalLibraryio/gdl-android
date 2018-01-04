package io.digitallibrary.reader;

import android.util.Log;

/**
 * A trivial class containing functions for checking preconditions and
 * invariants ("assertions that cannot be turned off").
 */

public final class Assertions {
    private static final String TAG = "Assertions";

    /**
     * Require that the given invariant {@code c} be {@code true}, raising
     * {@link AssertionError} if it isn't.
     *
     * @param condition The invariant
     * @param message   The message displayed on failure (a format string)
     * @param args      The message format arguments
     */
    public static void checkInvariant(
            final boolean condition,
            final String message,
            final Object... args) {
        if (!condition) {
            final String m = String.format(message, args);
            Log.e(TAG, "assertion failed: invariant: " + m);
            throw new AssertionError(m);
        }
    }

    /**
     * Require that the given precondition {@code c} be {@code true}, raising
     * {@link AssertionError} if it isn't.
     *
     * @param c       The precondition
     * @param message The message displayed on failure (a format string)
     * @param args    The message format arguments
     */

    public static void checkPrecondition(
            final boolean c,
            final String message,
            final Object... args) {
        if (!c) {
            final String m = String.format(message, args);
            Log.e(TAG, "assertion failed: precondition: " + m);
            throw new AssertionError(m);
        }
    }

    private Assertions() {
        throw new AssertionError("Unreachable code!");
    }
}
