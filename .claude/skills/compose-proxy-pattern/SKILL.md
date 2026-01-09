---
name: compose-proxy-pattern
description: Implements proxy pattern for caching, lazy loading, and access control. Use when adding caching layers, lazy image loading, or controlled resource access.
---

# Compose Proxy Pattern

## Overview

The Proxy pattern provides a surrogate or placeholder for another object to control access to it. In FrameLapse, this is useful for implementing caching proxies, lazy loading for images/resources, logging proxies, and protection proxies for premium features.

## When to Use

- Adding caching layer to repositories/services
- Implementing lazy loading for expensive resources
- Logging/monitoring service calls
- Access control for premium features
- Rate limiting API calls

## 1. Basic Caching Proxy

### Service Interface and Implementation

```kotlin
// domain/service/WeatherService.kt

interface WeatherService {
    suspend fun getWeatherData(location: String): Result<WeatherData>
}

// data/service/WeatherApiService.kt

class WeatherApiService(
    private val httpClient: HttpClient,
) : WeatherService {

    override suspend fun getWeatherData(location: String): Result<WeatherData> {
        return try {
            val response = httpClient.get("https://api.weather.com/v1/weather") {
                parameter("location", location)
            }
            Result.Success(response.body())
        } catch (e: Exception) {
            Result.Error(e, "Failed to fetch weather data")
        }
    }
}
```

### Caching Proxy

```kotlin
// data/service/CachingWeatherService.kt

class CachingWeatherService(
    private val realService: WeatherService,
    private val cacheDurationMs: Long = 5 * 60 * 1000, // 5 minutes
) : WeatherService {

    private data class CacheEntry(
        val data: WeatherData,
        val timestamp: Long,
    )

    private val cache = mutableMapOf<String, CacheEntry>()

    override suspend fun getWeatherData(location: String): Result<WeatherData> {
        val cacheKey = location.lowercase()
        val cachedEntry = cache[cacheKey]

        // Return cached data if still valid
        if (cachedEntry != null && !isExpired(cachedEntry)) {
            println("Cache HIT for $location")
            return Result.Success(cachedEntry.data)
        }

        // Fetch from real service
        println("Cache MISS for $location, fetching from API...")
        val result = realService.getWeatherData(location)

        // Cache successful results
        result.getOrNull()?.let { data ->
            cache[cacheKey] = CacheEntry(
                data = data,
                timestamp = System.currentTimeMillis(),
            )
        }

        return result
    }

    private fun isExpired(entry: CacheEntry): Boolean {
        return System.currentTimeMillis() - entry.timestamp > cacheDurationMs
    }

    fun clearCache() {
        cache.clear()
    }

    fun invalidate(location: String) {
        cache.remove(location.lowercase())
    }
}
```

### Usage with DI

```kotlin
// di/ServiceModule.kt

val serviceModule = module {
    // Real service
    single<WeatherService> {
        CachingWeatherService(
            realService = WeatherApiService(get()),
            cacheDurationMs = 5 * 60 * 1000,
        )
    }
}
```

## 2. Frame Repository Caching Proxy

### Caching Frame Access

