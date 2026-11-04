package com.soyeb.ieltspractice.feature.streak

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.soyeb.ieltspractice.data.model.Streak
import com.soyeb.ieltspractice.data.model.StreakDay
import com.soyeb.ieltspractice.data.model.StreakWeekDay
import com.soyeb.ieltspractice.design.LocalReducedMotion
import com.soyeb.ieltspractice.design.NB
import com.soyeb.ieltspractice.design.NbColors
import com.soyeb.ieltspractice.design.NbMotion
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
        // Dark: surface2 all but vanishes on a card, so empty days lift a little towards faint.
        HeatRole.Empty -> if (c.isDark) lerp(c.surface2, c.faint, 0.22f) else c.surface2
        HeatRole.Low -> lerp(c.brandSoft, c.brand, 0.3f)
        HeatRole.Mid -> lerp(c.brandSoft, c.brand, 0.62f)
        HeatRole.Full -> c.brand
        HeatRole.Frozen -> c.iceSoft
    }
}

/** The flame: grey until today is done, then the warm warn colour. */
@Composable
internal fun Flame(done: Boolean, size: Dp, modifier: Modifier = Modifier, tint: Color = if (done) NB.colors.warn else NB.colors.faint) {
    Icon(Icons.Rounded.LocalFireDepartment, null, modifier.size(size), tint = tint)
}

@Composable @ReadOnlyComposable
internal fun countColor(done: Boolean): Color = if (done) NB.colors.warnText else NB.colors.ink

/** One heatmap square. Today is outlined until it's done; a tapped square is ringed in ink. */
@Composable
internal fun HeatCell(d: StreakDay?, today: String?, size: Dp, modifier: Modifier = Modifier, selected: Boolean = false) {
    val shape = RoundedCornerShape(size * 0.22f)
    if (d == null) { Spacer(modifier.size(size)); return }
    val role = heatRole(d)
    val edge = when {
        selected -> 2.dp to NB.colors.ink
        d.day == today && d.level <= 0 -> 1.5.dp to NB.colors.brand
        else -> null
    }
    Box(
        modifier.size(size).background(heatFill(role), shape).let { m -> edge?.let { m.border(it.first, it.second, shape) } ?: m },
        contentAlignment = Alignment.Center,
    ) {
        if (role == HeatRole.Frozen) Icon(Icons.Rounded.AcUnit, null, Modifier.size(size * 0.7f), tint = NB.colors.ice)
    }
}

private val ROW_LABELS = listOf("Mon", "", "Wed", "", "Fri", "", "")
private val ROW_LABEL_WIDTH = 30.dp
private val MONTH_ROW = 16.dp

/**
 * Week columns (Monday at the top) that share the full width: the cell size comes from the space, so nothing scrolls or
 * clips. Month names sit over the column holding the 1st. [onSelect] makes each square tappable and gives it its own
 * TalkBack label (the streak screen); without it the caller labels the whole map.
 */
@Composable
internal fun Heatmap(
    cols: List<List<StreakDay?>>,
    today: String?,
    gap: Dp,
    modifier: Modifier = Modifier,
    rowLabels: Boolean = false,
    selected: String? = null,
    onSelect: ((StreakDay) -> Unit)? = null,
) {
    if (cols.isEmpty()) return
    val months = monthLabels(cols)
    val caption = NB.type.caption
    val muted = NB.colors.muted
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val lead = if (rowLabels) ROW_LABEL_WIDTH + gap else 0.dp
        // ponytail: half a dp of slack so rounding never pushes the last column a pixel past the edge.
        val cell = ((maxWidth - lead - gap * (cols.size - 1) - 0.5.dp) / cols.size).coerceAtMost(32.dp)
        Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
            if (rowLabels) Column(Modifier.width(ROW_LABEL_WIDTH).clearAndSetSemantics {}, verticalArrangement = Arrangement.spacedBy(gap)) {
                Spacer(Modifier.height(MONTH_ROW))
                ROW_LABELS.forEach { Box(Modifier.height(cell), contentAlignment = Alignment.CenterStart) { Text(it, style = caption, color = muted) } }
            }
            cols.forEachIndexed { i, col ->
                Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                    Box(Modifier.width(cell).height(MONTH_ROW).clearAndSetSemantics {}) {
                        months[i]?.let {
                            // Wider than its column, it runs over the next ones; the last column's label ends at the edge instead.
                            val align = if (i == cols.lastIndex) Alignment.End else Alignment.Start
                            Text(it, style = caption, color = muted, softWrap = false, modifier = Modifier.align(Alignment.TopStart).wrapContentWidth(align, unbounded = true))
                        }
                    }
                    col.forEach { d ->
                        val m = if (onSelect != null && d != null) Modifier
                            .clickable(role = Role.Button, onClickLabel = "Show this day") { onSelect(d) }
                            .clearAndSetSemantics { contentDescription = dayDescription(d, today) }
                        else Modifier
                        HeatCell(d, today, cell, m, selected = d != null && d.day == selected)
                    }
                }
            }
        }
    }
}

