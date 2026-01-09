---
name: performance-tracing
description: Custom trace sections with Perfetto, composition tracing, and bottleneck identification. Use when profiling specific code paths or analyzing system traces.
---

# Performance Tracing

## Overview

Performance tracing allows you to instrument code with custom trace sections that appear in system profiling tools like Perfetto. This skill covers adding trace instrumentation, analyzing traces, and identifying bottlenecks in FrameLapse.

## When to Use

- Profiling specific code paths (image processing, video encoding)
- Understanding Compose recomposition behavior
- Measuring time spent in specific operations
- Correlating app behavior with system events
- Debugging performance regressions

## 1. Basic Tracing Setup

### Dependencies

```kotlin
// build.gradle.kts (androidMain)
dependencies {
    implementation("androidx.tracing:tracing:1.2.0")
    implementation("androidx.tracing:tracing-ktx:1.2.0")

    // For Compose composition tracing
    implementation("androidx.compose.runtime:runtime-tracing:1.0.0-beta01")
}
```

### Simple Trace Sections

```kotlin
import androidx.tracing.trace

// Synchronous tracing
fun processImage(bitmap: Bitmap): ProcessedImage {
    return trace("ProcessImage") {
        val detected = trace("FaceDetection") {
            faceDetector.detect(bitmap)
        }

        val aligned = trace("FaceAlignment") {
            aligner.align(bitmap, detected)
        }

        trace("ImageSave") {
            storage.save(aligned)
        }

        ProcessedImage(aligned, detected)
    }
}

// Suspend function tracing
suspend fun loadFrames(projectId: String): List<Frame> {
    return trace("LoadFrames") {
        withContext(Dispatchers.IO) {
            trace("DatabaseQuery") {
                database.getFramesByProject(projectId)
            }
        }
    }
}
```

### Async Tracing

```kotlin
import androidx.tracing.Trace

// For async operations that span multiple functions
class VideoEncoder {
    private var encodeTraceToken: Long = 0

    fun startEncoding(projectId: String) {
        encodeTraceToken = Trace.beginAsyncSection("VideoEncode", projectId.hashCode())
        // Start encoding...
    }

    fun finishEncoding() {
        // Finish encoding...
        Trace.endAsyncSection("VideoEncode", encodeTraceToken.toInt())
    }
}
```

## 2. Compose Composition Tracing

### Enable Composition Tracing

```kotlin
// In Application class or entry point
class FrameLapseApp : Application() {
    override fun onCreate() {
        super.onCreate()

        // Enable composition tracing in debug builds
        if (BuildConfig.DEBUG) {
            enableComposeCompilerReports()
        }
    }
}

// build.gradle.kts
android {
    buildFeatures {
        compose = true
    }
    composeOptions {
        // Enable composition tracing
        kotlinCompilerExtensionVersion = "1.5.0"
    }
}

// For release builds with tracing
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        if (project.findProperty("enableComposeCompilerReports") == "true") {
            freeCompilerArgs.add("-P")
            freeCompilerArgs.add(
                "plugin:androidx.compose.compiler.plugins.kotlin:sourceInformation=true"
            )
        }
    }
}
```

### Tracing Composables

```kotlin
import androidx.compose.runtime.Composable
import androidx.tracing.trace

@Composable
fun FrameGallery(frames: List<Frame>) {
    trace("FrameGallery") {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(120.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(frames, key = { it.id }) { frame ->
                trace("FrameItem:${frame.id}") {
                    FrameGridItem(frame = frame)
                }
            }
        }
    }
}

@Composable
fun FrameGridItem(frame: Frame) {
    trace("FrameGridItem") {
        Card(modifier = Modifier.aspectRatio(1f)) {
            trace("AsyncImage") {
                AsyncImage(
                    model = frame.thumbnailPath,
                    contentDescription = null
                )
            }
        }
    }
}
```

### Custom Trace Labels

```kotlin
@Composable
fun EntryRow(entry: Entry) {
    // Custom trace label appears in Perfetto
    trace("EntryRowCustomTrace") {
        Row(modifier = Modifier.fillMaxWidth()) {
            // Content
        }
    }
}

// Trace with dynamic info
@Composable
fun FrameDetail(frame: Frame) {
    trace("FrameDetail:${frame.id}:${frame.status}") {
        Column {
            // Frame details
        }
    }
}
```

