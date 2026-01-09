package com.po4yka.framelapse.infra

import com.po4yka.framelapse.domain.service.FileSystem
import com.po4yka.framelapse.platform.FileManager

class AppFileSystem(private val fileManager: FileManager) : FileSystem {
    override fun fileExists(path: String): Boolean = fileManager.fileExists(path)

    override fun deleteFile(path: String): Boolean = fileManager.deleteFile(path)

    override fun getProjectDirectory(projectId: String): String =
        fileManager.getProjectDirectory(projectId)

    override fun getAvailableStorageBytes(): Long = fileManager.getAvailableStorageBytes()
}
