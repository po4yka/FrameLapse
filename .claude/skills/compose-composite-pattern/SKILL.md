---
name: compose-composite-pattern
description: Implements composite pattern for tree structures and hierarchical UI. Use when building nested lists, folder structures, or recursive component hierarchies.
---

# Compose Composite Pattern

## Overview

The Composite pattern composes objects into tree structures to represent part-whole hierarchies. In Compose, this enables building nested/recursive UI components where individual elements and groups are treated uniformly. Common uses include folder browsers, nested menus, shopping carts with categories, and settings screens.

## When to Use

- Building folder/file browser UIs
- Creating nested expandable lists
- Implementing hierarchical navigation menus
- Shopping carts with product categories
- Settings screens with grouped options

## 1. Basic Composite Pattern

### Component Interface

```kotlin
// domain/entity/TreeItem.kt

interface TreeItem {
    val id: String
    val name: String

    @Composable
    fun Render(
        depth: Int = 0,
        onItemClick: (TreeItem) -> Unit,
    )

    fun getChildCount(): Int
}
```

### Leaf Component

```kotlin
// domain/entity/FileItem.kt

data class FileItem(
    override val id: String,
    override val name: String,
    val size: Long,
    val iconType: FileIconType,
) : TreeItem {

    @Composable
    override fun Render(depth: Int, onItemClick: (TreeItem) -> Unit) {
        ListItem(
            modifier = Modifier
                .padding(start = (depth * 24).dp)
                .clickable { onItemClick(this) },
            leadingContent = {
                Icon(
                    imageVector = iconType.icon,
                    contentDescription = null,
                )
            },
            headlineContent = { Text(name) },
            supportingContent = { Text(formatSize(size)) },
        )
    }

    override fun getChildCount(): Int = 0

    private fun formatSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            else -> "${bytes / (1024 * 1024)} MB"
        }
    }
}

enum class FileIconType(val icon: ImageVector) {
    IMAGE(Icons.Default.Image),
    VIDEO(Icons.Default.VideoFile),
    DOCUMENT(Icons.Default.Description),
    OTHER(Icons.Default.InsertDriveFile),
}
```

### Composite Component

```kotlin
// domain/entity/FolderItem.kt

data class FolderItem(
    override val id: String,
    override val name: String,
    val children: List<TreeItem>,
    val isExpanded: Boolean = false,
) : TreeItem {

    @Composable
    override fun Render(depth: Int, onItemClick: (TreeItem) -> Unit) {
        var expanded by remember { mutableStateOf(isExpanded) }

        Column {
            // Folder header
            ListItem(
                modifier = Modifier
                    .padding(start = (depth * 24).dp)
                    .clickable { expanded = !expanded },
                leadingContent = {
                    Icon(
                        imageVector = if (expanded)
                            Icons.Default.FolderOpen
                        else
                            Icons.Default.Folder,
                        contentDescription = null,
                    )
                },
                headlineContent = { Text(name) },
                supportingContent = { Text("${getChildCount()} items") },
                trailingContent = {
                    Icon(
                        imageVector = if (expanded)
                            Icons.Default.ExpandLess
                        else
                            Icons.Default.ExpandMore,
                        contentDescription = if (expanded) "Collapse" else "Expand",
                    )
                },
            )

            // Children (recursive)
            AnimatedVisibility(visible = expanded) {
                Column {
                    children.forEach { child ->
                        child.Render(
                            depth = depth + 1,
                            onItemClick = onItemClick,
                        )
                    }
                }
            }
        }
    }

    override fun getChildCount(): Int {
        return children.sumOf { child ->
            when (child) {
                is FolderItem -> 1 + child.getChildCount()
                else -> 1
            }
        }
    }
}
```

### Usage

```kotlin
@Composable
fun FileBrowserScreen(rootFolder: FolderItem) {
    var selectedItem by remember { mutableStateOf<TreeItem?>(null) }

    LazyColumn {
        item {
            rootFolder.Render(
                depth = 0,
                onItemClick = { item -> selectedItem = item },
            )
        }
    }

    // Show selected item details
    selectedItem?.let { item ->
        if (item is FileItem) {
            FileDetailsDialog(
                file = item,
                onDismiss = { selectedItem = null },
            )
        }
    }
}
```

## 2. Shopping Cart Composite

### Cart Item Interface

