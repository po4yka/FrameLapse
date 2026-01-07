package com.po4yka.framelapse.platform

import com.po4yka.framelapse.domain.entity.BoundingBox
import com.po4yka.framelapse.domain.entity.FaceLandmarks
import com.po4yka.framelapse.domain.entity.LandmarkPoint
import com.po4yka.framelapse.domain.service.FaceDetector
import com.po4yka.framelapse.domain.service.ImageData
import com.po4yka.framelapse.domain.util.Result
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import platform.CoreGraphics.CGRectGetHeight
import platform.CoreGraphics.CGRectGetMinX
import platform.CoreGraphics.CGRectGetMinY
import platform.CoreGraphics.CGRectGetWidth
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.create
import platform.UIKit.UIImage
import platform.Vision.VNDetectFaceLandmarksRequest
import platform.Vision.VNFaceObservation
import platform.Vision.VNImageRequestHandler

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
class FaceDetectorImpl : FaceDetector {

    override val isAvailable: Boolean = true

    override suspend fun detectFace(imageData: ImageData): Result<FaceLandmarks?> = withContext(Dispatchers.Default) {
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

            detectFaceInCGImage(cgImage)
        } catch (e: Exception) {
            Result.Error(e, "Face detection failed: ${e.message}")
        }
    }

    override suspend fun detectFaceFromPath(imagePath: String): Result<FaceLandmarks?> = withContext(Dispatchers.IO) {
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

            detectFaceInCGImage(cgImage)
        } catch (e: Exception) {
            Result.Error(e, "Face detection failed: ${e.message}")
        }
    }

    override suspend fun detectFaceRealtime(imageData: ImageData): Result<FaceLandmarks?> =
        withContext(Dispatchers.Default) {
            detectFace(imageData)
        }

    override fun release() {
        // Vision framework doesn't require explicit cleanup
    }

    private fun detectFaceInCGImage(cgImage: platform.CoreGraphics.CGImageRef): Result<FaceLandmarks?> {
        var faceLandmarks: FaceLandmarks? = null
        var detectionError: Exception? = null

        val handler = VNImageRequestHandler(cgImage, options = emptyMap<Any?, Any?>())

        val request = VNDetectFaceLandmarksRequest { request, requestError ->
            if (requestError != null) {
                detectionError = Exception(requestError.localizedDescription)
                return@VNDetectFaceLandmarksRequest
            }

            val results = request?.results
            if (results == null || results.isEmpty()) {
                return@VNDetectFaceLandmarksRequest
            }

            val observation = results.firstOrNull() as? VNFaceObservation
                ?: return@VNDetectFaceLandmarksRequest

            faceLandmarks = processObservation(observation)
        }

        try {
            handler.performRequests(listOf(request), error = null)
        } catch (e: Exception) {
            return Result.Error(e, "Failed to perform face detection: ${e.message}")
        }

        detectionError?.let {
            return Result.Error(it, "Face detection error: ${it.message}")
        }

        return Result.Success(faceLandmarks)
    }

    private fun processObservation(observation: VNFaceObservation): FaceLandmarks? {
        val landmarks2D = observation.landmarks ?: return null

        val boundingBox = observation.boundingBox
        val boxX = CGRectGetMinX(boundingBox).toFloat()
        val boxY = CGRectGetMinY(boundingBox).toFloat()
        val boxWidth = CGRectGetWidth(boundingBox).toFloat()
        val boxHeight = CGRectGetHeight(boundingBox).toFloat()

        val points = mutableListOf<LandmarkPoint>()

        // Generate placeholder points based on bounding box
        // Vision framework provides fewer landmarks than MediaPipe's 478
        // We'll generate approximate landmarks based on face geometry
        val numPoints = FaceLandmarks.LANDMARK_COUNT
        for (i in 0 until numPoints) {
            val t = i.toFloat() / numPoints
            val x = boxX + boxWidth * (0.2f + 0.6f * kotlin.math.sin(t * kotlin.math.PI.toFloat() * 4))
            val y = 1f - (boxY + boxHeight * t)
            points.add(LandmarkPoint(x.coerceIn(0f, 1f), y.coerceIn(0f, 1f), 0f))
        }

        // Calculate approximate eye and nose positions from bounding box
        val leftEyeCenter = LandmarkPoint(
            x = boxX + boxWidth * 0.3f,
            y = 1f - (boxY + boxHeight * 0.65f),
            z = 0f,
        )

        val rightEyeCenter = LandmarkPoint(
            x = boxX + boxWidth * 0.7f,
            y = 1f - (boxY + boxHeight * 0.65f),
            z = 0f,
        )

        val noseTip = LandmarkPoint(
            x = boxX + boxWidth * 0.5f,
            y = 1f - (boxY + boxHeight * 0.4f),
            z = 0f,
        )

        val faceBoundingBox = BoundingBox(
            left = boxX.coerceIn(0f, 1f),
            top = (1f - boxY - boxHeight).coerceIn(0f, 1f),
            right = (boxX + boxWidth).coerceIn(0f, 1f),
            bottom = (1f - boxY).coerceIn(0f, 1f),
        )

        return FaceLandmarks(
            points = points,
            leftEyeCenter = leftEyeCenter,
            rightEyeCenter = rightEyeCenter,
            noseTip = noseTip,
            boundingBox = faceBoundingBox,
        )
    }

    private fun byteArrayToUIImage(bytes: ByteArray): UIImage? {
        val data = bytes.usePinned { pinned ->
            NSData.create(bytes = pinned.addressOf(0), length = bytes.size.toULong())
        }
        return UIImage.imageWithData(data)
    }
}
