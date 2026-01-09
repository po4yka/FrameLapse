# Changelog

All notable changes to FrameLapse will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- Comprehensive documentation suite (`docs/` folder)
- CONTRIBUTING.md for contributor guidance
- Algorithm documentation with Mermaid diagrams
- Navigation map and API reference

## [1.0.0] - 2024-XX-XX

### Added

#### Core Features
- **Photo Capture**: Built-in camera with ghost/guide overlay for consistent positioning
- **Face Detection**: ML-based facial landmark detection (478 3D landmarks)
- **Face Alignment**: Automatic affine transformation for eye position standardization
- **Video Compilation**: Hardware-accelerated encoding with H.264/HEVC support
- **Multi-project Support**: Create and manage multiple timelapse projects

#### Camera Features
- Front/back camera switching
- Flash control
- Grid overlay for manual alignment
- Adjustable ghost image opacity
- Real-time face detection feedback

#### Export Features
- Adjustable frame rate (1-60 FPS)
- Multiple resolutions (480p to 4K)
- Quality presets (Low, Medium, High, Ultra)
- Progress tracking with cancellation support
- GIF export support

#### Platform Support
- **Android**: MediaPipe face detection, CameraX, MediaCodec encoding
- **iOS**: Vision Framework detection, AVFoundation, AVAssetWriter encoding

#### Data Management
- SQLDelight database for frame and project metadata
- Local-first storage (no cloud dependency)
- Batch import from device gallery
- Frame selection, reordering, and deletion

#### Accessibility
- Full screen reader support with content descriptions
- WCAG AA color contrast compliance
- Touch target optimization (48dp minimum)
- Localization-ready string resources

#### Developer Experience
- Clean Architecture with Unidirectional Data Flow
- Comprehensive test coverage with fakes and fixtures
- Spotless code formatting with ktlint
- CI/CD with GitHub Actions

### Technical

#### Architecture
- Kotlin Multiplatform (KMP) with shared business logic
- Compose Multiplatform for UI
- Koin for dependency injection
- Navigation 3 for screen management
- StateFlow/SharedFlow for reactive state

#### Algorithms
- Multi-pass stabilization (FAST/SLOW modes)
- Affine transformation matrix calculation
- Stabilization score formula for quality metrics
- Rotation, scale, and translation refinement

---

## Version History Template

```markdown
## [X.Y.Z] - YYYY-MM-DD

### Added
- New features

### Changed
- Changes to existing functionality

### Deprecated
- Features to be removed in future versions

### Removed
- Features removed in this version

### Fixed
- Bug fixes

### Security
- Security-related changes
```

---

## Links

- [Latest Release](https://github.com/po4yka/FrameLapse/releases/latest)
- [All Releases](https://github.com/po4yka/FrameLapse/releases)
- [Compare Changes](https://github.com/po4yka/FrameLapse/compare)

[Unreleased]: https://github.com/po4yka/FrameLapse/compare/v1.0.0...HEAD
[1.0.0]: https://github.com/po4yka/FrameLapse/releases/tag/v1.0.0
