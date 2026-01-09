---
name: caching-strategies
description: Implements caching patterns for repositories including in-memory cache, stale-while-revalidate, and cache invalidation. Use when adding caching to reduce database/network calls.
---

# Caching Strategies

## Overview

This skill helps implement caching patterns for repositories to reduce database queries and improve performance. It covers in-memory caching, cache invalidation, and reactive cache patterns.

## 1. Simple In-Memory Cache

### Basic Cache Pattern

```kotlin
class CachedProjectRepository(
    private val localDataSource: ProjectLocalDataSource,
) : ProjectRepository {

    private val cache = mutableMapOf<String, Project>()
    private val cacheTimestamps = mutableMapOf<String, Long>()
    private val cacheDuration = 5.minutes.inWholeMilliseconds

    override suspend fun getProject(id: String): Result<Project> {
        // Check cache
        val cached = cache[id]
        val timestamp = cacheTimestamps[id] ?: 0L
        val isValid = System.currentTimeMillis() - timestamp < cacheDuration

        if (cached != null && isValid) {
            return Result.Success(cached)
        }

        // Fetch from source
        return withContext(Dispatchers.IO) {
            try {
                val entity = localDataSource.getById(id)
                    ?: return@withContext Result.Error(
                        FrameLapseError.Project.NotFound(id),
                        "Project not found",
                    )

                val project = ProjectMapper.toDomain(entity)

                // Update cache
                cache[id] = project
                cacheTimestamps[id] = System.currentTimeMillis()

                Result.Success(project)
            } catch (e: Exception) {
                Result.Error(e, "Failed to load project")
            }
        }
    }

    fun invalidate(id: String) {
        cache.remove(id)
        cacheTimestamps.remove(id)
    }

    fun invalidateAll() {
        cache.clear()
        cacheTimestamps.clear()
    }
}
```

## 2. StateFlow-Based Reactive Cache

### Flow Cache with Automatic Updates

```kotlin
class CachedFrameRepository(
    private val localDataSource: FrameLocalDataSource,
    private val imageStorageManager: ImageStorageManager,
    private val scope: CoroutineScope,
) : FrameRepository {

    // Reactive cache using StateFlow
    private val frameCache = MutableStateFlow<Map<String, List<Frame>>>(emptyMap())

    // Observe frames with automatic caching
    override fun observeFrames(projectId: String): Flow<List<Frame>> {
        return localDataSource.observeByProject(projectId)
            .map { entities -> entities.map { FrameMapper.toDomain(it) } }
            .onEach { frames ->
                // Update cache on each emission
                frameCache.update { cache ->
                    cache + (projectId to frames)
                }
            }
            .distinctUntilChanged()
            .flowOn(Dispatchers.IO)
    }

    // Get frames with cache-first strategy
    override suspend fun getFramesByProject(projectId: String): Result<List<Frame>> {
        // Try cache first
        val cached = frameCache.value[projectId]
        if (cached != null) {
            return Result.Success(cached)
        }

        // Fetch from database
        return withContext(Dispatchers.IO) {
            try {
                val entities = localDataSource.getByProject(projectId)
                val frames = entities.map { FrameMapper.toDomain(it) }

                // Update cache
                frameCache.update { it + (projectId to frames) }

                Result.Success(frames)
            } catch (e: Exception) {
                Result.Error(e, "Failed to load frames")
            }
        }
    }

    // Invalidate cache on mutations
    override suspend fun addFrame(frame: Frame): Result<Frame> {
        val result = withContext(Dispatchers.IO) {
            try {
                localDataSource.insert(FrameMapper.toInsertParams(frame))
                Result.Success(frame)
            } catch (e: Exception) {
                Result.Error(e, "Failed to add frame")
            }
        }

        if (result.isSuccess) {
            invalidateProject(frame.projectId)
        }

        return result
    }

    override suspend fun deleteFrame(id: String): Result<Unit> {
        val frame = getFrame(id).getOrNull()
        val result = withContext(Dispatchers.IO) {
            try {
                localDataSource.delete(id)
                Result.Success(Unit)
            } catch (e: Exception) {
                Result.Error(e, "Failed to delete frame")
            }
        }

        if (result.isSuccess && frame != null) {
            invalidateProject(frame.projectId)
        }

        return result
    }

    private fun invalidateProject(projectId: String) {
        frameCache.update { it - projectId }
    }
}
```

