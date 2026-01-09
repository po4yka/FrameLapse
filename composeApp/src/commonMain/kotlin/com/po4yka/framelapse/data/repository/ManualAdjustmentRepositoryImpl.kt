package com.po4yka.framelapse.data.repository

import com.po4yka.framelapse.data.local.ManualAdjustmentLocalDataSource
import com.po4yka.framelapse.data.mapper.ManualAdjustmentSerializer
import com.po4yka.framelapse.domain.entity.ContentType
import com.po4yka.framelapse.domain.entity.ManualAdjustment
import com.po4yka.framelapse.domain.repository.ManualAdjustmentRepository
import com.po4yka.framelapse.domain.util.Result
import com.po4yka.framelapse.platform.currentTimeMillis
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import com.po4yka.framelapse.data.local.ManualAdjustment as DbManualAdjustment

/**
 * Implementation of ManualAdjustmentRepository using SQLDelight local data source.
 */
class ManualAdjustmentRepositoryImpl(private val localDataSource: ManualAdjustmentLocalDataSource) :
    ManualAdjustmentRepository {

    override suspend fun getAdjustment(frameId: String): Result<ManualAdjustment?> = try {
        val entity = localDataSource.getByFrameId(frameId)
        Result.Success(entity?.let { mapToDomain(it) })
    } catch (e: Exception) {
        Result.Error(e, "Failed to get adjustment: ${e.message}")
    }

    override suspend fun getAdjustmentsByProject(projectId: String): Result<List<Pair<String, ManualAdjustment>>> =
        try {
            val entities = localDataSource.getByProject(projectId)
            val pairs = entities.mapNotNull { entity ->
                mapToDomain(entity)?.let { entity.frameId to it }
            }
            Result.Success(pairs)
        } catch (e: Exception) {
            Result.Error(e, "Failed to get adjustments by project: ${e.message}")
        }

    override suspend fun getActiveAdjustmentsByProject(
        projectId: String,
    ): Result<List<Pair<String, ManualAdjustment>>> = try {
        val entities = localDataSource.getActiveByProject(projectId)
        val pairs = entities.mapNotNull { entity ->
            mapToDomain(entity)?.let { entity.frameId to it }
        }
        Result.Success(pairs)
    } catch (e: Exception) {
        Result.Error(e, "Failed to get active adjustments: ${e.message}")
    }

    override suspend fun getFrameIdsWithAdjustments(projectId: String): Result<List<String>> = try {
        Result.Success(localDataSource.getFrameIdsWithAdjustments(projectId))
    } catch (e: Exception) {
        Result.Error(e, "Failed to get frame IDs with adjustments: ${e.message}")
    }

    override suspend fun countActiveAdjustments(projectId: String): Result<Long> = try {
        Result.Success(localDataSource.countActiveByProject(projectId))
    } catch (e: Exception) {
        Result.Error(e, "Failed to count active adjustments: ${e.message}")
    }

    override suspend fun hasAdjustment(frameId: String): Result<Boolean> = try {
        Result.Success(localDataSource.existsByFrameId(frameId))
    } catch (e: Exception) {
        Result.Error(e, "Failed to check adjustment existence: ${e.message}")
    }

    override suspend fun saveAdjustment(
        frameId: String,
        contentType: ContentType,
        adjustment: ManualAdjustment,
    ): Result<Unit> = try {
        val now = currentTimeMillis()
        val adjustmentJson = ManualAdjustmentSerializer.serialize(adjustment)

        localDataSource.insertOrReplace(
            id = adjustment.id,
            frameId = frameId,
            contentType = contentType.name,
            adjustmentJson = adjustmentJson,
            isActive = adjustment.isActive,
            createdAt = now,
            updatedAt = now,
        )
        Result.Success(Unit)
    } catch (e: Exception) {
        Result.Error(e, "Failed to save adjustment: ${e.message}")
    }

    override suspend fun toggleActive(frameId: String, isActive: Boolean): Result<Unit> = try {
        localDataSource.toggleActive(frameId, isActive, currentTimeMillis())
        Result.Success(Unit)
    } catch (e: Exception) {
        Result.Error(e, "Failed to toggle active state: ${e.message}")
    }

    override suspend fun activate(frameId: String): Result<Unit> = try {
        localDataSource.activate(frameId, currentTimeMillis())
        Result.Success(Unit)
    } catch (e: Exception) {
        Result.Error(e, "Failed to activate adjustment: ${e.message}")
    }

    override suspend fun deactivate(frameId: String): Result<Unit> = try {
        localDataSource.deactivate(frameId, currentTimeMillis())
        Result.Success(Unit)
    } catch (e: Exception) {
        Result.Error(e, "Failed to deactivate adjustment: ${e.message}")
    }

    override suspend fun deleteAdjustment(frameId: String): Result<Unit> = try {
        localDataSource.deleteByFrameId(frameId)
        Result.Success(Unit)
    } catch (e: Exception) {
        Result.Error(e, "Failed to delete adjustment: ${e.message}")
    }

    override suspend fun deleteAdjustmentsByProject(projectId: String): Result<Unit> = try {
        localDataSource.deleteByProject(projectId)
        Result.Success(Unit)
    } catch (e: Exception) {
        Result.Error(e, "Failed to delete adjustments by project: ${e.message}")
    }

    override fun observeAdjustment(frameId: String): Flow<ManualAdjustment?> =
        localDataSource.observeByFrameId(frameId).map { entity ->
            entity?.let { mapToDomain(it) }
        }

    override fun observeActiveAdjustments(projectId: String): Flow<List<Pair<String, ManualAdjustment>>> =
        localDataSource.observeActiveByProject(projectId).map { entities ->
            entities.mapNotNull { entity ->
                mapToDomain(entity)?.let { entity.frameId to it }
            }
        }

    /**
     * Maps a database entity to a domain model.
     */
    private fun mapToDomain(entity: DbManualAdjustment): ManualAdjustment? =
        ManualAdjustmentSerializer.deserialize(entity.adjustmentJson)
}
