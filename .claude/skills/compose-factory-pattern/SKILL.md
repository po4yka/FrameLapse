---
name: compose-factory-pattern
description: Implements factory pattern using CompositionLocal for component creation and theming. Use when creating families of related UI components or switching between component variants.
---

# Compose Factory Pattern

## Overview

The Factory pattern in Compose creates UI components dynamically based on configuration. It uses `CompositionLocalProvider` for dependency injection and allows swapping component families without changing consuming code. This is particularly useful for theming, A/B testing, and component variants.

## When to Use

- Creating themed component families (light/dark, branded variants)
- Switching between component implementations (simple/rich cards)
- A/B testing different UI variants
- Platform-specific component variations

## 1. Basic Factory Pattern

### Factory Interface

```kotlin
// ui/factory/CardFactory.kt

interface CardFactory {
    @Composable
    fun CreateCard(
        title: String,
        content: String,
        imageUrl: String? = null,
        modifier: Modifier = Modifier,
    )
}

// CompositionLocal for factory injection
val LocalCardFactory = staticCompositionLocalOf<CardFactory> {
    error("No CardFactory provided")
}
```

### Concrete Factories

```kotlin
// ui/factory/SimpleCardFactory.kt

class SimpleCardFactory : CardFactory {
    @Composable
    override fun CreateCard(
        title: String,
        content: String,
        imageUrl: String?,
        modifier: Modifier,
    ) {
        Card(modifier = modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = content,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

// ui/factory/RichCardFactory.kt

class RichCardFactory : CardFactory {
    @Composable
    override fun CreateCard(
        title: String,
        content: String,
        imageUrl: String?,
        modifier: Modifier,
    ) {
        Card(modifier = modifier.fillMaxWidth()) {
            Column {
                // Image header
                if (imageUrl != null) {
                    AsyncImage(
                        model = imageUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        contentScale = ContentScale.Crop,
                    )
                }

                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = content,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
```

### Factory Provider

```kotlin
// ui/factory/CardFactoryProvider.kt

enum class CardType { SIMPLE, RICH }

@Composable
fun CardFactoryProvider(
    cardType: CardType = CardType.SIMPLE,
    content: @Composable () -> Unit,
) {
    val factory = remember(cardType) {
        when (cardType) {
            CardType.SIMPLE -> SimpleCardFactory()
            CardType.RICH -> RichCardFactory()
        }
    }

    CompositionLocalProvider(LocalCardFactory provides factory) {
        content()
    }
}

// Usage
@Composable
fun ProjectListScreen(cardType: CardType) {
    CardFactoryProvider(cardType = cardType) {
        ProjectList()
    }
}

@Composable
private fun ProjectList() {
    val cardFactory = LocalCardFactory.current

    LazyColumn {
        items(projects) { project ->
            cardFactory.CreateCard(
                title = project.name,
                content = "${project.frameCount} frames",
                imageUrl = project.thumbnailPath,
            )
        }
    }
}
```

## 2. Abstract Factory for Themed Components

### Theme Component Factory

```kotlin
// ui/factory/ThemeComponentFactory.kt

// Product interfaces
interface ThemeButton {
    @Composable
    fun Render(
        text: String,
        onClick: () -> Unit,
        modifier: Modifier = Modifier,
    )
}

interface ThemeCard {
    @Composable
    fun Render(
        content: @Composable () -> Unit,
        modifier: Modifier = Modifier,
    )
}

// Abstract factory
interface ThemeComponentFactory {
    fun createButton(): ThemeButton
    fun createCard(): ThemeCard
}

val LocalThemeComponentFactory = staticCompositionLocalOf<ThemeComponentFactory> {
    error("No ThemeComponentFactory provided")
}
```

### Light Theme Factory

```kotlin
// ui/factory/LightThemeFactory.kt

class LightThemeFactory : ThemeComponentFactory {
    override fun createButton() = LightThemeButton()
    override fun createCard() = LightThemeCard()
}

class LightThemeButton : ThemeButton {
    @Composable
    override fun Render(text: String, onClick: () -> Unit, modifier: Modifier) {
        Button(
            onClick = onClick,
            modifier = modifier,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF2196F3),
                contentColor = Color.White,
            ),
        ) {
            Text(text)
        }
    }
}

class LightThemeCard : ThemeCard {
    @Composable
    override fun Render(content: @Composable () -> Unit, modifier: Modifier) {
        Card(
            modifier = modifier,
            colors = CardDefaults.cardColors(
                containerColor = Color.White,
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        ) {
            content()
        }
    }
}
```

