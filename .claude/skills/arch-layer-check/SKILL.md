---
name: arch-layer-check
description: Verifies Clean Architecture layer separation, dependency direction, and module boundaries. Use when reviewing code for layer violations, checking imports, or validating module dependencies.
---

# Architecture Layer Check

## Overview

This skill helps verify Clean Architecture layer separation and dependency direction. Use it to identify layer violations, check imports, and validate module boundaries.

## Layer Hierarchy

```
┌─────────────────────────────────────────────────────────┐
│  UI Layer (ui/*)                                        │
│  - Compose screens, components, theme                   │
│  - CAN depend on: presentation, domain/entity           │
└────────────────────────┬────────────────────────────────┘
                         │
┌────────────────────────▼────────────────────────────────┐
│  Presentation Layer (presentation/*)                    │
│  - ViewModels, State, Events, Effects                   │
│  - CAN depend on: domain (use cases, entities)          │
└────────────────────────┬────────────────────────────────┘
                         │
┌────────────────────────▼────────────────────────────────┐
│  Domain Layer (domain/*)                                │
│  - Entities, Use Cases, Repository Interfaces           │
│  - CAN depend on: NOTHING (pure Kotlin)                 │
└────────────────────────┬────────────────────────────────┘
                         │
┌────────────────────────▼────────────────────────────────┐
│  Data Layer (data/*)                                    │
│  - Repository implementations, data sources, mappers    │
│  - CAN depend on: domain (interfaces, entities)         │
└─────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────┐
│  Platform Layer (platform/*)                            │
│  - expect/actual implementations                        │
│  - CAN depend on: domain (service interfaces)           │
└─────────────────────────────────────────────────────────┘
```

## Dependency Rules

### Rule 1: Domain Has No Dependencies

```kotlin
// domain/usecase/CreateProjectUseCase.kt

// CORRECT imports:
import com.po4yka.framelapse.domain.entity.Project
import com.po4yka.framelapse.domain.repository.ProjectRepository
import com.po4yka.framelapse.domain.util.Result

// VIOLATION - importing from data:
import com.po4yka.framelapse.data.local.ProjectLocalDataSource  // ❌

// VIOLATION - importing from presentation:
import com.po4yka.framelapse.presentation.projectlist.ProjectListState  // ❌

// VIOLATION - importing platform-specific:
import android.content.Context  // ❌
```

### Rule 2: Data Depends Only on Domain

```kotlin
// data/repository/FrameRepositoryImpl.kt

// CORRECT imports:
import com.po4yka.framelapse.domain.entity.Frame
import com.po4yka.framelapse.domain.repository.FrameRepository
import com.po4yka.framelapse.domain.util.Result
import com.po4yka.framelapse.data.local.FrameLocalDataSource
import com.po4yka.framelapse.data.mapper.FrameMapper

// VIOLATION - importing from presentation:
import com.po4yka.framelapse.presentation.gallery.GalleryState  // ❌

// VIOLATION - importing from UI:
import com.po4yka.framelapse.ui.screens.GalleryScreen  // ❌
```

### Rule 3: Presentation Depends Only on Domain

```kotlin
// presentation/capture/CaptureViewModel.kt

// CORRECT imports:
import com.po4yka.framelapse.domain.usecase.capture.CaptureImageUseCase
import com.po4yka.framelapse.domain.entity.Frame
import com.po4yka.framelapse.domain.util.Result
import com.po4yka.framelapse.presentation.base.BaseViewModel

// VIOLATION - importing from data:
import com.po4yka.framelapse.data.repository.FrameRepositoryImpl  // ❌

// VIOLATION - importing from UI:
import com.po4yka.framelapse.ui.components.CaptureButton  // ❌
```

### Rule 4: UI Depends on Presentation and Domain Entities

```kotlin
// ui/screens/CaptureScreen.kt

// CORRECT imports:
import com.po4yka.framelapse.presentation.capture.CaptureViewModel
import com.po4yka.framelapse.presentation.capture.CaptureState
import com.po4yka.framelapse.presentation.capture.CaptureEvent
import com.po4yka.framelapse.domain.entity.Frame
import com.po4yka.framelapse.ui.components.CameraPreview

// VIOLATION - importing from data:
import com.po4yka.framelapse.data.local.FrameLocalDataSource  // ❌

// VIOLATION - importing use cases directly (bypass ViewModel):
import com.po4yka.framelapse.domain.usecase.CaptureImageUseCase  // ❌
```

## Common Violations

### Violation 1: Repository Implementation in Domain

```kotlin
// WRONG: domain/repository/FrameRepository.kt
class FrameRepository(  // ❌ Implementation in domain
    private val dataSource: FrameLocalDataSource,
) {
    suspend fun getFrames(): List<Frame> { ... }
}

// CORRECT: domain/repository/FrameRepository.kt
interface FrameRepository {  // ✓ Interface in domain
    suspend fun getFrames(projectId: String): Result<List<Frame>>
}

// data/repository/FrameRepositoryImpl.kt
class FrameRepositoryImpl(  // ✓ Implementation in data
    private val dataSource: FrameLocalDataSource,
) : FrameRepository { ... }
```

### Violation 2: Database Entities in Domain

