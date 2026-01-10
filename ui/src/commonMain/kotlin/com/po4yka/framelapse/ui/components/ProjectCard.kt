package com.po4yka.framelapse.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.po4yka.framelapse.domain.entity.Project
import com.po4yka.framelapse.ui.util.ImageLoadResult
import com.po4yka.framelapse.ui.util.rememberImageFromPath
import framelapse.ui.generated.resources.Res
import framelapse.ui.generated.resources.cd_failed_thumbnail
import framelapse.ui.generated.resources.cd_no_thumbnail
import framelapse.ui.generated.resources.cd_project_thumbnail
import framelapse.ui.generated.resources.fps_label
import framelapse.ui.generated.resources.frame_count
import org.jetbrains.compose.resources.stringResource

private val CARD_CORNER_RADIUS = 12.dp
private val CONTENT_PADDING = 12.dp
private val THUMBNAIL_ASPECT_RATIO = 16f / 9f
private val ICON_SIZE = 48.dp
private const val THUMBNAIL_HEIGHT_FRACTION = 0.6f

/**
 * Project card with thumbnail, name, and frame count.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ProjectCard(
    project: Project,
    frameCount: Int,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    thumbnailPath: String? = null,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        shape = RoundedCornerShape(CARD_CORNER_RADIUS),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column {
            // Thumbnail area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(THUMBNAIL_ASPECT_RATIO)
                    .clip(RoundedCornerShape(topStart = CARD_CORNER_RADIUS, topEnd = CARD_CORNER_RADIUS))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                if (thumbnailPath != null) {
                    val imageResult = rememberImageFromPath(thumbnailPath)
                    when (imageResult) {
                        is ImageLoadResult.Success -> {
                            Image(
                                bitmap = imageResult.image,
                                contentDescription = stringResource(Res.string.cd_project_thumbnail),
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
                                imageVector = Icons.Default.CameraAlt,
                                contentDescription = stringResource(Res.string.cd_failed_thumbnail),
                                modifier = Modifier.size(ICON_SIZE),
                                tint = MaterialTheme.colorScheme.outline,
                            )
                        }
                    }
                } else {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = stringResource(Res.string.cd_no_thumbnail),
                        modifier = Modifier.size(ICON_SIZE),
                        tint = MaterialTheme.colorScheme.outline,
                    )
                }
            }

            // Content area
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(CONTENT_PADDING),
            ) {
                Text(
                    text = project.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(Res.string.frame_count, frameCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Text(
                        text = stringResource(Res.string.fps_label, project.fps),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
