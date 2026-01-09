@file:Suppress("NOTHING_TO_INLINE")
@file:OptIn(kotlin.contracts.ExperimentalContracts::class)

package com.po4yka.framelapse.core.collections

import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

/**
 * A box-free collection for [Float] values. It allows storing and
 * retrieving float values without boxing overhead.
 *
 * This implementation is not thread-safe: if multiple threads access this
 * container concurrently, and one or more threads modify the structure of
 * the list, the calling code must provide appropriate synchronization.
 */
public sealed class FloatList(initialCapacity: Int) {
    @PublishedApi
    internal var content: FloatArray = if (initialCapacity == 0) {
        EmptyFloatArray
    } else {
        FloatArray(initialCapacity)
    }

    @PublishedApi
    @Suppress("ktlint:standard:backing-property-naming")
    internal var _size: Int = 0

    /** The number of elements in the [FloatList]. */
    public inline val size: Int
        get() = _size

    /**
     * Returns the last valid index in the [FloatList]. This can be `-1` when
     * the list is empty.
     */
    public inline val lastIndex: Int get() = _size - 1

    /** Returns an [IntRange] of the valid indices for this [FloatList]. */
    public inline val indices: IntRange get() = 0 until _size

    /** Returns `true` if the collection has no elements in it. */
    public inline fun isEmpty(): Boolean = _size == 0

    /**
     * Returns `true` if there are elements in the [FloatList] or `false` if it
     * is empty.
     */
    public inline fun isNotEmpty(): Boolean = _size != 0

    /**
     * Returns `true` if any of the elements give a `true` return value for
     * [predicate].
     */
    public inline fun any(predicate: (element: Float) -> Boolean): Boolean {
        contract { callsInPlace(predicate) }
        forEach {
            if (predicate(it)) {
                return true
            }
        }
        return false
    }

    /** Returns `true` if all elements match the given [predicate]. */
    public inline fun all(predicate: (element: Float) -> Boolean): Boolean {
        contract { callsInPlace(predicate) }
        forEach { if (!predicate(it)) return false }
        return true
    }

    /**
     * Returns `true` if the [FloatList] contains [element] or `false`
     * otherwise.
     */
    public operator fun contains(element: Float): Boolean {
        forEach {
            if (it == element) {
                return true
            }
        }
        return false
    }

    /** Returns the number of elements in this list. */
    public inline fun count(): Int = _size

    /** Counts the number of elements matching [predicate]. */
    public inline fun count(predicate: (element: Float) -> Boolean): Int {
        contract { callsInPlace(predicate) }
        var count = 0
        forEach { if (predicate(it)) count++ }
        return count
    }

    /**
     * Returns the first element in the [FloatList] or throws a
     * [NoSuchElementException] if it [isEmpty].
     */
    public fun first(): Float {
        if (isEmpty()) {
            throw NoSuchElementException("FloatList is empty.")
        }
        return content[0]
    }

    /**
     * Returns the first element in the [FloatList] for which [predicate]
     * returns `true` or throws [NoSuchElementException] if nothing matches.
     */
    public inline fun first(predicate: (element: Float) -> Boolean): Float {
        contract { callsInPlace(predicate) }
        forEach { item ->
            if (predicate(item)) return item
        }
        throw NoSuchElementException("FloatList contains no element matching the predicate.")
    }

    /**
     * Returns the last element in the [FloatList] or throws a
     * [NoSuchElementException] if it [isEmpty].
     */
    public fun last(): Float {
        if (isEmpty()) {
            throw NoSuchElementException("FloatList is empty.")
        }
        return content[lastIndex]
    }

    /**
     * Accumulates values, starting with [initial], and applying [operation] to
     * each element in the [FloatList] in order.
     */
    public inline fun <R> fold(initial: R, operation: (acc: R, element: Float) -> R): R {
        contract { callsInPlace(operation) }
        var acc = initial
        forEach { item ->
            acc = operation(acc, item)
        }
        return acc
    }

    /** Calls [block] for each element in the [FloatList], in order. */
    public inline fun forEach(block: (element: Float) -> Unit) {
        contract { callsInPlace(block) }
        val content = content
        for (i in 0 until _size) {
            block(content[i])
        }
    }

    /**
     * Calls [block] for each element in the [FloatList] along with its index,
     * in order.
     */
    public inline fun forEachIndexed(block: (index: Int, element: Float) -> Unit) {
        contract { callsInPlace(block) }
        val content = content
        for (i in 0 until _size) {
            block(i, content[i])
        }
    }

    /** Calls [block] for each element in the [FloatList] in reverse order. */
    public inline fun forEachReversed(block: (element: Float) -> Unit) {
        contract { callsInPlace(block) }
        val content = content
        for (i in _size - 1 downTo 0) {
            block(content[i])
        }
    }

