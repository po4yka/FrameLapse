---
name: microbenchmark-patterns
description: Isolated code measurement for algorithms, allocations, and specific operations. Use when comparing implementation approaches or measuring code-level performance.
---

# Microbenchmark Patterns

## Overview

Microbenchmarks measure the performance of isolated code units - specific functions, algorithms, or operations. They run in a controlled environment without the complexity of full app execution, making them ideal for comparing implementation approaches and identifying bottlenecks.

## When to Use

- Comparing algorithm implementations (sorting, searching)
- Measuring allocation costs
- Testing JNI/native call overhead
- Benchmarking image processing operations
- Evaluating serialization performance
- Validating optimization improvements

## 1. Module Setup

### Project Structure

```
FrameLapse/
├── composeApp/                    # Main app
├── benchmarkable/                 # Code to benchmark (NEW)
│   ├── src/main/kotlin/
│   │   └── com/po4yka/framelapse/benchmarkable/
│   │       ├── algorithms/
│   │       ├── processing/
│   │       └── data/
│   └── build.gradle.kts
├── microbenchmark/                # Benchmark tests (NEW)
│   ├── src/androidTest/kotlin/
│   │   └── com/po4yka/framelapse/benchmark/
│   └── build.gradle.kts
└── settings.gradle.kts
```

### Benchmarkable Module

```kotlin
// benchmarkable/build.gradle.kts
plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.po4yka.framelapse.benchmarkable"
    compileSdk = 35

    defaultConfig {
        minSdk = 21
    }

    buildTypes {
        release {
            isMinifyEnabled = false  // Don't obfuscate benchmark code
        }
    }
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    // Add dependencies for code being benchmarked
}
```

### Microbenchmark Module

```kotlin
// microbenchmark/build.gradle.kts
plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("androidx.benchmark")
}

android {
    namespace = "com.po4yka.framelapse.microbenchmark"
    compileSdk = 35

    defaultConfig {
        minSdk = 21
        testInstrumentationRunner = "androidx.benchmark.junit4.AndroidBenchmarkRunner"

        // Optional: Configure profiling
        testInstrumentationRunnerArguments["androidx.benchmark.profiling.mode"] = "StackSampling"
    }

    // MUST use release for accurate measurements
    testBuildType = "release"

    buildTypes {
        release {
            isDefault = true
        }
    }
}

dependencies {
    androidTestImplementation(project(":benchmarkable"))
    androidTestImplementation(libs.androidx.benchmark.junit4)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
}
```

## 2. Basic Benchmark Pattern

### Simple Function Benchmark

```kotlin
// microbenchmark/src/androidTest/kotlin/.../SimpleBenchmark.kt
@RunWith(AndroidJUnit4::class)
class SimpleBenchmark {

    @get:Rule
    val benchmarkRule = BenchmarkRule()

    @Test
    fun benchmarkStringConcatenation() {
        benchmarkRule.measureRepeated {
            val result = buildString {
                repeat(100) { append("item$it,") }
            }
        }
    }

    @Test
    fun benchmarkStringBuilder() {
        benchmarkRule.measureRepeated {
            val sb = StringBuilder()
            repeat(100) { sb.append("item$it,") }
            val result = sb.toString()
        }
    }
}
```

### Using runWithTimingDisabled

Exclude setup/teardown from measurements:

```kotlin
@Test
fun benchmarkListSorting() {
    val random = Random(42)  // Deterministic seed
    val unsortedList = List(10_000) { random.nextInt() }

    var listToSort: MutableList<Int> = mutableListOf()

    benchmarkRule.measureRepeated {
        // Setup - not measured
        listToSort = runWithTimingDisabled {
            unsortedList.toMutableList()
        }

        // Only this is measured
        listToSort.sort()
    }

    // Verify correctness AFTER benchmark
    assertTrue(listToSort.zipWithNext().all { it.first <= it.second })
}
```

## 3. Algorithm Comparison Benchmarks

### Sorting Algorithms

