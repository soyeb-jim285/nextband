package com.soyeb.ieltspractice.feature.streak

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
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

/** The sheet: the flame grows and the count ticks from N-1 to N while today fills in. Reduced motion: only the numbers change. */
@Composable
fun StreakCelebration(s: Streak, onDismiss: () -> Unit) {
    val reduced = LocalReducedMotion.current
    var filled by remember { mutableStateOf(reduced) }
    LaunchedEffect(Unit) { if (!filled) { delay(350); filled = true } }
    val ms = if (reduced) 0 else 600
    val count by animateIntAsState(if (filled) s.current else (s.current - 1).coerceAtLeast(0), tween(ms, easing = NbMotion.EaseOut), label = "count")
    val grow by animateFloatAsState(if (filled) 1f else 0.7f, tween(ms, easing = NbMotion.EaseOut), label = "grow")
    val today = localToday(s)
    // Until it fills, today shows as still to do.
    val week = if (filled) s.week else s.week.map { if (it.day == today && it.state == "done") it.copy(state = "today") else it }
    val milestone = s.justReached

    NbSheet(onDismiss, actions = { NbButton("Keep going", onDismiss, fill = true, size = BtnSize.Large) }) {
        Column(
            Modifier.fillMaxWidth().semantics(mergeDescendants = true) {
                contentDescription = "${flameDescription(s)}. ${celebrationLine(s.current)}"
                liveRegion = LiveRegionMode.Polite
            },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (milestone != null) Tag("$milestone-day milestone", fg = NB.colors.brandText, bg = NB.colors.brandSoft, modifier = Modifier.padding(bottom = 8.dp))
            val size = if (milestone != null) 96.dp else 72.dp
            Flame(filled, size, Modifier.graphicsLayer { scaleX = grow; scaleY = grow })
            Text("$count", style = NB.type.display, color = countColor(filled))
            Text(
                celebrationLine(s.current), style = NB.type.title1, color = NB.colors.ink, textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp).semantics { heading() },
            )
        }
        if (week.isNotEmpty()) WeekStrip(week, Modifier.fillMaxWidth().padding(top = 8.dp))
    }
}