/** Monday to Sunday, one circle each. One TalkBack summary for the row. A day that turns done fills in softly. */
@Composable
internal fun WeekStrip(week: List<StreakWeekDay>, modifier: Modifier = Modifier) {
    val c = NB.colors
    val reduced = LocalReducedMotion.current
    val summary = "This week: " + week.joinToString(", ") { weekDayDescription(it.day, it.state) }
    Row(modifier.clearAndSetSemantics { contentDescription = summary }, horizontalArrangement = Arrangement.SpaceBetween) {
        week.forEach { d ->
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val isToday = d.state == "today"
                Text(
                    weekLetter(d.day), style = NB.type.caption,
                    color = if (isToday) c.brandText else c.muted, fontWeight = if (isToday) FontWeight.Bold else null,
                )
                val target = when (d.state) {
                    "done" -> c.brand
                    "frozen" -> c.iceSoft
                    "missed" -> c.surface2
                    else -> Color.Transparent
                }
                val bg by animateColorAsState(target, tween(if (reduced) 0 else NbMotion.SLOW, easing = NbMotion.EaseOut), label = "dot")
                val edge = when (d.state) {
                    "today" -> 2.dp to c.brand
                    "future" -> 1.dp to c.line
                    else -> null
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
 * The Today card: flame and count with the freezes, the practised line, and 12 weeks of practice across the full width.
 * At risk (after 18:00, nothing done, a streak of 2+) the line warns and a Drill button saves it. The card opens the streak screen.
 */
@Composable
fun StreakCard(s: Streak, nav: Nav, hour: Int = hourNow()) {
    val risk = atRisk(s, hour)
    NbCard(onClick = { nav.go(StreakRoute) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.weight(1f).semantics(mergeDescendants = true) { contentDescription = flameDescription(s) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Flame(s.done, 28.dp)
                Spacer(Modifier.width(6.dp))
                Text("${s.current}", style = NB.type.headline.copy(fontFeatureSettings = "tnum"), color = countColor(s.done))
                Spacer(Modifier.width(8.dp))
                Text("day streak", style = NB.type.title3, color = NB.colors.ink)
            }
            FreezeCount(s.freezes)
        }
        Spacer(Modifier.height(4.dp))
        Text(practisedLine(s, risk), style = NB.type.bodySmall, color = if (risk) NB.colors.ink else NB.colors.muted)
        Spacer(Modifier.height(14.dp))
        val cols = heatColumns(s.days, weeks = 12)
        val n = cols.flatten().count { it != null && it.level > 0 }
        Heatmap(cols, localToday(s), gap = 4.dp, modifier = Modifier.clearAndSetSemantics { contentDescription = "Last 12 weeks: $n practice ${plural(n, "day")}" })
        if (risk) {
            Spacer(Modifier.height(14.dp))
            NbButton("Drill", { nav.requireAccount("Create an account to drill your mistakes.") { nav.go(Review()) } }, kind = BtnKind.Secondary, fill = true)
        }
    }
}
