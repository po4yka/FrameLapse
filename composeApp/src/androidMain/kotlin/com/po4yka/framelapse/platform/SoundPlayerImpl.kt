package com.po4yka.framelapse.platform

import android.content.Context
import android.media.MediaActionSound
import com.po4yka.framelapse.domain.service.SoundPlayer

/**
 * Android implementation of SoundPlayer using MediaActionSound.
 */
class SoundPlayerImpl(context: Context) : SoundPlayer {

    private val mediaActionSound = MediaActionSound().apply {
        load(MediaActionSound.SHUTTER_CLICK)
    }

    override fun playCaptureSound() {
        mediaActionSound.play(MediaActionSound.SHUTTER_CLICK)
    }

    override fun release() {
        mediaActionSound.release()
    }
}
