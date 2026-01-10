package com.po4yka.framelapse.ui.components.adjustment

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.po4yka.framelapse.domain.entity.AdjustmentPointType
import com.po4yka.framelapse.domain.entity.BodyManualAdjustment
import com.po4yka.framelapse.domain.entity.ContentType
import com.po4yka.framelapse.domain.entity.FaceManualAdjustment
import com.po4yka.framelapse.domain.entity.LandscapeManualAdjustment
import com.po4yka.framelapse.domain.entity.ManualAdjustment
import com.po4yka.framelapse.domain.entity.MuscleManualAdjustment

/**
 * Overlay for manual adjustment showing connection lines and drag handles.
 *
 * Combines Canvas drawing for connection lines with draggable handle components.
 *
 * @param contentType The type of content being adjusted
 * @param adjustment The current manual adjustment (or null if none)
 * @param activeDragPoint Currently dragged point type (null if none)
 * @param onDragStart Called when drag starts with the point type
 * @param onDrag Called during drag with delta in normalized coordinates
 * @param onDragEnd Called when drag ends
 * @param imageWidth Image width in pixels
 * @param imageHeight Image height in pixels
 * @param modifier Additional modifier
 */
@Composable
fun AdjustmentOverlay(
    contentType: ContentType,
    adjustment: ManualAdjustment?,
    activeDragPoint: AdjustmentPointType?,
    onDragStart: (AdjustmentPointType) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    imageWidth: Float,
    imageHeight: Float,
    modifier: Modifier = Modifier,
) {
    if (adjustment == null) return

    Box(modifier = modifier.fillMaxSize()) {
        // Draw connection lines
        Canvas(modifier = Modifier.fillMaxSize()) {
            when (adjustment) {
                is FaceManualAdjustment -> drawFaceConnections(adjustment)
                is BodyManualAdjustment -> drawBodyConnections(adjustment)
                is MuscleManualAdjustment -> drawBodyConnections(adjustment.bodyAdjustment)
                is LandscapeManualAdjustment -> drawLandscapeConnections(adjustment)
            }
        }

        // Draw drag handles
        when (adjustment) {
            is FaceManualAdjustment -> {
                FaceDragHandles(
                    adjustment = adjustment,
                    imageWidth = imageWidth,
                    imageHeight = imageHeight,
                    activeDragPoint = activeDragPoint,
                    onDragStart = onDragStart,
                    onDrag = onDrag,
                    onDragEnd = onDragEnd,
                )
            }
            is BodyManualAdjustment -> {
                BodyDragHandles(
                    adjustment = adjustment,
                    imageWidth = imageWidth,
                    imageHeight = imageHeight,
                    activeDragPoint = activeDragPoint,
                    onDragStart = onDragStart,
                    onDrag = onDrag,
                    onDragEnd = onDragEnd,
                )
            }
            is MuscleManualAdjustment -> {
                MuscleDragHandles(
                    adjustment = adjustment,
                    imageWidth = imageWidth,
                    imageHeight = imageHeight,
                    activeDragPoint = activeDragPoint,
                    onDragStart = onDragStart,
                    onDrag = onDrag,
                    onDragEnd = onDragEnd,
                )
            }
            is LandscapeManualAdjustment -> {
                LandscapeDragHandles(
                    adjustment = adjustment,
                    imageWidth = imageWidth,
                    imageHeight = imageHeight,
                    activeDragPoint = activeDragPoint,
                    onDragStart = onDragStart,
                    onDrag = onDrag,
                    onDragEnd = onDragEnd,
                )
            }
        }
    }
}

/**
 * Draws connection lines between face landmarks (eyes).
 */
private fun DrawScope.drawFaceConnections(adjustment: FaceManualAdjustment) {
    val lineColor = Color.White.copy(alpha = 0.7f)

    val leftEye = Offset(
        adjustment.leftEyeCenter.x * size.width,
        adjustment.leftEyeCenter.y * size.height,
    )
    val rightEye = Offset(
        adjustment.rightEyeCenter.x * size.width,
        adjustment.rightEyeCenter.y * size.height,
    )

    // Draw line between eyes
    drawLine(
        color = lineColor,
        start = leftEye,
        end = rightEye,
        strokeWidth = 2f,
        cap = StrokeCap.Round,
    )

    // Draw horizontal alignment guide
    val avgY = (leftEye.y + rightEye.y) / 2
    val dashEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)

    drawLine(
        color = Color.White.copy(alpha = 0.3f),
        start = Offset(0f, avgY),
        end = Offset(size.width, avgY),
        strokeWidth = 1f,
        pathEffect = dashEffect,
    )

    // Draw nose tip if available
    adjustment.noseTip?.let { nose ->
        val nosePos = Offset(
            nose.x * size.width,
            nose.y * size.height,
        )

        // Draw vertical guide from midpoint between eyes to nose
        val eyeCenter = Offset((leftEye.x + rightEye.x) / 2, (leftEye.y + rightEye.y) / 2)
        drawLine(
            color = lineColor.copy(alpha = 0.5f),
            start = eyeCenter,
            end = nosePos,
            strokeWidth = 1f,
        )
    }
}

