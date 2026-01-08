package com.po4yka.framelapse.platform

import com.po4yka.framelapse.domain.entity.BodyKeypoint
import com.po4yka.framelapse.domain.entity.BodyKeypointType
import com.po4yka.framelapse.domain.entity.BodyLandmarks
import com.po4yka.framelapse.domain.entity.BoundingBox
import com.po4yka.framelapse.domain.entity.LandmarkPoint
import com.po4yka.framelapse.domain.service.BodyPoseDetector
import com.po4yka.framelapse.domain.service.ImageData
import com.po4yka.framelapse.domain.util.Result
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.create
import platform.UIKit.UIImage
import platform.Vision.VNDetectHumanBodyPoseRequest
import platform.Vision.VNHumanBodyPoseObservation
import platform.Vision.VNHumanBodyPoseObservationJointNameLeftAnkle
import platform.Vision.VNHumanBodyPoseObservationJointNameLeftEar
import platform.Vision.VNHumanBodyPoseObservationJointNameLeftElbow
import platform.Vision.VNHumanBodyPoseObservationJointNameLeftEye
import platform.Vision.VNHumanBodyPoseObservationJointNameLeftHip
import platform.Vision.VNHumanBodyPoseObservationJointNameLeftKnee
import platform.Vision.VNHumanBodyPoseObservationJointNameLeftShoulder
import platform.Vision.VNHumanBodyPoseObservationJointNameLeftWrist
import platform.Vision.VNHumanBodyPoseObservationJointNameNose
import platform.Vision.VNHumanBodyPoseObservationJointNameRightAnkle
import platform.Vision.VNHumanBodyPoseObservationJointNameRightEar
import platform.Vision.VNHumanBodyPoseObservationJointNameRightElbow
import platform.Vision.VNHumanBodyPoseObservationJointNameRightEye
import platform.Vision.VNHumanBodyPoseObservationJointNameRightHip
import platform.Vision.VNHumanBodyPoseObservationJointNameRightKnee
import platform.Vision.VNHumanBodyPoseObservationJointNameRightShoulder
import platform.Vision.VNHumanBodyPoseObservationJointNameRightWrist
import platform.Vision.VNImageRequestHandler
import platform.Vision.VNRecognizedPointKey

