package org.sksoft.bit

import android.content.Context
import android.media.*

/** One original clip at a time; gain is independent of system STREAM_MUSIC volume. */
class Audio(private val context: Context) {
    private val manager = context.getSystemService(AudioManager::class.java)
    private var player: MediaPlayer? = null
    private var request: AudioFocusRequest? = null
    var volume = 70
        set(value) { field = value.coerceIn(0, 100); player?.setVolume(field / 100f, field / 100f) }
    private val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
    fun play(yes: Boolean): Boolean {
        stop()
        if (volume == 0) return true
        val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(attributes).setOnAudioFocusChangeListener { if (it < 0) stop() }.build()
        request = focus
        if (manager.requestAudioFocus(focus) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) { stop(); return false }
        return try {
            val p = MediaPlayer()
            player = p
            p.setAudioAttributes(attributes)
            context.assets.openFd(if (yes) "yes.wav" else "no.wav").use { p.setDataSource(it.fileDescriptor, it.startOffset, it.length) }
            p.setVolume(volume / 100f, volume / 100f)
            p.setOnCompletionListener { if (player === p) stop() }
            p.setOnErrorListener { _, _, _ -> if (player === p) stop(); true }
            p.prepare(); p.start(); true
        } catch (_: Exception) { stop(); false }
    }
    fun stop() {
        player?.release(); player = null
        request?.let { manager.abandonAudioFocusRequest(it) }; request = null
    }
}
