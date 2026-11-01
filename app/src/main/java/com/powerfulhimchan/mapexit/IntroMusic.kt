package com.powerfulhimchan.mapexit

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper

/** Local, offline 10-second loop. Only the visible, resumed title screen owns audio focus. */
internal class IntroMusic(context: Context) {
    private val app = context.applicationContext
    private val manager = app.getSystemService(AudioManager::class.java)
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_GAME)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()
    private var player: MediaPlayer? = null
    private var active = false
    internal val isPlaying: Boolean get() = player?.isPlaying == true
    private val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(attributes)
        .setOnAudioFocusChangeListener({ change ->
            when (change) {
                AudioManager.AUDIOFOCUS_LOSS -> stop()
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> player?.pause()
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> player?.setVolume(.12f, .12f)
                AudioManager.AUDIOFOCUS_GAIN -> if (active) {
                    player?.setVolume(.55f, .55f)
                    player?.start()
                }
            }
        }, Handler(Looper.getMainLooper()))
        .build()

    fun start() {
        if (active) return
        // Audio failure or denied focus must never prevent the player from entering the game.
        try {
            if (manager.requestAudioFocus(focus) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) return
            active = true
            val created = MediaPlayer.create(app, R.raw.intro_retro, attributes, 0)
            if (created == null) { stop(); return }
            player = created
            created.setOnErrorListener { _, _, _ -> stop(); true }
            created.isLooping = true
            created.setVolume(.55f, .55f)
            created.start()
        } catch (_: RuntimeException) { stop() }
    }

    fun stop() {
        active = false
        player?.release()
        player = null
        manager.abandonAudioFocusRequest(focus)
    }
}
