---
name: compose-state-patterns
description: Implements Compose-specific state management patterns including state hoisting, remember patterns, derivedStateOf, and side effect handling. Use when managing complex UI state.
---

# Compose State Patterns

## Overview

Compose state management requires understanding recomposition, state hoisting, side effects, and proper use of state APIs. This skill covers patterns for managing state efficiently in Jetpack Compose and Compose Multiplatform.

## When to Use

- Managing UI state in composables
- Optimizing recomposition with proper state patterns
- Handling side effects (navigation, API calls)
- Implementing state hoisting for testability

## 1. State Hoisting Pattern

### Basic State Hoisting

```kotlin
// BAD - state inside composable (not testable, not reusable)
@Composable
fun BadCounter() {
    var count by remember { mutableStateOf(0) }

    Button(onClick = { count++ }) {
        Text("Count: $count")
    }
}

// GOOD - state hoisted (testable, reusable)
@Composable
fun Counter(
    count: Int,
    onIncrement: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(onClick = onIncrement, modifier = modifier) {
        Text("Count: $count")
    }
}

// Parent manages state
@Composable
fun CounterScreen() {
    var count by remember { mutableStateOf(0) }

    Counter(
        count = count,
        onIncrement = { count++ },
    )
}
```

### Hoisting Complex State

```kotlin
// State holder class
@Stable
class CaptureControlsState(
    initialFlash: FlashMode = FlashMode.OFF,
    initialGridVisible: Boolean = false,
    initialGhostOpacity: Float = 0.5f,
) {
    var flashMode by mutableStateOf(initialFlash)
        private set

    var isGridVisible by mutableStateOf(initialGridVisible)
        private set

    var ghostOpacity by mutableStateOf(initialGhostOpacity)
        private set

    fun toggleFlash() {
        flashMode = when (flashMode) {
            FlashMode.OFF -> FlashMode.ON
            FlashMode.ON -> FlashMode.AUTO
            FlashMode.AUTO -> FlashMode.OFF
        }
    }

    fun toggleGrid() {
        isGridVisible = !isGridVisible
    }

    fun setGhostOpacity(opacity: Float) {
        ghostOpacity = opacity.coerceIn(0f, 1f)
    }
}

// Remember state holder
@Composable
fun rememberCaptureControlsState(
    initialFlash: FlashMode = FlashMode.OFF,
    initialGridVisible: Boolean = false,
    initialGhostOpacity: Float = 0.5f,
): CaptureControlsState {
    return remember {
        CaptureControlsState(
            initialFlash = initialFlash,
            initialGridVisible = initialGridVisible,
            initialGhostOpacity = initialGhostOpacity,
        )
    }
}

// Usage
@Composable
fun CaptureScreen() {
    val controlsState = rememberCaptureControlsState()

    CaptureControls(state = controlsState)
}

@Composable
fun CaptureControls(state: CaptureControlsState) {
    Row {
        IconButton(onClick = { state.toggleFlash() }) {
            Icon(
                imageVector = when (state.flashMode) {
                    FlashMode.OFF -> Icons.Default.FlashOff
                    FlashMode.ON -> Icons.Default.FlashOn
                    FlashMode.AUTO -> Icons.Default.FlashAuto
                },
                contentDescription = "Flash",
            )
        }

        IconButton(onClick = { state.toggleGrid() }) {
            Icon(
                imageVector = Icons.Default.GridOn,
                contentDescription = "Grid",
                tint = if (state.isGridVisible)
                    MaterialTheme.colorScheme.primary
                else
                    LocalContentColor.current,
            )
        }

        Slider(
            value = state.ghostOpacity,
            onValueChange = { state.setGhostOpacity(it) },
            modifier = Modifier.weight(1f),
        )
    }
}
```

## 2. Remember Patterns

### Basic Remember

```kotlin
// Remember value across recompositions
@Composable
fun ExpensiveCalculation(items: List<Item>) {
    // Recalculated only when items changes
    val sortedItems = remember(items) {
        items.sortedBy { it.timestamp }
    }

    LazyColumn {
        items(sortedItems) { item -> ItemRow(item) }
    }
}
```

### Remember with Keys

