---
name: macrobenchmark-patterns
description: App-level performance testing for startup, scroll, click latency, and frame timing. Use when measuring user-visible performance metrics.
---

# Macrobenchmark Patterns

## Overview

Macrobenchmarks measure app-level performance from a user's perspective, including startup time, scrolling smoothness, click latency, and frame timing. This skill covers setting up and running macrobenchmarks for FrameLapse.

## When to Use

- Measuring app startup performance
- Testing scroll smoothness in lists/grids
- Measuring click/tap response latency
- Validating performance improvements
- Comparing compilation mode impacts
- CI/CD performance regression testing

## 1. Module Setup

### Project Structure

```
FrameLapse/
├── composeApp/                    # Main app module
│   └── build.gradle.kts
├── macrobenchmark/                # Benchmark test module (NEW)
│   ├── src/main/kotlin/
│   │   └── com/po4yka/framelapse/benchmark/
│   │       ├── startup/
│   │       ├── frames/
│   │       ├── scroll/
│   │       └── baselineprofile/
│   └── build.gradle.kts
└── settings.gradle.kts
```

### Benchmark Module Configuration

```kotlin
// macrobenchmark/build.gradle.kts
plugins {
    id("com.android.test")
    id("org.jetbrains.kotlin.android")
    id("androidx.baselineprofile")
}

android {
    namespace = "com.po4yka.framelapse.benchmark"
    compileSdk = 35

    defaultConfig {
        minSdk = 28  // Macrobenchmark requires API 28+
        targetSdk = 35
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Point to target app
    targetProjectPath = ":composeApp"

    // Required for self-instrumenting tests
    experimentalProperties["android.experimental.self-instrumenting"] = true

    testOptions.managedDevices.devices {
        create<com.android.build.api.dsl.ManagedVirtualDevice>("pixel6Api31") {
            device = "Pixel 6"
            apiLevel = 31
            systemImageSource = "aosp-atd"  // Android Test Device image
        }
    }
}

dependencies {
    implementation(libs.androidx.benchmark.macro.junit4)
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.test.espresso.core)
    implementation(libs.androidx.test.uiautomator)
}

baselineProfile {
    managedDevices += "pixel6Api31"
    useConnectedDevices = false
}
```

### App Module Configuration

```kotlin
// composeApp/build.gradle.kts
plugins {
    id("com.android.application")
    id("androidx.baselineprofile")
}

android {
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(...)

            // Enable baseline profile generation
            baselineProfile.automaticGenerationDuringBuild = true
        }

        // Create benchmark build type
        create("benchmark") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
            isDebuggable = false
        }
    }
}

dependencies {
    implementation(libs.androidx.profileinstaller)
    baselineProfile(project(":macrobenchmark"))
}
```

## 2. Startup Benchmarks

### Cold vs Warm vs Hot Startup

```kotlin
// benchmark/startup/StartupBenchmark.kt
@RunWith(AndroidJUnit4::class)
class ColdStartupBenchmark : AbstractStartupBenchmark(StartupMode.COLD)

@RunWith(AndroidJUnit4::class)
class WarmStartupBenchmark : AbstractStartupBenchmark(StartupMode.WARM)

abstract class AbstractStartupBenchmark(
    private val startupMode: StartupMode,
) {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun startupNoCompilation() = startup(CompilationMode.None())

    @Test
    fun startupPartialWithBaselineProfiles() = startup(
        CompilationMode.Partial(
            baselineProfileMode = BaselineProfileMode.Require
        )
    )

    @Test
    fun startupFullCompilation() = startup(CompilationMode.Full())

    private fun startup(compilationMode: CompilationMode) {
        benchmarkRule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(StartupTimingMetric()),
            compilationMode = compilationMode,
            iterations = 5,
            startupMode = startupMode,
        ) {
            pressHome()
            startActivityAndWait()
        }
    }
}

private const val TARGET_PACKAGE = "com.po4yka.framelapse"
```

### Fully Drawn Startup

Measure time until app reports "fully drawn":

