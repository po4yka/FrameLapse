package com.po4yka.framelapse.domain.service

/**
 * Platform-agnostic representation of image data.
 */
data class ImageData(
    /** Width of the image in pixels. */
    val width: Int,
    /** Height of the image in pixels. */
    val height: Int,
    /** Raw image bytes (format depends on platform). */
    val bytes: ByteArray,
) {
    /** Aspect ratio (width / height). */
    val aspectRatio: Float
        get() = if (height > 0) width.toFloat() / height else 0f

    /** Total number of pixels. */
    val pixelCount: Int
        get() = width * height

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as ImageData

        if (width != other.width) return false
        if (height != other.height) return false
        if (!bytes.contentEquals(other.bytes)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = width
        result = 31 * result + height
        result = 31 * result + bytes.contentHashCode()
        return result
    }

    override fun toString(): String = "ImageData(width=$width, height=$height, bytes=${bytes.size})"
}
