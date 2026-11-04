package com.soyeb.ieltspractice.feature.streak

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.soyeb.ieltspractice.data.model.Streak
import com.soyeb.ieltspractice.data.model.StreakDay
import com.soyeb.ieltspractice.data.model.StreakWeekDay
import com.soyeb.ieltspractice.design.NB
import com.soyeb.ieltspractice.design.NbColors
import com.soyeb.ieltspractice.design.components.BtnKind
import com.soyeb.ieltspractice.design.components.NbButton
import com.soyeb.ieltspractice.design.components.NbCard
import com.soyeb.ieltspractice.feature.today.hourNow
import com.soyeb.ieltspractice.nav.Nav
import com.soyeb.ieltspractice.nav.Review
import com.soyeb.ieltspractice.nav.StreakRoute

// Visual rules (docs/engagement/notifications-and-streaks.md §3.4): warn goes ONLY on the flame and the count; the heatmap is a
// brand tint scale (brandSoft → brand, levels differ by fill strength); frozen days are an ice tint with a ❄; today is outlined.

// Ice: the sky tint of the listening tokens, kept as its own pair here so the skill hue stays identity-only. AA as listening/soft.
// ponytail: local pair, not an NbColors token; promote it when a second feature needs ice.
internal val NbColors.iceSoft: Color get() = if (isDark) Color(0xFF0B2536) else Color(0xFFE0F2FE)
internal val NbColors.ice: Color get() = if (isDark) Color(0xFF7DD3FC) else Color(0xFF0369A1)

@Composable @ReadOnlyComposable
internal fun heatFill(role: HeatRole): Color {
    val c = NB.colors
    return when (role) {
        HeatRole.Empty -> c.surface2
        HeatRole.Low -> lerp(c.brandSoft, c.brand, 0.3f)
        HeatRole.Mid -> lerp(c.brandSoft, c.brand, 0.62f)
        HeatRole.Full -> c.brand
        HeatRole.Frozen -> c.iceSoft
    }
}

/** The flame: grey until today is done, then the warm warn colour. */
@Composable
internal fun Flame(done: Boolean, size: Dp, modifier: Modifier = Modifier) {
    Icon(Icons.Rounded.LocalFireDepartment, null, modifier.size(size), tint = if (done) NB.colors.warn else NB.colors.faint)
}

@Composable @ReadOnlyComposable
internal fun countColor(done: Boolean): Color = if (done) NB.colors.warnText else NB.colors.ink

/** One heatmap square. Today is outlined until it's done. */
@Composable
internal fun HeatCell(d: StreakDay?, today: String?, size: Dp) {
    val shape = RoundedCornerShape(size / 4)
    if (d == null) { Spacer(Modifier.size(size)); return }
    val role = heatRole(d)
    val outline = d.day == today && d.level <= 0
    Box(
        Modifier.size(size).background(heatFill(role), shape)
            .let { if (outline) it.border(1.5.dp, NB.colors.brand, shape) else it },
        contentAlignment = Alignment.Center,
    ) {
        if (role == HeatRole.Frozen) Icon(Icons.Rounded.AcUnit, null, Modifier.size(size * 0.75f), tint = NB.colors.ice)
    }
}

/** Week columns, Monday at the top. [describe]: each column carries its own TalkBack summary (the streak screen). */
@Composable
internal fun HeatColumns(cols: List<List<StreakDay?>>, today: String?, cell: Dp, gap: Dp, describe: Boolean, labels: List<String?>? = null) {
    Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
        cols.forEachIndexed { i, col ->
            val m = if (describe) Modifier.clearAndSetSemantics { contentDescription = columnDescription(col) } else Modifier
            Column(m, verticalArrangement = Arrangement.spacedBy(gap)) {
                if (labels != null) Box(Modifier.width(cell).height(16.dp)) {
                    labels.getOrNull(i)?.let {
                        // Wider than its column: it runs over the next (empty) label slots.
                        Text(it, style = NB.type.caption, color = NB.colors.muted, softWrap = false, modifier = Modifier.wrapContentWidth(Alignment.Start, unbounded = true))
                    }
                }
                col.forEach { HeatCell(it, today, cell) }
            }
        }
    }
}

