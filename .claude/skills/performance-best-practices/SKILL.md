---
name: performance-best-practices
description: Performance optimization workflow, anti-patterns, frame budgets, and compilation modes. Use when optimizing app performance or reviewing performance-critical code.
---

# Performance Best Practices

## Overview

This skill provides foundational knowledge for Android/KMP performance optimization, including the optimization workflow, frame budgets, compilation modes, and common anti-patterns to avoid.

## When to Use

- Starting a performance optimization effort
- Reviewing performance-critical code paths
- Setting up benchmarking infrastructure
- Debugging jank or slow startup
- Choosing between JIT and AOT compilation strategies

## 1. Performance Optimization Workflow

### Systematic Approach

```
1. Profile with JankStats
   ↓ Identify janky frames in production
2. Macrobenchmark
   ↓ Measure user-visible impact (startup, scroll, clicks)
3. Generate Baseline Profiles
   ↓ Optimize JIT compilation paths
4. Microbenchmark
   ↓ Isolate and measure specific bottlenecks
5. Fix Issues
   ↓ Apply targeted optimizations
6. Re-run All Tests
   → Verify improvements without regressions
```

### FrameLapse Example Workflow

```kotlin
// Step 1: Add JankStats to identify problems
class GalleryActivity : ComponentActivity() {
    private lateinit var jankStats: JankStats

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        jankStats = JankStats.createAndTrack(window) { frameData ->
            if (frameData.isJank) {
                analytics.logJank(
                    screen = "Gallery",
                    durationMs = frameData.frameDurationUiNanos / 1_000_000,
                    state = frameData.states.toString()
                )
            }
        }
    }
}

// Step 2: Write macrobenchmark for gallery scroll
@Test
fun scrollGallery() = benchmarkRule.measureRepeated(
    packageName = TARGET_PACKAGE,
    metrics = listOf(FrameTimingMetric()),
    iterations = 5,
) {
    // Navigate to gallery and scroll
}

// Step 3: Generate baseline profile covering gallery path
rule.collect(packageName = TARGET_PACKAGE) {
    startApp()
    navigateToGallery()
    scrollFrameList()
}

// Step 4: Microbenchmark suspected bottleneck
@Test
fun benchmarkFrameDecoding() {
    benchmarkRule.measureRepeated {
        val bitmap = runWithTimingDisabled { loadTestImage() }
        decoder.decode(bitmap)
    }
}
```

## 2. Frame Budget Targets

### Understanding Frame Timing

| Refresh Rate | Frame Budget | Description |
|--------------|--------------|-------------|
| 60 Hz | 16.67 ms | Standard displays |
| 90 Hz | 11.11 ms | High refresh rate |
| 120 Hz | 8.33 ms | Premium displays |

### Budget Breakdown

```
Total Frame Budget (16.67ms @ 60Hz)
├── Input handling: ~2ms
├── Layout/Measure: ~4ms
├── Draw commands: ~4ms
├── GPU rendering: ~4ms
└── Buffer swap: ~2ms
```

### Measuring Frame Performance

```kotlin
// Track frame timing in Compose
@Composable
fun FrameTimingMonitor(content: @Composable () -> Unit) {
    val frameTime = remember { mutableStateOf(0L) }

    LaunchedEffect(Unit) {
        while (true) {
            val start = System.nanoTime()
            withFrameNanos { }
            frameTime.value = (System.nanoTime() - start) / 1_000_000
        }
    }

    content()

    // Debug overlay
    if (BuildConfig.DEBUG) {
        Text(
            text = "Frame: ${frameTime.value}ms",
            modifier = Modifier.background(
                if (frameTime.value > 16) Color.Red else Color.Green
            )
        )
    }
}
```

## 3. Compilation Modes

### Understanding ART Compilation

| Mode | Description | When Used |
|------|-------------|-----------|
| Interpreted | Bytecode executed directly | First run, cold code |
| JIT | Just-in-time compiled | Hot code paths |
| AOT | Ahead-of-time compiled | After install, with profiles |

### Compilation Mode Impact

