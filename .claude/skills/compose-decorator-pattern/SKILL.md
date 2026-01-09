---
name: compose-decorator-pattern
description: Implements decorator pattern using higher-order composables for UI styling and behavior composition. Use when adding optional visual or behavioral enhancements to components.
---

# Compose Decorator Pattern

## Overview

The Decorator pattern in Compose uses higher-order composables (HOCs) to wrap content with additional styling or behavior. Unlike traditional OOP decorators using inheritance, Compose decorators use functional composition - each decorator takes `content: @Composable () -> Unit` and wraps it.

## When to Use

- Adding optional visual styling (shadows, borders, padding)
- Wrapping components with consistent behavior (loading states, error boundaries)
- Creating reusable styling primitives that can be combined
- Avoiding style prop explosion in component APIs

## 1. Basic Decorator Pattern

### Simple Style Decorator

```kotlin
// ui/components/decorators/StyleDecorators.kt

@Composable
fun ShadowDecorator(
    elevation: Dp = 4.dp,
    shape: Shape = RoundedCornerShape(8.dp),
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier.shadow(elevation, shape)
    ) {
        content()
    }
}

@Composable
fun BorderDecorator(
    borderWidth: Dp = 1.dp,
    borderColor: Color = MaterialTheme.colorScheme.outline,
    shape: Shape = RoundedCornerShape(8.dp),
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier.border(borderWidth, borderColor, shape)
    ) {
        content()
    }
}

@Composable
fun PaddingDecorator(
    padding: Dp = 16.dp,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(modifier = modifier.padding(padding)) {
        content()
    }
}
```

### Composing Decorators

```kotlin
// Usage - decorators nest naturally
@Composable
fun DecoratedFrameCard(frame: Frame) {
    ShadowDecorator(elevation = 4.dp) {
        BorderDecorator(borderColor = MaterialTheme.colorScheme.primary) {
            PaddingDecorator(padding = 12.dp) {
                FrameCardContent(frame = frame)
            }
        }
    }
}

// Alternative - using extension functions
@Composable
fun FrameCardContent(frame: Frame) {
    Column {
        AsyncImage(model = frame.originalPath, contentDescription = null)
        Text(text = "Frame ${frame.sortOrder}")
    }
}
```

## 2. Behavioral Decorators

### Loading State Decorator

```kotlin
// ui/components/decorators/LoadingDecorator.kt

@Composable
fun LoadingDecorator(
    isLoading: Boolean,
    loadingContent: @Composable () -> Unit = { CircularProgressIndicator() },
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(modifier = modifier) {
        content()

        if (isLoading) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Color.Black.copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center,
            ) {
                loadingContent()
            }
        }
    }
}

// Usage
@Composable
fun GalleryScreen(state: GalleryState) {
    LoadingDecorator(isLoading = state.isLoading) {
        LazyVerticalGrid(columns = GridCells.Fixed(3)) {
            items(state.frames) { frame ->
                FrameThumbnail(frame = frame)
            }
        }
    }
}
```

### Error Boundary Decorator

```kotlin
// ui/components/decorators/ErrorBoundaryDecorator.kt

@Composable
fun ErrorBoundaryDecorator(
    error: String?,
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    if (error != null) {
        ErrorContent(
            message = error,
            onRetry = onRetry,
            modifier = modifier,
        )
    } else {
        content()
    }
}

@Composable
private fun ErrorContent(
    message: String,
    onRetry: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Default.Error,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = message, color = MaterialTheme.colorScheme.error)

        if (onRetry != null) {
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onRetry) {
                Text("Retry")
            }
        }
    }
}
```

## 3. Selection State Decorator

### Selectable Item Decorator

```kotlin
// ui/components/decorators/SelectableDecorator.kt

@Composable
fun SelectableDecorator(
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onToggleSelection: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .then(
                if (isSelectionMode) {
                    Modifier.clickable(onClick = onToggleSelection)
                } else {
                    Modifier
                }
            )
    ) {
        content()

        if (isSelectionMode) {
            // Selection indicator overlay
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
            ) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onToggleSelection() },
                )
            }

            // Selection highlight
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .border(
                            width = 3.dp,
                            color = MaterialTheme.colorScheme.primary,
                            shape = RoundedCornerShape(8.dp),
                        )
                )
            }
        }
    }
}

// Usage in Gallery
@Composable
fun SelectableFrameGrid(
    frames: List<Frame>,
    selectedIds: Set<String>,
    isSelectionMode: Boolean,
    onToggleSelection: (String) -> Unit,
) {
    LazyVerticalGrid(columns = GridCells.Fixed(3)) {
        items(frames, key = { it.id }) { frame ->
            SelectableDecorator(
                isSelected = frame.id in selectedIds,
                isSelectionMode = isSelectionMode,
                onToggleSelection = { onToggleSelection(frame.id) },
            ) {
                FrameThumbnail(frame = frame)
            }
        }
    }
}
```

