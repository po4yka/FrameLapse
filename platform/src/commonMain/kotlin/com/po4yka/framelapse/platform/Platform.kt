package com.po4yka.framelapse.platform

/**
 * Platform information provider.
 * Implemented via expect/actual for each platform.
 */
expect class Platform() {
    /**
     * Platform name (e.g., "Android", "iOS").
     */
    val name: String

    /**
     * Platform version (e.g., SDK version for Android, iOS version).
     */
    val version: String
}

/**
 * Returns a formatted platform string.
 */
fun Platform.displayString(): String = "$name $version"
