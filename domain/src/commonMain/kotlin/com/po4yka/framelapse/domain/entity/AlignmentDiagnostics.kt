package com.po4yka.framelapse.domain.entity

import kotlinx.serialization.Serializable

/**
 * Diagnostics captured during alignment for troubleshooting and quality audits.
 */
@Serializable
data class AlignmentDiagnostics(
    /** Whether aligned landmarks were detected successfully. */
    val alignedLandmarksDetected: Boolean,
    /** Optional error message if aligned landmark detection failed. */
    val alignedLandmarksError: String? = null,
    /** Whether fallback landmarks were generated instead of detected ones. */
    val fallbackLandmarksGenerated: Boolean = false,
    /** Optional reference frame id used for alignment. */
    val referenceFrameId: String? = null,
)
