---
name: baseline-profile-generation
description: AOT compilation optimization through baseline profile generation, startup profiles, and profile distribution. Use when optimizing app startup and critical paths.
---

# Baseline Profile Generation

## Overview

Baseline Profiles are lists of classes and methods that should be pre-compiled (AOT) when the app is installed. They significantly improve app startup time and reduce jank during initial interactions by avoiding JIT compilation overhead.

## When to Use

- Improving app startup time
- Reducing jank on first scroll/interaction
- Optimizing critical user paths (login, checkout, capture)
- Preparing for app release
- Reducing time-to-interactive metrics

## 1. Understanding Baseline Profiles

### How They Work

```
Without Baseline Profile:
┌─────────────────────────────────────────┐
│ App Install                             │
│ └── DEX files stored                    │
├─────────────────────────────────────────┤
│ First Launch                            │
│ ├── Code interpreted (slow)             │
│ ├── JIT compiles hot methods            │
│ └── Performance improves over time      │
├─────────────────────────────────────────┤
│ Subsequent Launches                     │
│ └── Better, but still JIT dependent     │
└─────────────────────────────────────────┘

With Baseline Profile:
┌─────────────────────────────────────────┐
│ App Install                             │
│ ├── DEX files stored                    │
│ └── Profiled methods AOT compiled       │
├─────────────────────────────────────────┤
│ First Launch                            │
│ ├── Critical code already compiled      │
│ └── Fast startup immediately            │
├─────────────────────────────────────────┤
│ All Launches                            │
│ └── Consistent, optimized performance   │
└─────────────────────────────────────────┘
```

### Typical Improvements

| Metric | Improvement |
|--------|-------------|
| Cold startup | 20-40% faster |
| First scroll | 30-50% smoother |
| First interaction | 15-30% faster |

## 2. Module Setup

### Dependencies

```kotlin
// composeApp/build.gradle.kts
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("androidx.baselineprofile")
}

android {
    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(...)

            // Generate baseline profile during release build
            baselineProfile.automaticGenerationDuringBuild = true
        }
    }
}

dependencies {
    // Required for profile installation
    implementation(libs.androidx.profileinstaller)

    // Link to benchmark module that generates profiles
    baselineProfile(project(":macrobenchmark"))
}
```

### Benchmark Module

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
        minSdk = 28
        targetSdk = 35
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    targetProjectPath = ":composeApp"
    experimentalProperties["android.experimental.self-instrumenting"] = true

    testOptions.managedDevices.devices {
        create<ManagedVirtualDevice>("pixel6Api31") {
            device = "Pixel 6"
            apiLevel = 31
            systemImageSource = "aosp-atd"
        }
    }
}

baselineProfile {
    managedDevices += "pixel6Api31"
    useConnectedDevices = false
}