```kotlin
// Multiple keys
@Composable
fun FilteredList(
    items: List<Frame>,
    filter: FrameFilter,
    sortOrder: SortOrder,
) {
    // Recalculated when any key changes
    val displayItems = remember(items, filter, sortOrder) {
        items
            .filter { filter.matches(it) }
            .sortedWith(sortOrder.comparator)
    }

    LazyColumn {
        items(displayItems, key = { it.id }) { frame ->
            FrameRow(frame)
        }
    }
}
```

### RememberSaveable for Config Changes

```kotlin
@Composable
fun SearchScreen() {
    // Survives configuration changes (rotation)
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedFilter by rememberSaveable { mutableStateOf(FilterType.ALL) }

    Column {
        SearchBar(
            query = searchQuery,
            onQueryChange = { searchQuery = it },
        )

        FilterChips(
            selected = selectedFilter,
            onSelect = { selectedFilter = it },
        )
    }
}

// Custom Saver for complex types
@Composable
fun ProjectEditorScreen() {
    var settings by rememberSaveable(stateSaver = ExportSettingsSaver) {
        mutableStateOf(ExportSettings.Default)
    }
}

val ExportSettingsSaver = Saver<ExportSettings, Map<String, Any>>(
    save = { settings ->
        mapOf(
            "resolution" to settings.resolution.name,
            "fps" to settings.fps,
            "format" to settings.format.name,
        )
    },
    restore = { map ->
        ExportSettings(
            resolution = Resolution.valueOf(map["resolution"] as String),
            fps = map["fps"] as Int,
            format = VideoFormat.valueOf(map["format"] as String),
        )
    }
)
```

## 3. DerivedStateOf Pattern

### Computed State

```kotlin
@Composable
fun GalleryScreen(frames: List<Frame>) {
    var selectedIds by remember { mutableStateOf(setOf<String>()) }

    // Derived state - only recomputes when dependencies change
    val selectionCount by remember {
        derivedStateOf { selectedIds.size }
    }

    val hasSelection by remember {
        derivedStateOf { selectedIds.isNotEmpty() }
    }

    val allSelected by remember {
        derivedStateOf { selectedIds.size == frames.size && frames.isNotEmpty() }
    }

    Scaffold(
        topBar = {
            if (hasSelection) {
                SelectionTopBar(
                    count = selectionCount,
                    allSelected = allSelected,
                    onSelectAll = {
                        selectedIds = if (allSelected) emptySet()
                        else frames.map { it.id }.toSet()
                    },
                    onDelete = { /* delete selectedIds */ },
                    onCancel = { selectedIds = emptySet() },
                )
            }
        }
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.padding(padding),
        ) {
            items(frames, key = { it.id }) { frame ->
                val isSelected by remember(selectedIds) {
                    derivedStateOf { frame.id in selectedIds }
                }

                FrameThumbnail(
                    frame = frame,
                    isSelected = isSelected,
                    onToggleSelection = {
                        selectedIds = if (isSelected) {
                            selectedIds - frame.id
                        } else {
                            selectedIds + frame.id
                        }
                    },
                )
            }
        }
    }
}
```

### Scroll-Based Derived State

```kotlin
@Composable
fun ScrollAwareHeader(content: @Composable () -> Unit) {
    val scrollState = rememberLazyListState()

    // Derived from scroll position
    val showElevation by remember {
        derivedStateOf { scrollState.firstVisibleItemScrollOffset > 0 }
    }

    val showScrollToTop by remember {
        derivedStateOf { scrollState.firstVisibleItemIndex > 3 }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Gallery") },
                // Only recomposes when showElevation changes
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = if (showElevation)
                        MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
                    else
                        MaterialTheme.colorScheme.surface,
                ),
            )
        },
        floatingActionButton = {
            AnimatedVisibility(visible = showScrollToTop) {
                FloatingActionButton(onClick = {
                    // Scroll to top
                }) {
                    Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Scroll to top")
                }
            }
        }
    ) { padding ->
        LazyColumn(
            state = scrollState,
            modifier = Modifier.padding(padding),
        ) {
            content()
        }
    }
}
```

## 4. Side Effect Patterns

### LaunchedEffect