```kotlin
// benchmarkable/src/main/kotlin/.../SortingAlgorithms.kt
object SortingAlgorithms {

    fun quickSort(arr: IntArray, low: Int = 0, high: Int = arr.size - 1) {
        if (low < high) {
            val pivot = partition(arr, low, high)
            quickSort(arr, low, pivot - 1)
            quickSort(arr, pivot + 1, high)
        }
    }

    private fun partition(arr: IntArray, low: Int, high: Int): Int {
        val pivot = arr[high]
        var i = low - 1
        for (j in low until high) {
            if (arr[j] <= pivot) {
                i++
                arr[i] = arr[j].also { arr[j] = arr[i] }
            }
        }
        arr[i + 1] = arr[high].also { arr[high] = arr[i + 1] }
        return i + 1
    }

    fun bubbleSort(arr: IntArray) {
        val n = arr.size
        for (i in 0 until n - 1) {
            for (j in 0 until n - i - 1) {
                if (arr[j] > arr[j + 1]) {
                    arr[j] = arr[j + 1].also { arr[j + 1] = arr[j] }
                }
            }
        }
    }

    fun mergeSort(arr: IntArray): IntArray {
        if (arr.size <= 1) return arr
        val mid = arr.size / 2
        val left = mergeSort(arr.sliceArray(0 until mid))
        val right = mergeSort(arr.sliceArray(mid until arr.size))
        return merge(left, right)
    }

    private fun merge(left: IntArray, right: IntArray): IntArray {
        val result = IntArray(left.size + right.size)
        var i = 0; var j = 0; var k = 0
        while (i < left.size && j < right.size) {
            if (left[i] <= right[j]) result[k++] = left[i++]
            else result[k++] = right[j++]
        }
        while (i < left.size) result[k++] = left[i++]
        while (j < right.size) result[k++] = right[j++]
        return result
    }
}

// microbenchmark/src/androidTest/kotlin/.../SortingBenchmark.kt
@RunWith(AndroidJUnit4::class)
class SortingBenchmark {

    @get:Rule
    val benchmarkRule = BenchmarkRule()

    private val random = Random(0)
    private val unsorted = IntArray(10_000) { random.nextInt() }

    @Test
    fun benchmarkQuickSort() {
        var arr: IntArray = intArrayOf()
        benchmarkRule.measureRepeated {
            arr = runWithTimingDisabled { unsorted.copyOf() }
            SortingAlgorithms.quickSort(arr)
        }
        assertTrue(arr.isSorted())
    }

    @Test
    fun benchmarkMergeSort() {
        var result: IntArray = intArrayOf()
        benchmarkRule.measureRepeated {
            val arr = runWithTimingDisabled { unsorted.copyOf() }
            result = SortingAlgorithms.mergeSort(arr)
        }
        assertTrue(result.isSorted())
    }

    @Test
    fun benchmarkKotlinSort() {
        var arr: IntArray = intArrayOf()
        benchmarkRule.measureRepeated {
            arr = runWithTimingDisabled { unsorted.copyOf() }
            arr.sort()  // Built-in TimSort
        }
        assertTrue(arr.isSorted())
    }

    private fun IntArray.isSorted() = zipWithNext().all { it.first <= it.second }
}
```

## 4. Allocation Benchmarks

### Measuring Object Creation Cost

```kotlin
@RunWith(AndroidJUnit4::class)
class AllocationBenchmark {

    @get:Rule
    val benchmarkRule = BenchmarkRule()

    // Measure Integer boxing within ART cache range (-128 to 127)
    @Test
    fun benchmarkIntegerCached() {
        var sum = 0
        benchmarkRule.measureRepeated {
            val integers = List(100) { Integer.valueOf(it % 128) }
            sum = integers.sumOf { it.toInt() }
        }
    }

    // Measure Integer boxing outside cache range
    @Test
    fun benchmarkIntegerUncached() {
        var sum = 0
        benchmarkRule.measureRepeated {
            val integers = List(100) { Integer.valueOf(1000 + it) }
            sum = integers.sumOf { it.toInt() }
        }
    }

    // Compare with primitive array
    @Test
    fun benchmarkPrimitiveArray() {
        var sum = 0
        benchmarkRule.measureRepeated {
            val ints = IntArray(100) { it }
            sum = ints.sum()
        }
    }

    // Measure data class allocation
    @Test
    fun benchmarkDataClassCreation() {
        data class Frame(val id: String, val timestamp: Long, val path: String)

        benchmarkRule.measureRepeated {
            val frames = List(100) {
                Frame("id_$it", System.currentTimeMillis(), "/path/$it")
            }
        }
    }

    // Measure reusing objects vs creating new
    @Test
    fun benchmarkObjectReuse() {
        class FrameBuilder {
            var id: String = ""
            var timestamp: Long = 0
            var path: String = ""

            fun build() = Frame(id, timestamp, path)
            fun reset() { id = ""; timestamp = 0; path = "" }
        }

        data class Frame(val id: String, val timestamp: Long, val path: String)

        val builder = FrameBuilder()

        benchmarkRule.measureRepeated {
            val frames = mutableListOf<Frame>()
            repeat(100) { i ->
                builder.id = "id_$i"
                builder.timestamp = System.currentTimeMillis()
                builder.path = "/path/$i"
                frames.add(builder.build())
                builder.reset()
            }
        }
    }
}
```

