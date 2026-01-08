package com.po4yka.framelapse.platform

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.po4yka.framelapse.domain.entity.BoundingBox
import com.po4yka.framelapse.domain.entity.FeatureDetectorType
import com.po4yka.framelapse.domain.entity.FeatureKeypoint
import com.po4yka.framelapse.domain.entity.HomographyMatrix
import com.po4yka.framelapse.domain.entity.LandmarkPoint
import com.po4yka.framelapse.domain.entity.LandscapeLandmarks
import com.po4yka.framelapse.domain.service.FeatureMatchResult
import com.po4yka.framelapse.domain.service.FeatureMatcher
import com.po4yka.framelapse.domain.service.ImageData
import com.po4yka.framelapse.domain.util.Result
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.calib3d.Calib3d
import org.opencv.core.CvType
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
import java.io.File

/**
 * Android implementation of FeatureMatcher using OpenCV Android SDK.
 *
 * Uses ORB or AKAZE feature detectors for keypoint extraction and
 * brute-force matching with Lowe's ratio test for robust matching.
 */
class FeatureMatcherImpl(private val context: Context) : FeatureMatcher {

    private val mutex = Mutex()
    private var isOpenCvInitialized = false

    private var orbDetector: ORB? = null
    private var akazeDetector: AKAZE? = null
    private var bfMatcher: BFMatcher? = null

    override val isAvailable: Boolean
        get() = try {
            ensureOpenCvInitialized()
            isOpenCvInitialized
        } catch (e: Exception) {
            false
        }

    override suspend fun detectFeatures(
        imageData: ImageData,
        detectorType: FeatureDetectorType,
        maxKeypoints: Int,
    ): Result<LandscapeLandmarks> = withContext(Dispatchers.Default) {
        mutex.withLock {
            try {
                ensureOpenCvInitialized()

                val bitmap = BitmapFactory.decodeByteArray(
                    imageData.bytes,
                    0,
                    imageData.bytes.size,
                ) ?: return@withContext Result.Error(
                    IllegalArgumentException("Failed to decode image"),
                    "Failed to decode image",
                )

                val result = detectFeaturesFromBitmap(
                    bitmap,
                    imageData.width,
                    imageData.height,
                    detectorType,
                    maxKeypoints,
                )

                bitmap.recycle()
                result
            } catch (e: Exception) {
                Result.Error(e, "Feature detection failed: ${e.message}")
            }
        }
    }

    override suspend fun detectFeaturesFromPath(
        imagePath: String,
        detectorType: FeatureDetectorType,
        maxKeypoints: Int,
    ): Result<LandscapeLandmarks> = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                if (!File(imagePath).exists()) {
                    return@withContext Result.Error(
                        IllegalArgumentException("File not found: $imagePath"),
                        "File not found",
                    )
                }

                ensureOpenCvInitialized()

                val bitmap = BitmapFactory.decodeFile(imagePath)
                    ?: return@withContext Result.Error(
                        IllegalArgumentException("Failed to decode image: $imagePath"),
                        "Failed to decode image",
                    )

                val result = detectFeaturesFromBitmap(
                    bitmap,
                    bitmap.width,
                    bitmap.height,
                    detectorType,
                    maxKeypoints,
                )

