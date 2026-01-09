package com.po4yka.framelapse.domain.entity

import kotlinx.serialization.Serializable

/**
 * Muscle region for targeted cropping in fitness timelapses.
 * Each region uses different body keypoints to determine crop bounds.
 */
@Serializable
enum class MuscleRegion(val displayName: String, val description: String) {
    /**
     * Full body crop from head to feet.
     * Uses: nose (top), ankles (bottom), shoulders/hips (sides).
     */
    FULL_BODY("Full Body", "Head to feet, full figure"),

    /**
     * Upper body crop: head to hip line.
     * Uses: nose (top), hips (bottom), shoulders (sides).
     */
    UPPER_BODY("Upper Body", "Head to hips, torso focus"),

    /**
     * Lower body crop: hip line to feet.
     * Uses: hips (top), ankles (bottom), hips/ankles (sides).
     */
    LOWER_BODY("Lower Body", "Hips to feet, leg focus"),

    /**
     * Arms focus: shoulders to wrists.
     * Uses: shoulders (top), wrists (bottom), arm span (sides).
     */
    ARMS("Arms", "Shoulder to wrist, arm definition"),

    /**
     * Back/posterior chain focus: shoulders to hip line.
     * Uses: shoulders (top), hips (bottom), shoulder span (sides).
     * Note: User should face away from camera.
     */
    BACK("Back", "Shoulders to hips, back muscles"),
    ;

    companion object {
        /**
         * Parse region from string name.
         * Returns FULL_BODY as default for unknown values.
         */
        fun fromString(value: String): MuscleRegion = entries.find { it.name == value } ?: FULL_BODY
    }
}
