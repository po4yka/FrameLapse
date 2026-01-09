package com.po4yka.framelapse.domain.service

import com.po4yka.framelapse.domain.entity.ModelCapabilities

/**
 * Provides available model capabilities on the current device.
 */
interface ModelCapabilitiesProvider {
    fun getCapabilities(): ModelCapabilities
}

class ModelCapabilitiesProviderImpl(
    private val faceDetector: FaceDetector,
    private val bodyPoseDetector: BodyPoseDetector,
    private val featureMatcher: FeatureMatcher,
) : ModelCapabilitiesProvider {
    override fun getCapabilities(): ModelCapabilities = ModelCapabilities(
        faceDetectionAvailable = faceDetector.isAvailable,
        bodyPoseAvailable = bodyPoseDetector.isAvailable,
        featureMatchingAvailable = featureMatcher.isAvailable,
    )
}