```kotlin
// In your MainActivity
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            var isReady by remember { mutableStateOf(false) }

            LaunchedEffect(Unit) {
                // Load initial data
                viewModel.loadProjects()
            }

            val state by viewModel.state.collectAsStateWithLifecycle()

            LaunchedEffect(state.isLoaded) {
                if (state.isLoaded && !isReady) {
                    isReady = true
                    // Report fully drawn
                    reportFullyDrawn()
                }
            }

            FrameLapseApp()
        }
    }
}

// Benchmark
@Test
fun measureFullyDrawnStartup() {
    benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(StartupTimingMetric()),
        iterations = 5,
        startupMode = StartupMode.COLD,
    ) {
        startActivityAndWait()
        // StartupTimingMetric automatically captures reportFullyDrawn timing
    }
}
```

## 3. Frame Timing Benchmarks

### Scroll Performance

```kotlin
// benchmark/frames/FrameTimingBenchmark.kt
@LargeTest
@RunWith(AndroidJUnit4::class)
class GalleryScrollBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun scrollGalleryNoCompilation() = scrollGallery(CompilationMode.None())

    @Test
    fun scrollGalleryWithBaseline() = scrollGallery(
        CompilationMode.Partial(BaselineProfileMode.Require)
    )

    @Test
    fun scrollGalleryFullCompilation() = scrollGallery(CompilationMode.Full())

    private fun scrollGallery(compilationMode: CompilationMode) {
        benchmarkRule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(FrameTimingMetric()),
            compilationMode = compilationMode,
            iterations = 5,
            startupMode = StartupMode.WARM,
            setupBlock = {
                // Navigate to gallery before measuring
                startActivityAndWait()
                device.waitForIdle()
            }
        ) {
            // Find scrollable element
            val gallery = device.findObject(
                By.res(TARGET_PACKAGE, "gallery_grid")
            )

            // Perform scroll gestures
            repeat(3) {
                gallery.setGestureMargin(device.displayWidth / 5)
                gallery.fling(Direction.DOWN)
                device.waitForIdle()
            }

            repeat(3) {
                gallery.fling(Direction.UP)
                device.waitForIdle()
            }
        }
    }
}
```

### With Custom Trace Metrics

```kotlin
@OptIn(ExperimentalMetricApi::class)
@Test
fun scrollWithTraceMetrics() {
    benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(
            FrameTimingMetric(),
            // Measure custom trace sections
            TraceSectionMetric("FrameGrid", TraceSectionMetric.Mode.Sum),
            TraceSectionMetric("AsyncImage", TraceSectionMetric.Mode.Sum),
            TraceSectionMetric("%FrameThumbnail%", TraceSectionMetric.Mode.Sum),
        ),
        iterations = 5,
    ) {
        scrollGalleryContent()
    }
}
```

## 4. Click Latency Benchmarks

### Measuring Response Time

```kotlin
// benchmark/clicklatency/ClickLatencyBenchmark.kt
@OptIn(ExperimentalMetricApi::class)
@RunWith(AndroidJUnit4::class)
class ClickLatencyBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun measureCaptureButtonLatency() {
        var isFirstIteration = true

        benchmarkRule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(
                TraceSectionMetric("CaptureButtonClick"),
                FrameTimingMetric(),
            ),
            compilationMode = CompilationMode.Full(),
            iterations = 10,
            startupMode = null,  // Don't restart between iterations
            setupBlock = {
                if (isFirstIteration) {
                    startActivityAndWait()
                    navigateToCaptureScreen()
                    isFirstIteration = false
                }
            }
        ) {
            // Click capture button
            device.findObject(
                By.desc("Capture photo")
            ).click()

            // Wait for capture completion
            device.wait(
                Until.hasObject(By.text("Photo captured")),
                5000
            )

            // Dismiss confirmation
            device.pressBack()
        }
    }

    @Test
    fun measureFrameItemClickLatency() {
        benchmarkRule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(
                TraceSectionMetric("FrameItemClick"),
                StartupTimingMetric(),  // Measures navigation time
            ),
            iterations = 5,
            setupBlock = {
                startActivityAndWait()
                navigateToGallery()
            }
        ) {
            // Click first frame item
            val firstFrame = device.findObject(
                By.res(TARGET_PACKAGE, "frame_item")
            )
            firstFrame.click()

            // Wait for detail screen
            device.wait(
                Until.hasObject(By.res(TARGET_PACKAGE, "frame_detail")),
                3000
            )

            // Go back for next iteration
            device.pressBack()
            device.waitForIdle()
        }
    }
}
```

