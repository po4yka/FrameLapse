package com.po4yka.framelapse.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.po4yka.framelapse.ui.util.ImageLoadResult
import com.po4yka.framelapse.ui.util.rememberImageFromPath
import androidx.compose.foundation.Image as ComposeImage

/**
 * Reusable thumbnail component for showing image previews.
 *
 * Displays:
 * - Loading spinner while loading
 * - Image on success
 * - Broken image icon on error or null path
 *
 * @param imagePath Path to the image file, or null to show placeholder
 * @param contentDescription Accessibility description
 * @param modifier Additional modifier
 * @param size Size of the thumbnail
 * @param cornerRadius Corner radius for rounded corners
 */
@Composable
fun ImportPreviewThumbnail(
    imagePath: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = 80.dp,
    cornerRadius: Dp = 8.dp,
) {
    val imageResult = rememberImageFromPath(imagePath)

    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(cornerRadius))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        when (imageResult) {
            is ImageLoadResult.Loading -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(size / 3),
                    strokeWidth = 2.dp,
                )
            }
            is ImageLoadResult.Success -> {
                ComposeImage(
                    bitmap = imageResult.image,
                    contentDescription = contentDescription,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
            is ImageLoadResult.Error -> {
                Icon(
                    imageVector = Icons.Default.BrokenImage,
                    contentDescription = contentDescription,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(size / 3),
                )
            }
        }
    }
}
