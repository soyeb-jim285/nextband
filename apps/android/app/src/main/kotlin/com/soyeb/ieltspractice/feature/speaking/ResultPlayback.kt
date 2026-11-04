package com.soyeb.ieltspractice.feature.speaking

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.soyeb.ieltspractice.data.logic.MarkerType
import com.soyeb.ieltspractice.data.logic.Timeline
import com.soyeb.ieltspractice.data.logic.clock
import com.soyeb.ieltspractice.design.NB
import com.soyeb.ieltspractice.design.components.BtnKind
import com.soyeb.ieltspractice.design.components.BtnSize
import com.soyeb.ieltspractice.design.components.NbButton
import com.soyeb.ieltspractice.media.AudioPlayer
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * What the result screens need from the recording: observable [isLoaded] / [isPlaying] / [currentTime] / [duration] (seconds) and
 * seek/toggle. Wraps the Media3 [AudioPlayer]; call [load] once (main thread), run [poll] from a LaunchedEffect, [release] on dispose.
 * In demo mode ([demoDuration] set) there is no engine: it looks loaded and playing at a fixed spot, so screenshots show the audio bar,
 * the playhead and the word playing now without any audio.
 */
@Stable
class ResultPlayer(private val demoDuration: Double? = null) {
    var isLoaded by mutableStateOf(demoDuration != null); private set
    var isPlaying by mutableStateOf(demoDuration != null); private set
    var currentTime by mutableDoubleStateOf(demoDuration?.let { it * 0.38 } ?: 0.0); private set
    var duration by mutableDoubleStateOf(demoDuration ?: 0.0); private set
    var failed by mutableStateOf(false); private set
    var speed by mutableFloatStateOf(1f); private set

    private var engine: AudioPlayer? = null

    fun load(context: Context, url: String) {
        if (demoDuration != null || engine != null) return
        engine = AudioPlayer(context).also { it.load(url) }
    }

    /** Mirror the engine into the observable state until the screen leaves (about 25 times a second while playing; asleep while paused). */
    suspend fun poll() {
        val e = engine ?: return
        var waited = 0
        while (true) {
            isPlaying = e.playing.value
            currentTime = e.positionMs / 1000.0
            val d = e.durationMs / 1000.0
            if (d > 0) { duration = d; isLoaded = true } else if (!isLoaded && ++waited > 60) failed = true
            if (!isPlaying && isLoaded) withTimeoutOrNull(5_000) { e.playing.first { it } } else delay(if (isPlaying) 40 else 250) // the transcript highlight reads currentTime
        }
    }

    fun play() { engine?.play(); if (engine != null) isPlaying = true }
    fun pause() { engine?.pause(); isPlaying = false }
    fun toggle() = if (isPlaying) pause() else play()

    /** Jump to [t] seconds and (by default) play from there. */
    fun seek(t: Double, play: Boolean = true) {
        val clamped = max(0.0, min(t, if (duration > 0) duration else t))
        currentTime = clamped
        engine?.seekTo((clamped * 1000).toLong())
        if (play) play()
    }

    /** Plays [from] to [to] seconds, then pauses (one word). Call from a coroutine; a pause or a seek outside the span ends it early. */
    suspend fun playSpan(from: Double, to: Double) {
        seek(from)
        delay(150) // let poll() catch up with the seek
        while (isPlaying && currentTime >= from - 0.5 && currentTime < to) delay(30)
        if (isPlaying && currentTime >= to) pause()
    }

    /** 1x, 1.25x, 0.75x. */
    fun cycleSpeed() {
        speed = when (speed) { 1f -> 1.25f; 1.25f -> 0.75f; else -> 1f }
        engine?.setSpeed(speed)
    }

    fun release() {
        engine?.release()
        engine = null
    }
}

// MARK: Marker looks

// One colour AND one shape per mistake type so the timeline reads without colour. Teal is never used here: it means the playhead
// and the word playing.

@Composable
fun MarkerType.color(): Color = when (this) {
    MarkerType.Grammar -> NB.colors.bad
    MarkerType.Vocabulary -> NB.colors.warn
    MarkerType.Pronunciation -> NB.colors.listening
    MarkerType.Fluency -> NB.colors.muted
}

/** Underline dash pattern in dp (null = solid), so the type reads in text without colour. */
fun MarkerType.underlineDashes(): FloatArray? = when (this) {
    MarkerType.Grammar -> null
    MarkerType.Vocabulary -> floatArrayOf(5f, 3f)
    MarkerType.Pronunciation -> floatArrayOf(1.5f, 3f)
    MarkerType.Fluency -> floatArrayOf(5f, 3f, 1.5f, 3f)
}

