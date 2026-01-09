package com.po4yka.framelapse.domain.entity

import kotlinx.serialization.Serializable

/**
 * Describes model and detector availability on the current device.
 */
@Serializable
data class ModelCapabilities(
    val faceDetectionAvailable: Boolean,
    val bodyPoseAvailable: Boolean,
    val featureMatchingAvailable: Boolean,
)
