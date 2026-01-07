package com.po4yka.framelapse

import com.po4yka.framelapse.di.initKoin

/**
 * Helper function to initialize Koin from iOS Swift code.
 * Swift cannot call functions with default parameters directly.
 */
fun doInitKoin() {
    initKoin()
}
