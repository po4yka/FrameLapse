package com.po4yka.framelapse.data.local

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import com.po4yka.framelapse.data.local.FrameLapseDatabase

actual class DatabaseDriverFactory {
    actual fun createDriver(): SqlDriver = NativeSqliteDriver(
        schema = FrameLapseDatabase.Schema,
        name = "framelapse.db",
    )
}
