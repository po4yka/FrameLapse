package com.po4yka.framelapse.domain.service

import com.po4yka.framelapse.domain.entity.AlignmentMatrix
import com.po4yka.framelapse.domain.entity.BoundingBox
import com.po4yka.framelapse.domain.util.Result

/**
 * Interface for platform-specific image processing operations.
 *
 * Implementations will use:
 * - Android: android.graphics.Bitmap, android.graphics.Matrix
 * - iOS: CoreGraphics, CGAffineTransform
 */
interface ImageProcessor {

    /**
     * Loads an image from the file system.
     *
     * @param path Absolute path to the image file.
     * @return Result containing the ImageData or an error.
     */
    suspend fun loadImage(path: String): Result<ImageData>

    /**
     * Saves image data to a file.
     *
     * @param data The image data to save.
     * @param path Destination file path.
     * @param quality JPEG quality (0-100), ignored for PNG.
     * @return Result containing the saved file path or an error.
     */
    suspend fun saveImage(data: ImageData, path: String, quality: Int = 90): Result<String>

    /**
     * Applies an affine transformation to an image.
     *
     * @param image The source image data.
     * @param matrix The transformation matrix.
     * @param outputWidth Output image width.
     * @param outputHeight Output image height.
     * @return Result containing the transformed ImageData or an error.
     */
    suspend fun applyAffineTransform(
        image: ImageData,
        matrix: AlignmentMatrix,
        outputWidth: Int,
        outputHeight: Int,
    ): Result<ImageData>

    /**
     * Crops an image to the specified bounds.
     *
     * @param image The source image data.
     * @param bounds The crop region.
     * @return Result containing the cropped ImageData or an error.
     */
    suspend fun cropImage(image: ImageData, bounds: BoundingBox): Result<ImageData>

    /**
     * Resizes an image to the specified dimensions.
     *
     * @param image The source image data.
     * @param width Target width.
     * @param height Target height.
     * @param maintainAspectRatio If true, maintains aspect ratio (may not match exact dimensions).
     * @return Result containing the resized ImageData or an error.
     */
    suspend fun resizeImage(
        image: ImageData,
        width: Int,
        height: Int,
        maintainAspectRatio: Boolean = true,
    ): Result<ImageData>

    /**
     * Rotates an image by the specified degrees.
     *
     * @param image The source image data.
     * @param degrees Rotation angle (clockwise).
     * @return Result containing the rotated ImageData or an error.
     */
    suspend fun rotateImage(image: ImageData, degrees: Float): Result<ImageData>

    /**
     * Gets the dimensions of an image file without fully loading it.
     *
     * @param path Path to the image file.
     * @return Result containing width and height as a Pair.
     */
    suspend fun getImageDimensions(path: String): Result<Pair<Int, Int>>
}
