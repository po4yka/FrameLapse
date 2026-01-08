package com.po4yka.framelapse.platform

import android.content.Context
import android.graphics.BitmapFactory
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import com.po4yka.framelapse.domain.entity.BodyKeypoint
import com.po4yka.framelapse.domain.entity.BodyKeypointType
import com.po4yka.framelapse.domain.entity.BodyLandmarks
import com.po4yka.framelapse.domain.entity.BoundingBox
import com.po4yka.framelapse.domain.entity.LandmarkPoint
import com.po4yka.framelapse.domain.service.BodyPoseDetector
import com.po4yka.framelapse.domain.service.ImageData
import com.po4yka.framelapse.domain.util.Result
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Android implementation of BodyPoseDetector using MediaPipe PoseLandmarker.
 *
 * MediaPipe provides 33 body keypoints with 3D coordinates.
 * Key landmarks for body alignment are shoulders (11, 12) and hips (23, 24).
 */
class BodyPoseDetectorImpl(private val context: Context) : BodyPoseDetector {

    private var poseLandmarker: PoseLandmarker? = null
    private var realtimePoseLandmarker: PoseLandmarker? = null
    private val mutex = Mutex()

    override val isAvailable: Boolean
        get() = try {
            ensureLandmarkerInitialized()
            poseLandmarker != null
        } catch (e: Exception) {
            false
        }

    override suspend fun detectBodyPose(imageData: ImageData): Result<BodyLandmarks?> =
        withContext(Dispatchers.Default) {
            mutex.withLock {
                try {
                    ensureLandmarkerInitialized()

                    val landmarker = poseLandmarker
                        ?: return@withContext Result.Error(
                            IllegalStateException("Body pose detector not initialized"),
                            "Body pose detector not available",
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
                    Result.Error(e, "Body pose detection failed: ${e.message}")
                }
            }
        }

    override suspend fun detectBodyPoseFromPath(imagePath: String): Result<BodyLandmarks?> =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                try {
                    if (!File(imagePath).exists()) {
                        return@withContext Result.Error(
                            IllegalArgumentException("File not found: $imagePath"),
                            "File not found",
                        )
                    }

                    ensureLandmarkerInitialized()

                    val landmarker = poseLandmarker
                        ?: return@withContext Result.Error(
                            IllegalStateException("Body pose detector not initialized"),
                            "Body pose detector not available",
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
                    Result.Error(e, "Body pose detection failed: ${e.message}")
                }
            }
        }