## 3. TraceSectionMetric in Benchmarks

### Measuring Custom Traces

```kotlin
@OptIn(ExperimentalMetricApi::class)
@RunWith(AndroidJUnit4::class)
class FrameProcessingBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun measureImageProcessing() {
        benchmarkRule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(
                FrameTimingMetric(),
                // Measure custom trace sections
                TraceSectionMetric("ProcessImage", TraceSectionMetric.Mode.Sum),
                TraceSectionMetric("FaceDetection", TraceSectionMetric.Mode.Sum),
                TraceSectionMetric("FaceAlignment", TraceSectionMetric.Mode.Sum),
            ),
            iterations = 5,
            startupMode = StartupMode.WARM,
        ) {
            uiAutomator {
                startIntent(Intent("$packageName.CAPTURE_ACTIVITY"))
                // Trigger image capture
                onElement { contentDescription == "Capture" }.click()
                // Wait for processing
                Thread.sleep(2000)
            }
        }
    }

    @Test
    fun measureComposeTraces() {
        benchmarkRule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(
                FrameTimingMetric(),
                // Measure Compose recomposition traces
                // Pattern matches "EntryRow (" prefix for all EntryRow composables
                TraceSectionMetric("%EntryRow (%", TraceSectionMetric.Mode.Sum),
                TraceSectionMetric("FrameGallery", TraceSectionMetric.Mode.Sum),
            ),
            iterations = 5,
        ) {
            uiAutomator {
                navigateToGallery()
                scrollFrameList()
            }
        }
    }
}
```

### Trace Section Modes

```kotlin
TraceSectionMetric.Mode.First  // Time of first occurrence
TraceSectionMetric.Mode.Sum    // Total time across all occurrences
TraceSectionMetric.Mode.Average // Average time per occurrence
TraceSectionMetric.Mode.Count   // Number of occurrences
```

## 4. FrameLapse-Specific Tracing

### Image Capture Pipeline

```kotlin
class CaptureUseCase(
    private val cameraController: CameraController,
    private val faceDetector: FaceDetector,
    private val imageProcessor: ImageProcessor,
    private val frameRepository: FrameRepository,
) {
    suspend fun capture(projectId: String): Result<Frame> {
        return trace("CaptureImage") {
            // Step 1: Capture from camera
            val imageData = trace("CameraCapture") {
                cameraController.takePicture()
            }

            // Step 2: Detect face
            val faceResult = trace("FaceDetection") {
                faceDetector.detect(imageData)
            }

            if (faceResult == null) {
                return@trace Result.Error(
                    FaceNotDetectedException(),
                    "No face detected"
                )
            }

            // Step 3: Process and align
            val processedImage = trace("ImageProcessing") {
                trace("DecodeImage") {
                    imageProcessor.decode(imageData)
                }.let { bitmap ->
                    trace("AlignFace") {
                        imageProcessor.alignFace(bitmap, faceResult)
                    }
                }
            }

            // Step 4: Save to storage
            val savedPath = trace("SaveImage") {
                withContext(Dispatchers.IO) {
                    imageProcessor.save(processedImage, projectId)
                }
            }

            // Step 5: Create database record
            val frame = trace("DatabaseInsert") {
                frameRepository.addFrame(
                    Frame(
                        id = UUID.randomUUID().toString(),
                        projectId = projectId,
                        imagePath = savedPath,
                        timestamp = System.currentTimeMillis(),
                        faceLandmarks = faceResult.landmarks
                    )
                )
            }

            Result.Success(frame.getOrThrow())
        }
    }
}
```

### Video Export Pipeline

