---
name: jankstats-monitoring
description: Real-time frame performance monitoring with JankStats API, aggregation patterns, and Compose integration. Use when implementing runtime jank detection.
---

# JankStats Monitoring

## Overview

JankStats is an AndroidX library for real-time frame performance monitoring. It tracks individual frame rendering performance, identifies frames that miss their target (jank), and provides per-frame callbacks with detailed metrics. This skill covers integration patterns for FrameLapse.

## When to Use

- Monitoring frame rendering in production
- Debugging UI stutters and jank
- Correlating jank with specific app states (scrolling, animations)
- Building performance dashboards
- Identifying problematic UI paths

## 1. Basic JankStats Setup

### Dependencies

```kotlin
// build.gradle.kts (androidMain)
dependencies {
    implementation("androidx.metrics:metrics-performance:1.0.0-beta01")
    implementation("androidx.tracing:tracing:1.2.0")
}
```

### Per-Frame Logging

```kotlin
// androidMain/kotlin/.../JankMonitor.kt
import androidx.metrics.performance.JankStats

class JankMonitor(private val activity: ComponentActivity) {

    private lateinit var jankStats: JankStats

    private val frameListener = JankStats.OnFrameListener { frameData ->
        // Called for EVERY frame
        if (frameData.isJank) {
            Log.w("JankStats", buildString {
                append("JANK detected: ")
                append("duration=${frameData.frameDurationUiNanos / 1_000_000}ms, ")
                append("states=${frameData.states}")
            })
        }
    }

    fun start() {
        jankStats = JankStats.createAndTrack(activity.window, frameListener)
    }

    fun pause() {
        jankStats.isTrackingEnabled = false
    }

    fun resume() {
        jankStats.isTrackingEnabled = true
    }
}
```

### Activity Integration

```kotlin
class GalleryActivity : ComponentActivity() {
    private lateinit var jankMonitor: JankMonitor

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        jankMonitor = JankMonitor(this)
        jankMonitor.start()
    }

    override fun onResume() {
        super.onResume()
        jankMonitor.resume()
    }

    override fun onPause() {
        super.onPause()
        jankMonitor.pause()
    }
}
```

## 2. JankStats Aggregation Pattern

### Reducing Callback Overhead

For production use, aggregate jank data instead of per-frame callbacks:

```kotlin
// androidMain/kotlin/.../JankStatsAggregator.kt

class JankStatsAggregator(
    window: Window,
    private val onJankReport: OnJankReportListener,
    private val maxFramesToReport: Int = 100,
) {
    fun interface OnJankReportListener {
        fun onJankReport(
            reason: String,
            totalFrames: Int,
            jankFrameData: List<FrameData>,
        )
    }

    val jankStats: JankStats

    private var totalFrames = 0
    private val jankFrameData = mutableListOf<FrameData>()

    init {
        jankStats = JankStats.createAndTrack(window) { frameData ->
            totalFrames++
            if (frameData.isJank) {
                jankFrameData.add(frameData)

                // Auto-report when buffer full
                if (jankFrameData.size >= maxFramesToReport) {
                    issueJankReport("Buffer full")
                }
            }
        }
    }

    fun issueJankReport(reason: String) {
        if (jankFrameData.isNotEmpty()) {
            onJankReport.onJankReport(
                reason,
                totalFrames,
                jankFrameData.toList()
            )
        }
        reset()
    }

    private fun reset() {
        totalFrames = 0
        jankFrameData.clear()
    }
}
```

### Usage with Aggregation

