---
name: platform-abstraction
description: Implements expect/actual patterns for platform-specific features with capability detection and graceful fallbacks. Use when adding platform features, implementing native bridges, or detecting device capabilities.
---

# Platform Abstraction

## Overview

This skill helps implement platform-specific features using Kotlin Multiplatform's expect/actual mechanism. It covers interface design, capability detection, graceful degradation, and platform DI setup.

## Core Pattern: Interface + expect/actual

### 1. Define Domain Interface (commonMain)

```kotlin
// domain/service/FaceDetector.kt
interface FaceDetector {
    /**
     * Whether face detection is available on this device.
     */
    val isAvailable: Boolean

    /**
     * Detects a face in the given image.
     *
     * @param imagePath Path to the image file.
     * @return FaceLandmarks if face detected, null otherwise.
     */
    suspend fun detectFace(imagePath: String): FaceLandmarks?

    /**
     * Detects a face in raw image data.
     *
     * @param imageData Raw image bytes.
     * @param width Image width.
     * @param height Image height.
     * @return FaceLandmarks if face detected, null otherwise.
     */
    suspend fun detectFace(imageData: ByteArray, width: Int, height: Int): FaceLandmarks?
}
```

### 2. Android Implementation (androidMain)

```kotlin
// platform/FaceDetectorImpl.kt (androidMain)
class FaceDetectorImpl(
    private val context: Context,
) : FaceDetector {

    private val detector: com.google.mlkit.vision.face.FaceDetector? = try {
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
            .build()
        FaceDetection.getClient(options)
    } catch (e: Exception) {
        null
    }

    override val isAvailable: Boolean = detector != null

    override suspend fun detectFace(imagePath: String): FaceLandmarks? =
        withContext(Dispatchers.IO) {
            if (!isAvailable) return@withContext null

            try {
                val bitmap = BitmapFactory.decodeFile(imagePath)
                val inputImage = InputImage.fromBitmap(bitmap, 0)
                val faces = detector!!.process(inputImage).await()

                faces.firstOrNull()?.let { face ->
                    FaceLandmarks(
                        leftEye = face.getLandmark(FaceLandmark.LEFT_EYE)?.toPoint(),
                        rightEye = face.getLandmark(FaceLandmark.RIGHT_EYE)?.toPoint(),
                        nose = face.getLandmark(FaceLandmark.NOSE_BASE)?.toPoint(),
                        confidence = face.trackingId?.toFloat() ?: 0.9f,
                    )
                }
            } catch (e: Exception) {
                null
            }
        }

    override suspend fun detectFace(imageData: ByteArray, width: Int, height: Int): FaceLandmarks? {
        // Implementation using byte array
    }

    private fun FaceLandmark.toPoint() = LandmarkPoint(
        x = position.x,
        y = position.y,
    )
}
```

### 3. iOS Implementation (iosMain)

```kotlin
// platform/FaceDetectorImpl.kt (iosMain)
class FaceDetectorImpl : FaceDetector {

    override val isAvailable: Boolean = true  // Vision framework always available on iOS

    override suspend fun detectFace(imagePath: String): FaceLandmarks? =
        withContext(Dispatchers.Default) {
            try {
                val image = UIImage.imageWithContentsOfFile(imagePath) ?: return@withContext null
                val ciImage = CIImage(image = image) ?: return@withContext null

                detectFaceInCIImage(ciImage)
            } catch (e: Exception) {
                null
            }
        }

    override suspend fun detectFace(imageData: ByteArray, width: Int, height: Int): FaceLandmarks? =
        withContext(Dispatchers.Default) {
            try {
                val nsData = imageData.usePinned { pinned ->
                    NSData.create(bytes = pinned.addressOf(0), length = imageData.size.toULong())
                }
                val image = UIImage(data = nsData) ?: return@withContext null
                val ciImage = CIImage(image = image) ?: return@withContext null

                detectFaceInCIImage(ciImage)
            } catch (e: Exception) {
                null
            }
        }

    private fun detectFaceInCIImage(ciImage: CIImage): FaceLandmarks? {
        val request = VNDetectFaceLandmarksRequest()
        val handler = VNImageRequestHandler(ciImage, emptyMap<Any?, Any?>())

        handler.performRequests(listOf(request), null)

        val observation = request.results?.firstOrNull() as? VNFaceObservation
            ?: return null

        val landmarks = observation.landmarks ?: return null

        return FaceLandmarks(
            leftEye = landmarks.leftEye?.normalizedPoints?.firstOrNull()?.toPoint(),
            rightEye = landmarks.rightEye?.normalizedPoints?.firstOrNull()?.toPoint(),
            nose = landmarks.nose?.normalizedPoints?.firstOrNull()?.toPoint(),
            confidence = observation.confidence,
        )
    }
}
```

