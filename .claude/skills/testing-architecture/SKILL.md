---
name: testing-architecture
description: Implements comprehensive testing patterns including fakes, test fixtures, Turbine for Flows, and coverage strategies. Use when improving test coverage or adding tests for architecture components.
---

# Testing Architecture

## Overview

This skill helps implement comprehensive testing patterns for Clean Architecture components. It covers fake implementations, test fixtures, Flow testing with Turbine, and ViewModel testing strategies.

## Testing Pyramid

```
        ┌─────────────┐
        │   UI/E2E    │  Few, slow, comprehensive
        │    Tests    │
        ├─────────────┤
        │ Integration │  Some, medium speed
        │    Tests    │
        ├─────────────┤
        │    Unit     │  Many, fast, focused
        │    Tests    │
        └─────────────┘
```

## 1. Fake Repository Pattern

### Fake Implementation

```kotlin
// commonTest/kotlin/.../fake/FakeFrameRepository.kt
class FakeFrameRepository : FrameRepository {

    private val frames = mutableListOf<Frame>()
    private val framesFlow = MutableStateFlow<List<Frame>>(emptyList())

    // Control behavior in tests
    var shouldFail = false
    var failureMessage = "Test failure"
    var addDelay: Long = 0L

    override suspend fun addFrame(frame: Frame): Result<Frame> {
        if (addDelay > 0) delay(addDelay)
        if (shouldFail) {
            return Result.Error(Exception(failureMessage), failureMessage)
        }
        frames.add(frame)
        framesFlow.value = frames.toList()
        return Result.Success(frame)
    }

    override suspend fun getFrame(id: String): Result<Frame> {
        if (shouldFail) {
            return Result.Error(Exception(failureMessage), failureMessage)
        }
        val frame = frames.find { it.id == id }
            ?: return Result.Error(Exception("Not found"), "Frame not found")
        return Result.Success(frame)
    }

    override suspend fun getFramesByProject(projectId: String): Result<List<Frame>> {
        if (shouldFail) {
            return Result.Error(Exception(failureMessage), failureMessage)
        }
        return Result.Success(frames.filter { it.projectId == projectId })
    }

    override suspend fun getLatestFrame(projectId: String): Result<Frame?> {
        if (shouldFail) {
            return Result.Error(Exception(failureMessage), failureMessage)
        }
        return Result.Success(
            frames.filter { it.projectId == projectId }
                .maxByOrNull { it.timestamp }
        )
    }

    override suspend fun deleteFrame(id: String): Result<Unit> {
        if (shouldFail) {
            return Result.Error(Exception(failureMessage), failureMessage)
        }
        frames.removeAll { it.id == id }
        framesFlow.value = frames.toList()
        return Result.Success(Unit)
    }

    override fun observeFrames(projectId: String): Flow<List<Frame>> {
        return framesFlow.map { list -> list.filter { it.projectId == projectId } }
    }

    // Test helpers
    fun clear() {
        frames.clear()
        framesFlow.value = emptyList()
    }

    fun seed(vararg testFrames: Frame) {
        frames.addAll(testFrames)
        framesFlow.value = frames.toList()
    }

    fun getAll(): List<Frame> = frames.toList()
}
```

## 2. Test Fixtures

### Entity Fixtures