dependencies {
    implementation(libs.androidx.benchmark.macro.junit4)
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.test.uiautomator)
}
```

## 3. Profile Generators

### Startup Profile Generator

Optimizes the app startup path:

```kotlin
// macrobenchmark/src/main/kotlin/.../baselineprofile/StartupProfileGenerator.kt
@RunWith(AndroidJUnit4::class)
class StartupProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generateStartupProfile() {
        rule.collect(
            packageName = TARGET_PACKAGE,
            maxIterations = 15,
            stableIterations = 3,
            includeInStartupProfile = true,  // Startup-specific profile
        ) {
            // Just start the app - captures startup code paths
            pressHome()
            startActivityAndWait()
        }
    }
}
```

### Full User Journey Profile

Optimizes common user flows:

```kotlin
// macrobenchmark/src/main/kotlin/.../baselineprofile/UserJourneyProfileGenerator.kt
@RunWith(AndroidJUnit4::class)
class UserJourneyProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generateFullProfile() {
        rule.collect(
            packageName = TARGET_PACKAGE,
            maxIterations = 15,
            stableIterations = 3,
        ) {
            // Start app
            pressHome()
            startActivityAndWait()

            // Navigate through key screens
            navigateToProjectList()
            selectFirstProject()
            scrollGallery()
            navigateToCapture()
            navigateToSettings()
            navigateBack()
        }
    }

    private fun MacrobenchmarkScope.navigateToProjectList() {
        // Already on project list after startup
        device.waitForIdle()
    }

    private fun MacrobenchmarkScope.selectFirstProject() {
        device.findObject(
            By.res(TARGET_PACKAGE, "project_item")
        ).click()
        device.waitForIdle()
    }

    private fun MacrobenchmarkScope.scrollGallery() {
        val gallery = device.findObject(By.scrollable(true))
        repeat(3) {
            gallery?.fling(Direction.DOWN)
            device.waitForIdle()
        }
        repeat(3) {
            gallery?.fling(Direction.UP)
            device.waitForIdle()
        }
    }

    private fun MacrobenchmarkScope.navigateToCapture() {
        device.findObject(
            By.res(TARGET_PACKAGE, "capture_fab")
        ).click()
        device.waitForIdle()
        // Wait for camera to initialize
        Thread.sleep(1000)
        device.pressBack()
    }

    private fun MacrobenchmarkScope.navigateToSettings() {
        device.findObject(
            By.res(TARGET_PACKAGE, "settings_button")
        ).click()
        device.waitForIdle()
    }

    private fun MacrobenchmarkScope.navigateBack() {
        device.pressBack()
        device.waitForIdle()
    }
}
```

### FrameLapse-Specific Profile Generator

```kotlin
// macrobenchmark/src/main/kotlin/.../baselineprofile/FrameLapseProfileGenerator.kt
@RunWith(AndroidJUnit4::class)
class FrameLapseProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generateProfile() {
        rule.collect(
            packageName = TARGET_PACKAGE,
            maxIterations = 15,
            stableIterations = 3,
        ) {
            // Core user journeys for FrameLapse
            pressHome()
            startActivityAndWait()

            // Journey 1: View existing project gallery
            selectExistingProject()
            scrollFrameGallery()
            viewFrameDetail()
            device.pressBack()

            // Journey 2: Capture new frame
            navigateToCaptureScreen()
            waitForCameraPreview()
            // Don't actually capture - just prime the code paths
            device.pressBack()

            // Journey 3: Export flow (UI only)
            openExportDialog()
            dismissDialog()

            // Journey 4: Settings
            openSettings()
            scrollSettings()
            device.pressBack()
        }
    }

    private fun MacrobenchmarkScope.selectExistingProject() {
        device.wait(
            Until.hasObject(By.res(TARGET_PACKAGE, "project_list")),
            5000
        )
        device.findObject(By.res(TARGET_PACKAGE, "project_item"))?.click()
        device.waitForIdle()
    }

    private fun MacrobenchmarkScope.scrollFrameGallery() {
        val gallery = device.findObject(
            By.res(TARGET_PACKAGE, "frame_gallery")
        ) ?: device.findObject(By.scrollable(true))

        gallery?.let {
            repeat(5) {
                gallery.fling(Direction.DOWN)
                device.waitForIdle()
            }
        }
    }

    private fun MacrobenchmarkScope.viewFrameDetail() {
        device.findObject(By.res(TARGET_PACKAGE, "frame_item"))?.click()
        device.waitForIdle()
    }

    private fun MacrobenchmarkScope.navigateToCaptureScreen() {
        device.findObject(By.res(TARGET_PACKAGE, "capture_fab"))?.click()
            ?: device.findObject(By.desc("Capture"))?.click()
        device.waitForIdle()
    }

    private fun MacrobenchmarkScope.waitForCameraPreview() {
        device.wait(
            Until.hasObject(By.res(TARGET_PACKAGE, "camera_preview")),
            5000
        )
        // Give camera time to initialize
        Thread.sleep(2000)
    }

    private fun MacrobenchmarkScope.openExportDialog() {
        device.pressBack()  // Back to gallery
        device.findObject(By.res(TARGET_PACKAGE, "export_button"))?.click()
            ?: device.findObject(By.desc("Export"))?.click()
        device.waitForIdle()
    }

    private fun MacrobenchmarkScope.dismissDialog() {
        device.pressBack()
        device.waitForIdle()
    }

    private fun MacrobenchmarkScope.openSettings() {
        device.findObject(By.res(TARGET_PACKAGE, "settings_button"))?.click()
            ?: device.findObject(By.desc("Settings"))?.click()
        device.waitForIdle()
    }

    private fun MacrobenchmarkScope.scrollSettings() {
        val settingsList = device.findObject(By.scrollable(true))
        settingsList?.fling(Direction.DOWN)
        device.waitForIdle()
        settingsList?.fling(Direction.UP)
        device.waitForIdle()
    }
}
```

## 4. Running Profile Generation

### Using Connected Device

```bash
# Generate baseline profile
./gradlew :composeApp:generateBaselineProfile

