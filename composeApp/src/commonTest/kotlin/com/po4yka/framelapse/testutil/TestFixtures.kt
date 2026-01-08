package com.po4yka.framelapse.testutil

import com.po4yka.framelapse.domain.entity.AlignmentMatrix
import com.po4yka.framelapse.domain.entity.AlignmentSettings
import com.po4yka.framelapse.domain.entity.BoundingBox
import com.po4yka.framelapse.domain.entity.DateRange
import com.po4yka.framelapse.domain.entity.EarlyStopReason
import com.po4yka.framelapse.domain.entity.ExportQuality
import com.po4yka.framelapse.domain.entity.ExportSettings
import com.po4yka.framelapse.domain.entity.FaceLandmarks
import com.po4yka.framelapse.domain.entity.Frame
import com.po4yka.framelapse.domain.entity.LandmarkPoint
import com.po4yka.framelapse.domain.entity.Orientation
import com.po4yka.framelapse.domain.entity.OvershootCorrection
import com.po4yka.framelapse.domain.entity.Project
import com.po4yka.framelapse.domain.entity.Resolution
import com.po4yka.framelapse.domain.entity.StabilizationMode
import com.po4yka.framelapse.domain.entity.StabilizationPass
import com.po4yka.framelapse.domain.entity.StabilizationResult
import com.po4yka.framelapse.domain.entity.StabilizationScore
import com.po4yka.framelapse.domain.entity.StabilizationSettings
import com.po4yka.framelapse.domain.entity.StabilizationStage
import com.po4yka.framelapse.domain.entity.VideoCodec

/**
 * Test data factory for creating domain entities with sensible defaults.
 */
object TestFixtures {

    private var projectIdCounter = 0
    private var frameIdCounter = 0

    fun resetCounters() {
        projectIdCounter = 0
        frameIdCounter = 0
    }

    // ==================== Project ====================

    fun createProject(
        id: String = "project_${++projectIdCounter}",
        name: String = "Test Project",
        createdAt: Long = 1704067200000L, // 2024-01-01 00:00:00 UTC
        updatedAt: Long = createdAt,
        fps: Int = 30,
        resolution: Resolution = Resolution.HD_1080P,
        orientation: Orientation = Orientation.PORTRAIT,
        thumbnailPath: String? = null,
    ): Project = Project(
        id = id,
        name = name,
        createdAt = createdAt,
        updatedAt = updatedAt,
        fps = fps,
        resolution = resolution,
        orientation = orientation,
        thumbnailPath = thumbnailPath,
    )

    // ==================== Frame ====================

    fun createFrame(
        id: String = "frame_${++frameIdCounter}",
        projectId: String = "project_1",
        originalPath: String = "/storage/frames/$id.jpg",
        alignedPath: String? = null,
        timestamp: Long = System.currentTimeMillis(),
        capturedAt: Long = timestamp,
        confidence: Float? = null,
        landmarks: FaceLandmarks? = null,
        sortOrder: Int = 0,
    ): Frame = Frame(
        id = id,
        projectId = projectId,
        originalPath = originalPath,
        alignedPath = alignedPath,
        timestamp = timestamp,
        capturedAt = capturedAt,
        confidence = confidence,
        landmarks = landmarks,
        sortOrder = sortOrder,
    )

    fun createFrameWithAlignment(
        id: String = "frame_${++frameIdCounter}",
        projectId: String = "project_1",
        confidence: Float = 0.95f,
    ): Frame = createFrame(
        id = id,
        projectId = projectId,
        alignedPath = "/storage/aligned/$id.jpg",
        confidence = confidence,
        landmarks = createFaceLandmarks(),
    )

    // ==================== FaceLandmarks ====================

    /**
     * Creates face landmarks with pixel coordinates.
     * Default values use realistic pixel coordinates for a typical face detection.
     */
    fun createFaceLandmarks(
        leftEyeCenter: LandmarkPoint = LandmarkPoint(150f, 200f, 0f),
        rightEyeCenter: LandmarkPoint = LandmarkPoint(350f, 200f, 0f),
        noseTip: LandmarkPoint = LandmarkPoint(250f, 300f, 0f),
        boundingBox: BoundingBox = BoundingBox(50f, 50f, 450f, 500f),
    ): FaceLandmarks = FaceLandmarks(
        points = createLandmarkPoints(leftEyeCenter, rightEyeCenter, noseTip),
        leftEyeCenter = leftEyeCenter,
        rightEyeCenter = rightEyeCenter,
        noseTip = noseTip,
        boundingBox = boundingBox,
    )

