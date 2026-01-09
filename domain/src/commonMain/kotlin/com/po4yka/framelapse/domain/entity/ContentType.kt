package com.po4yka.framelapse.domain.entity

import kotlinx.serialization.Serializable

/**
 * Defines the type of content being stabilized in a timelapse project.
 *
 * Each content type uses different reference points for alignment:
 * - FACE: Uses eye positions for alignment (default, existing behavior)
 * - BODY: Uses shoulder positions for upper-body timelapses
 * - MUSCLE: Body alignment + region-specific cropping for muscle focus
 * - LANDSCAPE: Feature matching for nature/architecture timelapses
 */
@Serializable
enum class ContentType(val displayName: String) {
    /**
     * Face-based alignment using eye centers as reference points.
     * Best for: Daily selfies, aging timelapses, portrait timelapses.
     * Framing: Head-centered with consistent eye positioning.
     */
    FACE("Face"),

    /**
     * Body-based alignment using shoulder centers as reference points.
     * Best for: Fitness progress, posture tracking, upper-body timelapses.
     * Framing: Head-to-waist with consistent shoulder positioning.
     */
    BODY("Body"),

    /**
     * Muscle-focused body alignment with region-specific cropping.
     * Best for: Fitness progress tracking, muscle definition comparison.
     * Framing: Depends on selected muscle region (full body, upper, lower, arms, back).
     * Uses body alignment (shoulder-based) then crops to the target region.
     */
    MUSCLE("Muscle"),

    /**
     * Landscape-based alignment using OpenCV feature matching.
     * Best for: Nature timelapses, architecture, construction progress, scenery.
     * Framing: Maintains consistent scene composition using homography transformation.
     * Uses ORB/AKAZE feature detection with RANSAC-based homography estimation.
     */
    LANDSCAPE("Landscape"),
    ;

    companion object {
        fun fromString(value: String): ContentType = entries.find { it.name == value } ?: FACE
    }
}
