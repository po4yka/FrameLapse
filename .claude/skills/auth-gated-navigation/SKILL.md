---
name: auth-gated-navigation
description: Implements conditional navigation patterns including auth gates, permission checks, feature flags, and onboarding flows. Use when adding protected routes or conditional navigation.
---

# Auth-Gated Navigation

## Overview

This skill helps implement conditional navigation patterns including authentication gates, permission checks, feature flags, and onboarding flows. Use it when certain screens require authentication, permissions, or specific conditions to access.

## 1. Authentication Gate Pattern

### Auth State Definition

```kotlin
// domain/entity/AuthState.kt
sealed interface AuthState {
    data object Loading : AuthState
    data object LoggedOut : AuthState
    data class LoggedIn(
        val userId: String,
        val userName: String,
        val isPremium: Boolean = false,
    ) : AuthState
}

// domain/repository/AuthRepository.kt
interface AuthRepository {
    fun observeAuthState(): Flow<AuthState>
    suspend fun login(credentials: Credentials): Result<AuthState.LoggedIn>
    suspend fun logout(): Result<Unit>
    suspend fun getCurrentUser(): AuthState
}
```

### Root-Level Auth Gate

```kotlin
// ui/navigation/RootNavHost.kt
@Composable
fun RootNavHost(authRepository: AuthRepository = koinInject()) {
    val authState by authRepository.observeAuthState()
        .collectAsStateWithLifecycle(initialValue = AuthState.Loading)

    when (authState) {
        AuthState.Loading -> {
            // Full-screen loading
            LoadingScreen()
        }
        AuthState.LoggedOut -> {
            // Auth flow (login, registration)
            AuthNavHost(
                onLoginSuccess = { /* Auth state will update automatically */ }
            )
        }
        is AuthState.LoggedIn -> {
            // Main app content
            MainNavHost(
                user = (authState as AuthState.LoggedIn),
            )
        }
    }
}
```

### Auth-Protected Screen

```kotlin
// ui/navigation/AppNavHost.kt
@Composable
fun AppNavHost(authState: AuthState) {
    val backStack = rememberNavBackStack(ProjectListKey)

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryProvider = { key ->
            when (key) {
                // Public screens
                is ProjectListKey -> NavEntry(key) {
                    ProjectListScreen(...)
                }

                // Protected screen
                is ExportKey -> NavEntry(key) {
                    RequiresAuth(
                        authState = authState,
                        onAuthRequired = { backStack.add(LoginKey) },
                    ) { user ->
                        ExportScreen(
                            projectId = key.projectId,
                            user = user,
                        )
                    }
                }
            }
        }
    )
}

@Composable
fun RequiresAuth(
    authState: AuthState,
    onAuthRequired: () -> Unit,
    content: @Composable (AuthState.LoggedIn) -> Unit,
) {
    when (authState) {
        is AuthState.LoggedIn -> content(authState)
        AuthState.Loading -> LoadingIndicator()
        AuthState.LoggedOut -> {
            LaunchedEffect(Unit) { onAuthRequired() }
            LoadingIndicator()
        }
    }
}
```

## 2. Permission-Gated Navigation

### Permission State

```kotlin
// For Android - use Accompanist or manual permission handling
sealed interface PermissionState {
    data object NotRequested : PermissionState
    data object Granted : PermissionState
    data object Denied : PermissionState
    data object PermanentlyDenied : PermissionState
}
```

### Permission-Gated Screen

```kotlin
@Composable
fun CaptureScreen(
    projectId: String,
    onNavigateBack: () -> Unit,
) {
    // Android-specific permission handling
    val cameraPermissionState = rememberPermissionState(
        Manifest.permission.CAMERA
    )

    when {
        cameraPermissionState.status.isGranted -> {
            // Permission granted - show camera
            CaptureContent(projectId = projectId)
        }

        cameraPermissionState.status.shouldShowRationale -> {
            // Show explanation why camera is needed
            PermissionRationaleScreen(
                title = "Camera Permission Required",
                message = "FrameLapse needs camera access to capture photos for your timelapse.",
                onRequestPermission = {
                    cameraPermissionState.launchPermissionRequest()
                },
                onDismiss = onNavigateBack,
            )
        }

        else -> {
            // First request or permanently denied
            PermissionRequestScreen(
                permission = "Camera",
                onRequestPermission = {
                    cameraPermissionState.launchPermissionRequest()
                },
                onOpenSettings = {
                    // Open app settings
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                        }
                    )
                },
                onDismiss = onNavigateBack,
            )
        }
    }
}
```