```kotlin
// data/repository/CachingFrameRepository.kt

class CachingFrameRepository(
    private val realRepository: FrameRepository,
) : FrameRepository {

    // In-memory cache
    private val frameCache = mutableMapOf<String, Frame>()
    private val projectFramesCache = mutableMapOf<String, List<Frame>>()
    private var cacheTimestamps = mutableMapOf<String, Long>()

    private val cacheDuration = 60_000L // 1 minute

    override suspend fun getFrame(id: String): Result<Frame> {
        // Check cache first
        frameCache[id]?.let { cached ->
            if (!isExpired("frame:$id")) {
                return Result.Success(cached)
            }
        }

        // Fetch from real repository
        val result = realRepository.getFrame(id)

        // Cache successful result
        result.getOrNull()?.let { frame ->
            frameCache[id] = frame
            cacheTimestamps["frame:$id"] = System.currentTimeMillis()
        }

        return result
    }

    override suspend fun getFramesByProject(projectId: String): Result<List<Frame>> {
        // Check cache
        val cacheKey = "project:$projectId"
        projectFramesCache[projectId]?.let { cached ->
            if (!isExpired(cacheKey)) {
                return Result.Success(cached)
            }
        }

        // Fetch from real repository
        val result = realRepository.getFramesByProject(projectId)

        // Cache and index individual frames
        result.getOrNull()?.let { frames ->
            projectFramesCache[projectId] = frames
            cacheTimestamps[cacheKey] = System.currentTimeMillis()

            // Also cache individual frames
            frames.forEach { frame ->
                frameCache[frame.id] = frame
                cacheTimestamps["frame:${frame.id}"] = System.currentTimeMillis()
            }
        }

        return result
    }

    override suspend fun addFrame(frame: Frame): Result<Frame> {
        val result = realRepository.addFrame(frame)

        // Invalidate project cache on mutation
        result.onSuccess {
            invalidateProjectCache(frame.projectId)
        }

        return result
    }

    override suspend fun deleteFrame(id: String): Result<Unit> {
        // Get frame first to know which project to invalidate
        val frame = frameCache[id] ?: realRepository.getFrame(id).getOrNull()

        val result = realRepository.deleteFrame(id)

        result.onSuccess {
            frameCache.remove(id)
            cacheTimestamps.remove("frame:$id")
            frame?.let { invalidateProjectCache(it.projectId) }
        }

        return result
    }

    override fun observeFrames(projectId: String): Flow<List<Frame>> {
        // Pass through - Flows are already reactive
        return realRepository.observeFrames(projectId)
            .onEach { frames ->
                // Update cache when flow emits
                projectFramesCache[projectId] = frames
                cacheTimestamps["project:$projectId"] = System.currentTimeMillis()
            }
    }

    private fun isExpired(key: String): Boolean {
        val timestamp = cacheTimestamps[key] ?: return true
        return System.currentTimeMillis() - timestamp > cacheDuration
    }

    private fun invalidateProjectCache(projectId: String) {
        projectFramesCache.remove(projectId)
        cacheTimestamps.remove("project:$projectId")
    }

    fun clearAllCache() {
        frameCache.clear()
        projectFramesCache.clear()
        cacheTimestamps.clear()
    }
}
```

## 3. Lazy Loading Proxy for Images

### Image Loading Proxy

```kotlin
// data/proxy/LazyImageProxy.kt

interface ImageLoader {
    suspend fun loadImage(path: String): Result<ImageBitmap>
    fun getPlaceholder(): ImageBitmap
}

class LazyImageProxy(
    private val realLoader: ImageLoader,
) : ImageLoader {

    private val loadedImages = mutableMapOf<String, ImageBitmap>()
    private val loadingPaths = mutableSetOf<String>()

    override suspend fun loadImage(path: String): Result<ImageBitmap> {
        // Return cached image
        loadedImages[path]?.let { return Result.Success(it) }

        // Prevent duplicate loading
        if (path in loadingPaths) {
            // Wait for ongoing load or return placeholder
            return Result.Success(getPlaceholder())
        }

        loadingPaths.add(path)

        val result = realLoader.loadImage(path)

        result.getOrNull()?.let { image ->
            loadedImages[path] = image
        }

        loadingPaths.remove(path)

        return result
    }

    override fun getPlaceholder(): ImageBitmap {
        return realLoader.getPlaceholder()
    }

    fun preload(paths: List<String>, scope: CoroutineScope) {
        scope.launch {
            paths.forEach { path ->
                if (path !in loadedImages) {
                    loadImage(path)
                }
            }
        }
    }

    fun evict(path: String) {
        loadedImages.remove(path)
    }

    fun clearCache() {
        loadedImages.clear()
    }
}
```

### Composable with Lazy Image

```kotlin
// ui/components/LazyImage.kt

@Composable
fun LazyImage(
    path: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val imageLoader = remember { koinInject<LazyImageProxy>() }
    var image by remember { mutableStateOf<ImageBitmap?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(path) {
        isLoading = true
        val result = imageLoader.loadImage(path)
        image = result.getOrNull()
        isLoading = false
    }

    Box(modifier = modifier) {
        if (isLoading || image == null) {
            // Placeholder
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            }
        } else {
            Image(
                bitmap = image!!,
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = contentScale,
            )
        }
    }
}
```

