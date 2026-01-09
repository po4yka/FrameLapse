package com.po4yka.framelapse.data.repository

import com.po4yka.framelapse.data.local.FrameLocalDataSource
import com.po4yka.framelapse.data.mapper.FrameMapper
import com.po4yka.framelapse.data.storage.ImageStorageManager
import com.po4yka.framelapse.domain.entity.Frame
import com.po4yka.framelapse.domain.entity.Landmarks
import com.po4yka.framelapse.domain.entity.StabilizationResult
import com.po4yka.framelapse.domain.repository.FrameRepository
import com.po4yka.framelapse.domain.util.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Implementation of FrameRepository using SQLDelight local data source.
 */
class FrameRepositoryImpl(
    private val localDataSource: FrameLocalDataSource,
    private val imageStorageManager: ImageStorageManager,
) : FrameRepository {

    override suspend fun addFrame(frame: Frame): Result<Frame> = try {
        val sortOrder = (localDataSource.getMaxSortOrder(frame.projectId) ?: -1) + 1
        val frameWithSortOrder = frame.copy(sortOrder = sortOrder.toInt())

        localDataSource.insert(FrameMapper.toInsertParams(frameWithSortOrder))
        Result.Success(frameWithSortOrder)
    } catch (e: Exception) {
        Result.Error(e, "Failed to add frame: ${e.message}")
    }

    override suspend fun getFrame(id: String): Result<Frame> = try {
        val entity = localDataSource.getById(id)
        if (entity != null) {
            Result.Success(FrameMapper.toDomain(entity))
        } else {
            Result.Error(
                NoSuchElementException("Frame not found: $id"),
                "Frame not found",
            )
        }
    } catch (e: Exception) {
        Result.Error(e, "Failed to get frame: ${e.message}")
    }

    override suspend fun getFramesByProject(projectId: String): Result<List<Frame>> = try {
        val entities = localDataSource.getByProject(projectId)
        Result.Success(entities.map { FrameMapper.toDomain(it) })
    } catch (e: Exception) {
        Result.Error(e, "Failed to get frames: ${e.message}")
    }

    override suspend fun getLatestFrame(projectId: String): Result<Frame?> = try {
        val entity = localDataSource.getLatestByProject(projectId)
        Result.Success(entity?.let { FrameMapper.toDomain(it) })
    } catch (e: Exception) {
        Result.Error(e, "Failed to get latest frame: ${e.message}")
    }

    override suspend fun getFramesByDateRange(
        projectId: String,
        startTimestamp: Long,
        endTimestamp: Long,
    ): Result<List<Frame>> = try {
        val entities = localDataSource.getByDateRange(projectId, startTimestamp, endTimestamp)
        Result.Success(entities.map { FrameMapper.toDomain(it) })
    } catch (e: Exception) {
        Result.Error(e, "Failed to get frames by date range: ${e.message}")
    }

    override suspend fun getFrameCount(projectId: String): Result<Long> = try {
        Result.Success(localDataSource.getCount(projectId))
    } catch (e: Exception) {
        Result.Error(e, "Failed to get frame count: ${e.message}")
    }

    override suspend fun getTotalFrameCount(): Result<Long> = try {
        Result.Success(localDataSource.getTotalCount())
    } catch (e: Exception) {
        Result.Error(e, "Failed to get total frame count: ${e.message}")
    }

    override suspend fun updateAlignedFrame(
        id: String,
        alignedPath: String,
        confidence: Float,
        landmarks: Landmarks,
        stabilizationResult: StabilizationResult?,
    ): Result<Unit> = try {
        val params = FrameMapper.toAlignedParams(id, alignedPath, confidence, landmarks, stabilizationResult)
        localDataSource.updateAligned(params)
        Result.Success(Unit)
    } catch (e: Exception) {
        Result.Error(e, "Failed to update aligned frame: ${e.message}")
    }

    override suspend fun updateSortOrder(id: String, sortOrder: Int): Result<Unit> = try {
        localDataSource.updateSortOrder(id, sortOrder.toLong())
        Result.Success(Unit)
    } catch (e: Exception) {
        Result.Error(e, "Failed to update sort order: ${e.message}")
    }

    override suspend fun deleteFrame(id: String): Result<Unit> = try {
        val frame = localDataSource.getById(id)
        localDataSource.delete(id)
        if (frame != null) {
            imageStorageManager.deleteImage(frame.originalPath)
            frame.alignedPath?.let { imageStorageManager.deleteImage(it) }
        }
        Result.Success(Unit)
    } catch (e: Exception) {
        Result.Error(e, "Failed to delete frame: ${e.message}")
    }

    override suspend fun deleteFramesByProject(projectId: String): Result<Unit> = try {
        localDataSource.deleteByProject(projectId)
        Result.Success(Unit)
    } catch (e: Exception) {
        Result.Error(e, "Failed to delete frames by project: ${e.message}")
    }

    override fun observeFrames(projectId: String): Flow<List<Frame>> =
        localDataSource.observeByProject(projectId).map { entities ->
            entities.map { FrameMapper.toDomain(it) }
        }
}