```kotlin
// commonTest/kotlin/.../fixtures/FrameFixtures.kt
object FrameFixtures {

    fun frame(
        id: String = UUID.randomUUID().toString(),
        projectId: String = "test-project",
        originalPath: String = "/test/frames/$id.jpg",
        alignedPath: String? = null,
        timestamp: Long = System.currentTimeMillis(),
        confidence: Float? = null,
        sortOrder: Int = 0,
    ) = Frame(
        id = id,
        projectId = projectId,
        originalPath = originalPath,
        alignedPath = alignedPath,
        timestamp = timestamp,
        confidence = confidence,
        sortOrder = sortOrder,
    )

    fun alignedFrame(
        base: Frame = frame(),
        confidence: Float = 0.95f,
    ) = base.copy(
        alignedPath = "/test/aligned/${base.id}.jpg",
        confidence = confidence,
    )

    fun frameList(
        projectId: String = "test-project",
        count: Int = 5,
    ): List<Frame> = (0 until count).map { index ->
        frame(
            id = "frame-$index",
            projectId = projectId,
            timestamp = System.currentTimeMillis() + index * 1000,
            sortOrder = index,
        )
    }
}

object ProjectFixtures {

    fun project(
        id: String = UUID.randomUUID().toString(),
        name: String = "Test Project",
        createdAt: Long = System.currentTimeMillis(),
        frameCount: Int = 0,
    ) = Project(
        id = id,
        name = name,
        createdAt = createdAt,
        frameCount = frameCount,
    )
}

object LandmarksFixtures {

    fun faceLandmarks(
        leftEye: LandmarkPoint = LandmarkPoint(100f, 100f),
        rightEye: LandmarkPoint = LandmarkPoint(200f, 100f),
        nose: LandmarkPoint = LandmarkPoint(150f, 150f),
        confidence: Float = 0.95f,
    ) = FaceLandmarks(
        leftEye = leftEye,
        rightEye = rightEye,
        nose = nose,
        confidence = confidence,
    )
}
```

## 3. Use Case Testing

### Basic Use Case Test

```kotlin
// commonTest/kotlin/.../usecase/GetProjectUseCaseTest.kt
class GetProjectUseCaseTest {

    private lateinit var repository: FakeProjectRepository
    private lateinit var useCase: GetProjectUseCase

    @BeforeTest
    fun setup() {
        repository = FakeProjectRepository()
        useCase = GetProjectUseCase(repository)
    }

    @Test
    fun `invoke returns project when exists`() = runTest {
        // Given
        val project = ProjectFixtures.project(id = "project-1", name = "My Project")
        repository.seed(project)

        // When
        val result = useCase("project-1")

        // Then
        assertTrue(result.isSuccess)
        assertEquals("My Project", result.getOrNull()?.name)
    }

    @Test
    fun `invoke returns error when project not found`() = runTest {
        // Given - empty repository

        // When
        val result = useCase("non-existent")

        // Then
        assertTrue(result.isError)
        assertContains(result.errorMessage ?: "", "not found")
    }

    @Test
    fun `invoke returns error when projectId is blank`() = runTest {
        // When
        val result = useCase("")

        // Then
        assertTrue(result.isError)
        assertContains(result.errorMessage ?: "", "Invalid")
    }
}
```

### Use Case with Dependencies Test

```kotlin
// commonTest/kotlin/.../usecase/AlignFaceUseCaseTest.kt
class AlignFaceUseCaseTest {

    private lateinit var faceDetector: FakeFaceDetector
    private lateinit var imageProcessor: FakeImageProcessor
    private lateinit var frameRepository: FakeFrameRepository
    private lateinit var fileManager: FakeFileManager
    private lateinit var useCase: AlignFaceUseCase

    @BeforeTest
    fun setup() {
        faceDetector = FakeFaceDetector()
        imageProcessor = FakeImageProcessor()
        frameRepository = FakeFrameRepository()
        fileManager = FakeFileManager()

        useCase = AlignFaceUseCase(
            faceDetector = faceDetector,
            imageProcessor = imageProcessor,
            frameRepository = frameRepository,
            fileManager = fileManager,
            multiPassStabilization = FakeMultiPassStabilization(),
            validateAlignment = ValidateAlignmentUseCase(),
        )
    }

    @Test
    fun `invoke aligns frame when face detected`() = runTest {
        // Given
        val frame = FrameFixtures.frame()
        faceDetector.detectedLandmarks = LandmarksFixtures.faceLandmarks()

        // When
        val result = useCase(frame)

        // Then
        assertTrue(result.isSuccess)
        assertNotNull(result.getOrNull()?.alignedPath)
        assertNotNull(result.getOrNull()?.confidence)
    }

    @Test
    fun `invoke returns error when no face detected`() = runTest {
        // Given
        val frame = FrameFixtures.frame()
        faceDetector.shouldDetect = false

        // When
        val result = useCase(frame)

        // Then
        assertTrue(result.isError)
        assertContains(result.errorMessage ?: "", "face")
    }
}
```

