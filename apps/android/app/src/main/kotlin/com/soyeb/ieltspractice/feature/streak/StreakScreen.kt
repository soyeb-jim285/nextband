package com.soyeb.ieltspractice.feature.streak

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
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
import com.soyeb.ieltspractice.design.components.PushedList
import com.soyeb.ieltspractice.design.components.PushedScreen
import com.soyeb.ieltspractice.design.components.rememberLoad
import com.soyeb.ieltspractice.nav.Nav
import com.soyeb.ieltspractice.nav.Review
import com.soyeb.ieltspractice.nav.Tab
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

private class StreakPage(val streak: Streak, val due: Int)

/**
 * The streak page, open on the surface (no cards): the count and this week, one ruled block of facts, the last 3 months
 * (tap a square for that day), and one button for what to do next.
 */
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
                s.repair?.let { r -> item { Banner(repairLine(r.target, r.daysDone), Icons.Rounded.Restore) } }
                item { Hero(s) }
                item { Facts(s) }
                item { LastThreeMonths(s) }
                item { Next(nav, page.due) }
            }
        }
    }
}

@Composable
private fun Hero(s: Streak) {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            Modifier.semantics(mergeDescendants = true) { contentDescription = flameDescription(s); heading() },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Flame(s.done, 52.dp)
            Spacer(Modifier.width(6.dp))
            Text("${s.current}", style = NB.type.display.copy(fontFeatureSettings = "tnum"), color = countColor(s.done))
        }
        Text("day streak", style = NB.type.title3, color = NB.colors.muted)
        // A broken streak with nothing to repair: calm, not a loss screen.
        if (s.current == 0 && s.repair == null && s.longest > 0) Text(
            "New streak, fresh start. Your longest is ${s.longest}.", style = NB.type.bodySmall, color = NB.colors.muted,
            textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp),
        )
        if (s.week.isNotEmpty()) WeekStrip(s.week, Modifier.fillMaxWidth().padding(top = 24.dp))
    }
}

/** Longest · Freezes / Next milestone · This month, between hairlines. */
@Composable
private fun Facts(s: Streak) {
    val c = NB.colors
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        HorizontalDivider(color = c.line)
        FactRow(
            { Fact("Longest") { Value("${s.longest} ${plural(s.longest, "day")}") } },
            {
                Fact("Freezes", "${s.freezes} ${plural(s.freezes, "freeze")} saved, ${freezeNote(s)}") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.AcUnit, null, Modifier.size(18.dp), tint = c.ice)
                        Spacer(Modifier.width(6.dp))
                        Value("${s.freezes}")
                    }
                    Text(freezeNote(s), style = NB.type.caption, color = c.muted)
                }
            },
        )
        HorizontalDivider(color = c.line)
        val next = s.nextMilestone
        FactRow(
            {
                if (next != null) Fact("Next milestone", "Next milestone: $next days, ${next - s.current} to go") {
                    Value("$next days")
                    Meter(milestoneFraction(s.current, next).toDouble(), Modifier.widthIn(max = 140.dp), max = 1.0, height = 4.dp)
                } else Fact("Milestones") { Value("All reached") }
            },
            { practisedThisMonth(s).let { n -> Fact("This month") { Value("$n practice ${plural(n, "day")}") } } },
        )
        HorizontalDivider(color = c.line)
    }
}

@Composable
private fun FactRow(start: @Composable ColumnScope.() -> Unit, end: @Composable ColumnScope.() -> Unit) {
    val line = NB.colors.line
    // The vertical hairline is drawn, not a VerticalDivider: IntrinsicSize can't measure the Meter (BoxWithConstraints).
    Row(Modifier.fillMaxWidth().drawBehind { drawLine(line, Offset(size.width / 2, 0f), Offset(size.width / 2, size.height), 1.dp.toPx()) }) {
        Column(Modifier.weight(1f).padding(top = 14.dp, bottom = 14.dp, end = 16.dp), content = start)
        Column(Modifier.weight(1f).padding(top = 14.dp, bottom = 14.dp, start = 16.dp), content = end)
    }
}

