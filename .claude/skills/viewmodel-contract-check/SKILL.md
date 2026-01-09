---
name: viewmodel-contract-check
description: Validates ViewModel state contracts including immutability, event completeness, effect appropriateness, and testing coverage. Use when reviewing ViewModels for correctness and completeness.
---

# ViewModel Contract Check

## Overview

This skill helps validate ViewModel implementations against the State/Event/Effect contract. Use it to review ViewModels for correctness, completeness, and adherence to UDF patterns.

## Contract Requirements

### 1. State Requirements

| Requirement | Check | Example |
|-------------|-------|---------|
| Immutability | All properties are `val` | `val isLoading: Boolean` |
| Defaults | All properties have defaults | `val error: String? = null` |
| Data class | State is a data class | `data class CaptureState(...)` |
| Implements UiState | Extends marker interface | `: UiState` |

### 2. Event Requirements

| Requirement | Check | Example |
|-------------|-------|---------|
| Sealed interface | Events are sealed | `sealed interface CaptureEvent` |
| Implements UiEvent | Extends marker interface | `: UiEvent` |
| Exhaustive handling | All events handled in `onEvent()` | `when (event) { ... }` |
| Naming | Events describe user intent | `CaptureImage`, not `OnButtonClick` |

### 3. Effect Requirements

| Requirement | Check | Example |
|-------------|-------|---------|
| Sealed interface | Effects are sealed | `sealed interface CaptureEffect` |
| Implements UiEffect | Extends marker interface | `: UiEffect` |
| One-time actions | Effects are transient | `PlaySound`, not `UpdateUI` |
| Navigation | Navigation uses Effects | `NavigateToGallery(projectId)` |

## Validation Checks

### Check 1: State Immutability

```kotlin
// VIOLATION: Mutable property in state
data class CaptureState(
    var frameCount: Int = 0,  // ❌ var is mutable
    val isProcessing: Boolean = false,
) : UiState

// CORRECT: All val properties
data class CaptureState(
    val frameCount: Int = 0,  // ✓ val is immutable
    val isProcessing: Boolean = false,
) : UiState
```

### Check 2: Default Values

```kotlin
// VIOLATION: Missing defaults
data class GalleryState(
    val projectId: String,  // ❌ No default - requires value at construction
    val frames: List<Frame>,  // ❌ No default
) : UiState

// CORRECT: All properties have defaults
data class GalleryState(
    val projectId: String = "",  // ✓ Default empty string
    val frames: List<Frame> = emptyList(),  // ✓ Default empty list
    val isLoading: Boolean = false,
    val error: String? = null,
) : UiState
```

### Check 3: Event Exhaustiveness

```kotlin
// VIOLATION: Missing event handling
override fun onEvent(event: CaptureEvent) {
    when (event) {
        is CaptureEvent.Initialize -> initialize(event.projectId)
        CaptureEvent.CaptureImage -> captureImage()
        // ❌ Missing: ToggleFlash, FlipCamera, etc.
    }
}

// CORRECT: All events handled
override fun onEvent(event: CaptureEvent) {
    when (event) {
        is CaptureEvent.Initialize -> initialize(event.projectId)
        CaptureEvent.CaptureImage -> captureImage()
        CaptureEvent.ToggleFlash -> toggleFlash()
        CaptureEvent.FlipCamera -> flipCamera()
        CaptureEvent.ToggleGrid -> toggleGrid()
        is CaptureEvent.UpdateGhostOpacity -> updateGhostOpacity(event.opacity)
        CaptureEvent.NavigateToGallery -> navigateToGallery()
        CaptureEvent.CameraReady -> updateState { copy(isCameraReady = true) }
        CaptureEvent.CameraError -> handleCameraError()
    }
}
```

### Check 4: Effect vs State Decision

```kotlin
// VIOLATION: Persistent data as Effect
sealed interface CaptureEffect : UiEffect {
    data class UpdateFrameCount(val count: Int) : CaptureEffect  // ❌ Should be state
    data class SetGhostImage(val path: String) : CaptureEffect  // ❌ Should be state
}

// CORRECT: One-time actions as Effects
sealed interface CaptureEffect : UiEffect {
    data object PlayCaptureSound : CaptureEffect  // ✓ One-time action
    data class ShowError(val message: String) : CaptureEffect  // ✓ Toast/Snackbar
    data class NavigateToGallery(val projectId: String) : CaptureEffect  // ✓ Navigation
    data object TriggerHaptic : CaptureEffect  // ✓ One-time feedback
}
```

### Check 5: Proper updateState Usage

```kotlin
// VIOLATION: Direct state mutation
private fun captureImage() {
    _state.value.copy(isProcessing = true)  // ❌ Missing assignment
}

// VIOLATION: Not using reducer pattern
private fun captureImage() {
    _state.value = CaptureState(isProcessing = true)  // ❌ Loses other state
}

// CORRECT: Using updateState with reducer
private fun captureImage() {
    updateState { copy(isProcessing = true) }  // ✓ Reducer pattern
}
```

