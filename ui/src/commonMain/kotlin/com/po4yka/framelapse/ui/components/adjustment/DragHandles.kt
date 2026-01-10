package com.po4yka.framelapse.ui.components.adjustment

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.po4yka.framelapse.domain.entity.AdjustmentPointType
import com.po4yka.framelapse.domain.entity.BodyManualAdjustment
import com.po4yka.framelapse.domain.entity.FaceManualAdjustment
import com.po4yka.framelapse.domain.entity.LandscapeManualAdjustment
import com.po4yka.framelapse.domain.entity.MuscleManualAdjustment
import kotlin.math.roundToInt

/**
 * A draggable handle for adjusting landmark positions.
 *
 * @param position The position in pixels within the parent container
 * @param label Short label for the handle (e.g., "L", "R", "1")
 * @param color The color of the handle
 * @param isActive Whether this handle is currently being dragged
 * @param onDragStart Called when drag starts
 * @param onDrag Called during drag with delta offset in pixels
 * @param onDragEnd Called when drag ends
 * @param size The size of the handle
 * @param modifier Additional modifier
 */
@Composable
fun DragHandle(
    position: Offset,
    label: String,
    color: Color,
    isActive: Boolean,
    onDragStart: () -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    size: Dp = 32.dp,
    modifier: Modifier = Modifier,
) {
    val scale by animateFloatAsState(
        targetValue = if (isActive) 1.3f else 1f,
        animationSpec = spring(stiffness = 300f),
        label = "handle_scale",
    )

    val density = LocalDensity.current
    val sizePx = with(density) { size.toPx() }

    Box(
        modifier = modifier
            .offset {
                IntOffset(
                    (position.x - sizePx / 2).roundToInt(),
                    (position.y - sizePx / 2).roundToInt(),
                )
            }
            .size(size)
            .scale(scale)
            .shadow(
                elevation = if (isActive) 8.dp else 4.dp,
                shape = CircleShape,
            )
            .clip(CircleShape)
            .background(color)
            .border(
                width = 2.dp,
                color = Color.White,
                shape = CircleShape,
            )
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { onDragStart() },
                    onDragEnd = { onDragEnd() },
                    onDragCancel = { onDragEnd() },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        onDrag(dragAmount)
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

/**
 * Drag handles for face alignment (left eye, right eye).
 *
 * @param adjustment The current face adjustment
 * @param imageWidth Image width in pixels
 * @param imageHeight Image height in pixels
 * @param activeDragPoint Currently dragged point type (null if none)
 * @param onDragStart Called when drag starts with the point type
 * @param onDrag Called during drag with delta in normalized coordinates
 * @param onDragEnd Called when drag ends
 */
@Composable
fun FaceDragHandles(
    adjustment: FaceManualAdjustment,
    imageWidth: Float,
    imageHeight: Float,
    activeDragPoint: AdjustmentPointType?,
    onDragStart: (AdjustmentPointType) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val leftEyeColor = MaterialTheme.colorScheme.primary
    val rightEyeColor = MaterialTheme.colorScheme.secondary

    Box(modifier = modifier) {
        // Left eye handle
        DragHandle(
            position = Offset(
                adjustment.leftEyeCenter.x * imageWidth,
                adjustment.leftEyeCenter.y * imageHeight,
            ),
            label = "L",
            color = leftEyeColor,
            isActive = activeDragPoint == AdjustmentPointType.LEFT_EYE,
            onDragStart = { onDragStart(AdjustmentPointType.LEFT_EYE) },
            onDrag = { delta ->
                onDrag(Offset(delta.x / imageWidth, delta.y / imageHeight))
            },
            onDragEnd = onDragEnd,
        )

        // Right eye handle
        DragHandle(
            position = Offset(
                adjustment.rightEyeCenter.x * imageWidth,
                adjustment.rightEyeCenter.y * imageHeight,
            ),
            label = "R",
            color = rightEyeColor,
            isActive = activeDragPoint == AdjustmentPointType.RIGHT_EYE,
            onDragStart = { onDragStart(AdjustmentPointType.RIGHT_EYE) },
            onDrag = { delta ->
                onDrag(Offset(delta.x / imageWidth, delta.y / imageHeight))
            },
            onDragEnd = onDragEnd,
        )
    }
}

/**
 * Drag handles for body alignment (shoulders and hips).
 */
@Composable
fun BodyDragHandles(
    adjustment: BodyManualAdjustment,
    imageWidth: Float,
    imageHeight: Float,
    activeDragPoint: AdjustmentPointType?,
    onDragStart: (AdjustmentPointType) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shoulderColor = MaterialTheme.colorScheme.primary
    val hipColor = MaterialTheme.colorScheme.tertiary

    Box(modifier = modifier) {
        // Left shoulder
        DragHandle(
            position = Offset(
                adjustment.leftShoulder.x * imageWidth,
                adjustment.leftShoulder.y * imageHeight,
            ),
            label = "LS",
            color = shoulderColor,
            isActive = activeDragPoint == AdjustmentPointType.LEFT_SHOULDER,
            onDragStart = { onDragStart(AdjustmentPointType.LEFT_SHOULDER) },
            onDrag = { delta ->
                onDrag(Offset(delta.x / imageWidth, delta.y / imageHeight))
            },
            onDragEnd = onDragEnd,
            size = 36.dp,
        )

        // Right shoulder
        DragHandle(
            position = Offset(
                adjustment.rightShoulder.x * imageWidth,
                adjustment.rightShoulder.y * imageHeight,
            ),
            label = "RS",
            color = shoulderColor,
            isActive = activeDragPoint == AdjustmentPointType.RIGHT_SHOULDER,
            onDragStart = { onDragStart(AdjustmentPointType.RIGHT_SHOULDER) },
            onDrag = { delta ->
                onDrag(Offset(delta.x / imageWidth, delta.y / imageHeight))
            },
            onDragEnd = onDragEnd,
            size = 36.dp,
        )

        // Left hip
        DragHandle(
            position = Offset(
                adjustment.leftHip.x * imageWidth,
                adjustment.leftHip.y * imageHeight,
            ),
            label = "LH",
            color = hipColor,
            isActive = activeDragPoint == AdjustmentPointType.LEFT_HIP,
            onDragStart = { onDragStart(AdjustmentPointType.LEFT_HIP) },
            onDrag = { delta ->
                onDrag(Offset(delta.x / imageWidth, delta.y / imageHeight))
            },
            onDragEnd = onDragEnd,
            size = 36.dp,
        )

        // Right hip
        DragHandle(
            position = Offset(
                adjustment.rightHip.x * imageWidth,
                adjustment.rightHip.y * imageHeight,
            ),
            label = "RH",
            color = hipColor,
            isActive = activeDragPoint == AdjustmentPointType.RIGHT_HIP,
            onDragStart = { onDragStart(AdjustmentPointType.RIGHT_HIP) },
            onDrag = { delta ->
                onDrag(Offset(delta.x / imageWidth, delta.y / imageHeight))
            },
            onDragEnd = onDragEnd,
            size = 36.dp,
        )
    }
}

/**
 * Drag handles for muscle mode (same as body, used for region cropping).
 */
@Composable
fun MuscleDragHandles(
    adjustment: MuscleManualAdjustment,
    imageWidth: Float,
    imageHeight: Float,
    activeDragPoint: AdjustmentPointType?,
    onDragStart: (AdjustmentPointType) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BodyDragHandles(
        adjustment = adjustment.bodyAdjustment,
        imageWidth = imageWidth,
        imageHeight = imageHeight,
        activeDragPoint = activeDragPoint,
        onDragStart = onDragStart,
        onDrag = onDrag,
        onDragEnd = onDragEnd,
        modifier = modifier,
    )
}

/**
 * Drag handles for landscape mode (4 corner points).
 */
@Composable
fun LandscapeDragHandles(
    adjustment: LandscapeManualAdjustment,
    imageWidth: Float,
    imageHeight: Float,
    activeDragPoint: AdjustmentPointType?,
    onDragStart: (AdjustmentPointType) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cornerColor = MaterialTheme.colorScheme.primary

    val corners = listOf(
        Triple(
            adjustment.cornerKeypoints.getOrNull(0),
            AdjustmentPointType.CORNER_TOP_LEFT,
            "TL",
        ),
        Triple(
            adjustment.cornerKeypoints.getOrNull(1),
            AdjustmentPointType.CORNER_TOP_RIGHT,
            "TR",
        ),
        Triple(
            adjustment.cornerKeypoints.getOrNull(2),
            AdjustmentPointType.CORNER_BOTTOM_LEFT,
            "BL",
        ),
        Triple(
            adjustment.cornerKeypoints.getOrNull(3),
            AdjustmentPointType.CORNER_BOTTOM_RIGHT,
            "BR",
        ),
    )

    Box(modifier = modifier) {
        corners.forEach { (point, pointType, label) ->
            if (point != null) {
                DragHandle(
                    position = Offset(
                        point.x * imageWidth,
                        point.y * imageHeight,
                    ),
                    label = label,
                    color = cornerColor,
                    isActive = activeDragPoint == pointType,
                    onDragStart = { onDragStart(pointType) },
                    onDrag = { delta ->
                        onDrag(Offset(delta.x / imageWidth, delta.y / imageHeight))
                    },
                    onDragEnd = onDragEnd,
                    size = 28.dp,
                )
            }
        }
    }
}

/**
 * Preview of a drag handle (for design/documentation purposes).
 */
@Composable
fun DragHandlePreview(label: String = "L", isActive: Boolean = false) {
    var position by remember { mutableStateOf(Offset(50f, 50f)) }

    Box(modifier = Modifier.size(100.dp)) {
        DragHandle(
            position = position,
            label = label,
            color = Color(0xFF2196F3),
            isActive = isActive,
            onDragStart = {},
            onDrag = { delta ->
                position = Offset(position.x + delta.x, position.y + delta.y)
            },
            onDragEnd = {},
        )
    }
}
