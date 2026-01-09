package com.po4yka.framelapse.core.collections.inplace

/**
 * Iterates through the chunks of the list and performs the given action on
 * each chunk.
 *
 * This is a zero-allocation operation - the same [InPlaceSubList] instance
 * is reused for each chunk, with only its start and end indices updated.
 * The last chunk may have fewer elements than the given [chunkSize].
 *
 * @param chunkSize The number of elements to take in each chunk. Must be
 *    greater than 0.
 * @param action The function to be executed for each chunk.
 * @throws IllegalArgumentException if chunkSize is less than or equal
 *    to 0.
 *
 * Example usage:
 * ```
 * val frameIds = listOf("f1", "f2", "f3", "f4", "f5", "f6", "f7")
 *
 * // Process in batches of 3
 * frameIds.forEachChunk(chunkSize = 3) { chunk ->
 *     chunk.fastForEach { frameId -> processFrame(frameId) }
 * }
 * // Processes: [f1, f2, f3], [f4, f5, f6], [f7]
 * ```
 */
public inline fun <T> List<T>.forEachChunk(chunkSize: Int, action: (InPlaceSubList<T>) -> Unit) {
    forEachWindow(chunkSize, chunkSize, partialWindows = true, action)
}

/**
 * Iterates through the chunks of the list with their indices and performs
 * the given action on each chunk.
 *
 * This is a zero-allocation operation - the same [InPlaceSubList] instance
 * is reused for each chunk, with only its start and end indices updated.
 * The last chunk may have fewer elements than the given [chunkSize].
 *
 * @param chunkSize The number of elements to take in each chunk. Must be
 *    greater than 0.
 * @param action The function to be executed for each chunk and its index.
 * @throws IllegalArgumentException if chunkSize is less than or equal
 *    to 0.
 *
 * Example usage:
 * ```
 * val photos = listOf(photo1, photo2, photo3, photo4, photo5)
 *
 * // Process in batches of 2 with progress reporting
 * photos.forEachChunkIndexed(chunkSize = 2) { batchIndex, chunk ->
 *     println("Processing batch ${batchIndex + 1}")
 *     chunk.fastForEach { photo -> importPhoto(photo) }
 * }
 * ```
 */
public inline fun <T> List<T>.forEachChunkIndexed(chunkSize: Int, action: (index: Int, InPlaceSubList<T>) -> Unit) {
    var index = 0
    forEachChunk(chunkSize) { chunk ->
        action(index++, chunk)
    }
}