### Multiple Permissions

```kotlin
@Composable
fun CaptureScreen(projectId: String) {
    val permissions = rememberMultiplePermissionsState(
        listOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
        )
    )

    when {
        permissions.allPermissionsGranted -> {
            CaptureContent(projectId = projectId)
        }
        permissions.shouldShowRationale -> {
            MultiPermissionRationaleScreen(
                permissions = permissions.revokedPermissions.map { it.permission },
                onRequest = { permissions.launchMultiplePermissionRequest() },
            )
        }
        else -> {
            PermissionRequestScreen(
                onRequest = { permissions.launchMultiplePermissionRequest() },
            )
        }
    }
}
```

## 3. Feature Flag Navigation

### Feature Flag State

```kotlin
// domain/entity/FeatureFlags.kt
data class FeatureFlags(
    val videoExportEnabled: Boolean = true,
    val gifExportEnabled: Boolean = false,
    val landscapeModeEnabled: Boolean = false,
    val cloudSyncEnabled: Boolean = false,
)

// domain/repository/FeatureFlagRepository.kt
interface FeatureFlagRepository {
    fun observeFlags(): Flow<FeatureFlags>
    suspend fun getFlags(): FeatureFlags
}
```

### Feature-Gated Navigation

```kotlin
@Composable
fun AppNavHost(featureFlags: FeatureFlags) {
    val backStack = rememberNavBackStack(ProjectListKey)

    NavDisplay(
        backStack = backStack,
        entryProvider = { key ->
            when (key) {
                is ExportKey -> NavEntry(key) {
                    if (featureFlags.videoExportEnabled) {
                        ExportScreen(projectId = key.projectId)
                    } else {
                        FeatureNotAvailableScreen(
                            featureName = "Video Export",
                            message = "Video export is coming soon!",
                            onDismiss = { backStack.removeLastOrNull() },
                        )
                    }
                }

                is GifExportKey -> NavEntry(key) {
                    if (featureFlags.gifExportEnabled) {
                        GifExportScreen(projectId = key.projectId)
                    } else {
                        FeatureComingSoonScreen(
                            featureName = "GIF Export",
                            onDismiss = { backStack.removeLastOrNull() },
                        )
                    }
                }

                // ...
            }
        }
    )
}
```

### Feature Check Before Navigation

```kotlin
@Composable
fun ProjectActionsMenu(
    featureFlags: FeatureFlags,
    onExportVideo: () -> Unit,
    onExportGif: () -> Unit,
) {
    DropdownMenu(...) {
        DropdownMenuItem(
            text = { Text("Export Video") },
            onClick = onExportVideo,
            enabled = featureFlags.videoExportEnabled,
        )

        if (featureFlags.gifExportEnabled) {
            DropdownMenuItem(
                text = { Text("Export GIF") },
                onClick = onExportGif,
            )
        }
    }
}
```

## 4. Onboarding Flow

### Onboarding State

```kotlin
// domain/entity/OnboardingState.kt
data class OnboardingState(
    val hasCompletedOnboarding: Boolean = false,
    val currentStep: Int = 0,
    val totalSteps: Int = 3,
)

// domain/repository/OnboardingRepository.kt
interface OnboardingRepository {
    suspend fun isOnboardingComplete(): Boolean
    suspend fun completeOnboarding()
    suspend fun resetOnboarding()
}
```

### Onboarding Gate

```kotlin
@Composable
fun RootNavHost(
    onboardingRepository: OnboardingRepository = koinInject(),
) {
    var isOnboardingComplete by remember { mutableStateOf<Boolean?>(null) }

    LaunchedEffect(Unit) {
        isOnboardingComplete = onboardingRepository.isOnboardingComplete()
    }

    when (isOnboardingComplete) {
        null -> LoadingScreen()
        false -> OnboardingNavHost(
            onComplete = {
                onboardingRepository.completeOnboarding()
                isOnboardingComplete = true
            }
        )
        true -> MainNavHost()
    }
}

@Composable
fun OnboardingNavHost(onComplete: () -> Unit) {
    val backStack = rememberNavBackStack(OnboardingWelcomeKey)

    NavDisplay(
        backStack = backStack,
        entryProvider = { key ->
            when (key) {
                OnboardingWelcomeKey -> NavEntry(key) {
                    OnboardingWelcomeScreen(
                        onNext = { backStack.add(OnboardingPermissionsKey) }
                    )
                }
                OnboardingPermissionsKey -> NavEntry(key) {
                    OnboardingPermissionsScreen(
                        onNext = { backStack.add(OnboardingTipsKey) }
                    )
                }
                OnboardingTipsKey -> NavEntry(key) {
                    OnboardingTipsScreen(
                        onComplete = onComplete
                    )
                }
            }
        }
    )
}
```