```kotlin
// Macrobenchmark compilation modes
enum class CompilationMode {
    None(),      // Interpreted only - worst case scenario
    Partial(),   // JIT + Baseline Profile - typical user experience
    Full(),      // Full AOT - best case scenario
    DEFAULT      // Device default
}

// Test all modes to understand impact
@Test
fun startupNoCompilation() = measureStartup(CompilationMode.None())

@Test
fun startupWithProfile() = measureStartup(
    CompilationMode.Partial(
        baselineProfileMode = BaselineProfileMode.Require
    )
)

@Test
fun startupFullCompilation() = measureStartup(CompilationMode.Full())
```

### Baseline Profile Benefits

```
Typical Improvements with Baseline Profiles:
├── App startup: 20-40% faster
├── First scroll: 30-50% smoother
├── First interaction: 15-30% faster
└── Code execution: Varies by code path
```

## 4. Memory and Allocation Patterns

### Avoid Allocations in Hot Paths

```kotlin
// BAD: Allocates on every frame
@Composable
fun FrameItem(frame: Frame) {
    val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) // Allocation!
    Text(dateFormat.format(frame.timestamp))
}

// GOOD: Reuse formatter
private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

@Composable
fun FrameItem(frame: Frame) {
    val formattedDate = remember(frame.timestamp) {
        dateFormat.format(frame.timestamp)
    }
    Text(formattedDate)
}
```

### Object Pool Pattern for Heavy Objects

```kotlin
// Object pool for bitmap processing
class BitmapPool(private val maxSize: Int = 10) {
    private val pool = ArrayDeque<Bitmap>()

    fun acquire(width: Int, height: Int, config: Bitmap.Config): Bitmap {
        synchronized(pool) {
            pool.find {
                it.width == width && it.height == height && it.config == config
            }?.let {
                pool.remove(it)
                return it
            }
        }
        return Bitmap.createBitmap(width, height, config)
    }

    fun release(bitmap: Bitmap) {
        if (!bitmap.isRecycled) {
            synchronized(pool) {
                if (pool.size < maxSize) {
                    pool.add(bitmap)
                } else {
                    bitmap.recycle()
                }
            }
        }
    }
}
```

### Lazy Initialization

```kotlin
// Delay expensive initialization
class ImageProcessor {
    // Lazy: Only created when first accessed
    private val faceDetector by lazy {
        FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                .build()
        )
    }

    suspend fun processImage(bitmap: Bitmap): Result<ProcessedImage> {
        // faceDetector initialized here on first call
        return faceDetector.process(bitmap)
    }
}
```

## 5. Background Processing Patterns

### Move Work Off Main Thread

```kotlin
// BAD: Blocking main thread
fun loadFrames(): List<Frame> {
    return database.getAllFrames() // Blocks UI!
}

// GOOD: Background dispatcher
suspend fun loadFrames(): List<Frame> = withContext(Dispatchers.IO) {
    database.getAllFrames()
}

// BETTER: Flow for reactive updates
fun observeFrames(): Flow<List<Frame>> =
    database.observeAllFrames()
        .flowOn(Dispatchers.IO)
        .distinctUntilChanged()
```

### Chunked Processing for Large Operations

```kotlin
// Process frames in chunks to avoid ANR
suspend fun processAllFrames(
    frames: List<Frame>,
    onProgress: (Int, Int) -> Unit
): Result<Unit> = withContext(Dispatchers.Default) {
    frames.chunked(10).forEachIndexed { index, chunk ->
        chunk.forEach { frame ->
            processFrame(frame)
        }
        onProgress(index * 10 + chunk.size, frames.size)
        yield() // Allow cancellation between chunks
    }
    Result.Success(Unit)
}
```

## 6. Image Loading Optimization

### Efficient Bitmap Loading

```kotlin
// Calculate sample size for memory efficiency
fun calculateInSampleSize(
    options: BitmapFactory.Options,
    reqWidth: Int,
    reqHeight: Int
): Int {
    val (height, width) = options.outHeight to options.outWidth
    var inSampleSize = 1

    if (height > reqHeight || width > reqWidth) {
        val halfHeight = height / 2
        val halfWidth = width / 2

        while (halfHeight / inSampleSize >= reqHeight &&
               halfWidth / inSampleSize >= reqWidth) {
            inSampleSize *= 2
        }
    }
    return inSampleSize
}

// Load bitmap with size constraints
suspend fun loadScaledBitmap(
    path: String,
    maxWidth: Int,
    maxHeight: Int
): Bitmap = withContext(Dispatchers.IO) {
    val options = BitmapFactory.Options().apply {
        inJustDecodeBounds = true
    }
    BitmapFactory.decodeFile(path, options)

    options.apply {
        inSampleSize = calculateInSampleSize(this, maxWidth, maxHeight)
        inJustDecodeBounds = false
    }

    BitmapFactory.decodeFile(path, options)
}
```

