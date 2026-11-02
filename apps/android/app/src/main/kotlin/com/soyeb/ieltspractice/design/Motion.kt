package com.soyeb.ieltspractice.design

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.node.invalidateDraw
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Motion tokens. UI motion stays under 300 ms and eases out hard: things respond at once and settle softly.
 * No bounce. Respect [LocalReducedMotion] (durations collapse to 0).
 */
object NbMotion {
    /** Strong ease-out: entrances, presses, anything reacting to the user. */
    val EaseOut = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)
    /** Ease-in-out: something already on screen moving to a new place (tab indicator, segmented thumb). */
    val EaseInOut = CubicBezierEasing(0.77f, 0f, 0.175f, 1f)
    /** Sheets and drawers. */
    val Drawer = CubicBezierEasing(0.32f, 0.72f, 0f, 1f)

    const val FAST = 140
    const val BASE = 220
    const val SLOW = 300
}

/** Scales the element down slightly while pressed (0.97 by default). Pass the same source the clickable uses. */
@Composable
fun Modifier.pressScale(source: InteractionSource, pressed: Float = 0.97f): Modifier {
    val isPressed by source.collectIsPressedAsState()
    val reduced = LocalReducedMotion.current
    val s by animateFloatAsState(
        if (isPressed && !reduced) pressed else 1f,
        tween(if (isPressed) NbMotion.FAST else NbMotion.BASE, easing = NbMotion.EaseOut),
        label = "press",
    )
    return graphicsLayer { scaleX = s; scaleY = s }
}

/**
 * App-wide press feedback instead of the Material ripple: a soft ink wash that fades in on press and out on release.
 * Installed as LocalIndication in [NbTheme], so every `clickable` gets it. Clip the element first so the wash follows its shape.
 */
object PressWash : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode = Node(interactionSource)
    override fun equals(other: Any?) = other === this
    override fun hashCode() = 7

    private class Node(val source: InteractionSource) : Modifier.Node(), DrawModifierNode, CompositionLocalConsumerModifierNode {
        val a = Animatable(0f)

        override fun onAttach() {
            coroutineScope.launch {
                var down = 0
                source.interactions.collect { i ->
                    when (i) {
                        is PressInteraction.Press -> down++
                        is PressInteraction.Release, is PressInteraction.Cancel -> down = (down - 1).coerceAtLeast(0)
                        else -> return@collect
                    }
                    val target = if (down > 0) 1f else 0f
                    launch {
                        a.animateTo(target, tween(if (target == 1f) 90 else NbMotion.BASE, easing = NbMotion.EaseOut)) { invalidateDraw() }
                    }
                }
            }
        }

        override fun ContentDrawScope.draw() {
            drawContent()
            if (a.value > 0f) {
                val c = currentValueOf(LocalNbColors)
                drawRect(c.ink.copy(alpha = (if (c.isDark) 0.10f else 0.06f) * a.value))
            }
        }
    }
}

/**
 * Entrance for the [index]th item of a short list: fades in and rises 16 dp, 60 ms after the one before.
 * Content is laid out and readable from the first frame (only alpha/translation move); reduced motion shows it at once.
 */
@Composable
fun Modifier.rise(index: Int): Modifier {
    val reduced = LocalReducedMotion.current
    val p = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (p.value < 1f) { delay(60L * index); p.animateTo(1f, tween(NbMotion.SLOW, easing = NbMotion.EaseOut)) }
    }
    return graphicsLayer { alpha = p.value; translationY = (1f - p.value) * 16.dp.toPx() }
}