```kotlin
@Composable
fun ProjectScreen(
    projectId: String,
    viewModel: ProjectViewModel,
) {
    // Runs when projectId changes
    LaunchedEffect(projectId) {
        viewModel.loadProject(projectId)
    }

    // Run once on first composition
    LaunchedEffect(Unit) {
        viewModel.trackScreenView()
    }
}
```

### Effect Handling with Lifecycle

```kotlin
@Composable
fun CameraScreen(
    viewModel: CaptureViewModel,
) {
    val lifecycleOwner = LocalLifecycleOwner.current

    // Handle lifecycle
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> viewModel.onResume()
                Lifecycle.Event.ON_PAUSE -> viewModel.onPause()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
}
```

### Collecting Flows

```kotlin
@Composable
fun GalleryScreen(viewModel: GalleryViewModel) {
    // Collect state with lifecycle awareness
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Handle one-time effects
    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is GalleryEffect.ShowError -> {
                    // Show snackbar
                }
                is GalleryEffect.NavigateToDetail -> {
                    // Navigate
                }
            }
        }
    }

    GalleryContent(
        state = state,
        onEvent = viewModel::onEvent,
    )
}
```

### SideEffect for Non-Compose Code

```kotlin
@Composable
fun AnalyticsTrackedScreen(
    screenName: String,
    analytics: Analytics,
) {
    // Called after every successful recomposition
    SideEffect {
        analytics.logScreenView(screenName)
    }
}
```

## 5. SnapshotFlow for State Changes

```kotlin
@Composable
fun SearchWithDebounce(onSearch: (String) -> Unit) {
    var query by remember { mutableStateOf("") }

    // Convert state changes to Flow
    LaunchedEffect(Unit) {
        snapshotFlow { query }
            .debounce(300)
            .filter { it.length >= 2 }
            .distinctUntilChanged()
            .collect { searchQuery ->
                onSearch(searchQuery)
            }
    }

    TextField(
        value = query,
        onValueChange = { query = it },
        placeholder = { Text("Search frames...") },
    )
}
```

## 6. ProduceState Pattern

```kotlin
@Composable
fun FrameWithMetadata(frame: Frame): FrameDisplayData {
    // Produce state from async source
    val metadata by produceState<ImageMetadata?>(
        initialValue = null,
        key1 = frame.originalPath,
    ) {
        value = withContext(Dispatchers.IO) {
            loadImageMetadata(frame.originalPath)
        }
    }

    return FrameDisplayData(
        frame = frame,
        metadata = metadata,
    )
}

// Alternative: from Flow
@Composable
fun ProjectWithFrameCount(project: Project): Int {
    val frameCount by produceState(
        initialValue = 0,
        key1 = project.id,
    ) {
        frameRepository.observeFrameCount(project.id)
            .collect { count -> value = count }
    }

    return frameCount
}
```

## 7. Stable Annotations

### Using @Stable and @Immutable

```kotlin
// Mark as stable for skipping recomposition
@Stable
interface FrameDisplayItem {
    val id: String
    val thumbnailPath: String
    val frameNumber: Int
}

// Data class with only val properties is automatically stable
@Immutable
data class ExportSettings(
    val resolution: Resolution,
    val fps: Int,
    val format: VideoFormat,
)

// State holder with @Stable
@Stable
class SelectionState(initialSelection: Set<String> = emptySet()) {
    var selected by mutableStateOf(initialSelection)
        private set

    fun toggle(id: String) {
        selected = if (id in selected) selected - id else selected + id
    }

    fun clear() {
        selected = emptySet()
    }
}
```

## 8. CompositionLocal Pattern

### Providing Values Down the Tree

```kotlin
// Define CompositionLocal
val LocalFrameRepository = staticCompositionLocalOf<FrameRepository> {
    error("No FrameRepository provided")
}

val LocalAnalytics = compositionLocalOf<Analytics> {
    NoOpAnalytics()  // Default implementation
}

// Provide at root
@Composable
fun App() {
    val frameRepository = remember { koinInject<FrameRepository>() }
    val analytics = remember { koinInject<Analytics>() }

    CompositionLocalProvider(
        LocalFrameRepository provides frameRepository,
        LocalAnalytics provides analytics,
    ) {
        AppContent()
    }
}

// Consume anywhere below
@Composable
fun FrameDetail(frameId: String) {
    val repository = LocalFrameRepository.current
    val analytics = LocalAnalytics.current

    LaunchedEffect(frameId) {
        analytics.logEvent("frame_viewed", mapOf("id" to frameId))
    }

    // Use repository...
}
```

