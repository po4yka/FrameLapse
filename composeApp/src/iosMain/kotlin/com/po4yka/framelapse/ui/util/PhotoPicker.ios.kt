package com.po4yka.framelapse.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSDate
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask
import platform.Foundation.timeIntervalSince1970
import platform.Foundation.writeToFile
import platform.PhotosUI.PHPickerConfiguration
import platform.PhotosUI.PHPickerConfigurationSelectionOrdered
import platform.PhotosUI.PHPickerFilter
import platform.PhotosUI.PHPickerResult
import platform.PhotosUI.PHPickerViewController
import platform.PhotosUI.PHPickerViewControllerDelegateProtocol
import platform.UIKit.UIApplication
import platform.darwin.NSObject
import kotlin.concurrent.AtomicInt
import kotlin.concurrent.AtomicReference

/**
 * iOS implementation of the photo picker using PHPickerViewController.
 */
@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun rememberPhotoPickerLauncher(onResult: (PhotoPickerResult) -> Unit): PhotoPickerLauncher =
    remember(onResult) {
        IOSPhotoPickerLauncher(onResult)
    }

/**
 * iOS-specific photo picker launcher using PHPickerViewController.
 */
@OptIn(ExperimentalForeignApi::class)
private class IOSPhotoPickerLauncher(private val onResult: (PhotoPickerResult) -> Unit) : PhotoPickerLauncher {

    override fun launch(maxItems: Int) {
        val configuration = PHPickerConfiguration().apply {
            filter = PHPickerFilter.imagesFilter
            selectionLimit = if (maxItems == 0) 0 else maxItems.toLong()
            selection = PHPickerConfigurationSelectionOrdered
        }

        val picker = PHPickerViewController(configuration = configuration)
        val delegate = PickerDelegate(onResult)
        picker.delegate = delegate

        // Present the picker
        val rootViewController = UIApplication.sharedApplication.keyWindow?.rootViewController
        rootViewController?.presentViewController(
            viewControllerToPresent = picker,
            animated = true,
            completion = null,
        )
    }
}

/**
 * Delegate for handling PHPickerViewController results.
 */
@OptIn(ExperimentalForeignApi::class)
private class PickerDelegate(private val onResult: (PhotoPickerResult) -> Unit) :
    NSObject(),
    PHPickerViewControllerDelegateProtocol {

    override fun picker(picker: PHPickerViewController, didFinishPicking: List<*>) {
        picker.dismissViewControllerAnimated(true, null)

        @Suppress("UNCHECKED_CAST")
        val results = didFinishPicking as List<PHPickerResult>

        if (results.isEmpty()) {
            onResult(PhotoPickerResult.Cancelled)
            return
        }

        val paths = AtomicReference(mutableListOf<String>())
        val pendingCount = AtomicInt(results.size)
        val hasError = AtomicReference(false)

        results.forEach { result ->
            val itemProvider = result.itemProvider
            val typeIdentifier = "public.image"

            if (itemProvider.hasItemConformingToTypeIdentifier(typeIdentifier)) {
                itemProvider.loadDataRepresentationForTypeIdentifier(typeIdentifier) { data, error ->
                    if (error != null || data == null) {
                        hasError.value = true
                    } else {
                        // Save data to internal storage
                        val path = saveImageDataToInternalStorage(data)
                        if (path != null) {
                            val currentPaths = paths.value
                            currentPaths.add(path)
                            paths.value = currentPaths
                        }
                    }

                    val remaining = pendingCount.addAndGet(-1)
                    if (remaining == 0) {
                        val finalPaths = paths.value
                        if (finalPaths.isEmpty() && hasError.value) {
                            onResult(PhotoPickerResult.Error("Failed to load selected photos"))
                        } else if (finalPaths.isEmpty()) {
                            onResult(PhotoPickerResult.Cancelled)
                        } else {
                            onResult(PhotoPickerResult.Success(finalPaths.toList()))
                        }
                    }
                }
            } else {
                val remaining = pendingCount.addAndGet(-1)
                if (remaining == 0) {
                    val finalPaths = paths.value
                    if (finalPaths.isEmpty()) {
                        onResult(PhotoPickerResult.Cancelled)
                    } else {
                        onResult(PhotoPickerResult.Success(finalPaths.toList()))
                    }
                }
            }
        }
    }
}

/**
 * Saves image data to internal storage and returns the file path.
 */
@OptIn(ExperimentalForeignApi::class)
private fun saveImageDataToInternalStorage(data: platform.Foundation.NSData): String? {
    return try {
        val fileManager = NSFileManager.defaultManager
        val documentsPath = fileManager.URLsForDirectory(
            directory = NSDocumentDirectory,
            inDomains = NSUserDomainMask,
        ).firstOrNull() as? NSURL ?: return null

        val importDir = documentsPath.URLByAppendingPathComponent("imports")
        if (importDir != null && !fileManager.fileExistsAtPath(importDir.path ?: "")) {
            fileManager.createDirectoryAtURL(
                url = importDir,
                withIntermediateDirectories = true,
                attributes = null,
                error = null,
            )
        }

        val timestamp = NSDate().timeIntervalSince1970.toLong()
        val fileName = "import_${timestamp}_${data.hashCode()}.jpg"
        val filePath = importDir?.URLByAppendingPathComponent(fileName)?.path ?: return null

        data.writeToFile(filePath, atomically = true)
        filePath
    } catch (e: Exception) {
        null
    }
}