```kotlin
class GalleryActivity : ComponentActivity() {
    private lateinit var jankAggregator: JankStatsAggregator

    private val jankReportListener = JankStatsAggregator.OnJankReportListener {
        reason, totalFrames, jankFrames ->

        Log.i("JankReport", buildString {
            appendLine("=== Jank Report ($reason) ===")
            appendLine("Total frames: $totalFrames")
            appendLine("Jank frames: ${jankFrames.size}")
            appendLine("Jank rate: ${jankFrames.size * 100 / totalFrames}%")
            jankFrames.forEach { frame ->
                appendLine("  - ${frame.frameDurationUiNanos / 1_000_000}ms: ${frame.states}")
            }
        })

        // Send to analytics
        analytics.logJankReport(
            screen = "Gallery",
            totalFrames = totalFrames,
            jankFrames = jankFrames.size,
            jankRate = jankFrames.size.toFloat() / totalFrames
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        jankAggregator = JankStatsAggregator(
            window = window,
            onJankReport = jankReportListener,
            maxFramesToReport = 50
        )
    }

    override fun onPause() {
        super.onPause()
        // Report when leaving screen
        jankAggregator.issueJankReport("Activity paused")
        jankAggregator.jankStats.isTrackingEnabled = false
    }

    override fun onResume() {
        super.onResume()
        jankAggregator.jankStats.isTrackingEnabled = true
    }
}
```

## 3. Correlating Jank with App State

### Using PerformanceMetricsState

Track what the app was doing when jank occurred:

```kotlin
import androidx.metrics.performance.PerformanceMetricsState

class GalleryActivity : ComponentActivity() {
    private var metricsStateHolder: PerformanceMetricsState.Holder? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val rootView = LocalView.current
            LaunchedEffect(rootView) {
                metricsStateHolder = PerformanceMetricsState.getHolderForHierarchy(rootView)
                metricsStateHolder?.state?.putState("Screen", "Gallery")
            }

            GalleryScreen(
                onScrollStateChanged = { isScrolling ->
                    if (isScrolling) {
                        metricsStateHolder?.state?.putState("Action", "Scrolling")
                    } else {
                        metricsStateHolder?.state?.removeState("Action")
                    }
                },
                onImageLoading = { frameId ->
                    metricsStateHolder?.state?.putState("Loading", frameId)
                },
                onImageLoaded = {
                    metricsStateHolder?.state?.removeState("Loading")
                }
            )
        }
    }
}
```

### Navigation State Tracking

```kotlin
// Track navigation changes
NavHost(navController = navController, startDestination = "home") {
    composable("home") { HomeScreen() }
    composable("gallery/{projectId}") { GalleryScreen() }
    composable("capture/{projectId}") { CaptureScreen() }
}

// Add listener
LaunchedEffect(navController) {
    navController.addOnDestinationChangedListener { _, destination, arguments ->
        metricsStateHolder?.state?.putState(
            "Navigation",
            "${destination.route} args=$arguments"
        )
    }
}
```

## 4. Compose Integration

### LazyColumn Scroll Tracking

```kotlin
@Composable
fun FrameGallery(
    frames: List<Frame>,
    onFrameClick: (Frame) -> Unit,
) {
    val metricsStateHolder = rememberMetricsStateHolder()
    val listState = rememberLazyListState()

    // Track scroll state
    LaunchedEffect(metricsStateHolder, listState) {
        snapshotFlow { listState.isScrollInProgress }
            .collect { isScrolling ->
                if (isScrolling) {
                    metricsStateHolder.state?.putState("LazyColumn", "Scrolling")
                } else {
                    metricsStateHolder.state?.removeState("LazyColumn")
                }
            }
    }

    // Track visible items for debugging
    LaunchedEffect(metricsStateHolder, listState) {
        snapshotFlow {
            listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
        }.collect { (index, offset) ->
            metricsStateHolder.state?.putState(
                "VisibleRange",
                "first=$index, offset=$offset"
            )
        }
    }

    LazyColumn(state = listState) {
        items(frames, key = { it.id }) { frame ->
            FrameItem(
                frame = frame,
                onClick = { onFrameClick(frame) }
            )
        }
    }
}

@Composable
fun rememberMetricsStateHolder(): PerformanceMetricsState.Holder {
    val view = LocalView.current
    return remember(view) {
        PerformanceMetricsState.getHolderForHierarchy(view)
    }
}
```

### Image Loading State Tracking

