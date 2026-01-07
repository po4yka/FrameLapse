# FrameLapse Development Roadmap

## Overview

This roadmap outlines the implementation plan for FrameLapse, a Kotlin Multiplatform application for creating stabilized daily self-portrait timelapse videos.

**Target Platforms:** Android, iOS
**Architecture:** Clean Architecture + UDF
**UI Framework:** Compose Multiplatform

---

## Phase 1: Project Foundation ✅

### 1.1 Project Setup
- [x] Configure Gradle with KMP plugins and version catalog
- [x] Set up composeApp module with commonMain, androidMain, iosMain source sets
- [x] Configure iOS app entry point in iosApp directory
- [x] Add Compose Multiplatform dependencies
- [x] Configure Koin for dependency injection
- [x] Set up SQLDelight with database schema
- [x] Add Coroutines and Flow dependencies
- [x] Configure Detekt + Spotless for code formatting
- [x] Set up CI/CD pipeline (GitHub Actions)

### 1.2 Core Architecture Scaffolding
- [x] Create base package structure:
  ```
  commonMain/kotlin/com/po4yka/framelapse/
  ├── data/
  │   ├── local/
  │   └── repository/
  ├── domain/
  │   ├── entity/
  │   ├── repository/
  │   └── usecase/
  ├── presentation/
  │   └── base/
  ├── ui/
  │   └── theme/
  ├── navigation/
  └── platform/
  ```
- [x] Define expect/actual interfaces for platform capabilities
- [x] Set up Koin modules (common, Android, iOS)
- [x] Create base ViewModel class with StateFlow pattern
- [x] Implement Result wrapper for error handling

### 1.3 Database Schema
- [x] Design SQLDelight schema:
  - [x] `Project` table (id, name, createdAt, fps, resolution, orientation)
  - [x] `Frame` table (id, projectId, originalPath, alignedPath, timestamp, confidence, landmarks)
  - [x] `Settings` table (key, value)
- [x] Create database driver expect/actual
- [x] Implement migrations strategy
- [x] Create DAO interfaces (via SQLDelight generated queries)

---

## Phase 2: Domain Layer ✅

### 2.1 Entities
- [x] `Project` - timelapse project with settings
- [x] `Frame` - individual photo with metadata
- [x] `FaceLandmarks` - 478 3D facial landmarks
- [x] `AlignmentMatrix` - affine transformation data
- [x] `ExportSettings` - video export configuration
- [x] `CaptureSettings` - camera capture preferences
- [x] `AlignmentSettings` - face alignment configuration

### 2.2 Repository Interfaces
- [x] `ProjectRepository`
  - [x] createProject, getProject, getAllProjects, deleteProject
  - [x] updateProjectSettings, observeProjects
- [x] `FrameRepository`
  - [x] addFrame, getFrames, deleteFrame
  - [x] getFrameCount, getLatestFrame, observeFrames
- [x] `SettingsRepository`
  - [x] get/set preferences (string, int, boolean, float)

### 2.3 Platform Service Interfaces
- [x] `ImageProcessor` - load, save, transform, crop, resize images
- [x] `FaceDetector` - detect face landmarks
- [x] `VideoEncoder` - encode frames to video
- [x] `CameraController` - camera capture control

### 2.4 Use Cases
- [x] **Project Management**
  - [x] `CreateProjectUseCase`
  - [x] `GetProjectsUseCase`
  - [x] `GetProjectUseCase`
  - [x] `DeleteProjectUseCase`
  - [x] `UpdateProjectSettingsUseCase`

- [x] **Frame Management**
  - [x] `AddFrameUseCase`
  - [x] `GetFramesUseCase`
  - [x] `GetLatestFrameUseCase`
  - [x] `DeleteFrameUseCase`
  - [x] `ImportPhotosUseCase`

- [x] **Face Processing**
  - [x] `DetectFaceUseCase`
  - [x] `AlignFaceUseCase`
  - [x] `CalculateAlignmentMatrixUseCase`
  - [x] `ValidateAlignmentUseCase`

- [x] **Video Export**
  - [x] `CompileVideoUseCase`
  - [x] `ExportGifUseCase`

