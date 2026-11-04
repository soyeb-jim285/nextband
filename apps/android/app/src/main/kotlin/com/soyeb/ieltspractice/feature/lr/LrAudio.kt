package com.soyeb.ieltspractice.feature.lr

import android.content.Context
import android.media.MediaMetadataRetriever
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Forward5
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Replay5
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.Lifecycle
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.exoplayer.ExoPlayer
import com.soyeb.ieltspractice.LocalDemo
import com.soyeb.ieltspractice.data.logic.clock
import com.soyeb.ieltspractice.data.model.LrAudioState
import com.soyeb.ieltspractice.design.NB
import com.soyeb.ieltspractice.design.components.Meter
import com.soyeb.ieltspractice.design.components.BtnKind
import com.soyeb.ieltspractice.design.components.BtnSize
import com.soyeb.ieltspractice.design.components.IconTile
import com.soyeb.ieltspractice.design.components.NbButton
import com.soyeb.ieltspractice.design.components.NbIconButton
import com.soyeb.ieltspractice.media.AudioPlayer
import com.soyeb.ieltspractice.media.MediaCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.ceil
import kotlin.math.max

// Listening audio. Practice: one recording per part with the usual controls. Exam: all parts play once, in order, with no controls; the clock
// IS the audio position, then the checking time runs on the wall clock (ported from the old LrAudio.kt, on media/AudioPlayer and Media3).

/** Practice and review player state: play, scrub, speed. In demo mode there is no engine, only a fixed spot, so screenshots need no audio. */
@Stable
class LrPlayer(private val demo: Boolean, private val resume: LrResume? = null) {
    var playing by mutableStateOf(false)
        private set
    var position by mutableDoubleStateOf(if (demo) 87.0 else 0.0)
        private set
    var duration by mutableDoubleStateOf(if (demo) 412.0 else 0.0)
        private set
    var speed by mutableFloatStateOf(1f)
        private set
    var failed by mutableStateOf(false)
        private set
    /** Set while the player sits at a restored position and has not been played since. */
    var resumedAt by mutableDoubleStateOf(0.0)
        private set
    private var engine: AudioPlayer? = null
    private var stopAt: Double? = null
    private var ready = resume == null // false until the saved position is applied: the position is 0 and must not overwrite it
    private var lastPersist = 0.0

    init { if (resume != null) speed = resume.rate }

    fun load(context: Context, url: String) {
        if (demo || engine != null) return
        engine = AudioPlayer(context).also { it.load(url); if (speed != 1f) it.setSpeed(speed) }
    }

    /** Keeps the state current (5 times a second while playing) until the screen leaves; while paused it sleeps until playback starts. */
    suspend fun poll() {
        val e = engine ?: return
        var waited = 0
        while (true) {
            playing = e.playing.value
            position = e.positionMs / 1000.0
            stopAt?.let { if (position >= it) { e.pause(); playing = false; stopAt = null } }
            val d = e.durationMs / 1000.0
            if (d > 0) duration = d else if (duration == 0.0 && ++waited > 80) failed = true
            if (resume != null) {
                if (!ready && d > 0) {
                    ready = true
                    val at = LrAudioState.resumePosition(resume.start, d)
                    if (at > 0) { seek(at); resumedAt = at }
                } else if (ready) {
                    resume.track(position)
                    if (playing && (position - lastPersist >= 5 || position < lastPersist)) { lastPersist = position; resume.persist() }
                }
            }
            if (!playing && d > 0 && (resume == null || ready)) withTimeoutOrNull(5_000) { e.playing.first { it } } else delay(if (playing) 200 else 400)
        }
    }

    fun toggle() {
        if (playing) { engine?.pause(); playing = false; savePosition() } else { engine?.play(); if (engine != null) { playing = true; resumedAt = 0.0 } }
    }

    private fun savePosition() {
        if (resume == null || !ready) return
        resume.track(position)
        resume.persist()
    }

    /** "Play from here": jump to [from], play, stop at [to]. */
    fun playWindow(from: Double, to: Double) {
        seek(from)
        stopAt = to
        if (demo) { playing = false; return }
        engine?.play()
        if (engine != null) playing = true
    }

    fun seek(t: Double) {
        stopAt = null
        val v = t.coerceIn(0.0, if (duration > 0) duration else max(t, 0.0))
        position = v
        engine?.seekTo((v * 1000).toLong())
    }

    fun replay() { seek(0.0); resumedAt = 0.0; if (!playing) toggle() }

    fun changeSpeed(s: Float) { speed = s; engine?.setSpeed(s); resume?.onRate?.invoke(s) }

