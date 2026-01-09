# Quick Start Guide

Get FrameLapse running in 5 minutes.

## Prerequisites Checklist

Before starting, ensure you have:

- [ ] **JDK 17+** - Check with `java -version`
- [ ] **Android Studio** (latest stable) - For Android development
- [ ] **Xcode 15+** - For iOS development (macOS only)
- [ ] **Git** - Check with `git --version`

## Step 1: Clone the Repository

```shell
git clone https://github.com/po4yka/FrameLapse.git
cd FrameLapse
```

## Step 2: Open in Android Studio

1. Launch Android Studio
2. Select **File > Open**
3. Navigate to the cloned `FrameLapse` directory
4. Click **Open**
5. Wait for Gradle sync to complete (may take a few minutes on first run)

## Step 3: Build and Run

### Android (Recommended First Build)

```shell
# Build debug APK
./gradlew :composeApp:assembleDebug
```

Or in Android Studio:
1. Select run configuration: **composeApp**
2. Select target device/emulator
3. Click **Run** (green play button)

### iOS

**Option A: Xcode**
1. Open `/iosApp/iosApp.xcworkspace` in Xcode
2. Select target simulator (iPhone 15 Pro recommended)
3. Click **Run**

**Option B: Android Studio with KMM Plugin**
1. Install "Kotlin Multiplatform Mobile" plugin
2. Select iOS run configuration
3. Click **Run**

## Step 4: Verify Setup

Run the test suite to confirm everything works:

```shell
# All tests
./gradlew test

# Just Android tests
./gradlew :composeApp:testDebugUnitTest
```

You should see `BUILD SUCCESSFUL` with all tests passing.

## Project Structure Overview

```
FrameLapse/
├── composeApp/          # Main app (UI, ViewModels)
│   ├── commonMain/      # Shared code (80%+ of codebase)
│   ├── androidMain/     # Android implementations
│   └── iosMain/         # iOS implementations
├── domain/              # Business logic, use cases
├── data/                # Repositories, database
├── core/                # Utilities
└── iosApp/              # iOS entry point
```

## Common Commands

| Task | Command |
|------|---------|
| Build Android debug | `./gradlew :composeApp:assembleDebug` |
| Build Android release | `./gradlew :composeApp:assembleRelease` |
| Run all tests | `./gradlew test` |
| Check code style | `./gradlew spotlessCheck` |
| Fix code style | `./gradlew spotlessApply` |
| Run lint | `./gradlew :composeApp:lintDebug` |
| Clean build | `./gradlew clean` |
| Check dependencies | `./gradlew dependencyUpdates` |

## First-Time Issues

### Gradle Sync Fails

**Symptom**: Android Studio shows sync errors

**Solutions**:
1. File > Invalidate Caches > Invalidate and Restart
2. Delete `.gradle` and `build` directories, re-sync
3. Ensure JDK 17+ is set in Android Studio preferences

### iOS Build Fails

**Symptom**: Xcode build errors

**Solutions**:
```shell
# Install CocoaPods
brew install cocoapods

# Install pods
cd iosApp && pod install

# If pod install fails
pod repo update && pod install
```

### Out of Memory Errors

**Symptom**: Build fails with heap space error

**Solution**: Increase Gradle memory in `gradle.properties`:
```properties
org.gradle.jvmargs=-Xmx6144M -Dfile.encoding=UTF-8
```

### MediaPipe Model Not Found (Android)

**Symptom**: Face detection crashes at runtime

**Solution**: Ensure the MediaPipe model is in the correct location:
```
composeApp/src/androidMain/assets/face_landmarker.task
```

## Next Steps

Now that you're set up:

1. **Explore the codebase**: Start with `composeApp/src/commonMain/`
2. **Read the architecture**: See [Architecture.md](../Architecture.md)
3. **Understand algorithms**: See [ALGORITHMS.md](./ALGORITHMS.md)
4. **Make a contribution**: See [CONTRIBUTING.md](../CONTRIBUTING.md)

## Need Help?

- [Troubleshooting Guide](./TROUBLESHOOTING.md) - Common issues and solutions
- [API Reference](./API_REFERENCE.md) - Use cases and services
- [GitHub Issues](https://github.com/po4yka/FrameLapse/issues) - Report bugs