## Platform Module Registration

### Android (androidMain)

```kotlin
// di/KoinModules.android.kt
actual val platformModule: Module = module {
    // Context-dependent services
    single { DatabaseDriverFactory(androidContext()) }
    single { FileManager(androidContext()) }

    // ML/AI services
    single<FaceDetector> { FaceDetectorImpl(androidContext()) }
    single<BodyPoseDetector> { BodyPoseDetectorImpl(androidContext()) }
    single<ImageProcessor> { ImageProcessorImpl(androidContext()) }

    // Media services
    single<VideoEncoder> { VideoEncoderImpl(androidContext()) }
    single<GifEncoder> { GifEncoderImpl(androidContext()) }
    single<CameraController> { CameraControllerImpl(androidContext()) }

    // System services
    single<SoundPlayer> { SoundPlayerImpl(androidContext()) }
    single<ShareHandler> { ShareHandlerImpl(androidContext()) }
}
```

### iOS (iosMain)

```kotlin
// di/KoinModules.ios.kt
actual val platformModule: Module = module {
    // No context needed on iOS
    single { DatabaseDriverFactory() }
    single { FileManager() }

    // ML/AI services
    single<FaceDetector> { FaceDetectorImpl() }
    single<BodyPoseDetector> { BodyPoseDetectorImpl() }
    single<ImageProcessor> { ImageProcessorImpl() }

    // Media services
    single<VideoEncoder> { VideoEncoderImpl() }
    single<GifEncoder> { GifEncoderImpl() }

    // System services
    single<SoundPlayer> { SoundPlayerImpl() }
    single<ShareHandler> { ShareHandlerImpl() }
}
```

## Capability Detection Pattern

### Service with Availability Check

```kotlin
// domain/service/FeatureMatcher.kt (commonMain)
interface FeatureMatcher {
    /**
     * Whether feature matching is available (requires OpenCV).
     */
    val isAvailable: Boolean

    /**
     * Matches features between two images.
     */
    suspend fun matchFeatures(
        sourceImage: ImageData,
        targetImage: ImageData,
    ): Result<FeatureMatches>
}

// platform/FeatureMatcherImpl.kt (androidMain)
class FeatureMatcherImpl : FeatureMatcher {

    override val isAvailable: Boolean = try {
        OpenCVLoader.initDebug()
    } catch (e: UnsatisfiedLinkError) {
        false
    }

    override suspend fun matchFeatures(
        sourceImage: ImageData,
        targetImage: ImageData,
    ): Result<FeatureMatches> {
        if (!isAvailable) {
            return Result.Error(
                UnsupportedOperationException("OpenCV not available"),
                "Feature matching requires OpenCV",
            )
        }
        // Implementation
    }
}
```

### Use Case with Graceful Degradation

```kotlin
class AlignLandscapeUseCase(
    private val featureMatcher: FeatureMatcher,
    // ...
) {
    val isAvailable: Boolean get() = featureMatcher.isAvailable

    suspend operator fun invoke(
        frame: Frame,
        referenceFrame: Frame?,
        settings: LandscapeAlignmentSettings,
        onProgress: ((StabilizationProgress) -> Unit)? = null,
    ): Result<Frame> {
        if (!isAvailable) {
            return Result.Error(
                UnsupportedOperationException("Landscape alignment not available"),
                "Landscape mode requires additional libraries",
            )
        }

        if (referenceFrame == null) {
            return Result.Error(
                IllegalArgumentException("Reference frame required"),
                "Please capture a reference image first",
            )
        }

        // Proceed with alignment
    }
}
```

## expect/actual for Simple Types

### Platform-Specific Values