### Dark Theme Factory

```kotlin
// ui/factory/DarkThemeFactory.kt

class DarkThemeFactory : ThemeComponentFactory {
    override fun createButton() = DarkThemeButton()
    override fun createCard() = DarkThemeCard()
}

class DarkThemeButton : ThemeButton {
    @Composable
    override fun Render(text: String, onClick: () -> Unit, modifier: Modifier) {
        Button(
            onClick = onClick,
            modifier = modifier,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF1565C0),
                contentColor = Color.White,
            ),
        ) {
            Text(text)
        }
    }
}

class DarkThemeCard : ThemeCard {
    @Composable
    override fun Render(content: @Composable () -> Unit, modifier: Modifier) {
        Card(
            modifier = modifier,
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF1E1E1E),
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            border = BorderStroke(1.dp, Color(0xFF333333)),
        ) {
            content()
        }
    }
}
```

### Theme Factory Provider

```kotlin
// ui/factory/ThemeFactoryProvider.kt

@Composable
fun ThemeFactoryProvider(
    isDarkMode: Boolean,
    content: @Composable () -> Unit,
) {
    val factory = remember(isDarkMode) {
        if (isDarkMode) DarkThemeFactory() else LightThemeFactory()
    }

    CompositionLocalProvider(LocalThemeComponentFactory provides factory) {
        content()
    }
}

// Usage
@Composable
fun ThemedScreen(isDarkMode: Boolean) {
    ThemeFactoryProvider(isDarkMode = isDarkMode) {
        val factory = LocalThemeComponentFactory.current
        val button = remember { factory.createButton() }
        val card = remember { factory.createCard() }

        Column {
            card.Render {
                Text("Themed content")
            }

            button.Render(
                text = "Click me",
                onClick = { /* ... */ },
            )
        }
    }
}
```

## 3. Frame Card Factory for FrameLapse

### Frame Display Variants

```kotlin
// ui/factory/FrameCardFactory.kt

interface FrameCardFactory {
    @Composable
    fun CreateFrameCard(
        frame: Frame,
        onClick: () -> Unit,
        onLongClick: () -> Unit,
        modifier: Modifier = Modifier,
    )
}

val LocalFrameCardFactory = staticCompositionLocalOf<FrameCardFactory> {
    error("No FrameCardFactory provided")
}

// Thumbnail view (gallery grid)
class ThumbnailFrameCardFactory : FrameCardFactory {
    @Composable
    override fun CreateFrameCard(
        frame: Frame,
        onClick: () -> Unit,
        onLongClick: () -> Unit,
        modifier: Modifier,
    ) {
        Box(
            modifier = modifier
                .aspectRatio(1f)
                .clip(RoundedCornerShape(4.dp))
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick,
                )
        ) {
            AsyncImage(
                model = frame.originalPath,
                contentDescription = "Frame ${frame.sortOrder}",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )

            // Frame number badge
            Text(
                text = "${frame.sortOrder + 1}",
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                color = Color.White,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

// Detailed view (frame inspection)
class DetailedFrameCardFactory : FrameCardFactory {
    @Composable
    override fun CreateFrameCard(
        frame: Frame,
        onClick: () -> Unit,
        onLongClick: () -> Unit,
        modifier: Modifier,
    ) {
        Card(
            modifier = modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick,
                ),
        ) {
            Column {
                AsyncImage(
                    model = frame.alignedPath ?: frame.originalPath,
                    contentDescription = "Frame ${frame.sortOrder}",
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f),
                    contentScale = ContentScale.Fit,
                )

                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "Frame ${frame.sortOrder + 1}",
                        style = MaterialTheme.typography.titleMedium,
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = formatTimestamp(frame.timestamp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    if (frame.confidence != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Alignment: ${(frame.confidence * 100).toInt()}%",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}
```

### Frame Card Provider

