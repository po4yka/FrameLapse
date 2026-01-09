package com.po4yka.framelapse.data.repository

import com.po4yka.framelapse.data.local.SettingsLocalDataSource
import com.po4yka.framelapse.domain.repository.SettingsRepository
import com.po4yka.framelapse.domain.util.Result

/**
 * Implementation of SettingsRepository using SQLDelight local data source.
 */
class SettingsRepositoryImpl(private val localDataSource: SettingsLocalDataSource) : SettingsRepository {

    override suspend fun getString(key: String): Result<String?> = try {
        Result.Success(localDataSource.getString(key))
    } catch (e: Exception) {
        Result.Error(e, "Failed to get string setting: ${e.message}")
    }

    override suspend fun setString(key: String, value: String): Result<Unit> = try {
        localDataSource.setString(key, value)
        Result.Success(Unit)
    } catch (e: Exception) {
        Result.Error(e, "Failed to set string setting: ${e.message}")
    }

    override suspend fun getInt(key: String, default: Int): Result<Int> = try {
        Result.Success(localDataSource.getInt(key) ?: default)
    } catch (e: Exception) {
        Result.Error(e, "Failed to get int setting: ${e.message}")
    }

    override suspend fun setInt(key: String, value: Int): Result<Unit> = try {
        localDataSource.setInt(key, value)
        Result.Success(Unit)
    } catch (e: Exception) {
        Result.Error(e, "Failed to set int setting: ${e.message}")
    }

    override suspend fun getBoolean(key: String, default: Boolean): Result<Boolean> = try {
        Result.Success(localDataSource.getBoolean(key) ?: default)
    } catch (e: Exception) {
        Result.Error(e, "Failed to get boolean setting: ${e.message}")
    }

    override suspend fun setBoolean(key: String, value: Boolean): Result<Unit> = try {
        localDataSource.setBoolean(key, value)
        Result.Success(Unit)
    } catch (e: Exception) {
        Result.Error(e, "Failed to set boolean setting: ${e.message}")
    }

    override suspend fun getFloat(key: String, default: Float): Result<Float> = try {
        Result.Success(localDataSource.getFloat(key) ?: default)
    } catch (e: Exception) {
        Result.Error(e, "Failed to get float setting: ${e.message}")
    }

    override suspend fun setFloat(key: String, value: Float): Result<Unit> = try {
        localDataSource.setFloat(key, value)
        Result.Success(Unit)
    } catch (e: Exception) {
        Result.Error(e, "Failed to set float setting: ${e.message}")
    }

    override suspend fun remove(key: String): Result<Unit> = try {
        localDataSource.remove(key)
        Result.Success(Unit)
    } catch (e: Exception) {
        Result.Error(e, "Failed to remove setting: ${e.message}")
    }

    override suspend fun exists(key: String): Result<Boolean> = try {
        Result.Success(localDataSource.exists(key))
    } catch (e: Exception) {
        Result.Error(e, "Failed to check setting existence: ${e.message}")
    }

    override suspend fun getAll(): Result<Map<String, String>> = try {
        Result.Success(localDataSource.getAll())
    } catch (e: Exception) {
        Result.Error(e, "Failed to get all settings: ${e.message}")
    }
}
