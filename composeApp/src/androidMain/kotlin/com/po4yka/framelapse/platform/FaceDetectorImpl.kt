package com.po4yka.framelapse.platform

import android.content.Context
import android.graphics.BitmapFactory
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult
import com.po4yka.framelapse.domain.entity.BoundingBox
import com.po4yka.framelapse.domain.entity.FaceLandmarks
import com.po4yka.framelapse.domain.entity.LandmarkPoint
import com.po4yka.framelapse.domain.service.FaceDetector
import com.po4yka.framelapse.domain.service.ImageData
import com.po4yka.framelapse.domain.util.Result
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

class FaceDetectorImpl(private val context: Context) : FaceDetector {

    private var faceLandmarker: FaceLandmarker? = null
    private var realtimeFaceLandmarker: FaceLandmarker? = null
    private val mutex = Mutex()

    override val isAvailable: Boolean
        get() = try {
            ensureLandmarkerInitialized()
            faceLandmarker != null
        } catch (e: Exception) {
            false
        }

    override suspend fun detectFace(imageData: ImageData): Result<FaceLandmarks?> = withContext(Dispatchers.Default) {
        mutex.withLock {
            try {
                ensureLandmarkerInitialized()

                val landmarker = faceLandmarker
                    ?: return@withContext Result.Error(
                        IllegalStateException("Face detector not initialized"),
                        "Face detector not available",
                    )

                val bitmap = BitmapFactory.decodeByteArray(
                    imageData.bytes,
                    0,
                    imageData.bytes.size,
                ) ?: return@withContext Result.Error(
                    IllegalArgumentException("Failed to decode image"),
                    "Failed to decode image",
                )

                val mpImage = BitmapImageBuilder(bitmap).build()
                val result = landmarker.detect(mpImage)

                bitmap.recycle()

                val landmarks = processResult(result, imageData.width, imageData.height)
                Result.Success(landmarks)
            } catch (e: Exception) {
                Result.Error(e, "Face detection failed: ${e.message}")
            }
        }
    }

    override suspend fun detectFaceFromPath(imagePath: String): Result<FaceLandmarks?> = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                if (!File(imagePath).exists()) {
                    return@withContext Result.Error(
                        IllegalArgumentException("File not found: $imagePath"),
                        "File not found",
                    )
                }

                ensureLandmarkerInitialized()

                val landmarker = faceLandmarker
                    ?: return@withContext Result.Error(
                        IllegalStateException("Face detector not initialized"),
                        "Face detector not available",
                    )

                val bitmap = BitmapFactory.decodeFile(imagePath)
                    ?: return@withContext Result.Error(
                        IllegalArgumentException("Failed to decode image: $imagePath"),
                        "Failed to decode image",
                    )

                val mpImage = BitmapImageBuilder(bitmap).build()
                val result = landmarker.detect(mpImage)

                val width = bitmap.width
                val height = bitmap.height
                bitmap.recycle()

