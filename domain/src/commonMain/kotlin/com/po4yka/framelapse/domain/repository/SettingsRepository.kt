package com.po4yka.framelapse.domain.repository

import com.po4yka.framelapse.domain.util.Result

/**
 * Repository interface for app settings (key-value storage).
 */
interface SettingsRepository {

    /**
     * Gets a string value for the given key.
     *
     * @param key The settings key.
     * @return Result containing the value or null if not set.
     */
    suspend fun getString(key: String): Result<String?>

    /**
     * Sets a string value for the given key.
     *
     * @param key The settings key.
     * @param value The value to store.
     * @return Result indicating success or failure.
     */
    suspend fun setString(key: String, value: String): Result<Unit>

    /**
     * Gets an integer value for the given key.
     *
     * @param key The settings key.
     * @param default The default value if not set.
     * @return Result containing the value or default.
     */
    suspend fun getInt(key: String, default: Int = 0): Result<Int>

    /**
     * Sets an integer value for the given key.
     *
     * @param key The settings key.
     * @param value The value to store.
     * @return Result indicating success or failure.
     */
    suspend fun setInt(key: String, value: Int): Result<Unit>

    /**
     * Gets a boolean value for the given key.
     *
     * @param key The settings key.
     * @param default The default value if not set.
     * @return Result containing the value or default.
     */
    suspend fun getBoolean(key: String, default: Boolean = false): Result<Boolean>

    /**
     * Sets a boolean value for the given key.
     *
     * @param key The settings key.
     * @param value The value to store.
     * @return Result indicating success or failure.
     */
    suspend fun setBoolean(key: String, value: Boolean): Result<Unit>

    /**
     * Gets a float value for the given key.
     *
     * @param key The settings key.
     * @param default The default value if not set.
     * @return Result containing the value or default.
     */
    suspend fun getFloat(key: String, default: Float = 0f): Result<Float>

    /**
     * Sets a float value for the given key.
     *
     * @param key The settings key.
     * @param value The value to store.
     * @return Result indicating success or failure.
     */
    suspend fun setFloat(key: String, value: Float): Result<Unit>

    /**
     * Removes a setting by key.
     *
     * @param key The settings key.
     * @return Result indicating success or failure.
     */
    suspend fun remove(key: String): Result<Unit>

    /**
     * Checks if a key exists.
     *
     * @param key The settings key.
     * @return Result containing true if exists, false otherwise.
     */
    suspend fun exists(key: String): Result<Boolean>

    /**
     * Gets all settings as key-value pairs.
     *
     * @return Result containing a map of all settings.
     */
    suspend fun getAll(): Result<Map<String, String>>

    companion object {
        // Common settings keys
        const val KEY_LAST_PROJECT_ID = "last_project_id"
        const val KEY_DEFAULT_FPS = "default_fps"
        const val KEY_DEFAULT_RESOLUTION = "default_resolution"
        const val KEY_GHOST_OPACITY = "ghost_opacity"
        const val KEY_SHOW_GRID = "show_grid"
        const val KEY_FLASH_MODE = "flash_mode"
        const val KEY_CAMERA_FACING = "camera_facing"
        const val KEY_REMINDER_ENABLED = "reminder_enabled"
        const val KEY_REMINDER_HOUR = "reminder_hour"
        const val KEY_REMINDER_MINUTE = "reminder_minute"
    }
}
