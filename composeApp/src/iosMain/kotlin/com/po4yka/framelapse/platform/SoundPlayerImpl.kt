package com.po4yka.framelapse.platform

import com.po4yka.framelapse.domain.service.SoundPlayer
import kotlinx.cinterop.ExperimentalForeignApi
import platform.AudioToolbox.AudioServicesPlaySystemSound

/**
 * iOS implementation of SoundPlayer using AudioToolbox.
 */
@OptIn(ExperimentalForeignApi::class)
class SoundPlayerImpl : SoundPlayer {

    override fun playCaptureSound() {
        // System shutter sound ID (1108 is the camera shutter sound on iOS)
        AudioServicesPlaySystemSound(SHUTTER_SOUND_ID)
    }

    override fun release() {
        // No resources to release for system sounds
    }

    companion object {
        // iOS system shutter sound
        private const val SHUTTER_SOUND_ID: UInt = 1108u
    }
}
