package com.po4yka.framelapse.testutil

import com.po4yka.framelapse.domain.entity.AlignmentMatrix
import com.po4yka.framelapse.domain.entity.BoundingBox
import com.po4yka.framelapse.domain.entity.FeatureDetectorType
import com.po4yka.framelapse.domain.entity.FeatureKeypoint
import com.po4yka.framelapse.domain.entity.HomographyMatrix
import com.po4yka.framelapse.domain.entity.LandscapeLandmarks
import com.po4yka.framelapse.domain.entity.Project
import com.po4yka.framelapse.domain.service.FeatureMatchResult
import com.po4yka.framelapse.domain.service.FeatureMatcher
import com.po4yka.framelapse.domain.service.ImageData
import com.po4yka.framelapse.domain.service.ImageProcessor
import com.po4yka.framelapse.domain.util.Result
import com.po4yka.framelapse.platform.currentTimeMillis
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * Fake implementation that mimics GetProjectsUseCase behavior for testing.
 * Note: We use composition instead of inheritance since use cases are final classes.
 */
class FakeGetProjectsUseCase {

    private val projects = MutableStateFlow<List<Project>>(emptyList())

    var shouldFail = false
    var failureException: Throwable = RuntimeException("Fake use case error")

    fun setProjects(projectList: List<Project>) {
        projects.value = projectList.sortedByDescending { it.updatedAt }
    }

    fun clear() {
        projects.value = emptyList()
    }

    suspend operator fun invoke(): Result<List<Project>> {
        if (shouldFail) return Result.Error(failureException)
        return Result.Success(projects.value)
    }

    fun observe(): Flow<List<Project>> = projects.map { it.sortedByDescending { p -> p.updatedAt } }
}

/**
 * Fake implementation that mimics CreateProjectUseCase behavior for testing.
 * Note: We use composition instead of inheritance since use cases are final classes.
 */
class FakeCreateProjectUseCase {

    private val createdProjects = mutableListOf<Project>()
    private var projectIdCounter = 0

    var shouldFail = false
    var failureException: Throwable = RuntimeException("Fake use case error")
    var validateName = true

    fun getCreatedProjects(): List<Project> = createdProjects.toList()

    fun clear() {
        createdProjects.clear()
        projectIdCounter = 0
    }

    suspend operator fun invoke(name: String, fps: Int = 30): Result<Project> {
        if (validateName && name.isBlank()) {
            return Result.Error(
                IllegalArgumentException("Project name cannot be empty"),
                "Project name cannot be empty",
            )
        }

        if (fps !in 1..60) {
            return Result.Error(
                IllegalArgumentException("FPS must be between 1 and 60"),
                "FPS must be between 1 and 60",
            )
        }

        if (shouldFail) return Result.Error(failureException)

        val id = "project_${++projectIdCounter}"
        val now = currentTimeMillis()
        val project = Project(
            id = id,
            name = name.trim(),
            createdAt = now,
            updatedAt = now,
            fps = fps,
        )
        createdProjects.add(project)
        return Result.Success(project)
    }
}

/**
 * Fake implementation that mimics DeleteProjectUseCase behavior for testing.
 * Note: We use composition instead of inheritance since use cases are final classes.
 */
class FakeDeleteProjectUseCase {

    private val deletedProjectIds = mutableListOf<String>()

    var shouldFail = false
    var failureException: Throwable = RuntimeException("Fake use case error")

    fun getDeletedProjectIds(): List<String> = deletedProjectIds.toList()

    fun clear() {
        deletedProjectIds.clear()
    }

    suspend operator fun invoke(projectId: String): Result<Unit> {
        if (projectId.isBlank()) {
            return Result.Error(
                IllegalArgumentException("Project ID cannot be empty"),
                "Project ID cannot be empty",
            )
        }

        if (shouldFail) return Result.Error(failureException)

        deletedProjectIds.add(projectId)
        return Result.Success(Unit)
    }
}

/**
 * Fake implementation that mimics FileManager behavior for testing.
 * Note: FileManager is an expect class, so this provides test-only behavior.
 * This is a standalone fake, not an actual FileManager implementation.
 */
class FakeFileManagerTestHelper {

    private val existingFiles = mutableSetOf<String>()
    private val directories = mutableSetOf<String>()

    fun addExistingFile(path: String) {
        existingFiles.add(path)
    }

    fun clear() {
        existingFiles.clear()
        directories.clear()
    }

    fun getAppDirectory(): String = "/fake/app/directory"

    fun getProjectDirectory(projectId: String): String = "/fake/projects/$projectId"

