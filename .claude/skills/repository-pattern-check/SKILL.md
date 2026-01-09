---
name: repository-pattern-check
description: Validates repository implementations including interface contracts, mapper usage, dispatcher handling, and caching patterns. Use when reviewing repositories for correctness.
---

# Repository Pattern Check

## Overview

This skill helps validate repository implementations against Clean Architecture principles. Use it to review repositories for proper interface separation, mapper usage, error handling, and reactive patterns.

## Repository Pattern Rules

### 1. Interface in Domain, Implementation in Data

```kotlin
// CORRECT Structure:
domain/repository/FrameRepository.kt      // Interface
data/repository/FrameRepositoryImpl.kt    // Implementation

// VIOLATION: Implementation in domain
domain/repository/FrameRepository.kt      // ❌ Contains implementation
```

### 2. Domain Interface Definition

```kotlin
// domain/repository/FrameRepository.kt

// CORRECT: Clean domain interface
interface FrameRepository {
    suspend fun addFrame(frame: Frame): Result<Frame>
    suspend fun getFrame(id: String): Result<Frame>
    suspend fun getFramesByProject(projectId: String): Result<List<Frame>>
    suspend fun deleteFrame(id: String): Result<Unit>
    fun observeFrames(projectId: String): Flow<List<Frame>>
}

// VIOLATION: Data layer types in interface
interface FrameRepository {
    suspend fun addFrame(entity: FrameEntity): FrameEntity  // ❌ Data entity
    suspend fun getFrame(id: String): FrameEntity?  // ❌ Data entity, nullable
    fun observeFrames(projectId: String): Flow<List<FrameEntity>>  // ❌ Data entity
}
```

## Validation Checks

### Check 1: Interface Separation

```kotlin
// VIOLATION: No interface
// data/repository/FrameRepository.kt
class FrameRepository(  // ❌ Direct class, no interface
    private val dataSource: FrameLocalDataSource,
) {
    suspend fun getFrames(projectId: String): List<Frame> { ... }
}

// CORRECT: Interface + Implementation
// domain/repository/FrameRepository.kt
interface FrameRepository {
    suspend fun getFramesByProject(projectId: String): Result<List<Frame>>
}

// data/repository/FrameRepositoryImpl.kt
class FrameRepositoryImpl(
    private val dataSource: FrameLocalDataSource,
) : FrameRepository {
    override suspend fun getFramesByProject(projectId: String): Result<List<Frame>> { ... }
}
```

### Check 2: Mapper Usage

```kotlin
// VIOLATION: No mapper, manual conversion
class FrameRepositoryImpl(
    private val dataSource: FrameLocalDataSource,
) : FrameRepository {

    override suspend fun getFrame(id: String): Result<Frame> {
        val entity = dataSource.getById(id)
        // ❌ Manual conversion inline
        return Result.Success(Frame(
            id = entity.id,
            projectId = entity.project_id,
            originalPath = entity.original_path,
            // ... many more fields
        ))
    }
}

// CORRECT: Using mapper
class FrameRepositoryImpl(
    private val dataSource: FrameLocalDataSource,
) : FrameRepository {

    override suspend fun getFrame(id: String): Result<Frame> {
        val entity = dataSource.getById(id)
            ?: return Result.Error(Exception("Not found"), "Frame not found")
        return Result.Success(FrameMapper.toDomain(entity))  // ✓ Mapper
    }
}

// data/mapper/FrameMapper.kt
object FrameMapper {
    fun toDomain(entity: FrameEntity): Frame = Frame(
        id = entity.id,
        projectId = entity.project_id,
        originalPath = entity.original_path,
        alignedPath = entity.aligned_path,
        timestamp = entity.timestamp,
        confidence = entity.confidence,
        sortOrder = entity.sort_order.toInt(),
    )

    fun toInsertParams(frame: Frame): FrameInsertParams = FrameInsertParams(
        id = frame.id,
        project_id = frame.projectId,
        original_path = frame.originalPath,
        timestamp = frame.timestamp,
        sort_order = frame.sortOrder.toLong(),
    )
}
```

### Check 3: Dispatcher Context