    fun release() {
        engine?.let { position = it.positionMs / 1000.0 }
        savePosition()
        engine?.release()
        engine = null
    }
}

/** Practice runner: where this part was left (seconds), the saved speed, and how to report position and speed back to the session. */
class LrResume(val start: Double, val rate: Float, val track: (Double) -> Unit, val persist: () -> Unit, val onRate: ((Float) -> Unit)? = null)

/** A segment to play (the result's transcript): [id] changes on every tap so the same segment can be replayed. */
data class AudioCue(val from: Double, val to: Double, val id: Int)

/** Remembers a [LrPlayer] for [src], loads it and keeps it polling; released when the composable leaves. */
@Composable
fun rememberLrPlayer(src: String, resume: LrResume? = null, cue: AudioCue? = null): LrPlayer {
    val context = LocalContext.current
    val demo = LocalDemo.current != null
    val player = remember(src) { LrPlayer(demo, resume) }
    LaunchedEffect(player) { if (src.isNotEmpty() && !demo) { player.load(context, src); player.poll() } }
    DisposableEffect(player) { onDispose { player.release() } }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { if (player.playing) player.toggle() } // never keeps playing behind another app
    LaunchedEffect(cue?.id) { if (cue != null) player.playWindow(cue.from, cue.to) }
    return player
}

private val SPEEDS = LrAudioState.RATES.map { it.toFloat() }

private fun speedText(s: Float) = if (s == 1f) "1×" else "$s×"

/**
 * The pinned practice player: play, scrubber, 5 s back and forward, speed 0.75 / 1 / 1.25 and replay the part.
 */
@Composable
fun PracticePlayer(player: LrPlayer, label: String, modifier: Modifier = Modifier) {
    val c = NB.colors
    Column(modifier.fillMaxWidth().semantics { contentDescription = "$label audio" }, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(
                onClick = player::toggle, enabled = !player.failed, shape = CircleShape, color = c.brand, contentColor = c.onBrand,
                modifier = Modifier.size(48.dp).semantics { contentDescription = if (player.playing) "Pause $label" else "Play $label" },
            ) {
                Row(Modifier.size(48.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (player.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, Modifier.size(28.dp))
                }
            }
            Column(Modifier.weight(1f)) {
                Slider(
                    value = if (player.duration > 0) (player.position / player.duration).toFloat().coerceIn(0f, 1f) else 0f,
                    onValueChange = { if (player.duration > 0) player.seek(it * player.duration) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 32.dp).semantics {
                        contentDescription = "Seek"
                        stateDescription = "${clock(player.position.toInt())} of ${clock(player.duration.toInt())}"
                    },
                    colors = SliderDefaults.colors(thumbColor = c.brand, activeTrackColor = c.brand, inactiveTrackColor = c.surface3),
                )
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
                    Text(clock(player.position.toInt()), style = NB.type.caption.copy(fontFeatureSettings = "tnum"), color = c.muted, modifier = Modifier.weight(1f))
                    Text(clock(player.duration.toInt()), style = NB.type.caption.copy(fontFeatureSettings = "tnum"), color = c.muted, textAlign = TextAlign.End)
                }
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            NbIconButton(Icons.Rounded.Replay5, "Back 5 seconds", { player.seek(player.position - 5) })
            NbIconButton(Icons.Rounded.Forward5, "Forward 5 seconds", { player.seek(player.position + 5) })
            Spacer(Modifier.weight(1f))
            NbButton("Replay part", player::replay, kind = BtnKind.Text, size = BtnSize.Small, icon = Icons.Rounded.Replay)
            Surface(
                onClick = {
                    val i = SPEEDS.indexOf(player.speed).let { if (it < 0) 1 else it }
                    player.changeSpeed(SPEEDS[(i + 1) % SPEEDS.size])
                },
                shape = NB.shape.pill, color = c.surface2, contentColor = c.ink,
                modifier = Modifier.heightIn(min = 36.dp).padding(start = 4.dp).semantics { contentDescription = "Playback speed ${speedText(player.speed)}. Tap to change" },
            ) {
                Row(Modifier.heightIn(min = 36.dp).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(speedText(player.speed), style = NB.type.label.copy(fontFeatureSettings = "tnum"))
                }
            }
        }
        if (player.resumedAt > 0) Text("Resumes from ${clock(player.resumedAt.toInt())}", style = NB.type.caption, color = c.brandText, modifier = Modifier.padding(start = 4.dp))
        if (player.failed) Text("The recording could not be loaded. Check your connection.", style = NB.type.caption, color = c.badText, modifier = Modifier.padding(start = 4.dp))
    }
}

