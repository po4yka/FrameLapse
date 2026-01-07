# FrameLapse Development Roadmap

## Overview

This roadmap outlines the implementation plan for FrameLapse, a Kotlin Multiplatform application for creating stabilized daily self-portrait timelapse videos.

**Target Platforms:** Android, iOS
**Architecture:** Clean Architecture + UDF
**UI Framework:** Compose Multiplatform

---

## Phase 1: Project Foundation

### 1.1 Project Setup
- [ ] Configure Gradle with KMP plugins and version catalog
- [ ] Set up composeApp module with commonMain, androidMain, iosMain source sets
- [ ] Configure iOS app entry point in iosApp directory
- [ ] Add Compose Multiplatform dependencies
- [ ] Configure Koin for dependency injection
- [ ] Set up SQLDelight with database schema
- [ ] Add Coroutines and Flow dependencies
- [ ] Configure ktlint for code formatting
- [ ] Set up CI/CD pipeline (GitHub Actions)

### 1.2 Core Architecture Scaffolding
- [ ] Create base package structure:
  ```
  commonMain/kotlin/com/framelapse/
  ├── data/
  │   ├── local/
  │   └── repository/
  ├── domain/
  │   ├── entity/
  │   ├── repository/
  │   └── usecase/
  ├── presentation/
  │   └── viewmodel/
  ├── ui/
  │   ├── component/
  │   ├── navigation/
  │   ├── screen/
  │   └── theme/
  └── platform/
  ```
- [ ] Define expect/actual interfaces for platform capabilities
- [ ] Set up Koin modules (common, Android, iOS)
- [ ] Create base ViewModel class with StateFlow pattern
- [ ] Implement Result wrapper for error handling

### 1.3 Database Schema
- [ ] Design SQLDelight schema:
  - [ ] `Project` table (id, name, createdAt, fps, resolution, orientation)
  - [ ] `Frame` table (id, projectId, originalPath, alignedPath, timestamp, confidence, landmarks)
  - [ ] `Settings` table (key, value)
- [ ] Create database driver expect/actual
- [ ] Implement migrations strategy
- [ ] Create DAO interfaces

---

## Phase 2: Domain Layer

### 2.1 Entities
- [ ] `Project` - timelapse project with settings
- [ ] `Frame` - individual photo with metadata
- [ ] `FaceLandmarks` - 478 3D facial landmarks
- [ ] `AlignmentMatrix` - affine transformation data
- [ ] `ExportSettings` - video export configuration
- [ ] `CaptureSettings` - camera capture preferences

### 2.2 Repository Interfaces
- [ ] `ProjectRepository`
  - [ ] createProject, getProject, getAllProjects, deleteProject
  - [ ] updateProjectSettings
- [ ] `FrameRepository`
  - [ ] addFrame, getFrames, deleteFrame
  - [ ] getFrameCount, getLatestFrame
- [ ] `SettingsRepository`
  - [ ] get/set preferences (guide photo, reminder time)

### 2.3 Use Cases
- [ ] **Project Management**
  - [ ] `CreateProjectUseCase`
  - [ ] `GetProjectsUseCase`
  - [ ] `DeleteProjectUseCase`
  - [ ] `UpdateProjectSettingsUseCase`

- [ ] **Photo Capture**
  - [ ] `CaptureImageUseCase`
  - [ ] `ImportPhotosUseCase`
  - [ ] `DeleteFrameUseCase`

- [ ] **Face Processing**
  - [ ] `DetectFaceUseCase`
  - [ ] `AlignFaceUseCase`
  - [ ] `ValidateAlignmentUseCase`

- [ ] **Video Export**
  - [ ] `CompileVideoUseCase`
  - [ ] `ExportGifUseCase`
  - [ ] `GetExportProgressUseCase`

---

## Phase 3: Platform Layer (expect/actual)

### 3.1 Camera Interface
- [ ] Define `CameraController` expect class
  - [ ] startPreview, stopPreview
  - [ ] captureImage
  - [ ] switchCamera (front/back)
  - [ ] setFlashMode

- [ ] **Android Implementation**
  - [ ] Implement with CameraX
  - [ ] Handle lifecycle binding
  - [ ] Configure ImageCapture use case
  - [ ] Handle rotation/orientation

- [ ] **iOS Implementation**
  - [ ] Implement with AVFoundation
  - [ ] Configure AVCaptureSession
  - [ ] Handle AVCapturePhotoOutput
  - [ ] Manage capture device

### 3.2 Face Detection Interface
- [ ] Define `FaceDetector` expect class
  - [ ] detectFace(imageBytes): FaceLandmarks?
  - [ ] detectFaceRealtime(frame): FaceLandmarks?

- [ ] **Android Implementation**
  - [ ] Integrate MediaPipe Face Landmarker
  - [ ] Configure for 478 landmarks
  - [ ] Optimize for real-time detection
  - [ ] Handle image format conversion

- [ ] **iOS Implementation**
  - [ ] Integrate Vision Framework
  - [ ] Use VNDetectFaceLandmarksRequest
  - [ ] Map Vision landmarks to common format
  - [ ] Handle CIImage conversion

### 3.3 Image Processor Interface
- [ ] Define `ImageProcessor` expect class
  - [ ] applyAffineTransform(image, matrix): ByteArray
  - [ ] cropImage(image, rect): ByteArray
  - [ ] resizeImage(image, size): ByteArray
  - [ ] saveImage(bytes, path)

- [ ] **Android Implementation**
  - [ ] Use Android Bitmap and Matrix
  - [ ] Implement hardware-accelerated transforms
  - [ ] Handle EXIF orientation