/**
 * iOS implementation of BodyPoseDetector using Vision Framework.
 *
 * Uses VNDetectHumanBodyPoseRequest (available iOS 14+) which provides
 * 17 COCO body keypoints. Key landmarks for body alignment are shoulders.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
class BodyPoseDetectorImpl : BodyPoseDetector {

    override val isAvailable: Boolean = true

    override suspend fun detectBodyPose(imageData: ImageData): Result<BodyLandmarks?> =
        withContext(Dispatchers.Default) {
            try {
                val uiImage = byteArrayToUIImage(imageData.bytes)
                    ?: return@withContext Result.Error(
                        IllegalArgumentException("Failed to decode image"),
                        "Failed to decode image",
                    )

                val cgImage = uiImage.CGImage
                    ?: return@withContext Result.Error(
                        IllegalStateException("Failed to get CGImage"),
                        "Failed to get CGImage",
                    )

                detectBodyPoseInCGImage(cgImage)
            } catch (e: Exception) {
                Result.Error(e, "Body pose detection failed: ${e.message}")
            }
        }

    override suspend fun detectBodyPoseFromPath(imagePath: String): Result<BodyLandmarks?> =
        withContext(Dispatchers.IO) {
            try {
                val fileManager = NSFileManager.defaultManager
                if (!fileManager.fileExistsAtPath(imagePath)) {
                    return@withContext Result.Error(
                        IllegalArgumentException("File not found: $imagePath"),
                        "File not found",
                    )
                }

                val uiImage = UIImage.imageWithContentsOfFile(imagePath)
                    ?: return@withContext Result.Error(
                        IllegalArgumentException("Failed to load image: $imagePath"),
                        "Failed to load image",
                    )

                val cgImage = uiImage.CGImage
                    ?: return@withContext Result.Error(
                        IllegalStateException("Failed to get CGImage"),
                        "Failed to get CGImage",
                    )

                detectBodyPoseInCGImage(cgImage)
            } catch (e: Exception) {
                Result.Error(e, "Body pose detection failed: ${e.message}")
            }
        }

    override suspend fun detectBodyPoseRealtime(imageData: ImageData): Result<BodyLandmarks?> =
        withContext(Dispatchers.Default) {
            detectBodyPose(imageData)
        }

    override fun release() {
        // Vision framework doesn't require explicit cleanup
    }

    private fun detectBodyPoseInCGImage(cgImage: platform.CoreGraphics.CGImageRef): Result<BodyLandmarks?> {
        var bodyLandmarks: BodyLandmarks? = null
        var detectionError: Exception? = null

        val handler = VNImageRequestHandler(cgImage, options = emptyMap<Any?, Any?>())

        val request = VNDetectHumanBodyPoseRequest { request, requestError ->
            if (requestError != null) {
                detectionError = Exception(requestError.localizedDescription)
                return@VNDetectHumanBodyPoseRequest
            }

            val results = request?.results
            if (results == null || results.isEmpty()) {
                return@VNDetectHumanBodyPoseRequest
            }

            val observation = results.firstOrNull() as? VNHumanBodyPoseObservation
                ?: return@VNDetectHumanBodyPoseRequest

            bodyLandmarks = processObservation(observation)
        }

        try {
            handler.performRequests(listOf(request), error = null)
        } catch (e: Exception) {
            return Result.Error(e, "Failed to perform body pose detection: ${e.message}")
        }

        detectionError?.let {
            return Result.Error(it, "Body pose detection error: ${it.message}")
        }

        return Result.Success(bodyLandmarks)
    }

    private fun processObservation(observation: VNHumanBodyPoseObservation): BodyLandmarks? {
        val keypoints = mutableListOf<BodyKeypoint>()
        var totalConfidence = 0f
        var keypointCount = 0

        // Process all available keypoints
        for ((jointName, keypointType) in VISION_JOINT_TO_KEYPOINT_TYPE) {
            val point = try {
                observation.recognizedPointForJointName(jointName, error = null)
            } catch (e: Exception) {
                null
            }

            if (point != null && point.confidence > CONFIDENCE_THRESHOLD) {
                // Vision coordinates: origin at bottom-left, y increases upward
                // We need to flip Y to match our coordinate system (top-left origin)
                keypoints.add(
                    BodyKeypoint(
                        type = keypointType,
                        position = LandmarkPoint(
                            x = point.location.useContents { x.toFloat() },
                            y = 1f - point.location.useContents { y.toFloat() },
                            z = 0f, // Vision doesn't provide Z coordinate
                        ),
                        confidence = point.confidence.toFloat(),
                        isVisible = point.confidence > VISIBILITY_THRESHOLD,
                    ),
                )
                totalConfidence += point.confidence.toFloat()
                keypointCount++
            }
        }

        // Extract key landmarks for alignment
        val leftShoulder = extractLandmarkPoint(observation, VNHumanBodyPoseObservationJointNameLeftShoulder)
            ?: return null
        val rightShoulder = extractLandmarkPoint(observation, VNHumanBodyPoseObservationJointNameRightShoulder)
            ?: return null
        val leftHip = extractLandmarkPoint(observation, VNHumanBodyPoseObservationJointNameLeftHip)
            ?: return null
        val rightHip = extractLandmarkPoint(observation, VNHumanBodyPoseObservationJointNameRightHip)
            ?: return null

        // Calculate neck center (midpoint between shoulders, slightly up)
        val neckCenter = LandmarkPoint(
            x = (leftShoulder.x + rightShoulder.x) / 2,
            y = (leftShoulder.y + rightShoulder.y) / 2 - NECK_OFFSET,
            z = 0f,
        )

        // Calculate bounding box from detected keypoints
        val boundingBox = calculateBoundingBox(keypoints)

        // Calculate overall confidence
        val confidence = if (keypointCount > 0) totalConfidence / keypointCount else 0f

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

    private fun extractLandmarkPoint(
        observation: VNHumanBodyPoseObservation,
        jointName: VNRecognizedPointKey,
    ): LandmarkPoint? {
        val point = try {
            observation.recognizedPointForJointName(jointName, error = null)
        } catch (e: Exception) {
            null
        }

        if (point == null || point.confidence < CONFIDENCE_THRESHOLD) {
            return null
        }

        return LandmarkPoint(
            x = point.location.useContents { x.toFloat() },
            y = 1f - point.location.useContents { y.toFloat() },
            z = 0f,
        )
    }

    private fun calculateBoundingBox(keypoints: List<BodyKeypoint>): BoundingBox {
        if (keypoints.isEmpty()) {
            return BoundingBox(0f, 0f, 1f, 1f)
        }

        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE
        var maxY = Float.MIN_VALUE

        for (keypoint in keypoints) {
            val pos = keypoint.position
            if (pos.x < minX) minX = pos.x
            if (pos.y < minY) minY = pos.y
            if (pos.x > maxX) maxX = pos.x
            if (pos.y > maxY) maxY = pos.y
        }

        // Add padding
        val paddingX = (maxX - minX) * BOUNDING_BOX_PADDING
        val paddingY = (maxY - minY) * BOUNDING_BOX_PADDING

        return BoundingBox(
            left = (minX - paddingX).coerceAtLeast(0f),
            top = (minY - paddingY).coerceAtLeast(0f),
            right = (maxX + paddingX).coerceAtMost(1f),
            bottom = (maxY + paddingY).coerceAtMost(1f),
        )
    }

    private fun byteArrayToUIImage(bytes: ByteArray): UIImage? {
        val data = bytes.usePinned { pinned ->
            NSData.create(bytes = pinned.addressOf(0), length = bytes.size.toULong())
        }
        return UIImage.imageWithData(data)
    }

    companion object {
        private const val CONFIDENCE_THRESHOLD = 0.3f
        private const val VISIBILITY_THRESHOLD = 0.5f
        private const val NECK_OFFSET = 0.05f
        private const val BOUNDING_BOX_PADDING = 0.15f

        // Mapping from Vision joint names to BodyKeypointType
        private val VISION_JOINT_TO_KEYPOINT_TYPE: Map<VNRecognizedPointKey, BodyKeypointType> = mapOf(
            VNHumanBodyPoseObservationJointNameNose to BodyKeypointType.NOSE,
            VNHumanBodyPoseObservationJointNameLeftEye to BodyKeypointType.LEFT_EYE,
            VNHumanBodyPoseObservationJointNameRightEye to BodyKeypointType.RIGHT_EYE,
            VNHumanBodyPoseObservationJointNameLeftEar to BodyKeypointType.LEFT_EAR,
            VNHumanBodyPoseObservationJointNameRightEar to BodyKeypointType.RIGHT_EAR,
            VNHumanBodyPoseObservationJointNameLeftShoulder to BodyKeypointType.LEFT_SHOULDER,
            VNHumanBodyPoseObservationJointNameRightShoulder to BodyKeypointType.RIGHT_SHOULDER,
            VNHumanBodyPoseObservationJointNameLeftElbow to BodyKeypointType.LEFT_ELBOW,
            VNHumanBodyPoseObservationJointNameRightElbow to BodyKeypointType.RIGHT_ELBOW,
            VNHumanBodyPoseObservationJointNameLeftWrist to BodyKeypointType.LEFT_WRIST,
            VNHumanBodyPoseObservationJointNameRightWrist to BodyKeypointType.RIGHT_WRIST,
            VNHumanBodyPoseObservationJointNameLeftHip to BodyKeypointType.LEFT_HIP,
            VNHumanBodyPoseObservationJointNameRightHip to BodyKeypointType.RIGHT_HIP,
            VNHumanBodyPoseObservationJointNameLeftKnee to BodyKeypointType.LEFT_KNEE,
            VNHumanBodyPoseObservationJointNameRightKnee to BodyKeypointType.RIGHT_KNEE,
            VNHumanBodyPoseObservationJointNameLeftAnkle to BodyKeypointType.LEFT_ANKLE,
            VNHumanBodyPoseObservationJointNameRightAnkle to BodyKeypointType.RIGHT_ANKLE,
        )
    }
}
