package com.lucasanso.naorola.blocking

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager

/**
 * Best effort to silence the video under the block screen: asks for transient audio focus,
 * which well-behaved players answer by pausing. Whether YouTube/Instagram pause is to be
 * confirmed on a device; leaving the player ("back") is the reliable way to stop playback.
 */
class AudioFocusMuter(context: Context) {
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private var request: AudioFocusRequest? = null

    fun mute() {
        if (request != null) return
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_UNKNOWN)
            .build()
        val newRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(attributes)
            .setOnAudioFocusChangeListener { }
            .build()
        runCatching { audioManager?.requestAudioFocus(newRequest) }
        request = newRequest
    }

    fun release() {
        val current = request ?: return
        runCatching { audioManager?.abandonAudioFocusRequest(current) }
        request = null
    }
}