## 4. ViewModel Testing with Turbine

### State Testing

```kotlin
// commonTest/kotlin/.../presentation/CaptureViewModelTest.kt
class CaptureViewModelTest {

    private lateinit var captureImageUseCase: FakeCaptureImageUseCase
    private lateinit var getLatestFrameUseCase: FakeGetLatestFrameUseCase
    private lateinit var getFramesUseCase: FakeGetFramesUseCase
    private lateinit var settingsRepository: FakeSettingsRepository
    private lateinit var viewModel: CaptureViewModel

    @BeforeTest
    fun setup() {
        captureImageUseCase = FakeCaptureImageUseCase()
        getLatestFrameUseCase = FakeGetLatestFrameUseCase()
        getFramesUseCase = FakeGetFramesUseCase()
        settingsRepository = FakeSettingsRepository()

        viewModel = CaptureViewModel(
            captureImageUseCase = captureImageUseCase,
            getLatestFrameUseCase = getLatestFrameUseCase,
            getFramesUseCase = getFramesUseCase,
            settingsRepository = settingsRepository,
        )
    }

    @Test
    fun `initialize sets projectId and loads frames`() = runTest {
        // Given
        getFramesUseCase.result = Result.Success(FrameFixtures.frameList(count = 3))

        viewModel.state.test {
            // Initial state
            val initial = awaitItem()
            assertEquals("", initial.projectId)
            assertEquals(0, initial.frameCount)

            // When
            viewModel.onEvent(CaptureEvent.Initialize("project-1"))

            // Then
            val updated = awaitItem()
            assertEquals("project-1", updated.projectId)
            assertEquals(3, updated.frameCount)
        }
    }

    @Test
    fun `captureImage updates state and sends effect on success`() = runTest {
        // Given
        val capturedFrame = FrameFixtures.alignedFrame()
        captureImageUseCase.result = Result.Success(capturedFrame)
        viewModel.onEvent(CaptureEvent.Initialize("project-1"))

        viewModel.state.test {
            skipItems(2) // Skip initial and initialize states

            viewModel.effect.test {
                // When
                viewModel.onEvent(CaptureEvent.CaptureImage)

                // Then - check state
                val processing = awaitItem()
                assertTrue(processing.isProcessing)

                val complete = awaitItem()
                assertFalse(complete.isProcessing)
                assertEquals(capturedFrame, complete.lastCapturedFrame)

                // Then - check effect
                val effect = awaitItem()
                assertTrue(effect is CaptureEffect.PlayCaptureSound)
            }
        }
    }

    @Test
    fun `captureImage shows error on failure`() = runTest {
        // Given
        captureImageUseCase.result = Result.Error(Exception("Test"), "Capture failed")
        viewModel.onEvent(CaptureEvent.Initialize("project-1"))

        viewModel.state.test {
            skipItems(2)

            viewModel.effect.test {
                // When
                viewModel.onEvent(CaptureEvent.CaptureImage)

                // Skip processing state
                skipItems(1)

                // Then - check state has error
                val errorState = awaitItem()
                assertFalse(errorState.isProcessing)
                assertEquals("Capture failed", errorState.error)

                // Then - check error effect
                val effect = awaitItem()
                assertTrue(effect is CaptureEffect.ShowError)
            }
        }
    }
}
```

## 5. Flow Testing Patterns

### Testing Flow Emissions

```kotlin
@Test
fun `observeFrames emits updated list when frame added`() = runTest {
    // Given
    val repository = FakeFrameRepository()

    repository.observeFrames("project-1").test {
        // Initial empty
        assertEquals(emptyList(), awaitItem())

        // When - add frame
        val frame = FrameFixtures.frame(projectId = "project-1")
        repository.addFrame(frame)

        // Then
        val updated = awaitItem()
        assertEquals(1, updated.size)
        assertEquals(frame.id, updated.first().id)

        cancelAndIgnoreRemainingEvents()
    }
}
```

