package com.po4yka.framelapse.platform.util

import com.po4yka.framelapse.domain.entity.BoundingBox
import com.po4yka.framelapse.domain.entity.LandmarkPoint

/**
 * Calculates a bounding box from a list of landmark points with optional padding.
 *
 * @param points The list of landmark points to calculate bounds from.
 * @param padding The padding to apply as a fraction of the bounding box dimensions.
 * @return A BoundingBox with coordinates normalized to [0, 1] range.
 */
fun calculateBoundingBox(points: List<LandmarkPoint>, padding: Float = 0f): BoundingBox {
    if (points.isEmpty()) {
        return BoundingBox(0f, 0f, 1f, 1f)
    }

    val minX = points.minOf { it.x }
    val minY = points.minOf { it.y }
    val maxX = points.maxOf { it.x }
    val maxY = points.maxOf { it.y }

    return if (padding > 0f) {
        val paddingX = (maxX - minX) * padding
        val paddingY = (maxY - minY) * padding
        BoundingBox(
            left = (minX - paddingX).coerceAtLeast(0f),
            top = (minY - paddingY).coerceAtLeast(0f),
            right = (maxX + paddingX).coerceAtMost(1f),
            bottom = (maxY + paddingY).coerceAtMost(1f),
        )
    } else {
        BoundingBox(
            left = minX.coerceAtLeast(0f),
            top = minY.coerceAtLeast(0f),
            right = maxX.coerceAtMost(1f),
            bottom = maxY.coerceAtMost(1f),
        )
    }
}