## 4. Ghost Overlay Decorator

### For Capture Screen Alignment

```kotlin
// ui/components/decorators/GhostOverlayDecorator.kt

@Composable
fun GhostOverlayDecorator(
    ghostImagePath: String?,
    opacity: Float = 0.5f,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(modifier = modifier) {
        // Live camera content
        content()

        // Ghost overlay from previous frame
        if (ghostImagePath != null) {
            AsyncImage(
                model = ghostImagePath,
                contentDescription = "Previous frame overlay",
                modifier = Modifier
                    .matchParentSize()
                    .alpha(opacity),
                contentScale = ContentScale.Crop,
            )
        }
    }
}

// Usage in Capture screen
@Composable
fun CaptureScreen(state: CaptureState) {
    GhostOverlayDecorator(
        ghostImagePath = state.lastCapturedFrame?.originalPath,
        opacity = state.ghostOpacity,
    ) {
        CameraPreview(
            onImageCaptured = { /* ... */ },
        )
    }
}
```

## 5. Animated Decorator

### Fade-in Content Decorator

```kotlin
// ui/components/decorators/AnimatedDecorators.kt

@Composable
fun FadeInDecorator(
    visible: Boolean,
    durationMillis: Int = 300,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis),
        label = "fade",
    )

    Box(modifier = modifier.alpha(alpha)) {
        content()
    }
}

@Composable
fun ScaleInDecorator(
    visible: Boolean,
    durationMillis: Int = 300,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val scale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.8f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow,
        ),
        label = "scale",
    )

    Box(
        modifier = modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
    ) {
        content()
    }
}
```

## 6. Combining Multiple Decorators

### Decorator Composition Helper

```kotlin
// ui/components/decorators/DecoratorComposition.kt

// Using extension approach for cleaner composition
@Composable
fun Modifier.withShadow(
    elevation: Dp = 4.dp,
    shape: Shape = RoundedCornerShape(8.dp),
) = this.shadow(elevation, shape)

@Composable
fun Modifier.withBorder(
    width: Dp = 1.dp,
    color: Color = MaterialTheme.colorScheme.outline,
    shape: Shape = RoundedCornerShape(8.dp),
) = this.border(width, color, shape)

// Combined decorator for common patterns
@Composable
fun CardDecorator(
    isElevated: Boolean = true,
    hasBorder: Boolean = false,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val decoratedModifier = modifier
        .then(if (isElevated) Modifier.shadow(4.dp, RoundedCornerShape(12.dp)) else Modifier)
        .then(if (hasBorder) Modifier.border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp)) else Modifier)
        .clip(RoundedCornerShape(12.dp))
        .background(MaterialTheme.colorScheme.surface)
        .padding(16.dp)

    Box(modifier = decoratedModifier) {
        content()
    }
}

// Usage
@Composable
fun ProjectCard(project: Project) {
    CardDecorator(isElevated = true, hasBorder = false) {
        Column {
            Text(project.name, style = MaterialTheme.typography.titleMedium)
            Text("${project.frameCount} frames", style = MaterialTheme.typography.bodySmall)
        }
    }
}
```

## Anti-Patterns

### Avoid: Deeply Nested Decorators

```kotlin
// BAD - too many levels of nesting
ShadowDecorator {
    BorderDecorator {
        PaddingDecorator {
            LoadingDecorator(isLoading) {
                ErrorBoundaryDecorator(error) {
                    SelectableDecorator(isSelected, isSelectionMode, onToggle) {
                        Content()
                    }
                }
            }
        }
    }
}

// BETTER - combine related decorators into one
@Composable
fun FrameCardDecorator(
    isLoading: Boolean,
    error: String?,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onToggleSelection: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    // Combines visual styling + state handling
    CardDecorator(modifier = modifier) {
        LoadingDecorator(isLoading = isLoading) {
            ErrorBoundaryDecorator(error = error) {
                SelectableDecorator(
                    isSelected = isSelected,
                    isSelectionMode = isSelectionMode,
                    onToggleSelection = onToggleSelection,
                ) {
                    content()
                }
            }
        }
    }
}
```

## Reference Examples

- Style composables: `composeApp/src/commonMain/kotlin/com/po4yka/framelapse/ui/components/`
- Existing modifiers: `composeApp/src/commonMain/kotlin/com/po4yka/framelapse/ui/theme/`

## Checklist

### Creating Decorators
- [ ] Takes `content: @Composable () -> Unit` as last parameter
- [ ] Uses `Box` or similar container for wrapping
- [ ] Exposes `modifier` parameter for customization
- [ ] Has sensible defaults for all parameters
- [ ] Can be composed with other decorators

### Using Decorators
- [ ] Nesting depth is reasonable (3-4 max)
- [ ] Related decorators combined into composite decorator
- [ ] Decorators don't duplicate Modifier functionality
- [ ] Performance-sensitive decorators use remember/derivedStateOf