## 5. Image Processing Benchmarks

### Bitmap Operations

```kotlin
@LargeTest
@RunWith(AndroidJUnit4::class)
class BitmapBenchmark {

    @get:Rule
    val benchmarkRule = BenchmarkRule()

    private val context: Context =
        InstrumentationRegistry.getInstrumentation().targetContext

    private lateinit var testBitmap: Bitmap

    @Before
    fun setup() {
        testBitmap = context.assets.open("test_image.jpg").use {
            BitmapFactory.decodeStream(it)
        }
    }

    @After
    fun teardown() {
        testBitmap.recycle()
    }

    // Compare single pixel access vs bulk access
    @Test
    fun benchmarkGetPixelIndividual() {
        val width = 100
        val pixels = IntArray(width)
        benchmarkRule.measureRepeated {
            for (x in 0 until width) {
                pixels[x] = testBitmap.getPixel(x, 0)  // 100 JNI calls
            }
        }
    }

    @Test
    fun benchmarkGetPixelsBulk() {
        val width = 100
        val pixels = IntArray(width)
        benchmarkRule.measureRepeated {
            testBitmap.getPixels(pixels, 0, width, 0, 0, width, 1)  // 1 JNI call
        }
    }

    // Bitmap scaling comparison
    @Test
    fun benchmarkScaleBilinear() {
        benchmarkRule.measureRepeated {
            val scaled = Bitmap.createScaledBitmap(
                testBitmap, 200, 200, true  // Bilinear filtering
            )
            runWithTimingDisabled { scaled.recycle() }
        }
    }

    @Test
    fun benchmarkScaleNearestNeighbor() {
        benchmarkRule.measureRepeated {
            val scaled = Bitmap.createScaledBitmap(
                testBitmap, 200, 200, false  // Nearest neighbor
            )
            runWithTimingDisabled { scaled.recycle() }
        }
    }

    // Bitmap config comparison
    @Test
    fun benchmarkDecodingRGB565() {
        val options = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.RGB_565  // 2 bytes per pixel
        }
        benchmarkRule.measureRepeated {
            val bitmap = runWithTimingDisabled {
                context.assets.open("test_image.jpg")
            }.use {
                BitmapFactory.decodeStream(it, null, options)
            }
            runWithTimingDisabled { bitmap?.recycle() }
        }
    }

    @Test
    fun benchmarkDecodingARGB8888() {
        val options = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888  // 4 bytes per pixel
        }
        benchmarkRule.measureRepeated {
            val bitmap = runWithTimingDisabled {
                context.assets.open("test_image.jpg")
            }.use {
                BitmapFactory.decodeStream(it, null, options)
            }
            runWithTimingDisabled { bitmap?.recycle() }
        }
    }
}
```

### FrameLapse Image Processing

```kotlin
// benchmarkable/src/main/kotlin/.../ImageProcessing.kt
class ImageProcessor {

    fun grayscale(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        for (i in pixels.indices) {
            val pixel = pixels[i]
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            val gray = (0.299 * r + 0.587 * g + 0.114 * b).toInt()
            pixels[i] = (0xFF shl 24) or (gray shl 16) or (gray shl 8) or gray
        }

        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        result.setPixels(pixels, 0, width, 0, 0, width, height)
        return result
    }

    fun grayscaleParallel(bitmap: Bitmap, parallelism: Int = 4): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val chunkSize = pixels.size / parallelism
        runBlocking {
            (0 until parallelism).map { chunk ->
                async(Dispatchers.Default) {
                    val start = chunk * chunkSize
                    val end = if (chunk == parallelism - 1) pixels.size else start + chunkSize
                    for (i in start until end) {
                        val pixel = pixels[i]
                        val r = (pixel shr 16) and 0xFF
                        val g = (pixel shr 8) and 0xFF
                        val b = pixel and 0xFF
                        val gray = (0.299 * r + 0.587 * g + 0.114 * b).toInt()
                        pixels[i] = (0xFF shl 24) or (gray shl 16) or (gray shl 8) or gray
                    }
                }
            }.awaitAll()
        }

        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        result.setPixels(pixels, 0, width, 0, 0, width, height)
        return result
    }
}

// microbenchmark/src/androidTest/kotlin/.../ImageProcessingBenchmark.kt
@LargeTest
@RunWith(AndroidJUnit4::class)
class ImageProcessingBenchmark {

    @get:Rule
    val benchmarkRule = BenchmarkRule()

    private val processor = ImageProcessor()
    private lateinit var testBitmap: Bitmap

    @Before
    fun setup() {
        // Create test bitmap
        testBitmap = Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(testBitmap)
        val paint = Paint()
        // Fill with test pattern
        for (y in 0 until testBitmap.height step 10) {
            for (x in 0 until testBitmap.width step 10) {
                paint.color = Color.rgb(x % 256, y % 256, (x + y) % 256)
                canvas.drawRect(x.toFloat(), y.toFloat(), (x + 10).toFloat(), (y + 10).toFloat(), paint)
            }
        }
    }

    @After
    fun teardown() {
        testBitmap.recycle()
    }

    @Test
    fun benchmarkGrayscaleSequential() {
        benchmarkRule.measureRepeated {
            val result = processor.grayscale(testBitmap)
            runWithTimingDisabled { result.recycle() }
        }
    }

    @Test
    fun benchmarkGrayscaleParallel2() {
        benchmarkRule.measureRepeated {
            val result = processor.grayscaleParallel(testBitmap, parallelism = 2)
            runWithTimingDisabled { result.recycle() }
        }
    }

    @Test
    fun benchmarkGrayscaleParallel4() {
        benchmarkRule.measureRepeated {
            val result = processor.grayscaleParallel(testBitmap, parallelism = 4)
            runWithTimingDisabled { result.recycle() }
        }
    }

    @Test
    fun benchmarkGrayscaleParallel8() {
        benchmarkRule.measureRepeated {
            val result = processor.grayscaleParallel(testBitmap, parallelism = 8)
            runWithTimingDisabled { result.recycle() }
        }
    }
}
```

