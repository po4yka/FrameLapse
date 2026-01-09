package com.po4yka.framelapse.data.repository

import com.po4yka.framelapse.data.local.SettingsLocalDataSource
import com.po4yka.framelapse.domain.repository.SettingsRepository
import com.po4yka.framelapse.domain.util.Result

/**
 * Implementation of SettingsRepository using SQLDelight local data source.
 */
class SettingsRepositoryImpl(private val localDataSource: SettingsLocalDataSource) : SettingsRepository {

    override suspend fun getString(key: String): Result<String?> = safeCall("Failed to get string setting") {
        localDataSource.getString(key)
    }

    override suspend fun setString(key: String, value: String): Result<Unit> =
        safeCall("Failed to set string setting") {
            localDataSource.setString(key, value)
        }

    override suspend fun getInt(key: String, default: Int): Result<Int> = safeCall("Failed to get int setting") {
        localDataSource.getInt(key) ?: default
    }

    override suspend fun setInt(key: String, value: Int): Result<Unit> = safeCall("Failed to set int setting") {
        localDataSource.setInt(key, value)
    }

    override suspend fun getBoolean(key: String, default: Boolean): Result<Boolean> =
        safeCall("Failed to get boolean setting") {
            localDataSource.getBoolean(key) ?: default
        }

    override suspend fun setBoolean(key: String, value: Boolean): Result<Unit> =
        safeCall("Failed to set boolean setting") {
            localDataSource.setBoolean(key, value)
        }

    override suspend fun getFloat(key: String, default: Float): Result<Float> =
        safeCall("Failed to get float setting") {
            localDataSource.getFloat(key) ?: default
        }

    override suspend fun setFloat(key: String, value: Float): Result<Unit> = safeCall("Failed to set float setting") {
        localDataSource.setFloat(key, value)
    }

    override suspend fun remove(key: String): Result<Unit> = safeCall("Failed to remove setting") {
        localDataSource.remove(key)
    }

    override suspend fun exists(key: String): Result<Boolean> = safeCall("Failed to check setting existence") {
        localDataSource.exists(key)
    }

    override suspend fun getAll(): Result<Map<String, String>> = safeCall("Failed to get all settings") {
        localDataSource.getAll()
    }
}
