package com.po4yka.framelapse.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.po4yka.framelapse.domain.entity.FaceLandmarks

/**
 * Face alignment guide overlay for the camera preview.
 * Shows target positions for optimal face alignment and current face position.
 *
 * @param currentLandmarks Currently detected face landmarks (null if no face detected)
 * @param targetLandmarks Reference face landmarks for alignment target (null if no reference)
 * @param showTargetGuide Whether to show the target alignment guide
 * @param modifier Modifier for the overlay
 */
@Composable
fun FaceAlignmentGuide(
    currentLandmarks: FaceLandmarks?,
    targetLandmarks: FaceLandmarks?,
    showTargetGuide: Boolean = true,
    modifier: Modifier = Modifier,
) {
    // Animate visibility
    val alpha by animateFloatAsState(
        targetValue = if (currentLandmarks != null || showTargetGuide) 1f else 0f,
        animationSpec = tween(durationMillis = 200),
        label = "guide_alpha",
    )

    if (alpha <= 0f) return

    Canvas(modifier = modifier.fillMaxSize()) {
        // Draw target guide (dashed circles for eyes, nose position)
        if (showTargetGuide && targetLandmarks != null) {
            drawTargetGuide(targetLandmarks, alpha)
        } else if (showTargetGuide) {
            // Draw default centered target guide if no reference landmarks
            drawDefaultTargetGuide(alpha)
        }

        // Draw current face position
        if (currentLandmarks != null) {
            drawCurrentFacePosition(currentLandmarks, alpha)

            // Draw alignment lines connecting current to target
            if (targetLandmarks != null) {
                drawAlignmentLines(currentLandmarks, targetLandmarks, alpha)
            }
        }
    }
}

/**
 * Draws the target guide based on reference landmarks.
 */
private fun DrawScope.drawTargetGuide(landmarks: FaceLandmarks, alpha: Float) {
    val guideColor = Color.White.copy(alpha = 0.5f * alpha)
    val dashEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)

    // Draw target eye positions
    val leftEyeCenter = Offset(
        x = landmarks.leftEyeCenter.x * size.width,
        y = landmarks.leftEyeCenter.y * size.height,
    )
    val rightEyeCenter = Offset(
        x = landmarks.rightEyeCenter.x * size.width,
        y = landmarks.rightEyeCenter.y * size.height,
    )

    // Draw eye target circles
    val eyeRadius = size.minDimension * 0.04f
    drawCircle(
        color = guideColor,
        radius = eyeRadius,
        center = leftEyeCenter,
        style = Stroke(width = 2f, pathEffect = dashEffect),
    )
    drawCircle(
        color = guideColor,
        radius = eyeRadius,
        center = rightEyeCenter,
        style = Stroke(width = 2f, pathEffect = dashEffect),
    )

    // Draw nose tip target
    val noseTip = Offset(
        x = landmarks.noseTip.x * size.width,
        y = landmarks.noseTip.y * size.height,
    )
    drawCircle(
        color = guideColor,
        radius = size.minDimension * 0.02f,
        center = noseTip,
        style = Stroke(width = 2f, pathEffect = dashEffect),
    )

    // Draw connecting line between eyes
    drawLine(
        color = guideColor,
        start = leftEyeCenter,
        end = rightEyeCenter,
        strokeWidth = 1f,
        pathEffect = dashEffect,
    )
}

/**
 * Draws a default centered target guide when no reference landmarks exist.
 */
private fun DrawScope.drawDefaultTargetGuide(alpha: Float) {
    val guideColor = Color.White.copy(alpha = 0.4f * alpha)
    val dashEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)

    val centerX = size.width / 2
    val centerY = size.height * 0.4f // Face should be in upper part of frame

    // Eye positions (roughly 1/4 of face width apart)
    val eyeSpacing = size.width * 0.15f
    val leftEyeCenter = Offset(centerX - eyeSpacing, centerY)
    val rightEyeCenter = Offset(centerX + eyeSpacing, centerY)

    // Draw eye target circles
    val eyeRadius = size.minDimension * 0.04f
    drawCircle(
        color = guideColor,
        radius = eyeRadius,
        center = leftEyeCenter,
        style = Stroke(width = 2f, pathEffect = dashEffect),
    )
    drawCircle(
        color = guideColor,
        radius = eyeRadius,
        center = rightEyeCenter,
        style = Stroke(width = 2f, pathEffect = dashEffect),
    )

    // Draw nose tip target
    val noseTip = Offset(centerX, centerY + size.height * 0.1f)
    drawCircle(
        color = guideColor,
        radius = size.minDimension * 0.02f,
        center = noseTip,
        style = Stroke(width = 2f, pathEffect = dashEffect),
    )

    // Draw connecting line between eyes
    drawLine(
        color = guideColor,
        start = leftEyeCenter,
        end = rightEyeCenter,
        strokeWidth = 1f,
        pathEffect = dashEffect,
    )

    // Draw vertical center line from between eyes to nose
    drawLine(
        color = guideColor,
        start = Offset(centerX, centerY),
        end = noseTip,
        strokeWidth = 1f,
        pathEffect = dashEffect,
    )

    // Draw face oval guide
    val ovalWidth = size.width * 0.5f
    val ovalHeight = size.height * 0.45f
    drawOval(
        color = guideColor,
        topLeft = Offset(centerX - ovalWidth / 2, centerY - ovalHeight * 0.35f),
        size = androidx.compose.ui.geometry.Size(ovalWidth, ovalHeight),
        style = Stroke(width = 2f, pathEffect = dashEffect),
    )
}

