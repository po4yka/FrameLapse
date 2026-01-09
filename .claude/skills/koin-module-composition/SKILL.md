---
name: koin-module-composition
description: Designs Koin module structure with scopes, composition, and lifecycle management. Use when adding features, organizing DI, or planning module dependencies.
---

# Koin Module Composition

## Overview

This skill helps design and organize Koin dependency injection modules following Clean Architecture principles. It covers module layering, scope management, and platform-specific composition.

## Module Hierarchy

FrameLapse uses a layered module structure:

```
commonModule     → Shared utilities (currently empty, for future use)
    ↓
dataModule       → Database, data sources, repositories (single scope)
    ↓
domainModule     → Use cases (factory scope)
    ↓
presentationModule → ViewModels (factory scope)
    ↓
platformModule   → Platform-specific implementations (expect/actual)
```

## 1. Layer-Based Module Organization

### Data Module Pattern

Use `single` scope for:
- Database instances (one per app lifecycle)
- Data sources (stateless, reusable)
- Repositories (bound to interfaces)

```kotlin
val dataModule = module {
    // Database - single instance
    single { get<DatabaseDriverFactory>().createDriver() }
    single { FrameLapseDatabase(get()) }
    single { get<FrameLapseDatabase>().projectQueries }
    single { get<FrameLapseDatabase>().frameQueries }

    // Data Sources - single instances
    single { ProjectLocalDataSource(get()) }
    single { FrameLocalDataSource(get()) }

    // Storage Managers - single instances
    single { ImageStorageManager(get()) }
    single { VideoStorageManager(get()) }

    // Repositories - bind implementation to interface
    single<ProjectRepository> { ProjectRepositoryImpl(get(), get()) }
    single<FrameRepository> { FrameRepositoryImpl(get(), get()) }
}
```

### Domain Module Pattern

Use `factory` scope for use cases (new instance per injection):

```kotlin
val domainModule = module {
    // Simple use cases
    factory { CreateProjectUseCase(get()) }
    factory { GetProjectsUseCase(get()) }
    factory { DeleteFrameUseCase(get()) }

    // Use cases with multiple dependencies
    factory {
        MultiPassStabilizationUseCase(
            faceDetector = get(),
            imageProcessor = get(),
            calculateMatrix = get(),
            calculateScore = get(),
            detectOvershoot = get(),
            refineRotation = get(),
            refineScale = get(),
            refineTranslation = get(),
        )
    }

    // Orchestrator use cases
    factory {
        AlignContentUseCase(
            alignFace = get(),
            alignBody = get(),
            alignMuscle = get(),
            alignLandscape = get(),
        )
    }
}
```

### Presentation Module Pattern

Use `factory` scope for ViewModels (new instance per screen):

```kotlin
val presentationModule = module {
    factory { ProjectListViewModel(get(), get(), get(), get()) }
    factory { CaptureViewModel(get(), get(), get(), get()) }
    factory { GalleryViewModel(get(), get(), get(), get(), get()) }
    factory { ExportViewModel(get(), get(), get()) }
}
```

## 2. Platform Module Pattern (expect/actual)

### Common Declaration

```kotlin
// commonMain/kotlin/.../di/KoinModules.kt
expect val platformModule: Module
```

### Android Implementation

```kotlin
// androidMain/kotlin/.../di/KoinModules.android.kt
actual val platformModule: Module = module {
    // Platform context
    single { androidContext() }

    // Platform services
    single { DatabaseDriverFactory(get()) }
    single { FileManager(get()) }

    // ML services with context
    single<FaceDetector> { FaceDetectorImpl(get()) }
    single<BodyPoseDetector> { BodyPoseDetectorImpl(get()) }
    single<ImageProcessor> { ImageProcessorImpl(get()) }

    // Media services
    single<VideoEncoder> { VideoEncoderImpl(get()) }
    single<GifEncoder> { GifEncoderImpl(get()) }
    single<CameraController> { CameraControllerImpl(get()) }
}
```

