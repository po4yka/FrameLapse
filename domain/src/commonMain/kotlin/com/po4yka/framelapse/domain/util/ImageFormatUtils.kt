package com.po4yka.framelapse.domain.util

/**
 * Utility object for handling image format detection and validation.
 */
object ImageFormatUtils {

    /**
     * Set of supported image file extensions (lowercase).
     */
    val SUPPORTED_EXTENSIONS = setOf(
        // Standard formats
        "jpg",
        "jpeg",
        "png",
        // HEIC family (common on iOS)
        "heic",
        "heif",
        // AVIF (modern efficient format)
        "avif",
    )

    /**
     * MIME type to file extension mapping.
     */
    private val MIME_TO_EXTENSION = mapOf(
        "image/jpeg" to "jpg",
        "image/png" to "png",
        "image/heic" to "heic",
        "image/heif" to "heic",
        "image/avif" to "avif",
    )

    /**
     * Checks if the given file path has a supported image extension.
     *
     * @param path The file path to check
     * @return true if the extension is supported, false otherwise
     */
    fun isSupported(path: String): Boolean {
        val extension = getExtension(path)
        return extension in SUPPORTED_EXTENSIONS
    }

    /**
     * Extracts the file extension from a path (lowercase).
     *
     * @param path The file path
     * @return The extension without the dot, or empty string if none
     */
    fun getExtension(path: String): String = path.substringAfterLast('.', "").lowercase()

    /**
     * Gets the appropriate file extension for a MIME type.
     *
     * @param mimeType The MIME type (e.g., "image/heic")
     * @return The file extension (e.g., "heic"), or "jpg" as default
     */
    fun getExtensionForMimeType(mimeType: String?): String = MIME_TO_EXTENSION[mimeType] ?: "jpg"

    /**
     * Checks if the format requires special handling (HEIC/AVIF).
     *
     * @param path The file path
     * @return true if the format is HEIC or AVIF
     */
    fun isModernFormat(path: String): Boolean {
        val extension = getExtension(path)
        return extension in setOf("heic", "heif", "avif")
    }

    /**
     * Checks if the format is HEIC/HEIF.
     *
     * @param path The file path
     * @return true if the format is HEIC or HEIF
     */
    fun isHeic(path: String): Boolean {
        val extension = getExtension(path)
        return extension in setOf("heic", "heif")
    }

    /**
     * Checks if the format is AVIF.
     *
     * @param path The file path
     * @return true if the format is AVIF
     */
    fun isAvif(path: String): Boolean = getExtension(path) == "avif"
}