- [ ] **iOS Implementation**
  - [ ] Use CoreGraphics
  - [ ] Implement CGAffineTransform operations
  - [ ] Handle image orientation metadata

### 3.4 Video Encoder Interface
- [ ] Define `VideoEncoder` expect class
  - [ ] encode(frames, settings, progressCallback): String
  - [ ] cancel()
  - [ ] getSupportedCodecs(): List<Codec>

- [ ] **Android Implementation**
  - [ ] Implement with MediaCodec + MediaMuxer
  - [ ] Configure Surface input
  - [ ] Support H.264/HEVC encoding
  - [ ] Handle frame timing

- [ ] **iOS Implementation**
  - [ ] Implement with AVAssetWriter
  - [ ] Use AVAssetWriterInputPixelBufferAdaptor
  - [ ] Configure video settings
  - [ ] Handle frame presentation times

### 3.5 File System Interface
- [ ] Define `FileManager` expect class
  - [ ] getAppDirectory(): String
  - [ ] getProjectDirectory(projectId): String
  - [ ] deleteFile(path)
  - [ ] fileExists(path): Boolean

- [ ] **Android/iOS Implementations**
  - [ ] Map to platform file systems
  - [ ] Handle app sandbox directories

---

## Phase 4: Data Layer

### 4.1 Local Data Sources
- [ ] `ProjectLocalDataSource` - SQLDelight queries
- [ ] `FrameLocalDataSource` - SQLDelight queries
- [ ] `SettingsLocalDataSource` - key-value storage

### 4.2 Repository Implementations
- [ ] `ProjectRepositoryImpl`
- [ ] `FrameRepositoryImpl`
- [ ] `SettingsRepositoryImpl`

### 4.3 File Storage
- [ ] Implement image file management
- [ ] Implement video file management
- [ ] Add thumbnail generation
- [ ] Handle storage cleanup

---

## Phase 5: Presentation Layer

### 5.1 ViewModels
- [ ] `MainViewModel` - app-level state, navigation
- [ ] `ProjectListViewModel` - project list, create/delete
- [ ] `CaptureViewModel` - camera state, capture flow
- [ ] `GalleryViewModel` - frame list, selection, deletion
- [ ] `ExportViewModel` - export settings, progress
- [ ] `SettingsViewModel` - app preferences

### 5.2 UI State Classes
- [ ] `ProjectListState`
- [ ] `CaptureState`
- [ ] `GalleryState`
- [ ] `ExportState`
- [ ] `SettingsState`

### 5.3 UI Events
- [ ] Define sealed classes for one-time events
- [ ] Implement event channels (SharedFlow)

---

## Phase 6: UI Layer (Compose Multiplatform)

### 6.1 Theme & Design System
- [ ] Define color scheme (light/dark)
- [ ] Define typography
- [ ] Create custom shapes
- [ ] Build reusable components:
  - [ ] `FramelapseButton`
  - [ ] `FramelapseCard`
  - [ ] `FramelapseDialog`
  - [ ] `LoadingIndicator`
  - [ ] `ProgressBar`

### 6.2 Navigation
- [ ] Set up navigation framework (Voyager or Navigation Compose)
- [ ] Define navigation routes:
  - [ ] ProjectList (home)
  - [ ] Capture
  - [ ] Gallery
  - [ ] Export
  - [ ] Settings
- [ ] Implement navigation transitions

### 6.3 Screens
- [ ] **Project List Screen**
  - [ ] Project cards with thumbnail and frame count
  - [ ] Create new project FAB
  - [ ] Delete project (swipe or long press)
  - [ ] Navigate to capture/gallery

- [ ] **Capture Screen**
  - [ ] Camera preview (platform view)
  - [ ] Ghost image overlay with opacity slider
  - [ ] Grid overlay toggle
  - [ ] Capture button
  - [ ] Flash toggle
  - [ ] Camera flip button
  - [ ] Last captured frame thumbnail

- [ ] **Gallery Screen**
  - [ ] Grid view of all frames
  - [ ] Frame selection mode
  - [ ] Delete selected frames
  - [ ] Reorder frames (drag and drop)
  - [ ] Frame detail view
  - [ ] Play preview button

- [ ] **Export Screen**
  - [ ] Resolution selector (480p-4K)
  - [ ] FPS selector (24/30/60)
  - [ ] Codec selector (H.264/HEVC)
  - [ ] Date range filter
  - [ ] Export progress indicator
  - [ ] Share/save options

- [ ] **Settings Screen**
  - [ ] Default project settings
  - [ ] Reminder notifications
  - [ ] Storage management
  - [ ] About/version info

### 6.4 Platform Views
- [ ] **Camera Preview Composable**
  - [ ] Android: Wrap PreviewView
  - [ ] iOS: Wrap AVCaptureVideoPreviewLayer

---

## Phase 7: Core Features Implementation

### 7.1 Photo Capture Flow
- [ ] Initialize camera with permissions
- [ ] Display real-time preview
- [ ] Overlay ghost image from previous capture
- [ ] Capture photo on button press
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
- [ ] Graceful camera permission denial
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
| 1. Project Foundation | Not Started | 0% |
| 2. Domain Layer | Not Started | 0% |
| 3. Platform Layer | Not Started | 0% |
| 4. Data Layer | Not Started | 0% |
| 5. Presentation Layer | Not Started | 0% |
| 6. UI Layer | Not Started | 0% |
| 7. Core Features | Not Started | 0% |
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
