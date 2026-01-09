package com.po4yka.framelapse.platform

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.po4yka.framelapse.domain.entity.BoundingBox
import com.po4yka.framelapse.domain.entity.FeatureDetectorType
import com.po4yka.framelapse.domain.entity.FeatureKeypoint
import com.po4yka.framelapse.domain.entity.HomographyMatrix
import com.po4yka.framelapse.domain.entity.LandscapeLandmarks
import com.po4yka.framelapse.domain.service.FeatureMatchResult
import com.po4yka.framelapse.domain.service.ImageData
import com.po4yka.framelapse.domain.util.Result
import org.opencv.android.Utils
import org.opencv.calib3d.Calib3d
import org.opencv.core.DMatch
import org.opencv.core.Mat
import org.opencv.core.MatOfDMatch
import org.opencv.core.MatOfKeyPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.features2d.AKAZE
import org.opencv.features2d.BFMatcher
import org.opencv.features2d.DescriptorMatcher
import org.opencv.features2d.ORB
import org.opencv.imgproc.Imgproc

internal class OpenCvFeatureMatcherCore(private val initializer: OpenCvInitializer) {
    private var orbDetector: ORB? = null
    private var akazeDetector: AKAZE? = null
    private var bfMatcher: BFMatcher? = null

    fun detectFeaturesFromBitmap(
        bitmap: Bitmap,
        imageWidth: Int,
        imageHeight: Int,
        detectorType: FeatureDetectorType,
        maxKeypoints: Int,
    ): Result<LandscapeLandmarks> {
        initializer.ensureInitialized()
        val mat = Mat()
        Utils.bitmapToMat(bitmap, mat)

        val grayMat = Mat()
        Imgproc.cvtColor(mat, grayMat, Imgproc.COLOR_RGBA2GRAY)

        val detector = getDetector(detectorType, maxKeypoints)
        val keypoints = MatOfKeyPoint()
        detector.detect(grayMat, keypoints)

        val keypointArray = keypoints.toArray()

        if (keypointArray.isEmpty()) {
            mat.release()
            grayMat.release()
            keypoints.release()
            return Result.Error(
                IllegalStateException("No keypoints detected"),
                "No features found in image",
            )
        }

        val landmarks = convertToLandscapeLandmarks(
            keypointArray.toList(),
            imageWidth,
            imageHeight,
            detectorType,
        )

        mat.release()
        grayMat.release()
        keypoints.release()

        return Result.Success(landmarks)
    }

