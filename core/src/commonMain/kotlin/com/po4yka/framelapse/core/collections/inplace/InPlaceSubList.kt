package com.po4yka.framelapse.core.collections.inplace

/**
 * A sublist view of a [sourceList] that provides access to a portion of
 * the list within the specified range ([start], [end]). Modifications to
 * the original list are reflected in the sublist.
 *
 * This class does not modify the original list and only provides read-only
 * access to the sublist. The [start] and [end] properties are mutable
 * to allow reusing the same instance for different ranges, enabling
 * zero-allocation iteration patterns.
 */
public class InPlaceSubList<T> @PublishedApi internal constructor(
    /** The source list from which this sublist was created. */
    @PublishedApi internal val sourceList: List<T>,
    /**
     * The start index (inclusive) within the [sourceList] from which this
     * sublist starts.
     */
    @PublishedApi internal var start: Int,
    /**
     * The end index (exclusive) within the [sourceList] at which this sublist
     * ends.
     */
    @PublishedApi internal var end: Int,
) : AbstractList<T>() {

    init {
        require(start >= 0) { "Start index must be greater than or equal to 0" }
        require(end <= sourceList.size) { "End index must be less than or equal to the size of the source list" }
        require(start <= end) { "Start index must be less than or equal to end index" }
    }

    /** Returns the size of this sublist. */
    override val size: Int
        get() = end - start

    /**
     * Returns the element at the specified [index] in this sublist.
     *
     * [index] is relative to this sublist and not the [sourceList].
     *
     * @throws IndexOutOfBoundsException if [index] is out of range for this
     *    sublist.
     */
    override fun get(index: Int): T {
        if (index < 0 || index >= size) {
            throw IndexOutOfBoundsException("Index $index out of bounds for size $size")
        }
        return sourceList[start + index]
    }

    /**
     * Performs the given [action] for each element in this sublist.
     *
     * This method is faster and more efficient than [List.forEach] as it uses
     * direct index access without iterator allocation.
     *
     * @param action The function to be executed for each element.
     */
    public inline fun fastForEach(action: (T) -> Unit) {
        for (i in start until end) {
            action(sourceList[i])
        }
    }

    /**
     * Performs the given [action] for each element in this sublist, providing
     * its index relative to this sublist.
     *
     * This method is faster and more efficient than [List.forEachIndexed] as
     * it uses direct index access without iterator allocation.
     *
     * @param action The function to be executed for each element with its
     *    index.
     */
    public inline fun fastForEachIndexed(action: (index: Int, T) -> Unit) {
        for (i in start until end) {
            action(i - start, sourceList[i])
        }
    }
}
