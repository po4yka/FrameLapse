package com.po4yka.framelapse.domain.service

/**
 * Interface for sharing and opening files using platform-specific mechanisms.
 * Platform implementations will use:
 * - Android: Intent.ACTION_SEND / Intent.ACTION_VIEW
 * - iOS: UIActivityViewController
 */
interface ShareHandler {

    /**
     * Share a file using the system share sheet.
     *
     * @param filePath Path to the file to share.
     * @param mimeType MIME type of the file (e.g., "video/mp4").
     */
    suspend fun shareFile(filePath: String, mimeType: String)

    /**
     * Open a file using the system default app.
     *
     * @param filePath Path to the file to open.
     * @param mimeType MIME type of the file (e.g., "video/mp4").
     */
    suspend fun openFile(filePath: String, mimeType: String)
}
