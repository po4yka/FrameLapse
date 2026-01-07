package com.po4yka.framelapse.platform

import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask

actual class FileManager {
    private val fileManager = NSFileManager.defaultManager

    actual fun getAppDirectory(): String {
        val paths = NSSearchPathForDirectoriesInDomains(
            NSDocumentDirectory,
            NSUserDomainMask,
            true,
        )
        return paths.filterIsInstance<String>().firstOrNull().orEmpty()
    }

    actual fun getProjectDirectory(projectId: String): String {
        val projectPath = "${getAppDirectory()}/projects/$projectId"
        if (!fileExists(projectPath)) {
            createDirectory(projectPath)
        }
        return projectPath
    }

    actual fun deleteFile(path: String): Boolean = try {
        fileManager.removeItemAtPath(path, null)
        true
    } catch (e: Exception) {
        false
    }

    actual fun fileExists(path: String): Boolean = fileManager.fileExistsAtPath(path)

    actual fun createDirectory(path: String): Boolean = try {
        fileManager.createDirectoryAtPath(
            path,
            withIntermediateDirectories = true,
            attributes = null,
            error = null,
        )
    } catch (e: Exception) {
        false
    }
}