```kotlin
// VIOLATION: Missing dispatcher
class FrameRepositoryImpl(
    private val dataSource: FrameLocalDataSource,
) : FrameRepository {

    override suspend fun getFramesByProject(projectId: String): Result<List<Frame>> {
        // ❌ Runs on caller's dispatcher (may be Main)
        val entities = dataSource.getByProject(projectId)
        return Result.Success(entities.map { FrameMapper.toDomain(it) })
    }
}

// CORRECT: Explicit IO dispatcher
class FrameRepositoryImpl(
    private val dataSource: FrameLocalDataSource,
) : FrameRepository {

    override suspend fun getFramesByProject(projectId: String): Result<List<Frame>> =
        withContext(Dispatchers.IO) {  // ✓ IO for database
            try {
                val entities = dataSource.getByProject(projectId)
                Result.Success(entities.map { FrameMapper.toDomain(it) })
            } catch (e: Exception) {
                Result.Error(e, "Failed to load frames")
            }
        }
}
```

### Check 4: Result<T> Wrapping

```kotlin
// VIOLATION: Nullable return, no error info
interface FrameRepository {
    suspend fun getFrame(id: String): Frame?  // ❌ Nullable, no error context
}

class FrameRepositoryImpl(...) : FrameRepository {
    override suspend fun getFrame(id: String): Frame? {
        return try {
            dataSource.getById(id)?.let { FrameMapper.toDomain(it) }
        } catch (e: Exception) {
            null  // ❌ Error information lost
        }
    }
}

// CORRECT: Result<T> with error context
interface FrameRepository {
    suspend fun getFrame(id: String): Result<Frame>  // ✓ Result type
}

class FrameRepositoryImpl(...) : FrameRepository {
    override suspend fun getFrame(id: String): Result<Frame> =
        withContext(Dispatchers.IO) {
            try {
                val entity = dataSource.getById(id)
                    ?: return@withContext Result.Error(
                        NotFoundException("Frame $id not found"),
                        "Frame not found",
                    )
                Result.Success(FrameMapper.toDomain(entity))
            } catch (e: Exception) {
                Result.Error(e, "Failed to load frame")  // ✓ Error preserved
            }
        }
}
```

### Check 5: Flow vs Suspend

```kotlin
// CORRECT: Flow for reactive, suspend for one-shot
interface FrameRepository {
    // One-shot operations use suspend
    suspend fun getFrame(id: String): Result<Frame>
    suspend fun addFrame(frame: Frame): Result<Frame>
    suspend fun deleteFrame(id: String): Result<Unit>

    // Reactive streams use Flow
    fun observeFrames(projectId: String): Flow<List<Frame>>
    fun observeFrameCount(projectId: String): Flow<Long>
}

// VIOLATION: Flow for one-shot
interface FrameRepository {
    fun getFrame(id: String): Flow<Frame>  // ❌ Flow for single item
}

// VIOLATION: Suspend for stream
interface FrameRepository {
    suspend fun observeFrames(projectId: String): List<Frame>  // ❌ Not reactive
}
```

### Check 6: Flow Implementation

```kotlin
// CORRECT: Flow with proper operators
class FrameRepositoryImpl(
    private val dataSource: FrameLocalDataSource,
) : FrameRepository {

    override fun observeFrames(projectId: String): Flow<List<Frame>> {
        return dataSource.observeByProject(projectId)
            .map { entities -> entities.map { FrameMapper.toDomain(it) } }
            .distinctUntilChanged()  // ✓ Avoid duplicate emissions
            .flowOn(Dispatchers.IO)  // ✓ IO for database operations
    }
}

// VIOLATION: Missing flowOn
override fun observeFrames(projectId: String): Flow<List<Frame>> {
    return dataSource.observeByProject(projectId)
        .map { entities -> entities.map { FrameMapper.toDomain(it) } }
    // ❌ Missing flowOn, runs on collector's dispatcher
}
```

### Check 7: Transaction Handling