    fun findHomography(
        sourceImageData: ImageData,
        referenceImageData: ImageData,
        detectorType: FeatureDetectorType,
        maxKeypoints: Int,
        ratioTestThreshold: Float,
        ransacThreshold: Float,
    ): Result<FeatureMatchResult> {
        initializer.ensureInitialized()

        val sourceBitmap = decodeBitmap(sourceImageData)
            ?: return Result.Error(
                IllegalArgumentException("Failed to decode source image"),
                "Failed to decode source image",
            )

        val referenceBitmap = decodeBitmap(referenceImageData) ?: run {
            sourceBitmap.recycle()
            return Result.Error(
                IllegalArgumentException("Failed to decode reference image"),
                "Failed to decode reference image",
            )
        }

        val sourceMat = Mat()
        Utils.bitmapToMat(sourceBitmap, sourceMat)
        val sourceGray = Mat()
        Imgproc.cvtColor(sourceMat, sourceGray, Imgproc.COLOR_RGBA2GRAY)

        val referenceMat = Mat()
        Utils.bitmapToMat(referenceBitmap, referenceMat)
        val referenceGray = Mat()
        Imgproc.cvtColor(referenceMat, referenceGray, Imgproc.COLOR_RGBA2GRAY)

        val detector = getDetector(detectorType, maxKeypoints)
        val sourceKeypoints = MatOfKeyPoint()
        val sourceDescriptors = Mat()
        detector.detectAndCompute(sourceGray, Mat(), sourceKeypoints, sourceDescriptors)

        val referenceKeypoints = MatOfKeyPoint()
        val referenceDescriptors = Mat()
        detector.detectAndCompute(referenceGray, Mat(), referenceKeypoints, referenceDescriptors)

        if (sourceKeypoints.rows() < MIN_MATCHES_FOR_HOMOGRAPHY ||
            referenceKeypoints.rows() < MIN_MATCHES_FOR_HOMOGRAPHY
        ) {
            releaseResources(
                sourceBitmap,
                referenceBitmap,
                sourceMat,
                sourceGray,
                referenceMat,
                referenceGray,
                sourceKeypoints,
                sourceDescriptors,
                referenceKeypoints,
                referenceDescriptors,
            )
            return Result.Error(
                IllegalStateException("Not enough keypoints detected"),
                "Insufficient keypoints for matching",
            )
        }

        ensureBfMatcherInitialized(detectorType)
        val knnMatches = mutableListOf<MatOfDMatch>()
        bfMatcher?.knnMatch(sourceDescriptors, referenceDescriptors, knnMatches, K_NEAREST_NEIGHBORS)

        val goodMatches = filterMatches(knnMatches, ratioTestThreshold)
        if (goodMatches.size < MIN_MATCHES_FOR_HOMOGRAPHY) {
            releaseResources(
                sourceBitmap,
                referenceBitmap,
                sourceMat,
                sourceGray,
                referenceMat,
                referenceGray,
                sourceKeypoints,
                sourceDescriptors,
                referenceKeypoints,
                referenceDescriptors,
            )
            return Result.Error(
                IllegalStateException("Not enough good matches: ${goodMatches.size}"),
                "Insufficient matches for homography",
            )
        }

        val srcPointsList = mutableListOf<org.opencv.core.Point>()
        val dstPointsList = mutableListOf<org.opencv.core.Point>()
        val srcKpArray = sourceKeypoints.toArray()
        val refKpArray = referenceKeypoints.toArray()

        for (match in goodMatches) {
            srcPointsList.add(srcKpArray[match.queryIdx].pt)
            dstPointsList.add(refKpArray[match.trainIdx].pt)
        }

        val srcPointsMat = MatOfPoint2f()
        srcPointsMat.fromList(srcPointsList)
        val dstPointsMat = MatOfPoint2f()
        dstPointsMat.fromList(dstPointsList)

        val mask = Mat()
        val homographyMat = Calib3d.findHomography(
            srcPointsMat,
            dstPointsMat,
            Calib3d.RANSAC,
            ransacThreshold.toDouble(),
            mask,
        )

        if (homographyMat.empty()) {
            srcPointsMat.release()
            dstPointsMat.release()
            mask.release()
            releaseResources(
                sourceBitmap,
                referenceBitmap,
                sourceMat,
                sourceGray,
                referenceMat,
                referenceGray,
                sourceKeypoints,
                sourceDescriptors,
                referenceKeypoints,
                referenceDescriptors,
            )
            return Result.Error(
                IllegalStateException("Homography computation failed"),
                "Failed to compute homography",
            )
        }

        val inlierCount = (0 until mask.rows()).count { mask.get(it, 0)[0] > 0 }

        val h = DoubleArray(9)
        homographyMat.get(0, 0, h)
        val homographyMatrix = HomographyMatrix.fromDoubleArray(h)

        val sourceLandmarks = convertToLandscapeLandmarks(
            srcKpArray.toList(),
            sourceImageData.width,
            sourceImageData.height,
            detectorType,
        )

        val referenceLandmarks = convertToLandscapeLandmarks(
            refKpArray.toList(),
            referenceImageData.width,
            referenceImageData.height,
            detectorType,
        )

        val confidence = calculateConfidence(
            matchCount = goodMatches.size,
            inlierCount = inlierCount,
            maxKeypoints = maxKeypoints,
        )

        srcPointsMat.release()
        dstPointsMat.release()
        mask.release()
        homographyMat.release()
        releaseResources(
            sourceBitmap,
            referenceBitmap,
            sourceMat,
            sourceGray,
            referenceMat,
            referenceGray,
            sourceKeypoints,
            sourceDescriptors,
            referenceKeypoints,
            referenceDescriptors,
        )

        return Result.Success(
            FeatureMatchResult(
                homography = homographyMatrix,
                sourceLandmarks = sourceLandmarks,
                referenceLandmarks = referenceLandmarks,
                matchCount = goodMatches.size,
                inlierCount = inlierCount,
                confidence = confidence,
            ),
        )
    }

