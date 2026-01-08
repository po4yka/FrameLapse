---
name: nav3-navigation
description: Sets up Nav3 navigation patterns including bottom navigation with multiple back stacks, tab navigation, and conditional navigation flows. Use when implementing bottom nav, tabs, auth gates, or managing multiple navigation stacks.
---

# Navigation Patterns in Nav3

## Overview

This skill helps implement common navigation patterns in Navigation 3, including bottom navigation, multiple back stacks, and conditional navigation.

## Bottom Navigation with Multiple Back Stacks

### Define Top-Level Destinations

```kotlin
@Serializable data object HomeTab : NavKey
@Serializable data object SearchTab : NavKey
@Serializable data object ProfileTab : NavKey
```

### Create TopLevelBackStack Manager

```kotlin
class TopLevelBackStack<T : Any>(startKey: T) {
    private val stacks = mutableMapOf<T, SnapshotStateList<T>>()

    var currentTab by mutableStateOf(startKey)
        private set

    val backStack: SnapshotStateList<T>
        get() = stacks.getOrPut(currentTab) { mutableStateListOf(currentTab) }

    fun switchTab(tab: T) {
        if (tab != currentTab) {
            stacks.getOrPut(tab) { mutableStateListOf(tab) }
            currentTab = tab
        }
    }

    fun navigate(key: T) {
        backStack.add(key)
    }

    fun goBack(): Boolean {
        return if (backStack.size > 1) {
            backStack.removeLastOrNull()
            true
        } else false
    }
}
```

### Implement Bottom Navigation UI

```kotlin
@Composable
fun MainScreen() {
    val navManager = remember { TopLevelBackStack(HomeTab) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Home, null) },
                    label = { Text("Home") },
                    selected = navManager.currentTab == HomeTab,
                    onClick = { navManager.switchTab(HomeTab) }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Search, null) },
                    label = { Text("Search") },
                    selected = navManager.currentTab == SearchTab,
                    onClick = { navManager.switchTab(SearchTab) }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Person, null) },
                    label = { Text("Profile") },
                    selected = navManager.currentTab == ProfileTab,
                    onClick = { navManager.switchTab(ProfileTab) }
                )
            }
        }
    ) { padding ->
        NavDisplay(
            modifier = Modifier.padding(padding),
            backStack = navManager.backStack,
            onBack = { navManager.goBack() },
            entryProvider = entryProvider {
                entry<HomeTab> { HomeContent(onNavigate = navManager::navigate) }
                entry<SearchTab> { SearchContent() }
                entry<ProfileTab> { ProfileContent() }
            }
        )
    }
}
```

## Conditional Navigation (Auth Gates)

### Define Auth State

```kotlin
sealed interface AuthState {
    data object Loading : AuthState
    data object LoggedOut : AuthState
    data class LoggedIn(val user: User) : AuthState
}
```

### Implement Auth Gate

```kotlin
@Composable
fun AuthGatedNavigation(authState: AuthState) {
    when (authState) {
        AuthState.Loading -> LoadingScreen()
        AuthState.LoggedOut -> LoginFlow()
        is AuthState.LoggedIn -> MainNavigation(authState.user)
    }
}
```

### RequiresLogin Pattern

```kotlin
@Composable
fun RequiresLogin(
    authState: AuthState,
    onLoginRequired: () -> Unit,
    content: @Composable (User) -> Unit
) {
    when (authState) {
        is AuthState.LoggedIn -> content(authState.user)
        else -> {
            LaunchedEffect(Unit) { onLoginRequired() }
            LoadingScreen()
        }
    }
}

// Usage in entryProvider
entry<ProfileScreen> {
    RequiresLogin(
        authState = authState,
        onLoginRequired = { backStack.add(LoginScreen) }
    ) { user ->
        ProfileContent(user)
    }
}
```

## Persistent Back Stack

Use `rememberNavBackStack` for configuration change survival:

```kotlin
val backStack = rememberNavBackStack(HomeScreen)
```

For custom persistence:

```kotlin
val backStack = rememberSaveable(
    saver = listSaver(
        save = { it.toList() },
        restore = { mutableStateListOf<NavKey>().apply { addAll(it) } }
    )
) {
    mutableStateListOf<NavKey>(HomeScreen)
}
```

## Reference Examples

- Bottom Navigation: See `app/src/main/java/com/example/nav3recipes/commonui/`
- Multiple Stacks: See `app/src/main/java/com/example/nav3recipes/multiplestacks/`
- Conditional Nav: See `app/src/main/java/com/example/nav3recipes/conditional/`
- Persistent Stack: See `app/src/main/java/com/example/nav3recipes/basicsaveable/`

## Checklist

- [ ] Top-level destinations defined as NavKeys
- [ ] Back stack manager handles tab switching
- [ ] State preserved per tab
- [ ] Bottom bar reflects current selection
- [ ] Back gesture handled correctly