## 3. Stale-While-Revalidate Pattern

### Return Stale Data While Refreshing

```kotlin
class StaleWhileRevalidateRepository(
    private val localDataSource: ProjectLocalDataSource,
    private val scope: CoroutineScope,
) : ProjectRepository {

    private data class CacheEntry<T>(
        val data: T,
        val timestamp: Long,
    )

    private val cache = MutableStateFlow<Map<String, CacheEntry<Project>>>(emptyMap())
    private val staleDuration = 1.minutes.inWholeMilliseconds
    private val maxAge = 10.minutes.inWholeMilliseconds

    override suspend fun getProject(id: String): Result<Project> {
        val now = System.currentTimeMillis()
        val entry = cache.value[id]

        // If cache exists and not expired
        if (entry != null) {
            val age = now - entry.timestamp

            // Fresh - return immediately
            if (age < staleDuration) {
                return Result.Success(entry.data)
            }

            // Stale but usable - return and refresh in background
            if (age < maxAge) {
                scope.launch { refreshInBackground(id) }
                return Result.Success(entry.data)
            }
        }

        // No cache or expired - fetch synchronously
        return fetchAndCache(id)
    }

    private suspend fun fetchAndCache(id: String): Result<Project> =
        withContext(Dispatchers.IO) {
            try {
                val entity = localDataSource.getById(id)
                    ?: return@withContext Result.Error(
                        FrameLapseError.Project.NotFound(id),
                        "Project not found",
                    )

                val project = ProjectMapper.toDomain(entity)
                cache.update {
                    it + (id to CacheEntry(project, System.currentTimeMillis()))
                }
                Result.Success(project)
            } catch (e: Exception) {
                Result.Error(e, "Failed to load project")
            }
        }

    private suspend fun refreshInBackground(id: String) {
        fetchAndCache(id) // Ignores result, just updates cache
    }
}
```

## 4. Write-Through Cache Pattern

### Update Cache on Write

```kotlin
class WriteThroughCachedRepository(
    private val localDataSource: ProjectLocalDataSource,
) : ProjectRepository {

    private val cache = MutableStateFlow<Map<String, Project>>(emptyMap())

    override suspend fun createProject(project: Project): Result<Project> =
        withContext(Dispatchers.IO) {
            try {
                localDataSource.insert(ProjectMapper.toInsertParams(project))

                // Write to cache immediately
                cache.update { it + (project.id to project) }

                Result.Success(project)
            } catch (e: Exception) {
                Result.Error(e, "Failed to create project")
            }
        }

    override suspend fun updateProject(project: Project): Result<Project> =
        withContext(Dispatchers.IO) {
            try {
                localDataSource.update(ProjectMapper.toUpdateParams(project))

                // Update cache immediately
                cache.update { it + (project.id to project) }

                Result.Success(project)
            } catch (e: Exception) {
                Result.Error(e, "Failed to update project")
            }
        }

    override suspend fun deleteProject(id: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                localDataSource.delete(id)

                // Remove from cache immediately
                cache.update { it - id }

                Result.Success(Unit)
            } catch (e: Exception) {
                Result.Error(e, "Failed to delete project")
            }
        }
}
```

## 5. Shared Flow Cache for Multiple Subscribers

### Cache with SharedFlow