```kotlin
// domain/entity/CartItem.kt

interface CartItem {
    val id: String
    fun getName(): String
    fun getPrice(): Double
    fun getItemCount(): Int

    @Composable
    fun Render(
        onQuantityChange: ((Int) -> Unit)? = null,
        onRemove: (() -> Unit)? = null,
    )
}
```

### Product (Leaf)

```kotlin
// domain/entity/Product.kt

data class Product(
    override val id: String,
    val name: String,
    val unitPrice: Double,
    val quantity: Int = 1,
    val imageUrl: String? = null,
) : CartItem {

    override fun getName(): String = name
    override fun getPrice(): Double = unitPrice * quantity
    override fun getItemCount(): Int = quantity

    @Composable
    override fun Render(
        onQuantityChange: ((Int) -> Unit)?,
        onRemove: (() -> Unit)?,
    ) {
        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Product image
                AsyncImage(
                    model = imageUrl,
                    contentDescription = name,
                    modifier = Modifier.size(60.dp).clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop,
                )

                Spacer(modifier = Modifier.width(12.dp))

                // Product details
                Column(modifier = Modifier.weight(1f)) {
                    Text(name, style = MaterialTheme.typography.titleSmall)
                    Text(
                        "$${String.format("%.2f", unitPrice)} each",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // Quantity controls
                if (onQuantityChange != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = {
                                if (quantity > 1) onQuantityChange(quantity - 1)
                            }
                        ) {
                            Icon(Icons.Default.Remove, contentDescription = "Decrease")
                        }

                        Text(
                            text = quantity.toString(),
                            modifier = Modifier.width(32.dp),
                            textAlign = TextAlign.Center,
                        )

                        IconButton(onClick = { onQuantityChange(quantity + 1) }) {
                            Icon(Icons.Default.Add, contentDescription = "Increase")
                        }
                    }
                }

                // Price
                Text(
                    "$${String.format("%.2f", getPrice())}",
                    style = MaterialTheme.typography.titleMedium,
                )

                // Remove button
                if (onRemove != null) {
                    IconButton(onClick = onRemove) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Remove",
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }
}
```

### Category (Composite)

```kotlin
// domain/entity/Category.kt

data class Category(
    override val id: String,
    val name: String,
    val children: List<CartItem>,
    val isExpanded: Boolean = true,
) : CartItem {

    override fun getName(): String = name
    override fun getPrice(): Double = children.sumOf { it.getPrice() }
    override fun getItemCount(): Int = children.sumOf { it.getItemCount() }

    @Composable
    override fun Render(
        onQuantityChange: ((Int) -> Unit)?,
        onRemove: (() -> Unit)?,
    ) {
        var expanded by remember { mutableStateOf(isExpanded) }

        Column {
            // Category header
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = if (expanded)
                            Icons.Default.ExpandLess
                        else
                            Icons.Default.ExpandMore,
                        contentDescription = null,
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = name,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            "${getItemCount()} items",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            "$${String.format("%.2f", getPrice())}",
                            style = MaterialTheme.typography.titleSmall,
                        )
                    }
                }
            }

            // Children
            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(start = 16.dp)) {
                    children.forEach { child ->
                        child.Render(
                            onQuantityChange = onQuantityChange,
                            onRemove = onRemove,
                        )
                    }
                }
            }
        }
    }
}
```

### Shopping Cart Screen

```kotlin
@Composable
fun ShoppingCartScreen(
    cartItems: List<CartItem>,
    onQuantityChange: (String, Int) -> Unit,
    onRemove: (String) -> Unit,
    onCheckout: () -> Unit,
) {
    val totalPrice = cartItems.sumOf { it.getPrice() }
    val totalItems = cartItems.sumOf { it.getItemCount() }

    Scaffold(
        bottomBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shadowElevation = 8.dp,
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            "Total ($totalItems items)",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            "$${String.format("%.2f", totalPrice)}",
                            style = MaterialTheme.typography.headlineSmall,
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = onCheckout,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Checkout")
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(16.dp),
        ) {
            items(cartItems, key = { it.id }) { item ->
                item.Render(
                    onQuantityChange = { qty -> onQuantityChange(item.id, qty) },
                    onRemove = { onRemove(item.id) },
                )
            }
        }
    }
}
```

## 3. Project/Frame Hierarchy for FrameLapse