/** Circle, square, triangle or diamond per type, centred on [c] with radius [r] (px), outlined in [edge]. */
fun DrawScope.drawMarker(type: MarkerType, c: Offset, r: Float, fill: Color, edge: Color) {
    val p = Path()
    when (type) {
        MarkerType.Grammar -> p.addOval(Rect(c, r))
        MarkerType.Vocabulary -> { val s = r * 0.85f; p.addRect(Rect(c.x - s, c.y - s, c.x + s, c.y + s)) }
        MarkerType.Pronunciation -> { p.moveTo(c.x, c.y - r); p.lineTo(c.x + r, c.y + r * 0.8f); p.lineTo(c.x - r, c.y + r * 0.8f); p.close() }
        MarkerType.Fluency -> { p.moveTo(c.x, c.y - r); p.lineTo(c.x + r, c.y); p.lineTo(c.x, c.y + r); p.lineTo(c.x - r, c.y); p.close() }
    }
    drawPath(p, fill)
    drawPath(p, edge, style = Stroke(1.dp.toPx()))
}

/** A type's shape in its colour, decorative (the text next to it names the type). */
@Composable
fun MarkerGlyph(type: MarkerType, glyphSize: Dp = 10.dp, modifier: Modifier = Modifier) {
    val fill = type.color()
    val edge = NB.colors.surface
    androidx.compose.foundation.Canvas(modifier.size(glyphSize)) { drawMarker(type, center, this.size.minDimension / 2 - 0.5f, fill, edge) }
}

// MARK: Audio bar

/**
 * Play/pause, a scrubber with the mistakes and long pauses on it, the time, and the speed (1x / 1.25x / 0.75x).
 * Tap or drag the track to seek. [timeline] may be empty (the examiner conversation has no markers).
 */
@Composable
fun ResultAudioBar(player: ResultPlayer, timeline: Timeline, modifier: Modifier = Modifier) {
    val c = NB.colors
    Surface(color = c.surface, contentColor = c.ink, modifier = modifier.fillMaxWidth()) {
        Column {
            HorizontalDivider(color = c.line)
            Row(Modifier.padding(start = 8.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    onClick = player::toggle, shape = CircleShape, color = c.brand, contentColor = c.onBrand,
                    modifier = Modifier.size(48.dp).semantics { contentDescription = if (player.isPlaying) "Pause" else "Play" },
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(if (player.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, Modifier.size(26.dp))
                    }
                }
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Scrubber(player, timeline)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(clock(player.currentTime.roundToInt()), style = NB.type.caption.copy(fontFeatureSettings = "tnum"), color = c.muted)
                        Text(clock(player.duration.roundToInt()), style = NB.type.caption.copy(fontFeatureSettings = "tnum"), color = c.muted)
                    }
                }
                NbButton(
                    if (player.speed == 1f) "1x" else if (player.speed == 1.25f) "1.25x" else "0.75x", player::cycleSpeed,
                    kind = BtnKind.Text, size = BtnSize.Small, modifier = Modifier.semantics { contentDescription = "Playback speed, ${player.speed} times" },
                )
            }
        }
    }
}

@Composable
private fun Scrubber(player: ResultPlayer, timeline: Timeline) {
    val c = NB.colors
    val colors = MarkerType.entries.associateWith { it.color() }
    val d = max(player.duration, 0.1)
    Canvas(
        // 48dp hit area (DESIGN.md min target); the 34dp drawing is centred in it.
        Modifier.fillMaxWidth().height(48.dp)
            .semantics {
                contentDescription = "Audio position"
                stateDescription = "${clock(player.currentTime.roundToInt())} of ${clock(player.duration.roundToInt())}"
                progressBarRangeInfo = ProgressBarRangeInfo(player.currentTime.toFloat().coerceIn(0f, d.toFloat()), 0f..d.toFloat())
                setProgress { v -> player.seek(v.toDouble(), play = false); true }
            }
            .pointerInput(d) { detectTapGestures { off -> player.seek(d * (off.x / size.width), play = true) } }
            .pointerInput(d) {
                detectHorizontalDragGestures { change, _ -> player.seek(d * (change.position.x / size.width).coerceIn(0f, 1f), play = false) }
            },
    ) {
        val trackH = 6.dp.toPx()
        val band = 34.dp.toPx()
        val top = (size.height - band) / 2
        val cy = top + band * 0.62f
        drawRoundRect(c.surface3, Offset(0f, cy - trackH / 2), Size(size.width, trackH), CornerRadius(trackH / 2))
        for (p in timeline.pauses) {
            val x = size.width * (p.start / d).toFloat()
            val w = max(2.dp.toPx(), size.width * ((p.end - p.start) / d).toFloat())
            drawRect(c.bad.copy(alpha = 0.45f), Offset(x, cy - trackH / 2), Size(w, trackH))
        }
        val played = size.width * (player.currentTime / d).toFloat().coerceIn(0f, 1f)
        drawRoundRect(c.brand, Offset(0f, cy - trackH / 2), Size(played, trackH), CornerRadius(trackH / 2))
        for (m in timeline.markers) {
            val x = size.width * (m.t / d).toFloat().coerceIn(0f, 1f)
            drawMarker(m.type, Offset(x, top + band * 0.2f), 4.dp.toPx(), colors.getValue(m.type), c.surface)
        }
        drawCircle(c.brand, 8.dp.toPx(), Offset(played, cy))
        drawCircle(c.surface, 3.dp.toPx(), Offset(played, cy))
    }
}
