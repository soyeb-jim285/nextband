package com.soyeb.ieltspractice.media

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Plays an audio URL (presigned attempt audio, examiner TTS) or a local file with Media3 ExoPlayer. Main thread only; call
 * [release] when the screen leaves (`DisposableEffect`). Poll [positionMs] from a `LaunchedEffect` loop while [playing].
 * Port of iOS Audio/Player.swift.
 */
class AudioPlayer(context: Context) {
    // Audio focus: a call or another app's media pauses it; unplugged headphones pause it rather than switching to the speaker.
    private val player = ExoPlayer.Builder(context.applicationContext)
        .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_SPEECH).build(), true)
        .setHandleAudioBecomingNoisy(true)
        .setMediaSourceFactory(MediaCache.sourceFactory(context))
        .build()

    private val _playing = MutableStateFlow(false)
    val playing: StateFlow<Boolean> = _playing.asStateFlow()

    /** True once the current item played to its end. */
    private val _ended = MutableStateFlow(false)
    val ended: StateFlow<Boolean> = _ended.asStateFlow()

    /** True after a playback error (a network drop, a bad URL) until [reload]. */
    private val _error = MutableStateFlow(false)
    val error: StateFlow<Boolean> = _error.asStateFlow()

    init {
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { _playing.value = isPlaying }
            override fun onPlaybackStateChanged(state: Int) { _ended.value = state == Player.STATE_ENDED }
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) { _ended.value = true; _error.value = true } // callers waiting for the end must not hang on a bad URL
        })
    }

    val positionMs: Long get() = player.currentPosition
    val durationMs: Long get() = player.duration.takeIf { it >= 0 } ?: 0L

    fun load(uri: String) {
        _ended.value = false
        _error.value = false
        player.setMediaItem(MediaItem.fromUri(uri))
        player.prepare()
    }

    /** Loads the current item again after an error or a stalled load, keeping the position. */
    fun reload() { _error.value = false; _ended.value = false; player.stop(); player.prepare() }

    fun play() { if (player.playbackState == Player.STATE_ENDED) player.seekTo(0); player.play() }
    fun pause() = player.pause()
    fun seekTo(ms: Long) = player.seekTo(ms)
    fun setSpeed(speed: Float) = player.setPlaybackSpeed(speed)
    fun release() = player.release()
}