- [x] **Capture Flow**
  - [x] `CaptureImageUseCase`

---

## Phase 3: Platform Layer (expect/actual) ✅

### 3.1 Camera Interface
- [x] Define `CameraController` expect class
  - [x] startPreview, stopPreview
  - [x] captureImage
  - [x] switchCamera (front/back)
  - [x] setFlashMode

- [x] **Android Implementation**
  - [x] Implement with CameraX
  - [x] Handle lifecycle binding
  - [x] Configure ImageCapture use case
  - [x] Handle rotation/orientation

- [x] **iOS Implementation**
  - [x] Implement with AVFoundation
  - [x] Configure AVCaptureSession
  - [x] Handle AVCapturePhotoOutput
  - [x] Manage capture device

### 3.2 Face Detection Interface
- [x] Define `FaceDetector` expect class
  - [x] detectFace(imageBytes): FaceLandmarks?
  - [x] detectFaceRealtime(frame): FaceLandmarks?

- [x] **Android Implementation**
  - [x] Integrate MediaPipe Face Landmarker
  - [x] Configure for 478 landmarks
  - [x] Optimize for real-time detection
  - [x] Handle image format conversion

- [x] **iOS Implementation**
  - [x] Integrate Vision Framework
  - [x] Use VNDetectFaceLandmarksRequest
  - [x] Map Vision landmarks to common format
  - [x] Handle CIImage conversion

### 3.3 Image Processor Interface
- [x] Define `ImageProcessor` expect class
  - [x] applyAffineTransform(image, matrix): ByteArray
  - [x] cropImage(image, rect): ByteArray
  - [x] resizeImage(image, size): ByteArray
  - [x] saveImage(bytes, path)

- [x] **Android Implementation**
  - [x] Use Android Bitmap and Matrix
  - [x] Implement hardware-accelerated transforms
  - [x] Handle EXIF orientation

- [x] **iOS Implementation**
  - [x] Use CoreGraphics
  - [x] Implement CGAffineTransform operations
  - [x] Handle image orientation metadata

### 3.4 Video Encoder Interface
- [x] Define `VideoEncoder` expect class
  - [x] encode(frames, settings, progressCallback): String
  - [x] cancel()
  - [x] getSupportedCodecs(): List<Codec>

- [x] **Android Implementation**
  - [x] Implement with MediaCodec + MediaMuxer
  - [x] Configure Surface input
  - [x] Support H.264/HEVC encoding
  - [x] Handle frame timing

- [x] **iOS Implementation**
  - [x] Implement with AVAssetWriter
  - [x] Use AVAssetWriterInputPixelBufferAdaptor
  - [x] Configure video settings
  - [x] Handle frame presentation times

### 3.5 File System Interface
- [x] Define `FileManager` expect class
  - [x] getAppDirectory(): String
  - [x] getProjectDirectory(projectId): String
  - [x] deleteFile(path)
  - [x] fileExists(path): Boolean

- [x] **Android/iOS Implementations**
  - [x] Map to platform file systems
  - [x] Handle app sandbox directories

---

## Phase 4: Data Layer ✅

### 4.1 Local Data Sources
- [x] `ProjectLocalDataSource` - SQLDelight queries
- [x] `FrameLocalDataSource` - SQLDelight queries
- [x] `SettingsLocalDataSource` - key-value storage

### 4.2 Repository Implementations
- [x] `ProjectRepositoryImpl`
- [x] `FrameRepositoryImpl`
- [x] `SettingsRepositoryImpl`

### 4.3 File Storage
- [x] Implement image file management (ImageStorageManager)
- [x] Implement video file management (VideoStorageManager)
- [x] Add thumbnail generation (ThumbnailGenerator)
- [x] Handle storage cleanup (StorageCleanupManager)

---

## Phase 5: Presentation Layer ✅

### 5.1 ViewModels
- [x] `MainViewModel` - app-level state, navigation
- [x] `ProjectListViewModel` - project list, create/delete
- [x] `CaptureViewModel` - camera state, capture flow
- [x] `GalleryViewModel` - frame list, selection, deletion
- [x] `ExportViewModel` - export settings, progress
- [x] `SettingsViewModel` - app preferences