```kotlin
class VideoExportUseCase(
    private val frameRepository: FrameRepository,
    private val videoEncoder: VideoEncoder,
) {
    suspend fun export(
        projectId: String,
        settings: ExportSettings,
        onProgress: (Float) -> Unit,
    ): Result<String> = trace("VideoExport") {

        // Load frames
        val frames = trace("LoadFrames") {
            frameRepository.getFramesByProject(projectId).getOrThrow()
        }

        // Initialize encoder
        trace("InitEncoder") {
            videoEncoder.initialize(settings)
        }

        // Encode frames
        frames.forEachIndexed { index, frame ->
            trace("EncodeFrame:$index") {
                val bitmap = trace("LoadBitmap") {
                    BitmapFactory.decodeFile(frame.imagePath)
                }

                trace("WriteFrame") {
                    videoEncoder.encodeFrame(bitmap)
                }

                bitmap.recycle()
            }

            onProgress(index.toFloat() / frames.size)
        }

        // Finalize
        val outputPath = trace("FinalizeVideo") {
            videoEncoder.finish()
        }

        Result.Success(outputPath)
    }
}
```

### Gallery Scroll Performance

```kotlin
@Composable
fun GalleryScreen(viewModel: GalleryViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    trace("GalleryScreen") {
        when {
            state.isLoading -> {
                trace("LoadingState") {
                    LoadingIndicator()
                }
            }
            state.frames.isEmpty() -> {
                trace("EmptyState") {
                    EmptyGalleryMessage()
                }
            }
            else -> {
                trace("FrameGrid") {
                    FrameGrid(
                        frames = state.frames,
                        onFrameClick = { viewModel.onEvent(GalleryEvent.FrameClicked(it)) }
                    )
                }
            }
        }
    }
}

@Composable
private fun FrameGrid(
    frames: List<Frame>,
    onFrameClick: (Frame) -> Unit,
) {
    val gridState = rememberLazyGridState()

    trace("LazyVerticalGrid") {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 100.dp),
            state = gridState,
            contentPadding = PaddingValues(8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(
                items = frames,
                key = { it.id }
            ) { frame ->
                trace("GridItem:${frame.id}") {
                    FrameThumbnail(
                        frame = frame,
                        onClick = { onFrameClick(frame) }
                    )
                }
            }
        }
    }
}
```

## 5. Analyzing Traces with Perfetto

### Recording a Trace

```bash
# Record trace via ADB
adb shell perfetto \
  -c - --txt \
  -o /data/misc/perfetto-traces/trace.perfetto-trace \
<<EOF
buffers: {
    size_kb: 63488
    fill_policy: RING_BUFFER
}
data_sources: {
    config {
        name: "linux.ftrace"
        ftrace_config {
            ftrace_events: "sched/sched_switch"
            ftrace_events: "power/suspend_resume"
            ftrace_events: "sched/sched_wakeup"
            atrace_categories: "view"
            atrace_categories: "webview"
            atrace_categories: "wm"
            atrace_categories: "am"
            atrace_categories: "dalvik"
            atrace_apps: "com.po4yka.framelapse"
        }
    }
}
duration_ms: 10000
EOF

# Pull trace file
adb pull /data/misc/perfetto-traces/trace.perfetto-trace
```

### Interpreting Traces

```
Perfetto UI (https://ui.perfetto.dev)
├── Timeline View
│   ├── CPU scheduling (which cores running your code)
│   ├── Thread activity (main thread, worker threads)
│   └── Custom trace sections (your instrumented code)
├── Metrics
│   ├── Frame timing (dropped frames, jank)
│   └── Custom metrics from TraceSectionMetric
└── SQL Queries
    └── Custom analysis of trace data
```

### Common Trace Patterns to Look For

| Pattern | Indicates |
|---------|-----------|
| Long main thread sections | Blocking operations on UI thread |
| Gaps between trace sections | Waiting for I/O or other threads |
| Repeated short sections | Excessive recomposition or allocations |
| Overlapping async sections | Concurrent operations |

## 6. KMP Abstraction

### Cross-Platform Trace Interface

