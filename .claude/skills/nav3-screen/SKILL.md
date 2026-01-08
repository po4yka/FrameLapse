---
name: nav3-screen
description: Creates new Navigation 3 screens with NavKey, NavEntry, and Composable content. Use when adding a new screen, destination, or route to a Nav3 app.
---

# Creating Nav3 Screens

## Overview

This skill helps create new Navigation 3 screens following the patterns established in this codebase.

## Steps

1. **Define the NavKey** - Create a serializable data object or data class for the route:

```kotlin
@Serializable
data object HomeScreen : NavKey

// Or with parameters:
@Serializable
data class ProductDetail(val productId: String) : NavKey
```

2. **Create the screen Composable**:

```kotlin
@Composable
fun ProductDetailContent(
    productId: String,
    onNavigateBack: () -> Unit,
    onNavigateTo: (Any) -> Unit
) {
    // Screen content
}
```

3. **Register in entryProvider**:

```kotlin
entryProvider = entryProvider {
    entry<ProductDetail> { key ->
        ProductDetailContent(
            productId = key.productId,
            onNavigateBack = { backStack.removeLastOrNull() },
            onNavigateTo = { backStack.add(it) }
        )
    }
}
```

## File Organization

Follow the project structure:
- Place NavKeys in a dedicated file or alongside the screen
- Screen composables go in the feature package
- Use `@Serializable` annotation for all NavKeys

## Reference Examples

- Basic screen: See `app/src/main/java/com/example/nav3recipes/basic/`
- Screen with args: See `app/src/main/java/com/example/nav3recipes/passingarguments/`
- DSL pattern: See `app/src/main/java/com/example/nav3recipes/basicdsl/`

## Checklist

- [ ] NavKey defined with `@Serializable`
- [ ] Screen Composable created
- [ ] Entry registered in entryProvider
- [ ] Navigation callbacks properly wired