### Timeline Composite

```kotlin
// domain/entity/TimelineItem.kt

sealed interface TimelineItem {
    val id: String

    @Composable
    fun Render(
        isSelected: Boolean,
        onSelect: () -> Unit,
        onAction: (TimelineAction) -> Unit,
    )

    fun getDuration(): Int  // in frames
}

sealed interface TimelineAction {
    data class Delete(val itemId: String) : TimelineAction
    data class Duplicate(val itemId: String) : TimelineAction
    data class MoveUp(val itemId: String) : TimelineAction
    data class MoveDown(val itemId: String) : TimelineAction
}

// Single frame
data class FrameTimelineItem(
    override val id: String,
    val frame: Frame,
    val holdDuration: Int = 1,  // frames to hold
) : TimelineItem {

    override fun getDuration(): Int = holdDuration

    @Composable
    override fun Render(
        isSelected: Boolean,
        onSelect: () -> Unit,
        onAction: (TimelineAction) -> Unit,
    ) {
        Card(
            modifier = Modifier
                .size(80.dp)
                .border(
                    width = if (isSelected) 3.dp else 0.dp,
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(8.dp),
                )
                .clickable(onClick = onSelect),
        ) {
            Box {
                AsyncImage(
                    model = frame.alignedPath ?: frame.originalPath,
                    contentDescription = "Frame ${frame.sortOrder}",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )

                // Duration badge
                if (holdDuration > 1) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(4.dp),
                        shape = RoundedCornerShape(4.dp),
                        color = Color.Black.copy(alpha = 0.7f),
                    ) {
                        Text(
                            text = "${holdDuration}f",
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
        }
    }
}

// Group of frames (for sections/chapters)
data class FrameGroupTimelineItem(
    override val id: String,
    val name: String,
    val items: List<TimelineItem>,
    val isExpanded: Boolean = true,
) : TimelineItem {

    override fun getDuration(): Int = items.sumOf { it.getDuration() }

    @Composable
    override fun Render(
        isSelected: Boolean,
        onSelect: () -> Unit,
        onAction: (TimelineAction) -> Unit,
    ) {
        var expanded by remember { mutableStateOf(isExpanded) }

        Column {
            // Group header
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected)
                        MaterialTheme.colorScheme.primaryContainer
                    else
                        MaterialTheme.colorScheme.surfaceVariant,
                ),
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = if (expanded)
                            Icons.Default.ExpandLess
                        else
                            Icons.Default.ExpandMore,
                        contentDescription = null,
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = name,
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f),
                    )

                    Text(
                        text = "${getDuration()} frames",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            // Child items
            AnimatedVisibility(visible = expanded) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(items, key = { it.id }) { item ->
                        item.Render(
                            isSelected = false,
                            onSelect = onSelect,
                            onAction = onAction,
                        )
                    }
                }
            }
        }
    }
}
```

## 4. Settings Composite

### Settings Item Hierarchy

```kotlin
// presentation/settings/SettingsItem.kt

sealed interface SettingsItem {
    val id: String
    val title: String

    @Composable
    fun Render(onAction: (SettingsAction) -> Unit)
}

// Toggle setting
data class ToggleSetting(
    override val id: String,
    override val title: String,
    val description: String? = null,
    val isEnabled: Boolean,
    val icon: ImageVector? = null,
) : SettingsItem {

    @Composable
    override fun Render(onAction: (SettingsAction) -> Unit) {
        ListItem(
            headlineContent = { Text(title) },
            supportingContent = description?.let { { Text(it) } },
            leadingContent = icon?.let { { Icon(it, contentDescription = null) } },
            trailingContent = {
                Switch(
                    checked = isEnabled,
                    onCheckedChange = { onAction(SettingsAction.Toggle(id, it)) },
                )
            },
        )
    }
}

// Slider setting
data class SliderSetting(
    override val id: String,
    override val title: String,
    val value: Float,
    val range: ClosedFloatingPointRange<Float>,
    val steps: Int = 0,
    val valueLabel: (Float) -> String = { it.toString() },
) : SettingsItem {

    @Composable
    override fun Render(onAction: (SettingsAction) -> Unit) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                Text(valueLabel(value), style = MaterialTheme.typography.bodyMedium)
            }

            Slider(
                value = value,
                onValueChange = { onAction(SettingsAction.UpdateSlider(id, it)) },
                valueRange = range,
                steps = steps,
            )
        }
    }
}

// Settings group (composite)
data class SettingsGroup(
    override val id: String,
    override val title: String,
    val children: List<SettingsItem>,
    val isExpanded: Boolean = true,
) : SettingsItem {

    @Composable
    override fun Render(onAction: (SettingsAction) -> Unit) {
        var expanded by remember { mutableStateOf(isExpanded) }

        Column {
            // Group header
            ListItem(
                modifier = Modifier.clickable { expanded = !expanded },
                headlineContent = {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                },
                trailingContent = {
                    Icon(
                        imageVector = if (expanded)
                            Icons.Default.ExpandLess
                        else
                            Icons.Default.ExpandMore,
                        contentDescription = null,
                    )
                },
            )

            // Children
            AnimatedVisibility(visible = expanded) {
                Column {
                    children.forEach { child ->
                        child.Render(onAction)
                    }
                }
            }

            HorizontalDivider()
        }
    }
}

sealed interface SettingsAction {
    data class Toggle(val id: String, val value: Boolean) : SettingsAction
    data class UpdateSlider(val id: String, val value: Float) : SettingsAction
    data class Navigate(val destination: String) : SettingsAction
}
```