### Testing Combined Flows

```kotlin
@Test
fun `combined state updates from multiple sources`() = runTest {
    // Given
    val projectRepo = FakeProjectRepository()
    val frameRepo = FakeFrameRepository()
    val viewModel = GalleryViewModel(projectRepo, frameRepo)

    viewModel.state.test {
        // Initial loading
        assertTrue(awaitItem().content is GalleryContent.Loading)

        // Setup data
        projectRepo.seed(ProjectFixtures.project(id = "p1"))
        frameRepo.seed(FrameFixtures.frame(projectId = "p1"))

        // Initialize
        viewModel.onEvent(GalleryEvent.Initialize("p1"))

        // Should get success state
        val success = awaitItem()
        assertTrue(success.content is GalleryContent.Success)

        cancelAndIgnoreRemainingEvents()
    }
}
```

## 6. Test Organization

### Test File Structure

```
composeApp/src/
├── commonTest/kotlin/com/po4yka/framelapse/
│   ├── domain/
│   │   └── usecase/
│   │       ├── GetProjectUseCaseTest.kt
│   │       ├── CreateProjectUseCaseTest.kt
│   │       ├── AlignFaceUseCaseTest.kt
│   │       └── ...
│   ├── presentation/
│   │   ├── CaptureViewModelTest.kt
│   │   ├── GalleryViewModelTest.kt
│   │   └── ...
│   ├── fake/
│   │   ├── FakeFrameRepository.kt
│   │   ├── FakeProjectRepository.kt
│   │   ├── FakeFaceDetector.kt
│   │   └── ...
│   └── fixtures/
│       ├── FrameFixtures.kt
│       ├── ProjectFixtures.kt
│       └── LandmarksFixtures.kt
└── androidUnitTest/kotlin/com/po4yka/framelapse/
    └── platform/
        └── FaceDetectorImplTest.kt  (MockK for Android deps)
```

## 7. Android-Specific Testing (MockK)

### Testing with MockK

```kotlin
// androidUnitTest/kotlin/.../presentation/CaptureViewModelTest.kt
class CaptureViewModelAndroidTest {

    @MockK
    private lateinit var captureImageUseCase: CaptureImageUseCase

    private lateinit var viewModel: CaptureViewModel

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        viewModel = CaptureViewModel(
            captureImageUseCase = captureImageUseCase,
            // ... other mocks
        )
    }

    @Test
    fun `capture calls use case with correct parameters`() = runTest {
        // Given
        coEvery { captureImageUseCase(any()) } returns Result.Success(mockk())

        // When
        viewModel.onEvent(CaptureEvent.CaptureImage)
        advanceUntilIdle()

        // Then
        coVerify { captureImageUseCase("project-1") }
    }
}
```

## Reference Examples

- Use case tests: `composeApp/src/commonTest/kotlin/com/po4yka/framelapse/domain/usecase/`
- Test fixtures: Create in `composeApp/src/commonTest/kotlin/com/po4yka/framelapse/fixtures/`
- Fakes: Create in `composeApp/src/commonTest/kotlin/com/po4yka/framelapse/fake/`

## Checklist

### Fakes
- [ ] Implement all interface methods
- [ ] Add control flags (shouldFail, delay)
- [ ] Add test helpers (seed, clear, getAll)
- [ ] Use StateFlow for observable fakes

### Fixtures
- [ ] Factory functions with sensible defaults
- [ ] All required parameters have defaults
- [ ] Builders for complex variations

### Use Case Tests
- [ ] Test success path
- [ ] Test error paths
- [ ] Test input validation
- [ ] Test edge cases (empty, null)

### ViewModel Tests
- [ ] Test initial state
- [ ] Test each event handler
- [ ] Test state transitions
- [ ] Test effects are emitted
- [ ] Use Turbine for Flow testing