                val landmarks = processResult(result, width, height)
                Result.Success(landmarks)
            } catch (e: Exception) {
                Result.Error(e, "Face detection failed: ${e.message}")
            }
        }
    }

    override suspend fun detectFaceRealtime(imageData: ImageData): Result<FaceLandmarks?> =
        withContext(Dispatchers.Default) {
            try {
                ensureRealtimeLandmarkerInitialized()

                val landmarker = realtimeFaceLandmarker
                    ?: return@withContext Result.Error(
                        IllegalStateException("Realtime face detector not initialized"),
                        "Realtime face detector not available",
                    )

                val bitmap = BitmapFactory.decodeByteArray(
                    imageData.bytes,
                    0,
                    imageData.bytes.size,
                ) ?: return@withContext Result.Error(
                    IllegalArgumentException("Failed to decode image"),
                    "Failed to decode image",
                )

                val mpImage = BitmapImageBuilder(bitmap).build()
                val result = landmarker.detect(mpImage)

                bitmap.recycle()

                val landmarks = processResult(result, imageData.width, imageData.height)
                Result.Success(landmarks)
            } catch (e: Exception) {
                Result.Error(e, "Realtime face detection failed: ${e.message}")
            }
        }

    override fun release() {
        faceLandmarker?.close()
        faceLandmarker = null
        realtimeFaceLandmarker?.close()
        realtimeFaceLandmarker = null
    }

    private fun ensureLandmarkerInitialized() {
        if (faceLandmarker != null) return

        val baseOptions = BaseOptions.builder()
            .setModelAssetPath(MODEL_PATH)
            .setDelegate(Delegate.GPU)
            .build()

        val options = FaceLandmarker.FaceLandmarkerOptions.builder()
            .setBaseOptions(baseOptions)
            .setRunningMode(RunningMode.IMAGE)
            .setNumFaces(1)
            .setMinFaceDetectionConfidence(MIN_DETECTION_CONFIDENCE)
            .setMinFacePresenceConfidence(MIN_PRESENCE_CONFIDENCE)
            .setMinTrackingConfidence(MIN_TRACKING_CONFIDENCE)
            .setOutputFaceBlendshapes(false)
            .setOutputFacialTransformationMatrixes(false)
            .build()

        faceLandmarker = try {
            FaceLandmarker.createFromOptions(context, options)
        } catch (e: Exception) {
            // Fallback to CPU if GPU fails
            val cpuOptions = FaceLandmarker.FaceLandmarkerOptions.builder()
                .setBaseOptions(
                    BaseOptions.builder()
                        .setModelAssetPath(MODEL_PATH)
                        .setDelegate(Delegate.CPU)
                        .build(),
                )
                .setRunningMode(RunningMode.IMAGE)
                .setNumFaces(1)
                .setMinFaceDetectionConfidence(MIN_DETECTION_CONFIDENCE)
                .setMinFacePresenceConfidence(MIN_PRESENCE_CONFIDENCE)
                .setMinTrackingConfidence(MIN_TRACKING_CONFIDENCE)
                .build()
            FaceLandmarker.createFromOptions(context, cpuOptions)
        }
    }

    private fun ensureRealtimeLandmarkerInitialized() {
        if (realtimeFaceLandmarker != null) return

        val baseOptions = BaseOptions.builder()
            .setModelAssetPath(MODEL_PATH)
            .setDelegate(Delegate.GPU)
            .build()

        val options = FaceLandmarker.FaceLandmarkerOptions.builder()
            .setBaseOptions(baseOptions)
            .setRunningMode(RunningMode.IMAGE)
            .setNumFaces(1)
            .setMinFaceDetectionConfidence(REALTIME_DETECTION_CONFIDENCE)
            .setMinFacePresenceConfidence(REALTIME_PRESENCE_CONFIDENCE)
            .setMinTrackingConfidence(REALTIME_TRACKING_CONFIDENCE)
            .build()

        realtimeFaceLandmarker = try {
            FaceLandmarker.createFromOptions(context, options)
        } catch (e: Exception) {
            // Fallback to CPU if GPU fails
            val cpuOptions = FaceLandmarker.FaceLandmarkerOptions.builder()
                .setBaseOptions(
                    BaseOptions.builder()
                        .setModelAssetPath(MODEL_PATH)
                        .setDelegate(Delegate.CPU)
                        .build(),
                )
                .setRunningMode(RunningMode.IMAGE)
                .setNumFaces(1)
                .setMinFaceDetectionConfidence(REALTIME_DETECTION_CONFIDENCE)
                .setMinFacePresenceConfidence(REALTIME_PRESENCE_CONFIDENCE)
                .setMinTrackingConfidence(REALTIME_TRACKING_CONFIDENCE)
                .build()
            FaceLandmarker.createFromOptions(context, cpuOptions)
        }
    }

    private fun processResult(result: FaceLandmarkerResult, imageWidth: Int, imageHeight: Int): FaceLandmarks? {
        if (result.faceLandmarks().isEmpty()) {
            return null
        }

        val landmarks = result.faceLandmarks()[0]

        if (landmarks.size < FaceLandmarks.LANDMARK_COUNT) {
            return null
        }

        val points = landmarks.map { landmark ->
            LandmarkPoint(
                x = landmark.x(),
                y = landmark.y(),
                z = landmark.z(),
            )
        }

        // Extract key points using MediaPipe Face Landmarker indices
        // Left iris center: landmark 468
        // Right iris center: landmark 473
        // Nose tip: landmark 1
        val leftEyeCenter = points.getOrNull(LEFT_IRIS_INDEX) ?: points[LEFT_EYE_OUTER_INDEX]
        val rightEyeCenter = points.getOrNull(RIGHT_IRIS_INDEX) ?: points[RIGHT_EYE_OUTER_INDEX]
        val noseTip = points[NOSE_TIP_INDEX]

        // Calculate bounding box from all landmarks
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE
        var maxY = Float.MIN_VALUE

        for (point in points) {
            if (point.x < minX) minX = point.x
            if (point.y < minY) minY = point.y
            if (point.x > maxX) maxX = point.x
            if (point.y > maxY) maxY = point.y
        }

        // Add padding to bounding box
        val padding = 0.1f
        val paddingX = (maxX - minX) * padding
        val paddingY = (maxY - minY) * padding

        val boundingBox = BoundingBox(
            left = (minX - paddingX).coerceAtLeast(0f),
            top = (minY - paddingY).coerceAtLeast(0f),
            right = (maxX + paddingX).coerceAtMost(1f),
            bottom = (maxY + paddingY).coerceAtMost(1f),
        )

        return FaceLandmarks(
            points = points,
            leftEyeCenter = leftEyeCenter,
            rightEyeCenter = rightEyeCenter,
            noseTip = noseTip,
            boundingBox = boundingBox,
        )
    }

    companion object {
        private const val MODEL_PATH = "face_landmarker.task"

        // Standard detection thresholds
        private const val MIN_DETECTION_CONFIDENCE = 0.5f
        private const val MIN_PRESENCE_CONFIDENCE = 0.5f
        private const val MIN_TRACKING_CONFIDENCE = 0.5f

        // Realtime detection thresholds (lower for speed)
        private const val REALTIME_DETECTION_CONFIDENCE = 0.3f
        private const val REALTIME_PRESENCE_CONFIDENCE = 0.3f
        private const val REALTIME_TRACKING_CONFIDENCE = 0.3f

        // MediaPipe Face Landmarker key landmark indices
        private const val LEFT_IRIS_INDEX = 468
        private const val RIGHT_IRIS_INDEX = 473
        private const val LEFT_EYE_OUTER_INDEX = 33
        private const val RIGHT_EYE_OUTER_INDEX = 263
        private const val NOSE_TIP_INDEX = 1
    }
}
