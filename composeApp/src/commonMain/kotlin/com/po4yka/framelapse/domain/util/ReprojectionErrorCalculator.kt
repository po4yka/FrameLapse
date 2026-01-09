package com.po4yka.framelapse.domain.util

import com.po4yka.framelapse.domain.entity.FeatureKeypoint
import com.po4yka.framelapse.domain.entity.HomographyMatrix
import com.po4yka.framelapse.domain.service.ReprojectionErrorResult
import kotlin.math.sqrt

internal object ReprojectionErrorCalculator {
    private const val DEFAULT_INLIER_THRESHOLD = 5.0f

    fun calculate(
        sourceKeypoints: List<FeatureKeypoint>,
        referenceKeypoints: List<FeatureKeypoint>,
        matches: List<Pair<Int, Int>>,
        homography: HomographyMatrix,
        imageWidth: Int,
        imageHeight: Int,
    ): Result<ReprojectionErrorResult> {
        if (matches.isEmpty()) {
            return Result.Error(
                IllegalArgumentException("No matches provided"),
                "Cannot calculate reprojection error without matches",
            )
        }

        val errors = mutableListOf<Float>()
        var inlierCount = 0

        for ((srcIdx, refIdx) in matches) {
            if (srcIdx >= sourceKeypoints.size || refIdx >= referenceKeypoints.size) {
                continue
            }

            val srcKp = sourceKeypoints[srcIdx]
            val refKp = referenceKeypoints[refIdx]

            val srcX = srcKp.position.x * imageWidth
            val srcY = srcKp.position.y * imageHeight
            val refX = refKp.position.x * imageWidth
            val refY = refKp.position.y * imageHeight

            val (projX, projY) = homography.transformPoint(srcX, srcY)

            val dx = projX - refX
            val dy = projY - refY
            val error = sqrt(dx * dx + dy * dy)

            errors.add(error)

            if (error <= DEFAULT_INLIER_THRESHOLD) {
                inlierCount++
            }
        }

        if (errors.isEmpty()) {
            return Result.Error(
                IllegalStateException("No valid matches for error calculation"),
                "No valid matches",
            )
        }

        val sortedErrors = errors.sorted()
        val meanError = errors.average().toFloat()
        val medianError = if (sortedErrors.size % 2 == 0) {
            (sortedErrors[sortedErrors.size / 2 - 1] + sortedErrors[sortedErrors.size / 2]) / 2f
        } else {
            sortedErrors[sortedErrors.size / 2]
        }
        val maxError = sortedErrors.last()

        return Result.Success(
            ReprojectionErrorResult(
                meanError = meanError,
                medianError = medianError,
                maxError = maxError,
                inlierCount = inlierCount,
                totalMatches = errors.size,
                inlierThreshold = DEFAULT_INLIER_THRESHOLD,
                errors = errors,
            ),
        )
    }
}