# Generate using specific generator
./gradlew :macrobenchmark:pixel6Api31BenchmarkAndroidTest \
    -P android.testInstrumentationRunnerArguments.class=\
    com.po4yka.framelapse.benchmark.baselineprofile.FrameLapseProfileGenerator
```

### Using Managed Virtual Device

```bash
# Create and use managed device
./gradlew :macrobenchmark:pixel6Api31Setup
./gradlew :composeApp:generateBaselineProfile
```

### Output Location

```
composeApp/src/main/baselineProfiles/
├── baseline-prof.txt          # Full profile
└── startup-prof.txt           # Startup-only profile (if generated separately)
```

## 5. Profile File Format

### Understanding Profile Contents

```
# baseline-prof.txt format
# Lines starting with H, S, P indicate different profile types

# Hot methods (H) - frequently executed
HSPLcom/po4yka/framelapse/MainActivity;->onCreate(Landroid/os/Bundle;)V

# Startup methods (S) - executed during startup
SPLcom/po4yka/framelapse/di/KoinModulesKt;->appModule()Lorg/koin/core/module/Module;

# Post-startup methods (P) - executed after startup
PLcom/po4yka/framelapse/presentation/gallery/GalleryViewModel;->loadFrames()V

# Class references
Lcom/po4yka/framelapse/domain/model/Frame;
Lcom/po4yka/framelapse/data/repository/FrameRepositoryImpl;
```

### Modular Profiles (AGP 8.0+)

Store profiles in organized files:

```
composeApp/src/main/baselineProfiles/
├── core.txt              # Core app functionality
├── gallery.txt           # Gallery feature
├── capture.txt           # Capture feature
├── export.txt            # Export feature
└── compose-runtime.txt   # Compose framework
```

```kotlin
// build.gradle.kts
android {
    baselineProfile {
        // Merge all profile files
        mergeIntoMain = true
    }
}
```

## 6. Validating Profiles

### Benchmarking Profile Impact

```kotlin
@RunWith(AndroidJUnit4::class)
class ProfileValidationBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun startupWithoutProfile() {
        benchmarkRule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(StartupTimingMetric()),
            compilationMode = CompilationMode.None(),  // No compilation
            iterations = 5,
            startupMode = StartupMode.COLD,
        ) {
            startActivityAndWait()
        }
    }

    @Test
    fun startupWithBaselineProfile() {
        benchmarkRule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(StartupTimingMetric()),
            compilationMode = CompilationMode.Partial(
                baselineProfileMode = BaselineProfileMode.Require
            ),
            iterations = 5,
            startupMode = StartupMode.COLD,
        ) {
            startActivityAndWait()
        }
    }

    @Test
    fun startupFullyCompiled() {
        benchmarkRule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(StartupTimingMetric()),
            compilationMode = CompilationMode.Full(),  // Full AOT (best case)
            iterations = 5,
            startupMode = StartupMode.COLD,
        ) {
            startActivityAndWait()
        }
    }
}
```

### Expected Results

```
CompilationMode.None():
  timeToInitialDisplayMs: 950ms (baseline - worst case)

CompilationMode.Partial(Require):
  timeToInitialDisplayMs: 620ms (35% improvement)

CompilationMode.Full():
  timeToInitialDisplayMs: 550ms (42% improvement - best case reference)
```

## 7. CI/CD Integration

### GitHub Actions Workflow

```yaml
# .github/workflows/baseline-profile.yml
name: Generate Baseline Profile

on:
  push:
    branches: [main]
  workflow_dispatch:

jobs:
  generate-profile:
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
          echo 'KERNEL=="kvm", GROUP="kvm", MODE="0666"' | sudo tee /etc/udev/rules.d/99-kvm.rules
          sudo udevadm control --reload-rules
          sudo udevadm trigger --name-match=kvm

      - name: Accept Android SDK licenses
        run: yes | $ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager --licenses

      - name: Generate Baseline Profile
        run: ./gradlew :composeApp:generateBaselineProfile

      - name: Commit profile
        run: |
          git config user.name "GitHub Actions"
          git config user.email "actions@github.com"
          git add composeApp/src/main/baselineProfiles/
          git diff --staged --quiet || git commit -m "Update baseline profile"
          git push