## 9. Optimizing Recomposition

### Lambda Stability

```kotlin
// BAD - new lambda on every recomposition
@Composable
fun BadList(items: List<Item>, onItemClick: (String) -> Unit) {
    LazyColumn {
        items(items) { item ->
            // Creates new lambda each time
            ItemRow(
                item = item,
                onClick = { onItemClick(item.id) },  // Unstable!
            )
        }
    }
}

// GOOD - remember callback
@Composable
fun GoodList(items: List<Item>, onItemClick: (String) -> Unit) {
    LazyColumn {
        items(items, key = { it.id }) { item ->
            val onClick = remember(item.id) {
                { onItemClick(item.id) }
            }

            ItemRow(
                item = item,
                onClick = onClick,  // Stable
            )
        }
    }
}

// BETTER - use method reference when possible
@Composable
fun BetterList(
    items: List<Item>,
    viewModel: ListViewModel,
) {
    LazyColumn {
        items(items, key = { it.id }) { item ->
            ItemRow(
                item = item,
                onClick = viewModel::onItemClick,  // Method reference is stable
            )
        }
    }
}
```

### Scoping Recomposition

```kotlin
@Composable
fun FrameCard(
    frame: Frame,
    isSelected: Boolean,
    onSelect: () -> Unit,
) {
    Card(
        modifier = Modifier.clickable(onClick = onSelect),
    ) {
        // This whole block recomposes when isSelected changes

        // Extract stable parts
        FrameImage(frame = frame)  // Won't recompose for selection change

        // Only this recomposes
        SelectionOverlay(isSelected = isSelected)
    }
}

@Composable
private fun FrameImage(frame: Frame) {
    AsyncImage(
        model = frame.originalPath,
        contentDescription = null,
    )
}

@Composable
private fun SelectionOverlay(isSelected: Boolean) {
    if (isSelected) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Blue.copy(alpha = 0.3f)),
        )
    }
}
```

## Anti-Patterns

### Avoid: State in ViewModel Collected Without Lifecycle

```kotlin
// BAD - can cause leaks
@Composable
fun BadScreen(viewModel: ViewModel) {
    val state by viewModel.state.collectAsState()  // Not lifecycle-aware!
}

// GOOD - lifecycle-aware collection
@Composable
fun GoodScreen(viewModel: ViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
}
```

### Avoid: Heavy Computation in Composition

```kotlin
// BAD - runs on every recomposition
@Composable
fun BadList(frames: List<Frame>) {
    val sorted = frames.sortedBy { it.timestamp }  // Every time!
}

// GOOD - remember the result
@Composable
fun GoodList(frames: List<Frame>) {
    val sorted = remember(frames) {
        frames.sortedBy { it.timestamp }
    }
}
```

## Reference Examples

- BaseViewModel: `composeApp/src/commonMain/kotlin/com/po4yka/framelapse/presentation/base/BaseViewModel.kt`
- Contracts: `composeApp/src/commonMain/kotlin/com/po4yka/framelapse/presentation/capture/CaptureContract.kt`
- Existing skills: `.claude/skills/viewmodel-state-patterns/SKILL.md`

## Checklist

### State Hoisting
- [ ] State lifted to appropriate level
- [ ] Callbacks passed down for mutations
- [ ] State holder class for complex state
- [ ] `rememberXxxState()` pattern used

### Remember Usage
- [ ] Keys provided for reactive recomputation
- [ ] `rememberSaveable` for surviving config changes
- [ ] Custom Saver for complex types
- [ ] Avoid remembering lambdas unnecessarily

### Derived State
- [ ] `derivedStateOf` for computed values
- [ ] Avoids unnecessary recomposition
- [ ] Dependencies captured correctly

### Side Effects
- [ ] `LaunchedEffect` for coroutines
- [ ] `DisposableEffect` for cleanup
- [ ] `SideEffect` for non-Compose callbacks
- [ ] Lifecycle-aware collection for Flows