```kotlin
@Composable
fun AsyncFrameImage(
    frame: Frame,
    modifier: Modifier = Modifier,
) {
    val metricsStateHolder = rememberMetricsStateHolder()
    var isLoading by remember { mutableStateOf(true) }

    // Track loading state
    LaunchedEffect(frame.id, isLoading) {
        if (isLoading) {
            metricsStateHolder.state?.putState("ImageLoad", frame.id)
        } else {
            metricsStateHolder.state?.removeState("ImageLoad")
        }
    }

    AsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
            .data(frame.imagePath)
            .crossfade(true)
            .build(),
        contentDescription = "Frame ${frame.orderIndex}",
        modifier = modifier,
        onLoading = { isLoading = true },
        onSuccess = { isLoading = false },
        onError = { isLoading = false }
    )
}
```

## 5. FrameLapse-Specific Monitoring

### Capture Preview Performance

```kotlin
@Composable
fun CapturePreview(
    cameraController: CameraController,
    onFrameCaptured: () -> Unit,
) {
    val metricsStateHolder = rememberMetricsStateHolder()

    // Track capture state
    LaunchedEffect(Unit) {
        metricsStateHolder.state?.putState("Screen", "CapturePreview")
    }

    AndroidView(
        factory = { context ->
            PreviewView(context).apply {
                controller = cameraController
                implementationMode = PreviewView.ImplementationMode.PERFORMANCE
            }
        },
        modifier = Modifier.fillMaxSize()
    )

    // Track when face detection is running
    LaunchedEffect(cameraController) {
        cameraController.setImageAnalysisAnalyzer(executor) { imageProxy ->
            metricsStateHolder.state?.putState("Analysis", "FaceDetection")
            // Process face detection
            imageProxy.close()
            metricsStateHolder.state?.removeState("Analysis")
        }
    }
}
```

### Alignment Processing Tracking

```kotlin
class AlignmentProcessor(
    private val metricsStateHolder: PerformanceMetricsState.Holder,
) {
    suspend fun processFrame(frame: Frame): Result<AlignedFrame> {
        metricsStateHolder.state?.putState("Processing", "Alignment:${frame.id}")

        return try {
            val result = withContext(Dispatchers.Default) {
                // Heavy computation
                alignFace(frame)
            }
            Result.Success(result)
        } finally {
            metricsStateHolder.state?.removeState("Processing")
        }
    }
}
```

## 6. Production Analytics Integration

### Structured Jank Reporting

```kotlin
data class JankEvent(
    val screen: String,
    val action: String?,
    val durationMs: Long,
    val frameOverrunMs: Long,
    val timestamp: Long,
    val additionalStates: Map<String, String>,
)

class JankAnalytics(
    private val analyticsClient: AnalyticsClient,
) {
    private val eventBuffer = mutableListOf<JankEvent>()
    private val bufferMutex = Mutex()

    suspend fun recordJank(frameData: FrameData) {
        val event = JankEvent(
            screen = frameData.states.find { it.key == "Screen" }?.value ?: "Unknown",
            action = frameData.states.find { it.key == "Action" }?.value,
            durationMs = frameData.frameDurationUiNanos / 1_000_000,
            frameOverrunMs = frameData.frameOverrunNanos / 1_000_000,
            timestamp = System.currentTimeMillis(),
            additionalStates = frameData.states.associate { it.key to it.value }
        )

        bufferMutex.withLock {
            eventBuffer.add(event)

            if (eventBuffer.size >= 20) {
                flush()
            }
        }
    }

    suspend fun flush() {
        val events = bufferMutex.withLock {
            eventBuffer.toList().also { eventBuffer.clear() }
        }

        if (events.isNotEmpty()) {
            analyticsClient.logBatch("jank_events", events)
        }
    }
}
```

### Severity Classification

```kotlin
enum class JankSeverity {
    MINOR,    // 1-2 frames dropped (16-33ms)
    MODERATE, // 3-5 frames dropped (33-83ms)
    SEVERE,   // 6+ frames dropped (>83ms)
    FREEZE    // >500ms
}

fun classifyJank(frameOverrunNanos: Long): JankSeverity {
    val overrunMs = frameOverrunNanos / 1_000_000
    return when {
        overrunMs > 500 -> JankSeverity.FREEZE
        overrunMs > 83 -> JankSeverity.SEVERE
        overrunMs > 33 -> JankSeverity.MODERATE
        else -> JankSeverity.MINOR
    }
}
```