```kotlin
// platform/Platform.kt (commonMain)
expect class Platform {
    val name: String
    val version: String
    val isDebug: Boolean
}

// platform/Platform.android.kt (androidMain)
actual class Platform {
    actual val name: String = "Android"
    actual val version: String = Build.VERSION.RELEASE
    actual val isDebug: Boolean = BuildConfig.DEBUG
}

// platform/Platform.ios.kt (iosMain)
actual class Platform {
    actual val name: String = "iOS"
    actual val version: String = UIDevice.currentDevice.systemVersion
    actual val isDebug: Boolean = Platform.isDebugBinary
}
```

### File System Access

```kotlin
// platform/FileManager.kt (commonMain)
expect class FileManager {
    fun getAppDirectory(): String
    fun getProjectDirectory(projectId: String): String
    fun getTempDirectory(): String
    fun deleteFile(path: String): Boolean
    fun fileExists(path: String): Boolean
    fun getAvailableStorageBytes(): Long
}

// platform/FileManager.android.kt (androidMain)
actual class FileManager(private val context: Context) {
    actual fun getAppDirectory(): String =
        context.filesDir.absolutePath

    actual fun getProjectDirectory(projectId: String): String =
        File(getAppDirectory(), "projects/$projectId").apply { mkdirs() }.absolutePath

    actual fun getTempDirectory(): String =
        context.cacheDir.absolutePath

    actual fun getAvailableStorageBytes(): Long {
        val stat = StatFs(getAppDirectory())
        return stat.availableBlocksLong * stat.blockSizeLong
    }
}

// platform/FileManager.ios.kt (iosMain)
actual class FileManager {
    actual fun getAppDirectory(): String =
        NSSearchPathForDirectoriesInDomains(
            NSDocumentDirectory,
            NSUserDomainMask,
            true
        ).first() as String

    actual fun getProjectDirectory(projectId: String): String {
        val path = "${getAppDirectory()}/projects/$projectId"
        NSFileManager.defaultManager.createDirectoryAtPath(
            path,
            withIntermediateDirectories = true,
            attributes = null,
            error = null
        )
        return path
    }

    actual fun getAvailableStorageBytes(): Long {
        val attrs = NSFileManager.defaultManager.attributesOfFileSystemForPath(
            getAppDirectory(),
            error = null
        )
        return (attrs?.get(NSFileSystemFreeSize) as? NSNumber)?.longValue ?: 0L
    }
}
```

## Testing with Fakes

### Fake Implementation for Tests

```kotlin
// commonTest/kotlin/.../FakeFaceDetector.kt
class FakeFaceDetector : FaceDetector {
    var shouldDetect = true
    var detectedLandmarks: FaceLandmarks? = FaceLandmarks(
        leftEye = LandmarkPoint(100f, 100f),
        rightEye = LandmarkPoint(200f, 100f),
        nose = LandmarkPoint(150f, 150f),
        confidence = 0.95f,
    )

    override val isAvailable: Boolean = true

    override suspend fun detectFace(imagePath: String): FaceLandmarks? =
        if (shouldDetect) detectedLandmarks else null

    override suspend fun detectFace(imageData: ByteArray, width: Int, height: Int): FaceLandmarks? =
        if (shouldDetect) detectedLandmarks else null
}
```

## Reference Examples

- FaceDetector interface: `composeApp/src/commonMain/kotlin/com/po4yka/framelapse/domain/service/FaceDetector.kt`
- Android impl: `composeApp/src/androidMain/kotlin/com/po4yka/framelapse/platform/FaceDetectorImpl.kt`
- iOS impl: `composeApp/src/iosMain/kotlin/com/po4yka/framelapse/platform/FaceDetectorImpl.kt`
- Platform module: `composeApp/src/androidMain/kotlin/com/po4yka/framelapse/di/KoinModules.android.kt`

## Checklist

### Interface Design
- [ ] Interface in commonMain domain/service
- [ ] `isAvailable` property for capability check
- [ ] Suspend functions for async operations
- [ ] Result<T> return types for error handling

### Implementation
- [ ] Platform impl in androidMain/iosMain
- [ ] Graceful failure when unavailable
- [ ] Proper dispatcher usage (IO/Default)
- [ ] Platform SDK types stay in platform layer

### DI Registration
- [ ] expect val platformModule in commonMain
- [ ] actual val platformModule in androidMain/iosMain
- [ ] Bind implementation to interface

### Testing
- [ ] Fake implementation in commonTest
- [ ] Configurable behavior for tests
- [ ] No platform dependencies in fakes
