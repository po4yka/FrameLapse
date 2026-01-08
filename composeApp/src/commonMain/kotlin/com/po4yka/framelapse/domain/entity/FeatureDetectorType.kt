package com.po4yka.framelapse.domain.entity

import kotlinx.serialization.Serializable

/**
 * Supported feature detector algorithms for landscape alignment.
 *
 * These detectors extract keypoints from images that can be matched
 * across frames to compute alignment transformations.
 */
@Serializable
enum class FeatureDetectorType(val displayName: String, val description: String) {
    /**
     * Oriented FAST and Rotated BRIEF.
     *
     * Fast binary descriptor-based detector.
     * Pros: Very fast, good for real-time applications.
     * Cons: Less robust to large scale/rotation changes.
     */
    ORB("ORB", "Fast feature detection, good for most scenes"),

    /**
     * Accelerated KAZE.
     *
     * Non-linear scale space based detector.
     * Pros: More robust to scale/rotation, better repeatability.
     * Cons: Slower than ORB, requires more computation.
     */
    AKAZE("AKAZE", "More robust detection, better for complex scenes"),
    ;

    companion object {
        fun fromString(value: String): FeatureDetectorType =
            entries.find { it.name == value } ?: ORB
    }
}
