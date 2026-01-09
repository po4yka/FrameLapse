package com.po4yka.framelapse.domain.usecase.alignment

import com.po4yka.framelapse.domain.entity.AlignmentSettings
import com.po4yka.framelapse.domain.entity.BodyAlignmentSettings
import com.po4yka.framelapse.domain.entity.ContentType
import com.po4yka.framelapse.domain.entity.Frame
import com.po4yka.framelapse.domain.entity.LandscapeAlignmentSettings
import com.po4yka.framelapse.domain.entity.MuscleAlignmentSettings
import com.po4yka.framelapse.domain.entity.MuscleRegion
import com.po4yka.framelapse.domain.entity.StabilizationProgress
import com.po4yka.framelapse.domain.usecase.body.AlignBodyUseCase
import com.po4yka.framelapse.domain.usecase.face.AlignFaceUseCase
import com.po4yka.framelapse.domain.usecase.landscape.AlignLandscapeUseCase
import com.po4yka.framelapse.domain.usecase.muscle.AlignMuscleUseCase
import com.po4yka.framelapse.domain.util.Result

/**
 * Unified alignment dispatcher that routes to the appropriate alignment use case
 * based on content type.
 *
 * This use case serves as the entry point for all alignment operations, providing
 * a consistent API regardless of whether the content is face-based, body-based, or muscle-focused.
 *
 * ## Supported Content Types
 * - FACE: Eye-based alignment using FaceDetector (head-centered framing)
 * - BODY: Shoulder-based alignment using BodyPoseDetector (head-to-waist framing)
 * - MUSCLE: Body alignment + region cropping for fitness tracking
 * - LANDSCAPE: Feature matching for scenery/architecture using OpenCV
 */
class AlignContentUseCase(
    private val alignFace: AlignFaceUseCase,
    private val alignBody: AlignBodyUseCase,
    private val alignMuscle: AlignMuscleUseCase,
    private val alignLandscape: AlignLandscapeUseCase,
) {
    /**
     * Aligns content in the given frame based on the specified content type.
     *
     * @param frame The frame to process.
     * @param contentType The type of content for alignment (FACE, BODY, or MUSCLE).
     * @param referenceFrame Optional reference frame for goal positions.
     * @param muscleRegion Target muscle region (only used for MUSCLE content type).
     * @param onProgress Optional callback for progress updates during stabilization.
     * @return Result containing the updated Frame with alignment data.
     */
    suspend operator fun invoke(
        frame: Frame,
        contentType: ContentType,
        referenceFrame: Frame? = null,
        muscleRegion: MuscleRegion? = null,
        onProgress: ((StabilizationProgress) -> Unit)? = null,
    ): Result<Frame> = when (contentType) {
        ContentType.FACE -> alignFace(
            frame = frame,
            referenceFrame = referenceFrame,
            onProgress = onProgress,
        )
        ContentType.BODY -> alignBody(
            frame = frame,
            referenceFrame = referenceFrame,
            onProgress = onProgress,
        )
        ContentType.MUSCLE -> alignMuscle(
            frame = frame,
            referenceFrame = referenceFrame,
            settings = MuscleAlignmentSettings(
                muscleRegion = muscleRegion ?: MuscleRegion.FULL_BODY,
            ),
            onProgress = onProgress,
        )
        ContentType.LANDSCAPE -> alignLandscape(
            frame = frame,
            referenceFrame = referenceFrame,
            settings = LandscapeAlignmentSettings(),
            onProgress = onProgress,
        )
    }

    /**
     * Aligns content with custom face alignment settings.
     *
     * @param frame The frame to process.
     * @param referenceFrame Optional reference frame for goal positions.
     * @param settings Face alignment settings.
     * @param onProgress Optional callback for progress updates.
     * @return Result containing the updated Frame.
     */
    suspend fun alignFaceWithSettings(
        frame: Frame,
        referenceFrame: Frame? = null,
        settings: AlignmentSettings = AlignmentSettings(),
        onProgress: ((StabilizationProgress) -> Unit)? = null,
    ): Result<Frame> = alignFace(
        frame = frame,
        referenceFrame = referenceFrame,
        settings = settings,
        onProgress = onProgress,
    )

    /**
     * Aligns content with custom body alignment settings.
     *
     * @param frame The frame to process.
     * @param referenceFrame Optional reference frame for goal positions.
     * @param settings Body alignment settings.
     * @param onProgress Optional callback for progress updates.
     * @return Result containing the updated Frame.
     */
    suspend fun alignBodyWithSettings(
        frame: Frame,
        referenceFrame: Frame? = null,
        settings: BodyAlignmentSettings = BodyAlignmentSettings(),
        onProgress: ((StabilizationProgress) -> Unit)? = null,
    ): Result<Frame> = alignBody(
        frame = frame,
        referenceFrame = referenceFrame,
        settings = settings,
        onProgress = onProgress,
    )

    /**
     * Aligns content with custom muscle alignment settings.
     *
     * @param frame The frame to process.
     * @param referenceFrame Optional reference frame for goal positions.
     * @param settings Muscle alignment settings including target region.
     * @param onProgress Optional callback for progress updates.
     * @return Result containing the updated Frame with muscle-cropped image.
     */
    suspend fun alignMuscleWithSettings(
        frame: Frame,
        referenceFrame: Frame? = null,
        settings: MuscleAlignmentSettings = MuscleAlignmentSettings(),
        onProgress: ((StabilizationProgress) -> Unit)? = null,
    ): Result<Frame> = alignMuscle(
        frame = frame,
        referenceFrame = referenceFrame,
        settings = settings,
        onProgress = onProgress,
    )

    /**
     * Aligns content with custom landscape alignment settings.
     *
     * @param frame The frame to process.
     * @param referenceFrame Reference frame to align to (required for landscape mode).
     * @param settings Landscape alignment settings including detector type and thresholds.
     * @param onProgress Optional callback for progress updates.
     * @return Result containing the updated Frame with homography-aligned image.
     */
    suspend fun alignLandscapeWithSettings(
        frame: Frame,
        referenceFrame: Frame?,
        settings: LandscapeAlignmentSettings = LandscapeAlignmentSettings(),
        onProgress: ((StabilizationProgress) -> Unit)? = null,
    ): Result<Frame> = alignLandscape(
        frame = frame,
        referenceFrame = referenceFrame,
        settings = settings,
        onProgress = onProgress,
    )

    /**
     * Checks if a specific content type alignment is available on this device.
     *
     * @param contentType The content type to check.
     * @return True if alignment for this content type is available.
     */
    fun isAvailable(contentType: ContentType): Boolean = when (contentType) {
        ContentType.FACE -> true // Face detection is always available
        ContentType.BODY -> true // Body detection is available on supported devices
        ContentType.MUSCLE -> true // Muscle mode uses body detection
        ContentType.LANDSCAPE -> alignLandscape.isAvailable // Requires OpenCV
    }
}
