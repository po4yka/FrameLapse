package com.po4yka.framelapse.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.po4yka.framelapse.domain.entity.Frame
import com.po4yka.framelapse.ui.util.ImageLoadResult
import com.po4yka.framelapse.ui.util.rememberImageFromPath

private val CORNER_RADIUS = 8.dp
private val BORDER_WIDTH = 3.dp
private val CHECKBOX_SIZE = 24.dp
private val CHECKBOX_PADDING = 8.dp
private val ICON_SIZE = 32.dp

/**
 * Frame grid item with selection support.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FrameGridItem(
    frame: Frame,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(CORNER_RADIUS))
            .then(
                if (isSelected) {
                    Modifier.border(
                        width = BORDER_WIDTH,
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(CORNER_RADIUS),
                    )
                } else {
                    Modifier
                },
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            ),
    ) {
        // Frame thumbnail
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            val imagePath = frame.alignedPath ?: frame.originalPath
            val imageResult = rememberImageFromPath(imagePath)

            when (imageResult) {
                is ImageLoadResult.Success -> {
                    Image(
                        bitmap = imageResult.image,
                        contentDescription = "Frame",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                }
                is ImageLoadResult.Loading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(ICON_SIZE),
                    )
                }
                is ImageLoadResult.Error -> {
                    Icon(
                        imageVector = Icons.Default.BrokenImage,
                        contentDescription = null,
                        modifier = Modifier.size(ICON_SIZE),
                        tint = MaterialTheme.colorScheme.outline,
                    )
                }
            }
        }

        // Selection indicator
        if (isSelected) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(CHECKBOX_PADDING)
                    .size(CHECKBOX_SIZE)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(CHECKBOX_SIZE - 8.dp),
                )
            }
        }
    }
}