## 7. Anti-Patterns to Avoid

### Common Performance Mistakes

| Anti-Pattern | Problem | Solution |
|--------------|---------|----------|
| Debug builds for benchmarks | JIT disabled, unrealistic results | Always use release builds |
| High-frequency callbacks | Overhead from listener invocations | Use aggregation |
| Mixing compilation states | Inconsistent measurements | Control compilation mode |
| Blocking main thread | UI freezes, ANRs | Use coroutines with IO dispatcher |
| Unnecessary recomposition | Wasted CPU cycles | Use `key()`, `remember()`, stable types |
| Large allocations in draw | GC pauses during rendering | Pre-allocate, use object pools |
| Synchronous I/O | Blocks UI thread | Async with Flow/suspend |

### Avoiding Unnecessary Recomposition

```kotlin
// BAD: Lambda recreated on every recomposition
@Composable
fun FrameList(frames: List<Frame>) {
    LazyColumn {
        items(frames) { frame ->
            FrameItem(
                frame = frame,
                onClick = { viewModel.onFrameClick(frame.id) } // New lambda each time!
            )
        }
    }
}

// GOOD: Stable lambda reference
@Composable
fun FrameList(frames: List<Frame>, onFrameClick: (String) -> Unit) {
    LazyColumn {
        items(frames, key = { it.id }) { frame ->
            FrameItem(
                frame = frame,
                onClick = remember(frame.id) { { onFrameClick(frame.id) } }
            )
        }
    }
}

// BETTER: Use derivedStateOf for computed values
@Composable
fun GalleryScreen(viewModel: GalleryViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Only recomputes when filteredFrames actually changes
    val sortedFrames by remember {
        derivedStateOf { state.frames.sortedByDescending { it.timestamp } }
    }

    FrameList(frames = sortedFrames)
}
```

## 8. KMP Platform Considerations

### Platform-Specific Optimizations

```kotlin
// expect/actual for platform-specific performance code
expect fun measureNanoTime(block: () -> Unit): Long

// Android implementation
actual fun measureNanoTime(block: () -> Unit): Long {
    return kotlin.system.measureNanoTime(block)
}

// iOS implementation
actual fun measureNanoTime(block: () -> Unit): Long {
    val start = CFAbsoluteTimeGetCurrent()
    block()
    return ((CFAbsoluteTimeGetCurrent() - start) * 1_000_000_000).toLong()
}
```

### Platform Monitoring Interface

```kotlin
// Common interface for performance monitoring
interface PerformanceMonitor {
    fun startTrace(name: String)
    fun endTrace(name: String)
    fun recordMetric(name: String, value: Long)
}

// Android: Use Firebase Performance or Perfetto
// iOS: Use Instruments/signposts
// Desktop: Use custom logging
```

## Reference Examples

- Caching patterns: `.claude/skills/caching-strategies/SKILL.md`
- Testing patterns: `.claude/skills/testing-architecture/SKILL.md`
- Error handling: `.claude/skills/error-handling-strategy/SKILL.md`

## Checklist

### Before Optimization
- [ ] Identified specific performance problem (not premature optimization)
- [ ] Established baseline measurements
- [ ] Testing on release build, not debug

### During Optimization
- [ ] Measuring one variable at a time
- [ ] Using appropriate tool (JankStats/Macro/Micro)
- [ ] Controlling compilation mode in benchmarks

### After Optimization
- [ ] Verified improvement with benchmarks
- [ ] No regressions in other areas
- [ ] Documented findings and changes

### Code Quality
- [ ] No allocations in draw/layout paths
- [ ] Heavy work on background dispatchers
- [ ] Proper use of remember/derivedStateOf in Compose
- [ ] Object pooling for frequently created objects
