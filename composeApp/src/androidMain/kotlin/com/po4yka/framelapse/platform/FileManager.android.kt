package com.po4yka.framelapse.platform

import android.content.Context
import java.io.File

actual class FileManager(private val context: Context) {

    actual fun getAppDirectory(): String = context.filesDir.absolutePath

    actual fun getProjectDirectory(projectId: String): String {
        val projectDir = File(getAppDirectory(), "projects/$projectId")
        if (!projectDir.exists()) {
            projectDir.mkdirs()
        }
        return projectDir.absolutePath
    }

    actual fun deleteFile(path: String): Boolean = try {
        File(path).delete()
    } catch (e: Exception) {
        false
    }

    actual fun fileExists(path: String): Boolean = File(path).exists()

    actual fun createDirectory(path: String): Boolean = try {
        File(path).mkdirs()
    } catch (e: Exception) {
        false
    }
}