### Instrumenting Click Handlers

```kotlin
// In your Composable
@Composable
fun CaptureButton(onClick: () -> Unit) {
    FloatingActionButton(
        onClick = {
            trace("CaptureButtonClick") {
                onClick()
            }
        }
    ) {
        Icon(Icons.Default.Camera, "Capture photo")
    }
}
```

## 5. UIAutomator Helpers

### Common Navigation Patterns

```kotlin
// benchmark/util/BenchmarkUtils.kt
object BenchmarkUtils {
    const val TARGET_PACKAGE = "com.po4yka.framelapse"
    const val DEFAULT_TIMEOUT = 5000L
}

fun MacrobenchmarkScope.navigateToGallery() {
    // Click gallery tab or navigate
    device.findObject(
        By.res(TARGET_PACKAGE, "nav_gallery")
    ).click()
    device.waitForIdle()
}

fun MacrobenchmarkScope.navigateToCaptureScreen() {
    device.findObject(
        By.res(TARGET_PACKAGE, "nav_capture")
    ).click()
    device.waitForIdle()
}

fun MacrobenchmarkScope.scrollFrameList(iterations: Int = 3) {
    val list = device.findObject(
        By.scrollable(true)
    )

    repeat(iterations) {
        list.fling(Direction.DOWN)
        device.waitForIdle()
    }
}

fun MacrobenchmarkScope.waitForElement(
    selector: BySelector,
    timeout: Long = BenchmarkUtils.DEFAULT_TIMEOUT,
): UiObject2? {
    return device.wait(Until.findObject(selector), timeout)
}
```

### Handling Different Screen States

```kotlin
fun MacrobenchmarkScope.ensureLoggedIn() {
    // Check if login screen is shown
    val loginButton = device.findObject(By.text("Log In"))
    if (loginButton != null) {
        // Perform login
        device.findObject(By.res(TARGET_PACKAGE, "email_input"))
            .text = "test@example.com"
        device.findObject(By.res(TARGET_PACKAGE, "password_input"))
            .text = "password"
        loginButton.click()
        device.waitForIdle()
    }
}

fun MacrobenchmarkScope.ensureProjectExists() {
    // Create project if none exists
    val emptyState = device.findObject(By.text("No projects yet"))
    if (emptyState != null) {
        device.findObject(By.desc("Create project")).click()
        device.findObject(By.res(TARGET_PACKAGE, "project_name"))
            .text = "Benchmark Project"
        device.findObject(By.text("Create")).click()
        device.waitForIdle()
    }
}
```

## 6. Compilation Mode Comparison

### Understanding Results

```kotlin
@RunWith(Parameterized::class)
class CompilationComparisonBenchmark(
    private val compilationMode: CompilationMode,
) {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "compilation={0}")
        fun data() = listOf(
            CompilationMode.None(),
            CompilationMode.Partial(BaselineProfileMode.Require),
            CompilationMode.Full(),
        )
    }

    @Test
    fun measureStartup() {
        benchmarkRule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(StartupTimingMetric()),
            compilationMode = compilationMode,
            iterations = 5,
            startupMode = StartupMode.COLD,
        ) {
            startActivityAndWait()
        }
    }
}
```

### Expected Results Pattern

```
CompilationMode.None():
  timeToInitialDisplayMs: 800-1200ms  (worst case)
  timeToFullDisplayMs: 1500-2500ms

CompilationMode.Partial(Require):
  timeToInitialDisplayMs: 400-600ms   (20-40% improvement)
  timeToFullDisplayMs: 800-1200ms

CompilationMode.Full():
  timeToInitialDisplayMs: 350-500ms   (best case, unrealistic for users)
  timeToFullDisplayMs: 700-1000ms
```

## 7. CI/CD Integration

### Running on CI