    fun computeHomography(
        sourceKeypoints: List<FeatureKeypoint>,
        referenceKeypoints: List<FeatureKeypoint>,
        matches: List<Pair<Int, Int>>,
        ransacThreshold: Float,
    ): Result<Pair<HomographyMatrix, Int>> {
        initializer.ensureInitialized()

        if (matches.size < MIN_MATCHES_FOR_HOMOGRAPHY) {
            return Result.Error(
                IllegalArgumentException("Need at least $MIN_MATCHES_FOR_HOMOGRAPHY matches"),
                "Insufficient matches for homography",
            )
        }

        val srcPoints = matches.map { (srcIdx, _) ->
            val kp = sourceKeypoints[srcIdx]
            floatArrayOf(kp.position.x, kp.position.y)
        }
        val dstPoints = matches.map { (_, refIdx) ->
            val kp = referenceKeypoints[refIdx]
            floatArrayOf(kp.position.x, kp.position.y)
        }

        val srcMat = MatOfPoint2f()
        srcMat.fromList(srcPoints.map { org.opencv.core.Point(it[0].toDouble(), it[1].toDouble()) })

        val dstMat = MatOfPoint2f()
        dstMat.fromList(dstPoints.map { org.opencv.core.Point(it[0].toDouble(), it[1].toDouble()) })

        val mask = Mat()
        val homography = Calib3d.findHomography(
            srcMat,
            dstMat,
            Calib3d.RANSAC,
            ransacThreshold.toDouble(),
            mask,
        )

        if (homography.empty()) {
            srcMat.release()
            dstMat.release()
            mask.release()
            return Result.Error(
                IllegalStateException("Homography computation failed"),
                "Failed to compute homography",
            )
        }

        val inlierCount = (0 until mask.rows()).count { mask.get(it, 0)[0] > 0 }

        val h = DoubleArray(9)
        homography.get(0, 0, h)
        val matrix = HomographyMatrix.fromDoubleArray(h)

        srcMat.release()
        dstMat.release()
        mask.release()
        homography.release()

        return Result.Success(Pair(matrix, inlierCount))
    }

    fun release() {
        orbDetector?.clear()
        orbDetector = null
        akazeDetector?.clear()
        akazeDetector = null
        bfMatcher = null
    }

    private fun getDetector(detectorType: FeatureDetectorType, maxKeypoints: Int): org.opencv.features2d.Feature2D =
        when (detectorType) {
            FeatureDetectorType.ORB -> {
                if (orbDetector == null) {
                    orbDetector = ORB.create(maxKeypoints)
                }
                orbDetector!!
            }
            FeatureDetectorType.AKAZE -> {
                if (akazeDetector == null) {
                    akazeDetector = AKAZE.create()
                }
                akazeDetector!!
            }
        }

    private fun ensureBfMatcherInitialized(detectorType: FeatureDetectorType) {
        val normType = when (detectorType) {
            FeatureDetectorType.ORB -> DescriptorMatcher.BRUTEFORCE_HAMMING
            FeatureDetectorType.AKAZE -> DescriptorMatcher.BRUTEFORCE_HAMMING
        }
        if (bfMatcher == null) {
            bfMatcher = BFMatcher.create(normType, false)
        }
    }

    private fun filterMatches(knnMatches: List<MatOfDMatch>, ratioTestThreshold: Float): List<DMatch> {
        val goodMatches = mutableListOf<DMatch>()
        for (match in knnMatches) {
            val matchArray = match.toArray()
            if (matchArray.size >= 2) {
                if (matchArray[0].distance < ratioTestThreshold * matchArray[1].distance) {
                    goodMatches.add(matchArray[0])
                }
            }
            match.release()
        }
        return goodMatches
    }

