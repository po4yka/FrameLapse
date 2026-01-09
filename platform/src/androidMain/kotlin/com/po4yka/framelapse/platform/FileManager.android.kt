package com.po4yka.framelapse.platform

import android.content.Context
import android.os.StatFs
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
        val file = File(path)
        if (file.isDirectory) {
            file.deleteRecursively()
        } else {
            file.delete()
        }
    } catch (e: Exception) {
        false
    }

    actual fun deleteRecursively(path: String): Boolean = try {
        val file = File(path)
        if (file.exists()) {
            file.deleteRecursively()
        } else {
            true
        }
    } catch (e: Exception) {
        false
    }

    actual fun fileExists(path: String): Boolean = File(path).exists()

    actual fun createDirectory(path: String): Boolean = try {
        File(path).mkdirs()
    } catch (e: Exception) {
        false
    }

    actual fun listFilesRecursively(path: String): List<String> = try {
        val root = File(path)
        if (!root.exists()) return emptyList()
        root.walkTopDown()
            .filter { it.isFile }
            .map { it.absolutePath }
            .toList()
    } catch (e: Exception) {
        emptyList()
    }

    actual fun getFileSizeBytes(path: String): Long = try {
        val file = File(path)
        if (file.exists() && file.isFile) file.length() else 0L
    } catch (e: Exception) {
        0L
    }

    actual fun getAvailableStorageBytes(): Long = try {
        val stat = StatFs(getAppDirectory())
        stat.availableBlocksLong * stat.blockSizeLong
    } catch (e: Exception) {
        0L
    }
}