```kotlin
// ui/factory/FrameCardProvider.kt

enum class FrameDisplayMode { THUMBNAIL, DETAILED }

@Composable
fun FrameCardProvider(
    displayMode: FrameDisplayMode = FrameDisplayMode.THUMBNAIL,
    content: @Composable () -> Unit,
) {
    val factory = remember(displayMode) {
        when (displayMode) {
            FrameDisplayMode.THUMBNAIL -> ThumbnailFrameCardFactory()
            FrameDisplayMode.DETAILED -> DetailedFrameCardFactory()
        }
    }

    CompositionLocalProvider(LocalFrameCardFactory provides factory) {
        content()
    }
}

// Usage in Gallery
@Composable
fun GalleryScreen(
    state: GalleryState,
    onEvent: (GalleryEvent) -> Unit,
) {
    var displayMode by remember { mutableStateOf(FrameDisplayMode.THUMBNAIL) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.projectName) },
                actions = {
                    IconButton(onClick = {
                        displayMode = if (displayMode == FrameDisplayMode.THUMBNAIL)
                            FrameDisplayMode.DETAILED
                        else
                            FrameDisplayMode.THUMBNAIL
                    }) {
                        Icon(
                            imageVector = if (displayMode == FrameDisplayMode.THUMBNAIL)
                                Icons.Default.ViewList
                            else
                                Icons.Default.GridView,
                            contentDescription = "Toggle view",
                        )
                    }
                }
            )
        }
    ) { padding ->
        FrameCardProvider(displayMode = displayMode) {
            FrameGrid(
                frames = state.frames,
                modifier = Modifier.padding(padding),
            )
        }
    }
}
```

## 4. Platform-Specific Factory

### Platform Component Factory

```kotlin
// ui/factory/PlatformComponentFactory.kt

interface PlatformComponentFactory {
    @Composable
    fun CreateShareButton(
        onShare: () -> Unit,
        modifier: Modifier = Modifier,
    )

    @Composable
    fun CreateFilePicker(
        onFilePicked: (String) -> Unit,
        modifier: Modifier = Modifier,
    )
}

// Common
expect fun createPlatformComponentFactory(): PlatformComponentFactory

// Android implementation
actual fun createPlatformComponentFactory(): PlatformComponentFactory =
    AndroidComponentFactory()

class AndroidComponentFactory : PlatformComponentFactory {
    @Composable
    override fun CreateShareButton(onShare: () -> Unit, modifier: Modifier) {
        val context = LocalContext.current

        IconButton(
            onClick = {
                // Android-specific share intent
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "video/*"
                }
                context.startActivity(Intent.createChooser(intent, "Share"))
                onShare()
            },
            modifier = modifier,
        ) {
            Icon(Icons.Default.Share, contentDescription = "Share")
        }
    }

    // ...
}

// iOS implementation
actual fun createPlatformComponentFactory(): PlatformComponentFactory =
    IOSComponentFactory()

// Desktop implementation
actual fun createPlatformComponentFactory(): PlatformComponentFactory =
    DesktopComponentFactory()
```

## Anti-Patterns

### Avoid: Factory with Too Many Parameters

```kotlin
// BAD - factory method has too many parameters
interface BadCardFactory {
    @Composable
    fun CreateCard(
        title: String,
        subtitle: String,
        content: String,
        imageUrl: String?,
        backgroundColor: Color,
        textColor: Color,
        elevation: Dp,
        cornerRadius: Dp,
        onClick: () -> Unit,
        onLongClick: () -> Unit,
        // ... more parameters
    )
}

// BETTER - use data class for configuration
data class CardConfig(
    val title: String,
    val subtitle: String? = null,
    val content: String,
    val imageUrl: String? = null,
)

interface GoodCardFactory {
    @Composable
    fun CreateCard(
        config: CardConfig,
        onClick: () -> Unit = {},
        modifier: Modifier = Modifier,
    )
}
```

### Avoid: Direct Factory Instantiation in Composables

```kotlin
// BAD - creates new factory on every recomposition
@Composable
fun BadUsage() {
    val factory = SimpleCardFactory()  // Created every recomposition!
    factory.CreateCard(...)
}

// BETTER - use CompositionLocal or remember
@Composable
fun GoodUsage() {
    val factory = LocalCardFactory.current  // Provided from above
    factory.CreateCard(...)
}
```

## Reference Examples

- Theme setup: `composeApp/src/commonMain/kotlin/com/po4yka/framelapse/ui/theme/`
- Composables: `composeApp/src/commonMain/kotlin/com/po4yka/framelapse/ui/components/`

## Checklist

### Factory Interface
- [ ] Interface defines `@Composable` methods
- [ ] Methods have sensible defaults
- [ ] CompositionLocal created with `staticCompositionLocalOf`
- [ ] Error message for missing provider

### Concrete Factories
- [ ] Each variant implements full interface
- [ ] No shared mutable state between instances
- [ ] Remember used for expensive object creation

### Provider
- [ ] Remembers factory based on configuration key
- [ ] Uses CompositionLocalProvider
- [ ] Configuration changes trigger new factory

### Usage
- [ ] Access via LocalXxxFactory.current
- [ ] No direct factory instantiation in composables
- [ ] Factory products cached with remember when appropriate