```yaml
# .github/workflows/benchmark.yml
name: Performance Benchmarks

on:
  pull_request:
    branches: [main]
  push:
    branches: [main]

jobs:
  benchmark:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Set up JDK
        uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'

      - name: Enable KVM
        run: |
          echo 'KERNEL=="kvm", GROUP="kvm", MODE="0666", OPTIONS+="static_node=kvm"' | sudo tee /etc/udev/rules.d/99-kvm4all.rules
          sudo udevadm control --reload-rules
          sudo udevadm trigger --name-match=kvm

      - name: Run benchmarks
        run: ./gradlew :macrobenchmark:pixel6Api31BenchmarkAndroidTest

      - name: Upload results
        uses: actions/upload-artifact@v4
        with:
          name: benchmark-results
          path: macrobenchmark/build/outputs/
```

### Benchmark Result Analysis

```kotlin
// Parse benchmark JSON output
data class BenchmarkResult(
    val benchmarkName: String,
    val metrics: Map<String, List<Double>>,
)

fun analyzeBenchmarkResults(jsonFile: File): List<BenchmarkResult> {
    val json = Json.decodeFromString<List<BenchmarkResult>>(jsonFile.readText())

    json.forEach { result ->
        println("Benchmark: ${result.benchmarkName}")
        result.metrics.forEach { (metric, values) ->
            val median = values.sorted()[values.size / 2]
            val p90 = values.sorted()[(values.size * 0.9).toInt()]
            println("  $metric: median=${median}ms, p90=${p90}ms")
        }
    }

    return json
}
```

## 8. FrameLapse-Specific Benchmarks

### Complete User Journey

```kotlin
@Test
fun measureTypicalUserJourney() {
    benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(
            StartupTimingMetric(),
            FrameTimingMetric(),
        ),
        iterations = 3,
        startupMode = StartupMode.COLD,
    ) {
        // 1. App startup
        startActivityAndWait()

        // 2. Select project
        device.findObject(By.res(TARGET_PACKAGE, "project_item")).click()
        device.waitForIdle()

        // 3. Navigate to capture
        device.findObject(By.res(TARGET_PACKAGE, "capture_fab")).click()
        device.waitForIdle()

        // 4. Wait for camera preview
        device.wait(
            Until.hasObject(By.res(TARGET_PACKAGE, "camera_preview")),
            5000
        )

        // 5. Take photo
        device.findObject(By.desc("Capture")).click()
        device.wait(Until.hasObject(By.text("Processing")), 1000)
        device.wait(Until.gone(By.text("Processing")), 10000)

        // 6. View gallery
        device.pressBack()
        scrollFrameList()
    }
}
```

## Anti-Patterns

### Avoid Flaky Selectors

```kotlin
// BAD: Text might be localized
device.findObject(By.text("Gallery"))

// GOOD: Resource IDs are stable
device.findObject(By.res(TARGET_PACKAGE, "nav_gallery"))

// GOOD: Content descriptions for accessibility
device.findObject(By.desc("Navigate to gallery"))
```

### Avoid Hardcoded Waits

```kotlin
// BAD: Arbitrary sleep
Thread.sleep(2000)

// GOOD: Wait for specific condition
device.wait(Until.hasObject(By.res(TARGET_PACKAGE, "content")), 5000)
device.waitForIdle()
```

## Reference Examples

- Baseline profiles: `.claude/skills/baseline-profile-generation/SKILL.md`
- Performance tracing: `.claude/skills/performance-tracing/SKILL.md`
- MacrobenchmarkSample: `performance-samples/MacrobenchmarkSample/`

## Checklist

### Module Setup
- [ ] Benchmark module created with correct configuration
- [ ] Target app has benchmark build type
- [ ] ProfileInstaller dependency added to app
- [ ] Managed virtual device configured

### Benchmark Coverage
- [ ] Cold startup benchmark implemented
- [ ] Warm startup benchmark implemented
- [ ] Scroll performance benchmarks
- [ ] Click latency benchmarks
- [ ] Key user journeys covered

### Test Quality
- [ ] Stable element selectors (resource IDs, not text)
- [ ] Proper wait conditions (not Thread.sleep)
- [ ] Multiple compilation modes tested
- [ ] Sufficient iterations (5+ for statistical significance)

### CI/CD
- [ ] Benchmarks run on CI
- [ ] Results archived and tracked
- [ ] Regression alerts configured
