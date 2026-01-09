package com.po4yka.framelapse.platform

/**
 * File system operations abstraction.
 * Implemented via expect/actual for each platform.
 */
expect class FileManager {
    /**
     * Returns the app's private documents directory path.
     */
    fun getAppDirectory(): String

    /**
     * Returns the directory path for a specific project.
     * Creates the directory if it doesn't exist.
     *
     * @param projectId The project identifier
     * @return The project directory path
     */
    fun getProjectDirectory(projectId: String): String

    /**
     * Deletes a file at the given path.
     *
     * @param path The file path to delete
     * @return True if deletion was successful
     */
    fun deleteFile(path: String): Boolean

    /**
     * Deletes a file or directory tree at the given path.
     *
     * @param path The file or directory path to delete
     * @return True if deletion was successful
     */
    fun deleteRecursively(path: String): Boolean

    /**
     * Checks if a file exists at the given path.
     *
     * @param path The file path to check
     * @return True if the file exists
     */
    fun fileExists(path: String): Boolean

    /**
     * Creates a directory at the given path.
     *
     * @param path The directory path to create
     * @return True if creation was successful
     */
    fun createDirectory(path: String): Boolean

    /**
     * Lists all files under the given path recursively.
     *
     * @param path Root directory to scan
     * @return Absolute paths for files found (directories excluded)
     */
    fun listFilesRecursively(path: String): List<String>

    /**
     * Returns the file size in bytes for the given path.
     *
     * @param path File path
     * @return File size in bytes or 0 if not found
     */
    fun getFileSizeBytes(path: String): Long

    /**
     * Returns the available storage space in bytes.
     *
     * @return Available bytes on the storage device
     */
    fun getAvailableStorageBytes(): Long
}
