package io.github.ccch1mneyyy.dsh_app.nativemedia

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.os.Build
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
 * A single restore is not enough, and a slow poll leaves the first second on the earpiece, so the
 * correction is event driven: the audio system is fixed the moment any playback starts
 * ([AudioManager.registerAudioPlaybackCallback]) and whenever the routed devices change
 * ([AudioManager.registerAudioDeviceCallback], `OnCommunicationDeviceChangedListener`). The
 * five-second tick only catches a route that drifted without either signal.
 *
 * Dictation owns the microphone and a real phone call owns the whole route, so both are left
 * alone.
 */
object MediaAudioRoute {
    private const val SAFETY_TICK_MS = 5_000L

    private val handler = Handler(Looper.getMainLooper())
    private var context: Context? = null
    private var audio: AudioManager? = null
    private var watchers = 0
    private var watching = false
    private var communicationListener: AudioManager.OnCommunicationDeviceChangedListener? = null

    private val tick = object : Runnable {
        override fun run() {
            if (!watching) return
            correct()
            handler.postDelayed(this, SAFETY_TICK_MS)
        }
    }

    /** Any playback starting (a clip, a page in the built-in browser) is when the route flips. */
    private val playbackCallback = object : AudioManager.AudioPlaybackCallback() {
        override fun onPlaybackConfigChanged(configs: MutableList<AudioPlaybackConfiguration>) {
            if (!watching) return
            // Only media start/stop matters; a route change for speech is dictation's business.
            val media = configs.any {
                it.audioAttributes.usage == AudioAttributes.USAGE_MEDIA ||
                    it.audioAttributes.usage == AudioAttributes.USAGE_GAME
            }
            if (media) correct()
        }
    }

    /** A device appearing or disappearing is the other moment libwebrtc re-routes. */
    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) = correct()
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) = correct()
    }

    /** Remember the application context the watchdog restores through. */
    fun attach(appContext: Context) {
        context = appContext.applicationContext
        audio = context?.getSystemService(AudioManager::class.java)
    }

    /**
     * Force media back onto the loudspeaker right now.
     * @param source any context; its audio manager is used.
     * @return whether the audio system had to be changed.
     */
    fun restore(source: Context): Boolean {
        if (MicArbiter.dictating) return false
        val manager = source.getSystemService(AudioManager::class.java) ?: return false
        var changed = false
        try {
            // A real call owns the route; the watchdog must never pull it out from under it.
            if (manager.mode == AudioManager.MODE_IN_CALL) return false
            if (manager.mode != AudioManager.MODE_NORMAL) {
                manager.mode = AudioManager.MODE_NORMAL
                changed = true
            }
            // Covers a later re-assertion of the communication route: while the flag is set, that
            // route plays through the loudspeaker instead of the earpiece.
            if (!manager.isSpeakerphoneOn) {
                manager.isSpeakerphoneOn = true
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
        val manager = audio ?: return
        try {
            manager.registerAudioPlaybackCallback(playbackCallback, handler)
        } catch (_: RuntimeException) {
            // Older ROMs without the callback keep the tick.
        }
        try {
            manager.registerAudioDeviceCallback(deviceCallback, handler)
        } catch (_: RuntimeException) {
            // As above.
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                val listener = AudioManager.OnCommunicationDeviceChangedListener { correct() }
                manager.addOnCommunicationDeviceChangedListener({ runnable -> handler.post(runnable) }, listener)
                communicationListener = listener
            } catch (_: RuntimeException) {
                // As above.
            }
        }
        handler.post(tick)
    }

    /** Release one peer link's hold; the last one stops every listener. */
    fun stopWatch() {
        if (watchers > 0) watchers -= 1
        if (watchers > 0 || !watching) return
        watching = false
        handler.removeCallbacks(tick)
        val manager = audio ?: return
        try {
            manager.unregisterAudioPlaybackCallback(playbackCallback)
        } catch (_: RuntimeException) {
            // Already unregistered.
        }
        try {
            manager.unregisterAudioDeviceCallback(deviceCallback)
        } catch (_: RuntimeException) {
            // Already unregistered.
        }
        communicationListener?.let { listener ->
            try {
                manager.removeOnCommunicationDeviceChangedListener(listener)
            } catch (_: RuntimeException) {
                // Already removed.
            }
        }
        communicationListener = null
    }

    private fun correct() {
        context?.let { restore(it) }
    }
}
