# Contributing to FrameLapse

Thank you for your interest in contributing to FrameLapse! This document provides guidelines and instructions for contributing to the project.

## Table of Contents

- [Code of Conduct](#code-of-conduct)
- [Development Setup](#development-setup)
- [Project Structure](#project-structure)
- [Branch Naming](#branch-naming)
- [Commit Messages](#commit-messages)
- [Pull Request Process](#pull-request-process)
- [Code Style](#code-style)
- [Testing Requirements](#testing-requirements)
- [Running CI Locally](#running-ci-locally)

## Code of Conduct

- Be respectful and inclusive in all communications
- Focus on constructive feedback
- Help maintain a welcoming environment for all contributors

## Development Setup

### Prerequisites

| Tool | Version | Purpose |
|------|---------|---------|
| JDK | 17+ | Kotlin/JVM compilation |
| Android Studio | Latest stable | Android development, IDE |
| Xcode | 15+ | iOS development |
| CocoaPods | Latest | iOS dependency management |
| Git | 2.x+ | Version control |

### Initial Setup

1. **Clone the repository:**
   ```shell
   git clone https://github.com/po4yka/FrameLapse.git
   cd FrameLapse
   ```

2. **Open in Android Studio:**
   - File > Open > Select project root
   - Wait for Gradle sync to complete

3. **For iOS development:**
   ```shell
   # Install CocoaPods if needed
   brew install cocoapods

   # Navigate to iOS app and install pods
   cd iosApp
   pod install
   ```

4. **Verify setup:**
   ```shell
   # Build Android debug
   ./gradlew :composeApp:assembleDebug

   # Run tests
   ./gradlew test
   ```

## Project Structure

```
/
├── composeApp/           # Main application module
│   ├── commonMain/       # Shared Kotlin code
│   ├── androidMain/      # Android implementations
│   └── iosMain/          # iOS implementations
├── core/                 # Shared utilities
├── domain/               # Business logic (use cases, entities)
├── data/                 # Repositories, data sources
├── platform/             # Platform abstractions
├── test-utils/           # Test fixtures and fakes
└── iosApp/               # iOS entry point
```

See [Architecture.md](./Architecture.md) for detailed architectural documentation.

## Branch Naming

Use descriptive branch names with prefixes:

| Prefix | Purpose | Example |
|--------|---------|---------|
| `feature/` | New features | `feature/export-gif-format` |
| `fix/` | Bug fixes | `fix/alignment-rotation-error` |
| `refactor/` | Code refactoring | `refactor/video-encoder-cleanup` |
| `docs/` | Documentation changes | `docs/update-readme` |
| `test/` | Test additions/fixes | `test/add-capture-usecase-tests` |
| `chore/` | Maintenance tasks | `chore/update-dependencies` |

## Commit Messages

Follow the conventional commit format:

```
<type>(<scope>): <description>

[optional body]

[optional footer]
```

### Types

| Type | Description |
|------|-------------|
| `feat` | New feature |
| `fix` | Bug fix |
| `refactor` | Code refactoring (no functional changes) |
| `test` | Adding or updating tests |
| `docs` | Documentation changes |
| `style` | Code style changes (formatting, etc.) |
| `chore` | Maintenance tasks |
| `perf` | Performance improvements |

### Examples

```
feat(capture): add flash toggle for front camera

fix(alignment): correct rotation calculation for landscape images

refactor(domain): extract common alignment logic to shared utility

test(export): add unit tests for CompileVideoUseCase
```

## Pull Request Process

### Before Submitting

1. **Ensure your code builds:**
   ```shell
   ./gradlew :composeApp:assembleDebug
   ```

2. **Run all tests:**
   ```shell
   ./gradlew test
   ```

3. **Run static analysis:**
   ```shell
   ./gradlew spotlessCheck :composeApp:lintDebug
   ```

4. **Fix formatting issues:**
   ```shell
   ./gradlew spotlessApply
   ```

### PR Guidelines

1. **Title**: Use conventional commit format
2. **Description**: Include:
   - Summary of changes
   - Related issue (if applicable)
   - Testing performed
   - Screenshots (for UI changes)

3. **Size**: Keep PRs focused and reasonably sized
   - Large changes should be split into smaller PRs
   - Each PR should address a single concern

### Review Process

1. All PRs require at least one approval
2. CI checks must pass
3. Address review comments promptly
4. Squash commits before merging (if requested)

## Code Style

### Kotlin Style

The project uses **Spotless** with **ktlint** for code formatting:

- Configuration: `.editorconfig`
- Max line length: 120 characters
- Indentation: 4 spaces

### Key Conventions

```kotlin
// Use data classes for state
data class CaptureState(
    val isProcessing: Boolean = false,
    val frameCount: Int = 0,
)

// Use sealed classes for events/effects
sealed interface CaptureEvent {
    data class Initialize(val projectId: String) : CaptureEvent
    data object CaptureImage : CaptureEvent
}

// Use Result<T> for error handling
suspend fun execute(): Result<Frame> {
    return try {
        Result.Success(/* data */)
    } catch (e: Exception) {
        Result.Error(e, "Operation failed")
    }
}

// Prefer extension functions for utilities
fun FaceLandmarks.getEyeCenter(): Point {
    return Point(
        x = (leftEyeCenter.x + rightEyeCenter.x) / 2,
        y = (leftEyeCenter.y + rightEyeCenter.y) / 2,
    )
}
```

### File Organization

- One class per file (generally)
- Group related files in packages
- Place interfaces next to implementations
- Use `internal` visibility for module-private code

## Testing Requirements

### Test Coverage

All new code should include appropriate tests:

| Layer | Test Type | Location |
|-------|-----------|----------|
| Domain | Unit tests | `domain/src/commonTest/` |
| Data | Integration tests | `data/src/commonTest/` |
| Presentation | ViewModel tests | `composeApp/src/commonTest/` |
| Android-specific | Android unit tests | `composeApp/src/androidUnitTest/` |

### Test Patterns

```kotlin
// Use descriptive test names
@Test
fun `invoke returns error when projectId is blank`() = runTest {
    val result = useCase("")
    assertTrue(result is Result.Error)
}

// Use Turbine for Flow testing
@Test
fun `state updates when frames are loaded`() = runTest {
    viewModel.state.test {
        viewModel.onEvent(LoadFrames)
        assertEquals(expectedFrames, awaitItem().frames)
    }
}

// Use fake implementations for dependencies
class FakeFrameRepository : FrameRepository {
    var frames = mutableListOf<Frame>()
    override suspend fun getFrames(projectId: String) = frames.filter { it.projectId == projectId }
}
```

### Running Tests

```shell
# All tests
./gradlew test

# Specific module
./gradlew :domain:test

# Android unit tests only
./gradlew :composeApp:testDebugUnitTest

# With coverage
./gradlew :composeApp:koverVerify
```

## Running CI Locally

You can run the same checks that CI performs:

```shell
# Full CI check
./gradlew spotlessCheck :composeApp:lintDebug test :composeApp:assembleDebug

# Or run individual checks:

# 1. Code formatting
./gradlew spotlessCheck

# 2. Android lint
./gradlew :composeApp:lintDebug

# 3. Unit tests
./gradlew test

# 4. Build
./gradlew :composeApp:assembleDebug
```

### Fixing Common Issues

| Issue | Solution |
|-------|----------|
| Spotless failures | Run `./gradlew spotlessApply` |
| Lint warnings | Check lint report in `composeApp/build/reports/lint/` |
| Test failures | Run specific test with `--info` flag for details |

## Questions?

If you have questions about contributing:

1. Check existing documentation in `/docs/`
2. Review similar PRs for patterns
3. Open a discussion or issue on GitHub

Thank you for contributing to FrameLapse!
