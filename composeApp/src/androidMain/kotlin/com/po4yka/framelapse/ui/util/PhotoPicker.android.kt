package com.po4yka.framelapse.ui.util

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import java.io.File

/**
 * Android implementation of the photo picker using ActivityResultContracts.
 * Uses PickVisualMedia for modern photo picker experience on Android 13+
 * with fallback for older versions.
 */
@Composable
actual fun rememberPhotoPickerLauncher(onResult: (PhotoPickerResult) -> Unit): PhotoPickerLauncher {
    val context = LocalContext.current
    var pendingMaxItems by remember { mutableIntStateOf(0) }

    // Launcher for picking multiple photos
    val multiplePhotoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(),
    ) { uris: List<Uri> ->
        val limit = pendingMaxItems
        val limitedUris = if (limit > 0 && uris.size > limit) {
            uris.take(limit)
        } else {
            uris
        }

        if (limitedUris.isEmpty()) {
            onResult(PhotoPickerResult.Cancelled)
        } else {
            val paths = limitedUris.mapNotNull { uri ->
                copyUriToInternalStorage(context, uri)
            }
            if (paths.isEmpty()) {
                onResult(PhotoPickerResult.Error("Failed to copy selected photos"))
            } else {
                onResult(PhotoPickerResult.Success(paths))
            }
        }
    }

    // Launcher for picking a single photo (when maxItems is 1)
    val singlePhotoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri: Uri? ->
        if (uri == null) {
            onResult(PhotoPickerResult.Cancelled)
        } else {
            val path = copyUriToInternalStorage(context, uri)
            if (path == null) {
                onResult(PhotoPickerResult.Error("Failed to copy selected photo"))
            } else {
                onResult(PhotoPickerResult.Success(listOf(path)))
            }
        }
    }

    return remember(multiplePhotoLauncher, singlePhotoLauncher) {
        object : PhotoPickerLauncher {
            override fun launch(maxItems: Int) {
                pendingMaxItems = maxItems
                val request = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                if (maxItems == 1) {
                    singlePhotoLauncher.launch(request)
                } else {
                    multiplePhotoLauncher.launch(request)
                }
            }
        }
    }
}

/**
 * Copies the content from a Uri to the app's internal storage.
 * This is necessary because content URIs may not be accessible later.
 *
 * @param context Android context
 * @param uri Content URI from the photo picker
 * @return Path to the copied file, or null if copy failed
 */
private fun copyUriToInternalStorage(context: Context, uri: Uri): String? {
    return try {
        val inputStream = context.contentResolver.openInputStream(uri) ?: return null

        // Create a unique filename for the imported photo
        val importDir = File(context.filesDir, "imports")
        if (!importDir.exists()) {
            importDir.mkdirs()
        }

        val fileName = "import_${System.currentTimeMillis()}_${uri.hashCode()}.jpg"
        val outputFile = File(importDir, fileName)

        inputStream.use { input ->
            outputFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }

        outputFile.absolutePath
    } catch (e: Exception) {
        null
    }
}