    fun deleteFile(path: String): Boolean {
        existingFiles.remove(path)
        directories.remove(path)
        return true
    }

    fun fileExists(path: String): Boolean = path in existingFiles || path in directories

    fun createDirectory(path: String): Boolean {
        directories.add(path)
        return true
    }
}

/**
 * Fake implementation that mimics StorageCleanupManager behavior for testing.
 * StorageCleanupManager depends on platform-specific managers, so this fake
 * provides controllable test behavior.
 */
class FakeStorageCleanupManager {
    var storageUsageBytes: Long = 0L
    var imageBytes: Long = 0L
    var videoBytes: Long = 0L
    var thumbnailBytes: Long = 0L
    var cleanupCallCount = 0

    fun setStorageUsage(totalBytes: Long, imageBytes: Long = 0L, videoBytes: Long = 0L, thumbnailBytes: Long = 0L) {
        this.storageUsageBytes = totalBytes
        this.imageBytes = imageBytes
        this.videoBytes = videoBytes
        this.thumbnailBytes = thumbnailBytes
    }

    fun getStorageUsage(): com.po4yka.framelapse.data.storage.StorageUsage =
        com.po4yka.framelapse.data.storage.StorageUsage(
            totalBytes = storageUsageBytes,
            imageBytes = imageBytes,
            videoBytes = videoBytes,
            thumbnailBytes = thumbnailBytes,
        )

    suspend fun cleanupProject(projectId: String): com.po4yka.framelapse.domain.util.Result<Unit> {
        cleanupCallCount++
        return com.po4yka.framelapse.domain.util.Result.Success(Unit)
    }

    fun reset() {
        storageUsageBytes = 0L
        imageBytes = 0L
        videoBytes = 0L
        thumbnailBytes = 0L
        cleanupCallCount = 0
    }
}

/**
 * Fake implementation of FeatureMatcher for testing landscape alignment use cases.
 */
class FakeFeatureMatcher : FeatureMatcher {

    override var isAvailable: Boolean = true

    var shouldFail = false
    var failureException: Throwable = RuntimeException("Fake error")

    /** Configurable result for detectFeatures() */
    var detectedLandmarks: LandscapeLandmarks? = null

    /** Configurable result for matchFeatures() */
    var matchResult: List<Pair<Int, Int>> = emptyList()

    /** Configurable result for computeHomography() - pair of (matrix, inlierCount) */
    var homographyResult: Pair<HomographyMatrix, Int>? = null

    /** Configurable result for findHomography() */
    var featureMatchResult: FeatureMatchResult? = null

    /** Track method invocations */
    var detectFeaturesCallCount = 0
    var detectFeaturesFromPathCallCount = 0
    var matchFeaturesCallCount = 0
    var computeHomographyCallCount = 0
    var findHomographyCallCount = 0
    var releaseCallCount = 0

    /** Last parameters passed to methods */
    var lastDetectorType: FeatureDetectorType? = null
    var lastMaxKeypoints: Int? = null
    var lastRatioTestThreshold: Float? = null
    var lastUseCrossCheck: Boolean? = null
    var lastRansacThreshold: Float? = null
    var lastImagePath: String? = null

    override suspend fun detectFeatures(
        imageData: ImageData,
        detectorType: FeatureDetectorType,
        maxKeypoints: Int,
    ): Result<LandscapeLandmarks> {
        detectFeaturesCallCount++
        lastDetectorType = detectorType
        lastMaxKeypoints = maxKeypoints

        if (shouldFail) return Result.Error(failureException)

        return detectedLandmarks?.let { Result.Success(it) }
            ?: Result.Error(RuntimeException("No detected landmarks configured"))
    }

    override suspend fun detectFeaturesFromPath(
        imagePath: String,
        detectorType: FeatureDetectorType,
        maxKeypoints: Int,
    ): Result<LandscapeLandmarks> {
        detectFeaturesFromPathCallCount++
        lastImagePath = imagePath
        lastDetectorType = detectorType
        lastMaxKeypoints = maxKeypoints

        if (shouldFail) return Result.Error(failureException)

        return detectedLandmarks?.let { Result.Success(it) }
            ?: Result.Error(RuntimeException("No detected landmarks configured"))
    }

    override suspend fun matchFeatures(
        sourceFeatures: LandscapeLandmarks,
        referenceFeatures: LandscapeLandmarks,
        ratioTestThreshold: Float,
        useCrossCheck: Boolean,
    ): Result<List<Pair<Int, Int>>> {
        matchFeaturesCallCount++
        lastRatioTestThreshold = ratioTestThreshold
        lastUseCrossCheck = useCrossCheck

        if (shouldFail) return Result.Error(failureException)

        return Result.Success(matchResult)
    }

