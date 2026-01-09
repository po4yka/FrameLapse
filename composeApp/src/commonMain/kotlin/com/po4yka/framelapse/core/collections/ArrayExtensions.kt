package com.po4yka.framelapse.core.collections

/**
 * Returns a [FloatArray] containing the results of applying the given
 * [transform] function to each element in the original array.
 *
 * This is more efficient than the stdlib `map` as it avoids boxing and
 * returns a primitive array.
 *
 * Example usage:
 * ```
 * val coordinates = floatArrayOf(1.0f, 2.0f, 3.0f)
 * val normalized = coordinates.mapArray { it / 3.0f }
 * ```
 */
public inline fun FloatArray.mapArray(transform: (Float) -> Float): FloatArray {
    return FloatArray(size) { index -> transform(this[index]) }
}

/**
 * Returns a [FloatArray] containing the results of applying the given
 * [transform] function to each element and its index in the original
 * array.
 *
 * Example usage:
 * ```
 * val values = floatArrayOf(1.0f, 2.0f, 3.0f)
 * val weighted = values.mapArrayIndexed { index, value -> value * (index + 1) }
 * ```
 */
public inline fun FloatArray.mapArrayIndexed(transform: (index: Int, Float) -> Float): FloatArray {
    return FloatArray(size) { index -> transform(index, this[index]) }
}

/**
 * Returns a [FloatList] containing the results of applying the given
 * [transform] function to each element in the original array.
 *
 * Example usage:
 * ```
 * val coordinates = floatArrayOf(1.0f, 2.0f, 3.0f)
 * val distances: FloatList = coordinates.map { it * 2.0f }
 * ```
 */
public inline fun FloatArray.map(transform: (Float) -> Float): FloatList {
    return buildFloatList(size) {
        this@map.forEach { add(transform(it)) }
    }
}

/**
 * Returns a [FloatArray] containing only elements matching the given
 * [predicate].
 *
 * This creates a new array containing only elements for which [predicate]
 * returns true. The resulting array may be smaller than the original.
 *
 * Example usage:
 * ```
 * val scores = floatArrayOf(0.1f, 0.5f, 0.8f, 0.3f, 0.9f)
 * val highScores = scores.filterArray { it >= 0.5f }
 * // Result: [0.5f, 0.8f, 0.9f]
 * ```
 */
public inline fun FloatArray.filterArray(predicate: (Float) -> Boolean): FloatArray {
    val result = FloatArray(size)
    var count = 0
    for (element in this) {
        if (predicate(element)) {
            result[count++] = element
        }
    }
    return result.copyOf(count)
}

/**
 * Returns a [FloatList] containing only elements matching the given
 * [predicate].
 *
 * Example usage:
 * ```
 * val scores = floatArrayOf(0.1f, 0.5f, 0.8f, 0.3f, 0.9f)
 * val highScores: FloatList = scores.filter { it >= 0.5f }
 * ```
 */
public inline fun FloatArray.filter(predicate: (Float) -> Boolean): FloatList {
    return buildFloatList {
        this@filter.forEach { if (predicate(it)) add(it) }
    }
}

/**
 * Returns the sum of all elements in the array.
 *
 * Example usage:
 * ```
 * val distances = floatArrayOf(1.5f, 2.0f, 3.5f)
 * val total = distances.sum() // 7.0f
 * ```
 */
public fun FloatArray.sum(): Float {
    var sum = 0f
    for (element in this) {
        sum += element
    }
    return sum
}

/**
 * Returns the average of all elements in the array, or [Float.NaN] if
 * empty.
 */
public fun FloatArray.average(): Float {
    if (isEmpty()) return Float.NaN
    return sum() / size
}

/**
 * Performs the given [action] on each element, providing the sequential
 * index. This is more efficient than using [withIndex] as it doesn't
 * create wrapper objects.
 *
 * Example usage:
 * ```
 * val values = floatArrayOf(1.0f, 2.0f, 3.0f)
 * values.forEachIndexed { index, value ->
 *     println("Index $index: $value")
 * }
 * ```
 */
public inline fun FloatArray.forEachIndexed(action: (index: Int, Float) -> Unit) {
    for (i in indices) {
        action(i, this[i])
    }
}

/**
 * Returns a [FloatArray] containing the first [n] elements.
 *
 * @throws IllegalArgumentException if [n] is negative.
 */
public fun FloatArray.take(n: Int): FloatArray {
    require(n >= 0) { "n must be >= 0" }
    if (n == 0) return FloatArray(0)
    if (n >= size) return copyOf()
    return copyOf(n)
}

/**
 * Returns a [FloatArray] containing all elements except the first [n]
 * elements.
 *
 * @throws IllegalArgumentException if [n] is negative.
 */
public fun FloatArray.drop(n: Int): FloatArray {
    require(n >= 0) { "n must be >= 0" }
    if (n == 0) return copyOf()
    if (n >= size) return FloatArray(0)
    return copyOfRange(n, size)
}
