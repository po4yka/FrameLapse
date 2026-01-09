package com.po4yka.framelapse.domain.error

/**
 * Error types for storage operations.
 */
sealed class StorageError(message: String) : Exception(message) {
    /**
     * Insufficient storage space available.
     */
    class InsufficientStorage(val requiredBytes: Long, val availableBytes: Long) :
        StorageError(
            "Insufficient storage: requires $requiredBytes bytes, but only $availableBytes available",
        ) {
        /**
         * User-friendly error message.
         */
        val userMessage: String
            get() = "Not enough storage space. Need ${formatBytes(requiredBytes)}, " +
                "but only ${formatBytes(availableBytes)} available."

        private fun formatBytes(bytes: Long): String = when {
            bytes >= 1_000_000_000 -> "${(bytes / 1_000_000_000.0).roundToOneDecimal()} GB"
            bytes >= 1_000_000 -> "${(bytes / 1_000_000.0).roundToOneDecimal()} MB"
            bytes >= 1_000 -> "${(bytes / 1_000.0).roundToOneDecimal()} KB"
            else -> "$bytes bytes"
        }

        private fun Double.roundToOneDecimal(): String {
            val rounded = kotlin.math.round(this * 10) / 10
            return if (rounded == rounded.toLong().toDouble()) {
                "${rounded.toLong()}.0"
            } else {
                rounded.toString()
            }
        }
    }

    /**
     * Failed to write file to storage.
     */
    class WriteFailure(val path: String, cause: Throwable? = null) :
        StorageError(
            "Failed to write file: $path${cause?.message?.let { " - $it" } ?: ""}",
        )

    /**
     * Failed to read file from storage.
     */
    class ReadFailure(val path: String, cause: Throwable? = null) :
        StorageError(
            "Failed to read file: $path${cause?.message?.let { " - $it" } ?: ""}",
        )

    /**
     * File or directory not found.
     */
    class NotFound(val path: String) : StorageError("File not found: $path")
}