### iOS Implementation

```kotlin
// iosMain/kotlin/.../di/KoinModules.ios.kt
actual val platformModule: Module = module {
    // Platform services (no context needed)
    single { DatabaseDriverFactory() }
    single { FileManager() }

    // ML services
    single<FaceDetector> { FaceDetectorImpl() }
    single<BodyPoseDetector> { BodyPoseDetectorImpl() }
    single<ImageProcessor> { ImageProcessorImpl() }

    // Media services
    single<VideoEncoder> { VideoEncoderImpl() }
    single<GifEncoder> { GifEncoderImpl() }
}
```

## 3. Module Composition

### App-Level Composition

```kotlin
fun appModules(): List<Module> = listOf(
    commonModule,
    dataModule,
    domainModule,
    presentationModule,
    platformModule,
)

// In Application class or iOS entry point
startKoin {
    androidContext(this@App) // Android only
    modules(appModules())
}
```

### Feature Module Pattern (for larger apps)

```kotlin
// When features grow, split into feature modules
fun captureFeatureModules(): List<Module> = listOf(
    captureDataModule,
    captureDomainModule,
    capturePresentationModule,
)

fun appModules(): List<Module> = listOf(
    commonModule,
    dataModule,
    platformModule,
) + captureFeatureModules() + galleryFeatureModules()
```

## 4. Scope Selection Guide

| Scope | Use For | Lifecycle |
|-------|---------|-----------|
| `single` | Database, repositories, platform services | App lifetime |
| `factory` | Use cases, ViewModels | New instance per injection |
| `scoped` | Feature-scoped components | Custom scope lifetime |
| `viewModel` | Android ViewModels | ViewModel lifecycle |

### When to Use Each

```kotlin
// single - Shared, expensive to create
single { FrameLapseDatabase(get()) }

// factory - Stateless, cheap to create
factory { CreateProjectUseCase(get()) }

// scoped - Feature-specific, needs cleanup
scope<CaptureScope> {
    scoped { CaptureSessionManager(get(), get()) }
}

// viewModel - Lifecycle-aware (Android AAC)
viewModel { CaptureViewModel(get(), get(), get(), get()) }
```

## 5. Dependency Resolution Patterns

### Named Qualifiers

```kotlin
// Define qualifiers for multiple implementations
val domainModule = module {
    single<Dispatcher>(named("io")) { Dispatchers.IO }
    single<Dispatcher>(named("main")) { Dispatchers.Main }
}

// Inject with qualifier
class MyUseCase(
    @Named("io") private val ioDispatcher: CoroutineDispatcher
)
```

### Lazy Injection

```kotlin
// Defer creation until first use
class CaptureViewModel(
    private val alignContent: Lazy<AlignContentUseCase>
) {
    fun align() {
        alignContent.value.invoke(...)
    }
}
```

## Reference Examples

- Module definitions: `composeApp/src/commonMain/kotlin/com/po4yka/framelapse/di/KoinModules.kt`
- Android platform: `composeApp/src/androidMain/kotlin/com/po4yka/framelapse/di/KoinModules.android.kt`
- iOS platform: `composeApp/src/iosMain/kotlin/com/po4yka/framelapse/di/KoinModules.ios.kt`

## Checklist

### New Module
- [ ] Module uses correct scope (single/factory)
- [ ] Dependencies are injected via `get()`
- [ ] Interfaces bound to implementations
- [ ] Module added to `appModules()`

### New Use Case
- [ ] Registered with `factory` scope
- [ ] All dependencies available in graph
- [ ] Complex dependencies use named parameters

### New Platform Service
- [ ] Interface in commonMain
- [ ] Implementation in androidMain/iosMain
- [ ] Registered in respective platformModule

### Code Review
- [ ] No circular dependencies
- [ ] No `single` for stateful per-screen components
- [ ] No `factory` for expensive-to-create objects
