---
name: nav3-scene
description: Creates custom Nav3 scenes including dialogs, bottom sheets, and list-detail layouts using SceneStrategy. Use when implementing modal dialogs, bottom sheets, two-pane layouts, or adaptive layouts.
---

# Custom Scenes in Nav3

## Overview

This skill helps implement custom scene layouts in Navigation 3, including dialogs, bottom sheets, and adaptive list-detail layouts.

## Dialog Scene

### Setup DialogSceneStrategy

```kotlin
@Composable
fun AppNavigation() {
    val backStack = rememberNavBackStack(HomeScreen)
    val dialogStrategy = remember { DialogSceneStrategy<NavKey>() }

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        sceneStrategy = dialogStrategy,
        entryProvider = entryProvider {
            entry<HomeScreen> { HomeContent(...) }

            entry<ConfirmDialog>(
                metadata = DialogSceneStrategy.dialog(
                    DialogProperties(
                        dismissOnClickOutside = true,
                        dismissOnBackPress = true
                    )
                )
            ) { key ->
                DialogContent(key, onDismiss = { backStack.removeLastOrNull() })
            }
        }
    )
}
```

### Navigate to Dialog

```kotlin
backStack.add(ConfirmDialog(message = "Delete item?"))
```

## Bottom Sheet Scene

### Setup BottomSheetSceneStrategy

```kotlin
@Composable
fun AppNavigation() {
    val backStack = rememberNavBackStack(HomeScreen)
    val bottomSheetStrategy = remember { BottomSheetSceneStrategy<NavKey>() }

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        sceneStrategy = bottomSheetStrategy,
        entryProvider = entryProvider {
            entry<HomeScreen> { HomeContent(...) }

            entry<FilterSheet>(
                metadata = BottomSheetSceneStrategy.bottomSheet()
            ) { key ->
                FilterSheetContent(key)
            }
        }
    )
}
```

## List-Detail Scene (Adaptive Layout)

### Using ListDetailSceneStrategy

```kotlin
@Composable
fun AppNavigation() {
    val backStack = rememberNavBackStack(ItemList)
    val listDetailStrategy = remember {
        ListDetailSceneStrategy<NavKey>(
            listPredicate = { it is ItemList },
            detailPredicate = { it is ItemDetail }
        )
    }

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        sceneStrategy = listDetailStrategy,
        entryProvider = entryProvider {
            entry<ItemList>(
                metadata = ListDetailSceneStrategy.listPane()
            ) {
                ItemListContent(onItemClick = { backStack.add(ItemDetail(it)) })
            }

            entry<ItemDetail>(
                metadata = ListDetailSceneStrategy.detailPane()
            ) { key ->
                ItemDetailContent(key.itemId)
            }
        }
    )
}
```

## Material 3 Adaptive Layout

For Material 3 integration:

```kotlin
@Composable
fun AdaptiveNavigation() {
    val backStack = rememberNavBackStack(ItemList)

    NavigableListDetailPaneScaffold(
        navigator = navigator,
        listPane = { ItemListContent() },
        detailPane = { ItemDetailContent() }
    )
}
```

## Reference Examples

- Dialog: See `app/src/main/java/com/example/nav3recipes/dialog/`
- Bottom Sheet: See `app/src/main/java/com/example/nav3recipes/bottomsheet/`
- List-Detail: See `app/src/main/java/com/example/nav3recipes/scenes/`
- Material Adaptive: See `app/src/main/java/com/example/nav3recipes/material/`

## Checklist

- [ ] SceneStrategy created and configured
- [ ] Metadata applied to scene-specific entries
- [ ] NavDisplay uses sceneStrategy parameter
- [ ] Dismiss/close callbacks wired correctly
