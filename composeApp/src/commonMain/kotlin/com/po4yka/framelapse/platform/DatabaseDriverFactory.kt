package com.po4yka.framelapse.platform

import app.cash.sqldelight.db.SqlDriver

/**
 * Factory for creating platform-specific SQLite drivers.
 * Implemented via expect/actual for each platform.
 */
expect class DatabaseDriverFactory {
    /**
     * Creates a SQLite driver for the current platform.
     *
     * @return SqlDriver instance
     */
    fun createDriver(): SqlDriver
}