/**
 * Exam listening: the recordings of all parts play once, in order, with no pause or seek for the learner. The clock IS the audio position
 * (a resumed attempt picks the recording up where it was), then the checking time runs on the wall clock.
 * In demo mode ([demoPhase] set) there is no engine; it sits in that phase at a fixed spot.
 */
@Stable
class ExamPlaylist(
    private val urls: List<String>,
    private val startElapsed: Double,
    private val demoPhase: ExamPhase? = null,
    private val checkEndsAt: Double? = null,
) {
    var durations by mutableStateOf<List<Double>?>(if (demoPhase != null) listOf(412.0, 405.0, 420.0, 398.0) else null)
        private set
    var error by mutableStateOf(false)
        private set
    var phase by mutableStateOf(demoPhase ?: ExamPhase.Idle)
        private set
    var idx by mutableIntStateOf(if (demoPhase == ExamPhase.Audio) 1 else 0)
        private set
    var elapsed by mutableDoubleStateOf(if (demoPhase == ExamPhase.Audio) 640.0 else if (demoPhase == ExamPhase.Review) 1635.0 + 38 else startElapsed)
        private set
    /** The system paused the recording (a call, headphones unplugged): offer to resume. */
    var stalled by mutableStateOf(false)
        private set
    private var exo: ExoPlayer? = null
    private var reviewStart = 0L

    val total: Double get() = durations?.sum() ?: 0.0
    val reviewSeconds: Double get() = examReviewSeconds(durations, checkEndsAt)
    val reviewLeft: Int get() = max(0, ceil(reviewSeconds - (elapsed - total)).toInt())
    /** Whole sitting left: rest of the recording plus the review window. */
    val timeLeft: Int get() = max(0, ceil(total + reviewSeconds - elapsed).toInt())
    private fun starts(): List<Double> = durations.orEmpty().runningFold(0.0) { a, d -> a + d }

    fun attach(context: Context) {
        if (demoPhase != null || exo != null || urls.isEmpty()) return
        val p = ExoPlayer.Builder(context.applicationContext)
            .setAudioAttributes(AudioAttributes.Builder().setContentType(C.AUDIO_CONTENT_TYPE_SPEECH).setUsage(C.USAGE_MEDIA).build(), true)
            .setMediaSourceFactory(MediaCache.sourceFactory(context))
            .build()
        p.addListener(object : Player.Listener {
            override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                if (timeline.isEmpty || timeline.windowCount != urls.size) return
                val w = Timeline.Window()
                val ds = (0 until timeline.windowCount).map { timeline.getWindow(it, w).durationMs }
                if (ds.all { it != C.TIME_UNSET }) durations = ds.map { it / 1000.0 }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) { idx = p.currentMediaItemIndex }

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED && phase == ExamPhase.Audio) { reviewStart = System.currentTimeMillis(); phase = ExamPhase.Review }
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) { if (!playWhenReady && phase == ExamPhase.Audio) stalled = true }

            override fun onPlayerError(error: PlaybackException) { this@ExamPlaylist.error = true }
        })
        p.setMediaItems(urls.map { MediaItem.fromUri(it) })
        p.prepare()
        exo = p
    }

    /**
     * Each part's length read from its file. ExoPlayer prepares playlist items lazily, so the timeline only knows the
     * durations of the parts it has buffered and the start gate would wait forever on a multi-part test.
     */
    suspend fun loadDurations() {
        if (demoPhase != null || urls.isEmpty() || durations != null) return
        val ds = withContext(Dispatchers.IO) {
            urls.map { u ->
                val r = MediaMetadataRetriever()
                try {
                    r.setDataSource(u, emptyMap())
                    r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                } catch (e: Exception) {
                    null
                } finally {
                    runCatching { r.release() }
                }
            }
        }
        if (durations != null) return
        if (ds.all { it != null && it > 0 }) durations = ds.map { it!! / 1000.0 } else error = true
    }

    fun start() {
        val d = durations ?: return
        val s = starts()
        stalled = false
        if (startElapsed >= total) {
            reviewStart = System.currentTimeMillis() - ((startElapsed - total) * 1000).toLong()
            phase = ExamPhase.Review
            return
        }
        var i = 0
        while (i < d.size - 1 && startElapsed >= s[i + 1]) i++
        idx = i
        phase = ExamPhase.Audio
        exo?.apply { seekTo(i, ((startElapsed - s[i]) * 1000).toLong()); playWhenReady = true }
    }

    fun resume() { stalled = false; exo?.playWhenReady = true }

    /** The stream failed mid-exam (a network drop): prepare again where it stopped. The clock reads the player, so it resumes there too. */
    fun retry() { error = false; stalled = false; exo?.apply { prepare(); playWhenReady = true } }

    /** Keeps [elapsed] current (4 times a second) until the screen leaves; [onElapsed] gets each new value. */
    suspend fun tick(onElapsed: (Double) -> Unit = {}) {
        while (true) {
            if (demoPhase == null) {
                // Nothing to count at the start gate: sleep until the recording starts rather than wake four times a second.
                if (phase == ExamPhase.Idle) { snapshotFlow { phase }.first { it != ExamPhase.Idle }; continue }
                if (phase == ExamPhase.Audio) elapsed = starts().getOrElse(idx) { 0.0 } + (exo?.currentPosition ?: 0L) / 1000.0
                else if (phase == ExamPhase.Review) elapsed = total + (System.currentTimeMillis() - reviewStart) / 1000.0
                if (phase != ExamPhase.Idle) onElapsed(elapsed)
            }
            delay(250)
        }
    }

    fun release() { exo?.release(); exo = null }
}