## 4. Logging Proxy

### Service Call Logging

```kotlin
// data/proxy/LoggingRepositoryProxy.kt

class LoggingFrameRepository(
    private val realRepository: FrameRepository,
    private val logger: Logger = Logger.getLogger("FrameRepository"),
) : FrameRepository {

    override suspend fun getFrame(id: String): Result<Frame> {
        logger.info("getFrame(id=$id) called")
        val startTime = System.currentTimeMillis()

        val result = realRepository.getFrame(id)

        val duration = System.currentTimeMillis() - startTime
        when {
            result.isSuccess -> logger.info("getFrame(id=$id) succeeded in ${duration}ms")
            result.isError -> logger.error("getFrame(id=$id) failed in ${duration}ms: ${result.errorMessage}")
        }

        return result
    }

    override suspend fun getFramesByProject(projectId: String): Result<List<Frame>> {
        logger.info("getFramesByProject(projectId=$projectId) called")
        val startTime = System.currentTimeMillis()

        val result = realRepository.getFramesByProject(projectId)

        val duration = System.currentTimeMillis() - startTime
        when {
            result.isSuccess -> {
                val count = result.getOrNull()?.size ?: 0
                logger.info("getFramesByProject returned $count frames in ${duration}ms")
            }
            result.isError -> {
                logger.error("getFramesByProject failed in ${duration}ms: ${result.errorMessage}")
            }
        }

        return result
    }

    override suspend fun addFrame(frame: Frame): Result<Frame> {
        logger.info("addFrame(frameId=${frame.id}, projectId=${frame.projectId}) called")
        val result = realRepository.addFrame(frame)

        when {
            result.isSuccess -> logger.info("addFrame succeeded")
            result.isError -> logger.error("addFrame failed: ${result.errorMessage}")
        }

        return result
    }

    // ... other methods with logging
}
```

## 5. Protection Proxy (Premium Features)

### Feature Access Control

```kotlin
// data/proxy/PremiumFeatureProxy.kt

interface ExportService {
    suspend fun exportVideo(projectId: String, settings: ExportSettings): Result<String>
    suspend fun exportGif(projectId: String, settings: GifSettings): Result<String>
    suspend fun export4K(projectId: String, settings: ExportSettings): Result<String>
}

class PremiumExportProxy(
    private val realService: ExportService,
    private val subscriptionRepository: SubscriptionRepository,
) : ExportService {

    override suspend fun exportVideo(
        projectId: String,
        settings: ExportSettings,
    ): Result<String> {
        // Video export available to all users
        return realService.exportVideo(projectId, settings)
    }

    override suspend fun exportGif(
        projectId: String,
        settings: GifSettings,
    ): Result<String> {
        // GIF export is premium
        if (!isPremiumUser()) {
            return Result.Error(
                PremiumRequiredException("GIF export requires premium"),
                "Upgrade to Premium to export GIFs",
            )
        }
        return realService.exportGif(projectId, settings)
    }

    override suspend fun export4K(
        projectId: String,
        settings: ExportSettings,
    ): Result<String> {
        // 4K export is premium
        if (!isPremiumUser()) {
            return Result.Error(
                PremiumRequiredException("4K export requires premium"),
                "Upgrade to Premium for 4K export",
            )
        }
        return realService.export4K(projectId, settings)
    }

    private suspend fun isPremiumUser(): Boolean {
        return subscriptionRepository.getCurrentSubscription()
            .getOrNull()
            ?.isPremium ?: false
    }
}

class PremiumRequiredException(message: String) : Exception(message)
```

### Usage in ViewModel

```kotlin
// presentation/export/ExportViewModel.kt

class ExportViewModel(
    private val exportService: ExportService,  // Actually PremiumExportProxy
) : BaseViewModel<ExportState, ExportEvent, ExportEffect>(ExportState()) {

    private fun export4K(projectId: String, settings: ExportSettings) {
        viewModelScope.launch {
            updateState { copy(isExporting = true) }

            exportService.export4K(projectId, settings)
                .onSuccess { path ->
                    updateState { copy(isExporting = false) }
                    sendEffect(ExportEffect.ExportComplete(path))
                }
                .onError { exception, message ->
                    updateState { copy(isExporting = false) }

                    when (exception) {
                        is PremiumRequiredException -> {
                            sendEffect(ExportEffect.ShowUpgradePrompt(message ?: "Premium required"))
                        }
                        else -> {
                            sendEffect(ExportEffect.ShowError(message ?: "Export failed"))
                        }
                    }
                }
        }
    }
}
```