    /**
     * Returns the element at the given [index] or throws
     * [IndexOutOfBoundsException] if the [index] is out of bounds of this
     * collection.
     */
    public operator fun get(index: Int): Float {
        if (index !in 0 until _size) {
            throw IndexOutOfBoundsException("Index $index out of bounds for size $_size")
        }
        return content[index]
    }

    /**
     * Returns the element at the given [index] or [defaultValue] if [index] is
     * out of bounds.
     */
    public inline fun getOrElse(index: Int, defaultValue: (index: Int) -> Float): Float {
        if (index !in 0 until _size) {
            return defaultValue(index)
        }
        return content[index]
    }

    /**
     * Returns the index of [element] in the [FloatList] or `-1` if [element]
     * is not there.
     */
    public fun indexOf(element: Float): Int {
        forEachIndexed { i, item ->
            if (element == item) {
                return i
            }
        }
        return -1
    }

    /**
     * Returns the index of the first element for which [predicate] returns
     * `true`, or `-1` if no element matches.
     */
    public inline fun indexOfFirst(predicate: (element: Float) -> Boolean): Int {
        contract { callsInPlace(predicate) }
        forEachIndexed { i, item ->
            if (predicate(item)) {
                return i
            }
        }
        return -1
    }

    /** Returns the sum of all elements in the list. */
    public fun sum(): Float {
        var sum = 0f
        forEach { sum += it }
        return sum
    }

    /**
     * Returns the average of all elements in the list, or [Float.NaN] if
     * empty.
     */
    public fun average(): Float {
        if (isEmpty()) return Float.NaN
        return sum() / _size
    }

    /** Returns the minimum element in the list, or [Float.NaN] if empty. */
    public fun min(): Float {
        if (isEmpty()) return Float.NaN
        var min = content[0]
        for (i in 1 until _size) {
            val value = content[i]
            if (value < min) min = value
        }
        return min
    }

    /** Returns the maximum element in the list, or [Float.NaN] if empty. */
    public fun max(): Float {
        if (isEmpty()) return Float.NaN
        var max = content[0]
        for (i in 1 until _size) {
            val value = content[i]
            if (value > max) max = value
        }
        return max
    }

    /** Returns a list containing only elements matching the given [predicate]. */
    public inline fun filter(predicate: (Float) -> Boolean): FloatList {
        contract { callsInPlace(predicate) }
        val result = MutableFloatList()
        forEach { element ->
            if (predicate(element)) result.add(element)
        }
        return result
    }

    /**
     * Returns a new [FloatList] with the results of applying the given
     * [transform] to each element.
     */
    public inline fun map(transform: (Float) -> Float): FloatList {
        contract { callsInPlace(transform) }
        return MutableFloatList(_size).also { list ->
            forEach { element ->
                list.add(transform(element))
            }
        }
    }

    /** Returns a new list that is a copy of the current list. */
    public fun copy(): FloatList = MutableFloatList(_size).also {
        content.copyInto(it.content, startIndex = 0, endIndex = _size)
        it._size = _size
    }

    /**
     * Returns a [FloatArray] containing all elements from this list in the
     * same order.
     */
    public fun toFloatArray(): FloatArray = content.copyOf(_size)

    /** Returns a [List] containing all elements (boxed). */
    public fun toList(): List<Float> = List(_size) { content[it] }

    /** Creates a String from the elements separated by [separator]. */
    public fun joinToString(
        separator: CharSequence = ", ",
        prefix: CharSequence = "",
        postfix: CharSequence = "",
    ): String = buildString {
        append(prefix)
        this@FloatList.forEachIndexed { index, element ->
            if (index != 0) append(separator)
            append(element)
        }
        append(postfix)
    }

    override fun hashCode(): Int {
        var hashCode = 0
        forEach { element ->
            hashCode += 31 * element.hashCode()
        }
        return hashCode
    }

    override fun equals(other: Any?): Boolean {
        if (other !is FloatList || other._size != _size) {
            return false
        }
        val content = content
        val otherContent = other.content
        for (i in indices) {
            if (content[i] != otherContent[i]) {
                return false
            }
        }
        return true
    }

    override fun toString(): String = joinToString(prefix = "[", postfix = "]")
}

/**
 * [MutableFloatList] is a [MutableList]-like collection for [Float]
 * values. It allows storing and retrieving the elements without boxing.
 */
public class MutableFloatList(initialCapacity: Int = 16) : FloatList(initialCapacity) {

    /**
     * Returns the total number of elements that can be held before the
     * [MutableFloatList] must grow.
     */
    public inline val capacity: Int
        get() = content.size

    /** Adds [element] to the [MutableFloatList] and returns `true`. */
    public fun add(element: Float): Boolean {
        ensureCapacity(_size + 1)
        content[_size] = element
        _size++
        return true
    }

