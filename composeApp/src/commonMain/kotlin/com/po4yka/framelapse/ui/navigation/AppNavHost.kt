package com.po4yka.framelapse.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Main navigation host for the app.
 * Platform-specific implementations use Navigation 3 (Android) or custom navigation (iOS).
 */
@Composable
expect fun AppNavHost(modifier: Modifier = Modifier)
