package com.soyeb.ieltspractice.feature.speaking

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.soyeb.ieltspractice.design.LocalReducedMotion
import com.soyeb.ieltspractice.design.NB

enum class OrbMode { Idle, Connecting, Speaking, Listening, Thinking }

/**
 * The live examiner's voice orb. It swells with [level] (0..1: the examiner's audio while it talks, the learner's microphone while
 * they answer), breathes slowly while connecting or thinking, and sits almost still when idle. Decorative: the status text beside
 * it carries the meaning. Static under reduced motion.
 */
@Composable
fun VoiceOrb(mode: OrbMode, level: Float, modifier: Modifier = Modifier, orbSize: Dp = 220.dp) {
    val reduced = LocalReducedMotion.current
    val brand = NB.colors.brand
    val soft = NB.colors.brandText
    val pulse = if (reduced) 0.5f else rememberInfiniteTransition(label = "orb").animateFloat(
        0f, 1f, infiniteRepeatable(tween(if (mode == OrbMode.Idle) 3200 else 1500, easing = LinearEasing), RepeatMode.Reverse), label = "pulse",
    ).value
    // The examiner's audio level isn't available, so while it talks the orb swells on its own beat (at least).
    val target = when (mode) {
        OrbMode.Speaking -> maxOf(level, 0.2f + 0.3f * pulse)
        OrbMode.Listening -> level
        else -> 0f
    }
    val lv by animateFloatAsState(
        target.coerceIn(0f, 1f),
        if (reduced) tween(0) else tween(120), label = "orbLevel",
    )
    val breathe = when (mode) {
        OrbMode.Connecting, OrbMode.Thinking -> 0.10f
        OrbMode.Idle -> 0.03f
        else -> 0.04f
    }
    Canvas(modifier.size(orbSize).clearAndSetSemantics {}) {
        val c = Offset(size.width / 2, size.height / 2)
        val base = size.minDimension * 0.25f
        val scale = 1f + breathe * pulse + 0.5f * lv
        // outer glow, a mid halo, then the core
        drawCircle(
            Brush.radialGradient(listOf(brand.copy(alpha = 0.34f), Color.Transparent), c, base * 2.0f * scale),
            base * 2.0f * scale, c,
        )
        drawCircle(brand.copy(alpha = 0.22f), base * 1.45f * scale, c)
        if (mode == OrbMode.Listening) drawCircle(brand.copy(alpha = 0.75f), base * 1.3f * scale, c, style = Stroke(2.dp.toPx()))
        drawCircle(
            Brush.radialGradient(listOf(soft, brand), Offset(c.x - base * 0.3f, c.y - base * 0.35f), base * 1.5f),
            base * (1f + 0.12f * lv + 0.03f * pulse), c,
        )
    }
}
