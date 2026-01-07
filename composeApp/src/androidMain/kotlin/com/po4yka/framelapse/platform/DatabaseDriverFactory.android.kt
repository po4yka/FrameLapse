package com.po4yka.framelapse.platform

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.po4yka.framelapse.data.local.FrameLapseDatabase

actual class DatabaseDriverFactory(private val context: Context) {
    actual fun createDriver(): SqlDriver = AndroidSqliteDriver(
        schema = FrameLapseDatabase.Schema,
        context = context,
        name = "framelapse.db",
    )
}