## 6. Serialization Benchmarks

### JSON Parsing Comparison

```kotlin
@RunWith(AndroidJUnit4::class)
class SerializationBenchmark {

    @get:Rule
    val benchmarkRule = BenchmarkRule()

    @Serializable
    data class Frame(
        val id: String,
        val projectId: String,
        val timestamp: Long,
        val imagePath: String,
        val thumbnailPath: String,
        val orderIndex: Int,
        val faceLandmarks: List<Float>,
    )

    private val testFrame = Frame(
        id = "frame_123",
        projectId = "project_456",
        timestamp = System.currentTimeMillis(),
        imagePath = "/storage/emulated/0/FrameLapse/frames/frame_123.jpg",
        thumbnailPath = "/storage/emulated/0/FrameLapse/thumbs/frame_123.jpg",
        orderIndex = 42,
        faceLandmarks = List(478 * 3) { it.toFloat() }  // 478 3D points
    )

    private val kotlinxJson = Json { ignoreUnknownKeys = true }

    @Test
    fun benchmarkKotlinxSerializationEncode() {
        benchmarkRule.measureRepeated {
            kotlinxJson.encodeToString(testFrame)
        }
    }

    @Test
    fun benchmarkKotlinxSerializationDecode() {
        val jsonString = kotlinxJson.encodeToString(testFrame)
        benchmarkRule.measureRepeated {
            kotlinxJson.decodeFromString<Frame>(jsonString)
        }
    }

    // Compare with manual JSON building
    @Test
    fun benchmarkManualJsonBuild() {
        benchmarkRule.measureRepeated {
            buildJsonObject {
                put("id", testFrame.id)
                put("projectId", testFrame.projectId)
                put("timestamp", testFrame.timestamp)
                put("imagePath", testFrame.imagePath)
                put("thumbnailPath", testFrame.thumbnailPath)
                put("orderIndex", testFrame.orderIndex)
                putJsonArray("faceLandmarks") {
                    testFrame.faceLandmarks.forEach { add(it) }
                }
            }.toString()
        }
    }
}
```

## 7. View/Compose Benchmarks

### View Inflation

```kotlin
@RunWith(AndroidJUnit4::class)
class ViewInflateBenchmark {

    @get:Rule
    val benchmarkRule = BenchmarkRule()

    private val context: Context =
        InstrumentationRegistry.getInstrumentation().context

    @Test
    fun benchmarkSimpleLayoutInflation() {
        val inflater = LayoutInflater.from(context)
        val root = FrameLayout(context)

        benchmarkRule.measureRepeated {
            val view = inflater.inflate(R.layout.item_frame_simple, root, false)
            runWithTimingDisabled { root.removeAllViews() }
        }
    }

    @Test
    fun benchmarkComplexLayoutInflation() {
        val inflater = LayoutInflater.from(context)
        val root = FrameLayout(context)

        benchmarkRule.measureRepeated {
            val view = inflater.inflate(R.layout.item_frame_complex, root, false)
            runWithTimingDisabled { root.removeAllViews() }
        }
    }
}
```

