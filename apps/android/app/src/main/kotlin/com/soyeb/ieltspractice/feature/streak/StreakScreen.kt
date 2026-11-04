package com.soyeb.ieltspractice.feature.streak

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.soyeb.ieltspractice.LocalGraph
import com.soyeb.ieltspractice.data.model.Streak
import com.soyeb.ieltspractice.data.model.StreakDay
import com.soyeb.ieltspractice.design.NB
import com.soyeb.ieltspractice.design.components.Banner
import com.soyeb.ieltspractice.design.components.BtnSize
import com.soyeb.ieltspractice.design.components.LoadContent
import com.soyeb.ieltspractice.design.components.Meter
import com.soyeb.ieltspractice.design.components.NbButton
import com.soyeb.ieltspractice.design.components.NbCard
import com.soyeb.ieltspractice.design.components.PushedList
import com.soyeb.ieltspractice.design.components.PushedScreen
import com.soyeb.ieltspractice.design.components.SectionHeader
import com.soyeb.ieltspractice.design.components.rememberLoad
import com.soyeb.ieltspractice.nav.Nav
import com.soyeb.ieltspractice.nav.Review
import com.soyeb.ieltspractice.nav.Tab
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

private class StreakPage(val streak: Streak, val due: Int)

/** The streak page: count, this week, freezes, milestone, longest, 6 months of practice and one thing to do next. */
@Composable
fun StreakScreen(nav: Nav) {
    val g = LocalGraph.current
    val account by g.api.hasAccount.collectAsState()
    val loader = rememberLoad(account) {
        coroutineScope {
            val due = async { if (account) runCatching { g.api.dueCards().dueTotal }.getOrDefault(0) else 0 }
            StreakPage(g.api.streak(), due.await())
        }
    }
    PushedScreen("Your streak", nav::back) { pad ->
        LoadContent(loader, Modifier.padding(pad)) { page ->
            val s = page.streak
            PushedList(pad) {
                item { Hero(s) }
                s.repair?.let { r -> item { Banner(repairLine(r.target, r.daysDone), Icons.Rounded.Restore) } }
                item { WeekCard(s) }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SectionHeader("Last 6 months")
                        NbCard { SixMonths(s) }
                    }
                }
                item { NextCard(nav, page.due) }
            }
        }
    }
}

@Composable
private fun Hero(s: Streak) {
    Column(
        Modifier.fillMaxWidth().padding(top = 12.dp).semantics(mergeDescendants = true) { contentDescription = flameDescription(s); heading() },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Flame(s.done, 56.dp)
        Text("${s.current}", style = NB.type.display, color = countColor(s.done))
        Text("day streak", style = NB.type.title3, color = NB.colors.muted)
        // A broken streak with nothing to repair: calm, not a loss screen.
        if (s.current == 0 && s.repair == null && s.longest > 0) Text(
            "New streak, fresh start. Your longest is ${s.longest}.", style = NB.type.bodySmall, color = NB.colors.muted,
            textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
private fun WeekCard(s: Streak) {
    val c = NB.colors
    NbCard {
        if (s.week.isNotEmpty()) {
            WeekStrip(s.week, Modifier.fillMaxWidth())
            HorizontalDivider(Modifier.padding(vertical = 14.dp), color = c.line)
        }
        Row(Modifier.semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.AcUnit, null, Modifier.size(18.dp), tint = c.ice)
            Spacer(Modifier.width(8.dp))
            Text(freezeLine(s), style = NB.type.bodySmall, color = c.ink)
        }
        Spacer(Modifier.height(14.dp))
        val next = s.nextMilestone
        Column(Modifier.semantics(mergeDescendants = true) {}) {
            Text(
                if (next != null) "Next milestone: $next days" else "Every milestone reached",
                style = NB.type.label, color = c.ink,
            )
            Meter(milestoneFraction(s.current, next).toDouble(), max = 1.0, height = 8.dp)
        }
        Spacer(Modifier.height(6.dp))
        Text("Longest: ${s.longest} ${plural(s.longest, "day")}", style = NB.type.bodySmall, color = c.muted)
    }
}

/** 26 week columns with month labels, scrolled to today (reverse scrolling starts at the end), weekday labels pinned left. */
@Composable
private fun SixMonths(s: Streak) {
    val cols = heatColumns(s.days)
    val cell = 14.dp
    val gap = 4.dp
    Row {
        Column(Modifier.padding(top = 16.dp + gap).clearAndSetSemantics {}, verticalArrangement = Arrangement.spacedBy(gap)) {
            listOf("Mon", "", "Wed", "", "Fri", "", "").forEach {
                Box(Modifier.height(cell), contentAlignment = Alignment.CenterStart) { Text(it, style = NB.type.caption, color = NB.colors.muted) }
            }
        }
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f).horizontalScroll(rememberScrollState(), reverseScrolling = true)) {
            HeatColumns(cols, localToday(s), cell, gap, describe = true, labels = monthLabels(cols))
        }
    }
    Spacer(Modifier.height(12.dp))
    Legend()
}

/** Less ▢▢▢▢ More · ❄ frozen. The scale reads by fill strength, the freeze by its marker. */
@Composable
private fun Legend() {
    Row(
        Modifier.fillMaxWidth().clearAndSetSemantics {
            contentDescription = "Darker squares mean more practice: drills, a test, or a test and drills. Snowflake squares were kept by a freeze."
        },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("Less", style = NB.type.caption, color = NB.colors.muted)
        listOf(0, 1, 2, 3).forEach { HeatCell(StreakDay("", it), null, 12.dp) }
        Text("More", style = NB.type.caption, color = NB.colors.muted)
        Spacer(Modifier.weight(1f))
        HeatCell(StreakDay("", 0, frozen = true), null, 12.dp)
        Text("Frozen", style = NB.type.caption, color = NB.colors.muted)
    }
}

/** One thing to do: drills when some are due, else a practice test. */
@Composable
private fun NextCard(nav: Nav, due: Int) {
    NbCard {
        Text(
            if (due > 0) "Keep it going: $due ${plural(due, "drill")} ${if (due == 1) "is" else "are"} waiting" else "A practice test keeps your streak going",
            style = NB.type.title3, color = NB.colors.ink,
        )
        Spacer(Modifier.height(4.dp))
        Text("Five drills, one test part or ten minutes of listening or reading counts as a day.", style = NB.type.bodySmall, color = NB.colors.muted)
        Spacer(Modifier.height(14.dp))
        if (due > 0) NbButton("Drill now", { nav.go(Review()) }, fill = true, size = BtnSize.Large, haptic = true)
        else NbButton("Start a practice test", { nav.openTab(Tab.Listening) }, fill = true, size = BtnSize.Large, haptic = true)
    }
}