    override suspend fun detectBodyPoseRealtime(imageData: ImageData): Result<BodyLandmarks?> =
        withContext(Dispatchers.Default) {
            try {
                ensureRealtimeLandmarkerInitialized()

                val landmarker = realtimePoseLandmarker
                    ?: return@withContext Result.Error(
                        IllegalStateException("Realtime body pose detector not initialized"),
                        "Realtime body pose detector not available",
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
                Result.Error(e, "Realtime body pose detection failed: ${e.message}")
            }
        }

    override fun release() {
        poseLandmarker?.close()
        poseLandmarker = null
        realtimePoseLandmarker?.close()
        realtimePoseLandmarker = null
    }

    private fun ensureLandmarkerInitialized() {
        if (poseLandmarker != null) return

        val baseOptions = BaseOptions.builder()
            .setModelAssetPath(MODEL_PATH)
            .setDelegate(Delegate.GPU)
            .build()

        val options = PoseLandmarker.PoseLandmarkerOptions.builder()
            .setBaseOptions(baseOptions)
            .setRunningMode(RunningMode.IMAGE)
            .setNumPoses(1)
            .setMinPoseDetectionConfidence(MIN_DETECTION_CONFIDENCE)
            .setMinPosePresenceConfidence(MIN_PRESENCE_CONFIDENCE)
            .setMinTrackingConfidence(MIN_TRACKING_CONFIDENCE)
            .build()

        poseLandmarker = try {
            PoseLandmarker.createFromOptions(context, options)
        } catch (e: Exception) {
            // Fallback to CPU if GPU fails
            val cpuOptions = PoseLandmarker.PoseLandmarkerOptions.builder()
                .setBaseOptions(
                    BaseOptions.builder()
                        .setModelAssetPath(MODEL_PATH)
                        .setDelegate(Delegate.CPU)
                        .build(),
                )
                .setRunningMode(RunningMode.IMAGE)
                .setNumPoses(1)
                .setMinPoseDetectionConfidence(MIN_DETECTION_CONFIDENCE)
                .setMinPosePresenceConfidence(MIN_PRESENCE_CONFIDENCE)
                .setMinTrackingConfidence(MIN_TRACKING_CONFIDENCE)
                .build()
            PoseLandmarker.createFromOptions(context, cpuOptions)
        }
    }

    private fun ensureRealtimeLandmarkerInitialized() {
        if (realtimePoseLandmarker != null) return

        val baseOptions = BaseOptions.builder()
            .setModelAssetPath(MODEL_PATH)
            .setDelegate(Delegate.GPU)
            .build()

        val options = PoseLandmarker.PoseLandmarkerOptions.builder()
            .setBaseOptions(baseOptions)
            .setRunningMode(RunningMode.IMAGE)
            .setNumPoses(1)
            .setMinPoseDetectionConfidence(REALTIME_DETECTION_CONFIDENCE)
            .setMinPosePresenceConfidence(REALTIME_PRESENCE_CONFIDENCE)
            .setMinTrackingConfidence(REALTIME_TRACKING_CONFIDENCE)
            .build()

        realtimePoseLandmarker = try {
            PoseLandmarker.createFromOptions(context, options)
        } catch (e: Exception) {
            // Fallback to CPU if GPU fails
            val cpuOptions = PoseLandmarker.PoseLandmarkerOptions.builder()
                .setBaseOptions(
                    BaseOptions.builder()
                        .setModelAssetPath(MODEL_PATH)
                        .setDelegate(Delegate.CPU)
                        .build(),
                )
                .setRunningMode(RunningMode.IMAGE)
                .setNumPoses(1)
                .setMinPoseDetectionConfidence(REALTIME_DETECTION_CONFIDENCE)
                .setMinPosePresenceConfidence(REALTIME_PRESENCE_CONFIDENCE)
                .setMinTrackingConfidence(REALTIME_TRACKING_CONFIDENCE)
                .build()
            PoseLandmarker.createFromOptions(context, cpuOptions)
        }
    }

    private fun processResult(result: PoseLandmarkerResult, imageWidth: Int, imageHeight: Int): BodyLandmarks? {
        if (result.landmarks().isEmpty()) {
            return null
        }

        val landmarks = result.landmarks()[0]
        if (landmarks.size < MIN_REQUIRED_LANDMARKS) {
            return null
        }

        // Extract all keypoints with their types
        val keypoints = mutableListOf<BodyKeypoint>()
        for ((index, type) in MEDIAPIPE_TO_KEYPOINT_TYPE) {
            val landmark = landmarks.getOrNull(index) ?: continue
            keypoints.add(
                BodyKeypoint(
                    type = type,
                    position = LandmarkPoint(
                        x = landmark.x(),
                        y = landmark.y(),
                        z = landmark.z(),
                    ),
                    confidence = landmark.visibility().orElse(1.0f),
                    isVisible = landmark.visibility().orElse(1.0f) > VISIBILITY_THRESHOLD,
                ),
            )
        }

        // Extract key body landmarks for alignment
        val leftShoulderLm = landmarks.getOrNull(LEFT_SHOULDER_INDEX) ?: return null
        val rightShoulderLm = landmarks.getOrNull(RIGHT_SHOULDER_INDEX) ?: return null
        val leftHipLm = landmarks.getOrNull(LEFT_HIP_INDEX) ?: return null
        val rightHipLm = landmarks.getOrNull(RIGHT_HIP_INDEX) ?: return null

        val leftShoulder = LandmarkPoint(leftShoulderLm.x(), leftShoulderLm.y(), leftShoulderLm.z())
        val rightShoulder = LandmarkPoint(rightShoulderLm.x(), rightShoulderLm.y(), rightShoulderLm.z())
        val leftHip = LandmarkPoint(leftHipLm.x(), leftHipLm.y(), leftHipLm.z())
        val rightHip = LandmarkPoint(rightHipLm.x(), rightHipLm.y(), rightHipLm.z())

        // Calculate neck center (midpoint between shoulders, slightly up)
        val neckCenter = LandmarkPoint(
            x = (leftShoulder.x + rightShoulder.x) / 2,
            y = (leftShoulder.y + rightShoulder.y) / 2 - NECK_OFFSET,
            z = (leftShoulder.z + rightShoulder.z) / 2,
        )

        // Calculate bounding box from upper body landmarks
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE
        var maxY = Float.MIN_VALUE

        // Include head, shoulders, and hips in bounding box
        val boundingLandmarks = listOf(
            NOSE_INDEX, LEFT_SHOULDER_INDEX, RIGHT_SHOULDER_INDEX,
            LEFT_HIP_INDEX, RIGHT_HIP_INDEX, LEFT_EAR_INDEX, RIGHT_EAR_INDEX,
        )
        for (index in boundingLandmarks) {
            val lm = landmarks.getOrNull(index) ?: continue
            if (lm.x() < minX) minX = lm.x()
            if (lm.y() < minY) minY = lm.y()
            if (lm.x() > maxX) maxX = lm.x()
            if (lm.y() > maxY) maxY = lm.y()
        }

        // Add padding to bounding box
        val padding = 0.15f
        val paddingX = (maxX - minX) * padding
        val paddingY = (maxY - minY) * padding

        val boundingBox = BoundingBox(
            left = (minX - paddingX).coerceAtLeast(0f),
            top = (minY - paddingY).coerceAtLeast(0f),
            right = (maxX + paddingX).coerceAtMost(1f),
            bottom = (maxY + paddingY).coerceAtMost(1f),
        )

        // Calculate overall confidence from key landmarks
        val confidence = listOf(
            leftShoulderLm.visibility().orElse(0f),
            rightShoulderLm.visibility().orElse(0f),
            leftHipLm.visibility().orElse(0f),
            rightHipLm.visibility().orElse(0f),
        ).average().toFloat()

        return BodyLandmarks(
            keypoints = keypoints,
            leftShoulder = leftShoulder,
            rightShoulder = rightShoulder,
            leftHip = leftHip,
            rightHip = rightHip,
            neckCenter = neckCenter,
            boundingBox = boundingBox,
            confidence = confidence,
        )
    }

    companion object {
        private const val MODEL_PATH = "pose_landmarker.task"

        // Standard detection thresholds
        private const val MIN_DETECTION_CONFIDENCE = 0.5f
        private const val MIN_PRESENCE_CONFIDENCE = 0.5f
        private const val MIN_TRACKING_CONFIDENCE = 0.5f

        // Realtime detection thresholds (lower for speed)
        private const val REALTIME_DETECTION_CONFIDENCE = 0.3f
        private const val REALTIME_PRESENCE_CONFIDENCE = 0.3f
        private const val REALTIME_TRACKING_CONFIDENCE = 0.3f

        // Minimum landmarks required for body detection
        private const val MIN_REQUIRED_LANDMARKS = 17

        // Visibility threshold for keypoint detection
        private const val VISIBILITY_THRESHOLD = 0.5f

        // Offset for neck center calculation (fraction of shoulder distance)
        private const val NECK_OFFSET = 0.05f

        // MediaPipe Pose Landmarker indices
        private const val NOSE_INDEX = 0
        private const val LEFT_EYE_INDEX = 2
        private const val RIGHT_EYE_INDEX = 5
        private const val LEFT_EAR_INDEX = 7
        private const val RIGHT_EAR_INDEX = 8
        private const val LEFT_SHOULDER_INDEX = 11
        private const val RIGHT_SHOULDER_INDEX = 12
        private const val LEFT_ELBOW_INDEX = 13
        private const val RIGHT_ELBOW_INDEX = 14
        private const val LEFT_WRIST_INDEX = 15
        private const val RIGHT_WRIST_INDEX = 16
        private const val LEFT_HIP_INDEX = 23
        private const val RIGHT_HIP_INDEX = 24
        private const val LEFT_KNEE_INDEX = 25
        private const val RIGHT_KNEE_INDEX = 26
        private const val LEFT_ANKLE_INDEX = 27
        private const val RIGHT_ANKLE_INDEX = 28

        // Mapping from MediaPipe indices to BodyKeypointType
        private val MEDIAPIPE_TO_KEYPOINT_TYPE = mapOf(
            NOSE_INDEX to BodyKeypointType.NOSE,
            LEFT_EYE_INDEX to BodyKeypointType.LEFT_EYE,
            RIGHT_EYE_INDEX to BodyKeypointType.RIGHT_EYE,
            LEFT_EAR_INDEX to BodyKeypointType.LEFT_EAR,
            RIGHT_EAR_INDEX to BodyKeypointType.RIGHT_EAR,
            LEFT_SHOULDER_INDEX to BodyKeypointType.LEFT_SHOULDER,
            RIGHT_SHOULDER_INDEX to BodyKeypointType.RIGHT_SHOULDER,
            LEFT_ELBOW_INDEX to BodyKeypointType.LEFT_ELBOW,
            RIGHT_ELBOW_INDEX to BodyKeypointType.RIGHT_ELBOW,
            LEFT_WRIST_INDEX to BodyKeypointType.LEFT_WRIST,
            RIGHT_WRIST_INDEX to BodyKeypointType.RIGHT_WRIST,
            LEFT_HIP_INDEX to BodyKeypointType.LEFT_HIP,
            RIGHT_HIP_INDEX to BodyKeypointType.RIGHT_HIP,
            LEFT_KNEE_INDEX to BodyKeypointType.LEFT_KNEE,
            RIGHT_KNEE_INDEX to BodyKeypointType.RIGHT_KNEE,
            LEFT_ANKLE_INDEX to BodyKeypointType.LEFT_ANKLE,
            RIGHT_ANKLE_INDEX to BodyKeypointType.RIGHT_ANKLE,
        )
    }
}