/**
 * Draws the current face position indicators.
 */
private fun DrawScope.drawCurrentFacePosition(landmarks: FaceLandmarks, alpha: Float) {
    val currentColor = Color(0xFF4CAF50).copy(alpha = alpha) // Green for current

    // Draw current eye positions
    val leftEyeCenter = Offset(
        x = landmarks.leftEyeCenter.x * size.width,
        y = landmarks.leftEyeCenter.y * size.height,
    )
    val rightEyeCenter = Offset(
        x = landmarks.rightEyeCenter.x * size.width,
        y = landmarks.rightEyeCenter.y * size.height,
    )

    // Draw eye circles (solid)
    val eyeRadius = size.minDimension * 0.03f
    drawCircle(
        color = currentColor,
        radius = eyeRadius,
        center = leftEyeCenter,
    )
    drawCircle(
        color = currentColor,
        radius = eyeRadius,
        center = rightEyeCenter,
    )

    // Draw nose tip
    val noseTip = Offset(
        x = landmarks.noseTip.x * size.width,
        y = landmarks.noseTip.y * size.height,
    )
    drawCircle(
        color = currentColor,
        radius = size.minDimension * 0.015f,
        center = noseTip,
    )

    // Draw connecting line between eyes
    drawLine(
        color = currentColor.copy(alpha = alpha * 0.7f),
        start = leftEyeCenter,
        end = rightEyeCenter,
        strokeWidth = 2f,
        cap = StrokeCap.Round,
    )

    // Draw bounding box
    val bbox = landmarks.boundingBox
    val topLeft = Offset(bbox.left * size.width, bbox.top * size.height)
    val boxSize = androidx.compose.ui.geometry.Size(
        bbox.width * size.width,
        bbox.height * size.height,
    )

    // Draw corner markers instead of full rectangle
    val cornerLength = minOf(boxSize.width, boxSize.height) * 0.15f
    val cornerColor = currentColor.copy(alpha = alpha * 0.8f)

    // Top-left corner
    drawLine(cornerColor, topLeft, Offset(topLeft.x + cornerLength, topLeft.y), strokeWidth = 3f)
    drawLine(cornerColor, topLeft, Offset(topLeft.x, topLeft.y + cornerLength), strokeWidth = 3f)

    // Top-right corner
    val topRight = Offset(topLeft.x + boxSize.width, topLeft.y)
    drawLine(cornerColor, topRight, Offset(topRight.x - cornerLength, topRight.y), strokeWidth = 3f)
    drawLine(cornerColor, topRight, Offset(topRight.x, topRight.y + cornerLength), strokeWidth = 3f)

    // Bottom-left corner
    val bottomLeft = Offset(topLeft.x, topLeft.y + boxSize.height)
    drawLine(cornerColor, bottomLeft, Offset(bottomLeft.x + cornerLength, bottomLeft.y), strokeWidth = 3f)
    drawLine(cornerColor, bottomLeft, Offset(bottomLeft.x, bottomLeft.y - cornerLength), strokeWidth = 3f)

    // Bottom-right corner
    val bottomRight = Offset(topLeft.x + boxSize.width, topLeft.y + boxSize.height)
    drawLine(cornerColor, bottomRight, Offset(bottomRight.x - cornerLength, bottomRight.y), strokeWidth = 3f)
    drawLine(cornerColor, bottomRight, Offset(bottomRight.x, bottomRight.y - cornerLength), strokeWidth = 3f)
}

/**
 * Draws lines connecting current face position to target position.
 */
private fun DrawScope.drawAlignmentLines(current: FaceLandmarks, target: FaceLandmarks, alpha: Float) {
    val lineColor = Color(0xFFFFEB3B).copy(alpha = alpha * 0.6f) // Yellow alignment lines

    // Left eye alignment
    val currentLeftEye = Offset(
        x = current.leftEyeCenter.x * size.width,
        y = current.leftEyeCenter.y * size.height,
    )
    val targetLeftEye = Offset(
        x = target.leftEyeCenter.x * size.width,
        y = target.leftEyeCenter.y * size.height,
    )

    // Right eye alignment
    val currentRightEye = Offset(
        x = current.rightEyeCenter.x * size.width,
        y = current.rightEyeCenter.y * size.height,
    )
    val targetRightEye = Offset(
        x = target.rightEyeCenter.x * size.width,
        y = target.rightEyeCenter.y * size.height,
    )

    // Only draw lines if there's significant offset
    val threshold = size.minDimension * 0.01f

    if (distanceBetween(currentLeftEye, targetLeftEye) > threshold) {
        drawLine(
            color = lineColor,
            start = currentLeftEye,
            end = targetLeftEye,
            strokeWidth = 2f,
            cap = StrokeCap.Round,
        )
    }

    if (distanceBetween(currentRightEye, targetRightEye) > threshold) {
        drawLine(
            color = lineColor,
            start = currentRightEye,
            end = targetRightEye,
            strokeWidth = 2f,
            cap = StrokeCap.Round,
        )
    }
}

/**
 * Calculates the distance between two points.
 */
private fun distanceBetween(a: Offset, b: Offset): Float {
    val dx = a.x - b.x
    val dy = a.y - b.y
    return kotlin.math.sqrt(dx * dx + dy * dy)
}