## 5. Building Composite Trees

### Helper Functions

```kotlin
// Helper DSL for building trees
fun folder(
    id: String,
    name: String,
    builder: MutableList<TreeItem>.() -> Unit,
): FolderItem {
    val children = mutableListOf<TreeItem>()
    children.builder()
    return FolderItem(id, name, children)
}

fun MutableList<TreeItem>.file(
    id: String,
    name: String,
    size: Long,
    iconType: FileIconType = FileIconType.OTHER,
) {
    add(FileItem(id, name, size, iconType))
}

fun MutableList<TreeItem>.folder(
    id: String,
    name: String,
    builder: MutableList<TreeItem>.() -> Unit,
) {
    add(folder(id, name, builder))
}

// Usage
val root = folder("root", "Project Files") {
    folder("images", "Images") {
        file("img1", "photo1.jpg", 1024000, FileIconType.IMAGE)
        file("img2", "photo2.jpg", 2048000, FileIconType.IMAGE)
    }
    folder("exports", "Exports") {
        file("vid1", "timelapse.mp4", 50000000, FileIconType.VIDEO)
    }
    file("readme", "README.txt", 1024, FileIconType.DOCUMENT)
}
```

## Anti-Patterns

### Avoid: Infinite Recursion

```kotlin
// BAD - no depth limit
@Composable
fun BadComposite.Render() {
    children.forEach { it.Render() }  // Could stack overflow
}

// BETTER - with depth limit
@Composable
fun GoodComposite.Render(depth: Int = 0, maxDepth: Int = 10) {
    if (depth >= maxDepth) {
        Text("...")  // Truncate deep trees
        return
    }
    children.forEach { it.Render(depth + 1, maxDepth) }
}
```

### Avoid: Heavy Operations in Render

```kotlin
// BAD - expensive calculation on every recomposition
@Composable
fun BadCategory.Render() {
    val total = calculateDeepTotal()  // Expensive!
    Text("Total: $total")
}

// BETTER - use derivedStateOf or remember
@Composable
fun GoodCategory.Render() {
    val total = remember(children) { calculateDeepTotal() }
    Text("Total: $total")
}
```

## Reference Examples

- Navigation keys: `composeApp/src/commonMain/kotlin/com/po4yka/framelapse/ui/navigation/NavKeys.kt`
- Screen composables: `composeApp/src/commonMain/kotlin/com/po4yka/framelapse/ui/screens/`

## Checklist

### Component Interface
- [ ] Common interface for leaf and composite
- [ ] `@Composable` render method
- [ ] Aggregation methods (getPrice, getCount, etc.)

### Leaf Components
- [ ] Implement interface fully
- [ ] Handle click/selection
- [ ] Display individual data

### Composite Components
- [ ] Store list of children
- [ ] Implement expand/collapse
- [ ] Recursively render children
- [ ] Aggregate child data

### Performance
- [ ] Depth limit for deep trees
- [ ] Remember expensive calculations
- [ ] Use LazyColumn for large flat lists
- [ ] AnimatedVisibility for smooth expand/collapse