## 6. Rate Limiting Proxy

### API Call Throttling

```kotlin
// data/proxy/RateLimitingProxy.kt

class RateLimitingApiProxy(
    private val realApi: AlignmentApi,
    private val maxRequestsPerMinute: Int = 30,
) : AlignmentApi {

    private val requestTimestamps = mutableListOf<Long>()
    private val mutex = Mutex()

    override suspend fun alignFrame(
        imagePath: String,
        settings: AlignmentSettings,
    ): Result<AlignmentResult> {
        mutex.withLock {
            // Clean old timestamps
            val oneMinuteAgo = System.currentTimeMillis() - 60_000
            requestTimestamps.removeAll { it < oneMinuteAgo }

            // Check rate limit
            if (requestTimestamps.size >= maxRequestsPerMinute) {
                val oldestRequest = requestTimestamps.first()
                val waitTime = 60_000 - (System.currentTimeMillis() - oldestRequest)

                if (waitTime > 0) {
                    return Result.Error(
                        RateLimitException("Rate limit exceeded"),
                        "Too many requests. Please wait ${waitTime / 1000} seconds.",
                    )
                }
            }

            // Record request
            requestTimestamps.add(System.currentTimeMillis())
        }

        return realApi.alignFrame(imagePath, settings)
    }
}

class RateLimitException(message: String) : Exception(message)
```

## 7. Composable Proxy Usage

### Using Proxy in Compose

```kotlin
@Composable
fun WeatherWidget(location: String) {
    val weatherService = remember { koinInject<WeatherService>() }
    var weather by remember { mutableStateOf<WeatherData?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(location) {
        isLoading = true
        error = null

        weatherService.getWeatherData(location)
            .onSuccess { data ->
                weather = data
                isLoading = false
            }
            .onError { _, message ->
                error = message
                isLoading = false
            }
    }

    Card {
        when {
            isLoading -> CircularProgressIndicator()
            error != null -> Text("Error: $error")
            weather != null -> WeatherContent(weather!!)
        }
    }
}
```

## Anti-Patterns

### Avoid: Proxy Modifying Data

```kotlin
// BAD - proxy changes data
class BadCachingProxy(private val real: Repository) : Repository {
    override suspend fun getData(): Data {
        val data = real.getData()
        return data.copy(name = data.name.uppercase())  // Modifying data!
    }
}

// BETTER - proxy only controls access
class GoodCachingProxy(private val real: Repository) : Repository {
    override suspend fun getData(): Data {
        return cachedData ?: real.getData().also { cachedData = it }
    }
}
```

### Avoid: Proxy with Tight Coupling

```kotlin
// BAD - proxy knows about specific implementations
class BadProxy : Repository {
    private val realRepo = FrameRepositoryImpl(...)  // Direct instantiation
}

// BETTER - proxy receives dependency
class GoodProxy(
    private val realRepo: Repository,  // Injected interface
) : Repository
```

## Reference Examples

- Repository pattern: `composeApp/src/commonMain/kotlin/com/po4yka/framelapse/data/repository/`
- Caching skill: `.claude/skills/caching-strategies/SKILL.md`

## Checklist

### Proxy Implementation
- [ ] Implements same interface as real subject
- [ ] Delegates to real subject for actual work
- [ ] Adds behavior (caching/logging/access control)
- [ ] Transparent to clients

### Caching Proxy
- [ ] Cache has expiration mechanism
- [ ] Cache invalidation on mutations
- [ ] Thread-safe cache access
- [ ] Memory limits considered

### Protection Proxy
- [ ] Clear error messages for denied access
- [ ] Graceful degradation when possible
- [ ] Access checks are efficient

### Integration
- [ ] Registered in DI as interface type
- [ ] Real implementation is internal/private
- [ ] Configuration via DI (cache duration, limits)
