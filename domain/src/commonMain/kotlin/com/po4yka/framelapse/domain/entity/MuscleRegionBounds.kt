package com.po4yka.framelapse.domain.entity

import kotlinx.serialization.Serializable
import kotlin.math.max

/**
 * Calculated bounds for a muscle region crop.
 * Uses normalized coordinates (0.0-1.0) relative to the aligned image.
 */
@Serializable
data class MuscleRegionBounds(
    /** The muscle region these bounds represent. */
    val region: MuscleRegion,

    /** Left edge of the crop region (0.0-1.0). */
    val left: Float,

    /** Top edge of the crop region (0.0-1.0). */
    val top: Float,

    /** Right edge of the crop region (0.0-1.0). */
    val right: Float,

    /** Bottom edge of the crop region (0.0-1.0). */
    val bottom: Float,
) {
    /** Width of the region (0.0-1.0). */
    val width: Float get() = right - left

    /** Height of the region (0.0-1.0). */
    val height: Float get() = bottom - top

    /** Horizontal center of the region. */
    val centerX: Float get() = left + width / 2

    /** Vertical center of the region. */
    val centerY: Float get() = top + height / 2

    /**
     * Converts to square bounds maintaining aspect ratio.
     * Expands the shorter dimension to match the longer one,
     * keeping the region centered.
     */
    fun toSquareBounds(): MuscleRegionBounds {
        val size = max(width, height)
        var newLeft = centerX - size / 2
        var newTop = centerY - size / 2
        var newRight = newLeft + size
        var newBottom = newTop + size

        // Clamp to valid bounds and adjust to maintain square
        if (newLeft < 0f) {
            newLeft = 0f
            newRight = size.coerceAtMost(1f)
        }
        if (newRight > 1f) {
            newRight = 1f
            newLeft = (1f - size).coerceAtLeast(0f)
        }
        if (newTop < 0f) {
            newTop = 0f
            newBottom = size.coerceAtMost(1f)
        }
        if (newBottom > 1f) {
            newBottom = 1f
            newTop = (1f - size).coerceAtLeast(0f)
        }

        return copy(
            left = newLeft,
            top = newTop,
            right = newRight,
            bottom = newBottom,
        )
    }

    /**
     * Converts normalized bounds to pixel coordinates.
     *
     * @param imageWidth Width of the image in pixels.
     * @param imageHeight Height of the image in pixels.
     * @return BoundingBox with pixel coordinates.
     */
    fun toPixelBounds(imageWidth: Int, imageHeight: Int): BoundingBox = BoundingBox(
        left = left * imageWidth,
        top = top * imageHeight,
        right = right * imageWidth,
        bottom = bottom * imageHeight,
    )

    /**
     * Applies padding around the bounds.
     *
     * @param padding Padding as a fraction of the region size (0.0-0.5).
     * @return New bounds with padding applied.
     */
    fun withPadding(padding: Float): MuscleRegionBounds {
        val paddingX = width * padding
        val paddingY = height * padding
        return copy(
            left = (left - paddingX).coerceAtLeast(0f),
            top = (top - paddingY).coerceAtLeast(0f),
            right = (right + paddingX).coerceAtMost(1f),
            bottom = (bottom + paddingY).coerceAtMost(1f),
        )
    }
}