/**
 * Draws connection lines between body landmarks (shoulders, hips).
 */
private fun DrawScope.drawBodyConnections(adjustment: BodyManualAdjustment) {
    val lineColor = Color.White.copy(alpha = 0.7f)
    val guideColor = Color.White.copy(alpha = 0.3f)

    val leftShoulder = Offset(
        adjustment.leftShoulder.x * size.width,
        adjustment.leftShoulder.y * size.height,
    )
    val rightShoulder = Offset(
        adjustment.rightShoulder.x * size.width,
        adjustment.rightShoulder.y * size.height,
    )
    val leftHip = Offset(
        adjustment.leftHip.x * size.width,
        adjustment.leftHip.y * size.height,
    )
    val rightHip = Offset(
        adjustment.rightHip.x * size.width,
        adjustment.rightHip.y * size.height,
    )

    // Draw shoulder line
    drawLine(
        color = lineColor,
        start = leftShoulder,
        end = rightShoulder,
        strokeWidth = 2f,
        cap = StrokeCap.Round,
    )

    // Draw hip line
    drawLine(
        color = lineColor,
        start = leftHip,
        end = rightHip,
        strokeWidth = 2f,
        cap = StrokeCap.Round,
    )

    // Draw torso sides
    drawLine(
        color = lineColor.copy(alpha = 0.5f),
        start = leftShoulder,
        end = leftHip,
        strokeWidth = 1.5f,
        cap = StrokeCap.Round,
    )
    drawLine(
        color = lineColor.copy(alpha = 0.5f),
        start = rightShoulder,
        end = rightHip,
        strokeWidth = 1.5f,
        cap = StrokeCap.Round,
    )

    // Draw center spine guide
    val shoulderCenter = Offset(
        (leftShoulder.x + rightShoulder.x) / 2,
        (leftShoulder.y + rightShoulder.y) / 2,
    )
    val hipCenter = Offset(
        (leftHip.x + rightHip.x) / 2,
        (leftHip.y + rightHip.y) / 2,
    )

    val dashEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
    drawLine(
        color = guideColor,
        start = shoulderCenter,
        end = hipCenter,
        strokeWidth = 1f,
        pathEffect = dashEffect,
    )

    // Draw horizontal alignment guides
    drawLine(
        color = guideColor,
        start = Offset(0f, shoulderCenter.y),
        end = Offset(size.width, shoulderCenter.y),
        strokeWidth = 1f,
        pathEffect = dashEffect,
    )
}

/**
 * Draws connection lines for landscape corners (quadrilateral).
 */
private fun DrawScope.drawLandscapeConnections(adjustment: LandscapeManualAdjustment) {
    val lineColor = Color.White.copy(alpha = 0.7f)

    val corners = adjustment.cornerKeypoints
    if (corners.size < 4) return

    val positions = corners.map { point ->
        Offset(
            point.x * size.width,
            point.y * size.height,
        )
    }

    // Draw quadrilateral outline
    val path = Path().apply {
        moveTo(positions[0].x, positions[0].y) // TL
        lineTo(positions[1].x, positions[1].y) // TR
        lineTo(positions[3].x, positions[3].y) // BR
        lineTo(positions[2].x, positions[2].y) // BL
        close()
    }

    drawPath(
        path = path,
        color = lineColor,
        style = Stroke(width = 2f),
    )

    // Draw diagonals
    val guideColor = Color.White.copy(alpha = 0.3f)
    val dashEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)

    drawLine(
        color = guideColor,
        start = positions[0], // TL
        end = positions[3], // BR
        strokeWidth = 1f,
        pathEffect = dashEffect,
    )
    drawLine(
        color = guideColor,
        start = positions[1], // TR
        end = positions[2], // BL
        strokeWidth = 1f,
        pathEffect = dashEffect,
    )

    // Draw center cross
    val center = Offset(
        positions.map { it.x }.average().toFloat(),
        positions.map { it.y }.average().toFloat(),
    )
    val crossSize = 20f

    drawLine(
        color = guideColor,
        start = Offset(center.x - crossSize, center.y),
        end = Offset(center.x + crossSize, center.y),
        strokeWidth = 1f,
    )
    drawLine(
        color = guideColor,
        start = Offset(center.x, center.y - crossSize),
        end = Offset(center.x, center.y + crossSize),
        strokeWidth = 1f,
    )
}
