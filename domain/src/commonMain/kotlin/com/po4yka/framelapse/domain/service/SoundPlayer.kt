package com.po4yka.framelapse.domain.service

/**
 * Interface for playing system sounds.
 * Platform implementations will use:
 * - Android: MediaPlayer or SoundPool
 * - iOS: AVAudioPlayer or SystemSoundID
 */
interface SoundPlayer {

    /**
     * Play the camera shutter/capture sound.
     */
    fun playCaptureSound()

    /**
     * Release resources when no longer needed.
     */
    fun release()
}
