package com.po4yka.framelapse

import androidx.compose.runtime.Composable
import com.po4yka.framelapse.ui.navigation.AppNavHost
import com.po4yka.framelapse.ui.theme.FrameLapseTheme

@Composable
fun App() {
    FrameLapseTheme {
        AppNavHost()
    }
}