```kotlin
// commonMain
expect inline fun <T> trace(sectionName: String, block: () -> T): T

// androidMain
actual inline fun <T> trace(sectionName: String, block: () -> T): T {
    return androidx.tracing.trace(sectionName, block)
}

// iosMain
actual inline fun <T> trace(sectionName: String, block: () -> T): T {
    // Use os_signpost for iOS
    val signpostID = OSSignpostID(log: performanceLog)
    os_signpost(.begin, log: performanceLog, name: sectionName, signpostID: signpostID)
    val result = block()
    os_signpost(.end, log: performanceLog, name: sectionName, signpostID: signpostID)
    return result
}

// desktopMain
actual inline fun <T> trace(sectionName: String, block: () -> T): T {
    // Simple timing for desktop
    val start = System.nanoTime()
    val result = block()
    val duration = (System.nanoTime() - start) / 1_000_000
    if (duration > 16) {
        println("PERF: $sectionName took ${duration}ms")
    }
    return result
}
```

### Trace Configuration

```kotlin
// commonMain
object TraceConfig {
    var enabled: Boolean = true
    var verboseLogging: Boolean = false
}

expect inline fun <T> traceIf(
    enabled: Boolean,
    sectionName: String,
    block: () -> T
): T

// androidMain
actual inline fun <T> traceIf(
    enabled: Boolean,
    sectionName: String,
    block: () -> T
): T {
    return if (enabled && TraceConfig.enabled) {
        trace(sectionName, block)
    } else {
        block()
    }
}
```

## 7. Debug Overlay

### Performance Overlay Composable

```kotlin
@Composable
fun PerformanceOverlay(
    enabled: Boolean = BuildConfig.DEBUG,
    content: @Composable () -> Unit,
) {
    var lastFrameTime by remember { mutableLongStateOf(0L) }
    var fps by remember { mutableIntStateOf(0) }
    var frameCount by remember { mutableIntStateOf(0) }
    var lastFpsUpdate by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos { frameTimeNanos ->
                val currentTime = System.currentTimeMillis()
                frameCount++

                if (currentTime - lastFpsUpdate >= 1000) {
                    fps = frameCount
                    frameCount = 0
                    lastFpsUpdate = currentTime
                }

                lastFrameTime = frameTimeNanos / 1_000_000
            }
        }
    }

    Box {
        content()

        if (enabled) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .background(Color.Black.copy(alpha = 0.7f))
                    .padding(4.dp)
            ) {
                Text(
                    text = "FPS: $fps",
                    color = if (fps < 55) Color.Red else Color.Green,
                    fontSize = 12.sp
                )
                Text(
                    text = "Frame: ${lastFrameTime}ms",
                    color = if (lastFrameTime > 16) Color.Red else Color.Green,
                    fontSize = 12.sp
                )
            }
        }
    }
}
```

## Anti-Patterns

### Avoid Excessive Trace Granularity

```kotlin
// BAD: Too many traces create overhead
items.forEach { item ->
    trace("ProcessItem") {
        trace("Step1") { step1(item) }
        trace("Step2") { step2(item) }
        trace("Step3") { step3(item) }
    }
}

// GOOD: Trace at meaningful boundaries
trace("ProcessAllItems") {
    items.forEach { item ->
        processItem(item)
    }
}
```

### Avoid Traces in Tight Loops

```kotlin
// BAD: Trace inside pixel loop
for (y in 0 until height) {
    for (x in 0 until width) {
        trace("ProcessPixel") {  // Millions of traces!
            processPixel(x, y)
        }
    }
}

// GOOD: Trace the entire operation
trace("ProcessAllPixels") {
    for (y in 0 until height) {
        for (x in 0 until width) {
            processPixel(x, y)
        }
    }
}
```

## Reference Examples

- JankStats monitoring: `.claude/skills/jankstats-monitoring/SKILL.md`
- Performance best practices: `.claude/skills/performance-best-practices/SKILL.md`
- Macrobenchmark: `performance-samples/MacrobenchmarkSample/`

## Checklist

### Instrumentation
- [ ] Key operations have trace sections
- [ ] Trace names are descriptive and unique
- [ ] Async operations use async trace API
- [ ] No traces in tight loops

### Compose Tracing
- [ ] Composition tracing enabled for profiling
- [ ] Key composables have trace sections
- [ ] Recomposition patterns visible in traces

### Analysis
- [ ] Traces recorded with Perfetto
- [ ] Main thread blocking identified
- [ ] TraceSectionMetric used in benchmarks
- [ ] Baseline measurements established