    fun createLandmarkPoint(x: Float = 0.5f, y: Float = 0.5f, z: Float = 0f): LandmarkPoint = LandmarkPoint(x, y, z)

    fun createBoundingBox(
        left: Float = 0.1f,
        top: Float = 0.1f,
        right: Float = 0.9f,
        bottom: Float = 0.9f,
    ): BoundingBox = BoundingBox(left, top, right, bottom)

    private fun createLandmarkPoints(
        leftEye: LandmarkPoint,
        rightEye: LandmarkPoint,
        nose: LandmarkPoint,
    ): List<LandmarkPoint> {
        // Create minimal set of landmark points for testing
        // In real usage, this would be 478 points
        return buildList {
            add(leftEye)
            add(rightEye)
            add(nose)
            // Fill remaining points with interpolated values
            repeat(FaceLandmarks.LANDMARK_COUNT - 3) { i ->
                val t = i.toFloat() / FaceLandmarks.LANDMARK_COUNT
                add(LandmarkPoint(t, t, 0f))
            }
        }
    }

    // ==================== AlignmentSettings ====================

    fun createAlignmentSettings(
        minConfidence: Float = AlignmentSettings.DEFAULT_MIN_CONFIDENCE,
        targetEyeDistance: Float = AlignmentSettings.DEFAULT_TARGET_EYE_DISTANCE,
        outputSize: Int = AlignmentSettings.DEFAULT_OUTPUT_SIZE,
        verticalOffset: Float = AlignmentSettings.DEFAULT_VERTICAL_OFFSET,
    ): AlignmentSettings = AlignmentSettings(
        minConfidence = minConfidence,
        targetEyeDistance = targetEyeDistance,
        outputSize = outputSize,
        verticalOffset = verticalOffset,
    )

    // ==================== AlignmentMatrix ====================

    fun createAlignmentMatrix(
        scaleX: Float = 1f,
        skewX: Float = 0f,
        translateX: Float = 0f,
        skewY: Float = 0f,
        scaleY: Float = 1f,
        translateY: Float = 0f,
    ): AlignmentMatrix = AlignmentMatrix(
        scaleX = scaleX,
        skewX = skewX,
        translateX = translateX,
        skewY = skewY,
        scaleY = scaleY,
        translateY = translateY,
    )

    fun createIdentityMatrix(): AlignmentMatrix = createAlignmentMatrix()

    // ==================== ExportSettings ====================

    fun createExportSettings(
        resolution: Resolution = Resolution.HD_1080P,
        fps: Int = 30,
        codec: VideoCodec = VideoCodec.H264,
        quality: ExportQuality = ExportQuality.HIGH,
        dateRange: DateRange? = null,
    ): ExportSettings = ExportSettings(
        resolution = resolution,
        fps = fps,
        codec = codec,
        quality = quality,
        dateRange = dateRange,
    )

    // ==================== DateRange ====================

    fun createDateRange(
        startTimestamp: Long = 1704067200000L, // 2024-01-01
        endTimestamp: Long = 1706745600000L, // 2024-02-01
    ): DateRange = DateRange(startTimestamp, endTimestamp)

    // ==================== Lists ====================

    fun createProjectList(count: Int = 3): List<Project> =
        (1..count).map { createProject(id = "project_$it", name = "Project $it") }

    fun createFrameList(count: Int = 5, projectId: String = "project_1"): List<Frame> = (1..count).map { index ->
        createFrame(
            id = "frame_$index",
            projectId = projectId,
            sortOrder = index - 1,
            timestamp = 1704067200000L + (index * 86400000L), // Each frame 1 day apart
        )
    }

    // ==================== Edge Cases ====================

    /** Creates landmarks with eyes perfectly horizontal (no rotation needed) */
    fun createHorizontalEyesLandmarks(): FaceLandmarks = createFaceLandmarks(
        leftEyeCenter = LandmarkPoint(150f, 200f, 0f),
        rightEyeCenter = LandmarkPoint(350f, 200f, 0f),
    )

    /** Creates landmarks with eyes at 45 degrees (tilted) */
    fun createTiltedEyesLandmarks(): FaceLandmarks = createFaceLandmarks(
        leftEyeCenter = LandmarkPoint(150f, 250f, 0f),
        rightEyeCenter = LandmarkPoint(350f, 150f, 0f),
    )