## 7. KMP Abstraction Layer

### Platform-Agnostic Interface

```kotlin
// commonMain
interface FramePerformanceMonitor {
    fun startTracking()
    fun stopTracking()
    fun putState(key: String, value: String)
    fun removeState(key: String)
    fun setOnJankListener(listener: (JankInfo) -> Unit)
}

data class JankInfo(
    val durationMs: Long,
    val isJank: Boolean,
    val states: Map<String, String>,
)

// androidMain
class AndroidFramePerformanceMonitor(
    private val activity: ComponentActivity,
) : FramePerformanceMonitor {

    private var jankStats: JankStats? = null
    private var metricsStateHolder: PerformanceMetricsState.Holder? = null
    private var onJankListener: ((JankInfo) -> Unit)? = null

    override fun startTracking() {
        val rootView = activity.window.decorView
        metricsStateHolder = PerformanceMetricsState.getHolderForHierarchy(rootView)

        jankStats = JankStats.createAndTrack(activity.window) { frameData ->
            onJankListener?.invoke(
                JankInfo(
                    durationMs = frameData.frameDurationUiNanos / 1_000_000,
                    isJank = frameData.isJank,
                    states = frameData.states.associate { it.key to it.value }
                )
            )
        }
    }

    override fun stopTracking() {
        jankStats?.isTrackingEnabled = false
    }

    override fun putState(key: String, value: String) {
        metricsStateHolder?.state?.putState(key, value)
    }

    override fun removeState(key: String) {
        metricsStateHolder?.state?.removeState(key)
    }

    override fun setOnJankListener(listener: (JankInfo) -> Unit) {
        onJankListener = listener
    }
}

// iosMain - stub or alternative implementation
class IOSFramePerformanceMonitor : FramePerformanceMonitor {
    // iOS uses CADisplayLink or Instruments for similar functionality
    // Provide stub or alternative implementation
}
```

## Anti-Patterns

### Avoid High-Frequency State Updates

```kotlin
// BAD: Updates state on every frame
LaunchedEffect(listState) {
    snapshotFlow { listState.firstVisibleItemScrollOffset }
        .collect { offset ->
            metricsStateHolder.state?.putState("Offset", offset.toString())
        }
}

// GOOD: Throttle updates
LaunchedEffect(listState) {
    snapshotFlow { listState.firstVisibleItemScrollOffset }
        .sample(100) // Sample every 100ms
        .collect { offset ->
            metricsStateHolder.state?.putState("Offset", offset.toString())
        }
}
```

### Avoid Heavy Work in Callbacks

```kotlin
// BAD: Processing in callback
JankStats.createAndTrack(window) { frameData ->
    database.insertJankEvent(frameData) // Database I/O in callback!
}

// GOOD: Queue for background processing
val jankChannel = Channel<FrameData>(Channel.BUFFERED)

JankStats.createAndTrack(window) { frameData ->
    if (frameData.isJank) {
        jankChannel.trySend(frameData)
    }
}

// Process in background
scope.launch(Dispatchers.IO) {
    jankChannel.consumeEach { frameData ->
        database.insertJankEvent(frameData)
    }
}
```

## Reference Examples

- Performance best practices: `.claude/skills/performance-best-practices/SKILL.md`
- Caching strategies: `.claude/skills/caching-strategies/SKILL.md`
- JankStats sample: `performance-samples/JankStatsSample/`

## Checklist

### Setup
- [ ] JankStats dependency added to Android target
- [ ] Tracking enabled in onResume, disabled in onPause
- [ ] PerformanceMetricsState holder initialized

### State Tracking
- [ ] Screen name tracked
- [ ] Scroll state tracked for lists
- [ ] Loading states tracked for images
- [ ] Heavy processing states tracked

### Production
- [ ] Jank events aggregated (not per-frame callbacks)
- [ ] Severity classification implemented
- [ ] Analytics integration for reporting
- [ ] Background processing for I/O operations
