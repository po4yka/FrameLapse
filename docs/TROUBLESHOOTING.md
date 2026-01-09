# Troubleshooting Guide

This guide covers common issues and their solutions when developing or using FrameLapse.

## Table of Contents

- [Build Issues](#build-issues)
- [Runtime Issues](#runtime-issues)
- [Face Detection Issues](#face-detection-issues)
- [Video Export Issues](#video-export-issues)
- [Platform-Specific Issues](#platform-specific-issues)
- [Performance Issues](#performance-issues)
- [Testing Issues](#testing-issues)

---

## Build Issues

### Gradle Sync Fails

**Symptoms:**
- Android Studio shows sync errors
- "Could not resolve dependency" messages
- Kotlin version mismatch errors

**Solutions:**

1. **Invalidate caches and restart:**
   ```
   File > Invalidate Caches > Invalidate and Restart
   ```

2. **Clean Gradle cache:**
   ```shell
   rm -rf ~/.gradle/caches
   ./gradlew clean
   ```

3. **Verify JDK version:**
   ```shell
   java -version
   # Should show JDK 17 or higher
   ```

4. **Check Android Studio JDK settings:**
   ```
   File > Project Structure > SDK Location > JDK Location
   ```

5. **Force dependency resolution:**
   ```shell
   ./gradlew --refresh-dependencies
   ```

---

### Out of Memory During Build

**Symptoms:**
- "Java heap space" error
- Build fails during compilation
- System becomes unresponsive

**Solutions:**

1. **Increase Gradle memory** in `gradle.properties`:
   ```properties
   org.gradle.jvmargs=-Xmx6144M -Dfile.encoding=UTF-8
   ```

2. **Increase Kotlin daemon memory**:
   ```properties
   kotlin.daemon.jvmargs=-Xmx3072M
   ```

3. **Disable configuration cache temporarily**:
   ```properties
   org.gradle.configuration-cache=false
   ```

4. **Build specific module only**:
   ```shell
   ./gradlew :composeApp:assembleDebug
   ```

---

### Spotless/Formatting Failures

**Symptoms:**
- CI fails on spotlessCheck
- "The following files had format violations" error

**Solutions:**

1. **Auto-fix formatting:**
   ```shell
   ./gradlew spotlessApply
   ```

2. **Check specific files:**
   ```shell
   ./gradlew spotlessCheck --info
   ```

3. **Verify `.editorconfig` exists** in project root

---

### iOS Build Fails

**Symptoms:**
- "Module not found" errors
- CocoaPods errors
- Xcode build failures

**Solutions:**

1. **Install/update CocoaPods:**
   ```shell
   brew install cocoapods
   # or update existing
   brew upgrade cocoapods
   ```

2. **Install pods:**
   ```shell
   cd iosApp
   pod install
   ```

3. **If pod install fails:**
   ```shell
   pod repo update
   pod deintegrate
   pod install
   ```

4. **Clean Xcode derived data:**
   ```shell
   rm -rf ~/Library/Developer/Xcode/DerivedData
   ```

5. **Reset package caches:**
   ```
   Xcode > File > Packages > Reset Package Caches
   ```

---

## Runtime Issues

### App Crashes on Launch

**Symptoms:**
- Immediate crash after launch
- "Application not responding" (ANR)
- Black screen

**Possible Causes & Solutions:**

1. **Missing ML model (Android):**
   - Ensure `face_landmarker.task` is in `composeApp/src/androidMain/assets/`

2. **Database migration issue:**
   - Clear app data or reinstall
   - Check SQLDelight migrations

3. **Permission not granted:**
   - Ensure camera permission is requested and granted

4. **Check logcat (Android):**
   ```shell
   adb logcat | grep -i "FrameLapse\|crash\|exception"
   ```

---

### Camera Preview Not Showing

**Symptoms:**
- Black screen on capture screen
- Camera permission granted but no preview

**Solutions:**

1. **Check camera availability:**
   ```kotlin
   val hasCamera = context.packageManager
       .hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
   ```

2. **Android: Ensure CameraX is initialized:**
   - Check logcat for CameraX errors

3. **iOS: Check AVFoundation setup:**
   - Verify Info.plist has camera usage description

4. **Test on physical device** (emulators have limited camera support)

---

### Database Errors

**Symptoms:**
- "Cannot access database" errors
- Data not persisting
- Corruption errors

**Solutions:**

1. **Clear app data:**
   ```shell
   # Android
   adb shell pm clear com.po4yka.framelapse
   ```

2. **Check database version:**
   - Verify SQLDelight schema version matches migrations

3. **Enable database debugging:**
   ```kotlin
   // Add logging to see SQL queries
   Database.Schema.create(driver)
   ```

---

## Face Detection Issues

### No Face Detected

**Symptoms:**
- Face detection returns null
- Low confidence scores
- "Face not found" messages

**Solutions:**

1. **Improve lighting:**
   - Ensure even, front-facing light
   - Avoid backlighting

2. **Center face in frame:**
   - Face should occupy 30-50% of frame
   - Eyes clearly visible

3. **Check confidence thresholds:**
   - Standard: 0.5 (stored images)
   - Realtime: 0.3 (preview)

4. **Debug detection:**
   ```kotlin
   val result = faceDetector.detectFace(imageData)
   println("Detection result: $result")
   println("Confidence: ${result.getOrNull()?.confidence}")
   ```

---

### Alignment Looks Wrong

**Symptoms:**
- Face not centered after alignment
- Eyes not horizontal
- Scale is off

**Solutions:**

1. **Re-calibrate reference:**
   - Go to Calibration screen
   - Set new reference position

2. **Check stabilization score:**
   - Score should be < 20.0 for success
   - Score >= 20.0 indicates failure

3. **Use SLOW mode for export:**
   ```kotlin
   alignFaceUseCase(frame, reference, StabilizationMode.SLOW)
   ```

4. **Manual adjustment:**
   - Use ManualAdjustmentScreen to correct landmarks

5. **Verify reference frame:**
   - Reference should have high confidence (> 0.7)
   - Face clearly visible and centered

---

### MediaPipe Initialization Fails (Android)

**Symptoms:**
- "Failed to initialize FaceLandmarker" error
- GPU delegate errors

**Solutions:**

1. **Check model file:**
   ```shell
   ls composeApp/src/androidMain/assets/face_landmarker.task
   ```

2. **CPU fallback:**
   - If GPU fails, implementation automatically falls back to CPU
   - Check logs for fallback message

3. **Update MediaPipe:**
   - Check for newer version in `libs.versions.toml`

---

## Video Export Issues

### Export Fails or Crashes

**Symptoms:**
- "Export failed" error
- App crashes during export
- Incomplete video file

**Solutions:**

1. **Check available storage:**
   ```kotlin
   // Estimate: ~2MB per frame
   val requiredSpace = frameCount * 2_000_000L
   ```

2. **Reduce resolution:**
   - Try 720p instead of 1080p/4K

3. **Check codec support:**
   ```kotlin
   val codecs = videoEncoder.getSupportedCodecs()
   println("Supported: $codecs")
   ```

4. **Use H.264 fallback:**
   - HEVC may not be supported on older devices

5. **Process in background:**
   - Ensure export runs on IO dispatcher

---

### Video Quality Issues

**Symptoms:**
- Pixelated or blurry output
- Compression artifacts
- Wrong aspect ratio

**Solutions:**

1. **Increase quality preset:**
   ```kotlin
   ExportSettings(quality = ExportQuality.HIGH)
   ```

2. **Verify source frame quality:**
   - Check original images are not corrupted

3. **Check bitrate calculation:**
   ```kotlin
   // bitrate = width × height × fps × 0.1 × quality
   val bitrate = 1920 * 1080 * 30 * 0.1 * 2.0  // ~12 Mbps for HIGH
   ```

---

### Export Takes Too Long

**Symptoms:**
- Progress stuck
- Very slow encoding

**Solutions:**

1. **Use hardware acceleration:**
   - Verify MediaCodec/AVAssetWriter is using hardware encoder

2. **Reduce frame count:**
   - Export subset of frames for preview

3. **Lower resolution:**
   - 720p encodes faster than 1080p/4K

4. **Profile performance:**
   ```kotlin
   val startTime = System.currentTimeMillis()
   // ... encoding
   val elapsed = System.currentTimeMillis() - startTime
   println("Encoding took: ${elapsed}ms")
   ```

---

## Platform-Specific Issues

### Android-Specific

#### ProGuard/R8 Issues (Release Build)

**Symptoms:**
- Release build crashes but debug works
- "Class not found" in release

**Solutions:**

1. **Check ProGuard rules:**
   - Ensure SQLDelight, Koin, Serialization keep rules exist

2. **Add keep rules:**
   ```proguard
   -keep class com.po4yka.framelapse.** { *; }
   -keep class kotlinx.serialization.** { *; }
   ```

---

#### 64-bit Native Library Issues

**Symptoms:**
- Crashes on certain devices
- "UnsatisfiedLinkError"

**Solutions:**

1. **Ensure all ABIs are included:**
   ```kotlin
   ndk {
       abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
   }
   ```

2. **Check OpenCV native libraries**

---

### iOS-Specific

#### Vision Framework Not Available

**Symptoms:**
- Face detection fails on iOS
- "VNDetectFaceLandmarksRequest" errors

**Solutions:**

1. **Check iOS version:**
   - Vision Framework requires iOS 11+
   - Face landmarks requires iOS 12+

2. **Check entitlements:**
   - No special entitlements needed for Vision

---

#### Memory Pressure

**Symptoms:**
- App killed by system
- "Received memory warning"

**Solutions:**

1. **Release image references:**
   ```swift
   autoreleasepool {
       // Image processing
   }
   ```

2. **Process frames in batches**

---

## Performance Issues

### Slow Face Detection

**Symptoms:**
- Preview lag
- Detection takes > 100ms

**Solutions:**

1. **Use realtime mode for preview:**
   ```kotlin
   faceDetector.detectFaceRealtime(imageData)
   ```

2. **Reduce image resolution for detection:**
   - Scale down before detection, scale landmarks back up

3. **Skip frames:**
   - Detect every 2-3 frames for preview

---

### UI Jank During Processing

**Symptoms:**
- UI freezes during operations
- Dropped frames

**Solutions:**

1. **Ensure background dispatchers:**
   ```kotlin
   withContext(Dispatchers.IO) {
       // Heavy processing
   }
   ```

2. **Use loading states:**
   - Show progress indicators during operations

3. **Batch updates:**
   - Collect changes and update UI once

---

## Testing Issues

### Tests Fail on CI but Pass Locally

**Symptoms:**
- Green locally, red on CI
- Flaky tests

**Solutions:**

1. **Check time-dependent tests:**
   - Use fixed clocks in tests

2. **Ensure test isolation:**
   - Reset state between tests

3. **Run with same configuration:**
   ```shell
   ./gradlew test --no-daemon --rerun-tasks
   ```

---

### Mock/Fake Issues

**Symptoms:**
- "No implementation found" for expect/actual
- MockK verification failures

**Solutions:**

1. **Use fakes from test-utils:**
   ```kotlin
   val fakeRepository = FakeFrameRepository()
   ```

2. **Verify mock setup:**
   ```kotlin
   every { mock.method() } returns value
   verify { mock.method() }
   ```

---

## Getting More Help

If you can't resolve your issue:

1. **Check existing issues:**
   [GitHub Issues](https://github.com/po4yka/FrameLapse/issues)

2. **Enable verbose logging:**
   ```shell
   ./gradlew build --info --stacktrace
   ```

3. **Collect diagnostic info:**
   - Device model and OS version
   - App version
   - Steps to reproduce
   - Relevant logs

4. **Open a new issue** with the above information
