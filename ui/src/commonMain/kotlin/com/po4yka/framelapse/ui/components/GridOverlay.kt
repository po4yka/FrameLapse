package com.po4yka.framelapse.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap

/**
 * Grid overlay component for camera preview.
 * Displays rule-of-thirds grid lines to help with composition and alignment.
 *
 * @param showGrid Whether to show the grid overlay
 * @param modifier Modifier for the overlay
 * @param gridColor Color of the grid lines
 * @param lineWidth Width of the grid lines in pixels
 * @param divisions Number of divisions for the grid (default 3 for rule of thirds)
 */
@Composable
fun GridOverlay(
    showGrid: Boolean,
    modifier: Modifier = Modifier,
    gridColor: Color = Color.White.copy(alpha = 0.5f),
    lineWidth: Float = 1f,
    divisions: Int = 3,
) {
    if (!showGrid) {
        return
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height

        // Draw vertical lines
        for (i in 1 until divisions) {
            val x = width * i / divisions
            drawLine(
                color = gridColor,
                start = Offset(x, 0f),
                end = Offset(x, height),
                strokeWidth = lineWidth,
                cap = StrokeCap.Round,
            )
        }

        // Draw horizontal lines
        for (i in 1 until divisions) {
            val y = height * i / divisions
            drawLine(
                color = gridColor,
                start = Offset(0f, y),
                end = Offset(width, y),
                strokeWidth = lineWidth,
                cap = StrokeCap.Round,
            )
        }

        // Optional: Draw center crosshair for face alignment
        val centerX = width / 2
        val centerY = height / 2
        val crosshairSize = minOf(width, height) * 0.05f

        // Vertical center line (short)
        drawLine(
            color = gridColor.copy(alpha = gridColor.alpha * 0.7f),
            start = Offset(centerX, centerY - crosshairSize),
            end = Offset(centerX, centerY + crosshairSize),
            strokeWidth = lineWidth * 1.5f,
            cap = StrokeCap.Round,
        )

        // Horizontal center line (short)
        drawLine(
            color = gridColor.copy(alpha = gridColor.alpha * 0.7f),
            start = Offset(centerX - crosshairSize, centerY),
            end = Offset(centerX + crosshairSize, centerY),
            strokeWidth = lineWidth * 1.5f,
            cap = StrokeCap.Round,
        )
    }
}
