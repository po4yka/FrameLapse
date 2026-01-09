package com.po4yka.framelapse.domain.service

/**
 * File-system abstraction for domain logic.
 */
interface FileSystem {
    fun fileExists(path: String): Boolean
    fun deleteFile(path: String): Boolean
    fun getProjectDirectory(projectId: String): String
    fun getAvailableStorageBytes(): Long
}