### 5.2 UI State Classes
- [x] `ProjectListState`
- [x] `CaptureState`
- [x] `GalleryState`
- [x] `ExportState`
- [x] `SettingsState`

### 5.3 UI Events
- [x] Define sealed classes for one-time events
- [x] Implement event channels (SharedFlow)

---

## Phase 6: UI Layer (Compose Multiplatform) ✅

### 6.1 Theme & Design System
- [x] Define color scheme (light/dark)
- [x] Define typography
- [x] Create custom shapes
- [x] Build reusable components:
  - [x] `AppBars` - Top app bar, navigation
  - [x] `ProjectCard` - Project list item
  - [x] `FrameGridItem` - Frame thumbnail in gallery
  - [x] `Dialogs` - Create project, confirm delete
  - [x] `LoadingIndicator`
  - [x] `ProgressIndicators`
  - [x] `EmptyState` - Empty list state
  - [x] `ErrorDisplay` - Error states
  - [x] `SettingsItems` - Settings list items
  - [x] `PermissionDenied` - Permission denial screen

### 6.2 Navigation
- [x] Set up navigation framework (Navigation Compose)
- [x] Define navigation routes:
  - [x] ProjectList (home)
  - [x] Capture
  - [x] Gallery
  - [x] Export
  - [x] Settings
- [x] Implement navigation transitions (AppNavHost, AppNavController, Route)

### 6.3 Screens
- [x] **Project List Screen**
  - [x] Project cards with thumbnail and frame count
  - [x] Create new project FAB
  - [x] Delete project (swipe or long press)
  - [x] Navigate to capture/gallery

- [x] **Capture Screen**
  - [x] Camera preview (platform view)
  - [ ] Ghost image overlay with opacity slider
  - [x] Grid overlay toggle
  - [x] Capture button
  - [x] Flash toggle
  - [x] Camera flip button
  - [ ] Last captured frame thumbnail

- [x] **Gallery Screen**
  - [x] Grid view of all frames
  - [x] Frame selection mode
  - [x] Delete selected frames
  - [ ] Reorder frames (drag and drop)
  - [ ] Frame detail view
  - [ ] Play preview button

- [x] **Export Screen**
  - [x] Resolution selector (480p-4K)
  - [x] FPS selector (24/30/60)
  - [x] Codec selector (H.264/HEVC)
  - [ ] Date range filter
  - [x] Export progress indicator
  - [ ] Share/save options

- [x] **Settings Screen**
  - [x] Default project settings
  - [ ] Reminder notifications
  - [ ] Storage management
  - [x] About/version info

### 6.4 Platform Views
- [x] **Camera Preview Composable**
  - [x] Android: Wrap PreviewView (CameraX)
  - [x] iOS: Wrap AVCaptureVideoPreviewLayer
- [x] **Camera Permission Handling**
  - [x] Android: Activity Result API
  - [x] iOS: AVCaptureDevice authorization

---

## Phase 7: Core Features Implementation

### 7.1 Photo Capture Flow
- [x] Initialize camera with permissions
- [x] Display real-time preview
- [ ] Overlay ghost image from previous capture
- [x] Capture photo on button press
- [ ] Run face detection on captured image
- [ ] Calculate alignment matrix
- [ ] Apply transformation and crop
- [ ] Save original and aligned images
- [ ] Update database with frame metadata
- [ ] Update ghost image for next capture

### 7.2 Photo Import Flow
- [ ] Open system photo picker
- [ ] Support multiple selection
- [ ] Process each photo:
  - [ ] Detect face
  - [ ] Align and crop
  - [ ] Save to project directory
- [ ] Show progress for batch imports
- [ ] Handle photos without detectable faces

### 7.3 Face Alignment Algorithm
- [ ] Extract eye center positions from landmarks
- [ ] Calculate reference eye positions (normalized coordinates)
- [ ] Compute rotation angle from eye positions
- [ ] Compute scale factor for consistent face size
- [ ] Build affine transformation matrix
- [ ] Apply transformation with anti-aliasing
- [ ] Crop to remove black borders
- [ ] Validate alignment quality