### Check 6: Error Handling

```kotlin
// VIOLATION: Swallowing errors
private fun loadData() {
    viewModelScope.launch {
        try {
            val data = repository.getData()
            updateState { copy(data = data) }
        } catch (e: Exception) {
            // ❌ Error silently ignored
        }
    }
}

// CORRECT: Proper error handling
private fun loadData() {
    viewModelScope.launch {
        updateState { copy(isLoading = true, error = null) }

        repository.getData()
            .onSuccess { data ->
                updateState { copy(isLoading = false, data = data) }
            }
            .onError { _, message ->
                updateState { copy(isLoading = false, error = message) }
                sendEffect(ShowError(message ?: "Failed to load data"))
            }
    }
}
```

### Check 7: Scope Safety

```kotlin
// VIOLATION: Using GlobalScope
private fun loadData() {
    GlobalScope.launch {  // ❌ Not tied to ViewModel lifecycle
        // ...
    }
}

// VIOLATION: Creating new scope
private fun loadData() {
    CoroutineScope(Dispatchers.IO).launch {  // ❌ Not cancelled on destroy
        // ...
    }
}

// CORRECT: Using viewModelScope
private fun loadData() {
    viewModelScope.launch {  // ✓ Cancelled when ViewModel cleared
        // ...
    }
}
```

### Check 8: Event Naming Convention

```kotlin
// VIOLATION: UI-centric naming
sealed interface GalleryEvent : UiEvent {
    data object OnDeleteButtonClicked : GalleryEvent  // ❌ Describes UI element
    data object HandleBackPress : GalleryEvent  // ❌ Describes implementation
}

// CORRECT: Intent-based naming
sealed interface GalleryEvent : UiEvent {
    data class DeleteFrames(val frameIds: List<String>) : GalleryEvent  // ✓ Describes intent
    data object NavigateBack : GalleryEvent  // ✓ Describes intent
}
```

## Quick Reference

### State Decision Matrix

| Data Type | In State? | In Effect? |
|-----------|-----------|------------|
| Loading indicator | ✓ | |
| Error message (persistent) | ✓ | |
| List of items | ✓ | |
| Selected items | ✓ | |
| Form input values | ✓ | |
| Toast/Snackbar | | ✓ |
| Navigation | | ✓ |
| Sound/Haptic | | ✓ |
| Share intent | | ✓ |
| Permission request | | ✓ |

### Event Categories

| Category | Examples |
|----------|----------|
| Lifecycle | `Initialize`, `OnResume`, `OnPause` |
| User Actions | `CaptureImage`, `DeleteFrame`, `ToggleFlash` |
| Settings | `UpdateOpacity`, `ChangeResolution` |
| Navigation | `NavigateToGallery`, `NavigateBack` |
| External | `CameraReady`, `PermissionGranted` |

## Validation Script

```kotlin
// Pseudo-code for automated checks
fun validateViewModel(viewModel: BaseViewModel<*, *, *>) {
    // Check 1: State is data class with val properties
    val stateClass = viewModel.state.value::class
    require(stateClass.isData) { "State must be data class" }
    stateClass.memberProperties.forEach { prop ->
        require(!prop.isLateinit && prop.isConst.not()) {
            "Property ${prop.name} must be val with default"
        }
    }

    // Check 2: All events handled (compile-time with sealed + when)

    // Check 3: Effects are one-time (manual review)
}
```

## Reference Examples

- BaseViewModel: `composeApp/src/commonMain/kotlin/com/po4yka/framelapse/presentation/base/BaseViewModel.kt`
- CaptureContract: `composeApp/src/commonMain/kotlin/com/po4yka/framelapse/presentation/capture/CaptureContract.kt`
- CaptureViewModel: `composeApp/src/commonMain/kotlin/com/po4yka/framelapse/presentation/capture/CaptureViewModel.kt`

## Checklist

### State
- [ ] Data class with `: UiState`
- [ ] All properties are `val`
- [ ] All properties have defaults
- [ ] No UI types (Composables, Views)
- [ ] Computed properties use `get()`

### Events
- [ ] Sealed interface with `: UiEvent`
- [ ] All events handled in `onEvent()`
- [ ] Intent-based naming (not UI-centric)
- [ ] Parameterized events use data classes

### Effects
- [ ] Sealed interface with `: UiEffect`
- [ ] Only one-time actions
- [ ] Navigation uses Effects
- [ ] Feedback (toast/haptic) uses Effects

### Implementation
- [ ] Uses `updateState { copy(...) }` pattern
- [ ] Uses `viewModelScope` for coroutines
- [ ] Errors captured in state and/or effects
- [ ] Loading states properly tracked