```kotlin
// VIOLATION: Non-atomic operations
class FrameRepositoryImpl(...) : FrameRepository {
    override suspend fun deleteFramesByProject(projectId: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val frames = dataSource.getByProject(projectId)
                frames.forEach { frame ->
                    dataSource.delete(frame.id)  // ❌ Not atomic
                    storageManager.deleteFile(frame.originalPath)
                }
                Result.Success(Unit)
            } catch (e: Exception) {
                Result.Error(e, "Failed to delete frames")
            }
        }
}

// CORRECT: Atomic transaction
class FrameRepositoryImpl(
    private val database: FrameLapseDatabase,
    private val dataSource: FrameLocalDataSource,
) : FrameRepository {
    override suspend fun deleteFramesByProject(projectId: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                database.transaction {  // ✓ Atomic
                    dataSource.deleteByProject(projectId)
                }
                // File cleanup outside transaction
                cleanupFilesForProject(projectId)
                Result.Success(Unit)
            } catch (e: Exception) {
                Result.Error(e, "Failed to delete frames")
            }
        }
}
```

## Common Issues

### Issue 1: Business Logic in Repository

```kotlin
// VIOLATION: Business logic in repository
class FrameRepositoryImpl(...) : FrameRepository {
    override suspend fun addFrame(frame: Frame): Result<Frame> {
        // ❌ Validation logic belongs in use case
        if (frame.confidence != null && frame.confidence < 0.7f) {
            return Result.Error(Exception("Low confidence"), "Quality too low")
        }
        // ❌ Business rule in repository
        val maxFrames = 1000
        if (dataSource.getCount() >= maxFrames) {
            return Result.Error(Exception("Limit reached"), "Frame limit reached")
        }
        // ...
    }
}

// CORRECT: Pure data operations
class FrameRepositoryImpl(...) : FrameRepository {
    override suspend fun addFrame(frame: Frame): Result<Frame> =
        withContext(Dispatchers.IO) {
            try {
                dataSource.insert(FrameMapper.toInsertParams(frame))
                Result.Success(frame)
            } catch (e: Exception) {
                Result.Error(e, "Failed to save frame")
            }
        }
}
```

### Issue 2: Exposing Data Layer Types

```kotlin
// VIOLATION: SQLDelight types leaked
interface FrameRepository {
    suspend fun getFrames(projectId: String): SelectAllByProject  // ❌ Query type
}

// CORRECT: Domain types only
interface FrameRepository {
    suspend fun getFramesByProject(projectId: String): Result<List<Frame>>  // ✓ Domain type
}
```

## Quick Reference

### Method Naming Convention

| Operation | Interface Method | Return Type |
|-----------|------------------|-------------|
| Create | `addFrame(frame)` | `Result<Frame>` |
| Read one | `getFrame(id)` | `Result<Frame>` |
| Read many | `getFramesByProject(id)` | `Result<List<Frame>>` |
| Update | `updateFrame(frame)` | `Result<Frame>` |
| Delete | `deleteFrame(id)` | `Result<Unit>` |
| Observe | `observeFrames(id)` | `Flow<List<Frame>>` |
| Count | `getFrameCount(id)` | `Result<Long>` |

## Reference Examples

- Repository interface: `composeApp/src/commonMain/kotlin/com/po4yka/framelapse/domain/repository/FrameRepository.kt`
- Repository impl: `composeApp/src/commonMain/kotlin/com/po4yka/framelapse/data/repository/FrameRepositoryImpl.kt`
- Mapper: `composeApp/src/commonMain/kotlin/com/po4yka/framelapse/data/mapper/FrameMapper.kt`

## Checklist

### Interface (domain)
- [ ] Located in `domain/repository/`
- [ ] Uses domain entity types
- [ ] Returns `Result<T>` for operations
- [ ] Uses `Flow<T>` for reactive queries
- [ ] No data layer imports

### Implementation (data)
- [ ] Located in `data/repository/`
- [ ] Implements domain interface
- [ ] Uses `withContext(Dispatchers.IO)`
- [ ] Uses mapper for entity conversion
- [ ] Wraps exceptions in Result.Error
- [ ] Uses `flowOn(Dispatchers.IO)` for Flows

### Mapper
- [ ] Located in `data/mapper/`
- [ ] Bidirectional conversion methods
- [ ] `toDomain(entity)` and `toEntity(domain)`
- [ ] Handles nullable fields properly

### Error Handling
- [ ] All exceptions caught
- [ ] User-friendly error messages
- [ ] Original exception preserved in Result.Error