### 7.4 Video Compilation Flow
- [ ] Load all frames sorted by date
- [ ] Filter by date range if specified
- [ ] Initialize video encoder with settings
- [ ] Feed frames at specified FPS
- [ ] Report progress via callback
- [ ] Finalize and save video file
- [ ] Generate thumbnail
- [ ] Return video path for sharing

---

## Phase 8: Advanced Features

### 8.1 Real-time Alignment Preview
- [ ] Run face detection on camera frames
- [ ] Show alignment guide overlay
- [ ] Indicate when face is properly positioned
- [ ] Show confidence indicator

### 8.2 Reminder Notifications
- [ ] Schedule daily notifications
- [ ] Allow custom reminder time
- [ ] Deep link to capture screen
- [ ] Handle notification permissions

### 8.3 Video Preview
- [ ] Play timelapse preview before export
- [ ] Adjustable playback speed
- [ ] Scrubbing/seeking

### 8.4 Filters & Effects
- [ ] Black & white filter
- [ ] Sepia filter
- [ ] Brightness/contrast adjustment
- [ ] Apply filter preview

### 8.5 Date/Text Overlay
- [ ] Add date stamp to frames
- [ ] Custom text overlay
- [ ] Font and position options

---

## Phase 9: Testing

### 9.1 Unit Tests
- [ ] Domain layer use cases
- [ ] Alignment algorithm calculations
- [ ] Repository implementations
- [ ] ViewModel state management

### 9.2 Integration Tests
- [ ] Database operations
- [ ] File storage operations
- [ ] End-to-end capture flow

### 9.3 UI Tests
- [ ] Screen navigation
- [ ] User interactions
- [ ] State changes

### 9.4 Platform Tests
- [ ] Android instrumented tests
- [ ] iOS XCTest integration

---

## Phase 10: Polish & Release

### 10.1 Performance Optimization
- [ ] Profile and optimize face detection
- [ ] Optimize video encoding speed
- [ ] Reduce memory usage for large projects
- [ ] Implement lazy loading for galleries

### 10.2 Error Handling
- [x] Graceful camera permission denial
- [ ] Handle storage full scenarios
- [ ] Face detection failure recovery
- [ ] Export error handling

### 10.3 Accessibility
- [ ] Content descriptions for images
- [ ] Screen reader support
- [ ] Touch target sizes
- [ ] Color contrast compliance

### 10.4 Localization
- [ ] Extract strings to resources
- [ ] Support multiple languages
- [ ] Handle RTL layouts

### 10.5 Release Preparation
- [ ] App icons and splash screen
- [ ] Store listings (descriptions, screenshots)
- [ ] Privacy policy
- [ ] Build release configurations
- [ ] Code signing (Android keystore, iOS certificates)
- [ ] Beta testing (TestFlight, Play Console)

---

## Progress Summary

| Phase | Status | Progress |
|-------|--------|----------|
| 1. Project Foundation | Complete | 100% |
| 2. Domain Layer | Complete | 100% |
| 3. Platform Layer | Complete | 100% |
| 4. Data Layer | Complete | 100% |
| 5. Presentation Layer | Complete | 100% |
| 6. UI Layer | Complete | 90% |
| 7. Core Features | In Progress | 30% |
| 8. Advanced Features | Not Started | 0% |
| 9. Testing | Not Started | 0% |
| 10. Polish & Release | Not Started | 0% |

---

## Milestones

### MVP (Minimum Viable Product)
- [ ] Phases 1-7 complete
- [ ] Basic capture and export working
- [ ] Single project support
- [ ] Manual alignment fallback

### Beta Release
- [ ] Phase 8 partially complete (real-time preview, reminders)
- [ ] Phase 9 (core tests passing)
- [ ] Multi-project support
- [ ] Stable face alignment

### Production Release
- [ ] All phases complete
- [ ] Performance optimized
- [ ] Full test coverage
- [ ] Store-ready builds

---

## Notes

- Prioritize Android implementation first, then iOS
- Keep platform-specific code minimal
- Test face alignment with diverse photos early
- Monitor app size (target <50MB)
- Consider privacy: all processing is local, no cloud uploads
