package com.po4yka.framelapse.core.collections.inplace

/**
 * Iterates through the windows of the list and performs the given action
 * on each window.
 *
 * This is a zero-allocation operation - the same [InPlaceSubList] instance
 * is reused for each window, with only its start and end indices updated.
 * This makes it ideal for performance-critical batch processing where you
 * need to process elements in sliding windows.
 *
 * @param windowSize The desired size of each window. Must be greater
 *    than 0.
 * @param step The step size between consecutive windows. Must be greater
 *    than 0. Defaults to 1.
 * @param partialWindows Whether to include partial windows at the end of
 *    the list if any. Defaults to `false`.
 * @param action The function to be executed for each window.
 * @throws IllegalArgumentException if windowSize or step is less than or
 *    equal to 0.
 *
 * Example usage:
 * ```
 * val frames = listOf(frame1, frame2, frame3, frame4, frame5)
 *
 * // Process in sliding windows of 3 with step 1
 * frames.forEachWindow(windowSize = 3, step = 1) { window ->
 *     window.fastForEach { frame -> processFrame(frame) }
 * }
 *
 * // Process in non-overlapping windows of 2
 * frames.forEachWindow(windowSize = 2, step = 2, partialWindows = true) { window ->
 *     println("Window size: ${window.size}")
 * }
 * ```
 */
public inline fun <T> List<T>.forEachWindow(
    windowSize: Int,
    step: Int = 1,
    partialWindows: Boolean = false,
    action: (InPlaceSubList<T>) -> Unit,
) {
    if (isEmpty()) return
    require(windowSize > 0) { "Window size must be greater than 0" }
    require(step > 0) { "Step must be greater than 0" }
    if (size < windowSize && !partialWindows) return

    val current = InPlaceSubList(this, 0, minOf(windowSize, size))

    while (current.start < size) {
        action(current)
        current.start += step
        val newEnd = current.start + windowSize
        if (newEnd > size && !partialWindows) break
        current.end = newEnd.coerceAtMost(size)
    }
}

/**
 * Iterates through the windows of the list with their indices and performs
 * the given action on each window.
 *
 * This is a zero-allocation operation - the same [InPlaceSubList] instance
 * is reused for each window, with only its start and end indices updated.
 *
 * @param windowSize The desired size of each window. Must be greater
 *    than 0.
 * @param step The step size between consecutive windows. Must be greater
 *    than 0. Defaults to 1.
 * @param partialWindows Whether to include partial windows at the end of
 *    the list if any. Defaults to `false`.
 * @param action The function to be executed for each window and its index.
 * @throws IllegalArgumentException if windowSize or step is less than or
 *    equal to 0.
 *
 * Example usage:
 * ```
 * frames.forEachWindowIndexed(windowSize = 3, step = 2) { index, window ->
 *     println("Processing batch $index with ${window.size} frames")
 *     window.fastForEach { frame -> processFrame(frame) }
 * }
 * ```
 */
public inline fun <T> List<T>.forEachWindowIndexed(
    windowSize: Int,
    step: Int = 1,
    partialWindows: Boolean = false,
    action: (index: Int, InPlaceSubList<T>) -> Unit,
) {
    var index = 0
    forEachWindow(windowSize, step, partialWindows) {
        action(index++, it)
    }
}