                bitmap.recycle()
                result
            } catch (e: Exception) {
                Result.Error(e, "Feature detection failed: ${e.message}")
            }
        }
    }

    override suspend fun matchFeatures(
        sourceFeatures: LandscapeLandmarks,
        referenceFeatures: LandscapeLandmarks,
        ratioTestThreshold: Float,
        useCrossCheck: Boolean,
    ): Result<List<Pair<Int, Int>>> = withContext(Dispatchers.Default) {
        mutex.withLock {
            try {
                ensureOpenCvInitialized()

                if (sourceFeatures.keypoints.isEmpty() || referenceFeatures.keypoints.isEmpty()) {
                    return@withContext Result.Error(
                        IllegalArgumentException("Cannot match with empty keypoint sets"),
                        "No keypoints to match",
                    )
                }

                // For brute-force matching, we need descriptors which are not stored in LandscapeLandmarks
                // This method is meant to be called after detectFeatures, which stores descriptors internally
                // For now, return an error indicating this limitation
                return@withContext Result.Error(
                    UnsupportedOperationException(
                        "matchFeatures requires descriptor data. Use findHomography for full pipeline.",
                    ),
                    "Use findHomography for matching with descriptors",
                )
            } catch (e: Exception) {
                Result.Error(e, "Feature matching failed: ${e.message}")
            }
        }
    }

    override suspend fun computeHomography(
        sourceKeypoints: List<FeatureKeypoint>,
        referenceKeypoints: List<FeatureKeypoint>,
        matches: List<Pair<Int, Int>>,
        ransacThreshold: Float,
    ): Result<Pair<HomographyMatrix, Int>> = withContext(Dispatchers.Default) {
        mutex.withLock {
            try {
                ensureOpenCvInitialized()

                if (matches.size < MIN_MATCHES_FOR_HOMOGRAPHY) {
                    return@withContext Result.Error(
                        IllegalArgumentException("Need at least $MIN_MATCHES_FOR_HOMOGRAPHY matches"),
                        "Insufficient matches for homography",
                    )
                }

                // Extract matched points (using pixel coordinates, assuming normalized 0-1)
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
                    return@withContext Result.Error(
                        IllegalStateException("Homography computation failed"),
                        "Failed to compute homography",
                    )
                }

                // Count inliers
                val inlierCount = (0 until mask.rows()).count { mask.get(it, 0)[0] > 0 }

                // Extract homography matrix values
                val h = DoubleArray(9)
                homography.get(0, 0, h)
                val matrix = HomographyMatrix.fromDoubleArray(h)

                srcMat.release()
                dstMat.release()
                mask.release()
                homography.release()

                Result.Success(Pair(matrix, inlierCount))
            } catch (e: Exception) {
                Result.Error(e, "Homography computation failed: ${e.message}")
            }
        }
    }

    override suspend fun findHomography(
        sourceImageData: ImageData,
        referenceImageData: ImageData,
        detectorType: FeatureDetectorType,
        maxKeypoints: Int,
        ratioTestThreshold: Float,
        ransacThreshold: Float,
    ): Result<FeatureMatchResult> = withContext(Dispatchers.Default) {
        mutex.withLock {
            try {
                ensureOpenCvInitialized()

                // Decode source image
                val sourceBitmap = BitmapFactory.decodeByteArray(
                    sourceImageData.bytes,
                    0,
                    sourceImageData.bytes.size,
                ) ?: return@withContext Result.Error(
                    IllegalArgumentException("Failed to decode source image"),
                    "Failed to decode source image",
                )

                // Decode reference image
                val referenceBitmap = BitmapFactory.decodeByteArray(
                    referenceImageData.bytes,
                    0,
                    referenceImageData.bytes.size,
                ) ?: run {
                    sourceBitmap.recycle()
                    return@withContext Result.Error(
                        IllegalArgumentException("Failed to decode reference image"),
                        "Failed to decode reference image",
                    )
                }

                // Convert to OpenCV Mat
                val sourceMat = Mat()
                Utils.bitmapToMat(sourceBitmap, sourceMat)
                val sourceGray = Mat()
                Imgproc.cvtColor(sourceMat, sourceGray, Imgproc.COLOR_RGBA2GRAY)

                val referenceMat = Mat()
                Utils.bitmapToMat(referenceBitmap, referenceMat)
                val referenceGray = Mat()
                Imgproc.cvtColor(referenceMat, referenceGray, Imgproc.COLOR_RGBA2GRAY)

                // Detect features
                val detector = getDetector(detectorType, maxKeypoints)
                val sourceKeypoints = MatOfKeyPoint()
                val sourceDescriptors = Mat()
                detector.detectAndCompute(sourceGray, Mat(), sourceKeypoints, sourceDescriptors)

                val referenceKeypoints = MatOfKeyPoint()
                val referenceDescriptors = Mat()
                detector.detectAndCompute(referenceGray, Mat(), referenceKeypoints, referenceDescriptors)

                // Check if we have enough keypoints
                if (sourceKeypoints.rows() < MIN_MATCHES_FOR_HOMOGRAPHY ||
                    referenceKeypoints.rows() < MIN_MATCHES_FOR_HOMOGRAPHY
                ) {
                    releaseResources(
                        sourceBitmap, referenceBitmap, sourceMat, sourceGray,
                        referenceMat, referenceGray, sourceKeypoints, sourceDescriptors,
                        referenceKeypoints, referenceDescriptors,
                    )
                    return@withContext Result.Error(
                        IllegalStateException("Not enough keypoints detected"),
                        "Insufficient keypoints for matching",
                    )
                }

                // Match features using KNN and Lowe's ratio test
                ensureBfMatcherInitialized(detectorType)
                val knnMatches = mutableListOf<MatOfDMatch>()
                bfMatcher?.knnMatch(sourceDescriptors, referenceDescriptors, knnMatches, K_NEAREST_NEIGHBORS)

                // Apply Lowe's ratio test
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

                if (goodMatches.size < MIN_MATCHES_FOR_HOMOGRAPHY) {
                    releaseResources(
                        sourceBitmap, referenceBitmap, sourceMat, sourceGray,
                        referenceMat, referenceGray, sourceKeypoints, sourceDescriptors,
                        referenceKeypoints, referenceDescriptors,
                    )
                    return@withContext Result.Error(
                        IllegalStateException("Not enough good matches: ${goodMatches.size}"),
                        "Insufficient matches for homography",
                    )
                }

                // Extract matched point coordinates for homography
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

                // Compute homography with RANSAC
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
                        sourceBitmap, referenceBitmap, sourceMat, sourceGray,
                        referenceMat, referenceGray, sourceKeypoints, sourceDescriptors,
                        referenceKeypoints, referenceDescriptors,
                    )
                    return@withContext Result.Error(
                        IllegalStateException("Homography computation failed"),
                        "Failed to compute homography",
                    )
                }

                // Count inliers
                val inlierCount = (0 until mask.rows()).count { mask.get(it, 0)[0] > 0 }

                // Extract homography matrix values
                val h = DoubleArray(9)
                homographyMat.get(0, 0, h)
                val homographyMatrix = HomographyMatrix.fromDoubleArray(h)

                // Convert keypoints to domain model
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

                // Calculate confidence based on inlier ratio and match count
                val inlierRatio = inlierCount.toFloat() / goodMatches.size
                val matchRatioScore = (goodMatches.size.toFloat() / maxKeypoints).coerceAtMost(1f)
                val confidence = (inlierRatio * INLIER_WEIGHT + matchRatioScore * MATCH_WEIGHT).coerceIn(0f, 1f)

                // Release resources
                srcPointsMat.release()
                dstPointsMat.release()
                mask.release()
                homographyMat.release()
                releaseResources(
                    sourceBitmap, referenceBitmap, sourceMat, sourceGray,
                    referenceMat, referenceGray, sourceKeypoints, sourceDescriptors,
                    referenceKeypoints, referenceDescriptors,
                )

                Result.Success(
                    FeatureMatchResult(
                        homography = homographyMatrix,
                        sourceLandmarks = sourceLandmarks,
                        referenceLandmarks = referenceLandmarks,
                        matchCount = goodMatches.size,
                        inlierCount = inlierCount,
                        confidence = confidence,
                    ),
                )
            } catch (e: Exception) {
                Result.Error(e, "findHomography failed: ${e.message}")
            }
        }
    }

    override fun release() {
        orbDetector?.clear()
        orbDetector = null
        akazeDetector?.clear()
        akazeDetector = null
        bfMatcher = null
    }

    private fun ensureOpenCvInitialized() {
        if (isOpenCvInitialized) return

        // Try initLocal first (newer API), fallback to initDebug
        isOpenCvInitialized = try {
            OpenCVLoader.initLocal()
        } catch (e: Exception) {
            @Suppress("DEPRECATION")
            OpenCVLoader.initDebug()
        }

        if (!isOpenCvInitialized) {
            throw IllegalStateException("Failed to initialize OpenCV")
        }
    }

    private fun getDetector(detectorType: FeatureDetectorType, maxKeypoints: Int): org.opencv.features2d.Feature2D {
        return when (detectorType) {
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

    private fun detectFeaturesFromBitmap(
        bitmap: Bitmap,
        imageWidth: Int,
        imageHeight: Int,
        detectorType: FeatureDetectorType,
        maxKeypoints: Int,
    ): Result<LandscapeLandmarks> {
        // Convert bitmap to OpenCV Mat
        val mat = Mat()
        Utils.bitmapToMat(bitmap, mat)

        // Convert to grayscale for feature detection
        val grayMat = Mat()
        Imgproc.cvtColor(mat, grayMat, Imgproc.COLOR_RGBA2GRAY)

        // Get detector
        val detector = getDetector(detectorType, maxKeypoints)

        // Detect keypoints
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

    private fun convertToLandscapeLandmarks(
        keypoints: List<org.opencv.core.KeyPoint>,
        imageWidth: Int,
        imageHeight: Int,
        detectorType: FeatureDetectorType,
    ): LandscapeLandmarks {
        // Convert OpenCV keypoints to domain keypoints with normalized coordinates
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

        // Calculate bounding box from keypoints
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

        // Calculate quality score based on keypoint count and distribution
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
        /** Minimum number of matches required for homography computation. */
        private const val MIN_MATCHES_FOR_HOMOGRAPHY = 4

        /** Number of nearest neighbors for KNN matching. */
        private const val K_NEAREST_NEIGHBORS = 2

        /** Weight for inlier ratio in confidence calculation. */
        private const val INLIER_WEIGHT = 0.6f

        /** Weight for match ratio in confidence calculation. */
        private const val MATCH_WEIGHT = 0.4f

        /** Weight for keypoint count in quality score. */
        private const val COUNT_WEIGHT = 0.7f

        /** Weight for response strength in quality score. */
        private const val RESPONSE_WEIGHT = 0.3f

        /** Maximum expected response value for normalization. */
        private const val MAX_EXPECTED_RESPONSE = 100f
    }
}
