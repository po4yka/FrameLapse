# FrameLapse Review Findings and Improvement Roadmap

This document captures the review findings from a deep pass on the current codebase and proposes
an improvement roadmap tailored for an ML-powered KMP application.

## Findings

### High Severity

1) Recursive deletion is not guaranteed for project directories
- Evidence:
  - `composeApp/src/commonMain/kotlin/com/po4yka/framelapse/data/storage/ImageStorageManager.kt:152`
  - `composeApp/src/commonMain/kotlin/com/po4yka/framelapse/data/storage/ImageStorageManager.kt:185`
  - `composeApp/src/commonMain/kotlin/com/po4yka/framelapse/data/storage/VideoStorageManager.kt:64`
  - `composeApp/src/commonMain/kotlin/com/po4yka/framelapse/data/storage/StorageCleanupManager.kt:34`
- Why it matters: Non-empty directories are typically not deleted by simple delete calls, leaving
  orphaned images/videos and wasting storage.
- Recommendation: Implement a recursive delete in `FileManager` or a dedicated directory walker
  in shared code, then use it consistently for `frames/`, `aligned/`, `thumbnails/`, and `exports/`.

2) Cleanup responsibilities are duplicated and inconsistent
- Evidence:
  - `composeApp/src/commonMain/kotlin/com/po4yka/framelapse/domain/usecase/project/DeleteProjectUseCase.kt:39`
  - `composeApp/src/commonMain/kotlin/com/po4yka/framelapse/domain/usecase/project/DeleteProjectUseCase.kt:75`
  - `composeApp/src/commonMain/kotlin/com/po4yka/framelapse/data/repository/ProjectRepositoryImpl.kt:73`
- Why it matters: Two cleanup paths run at different layers with different failure semantics. A
  single file deletion failure can abort the use case even though repository cleanup would also
  run, resulting in inconsistent behavior and harder-to-debug failures.
- Recommendation: Centralize project cleanup in a single owner (preferably `StorageCleanupManager`)
  and make the use case delegate to it; remove duplicate deletion logic in the other layer.

### Medium Severity

3) Aligned-image landmark detection errors are silently ignored
- Evidence:
  - `composeApp/src/commonMain/kotlin/com/po4yka/framelapse/domain/usecase/face/AlignFaceUseCase.kt:115`
- Why it matters: If the platform detector fails (e.g., regression, memory pressure, invalid
  image format), the failure is swallowed and replaced with synthetic landmarks, masking failures
  and making quality regressions harder to detect.
- Recommendation: Propagate a structured warning or error for detection failures on aligned images
  (e.g., include a diagnostic flag in `StabilizationResult` or use a distinct error type), while
  still allowing the pipeline to proceed if you want best-effort behavior.

4) Media storage paths are inconsistent across capture and alignment flows
- Evidence:
  - `composeApp/src/commonMain/kotlin/com/po4yka/framelapse/domain/usecase/capture/CaptureImageUseCase.kt:47`
  - `composeApp/src/commonMain/kotlin/com/po4yka/framelapse/domain/usecase/face/AlignFaceUseCase.kt:102`
  - `composeApp/src/commonMain/kotlin/com/po4yka/framelapse/data/storage/ImageStorageManager.kt:47`
- Why it matters: Capture and aligned images are saved in the project root, while storage
  utilities expect `frames/` and `aligned/`. This increases the risk of orphaned files and makes
  cleanup inconsistent.
- Recommendation: Route all save/delete paths through a single storage API (e.g., `ImageStorageManager`)
  with explicit `frames/` and `aligned/` directories.

## Improvement Roadmap

### Phase 1: Storage and Cleanup Reliability (1-2 weeks)
Goals: prevent storage leaks, ensure consistent cleanup, avoid orphaned media.
- Implement recursive delete support (shared helper or platform `FileManager`).
- Make `StorageCleanupManager` the single owner of cleanup logic.
- Route all image/video paths through storage managers (capture, alignment, export).
- Add a lightweight storage audit/debug tool (e.g., list orphaned files) for QA.

### Phase 2: ML Pipeline Robustness (2-3 weeks)
Goals: make ML failures observable, standardize algorithm steps across modes.
- Introduce a pipeline abstraction: `Stage` (detect → validate → align → postprocess → persist).
- Add structured diagnostics to alignment outputs (e.g., detector failure reason, fallback used).
- Standardize alignment settings and progress reporting across face/body/landscape modes.

### Phase 3: Architecture Hardening for KMP ML (3-4 weeks)
Goals: improved separation, consistent shared APIs, cleaner platform boundaries.
- Split shared code into modules: `core`, `domain`, `data`, `ml`, `presentation`, `ui`.
- Create a shared `MediaStore` abstraction that owns naming, paths, and cleanup.
- Introduce a `ModelCapabilities` contract to make availability and quality explicit.
- Add a background processing boundary (`ProcessingQueue`) with platform implementations.

### Phase 4: Performance and Test Coverage (2-3 weeks)
Goals: stabilize performance and validate regressions across platforms.
- Add perf baselines for alignment time and export throughput.
- Add test coverage for cleanup, storage paths, and alignment diagnostics.
- Add platform integration tests for detector availability and fallback behavior.
