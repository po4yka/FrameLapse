package com.po4yka.framelapse.platform

import com.po4yka.framelapse.domain.service.ShareHandler
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSURL
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication

/**
 * iOS implementation of ShareHandler using UIActivityViewController.
 */
@OptIn(ExperimentalForeignApi::class)
class ShareHandlerImpl : ShareHandler {

    override suspend fun shareFile(filePath: String, mimeType: String) {
        val fileUrl = NSURL.fileURLWithPath(filePath)
        val activityItems = listOf(fileUrl)

        val activityViewController = UIActivityViewController(
            activityItems = activityItems,
            applicationActivities = null,
        )

        val rootViewController = UIApplication.sharedApplication.keyWindow?.rootViewController
        rootViewController?.presentViewController(
            viewControllerToPresent = activityViewController,
            animated = true,
            completion = null,
        )
    }

    override suspend fun openFile(filePath: String, mimeType: String) {
        val fileUrl = NSURL.fileURLWithPath(filePath)
        UIApplication.sharedApplication.openURL(fileUrl)
    }
}
