package io.github.ccch1mneyyy.dsh_app.nativemedia

import android.content.Context
import android.media.AudioManager

/**
 * Restores the app's loudspeaker route after something switched the device into the
 * communication route.
 *
 * libwebrtc's Android audio device module (flutter_webrtc, the transport of the DSH Remote
 * peer link) selects a communication device and puts the audio system into
 * `MODE_IN_COMMUNICATION`, even though that link only carries a DataChannel. While the mode
 * sticks, every other sound the app plays — a chat video, a Douyin clip — follows the
 * communication route and comes out of the earpiece. Returning to `MODE_NORMAL` puts media
 * back on the loudspeaker; the speakerphone flag covers a later re-assertion of the
 * communication route by the audio device module.
 *
 * Dictation owns the microphone, so its route is left untouched.
 */
object MediaAudioRoute {
    /**
     * Force media back onto the loudspeaker.
     * @param context any context; the audio manager is read from it.
     * @return whether the audio system had to be changed.
     */
    fun restore(context: Context): Boolean {
        if (MicArbiter.dictating) return false
        val audio = context.getSystemService(AudioManager::class.java) ?: return false
        var changed = false
        try {
            if (audio.mode != AudioManager.MODE_NORMAL) {
                audio.mode = AudioManager.MODE_NORMAL
                changed = true
            }
            if (!audio.isSpeakerphoneOn) {
                audio.isSpeakerphoneOn = true
                changed = true
            }
        } catch (_: RuntimeException) {
            // A ROM that refuses the mode change keeps its own route; never fail the caller.
        }
        return changed
    }
}
