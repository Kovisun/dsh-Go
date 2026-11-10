package io.github.ccch1mneyyy.dsh_app.nativemedia

import android.content.Context
import android.media.AudioManager
import android.os.Handler
import android.os.Looper

/**
 * Keeps the app's media audio on the loudspeaker while something else wants the device in the
 * communication route.
 *
 * libwebrtc's Android audio device module (flutter_webrtc, the transport of the DSH Remote peer
 * link) selects a communication device and puts the audio system into `MODE_IN_COMMUNICATION`,
 * even though that link only carries a DataChannel. While the mode sticks, every other sound the
 * app plays — a chat video, a Douyin clip in the built-in browser — follows the communication
 * route and comes out of the earpiece.
 *
 * A single restore is not enough: the module re-asserts its route when audio focus or the device
 * list changes, which is exactly what happens when playback starts. [startWatch] therefore keeps
 * re-asserting the media route while a peer link is up, in a cheap one-second tick that only
 * touches the audio system when the mode actually drifted.
 *
 * Dictation owns the microphone and a real phone call owns the whole route, so both are left
 * alone.
 */
object MediaAudioRoute {
    private val handler = Handler(Looper.getMainLooper())
    private var context: Context? = null
    private var watchers = 0
    private var watching = false

    private val tick = object : Runnable {
        override fun run() {
            if (!watching) return
            context?.let { restore(it) }
            handler.postDelayed(this, WATCH_INTERVAL_MS)
        }
    }

    /** Remember the application context the watchdog restores through. */
    fun attach(appContext: Context) {
        context = appContext.applicationContext
    }

    /**
     * Force media back onto the loudspeaker right now.
     * @param source any context; its audio manager is used.
     * @return whether the audio system had to be changed.
     */
    fun restore(source: Context): Boolean {
        if (MicArbiter.dictating) return false
        val audio = source.getSystemService(AudioManager::class.java) ?: return false
        var changed = false
        try {
            // A real call owns the route; the watchdog must never pull it out from under it.
            if (audio.mode == AudioManager.MODE_IN_CALL) return false
            if (audio.mode != AudioManager.MODE_NORMAL) {
                audio.mode = AudioManager.MODE_NORMAL
                changed = true
            }
            // Covers a later re-assertion of the communication route: while the flag is set, that
            // route plays through the loudspeaker instead of the earpiece.
            if (!audio.isSpeakerphoneOn) {
                audio.isSpeakerphoneOn = true
                changed = true
            }
        } catch (_: RuntimeException) {
            // A ROM that refuses the mode change keeps its own route; never fail the caller.
        }
        return changed
    }

    /** Start holding the media route (idempotent; reference counted per peer link). */
    fun startWatch() {
        watchers += 1
        if (watching) return
        watching = true
        handler.post(tick)
    }

    /** Release one peer link's hold; the last one stops the tick. */
    fun stopWatch() {
        if (watchers > 0) watchers -= 1
        if (watchers > 0 || !watching) return
        watching = false
        handler.removeCallbacks(tick)
    }

    /** `AudioManager.release`-style reset for tests and teardown. */
    fun stopAll() {
        watchers = 0
        if (!watching) return
        watching = false
        handler.removeCallbacks(tick)
    }

    private const val WATCH_INTERVAL_MS = 1_000L
}