```kotlin
class SharedFlowCachedRepository(
    private val localDataSource: FrameLocalDataSource,
    private val scope: CoroutineScope,
) : FrameRepository {

    // Shared flows per project, created lazily
    private val frameFlows = mutableMapOf<String, SharedFlow<List<Frame>>>()
    private val flowMutex = Mutex()

    override fun observeFrames(projectId: String): Flow<List<Frame>> {
        return flow {
            val sharedFlow = flowMutex.withLock {
                frameFlows.getOrPut(projectId) {
                    createSharedFlow(projectId)
                }
            }
            emitAll(sharedFlow)
        }
    }

    private fun createSharedFlow(projectId: String): SharedFlow<List<Frame>> {
        return localDataSource.observeByProject(projectId)
            .map { entities -> entities.map { FrameMapper.toDomain(it) } }
            .distinctUntilChanged()
            .shareIn(
                scope = scope,
                started = SharingStarted.WhileSubscribed(
                    stopTimeoutMillis = 5000,  // Keep alive 5s after last subscriber
                    replayExpirationMillis = 0,
                ),
                replay = 1,  // Cache last emission
            )
    }

    // Clear flow when project deleted
    fun clearProjectFlow(projectId: String) {
        scope.launch {
            flowMutex.withLock {
                frameFlows.remove(projectId)
            }
        }
    }
}
```

## 6. LRU Cache for Limited Memory

### Bounded Cache with Eviction

```kotlin
class LruCachedRepository(
    private val localDataSource: FrameLocalDataSource,
    private val maxCacheSize: Int = 100,
) : FrameRepository {

    private val cache = object : LinkedHashMap<String, Frame>(maxCacheSize, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Frame>?): Boolean {
            return size > maxCacheSize
        }
    }

    private val cacheLock = Mutex()

    override suspend fun getFrame(id: String): Result<Frame> {
        // Check cache
        val cached = cacheLock.withLock { cache[id] }
        if (cached != null) {
            return Result.Success(cached)
        }

        // Fetch from database
        return withContext(Dispatchers.IO) {
            try {
                val entity = localDataSource.getById(id)
                    ?: return@withContext Result.Error(
                        FrameLapseError.Project.NotFound(id),
                        "Frame not found",
                    )

                val frame = FrameMapper.toDomain(entity)

                // Add to cache (may evict oldest)
                cacheLock.withLock { cache[id] = frame }

                Result.Success(frame)
            } catch (e: Exception) {
                Result.Error(e, "Failed to load frame")
            }
        }
    }
}
```

## 7. Cache Key Strategies

### Composite Cache Keys

```kotlin
data class CacheKey(
    val type: String,
    val id: String,
    val params: Map<String, Any> = emptyMap(),
) {
    override fun toString(): String = buildString {
        append(type)
        append(":")
        append(id)
        if (params.isNotEmpty()) {
            append("?")
            append(params.entries.joinToString("&") { "${it.key}=${it.value}" })
        }
    }

    companion object {
        fun project(id: String) = CacheKey("project", id)
        fun frames(projectId: String) = CacheKey("frames", projectId)
        fun framesByDate(projectId: String, start: Long, end: Long) = CacheKey(
            type = "frames",
            id = projectId,
            params = mapOf("start" to start, "end" to end),
        )
    }
}
```

## Reference Examples

- Repository interface: `composeApp/src/commonMain/kotlin/com/po4yka/framelapse/domain/repository/FrameRepository.kt`
- Repository impl: `composeApp/src/commonMain/kotlin/com/po4yka/framelapse/data/repository/FrameRepositoryImpl.kt`
- Flow usage: `composeApp/src/commonMain/kotlin/com/po4yka/framelapse/presentation/gallery/GalleryViewModel.kt`

## Checklist

### Cache Design
- [ ] Cache scope matches data lifecycle
- [ ] TTL/expiration configured appropriately
- [ ] Memory limits defined for large datasets

### Invalidation
- [ ] Cache invalidated on mutations (add/update/delete)
- [ ] Related caches invalidated (e.g., project cache when frames change)
- [ ] Manual invalidation API available

### Thread Safety
- [ ] Mutex/synchronized for mutable cache access
- [ ] StateFlow for reactive cache state
- [ ] Dispatcher.IO for database operations

### Performance
- [ ] Cache hits logged for monitoring
- [ ] LRU eviction for memory-constrained scenarios
- [ ] SharedFlow for multiple subscribers
