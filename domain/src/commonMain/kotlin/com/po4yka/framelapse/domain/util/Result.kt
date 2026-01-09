package com.po4yka.framelapse.domain.util

/**
 * A sealed class representing either a successful result with data,
 * an error with an exception, or a loading state.
 */
sealed class Result<out T> {
    data class Success<out T>(val data: T) : Result<T>()
    data class Error(val exception: Throwable, val message: String? = null) : Result<Nothing>()
    data object Loading : Result<Nothing>()

    val isSuccess: Boolean get() = this is Success
    val isError: Boolean get() = this is Error
    val isLoading: Boolean get() = this is Loading

    /**
     * Returns the encapsulated data if this is a Success, null otherwise.
     */
    fun getOrNull(): T? = when (this) {
        is Success -> data
        is Error -> null
        is Loading -> null
    }

    /**
     * Returns the encapsulated exception if this is an Error, null otherwise.
     */
    fun exceptionOrNull(): Throwable? = when (this) {
        is Success -> null
        is Error -> exception
        is Loading -> null
    }

    /**
     * Returns the encapsulated data if this is a Success, or throws the exception if it's an Error.
     */
    fun getOrThrow(): T = when (this) {
        is Success -> data
        is Error -> throw exception
        is Loading -> throw IllegalStateException("Result is still loading")
    }

    /**
     * Transforms the encapsulated data using the given function.
     */
    inline fun <R> map(transform: (T) -> R): Result<R> = when (this) {
        is Success -> Success(transform(data))
        is Error -> this
        is Loading -> Loading
    }

    /**
     * Transforms the encapsulated data using the given function that returns a Result.
     */
    inline fun <R> flatMap(transform: (T) -> Result<R>): Result<R> = when (this) {
        is Success -> transform(data)
        is Error -> this
        is Loading -> Loading
    }

    /**
     * Performs the given action if this is a Success.
     */
    inline fun onSuccess(action: (T) -> Unit): Result<T> {
        if (this is Success) action(data)
        return this
    }

    /**
     * Performs the given action if this is an Error.
     */
    inline fun onError(action: (Throwable, String?) -> Unit): Result<T> {
        if (this is Error) action(exception, message)
        return this
    }

    /**
     * Performs the given action if this is Loading.
     */
    inline fun onLoading(action: () -> Unit): Result<T> {
        if (this is Loading) action()
        return this
    }
}

/**
 * Returns the encapsulated data if this is a Success, or the default value otherwise.
 * This is an extension function to avoid variance issues with the 'out' type parameter.
 */
fun <T> Result<T>.getOrDefault(defaultValue: T): T = when (this) {
    is Result.Success -> data
    is Result.Error -> defaultValue
    is Result.Loading -> defaultValue
}

/**
 * Wraps the given block in a try-catch and returns a Result.
 */
inline fun <T> runCatching(block: () -> T): Result<T> = try {
    Result.Success(block())
} catch (e: Exception) {
    Result.Error(e, e.message)
}

/**
 * Wraps the given suspend block in a try-catch and returns a Result.
 */
suspend inline fun <T> runCatchingSuspend(crossinline block: suspend () -> T): Result<T> = try {
    Result.Success(block())
} catch (e: Exception) {
    Result.Error(e, e.message)
}
