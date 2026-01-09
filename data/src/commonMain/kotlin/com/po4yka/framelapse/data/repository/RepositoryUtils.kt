package com.po4yka.framelapse.data.repository

import com.po4yka.framelapse.domain.util.Result

/**
 * Executes a suspend block and wraps the result in a [Result].
 *
 * This helper reduces boilerplate in repository implementations by handling
 * try-catch wrapping consistently.
 *
 * @param errorMessage The error message prefix to use if an exception is thrown.
 * @param block The suspend block to execute.
 * @return [Result.Success] with the block's result, or [Result.Error] if an exception occurs.
 */
suspend inline fun <T> safeCall(errorMessage: String, crossinline block: suspend () -> T): Result<T> = try {
    Result.Success(block())
} catch (e: Exception) {
    Result.Error(e, "$errorMessage: ${e.message}")
}
