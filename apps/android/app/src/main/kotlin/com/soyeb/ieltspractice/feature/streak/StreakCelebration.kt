package com.soyeb.ieltspractice.feature.streak

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.soyeb.ieltspractice.LocalDemo
import com.soyeb.ieltspractice.LocalGraph
import com.soyeb.ieltspractice.data.model.Streak
import com.soyeb.ieltspractice.design.LocalReducedMotion
import com.soyeb.ieltspractice.design.NB
import com.soyeb.ieltspractice.design.NbMotion
import com.soyeb.ieltspractice.design.components.BtnSize
import com.soyeb.ieltspractice.design.components.NbButton
import com.soyeb.ieltspractice.design.components.NbSheet
import com.soyeb.ieltspractice.design.components.Tag
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

/** Put on a screen reached by finishing practice (a result, the drill summary): reloads the streak, which may celebrate. */
@Composable
fun StreakAfterPractice() {
    val store = LocalGraph.current.streak
    LaunchedEffect(Unit) { store.refresh(afterPractice = true) }
}

/**
 * Draws the daily celebration over whatever screen is open (AppShell, next to GateHost). Demo runs stay quiet except the
 * `streak-celebration` screenshot, which opens it from the fixture.
 */
@Composable
fun StreakCelebrationHost() {
    val g = LocalGraph.current
    val demo = LocalDemo.current
    if (demo?.screen == "streak-celebration") LaunchedEffect(Unit) { g.streak.celebrating = runCatching { g.api.streak() }.getOrNull() }
    val s = g.streak.celebrating ?: return
    if (demo != null && demo.screen != "streak-celebration") return
    StreakCelebration(s) { g.streak.celebrating = null }
}

/**
 * The sheet: after a beat the flame lights (grey to warm, a little growth and a soft bloom behind it) and the count steps up
 * from N-1 to N while today's dot fills in. A milestone adds a ring of sparks. Reduced motion: it opens already lit.
 */
@Composable
fun StreakCelebration(s: Streak, onDismiss: () -> Unit) {
    val reduced = LocalReducedMotion.current
    var filled by remember { mutableStateOf(reduced) }
    val bloom = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        if (filled) return@LaunchedEffect
        delay(450)
        filled = true
        bloom.animateTo(1f, tween(900, easing = LinearOutSlowInEasing))
    }
    val ms = if (reduced) 0 else 500
    val lit = updateTransition(filled, label = "lit")
    val grow by lit.animateFloat({ tween(ms, easing = NbMotion.EaseOut) }, label = "grow") { if (it) 1f else 0.8f }
    val flameTint by lit.animateColor({ tween(ms, easing = NbMotion.EaseOut) }, label = "flameTint") { if (it) NB.colors.warn else NB.colors.faint }
    val today = localToday(s)
    // Until it fills, today shows as still to do.
    val week = if (filled) s.week else s.week.map { if (it.day == today && it.state == "done") it.copy(state = "today") else it }
    val milestone = s.justReached
    val flameSize = if (milestone != null) 72.dp else 56.dp
    val warm = NB.colors.warn
    val brand = NB.colors.brand

    NbSheet(onDismiss, actions = { NbButton("Keep going", onDismiss, fill = true, size = BtnSize.Large) }) {
        Column(
            Modifier.fillMaxWidth().semantics(mergeDescendants = true) {
                contentDescription = "${flameDescription(s)}. ${celebrationLine(s.current)}"
                liveRegion = LiveRegionMode.Polite
            },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (milestone != null) Tag("$milestone-day milestone", fg = NB.colors.brandText, bg = NB.colors.brandSoft, modifier = Modifier.padding(bottom = 12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.drawBehind {
                        // Read in the draw phase only: the bloom redraws each frame without recomposing.
                        val p = bloom.value
                        if (p <= 0f || p >= 1f) return@drawBehind
                        val r = size.minDimension * (0.55f + 0.6f * p)
                        val a = 0.4f * (1f - p) * (p * 5f).coerceAtMost(1f)
                        drawCircle(Brush.radialGradient(listOf(warm.copy(alpha = a), Color.Transparent), center, r), r)
                        if (milestone != null) repeat(10) { i ->
                            val angle = Math.toRadians(i * 36.0 - 90.0)
                            val d = size.minDimension * (0.45f + 0.75f * p)
                            drawCircle(
                                (if (i % 2 == 0) warm else brand).copy(alpha = 1f - p), 3.dp.toPx() * (1f - p * 0.6f),
                                center + Offset((cos(angle) * d).toFloat(), (sin(angle) * d).toFloat()),
                            )
                        }
                    },
                ) { Flame(filled, flameSize, Modifier.graphicsLayer { scaleX = grow; scaleY = grow }, tint = flameTint) }
                Spacer(Modifier.width(6.dp))
                AnimatedContent(
                    if (filled) s.current else (s.current - 1).coerceAtLeast(0),
                    transitionSpec = {
                        (slideInVertically(tween(ms, easing = NbMotion.EaseOut)) { it / 2 } + fadeIn(tween(ms))) togetherWith
                            (slideOutVertically(tween(ms, easing = NbMotion.EaseOut)) { -it / 2 } + fadeOut(tween(ms / 2)))
                    },
                    label = "count",
                ) { n -> Text("$n", style = NB.type.display.copy(fontFeatureSettings = "tnum"), color = countColor(n == s.current)) }
            }
            Text(
                celebrationLine(s.current), style = NB.type.title1, color = NB.colors.ink, textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp).semantics { heading() },
            )
        }
        if (week.isNotEmpty()) WeekStrip(week, Modifier.fillMaxWidth().padding(top = 20.dp))
        s.nextMilestone?.let { next ->
            Text(
                "Next milestone: $next days, ${next - s.current} to go", style = NB.type.bodySmall, color = NB.colors.muted,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            )
        }
    }
}