    /** Creates landmarks with very close eyes (small face) - still > MIN_EYE_DISTANCE (10f) */
    fun createSmallFaceLandmarks(): FaceLandmarks = createFaceLandmarks(
        leftEyeCenter = LandmarkPoint(220f, 200f, 0f),
        rightEyeCenter = LandmarkPoint(280f, 200f, 0f), // 60px distance
        boundingBox = BoundingBox(200f, 175f, 300f, 325f),
    )

    /** Creates landmarks with far apart eyes (large face) */
    fun createLargeFaceLandmarks(): FaceLandmarks = createFaceLandmarks(
        leftEyeCenter = LandmarkPoint(50f, 200f, 0f),
        rightEyeCenter = LandmarkPoint(450f, 200f, 0f), // 400px distance
        boundingBox = BoundingBox(0f, 0f, 500f, 500f),
    )

    // ==================== Stabilization ====================

    fun createStabilizationSettings(
        mode: StabilizationMode = StabilizationMode.FAST,
        rotationStopThreshold: Float = 0.1f,
        scaleErrorThreshold: Float = 1.0f,
        convergenceThreshold: Float = 0.05f,
        successScoreThreshold: Float = 20.0f,
        noActionScoreThreshold: Float = 0.5f,
    ): StabilizationSettings = StabilizationSettings(
        mode = mode,
        rotationStopThreshold = rotationStopThreshold,
        scaleErrorThreshold = scaleErrorThreshold,
        convergenceThreshold = convergenceThreshold,
        successScoreThreshold = successScoreThreshold,
        noActionScoreThreshold = noActionScoreThreshold,
    )

    fun createStabilizationScore(
        value: Float = 5.0f,
        leftEyeDistance: Float = 2.5f,
        rightEyeDistance: Float = 2.5f,
    ): StabilizationScore = StabilizationScore(
        value = value,
        leftEyeDistance = leftEyeDistance,
        rightEyeDistance = rightEyeDistance,
    )

    fun createStabilizationPass(
        passNumber: Int = 1,
        stage: StabilizationStage = StabilizationStage.INITIAL,
        scoreBefore: Float = 100f,
        scoreAfter: Float = 10f,
        converged: Boolean = false,
        durationMs: Long = 50L,
    ): StabilizationPass = StabilizationPass(
        passNumber = passNumber,
        stage = stage,
        scoreBefore = scoreBefore,
        scoreAfter = scoreAfter,
        converged = converged,
        durationMs = durationMs,
    )

    fun createStabilizationResult(
        success: Boolean = true,
        finalScore: StabilizationScore = createStabilizationScore(),
        passesExecuted: Int = 3,
        passes: List<StabilizationPass> = listOf(createStabilizationPass()),
        mode: StabilizationMode = StabilizationMode.FAST,
        earlyStopReason: EarlyStopReason? = EarlyStopReason.SCORE_BELOW_THRESHOLD,
        totalDurationMs: Long = 150L,
        initialScore: Float = 100f,
        goalEyeDistance: Float = 150f,
    ): StabilizationResult = StabilizationResult(
        success = success,
        finalScore = finalScore,
        passesExecuted = passesExecuted,
        passes = passes,
        mode = mode,
        earlyStopReason = earlyStopReason,
        totalDurationMs = totalDurationMs,
        initialScore = initialScore,
        goalEyeDistance = goalEyeDistance,
    )

    fun createOvershootCorrection(
        leftEyeOvershootX: Float = 5f,
        leftEyeOvershootY: Float = 2f,
        rightEyeOvershootX: Float = 5f,
        rightEyeOvershootY: Float = 2f,
        needsCorrection: Boolean = true,
    ): OvershootCorrection = OvershootCorrection(
        leftEyeOvershootX = leftEyeOvershootX,
        leftEyeOvershootY = leftEyeOvershootY,
        rightEyeOvershootX = rightEyeOvershootX,
        rightEyeOvershootY = rightEyeOvershootY,
        needsCorrection = needsCorrection,
    )

    /** Creates a frame with stabilization result */
    fun createFrameWithStabilization(
        id: String = "frame_${++frameIdCounter}",
        projectId: String = "project_1",
        confidence: Float = 0.95f,
        stabilizationResult: StabilizationResult = createStabilizationResult(),
    ): Frame = createFrame(
        id = id,
        projectId = projectId,
        alignedPath = "/storage/aligned/$id.jpg",
        confidence = confidence,
        landmarks = createFaceLandmarks(),
    ).copy(stabilizationResult = stabilizationResult)
}
