package com.po4yka.framelapse.ui.util

import androidx.compose.runtime.Composable

/**
 * Result of a photo picker operation.
 */
sealed class PhotoPickerResult {
    /**
     * User selected photos successfully.
     *
     * @param uris List of URIs/paths to the selected photos
     */
    data class Success(val uris: List<String>) : PhotoPickerResult()

    /**
     * User cancelled the photo picker.
     */
    data object Cancelled : PhotoPickerResult()

    /**
     * An error occurred during photo selection.
     *
     * @param message Error description
     */
    data class Error(val message: String) : PhotoPickerResult()
}

/**
 * Photo picker state and launcher for selecting photos from the device gallery.
 */
interface PhotoPickerLauncher {
    /**
     * Launch the photo picker to select multiple photos.
     *
     * @param maxItems Maximum number of photos that can be selected (0 for unlimited)
     */
    fun launch(maxItems: Int = 0)
}

/**
 * Creates a remembered photo picker launcher for selecting photos from the device gallery.
 * This composable handles the platform-specific photo picker implementation.
 *
 * @param onResult Callback invoked when the user finishes selecting photos
 * @return A [PhotoPickerLauncher] that can be used to launch the photo picker
 */
@Composable
expect fun rememberPhotoPickerLauncher(onResult: (PhotoPickerResult) -> Unit): PhotoPickerLauncher
