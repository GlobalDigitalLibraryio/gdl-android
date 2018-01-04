package io.digitallibrary.reader.reader;

/**
 * Functions called via the {@code host_app_feedback.js} file using the {@code
 * gesture} URI scheme.
 */

public interface ReaderGestureFeedbackListenerType {
    /**
     * Called when an exception is raised when trying to dispatch a function.
     *
     * @param x The exception
     */
    void onGestureFunctionDispatchError(
            Throwable x);

    /**
     * Called when an unknown request is made.
     *
     * @param text The text of the request
     */
    void onGestureFunctionUnknown(
            String text);

    /**
     * Called upon receipt of a center click gesture.
     */
    void onGestureClickCenter();

    /**
     * Called if {@link #onGestureClickCenter()} raises an exception.
     *
     * @param x The exception raised
     */
    void onGestureClickCenterError(
            Throwable x);

    /**
     * Called upon receipt of a left click gesture.
     */
    void onGestureClickLeft();

    /**
     * Called if {@link #onGestureClickLeft()} raises an exception.
     *
     * @param x The exception raised
     */
    void onGestureClickLeftError(
            Throwable x);

    /**
     * Called upon receipt of a right click gesture.
     */
    void onGestureClickRight();

    /**
     * Called if {@link #onGestureClickRight()} raises an exception.
     *
     * @param x The exception raised
     */
    void onGestureClickRightError(
            Throwable x);

    /**
     * Called upon receipt of a left swipe gesture.
     */
    void onGestureSwipeLeft();

    /**
     * Called if {@link #onGestureSwipeLeft()} raises an exception.
     *
     * @param x The exception raised
     */
    void onGestureSwipeLeftError(
            Throwable x);

    /**
     * Called upon receipt of a right swipe gesture.
     */
    void onGestureSwipeRight();

    /**
     * Called if {@link #onGestureSwipeRight()} raises an exception.
     *
     * @param x The exception raised
     */
    void onGestureSwipeRightError(
            Throwable x);
}