```

### Verifying Profile in Release

```kotlin
// In Application class or debug menu
class FrameLapseApp : Application() {
    override fun onCreate() {
        super.onCreate()

        if (BuildConfig.DEBUG) {
            // Check if profile is installed
            ProfileVerifier.getCompilationStatusAsync().let { future ->
                future.addListener({
                    val status = future.get()
                    Log.d("BaselineProfile", "Profile status: ${status.profileInstallResultCode}")
                    Log.d("BaselineProfile", "Has compiled: ${status.isCompiledWithProfile}")
                }, ContextCompat.getMainExecutor(this))
            }
        }
    }
}
```

## 8. Best Practices

### Profile Coverage Strategy

```kotlin
// Cover critical paths based on analytics/usage data
@Test
fun generateComprehensiveProfile() {
    rule.collect(packageName = TARGET_PACKAGE) {
        // 1. Startup (required)
        startActivityAndWait()

        // 2. Most common user action (based on analytics)
        // e.g., 80% of users view gallery first
        scrollGallery()

        // 3. Core feature (capture)
        navigateToCapture()

        // 4. Secondary features (based on usage frequency)
        openSettings()
        viewFrameDetail()

        // 5. Edge cases that affect perception
        // e.g., first export is slow without profile
        openExportPreview()
    }
}
```

### Stable Iterations

```kotlin
rule.collect(
    packageName = TARGET_PACKAGE,
    maxIterations = 15,    // Maximum attempts
    stableIterations = 3,  // Stop when profile stabilizes for 3 iterations
) {
    // Journey
}
```

### Profile Size Considerations

```kotlin
// Large profiles (>500KB) may indicate:
// - Too many code paths covered
// - Third-party library bloat
// - Consider splitting into modular profiles

// Check profile size
val profileFile = File("composeApp/src/main/baselineProfiles/baseline-prof.txt")
val sizeKb = profileFile.length() / 1024
Log.d("Profile", "Profile size: ${sizeKb}KB")

// Recommendation: Keep under 500KB for optimal install time
```

## Anti-Patterns

### Avoid Covering Everything

```kotlin
// BAD: Trying to profile every feature
@Test
fun generateEverything() {
    rule.collect(packageName = TARGET_PACKAGE) {
        startActivityAndWait()
        // Visit every screen
        // Click every button
        // Open every dialog
        // ...hundreds of actions
    }
}

// GOOD: Focus on critical paths
@Test
fun generateCriticalPaths() {
    rule.collect(packageName = TARGET_PACKAGE) {
        startActivityAndWait()  // Startup
        scrollMainList()        // Common action
        openMostUsedFeature()   // Based on analytics
    }
}
```

### Avoid Unstable Navigation

```kotlin
// BAD: Random waits
Thread.sleep(5000)

// GOOD: Wait for specific conditions
device.wait(Until.hasObject(By.res(TARGET_PACKAGE, "content")), 5000)
device.waitForIdle()
```

### Avoid Profile Staleness

```kotlin
// Regenerate profiles when:
// - Major dependency updates (Compose, libraries)
// - New features added to critical paths
// - Performance regression detected
// - Quarterly as maintenance

// CI check for profile age
val profileFile = File("baseline-prof.txt")
val ageInDays = (System.currentTimeMillis() - profileFile.lastModified()) / (1000 * 60 * 60 * 24)
if (ageInDays > 90) {
    warning("Baseline profile is $ageInDays days old. Consider regenerating.")
}
```

## Reference Examples

- Macrobenchmark patterns: `.claude/skills/macrobenchmark-patterns/SKILL.md`
- Performance best practices: `.claude/skills/performance-best-practices/SKILL.md`
- MacrobenchmarkSample profiles: `performance-samples/MacrobenchmarkSample/macrobenchmark/src/main/kotlin/.../baselineprofile/`

## Checklist

### Setup
- [ ] ProfileInstaller dependency added to app
- [ ] baselineProfile plugin applied
- [ ] Benchmark module configured with BaselineProfileRule
- [ ] Managed virtual device configured

### Profile Generation
- [ ] Startup profile generator implemented
- [ ] Critical user journeys covered
- [ ] Profile generated on consistent device/emulator
- [ ] Profile file committed to source control

### Validation
- [ ] Benchmarks show improvement with profile
- [ ] Profile size is reasonable (<500KB)
- [ ] ProfileVerifier confirms installation in debug builds
- [ ] No regressions in non-profiled paths

### Maintenance
- [ ] CI job for profile generation
- [ ] Profile regenerated on major updates
- [ ] Profile age monitored
- [ ] Performance metrics tracked over time
