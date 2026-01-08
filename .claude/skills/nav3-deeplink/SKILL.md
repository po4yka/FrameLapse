---
name: nav3-deeplink
description: Adds deep linking support to Navigation 3 apps including URI pattern matching, NavKey decoding, and synthetic back stack construction. Use when implementing deep links, URL handling, or external app navigation.
---

# Adding Deep Linking to Nav3

## Overview

This skill helps implement deep linking in Navigation 3 apps, including basic URL parsing and advanced synthetic back stack construction.

## Basic Deep Linking

### 1. Define Deep Link Patterns

```kotlin
val deepLinkPatterns = listOf(
    DeepLinkPattern(
        "myapp://product/{id}",
        ProductDetail.serializer()
    ),
    DeepLinkPattern(
        "myapp://category/{categoryId}/product/{id}",
        ProductDetail.serializer()
    )
)
```

### 2. Parse Intent in Activity

```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    val uri: Uri? = intent.data

    val key: NavKey = uri?.let {
        val request = DeepLinkRequest(uri)
        val match = deepLinkPatterns.firstNotNullOfOrNull { pattern ->
            DeepLinkMatcher(request, pattern).match()
        }
        match?.let {
            KeyDecoder(match.args).decodeSerializableValue(match.serializer)
        }
    } ?: HomeScreen

    setContent {
        val backStack = rememberNavBackStack(key)
        // NavDisplay setup...
    }
}
```

## Advanced: Synthetic Back Stack

For proper Up navigation, build a synthetic back stack:

```kotlin
val syntheticBackStack = buildSyntheticBackStack(matchedKey)

fun buildSyntheticBackStack(target: NavKey): List<NavKey> {
    return when (target) {
        is ProductDetail -> listOf(HomeScreen, CategoryList, target)
        is CategoryList -> listOf(HomeScreen, target)
        else -> listOf(target)
    }
}
```

### Handle New Task vs Existing Task

```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    val isNewTask = (intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK) != 0

    if (isNewTask) {
        // Build full synthetic back stack
        val backStack = buildSyntheticBackStack(deepLinkKey)
    } else {
        // Just add to existing back stack
        backStack.add(deepLinkKey)
    }
}
```

## Reference Examples

- Basic deep linking: See `app/src/main/java/com/example/nav3recipes/deeplink/`
- Advanced deep linking: See `advanceddeeplinkapp/`
- Deep link guide: See `docs/deeplink-guide.md`

## AndroidManifest Setup

```xml
<activity android:name=".MainActivity">
    <intent-filter>
        <action android:name="android.intent.action.VIEW" />
        <category android:name="android.intent.category.DEFAULT" />
        <category android:name="android.intent.category.BROWSABLE" />
        <data android:scheme="myapp" />
    </intent-filter>
</activity>
```

## Checklist

- [ ] Deep link patterns defined
- [ ] Intent parsing in onCreate
- [ ] Synthetic back stack for proper Up navigation
- [ ] AndroidManifest intent-filter configured
- [ ] Test with `adb shell am start -d "myapp://path"`