    /** Adds [element] to the [MutableFloatList] at the given [index]. */
    public fun add(index: Int, element: Float) {
        if (index !in 0.._size) {
            throw IndexOutOfBoundsException("Index $index out of bounds for size $_size")
        }
        ensureCapacity(_size + 1)
        val content = content
        if (index != _size) {
            content.copyInto(
                destination = content,
                destinationOffset = index + 1,
                startIndex = index,
                endIndex = _size,
            )
        }
        content[index] = element
        _size++
    }

    /** Adds all [elements] to the end of the [MutableFloatList]. */
    public fun addAll(elements: FloatArray): Boolean {
        if (elements.isEmpty()) return false
        ensureCapacity(_size + elements.size)
        elements.copyInto(content, _size)
        _size += elements.size
        return true
    }

    /** Adds all [elements] to the end of the [MutableFloatList]. */
    public fun addAll(elements: FloatList): Boolean {
        if (elements.isEmpty()) return false
        ensureCapacity(_size + elements._size)
        elements.content.copyInto(
            destination = content,
            destinationOffset = _size,
            startIndex = 0,
            endIndex = elements._size,
        )
        _size += elements._size
        return true
    }

    /** [add] [element] to the [MutableFloatList]. */
    public inline operator fun plusAssign(element: Float) {
        add(element)
    }

    /**
     * Removes all elements in the [MutableFloatList]. The storage isn't
     * released.
     */
    public fun clear() {
        _size = 0
    }

    /**
     * Removes [element] from the [MutableFloatList]. Returns `true` if element
     * was found and removed.
     */
    public fun remove(element: Float): Boolean {
        val index = indexOf(element)
        if (index >= 0) {
            removeAt(index)
            return true
        }
        return false
    }

    /** Removes the element at the given [index] and returns it. */
    public fun removeAt(index: Int): Float {
        if (index !in 0 until _size) {
            throw IndexOutOfBoundsException("Index $index out of bounds for size $_size")
        }
        val content = content
        val item = content[index]
        if (index != lastIndex) {
            content.copyInto(
                destination = content,
                destinationOffset = index,
                startIndex = index + 1,
                endIndex = _size,
            )
        }
        _size--
        return item
    }

    /** Sets the value at [index] to [element]. */
    public operator fun set(index: Int, element: Float): Float {
        if (index !in 0 until _size) {
            throw IndexOutOfBoundsException("Index $index out of bounds for size $_size")
        }
        val content = content
        val old = content[index]
        content[index] = element
        return old
    }

    /** Ensures that there is enough space to store [capacity] elements. */
    public fun ensureCapacity(capacity: Int) {
        val oldContent = content
        if (oldContent.size < capacity) {
            val newSize = maxOf(capacity, oldContent.size * 3 / 2)
            content = oldContent.copyOf(newSize)
        }
    }

    /** Reduces the internal storage to match [size]. */
    public fun trim() {
        if (content.size > _size) {
            content = content.copyOf(_size)
        }
    }
}

private val EmptyFloatArray = FloatArray(0)
private val EmptyFloatList: FloatList = MutableFloatList(0)

/** Returns an empty read-only [FloatList]. */
public fun emptyFloatList(): FloatList = EmptyFloatList

/** Returns a read-only [FloatList] with nothing in it. */
public fun floatListOf(): FloatList = EmptyFloatList

/** Returns a new read-only [FloatList] with the given elements. */
public fun floatListOf(vararg elements: Float): FloatList = if (elements.isEmpty()) {
    EmptyFloatList
} else {
    MutableFloatList(elements.size).apply { addAll(elements) }
}

/** Returns a new empty [MutableFloatList] with the default capacity. */
public fun mutableFloatListOf(): MutableFloatList = MutableFloatList()

/** Returns a new [MutableFloatList] with the given elements. */
public fun mutableFloatListOf(vararg elements: Float): MutableFloatList =
    MutableFloatList(elements.size).apply { addAll(elements) }

/**
 * Builds a new [FloatList] by populating a [MutableFloatList] using the
 * given [builderAction].
 */
public inline fun buildFloatList(builderAction: MutableFloatList.() -> Unit): FloatList {
    contract { callsInPlace(builderAction, InvocationKind.EXACTLY_ONCE) }
    return MutableFloatList().apply(builderAction)
}

/**
 * Builds a new [FloatList] with the given [initialCapacity] by populating
 * a [MutableFloatList] using the given [builderAction].
 */
public inline fun buildFloatList(initialCapacity: Int, builderAction: MutableFloatList.() -> Unit): FloatList {
    contract { callsInPlace(builderAction, InvocationKind.EXACTLY_ONCE) }
    return MutableFloatList(initialCapacity).apply(builderAction)
}