/** Exam recording bar: which part is playing and the whole-test progress. No transport controls on purpose. */
@Composable
fun ExamAudioBar(p: ExamPlaylist, modifier: Modifier = Modifier, parts: List<Int>? = null) {
    val c = NB.colors
    Row(modifier.fillMaxWidth().semantics { contentDescription = "Recording" }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(Icons.Rounded.Headphones, null, Modifier.size(24.dp), tint = c.listening)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                when {
                    p.phase == ExamPhase.Review -> "Recording finished. Check your answers."
                    p.error -> "The recording stopped. Check your connection and retry."
                    parts != null -> "Part ${parts.getOrNull(p.idx) ?: (p.idx + 1)} is playing"
                    else -> "Part ${p.idx + 1} of ${p.durations?.size ?: 4} is playing"
                },
                style = NB.type.label, color = c.ink,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Meter(
                    if (p.total > 0) p.elapsed.toDouble() else 0.0,
                    Modifier.weight(1f).semantics { contentDescription = "Recording progress" },
                    max = if (p.total > 0) p.total.toDouble() else 1.0, color = c.listening, height = 6.dp,
                )
                if (p.total > 0 && p.phase != ExamPhase.Review) {
                    Text("${clock(minOf(p.elapsed, p.total).toInt())} / ${clock(p.total.toInt())}", style = NB.type.caption.copy(fontFeatureSettings = "tnum"), color = c.muted)
                }
            }
        }
        if (p.error && p.phase == ExamPhase.Audio) NbButton("Retry audio", p::retry, kind = BtnKind.Secondary, size = BtnSize.Small)
        else if (p.stalled && p.phase == ExamPhase.Audio) NbButton("Resume audio", p::resume, kind = BtnKind.Secondary, size = BtnSize.Small)
    }
}

/** The start gate of a listening exam: nothing plays until the learner is ready. */
@Composable
fun ExamGate(p: ExamPlaylist, resumed: Boolean, onBack: () -> Unit, parts: List<Int>, modifier: Modifier = Modifier) {
    val c = NB.colors
    Column(modifier.fillMaxWidth().padding(NB.space.gutter), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        IconTile(Icons.Rounded.Headphones, c.listening, c.listeningSoft, 56.dp)
        Text(if (resumed) "Ready to continue?" else "Ready to listen?", style = NB.type.headline, color = c.ink)
        Text(
            "The recording plays once, ${if (parts.size > 1) "from Part ${parts.first()} to Part ${parts.last()}" else "Part ${parts.firstOrNull()} only"}, with no pause or rewind. " +
                "At the end you get the checking time the recording announces, then the test submits itself.",
            style = NB.type.body, color = c.ink,
        )
        Text("Check your volume first. Headphones help.", style = NB.type.bodySmall, color = c.muted)
        if (p.error) Text("The recording could not be loaded. Check your connection and reopen the test.", style = NB.type.bodySmall, color = c.badText)
        NbButton(if (resumed) "Resume test" else "Start test", p::start, size = BtnSize.Large, fill = true, enabled = p.durations != null && !p.error, haptic = true)
        if (p.durations == null && !p.error) Text("Loading the recording…", style = NB.type.caption, color = c.muted)
        NbButton("Back", onBack, kind = BtnKind.Text, fill = true)
    }
}