    override suspend fun computeHomography(
        sourceKeypoints: List<FeatureKeypoint>,
        referenceKeypoints: List<FeatureKeypoint>,
        matches: List<Pair<Int, Int>>,
        ransacThreshold: Float,
    ): Result<Pair<HomographyMatrix, Int>> {
        computeHomographyCallCount++
        lastRansacThreshold = ransacThreshold

        if (shouldFail) return Result.Error(failureException)

        return homographyResult?.let { Result.Success(it) }
            ?: Result.Error(RuntimeException("No homography result configured"))
    }

    override suspend fun findHomography(
        sourceImageData: ImageData,
        referenceImageData: ImageData,
        detectorType: FeatureDetectorType,
        maxKeypoints: Int,
        ratioTestThreshold: Float,
        ransacThreshold: Float,
    ): Result<FeatureMatchResult> {
        findHomographyCallCount++
        lastDetectorType = detectorType
        lastMaxKeypoints = maxKeypoints
        lastRatioTestThreshold = ratioTestThreshold
        lastRansacThreshold = ransacThreshold

        if (shouldFail) return Result.Error(failureException)

        return featureMatchResult?.let { Result.Success(it) }
            ?: Result.Error(RuntimeException("No feature match result configured"))
    }

    override fun release() {
        releaseCallCount++
    }

    /**
     * Resets all state to defaults for clean test setup.
     */
    fun reset() {
        isAvailable = true
        shouldFail = false
        failureException = RuntimeException("Fake error")
        detectedLandmarks = null
        matchResult = emptyList()
        homographyResult = null
        featureMatchResult = null
        detectFeaturesCallCount = 0
        detectFeaturesFromPathCallCount = 0
        matchFeaturesCallCount = 0
        computeHomographyCallCount = 0
        findHomographyCallCount = 0
        releaseCallCount = 0
        lastDetectorType = null
        lastMaxKeypoints = null
        lastRatioTestThreshold = null
        lastUseCrossCheck = null
        lastRansacThreshold = null
        lastImagePath = null
    }

    /**
     * Configures the fake to return successful detection results.
     */
    fun configureSuccessfulDetection(landmarks: LandscapeLandmarks = TestFixtures.createLandscapeLandmarks()) {
        detectedLandmarks = landmarks
    }

    /**
     * Configures the fake to return successful matching results.
     */
    fun configureSuccessfulMatching(matches: List<Pair<Int, Int>> = TestFixtures.createMatchPairs()) {
        matchResult = matches
    }

    /**
     * Configures the fake to return successful homography computation.
     */
    fun configureSuccessfulHomography(matrix: HomographyMatrix = HomographyMatrix.IDENTITY, inlierCount: Int = 15) {
        homographyResult = Pair(matrix, inlierCount)
    }

    /**
     * Configures the fake for a complete successful pipeline.
     */
    fun configureSuccessfulPipeline(
        landmarks: LandscapeLandmarks = TestFixtures.createLandscapeLandmarks(),
        matches: List<Pair<Int, Int>> = TestFixtures.createMatchPairs(),
        matrix: HomographyMatrix = HomographyMatrix.IDENTITY,
        inlierCount: Int = 15,
        confidence: Float = 0.85f,
    ) {
        configureSuccessfulDetection(landmarks)
        configureSuccessfulMatching(matches)
        configureSuccessfulHomography(matrix, inlierCount)
        featureMatchResult = FeatureMatchResult(
            homography = matrix,
            sourceLandmarks = landmarks,
            referenceLandmarks = landmarks,
            matchCount = matches.size,
            inlierCount = inlierCount,
            confidence = confidence,
        )
    }
}

/**
 * Fake implementation of ImageProcessor for testing.
 */
class FakeImageProcessor : ImageProcessor {

    var shouldFail = false
    var failureException: Throwable = RuntimeException("Fake image processor error")

    /** Configurable loaded image result */
    var loadedImage: ImageData? = ImageData(width = 1920, height = 1080, bytes = ByteArray(0))

    /** Configurable transformed image result */
    var transformedImage: ImageData? = ImageData(width = 1080, height = 1080, bytes = ByteArray(0))

    /** Track method invocations */
    var loadImageCallCount = 0
    var saveImageCallCount = 0
    var applyAffineTransformCallCount = 0
    var applyHomographyTransformCallCount = 0
    var cropImageCallCount = 0
    var resizeImageCallCount = 0
    var rotateImageCallCount = 0