/** Monday to Sunday, one circle each. One TalkBack summary for the row. */
@Composable
internal fun WeekStrip(week: List<StreakWeekDay>, modifier: Modifier = Modifier) {
    val c = NB.colors
    val summary = "This week: " + week.joinToString(", ") { weekDayDescription(it.day, it.state) }
    Row(modifier.clearAndSetSemantics { contentDescription = summary }, horizontalArrangement = Arrangement.SpaceBetween) {
        week.forEach { d ->
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val isToday = d.state == "today"
                Text(
                    weekLetter(d.day), style = NB.type.caption,
                    color = if (isToday) c.brandText else c.muted, fontWeight = if (isToday) FontWeight.Bold else null,
                )
                val (bg, edge): Pair<Color, Pair<Dp, Color>?> = when (d.state) {
                    "done" -> c.brand to null
                    "frozen" -> c.iceSoft to null
                    "missed" -> c.surface2 to null
                    "today" -> Color.Transparent to (2.dp to c.brand)
                    else -> Color.Transparent to (1.dp to c.line)
                }
                Box(
                    Modifier.size(32.dp).background(bg, CircleShape).let { m -> edge?.let { m.border(it.first, it.second, CircleShape) } ?: m },
                    contentAlignment = Alignment.Center,
                ) {
                    when (d.state) {
                        "done" -> Icon(Icons.Rounded.Check, null, Modifier.size(18.dp), tint = c.onBrand)
                        "frozen" -> Icon(Icons.Rounded.AcUnit, null, Modifier.size(16.dp), tint = c.ice)
                    }
                }
            }
        }
    }
}

/** "❄ 1" */
@Composable
internal fun FreezeCount(n: Int) {
    Row(
        Modifier.background(NB.colors.iceSoft, NB.shape.pill).semantics(mergeDescendants = true) {
            contentDescription = "$n ${plural(n, "freeze")} saved"
        },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.width(8.dp))
        Icon(Icons.Rounded.AcUnit, null, Modifier.size(14.dp), tint = NB.colors.ice)
        Spacer(Modifier.width(4.dp))
        Text("$n", style = NB.type.label.copy(fontFeatureSettings = "tnum"), color = NB.colors.ink)
        Spacer(Modifier.width(10.dp))
    }
}

/**
 * The Today card: flame and count, freezes, the last 5 weeks, and the practised line. At risk (after 18:00, nothing done,
 * a streak of 2+) the line warns and a Drill button saves it. The whole card opens the streak screen.
 */
@Composable
fun StreakCard(s: Streak, nav: Nav, hour: Int = hourNow()) {
    val risk = atRisk(s, hour)
    val today = localToday(s)
    NbCard(onClick = { nav.go(StreakRoute) }) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(
                    Modifier.semantics(mergeDescendants = true) { contentDescription = flameDescription(s) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Flame(s.done, 28.dp)
                    Spacer(Modifier.width(6.dp))
                    Text("${s.current}", style = NB.type.headline.copy(fontFeatureSettings = "tnum"), color = countColor(s.done))
                    Spacer(Modifier.width(8.dp))
                    Text("day streak", style = NB.type.title3, color = NB.colors.ink)
                }
                Spacer(Modifier.height(6.dp))
                FreezeCount(s.freezes)
                Spacer(Modifier.height(10.dp))
                Text(practisedLine(s, risk), style = NB.type.bodySmall, color = if (risk) NB.colors.ink else NB.colors.muted)
            }
            Spacer(Modifier.width(12.dp))
            val cols = heatColumns(s.days, weeks = 5)
            val n = cols.flatten().count { it != null && it.level > 0 }
            Box(Modifier.semantics { contentDescription = "Last 5 weeks: $n practice ${plural(n, "day")}" }) {
                HeatColumns(cols, today, cell = 12.dp, gap = 3.dp, describe = false)
            }
        }
        if (risk) {
            Spacer(Modifier.height(12.dp))
            NbButton("Drill", { nav.requireAccount("Create an account to drill your mistakes.") { nav.go(Review()) } }, kind = BtnKind.Secondary, fill = true)
        }
    }
}
