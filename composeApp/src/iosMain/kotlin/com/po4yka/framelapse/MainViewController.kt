@file:Suppress("ktlint:standard:function-naming")

package com.po4yka.framelapse

import androidx.compose.ui.window.ComposeUIViewController

/**
 * Creates the main UIViewController for the iOS app.
 * This is called from Swift code to initialize the Compose UI.
 */
fun MainViewController() = ComposeUIViewController { App() }