    /** Last parameters passed to methods */
    var lastLoadPath: String? = null
    var lastSavePath: String? = null

    /** Specific failures for different operations */
    var loadShouldFail = false
    var saveShouldFail = false
    var transformShouldFail = false

    override suspend fun loadImage(path: String): Result<ImageData> {
        loadImageCallCount++
        lastLoadPath = path

        if (shouldFail || loadShouldFail) return Result.Error(failureException)

        return loadedImage?.let { Result.Success(it) }
            ?: Result.Error(RuntimeException("No loaded image configured"))
    }

    override suspend fun saveImage(data: ImageData, path: String, quality: Int): Result<String> {
        saveImageCallCount++
        lastSavePath = path

        if (shouldFail || saveShouldFail) return Result.Error(failureException)

        return Result.Success(path)
    }

    override suspend fun applyAffineTransform(
        image: ImageData,
        matrix: AlignmentMatrix,
        outputWidth: Int,
        outputHeight: Int,
    ): Result<ImageData> {
        applyAffineTransformCallCount++

        if (shouldFail || transformShouldFail) return Result.Error(failureException)

        return transformedImage?.let { Result.Success(it) }
            ?: Result.Success(ImageData(width = outputWidth, height = outputHeight, bytes = ByteArray(0)))
    }

    override suspend fun applyHomographyTransform(
        image: ImageData,
        matrix: HomographyMatrix,
        outputWidth: Int,
        outputHeight: Int,
    ): Result<ImageData> {
        applyHomographyTransformCallCount++

        if (shouldFail || transformShouldFail) return Result.Error(failureException)

        return transformedImage?.let { Result.Success(it) }
            ?: Result.Success(ImageData(width = outputWidth, height = outputHeight, bytes = ByteArray(0)))
    }

    override suspend fun cropImage(image: ImageData, bounds: BoundingBox): Result<ImageData> {
        cropImageCallCount++

        if (shouldFail) return Result.Error(failureException)

        return Result.Success(image)
    }

    override suspend fun resizeImage(
        image: ImageData,
        width: Int,
        height: Int,
        maintainAspectRatio: Boolean,
    ): Result<ImageData> {
        resizeImageCallCount++

        if (shouldFail) return Result.Error(failureException)

        return Result.Success(ImageData(width = width, height = height, bytes = ByteArray(0)))
    }

    override suspend fun rotateImage(image: ImageData, degrees: Float): Result<ImageData> {
        rotateImageCallCount++

        if (shouldFail) return Result.Error(failureException)

        return Result.Success(image)
    }

    override suspend fun getImageDimensions(path: String): Result<Pair<Int, Int>> {
        if (shouldFail) return Result.Error(failureException)

        return loadedImage?.let { Result.Success(Pair(it.width, it.height)) }
            ?: Result.Error(RuntimeException("No image configured"))
    }

    fun reset() {
        shouldFail = false
        loadShouldFail = false
        saveShouldFail = false
        transformShouldFail = false
        failureException = RuntimeException("Fake image processor error")
        loadedImage = ImageData(width = 1920, height = 1080, bytes = ByteArray(0))
        transformedImage = ImageData(width = 1080, height = 1080, bytes = ByteArray(0))
        loadImageCallCount = 0
        saveImageCallCount = 0
        applyAffineTransformCallCount = 0
        applyHomographyTransformCallCount = 0
        cropImageCallCount = 0
        resizeImageCallCount = 0
        rotateImageCallCount = 0
        lastLoadPath = null
        lastSavePath = null
    }
}

/**
 * Fake FileManager for testing.
 * Since FileManager is an expect class, this doesn't implement it directly,
 * but provides the same interface for test usage.
 */
class FakeFileManager {
    private val directories = mutableSetOf<String>()
    private val files = mutableSetOf<String>()

    var appDirectory: String = "/fake/app"
    var availableStorageBytes: Long = 1_000_000_000L // 1GB

    fun getProjectDirectory(projectId: String): String {
        val path = "$appDirectory/projects/$projectId"
        directories.add(path)
        return path
    }

    fun deleteFile(path: String): Boolean {
        files.remove(path)
        return true
    }

    fun fileExists(path: String): Boolean = path in files

    fun createDirectory(path: String): Boolean {
        directories.add(path)
        return true
    }

    fun addFile(path: String) {
        files.add(path)
    }

    fun reset() {
        directories.clear()
        files.clear()
        appDirectory = "/fake/app"
        availableStorageBytes = 1_000_000_000L
    }
}