/** A caption label over its value. [spoken] replaces the pieces for TalkBack when they don't read well on their own. */
@Composable
private fun ColumnScope.Fact(label: String, spoken: String? = null, value: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().let { m -> if (spoken != null) m.clearAndSetSemantics { contentDescription = spoken } else m.semantics(mergeDescendants = true) {} },
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(label, style = NB.type.caption, color = NB.colors.muted)
        value()
    }
}

@Composable
private fun Value(text: String) {
    Text(text, style = NB.type.title2.copy(fontFeatureSettings = "tnum"), color = NB.colors.ink)
}

/**
 * 13 weeks across the full width; tapping the grid names the nearest day on the line below. TalkBack reads one summary per
 * week instead (13 stops, not 91), so the line isn't a live region and nothing is announced twice.
 */
@Composable
private fun LastThreeMonths(s: Streak) {
    val today = localToday(s)
    val cols = remember(s.days) { heatColumns(s.days, weeks = 13) }
    val n = remember(cols) { cols.sumOf { col -> col.count { it != null && it.level > 0 } } }
    var picked by rememberSaveable { mutableStateOf<String?>(null) }
    val onSelect = remember { { d: StreakDay -> picked = if (picked == d.day) null else d.day } }
    val day = picked?.let { p -> s.days.firstOrNull { it.day == p } }
    Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text("Last 3 months", style = NB.type.title2, color = NB.colors.ink, modifier = Modifier.weight(1f).semantics { heading() })
            Text("$n practice ${plural(n, "day")}", style = NB.type.caption, color = NB.colors.muted)
        }
        Heatmap(cols, today, gap = 3.dp, rowLabels = true, selected = picked, onSelect = onSelect)
        Text(
            day?.let { dayDescription(it, today) } ?: "Tap a square to see that day",
            style = NB.type.bodySmall, color = if (day != null) NB.colors.ink else NB.colors.muted,
        )
        Legend(today, s.done)
    }
}

/**
 * Less ▢▢▢▢ More · ❄ Freeze · ▢ Today. The scale reads by fill strength, the freeze by its marker. Two groups that wrap
 * at large font sizes; "Today" shows only while today's square is outlined (not done yet).
 */
@Composable
private fun Legend(today: String?, done: Boolean) {
    val cap = NB.type.caption
    val muted = NB.colors.muted
    FlowRow(
        Modifier.fillMaxWidth().clearAndSetSemantics {
            contentDescription = "Darker squares mean more practice: drills, a test, or a test and drills. Snowflake squares were kept by a freeze." +
                if (done) "" else " Today is outlined until you practise."
        },
        horizontalArrangement = Arrangement.SpaceBetween, verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Less", style = cap, color = muted)
            listOf(0, 1, 2, 3).forEach { HeatCell(StreakDay("", it), null, 12.dp) }
            Text("More", style = cap, color = muted)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            HeatCell(StreakDay("", 0, frozen = true), null, 12.dp)
            Text("Freeze", style = cap, color = muted)
            if (!done) {
                Spacer(Modifier.width(6.dp))
                HeatCell(StreakDay(today ?: "today", 0), today ?: "today", 12.dp)
                Text("Today", style = cap, color = muted)
            }
        }
    }
}

/** One thing to do: drills when some are due, else a practice test. A plain button, no card. */
@Composable
private fun Next(nav: Nav, due: Int) {
    Column(Modifier.fillMaxWidth().padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            (if (due > 0) "$due ${plural(due, "drill")} ${if (due == 1) "is" else "are"} waiting. " else "") +
                "One test part, 5 drills or 10 minutes of Listening or Reading counts as a day.",
            style = NB.type.bodySmall, color = NB.colors.muted,
        )
        if (due > 0) NbButton("Drill now", { nav.go(Review()) }, fill = true, size = BtnSize.Large, haptic = true)
        else NbButton("Start a practice test", { nav.openTab(Tab.Listening) }, fill = true, size = BtnSize.Large, haptic = true)
    }
}
