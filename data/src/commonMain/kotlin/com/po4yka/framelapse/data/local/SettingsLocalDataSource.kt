package com.po4yka.framelapse.data.local

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Single

/**
 * Local data source for Settings database operations.
 * Provides type-safe key-value storage wrapping SQLDelight queries.
 */
@Single
class SettingsLocalDataSource(private val queries: SettingsQueries) {

    /**
     * Gets a string value for the given key.
     */
    suspend fun getString(key: String): String? = withContext(Dispatchers.IO) {
        queries.selectByKey(key).executeAsOneOrNull()
    }

    /**
     * Sets a string value for the given key.
     */
    suspend fun setString(key: String, value: String): Unit = withContext(Dispatchers.IO) {
        queries.insertOrReplace(key, value)
    }

    /**
     * Gets an integer value for the given key.
     */
    suspend fun getInt(key: String): Int? = withContext(Dispatchers.IO) {
        queries.selectByKey(key).executeAsOneOrNull()?.toIntOrNull()
    }

    /**
     * Sets an integer value for the given key.
     */
    suspend fun setInt(key: String, value: Int): Unit = withContext(Dispatchers.IO) {
        queries.insertOrReplace(key, value.toString())
    }

    /**
     * Gets a boolean value for the given key.
     */
    suspend fun getBoolean(key: String): Boolean? = withContext(Dispatchers.IO) {
        queries.selectByKey(key).executeAsOneOrNull()?.toBooleanStrictOrNull()
    }

    /**
     * Sets a boolean value for the given key.
     */
    suspend fun setBoolean(key: String, value: Boolean): Unit = withContext(Dispatchers.IO) {
        queries.insertOrReplace(key, value.toString())
    }

    /**
     * Gets a float value for the given key.
     */
    suspend fun getFloat(key: String): Float? = withContext(Dispatchers.IO) {
        queries.selectByKey(key).executeAsOneOrNull()?.toFloatOrNull()
    }

    /**
     * Sets a float value for the given key.
     */
    suspend fun setFloat(key: String, value: Float): Unit = withContext(Dispatchers.IO) {
        queries.insertOrReplace(key, value.toString())
    }

    /**
     * Removes a setting by key.
     */
    suspend fun remove(key: String): Unit = withContext(Dispatchers.IO) {
        queries.delete(key)
    }

    /**
     * Checks if a key exists.
     */
    suspend fun exists(key: String): Boolean = withContext(Dispatchers.IO) {
        queries.exists(key).executeAsOne()
    }

    /**
     * Gets all settings as key-value pairs.
     */
    suspend fun getAll(): Map<String, String> = withContext(Dispatchers.IO) {
        queries.selectAll().executeAsList().associate { it.key to it.value_ }
    }
}