```kotlin
// WRONG: domain/entity/Frame.kt using Room annotations
@Entity(tableName = "frames")  // ❌ Database annotation in domain
data class Frame(
    @PrimaryKey val id: String,
    val projectId: String,
)

// CORRECT: Separate domain entity from database entity
// domain/entity/Frame.kt
data class Frame(  // ✓ Pure Kotlin
    val id: String,
    val projectId: String,
)

// data/local/entity/FrameEntity.kt
@Entity(tableName = "frames")  // ✓ Database annotation in data
data class FrameEntity(
    @PrimaryKey val id: String,
    val projectId: String,
)
```

### Violation 3: ViewModel Logic in UI

```kotlin
// WRONG: ui/screens/GalleryScreen.kt
@Composable
fun GalleryScreen(frameRepository: FrameRepository) {  // ❌ Repository in UI
    val frames by remember { mutableStateOf<List<Frame>>(emptyList()) }
    LaunchedEffect(Unit) {
        frames = frameRepository.getFrames(projectId)  // ❌ Business logic in UI
    }
}

// CORRECT: ui/screens/GalleryScreen.kt
@Composable
fun GalleryScreen(viewModel: GalleryViewModel) {  // ✓ ViewModel injection
    val state by viewModel.state.collectAsStateWithLifecycle()
    GalleryContent(state = state, onEvent = viewModel::onEvent)
}
```

### Violation 4: Platform Code in Common

```kotlin
// WRONG: domain/service/FaceDetector.kt
class FaceDetector(
    private val context: Context,  // ❌ Android type in common
) {
    fun detect(image: Bitmap): FaceLandmarks? { ... }  // ❌ Platform type
}

// CORRECT: Use expect/actual
// domain/service/FaceDetector.kt (commonMain)
interface FaceDetector {  // ✓ Interface in common
    suspend fun detectFace(imagePath: String): FaceLandmarks?
}

// platform/FaceDetectorImpl.kt (androidMain)
class FaceDetectorImpl(  // ✓ Platform impl in platformMain
    private val context: Context,
) : FaceDetector { ... }
```

## Import Validation Script

### Check Domain Layer

```bash
# Find domain files importing from data/presentation/ui
grep -r "import com.po4yka.framelapse.data" composeApp/src/commonMain/kotlin/com/po4yka/framelapse/domain/
grep -r "import com.po4yka.framelapse.presentation" composeApp/src/commonMain/kotlin/com/po4yka/framelapse/domain/
grep -r "import com.po4yka.framelapse.ui" composeApp/src/commonMain/kotlin/com/po4yka/framelapse/domain/

# Find Android imports in domain
grep -r "import android\." composeApp/src/commonMain/kotlin/com/po4yka/framelapse/domain/
grep -r "import androidx\." composeApp/src/commonMain/kotlin/com/po4yka/framelapse/domain/
```

### Check Data Layer

```bash
# Find data files importing from presentation/ui
grep -r "import com.po4yka.framelapse.presentation" composeApp/src/commonMain/kotlin/com/po4yka/framelapse/data/
grep -r "import com.po4yka.framelapse.ui" composeApp/src/commonMain/kotlin/com/po4yka/framelapse/data/
```

### Check Presentation Layer

```bash
# Find presentation files importing from data/ui
grep -r "import com.po4yka.framelapse.data" composeApp/src/commonMain/kotlin/com/po4yka/framelapse/presentation/
grep -r "import com.po4yka.framelapse.ui" composeApp/src/commonMain/kotlin/com/po4yka/framelapse/presentation/
```

## Layer Boundaries Summary

| Layer | Can Import | Cannot Import |
|-------|------------|---------------|
| domain | kotlin stdlib, kotlinx | data, presentation, ui, platform |
| data | domain, kotlin, kotlinx | presentation, ui |
| presentation | domain, kotlin, kotlinx, lifecycle | data, ui |
| ui | presentation, domain/entity, kotlin, compose | data (directly) |
| platform | domain, platform SDK | data, presentation, ui |

## Reference Examples

- Domain entity: `composeApp/src/commonMain/kotlin/com/po4yka/framelapse/domain/entity/Frame.kt`
- Domain interface: `composeApp/src/commonMain/kotlin/com/po4yka/framelapse/domain/repository/FrameRepository.kt`
- Data implementation: `composeApp/src/commonMain/kotlin/com/po4yka/framelapse/data/repository/FrameRepositoryImpl.kt`
- ViewModel: `composeApp/src/commonMain/kotlin/com/po4yka/framelapse/presentation/capture/CaptureViewModel.kt`

## Checklist

### Domain Layer
- [ ] No imports from `data.*`
- [ ] No imports from `presentation.*`
- [ ] No imports from `ui.*`
- [ ] No platform-specific imports (android.*, UIKit.*)
- [ ] Repository interfaces, not implementations
- [ ] Entities are pure data classes

### Data Layer
- [ ] No imports from `presentation.*`
- [ ] No imports from `ui.*`
- [ ] Only implements domain interfaces
- [ ] Maps between data entities and domain entities

### Presentation Layer
- [ ] No imports from `data.*` (except through DI)
- [ ] No imports from `ui.*`
- [ ] Uses domain use cases, not repositories directly
- [ ] ViewModels don't create UI components

### UI Layer
- [ ] No direct imports from `data.*`
- [ ] Uses ViewModels for business logic
- [ ] Only domain entities (not data entities)
- [ ] No business logic in Composables

### Platform Layer
- [ ] Implements domain service interfaces
- [ ] Platform types stay in platform layer
- [ ] Common code uses expect declarations