    private fun convertToLandscapeLandmarks(
        keypoints: List<org.opencv.core.KeyPoint>,
        imageWidth: Int,
        imageHeight: Int,
        detectorType: FeatureDetectorType,
    ): LandscapeLandmarks {
        val featureKeypoints = keypoints.map { kp ->
            FeatureKeypoint.fromPixelCoordinates(
                x = kp.pt.x.toFloat(),
                y = kp.pt.y.toFloat(),
                imageWidth = imageWidth,
                imageHeight = imageHeight,
                response = kp.response,
                size = kp.size,
                angle = kp.angle,
                octave = kp.octave,
            )
        }

        val boundingBox = if (featureKeypoints.isNotEmpty()) {
            val minX = featureKeypoints.minOf { it.position.x }
            val maxX = featureKeypoints.maxOf { it.position.x }
            val minY = featureKeypoints.minOf { it.position.y }
            val maxY = featureKeypoints.maxOf { it.position.y }
            BoundingBox(
                left = minX.coerceAtLeast(0f),
                top = minY.coerceAtLeast(0f),
                right = maxX.coerceAtMost(1f),
                bottom = maxY.coerceAtMost(1f),
            )
        } else {
            BoundingBox(0f, 0f, 1f, 1f)
        }

        val countScore = (featureKeypoints.size.toFloat() / LandscapeLandmarks.RECOMMENDED_KEYPOINTS)
            .coerceAtMost(1f)
        val responseScore = if (featureKeypoints.isNotEmpty()) {
            featureKeypoints.map { it.response }.average().toFloat() / MAX_EXPECTED_RESPONSE
        } else {
            0f
        }.coerceAtMost(1f)
        val qualityScore = (countScore * COUNT_WEIGHT + responseScore * RESPONSE_WEIGHT).coerceIn(0f, 1f)

        return LandscapeLandmarks(
            keypoints = featureKeypoints.take(LandscapeLandmarks.MAX_KEYPOINTS),
            detectorType = detectorType,
            keypointCount = featureKeypoints.size,
            boundingBox = boundingBox,
            qualityScore = qualityScore,
        )
    }

    private fun calculateConfidence(matchCount: Int, inlierCount: Int, maxKeypoints: Int): Float {
        val inlierRatio = if (matchCount == 0) 0f else inlierCount.toFloat() / matchCount
        val matchRatioScore = (matchCount.toFloat() / maxKeypoints).coerceAtMost(1f)
        return (inlierRatio * INLIER_WEIGHT + matchRatioScore * MATCH_WEIGHT).coerceIn(0f, 1f)
    }

    private fun decodeBitmap(imageData: ImageData): Bitmap? =
        BitmapFactory.decodeByteArray(imageData.bytes, 0, imageData.bytes.size)

    private fun releaseResources(
        sourceBitmap: Bitmap,
        referenceBitmap: Bitmap,
        sourceMat: Mat,
        sourceGray: Mat,
        referenceMat: Mat,
        referenceGray: Mat,
        sourceKeypoints: MatOfKeyPoint,
        sourceDescriptors: Mat,
        referenceKeypoints: MatOfKeyPoint,
        referenceDescriptors: Mat,
    ) {
        sourceBitmap.recycle()
        referenceBitmap.recycle()
        sourceMat.release()
        sourceGray.release()
        referenceMat.release()
        referenceGray.release()
        sourceKeypoints.release()
        sourceDescriptors.release()
        referenceKeypoints.release()
        referenceDescriptors.release()
    }

    companion object {
        private const val MIN_MATCHES_FOR_HOMOGRAPHY = 4
        private const val K_NEAREST_NEIGHBORS = 2
        private const val INLIER_WEIGHT = 0.6f
        private const val MATCH_WEIGHT = 0.4f
        private const val COUNT_WEIGHT = 0.7f
        private const val RESPONSE_WEIGHT = 0.3f
        private const val MAX_EXPECTED_RESPONSE = 100f
    }
}