### RecyclerView Scroll

```kotlin
@LargeTest
@RunWith(AndroidJUnit4::class)
class RecyclerViewBenchmark {

    @get:Rule
    val benchmarkRule = BenchmarkRule()

    @get:Rule
    val activityRule = ActivityScenarioRule(BenchmarkActivity::class.java)

    @Before
    fun setup() {
        activityRule.scenario.onActivity { activity ->
            // Configure RecyclerView with 1px height (one item visible)
            activity.recyclerView.layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                1
            )
            // Load test data
            activity.adapter.submitList(List(1000) { Frame(id = "frame_$it") })
        }
    }

    @UiThreadTest
    @Test
    fun benchmarkScrollOneItem() {
        activityRule.scenario.onActivity { activity ->
            val recyclerView = activity.recyclerView

            benchmarkRule.measureRepeated {
                // Scroll by one item height - triggers bind/layout
                val lastChild = recyclerView.getChildAt(recyclerView.childCount - 1)
                recyclerView.scrollBy(0, lastChild?.height ?: 100)
            }
        }
    }
}
```

## 8. Running Benchmarks

### Command Line

```bash
# Run all microbenchmarks
./gradlew :microbenchmark:connectedAndroidTest

# Run specific benchmark class
./gradlew :microbenchmark:connectedAndroidTest \
    -Pandroid.testInstrumentationRunnerArguments.class=com.po4yka.framelapse.benchmark.SortingBenchmark

# With profiling
./gradlew :microbenchmark:connectedAndroidTest \
    -Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.profiling.mode=StackSampling
```

### Output Location

```
microbenchmark/build/outputs/connected_android_test_additional_output/
├── benchmarkData.json
└── [device]/
    └── [benchmark].trace
```

### Interpreting Results

```json
{
  "benchmarks": [
    {
      "name": "benchmarkQuickSort",
      "className": "SortingBenchmark",
      "metrics": {
        "timeNs": {
          "median": 125000,
          "min": 120000,
          "max": 145000,
          "runs": [121000, 125000, 123000, 145000, 124000]
        },
        "allocationCount": {
          "median": 5,
          "runs": [5, 5, 5, 5, 5]
        }
      }
    }
  ]
}
```

## Anti-Patterns

### Avoid Measuring Setup

```kotlin
// BAD: Setup included in measurement
@Test
fun badBenchmark() {
    benchmarkRule.measureRepeated {
        val list = List(10_000) { Random.nextInt() }  // Setup!
        list.sorted()
    }
}

// GOOD: Setup excluded
@Test
fun goodBenchmark() {
    benchmarkRule.measureRepeated {
        val list = runWithTimingDisabled { List(10_000) { Random.nextInt() } }
        list.sorted()
    }
}
```

### Avoid Non-Deterministic Input

```kotlin
// BAD: Random seed changes results
@Test
fun badBenchmark() {
    val list = List(10_000) { Random.nextInt() }  // Different each run!
}

// GOOD: Fixed seed for reproducibility
@Test
fun goodBenchmark() {
    val random = Random(42)  // Deterministic
    val list = List(10_000) { random.nextInt() }
}
```

### Avoid Benchmarking Dead Code

```kotlin
// BAD: Compiler might optimize away
@Test
fun badBenchmark() {
    benchmarkRule.measureRepeated {
        val result = expensiveCalculation()
        // result never used - might be optimized out
    }
}

// GOOD: Use result to prevent optimization
private var blackhole: Any? = null

@Test
fun goodBenchmark() {
    benchmarkRule.measureRepeated {
        blackhole = expensiveCalculation()
    }
}
```

## Reference Examples

- Performance best practices: `.claude/skills/performance-best-practices/SKILL.md`
- Macrobenchmark patterns: `.claude/skills/macrobenchmark-patterns/SKILL.md`
- MicrobenchmarkSample: `performance-samples/MicrobenchmarkSample/`

## Checklist

### Module Setup
- [ ] Separate benchmarkable module for code under test
- [ ] Microbenchmark module with AndroidBenchmarkRunner
- [ ] testBuildType set to release
- [ ] No minification on benchmark code

### Test Quality
- [ ] Setup excluded with runWithTimingDisabled
- [ ] Deterministic inputs (fixed random seeds)
- [ ] Results used (prevent dead code elimination)
- [ ] Assertions after benchmark (not in hot loop)

### Analysis
- [ ] Sufficient iterations for statistical significance
- [ ] Multiple approaches compared
- [ ] Results documented and tracked
- [ ] Profile data analyzed for hotspots
