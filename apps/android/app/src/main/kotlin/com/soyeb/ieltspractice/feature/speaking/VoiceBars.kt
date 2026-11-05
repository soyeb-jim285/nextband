package com.soyeb.ieltspractice.feature.speaking

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.withInfiniteAnimationFrameMillis
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.soyeb.ieltspractice.design.LocalReducedMotion
import com.soyeb.ieltspractice.design.NB
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.sin

/** What the examiner is doing; each has its own motion (the web's Visualizer, after LiveKit's agent bar visualizer). */
enum class OrbMode { Idle, Connecting, Speaking, Listening, Thinking }

private const val BARS = 5
private const val MID = (BARS - 1) / 2f
/** 0 at the edges, 1 in the middle. */
private fun centre(i: Int) = 1f - abs(i - MID) / MID

/**
 * Target heights (0..1) for one frame. Speaking follows the examiner's [voice] (0..1, its audio level) with each bar on its own beat,
 * so one level reads as a voice; listening follows your microphone [level]; connecting returns the lit bar sweeping there and back;
 * thinking is a slow wave; idle rests. Pure, so the shapes are unit-tested.
 */
internal fun barTargets(mode: OrbMode, tSec: Float, voice: Float, level: Float, reduced: Boolean, out: FloatArray): Int {
    var lit = -1
    for (i in 0 until BARS) out[i] = when {
        reduced -> if (mode == OrbMode.Speaking || mode == OrbMode.Listening) 0.3f + 0.4f * centre(i) else 0.14f
        mode == OrbMode.Speaking -> {
            val v = if (voice > 0.02f) (voice * 1.6f).coerceAtMost(1f) else 0.45f + 0.25f * sin(tSec * 2.1f) // no audio level yet: its own beat
            0.16f + v * (0.3f + 0.7f * centre(i)) * (0.55f + 0.45f * abs(sin(tSec * 5.3f + i * 1.7f)))
        }
        mode == OrbMode.Listening -> {
            val v = (level * 1.8f).coerceAtMost(1f)
            0.14f + v * (0.35f + 0.65f * centre(i)) * (0.85f + 0.15f * sin(tSec * 9f + i))
        }
        mode == OrbMode.Thinking -> 0.16f + 0.14f * (0.5f + 0.5f * sin(tSec * 4f - i * 0.9f))
        mode == OrbMode.Connecting -> 0.14f
        else -> 0.12f
    }
    for (i in 0 until BARS) out[i] = out[i].coerceIn(0f, 1f)
    if (mode == OrbMode.Connecting && !reduced) {
        lit = floor(tSec * 6f).toInt() % (BARS * 2 - 2)
        if (lit >= BARS) lit = BARS * 2 - 2 - lit
    }
    return lit
}

/**
 * The examiner as five voice bars over a glow that swells with the voice: the examiner's in the brand colour, yours in ink. [compact]: one
 * small row (Part 2, where the cue card is the focus). [tint] overrides the bar colour (on a brand hero). Heights are animated in the draw
 * phase from one frame clock, so the 60 fps motion never recomposes the screen. Reduced motion: still bars per state. Decorative: the
 * status text beside it carries the meaning.
 */
@Composable
fun VoiceBars(
    mode: OrbMode, level: () -> Float, modifier: Modifier = Modifier, voice: () -> Float = { 0f }, compact: Boolean = false, tint: Color? = null,
) {
    val reduced = LocalReducedMotion.current
    val bar = tint ?: if (mode == OrbMode.Listening) NB.colors.ink.copy(alpha = 0.75f) else NB.colors.brand
    val glow = tint?.copy(alpha = 0.25f) ?: if (mode == OrbMode.Listening) NB.colors.surface2 else NB.colors.brandSoft
    val clock = remember { mutableLongStateOf(0L) }
    val start = remember { System.nanoTime() / 1_000_000 }
    val shown = remember { FloatArray(BARS) { 0.12f } }
    val target = remember { FloatArray(BARS) }
    // The infinite-animation clock (not withFrameMillis): tests and the system's animation policy treat it like any endless animation.
    if (!reduced) LaunchedEffect(Unit) { while (true) withInfiniteAnimationFrameMillis { clock.longValue = it } }
    val w = if (compact) 6.dp else 16.dp
    val gap = if (compact) 4.dp else 10.dp
    val h = if (compact) 32.dp else 112.dp
    val halo = if (compact) 64.dp else 192.dp
    Canvas(Modifier.width(halo).height(if (compact) 40.dp else 168.dp).then(modifier).clearAndSetSemantics {}) {
        clock.longValue // redraw every frame
        val t = (System.nanoTime() / 1_000_000 - start) / 1000f
        val lit = barTargets(mode, t, voice(), level(), reduced, target)
        for (i in 0 until BARS) {
            // fast attack, slower release: speech peaks land at once and fall back smoothly
            shown[i] = if (reduced) target[i] else shown[i] + (target[i] - shown[i]) * (if (target[i] > shown[i]) 0.55f else 0.18f)
        }
        val loud = shown.average().toFloat()
        val v = when (mode) {
            OrbMode.Speaking, OrbMode.Listening -> (loud * 1.4f).coerceAtMost(1f)
            OrbMode.Idle -> 0f
            else -> 0.25f
        }
        val c = Offset(size.width / 2, size.height / 2)
        val r = halo.toPx() / 2 * (0.7f + 0.45f * v)
        drawCircle(Brush.radialGradient(listOf(glow, Color.Transparent), c, r), r, c, alpha = 0.35f + 0.65f * v)
        val bw = w.toPx()
        val total = bw * BARS + gap.toPx() * (BARS - 1)
        val maxH = h.toPx()
        for (i in 0 until BARS) {
            val bh = maxH * shown[i].coerceIn(0.08f, 1f)
            val a = when { mode == OrbMode.Connecting -> if (i == lit || reduced) 1f else 0.35f; mode == OrbMode.Idle -> 0.45f; else -> 1f }
            drawRoundRect(
                bar, Offset(c.x - total / 2 + i * (bw + gap.toPx()), c.y - bh / 2), Size(bw, bh), CornerRadius(bw / 2), alpha = a,
            )
        }
    }
}