## 5. Conditional Back Stack

### Building Synthetic Back Stack

```kotlin
fun buildBackStackForDeepLink(target: NavKey, authState: AuthState): List<NavKey> {
    val stack = mutableListOf<NavKey>()

    // Always start with root
    stack.add(ProjectListKey)

    // Add intermediate screens based on target
    when (target) {
        is CaptureKey -> {
            // Project list -> Capture
            // No intermediates needed
        }
        is GalleryKey -> {
            // Project list -> Gallery
            // No intermediates needed
        }
        is ExportKey -> {
            // Project list -> Gallery -> Export
            stack.add(GalleryKey(target.projectId))
        }
        is FrameDetailKey -> {
            // Project list -> Gallery -> Frame detail
            stack.add(GalleryKey(target.projectId))
        }
    }

    // Add target if auth allows
    if (requiresAuth(target) && authState !is AuthState.LoggedIn) {
        stack.add(LoginKey)
    } else {
        stack.add(target)
    }

    return stack
}

private fun requiresAuth(key: NavKey): Boolean = when (key) {
    is ExportKey -> true
    is CloudSyncKey -> true
    else -> false
}
```

## 6. Navigation Guards

### Navigation Interceptor Pattern

```kotlin
class NavigationGuard(
    private val authRepository: AuthRepository,
    private val featureFlagRepository: FeatureFlagRepository,
) {
    suspend fun canNavigate(target: NavKey): NavigationResult {
        val authState = authRepository.getCurrentUser()
        val flags = featureFlagRepository.getFlags()

        return when (target) {
            // Auth-required destinations
            is ExportKey -> when (authState) {
                is AuthState.LoggedIn -> NavigationResult.Allowed
                else -> NavigationResult.Redirect(LoginKey)
            }

            // Feature-flagged destinations
            is GifExportKey -> when {
                !flags.gifExportEnabled -> NavigationResult.Blocked("GIF export coming soon")
                else -> NavigationResult.Allowed
            }

            // Premium-only destinations
            is CloudSyncKey -> when {
                authState !is AuthState.LoggedIn -> NavigationResult.Redirect(LoginKey)
                !authState.isPremium -> NavigationResult.Redirect(UpgradeKey)
                else -> NavigationResult.Allowed
            }

            else -> NavigationResult.Allowed
        }
    }
}

sealed interface NavigationResult {
    data object Allowed : NavigationResult
    data class Redirect(val target: NavKey) : NavigationResult
    data class Blocked(val message: String) : NavigationResult
}
```

### Using Navigation Guard

```kotlin
@Composable
fun GuardedNavigation(
    backStack: SnapshotStateList<NavKey>,
    guard: NavigationGuard,
) {
    val scope = rememberCoroutineScope()

    fun navigateSafely(target: NavKey) {
        scope.launch {
            when (val result = guard.canNavigate(target)) {
                NavigationResult.Allowed -> backStack.add(target)
                is NavigationResult.Redirect -> backStack.add(result.target)
                is NavigationResult.Blocked -> {
                    // Show snackbar or dialog
                }
            }
        }
    }

    // Pass navigateSafely to child composables
}
```

## Reference Examples

- Navigation setup: `composeApp/src/commonMain/kotlin/com/po4yka/framelapse/ui/navigation/AppNavHost.kt`
- NavKeys: `composeApp/src/commonMain/kotlin/com/po4yka/framelapse/ui/navigation/NavKeys.kt`
- Existing Nav3 skills: `.claude/skills/nav3-navigation/SKILL.md`

## Checklist

### Auth Gates
- [ ] Auth state observed reactively
- [ ] Loading state shown during auth check
- [ ] Proper redirect to login when needed
- [ ] User data passed to protected screens

### Permission Gates
- [ ] Permission state checked before content
- [ ] Rationale shown when needed
- [ ] Settings link for permanently denied
- [ ] Graceful fallback on denial

### Feature Flags
- [ ] Flags loaded at app start
- [ ] UI elements hidden/disabled appropriately
- [ ] "Coming soon" for disabled features
- [ ] No crashes on flag changes

### Onboarding
- [ ] Completion persisted
- [ ] Can be reset for testing
- [ ] Proper step navigation
- [ ] Skip option if appropriate
