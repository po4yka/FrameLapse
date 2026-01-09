package com.po4yka.framelapse.platform

import com.po4yka.framelapse.domain.entity.FeatureDetectorType
import com.po4yka.framelapse.domain.entity.FeatureKeypoint
import com.po4yka.framelapse.domain.entity.HomographyMatrix
import com.po4yka.framelapse.domain.entity.LandscapeLandmarks
import com.po4yka.framelapse.domain.service.FeatureMatchResult
import com.po4yka.framelapse.domain.service.FeatureMatcher
import com.po4yka.framelapse.domain.service.ImageData
import com.po4yka.framelapse.domain.service.ReprojectionErrorResult
import com.po4yka.framelapse.domain.util.Result

class FeatureMatcherImpl : FeatureMatcher {
    private val core = IosFeatureMatcherCore(IosImageCodec())

    override val isAvailable: Boolean
        get() = core.isAvailable

    override suspend fun detectFeatures(
        imageData: ImageData,
        detectorType: FeatureDetectorType,
        maxKeypoints: Int,
    ): Result<LandscapeLandmarks> = core.detectFeatures(imageData, detectorType, maxKeypoints, isSource = true)

    override suspend fun detectFeaturesFromPath(
        imagePath: String,
        detectorType: FeatureDetectorType,
        maxKeypoints: Int,
    ): Result<LandscapeLandmarks> = core.detectFeaturesFromPath(imagePath, detectorType, maxKeypoints)

    override suspend fun matchFeatures(
        sourceFeatures: LandscapeLandmarks,
        referenceFeatures: LandscapeLandmarks,
        ratioTestThreshold: Float,
        useCrossCheck: Boolean,
    ): Result<List<Pair<Int, Int>>> =
        core.matchFeatures(sourceFeatures, referenceFeatures, ratioTestThreshold, useCrossCheck)

    override suspend fun computeHomography(
        sourceKeypoints: List<FeatureKeypoint>,
        referenceKeypoints: List<FeatureKeypoint>,
        matches: List<Pair<Int, Int>>,
        ransacThreshold: Float,
    ): Result<Pair<HomographyMatrix, Int>> =
        core.computeHomography(sourceKeypoints, referenceKeypoints, matches, ransacThreshold)

    override suspend fun findHomography(
        sourceImageData: ImageData,
        referenceImageData: ImageData,
        detectorType: FeatureDetectorType,
        maxKeypoints: Int,
        ratioTestThreshold: Float,
        ransacThreshold: Float,
    ): Result<FeatureMatchResult> = core.findHomography(
        sourceImageData = sourceImageData,
        referenceImageData = referenceImageData,
        detectorType = detectorType,
        maxKeypoints = maxKeypoints,
        ratioTestThreshold = ratioTestThreshold,
        ransacThreshold = ransacThreshold,
    )

    override suspend fun calculateReprojectionError(
        sourceKeypoints: List<FeatureKeypoint>,
        referenceKeypoints: List<FeatureKeypoint>,
        matches: List<Pair<Int, Int>>,
        homography: HomographyMatrix,
        imageWidth: Int,
        imageHeight: Int,
    ): Result<ReprojectionErrorResult> = core.calculateReprojectionError(
        sourceKeypoints = sourceKeypoints,
        referenceKeypoints = referenceKeypoints,
        matches = matches,
        homography = homography,
        imageWidth = imageWidth,
        imageHeight = imageHeight,
    )

    override fun release() {
        core.release()
    }
}
