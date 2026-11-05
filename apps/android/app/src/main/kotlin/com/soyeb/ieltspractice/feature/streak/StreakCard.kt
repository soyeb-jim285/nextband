package com.soyeb.ieltspractice.feature.streak

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
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

/** Neighbouring levels stay at least 1.5:1 apart in lightness (ContrastTest), so they read by fill strength alone. */
internal fun NbColors.heat(role: HeatRole): Color = when (role) {
    // Empty days stay visible on a card: surface3 in light; dark lifts surface2 a little towards faint.
    HeatRole.Empty -> if (isDark) lerp(surface2, faint, 0.22f) else surface3
    HeatRole.Low -> lerp(brandSoft, brand, 0.4f)
    HeatRole.Mid -> lerp(brandSoft, brand, 0.7f)
    HeatRole.Full -> brand
    HeatRole.Frozen -> iceSoft
}

@Composable @ReadOnlyComposable
internal fun heatFill(role: HeatRole): Color = NB.colors.heat(role)

/** The flame: grey until today is done, then the warm warn colour. */
@Composable
internal fun Flame(done: Boolean, size: Dp, modifier: Modifier = Modifier, tint: Color = if (done) NB.colors.warn else NB.colors.faint) {
    Icon(Icons.Rounded.LocalFireDepartment, null, modifier.size(size), tint = tint)
}

@Composable @ReadOnlyComposable
internal fun countColor(done: Boolean): Color = if (done) NB.colors.warnText else NB.colors.ink

/**
 * One heatmap square. Today ([pending]: not practised yet) gets a brand ring, a tapped square an ink one; both sit outside a
 * gap before the fill, so the ring reads against the card whatever the fill (a week cell on the Today card can be full).
 */
@Composable
internal fun HeatCell(
    d: StreakDay?, today: String?, size: Dp, modifier: Modifier = Modifier, selected: Boolean = false,
    pending: Boolean = d != null && d.day == today && d.level <= 0,
) {
    val shape = RoundedCornerShape(size * 0.22f)
    if (d == null) { Spacer(modifier.size(size)); return }
    val role = heatRole(d)
    val base = modifier.size(size)
    Box(
        when {
            selected -> base.border(2.dp, NB.colors.ink, shape).padding(3.dp).background(heatFill(role), RoundedCornerShape((size - 6.dp) * 0.22f))
            pending -> (size * 0.14f).let { g -> base.border(1.5.dp, NB.colors.brand, shape).padding(g).background(heatFill(role), RoundedCornerShape((size - g * 2) * 0.22f)) }
            else -> base.background(heatFill(role), shape)
        },
        contentAlignment = Alignment.Center,
    ) {
        if (role == HeatRole.Frozen) Icon(Icons.Rounded.AcUnit, null, Modifier.size(size * 0.7f), tint = NB.colors.ice)
    }
}

private val ROW_LABELS = listOf("Mon", "", "Wed", "", "Fri", "", "")

/**
 * Week columns (Monday at the top) that share the width up to [maxCell] a square, placed by [align] when capped: nothing scrolls.
 * Month names sit over the column holding the 1st ([months], one per column); one that would touch the label before it is
 * dropped. The label sizes come from the text, so large fonts don't clip. A column may hold fewer than 7 squares.
 * [onSelect]: a tap anywhere on the grid picks the nearest square (one target, not 91 tiny ones). TalkBack reads one
 * summary per week; the caller can clear that and label the whole map instead. [todayDone]: whether the cell keyed [today] is
 * done, for a map whose cells aren't days (the Today card's weeks); by default a day cell is pending while its level is 0.
 */
@Composable
internal fun Heatmap(
    cols: List<List<StreakDay?>>,
    today: String?,
    gap: Dp,
    modifier: Modifier = Modifier,
    maxCell: Dp = 32.dp,
    rowLabels: Boolean = false,
    selected: String? = null,
    months: List<String?> = remember(cols) { monthLabels(cols) },
    onSelect: ((StreakDay) -> Unit)? = null,
    align: Alignment = Alignment.TopCenter,
    todayDone: Boolean? = null,
) {
    if (cols.isEmpty()) return
    val weeks = remember(cols, today) { cols.map { weekDescription(it, today) } }
    val caption = NB.type.caption
    val muted = NB.colors.muted
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val (labelWidth, monthRow) = remember(caption, density) {
        with(density) {
            val sizes = listOf("Mon", "Wed", "Fri").map { measurer.measure(it, caption, softWrap = false).size }
            sizes.maxOf { it.width }.toDp() to sizes.maxOf { it.height }.toDp()
        }
    }
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val lead = if (rowLabels) labelWidth + gap else 0.dp
        // ponytail: half a dp of slack so rounding never pushes the last column a pixel past the edge.
        val cell = ((maxWidth - lead - gap * (cols.size - 1) - 0.5.dp) / cols.size).coerceAtMost(maxCell)
        Row(Modifier.align(align), horizontalArrangement = Arrangement.spacedBy(gap)) {
            if (rowLabels) Column(Modifier.width(labelWidth).clearAndSetSemantics {}, verticalArrangement = Arrangement.spacedBy(gap)) {
                Spacer(Modifier.height(monthRow))
                // Taller than a square at large font sizes: the label spills into the empty rows around it, never clipped.
                ROW_LABELS.forEach {
                    Box(Modifier.height(cell), contentAlignment = Alignment.CenterStart) {
                        Text(it, style = caption, color = muted, softWrap = false, modifier = Modifier.wrapContentHeight(unbounded = true))
                    }
                }
            }
            val grid = cell * cols.size + gap * (cols.size - 1)
            // Wider than its column, a label runs over the next ones; near the edge it ends there instead.
            val starts = remember(months, caption, density, cell, gap) {
                with(density) {
                    val widths = months.map { m -> m?.let { measurer.measure(it, caption, softWrap = false).size.width.toFloat() } }
                    monthLabelStarts(widths, (cell + gap).toPx(), grid.toPx(), 4.dp.toPx()).map { it?.toDp() }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                Box(Modifier.width(grid).height(monthRow).clearAndSetSemantics {}) {
                    months.forEachIndexed { i, month ->
                        val x = starts[i]
                        if (month != null && x != null) {
                            Text(month, style = caption, color = muted, softWrap = false, modifier = Modifier.offset(x = x).wrapContentWidth(Alignment.Start, unbounded = true))
                        }
                    }
                }
                val tap = if (onSelect == null) Modifier else Modifier.pointerInput(cols, cell, onSelect) {
                    detectTapGestures { p ->
                        val pitch = (cell + gap).toPx()
                        val c = ((p.x + gap.toPx() / 2) / pitch).toInt().coerceIn(0, cols.lastIndex)
                        val r = ((p.y + gap.toPx() / 2) / pitch).toInt().coerceIn(0, cols[c].lastIndex)
                        cols[c][r]?.let(onSelect)
                    }
                }
                Row(tap, horizontalArrangement = Arrangement.spacedBy(gap)) {
                    cols.forEachIndexed { i, col ->
                        Column(
                            Modifier.clearAndSetSemantics { if (weeks[i].isNotEmpty()) contentDescription = weeks[i] },
                            verticalArrangement = Arrangement.spacedBy(gap),
                        ) {
                            col.forEach { d ->
                                val pending = d != null && d.day == today && (todayDone?.not() ?: (d.level <= 0))
                                HeatCell(d, today, cell, selected = d != null && d.day == selected, pending = pending)
                            }
                        }
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
                // Missed and future differ by shape, not only a faint tint: missed is filled with a dash, future is an
                // empty faint ring (3:1 on the card).
                val bg by animateColorAsState(target, tween(if (reduced) 0 else NbMotion.SLOW, easing = NbMotion.EaseOut), label = "dot")
                val edge = when (d.state) {
                    "today" -> 2.dp to c.brand
                    "future" -> 1.5.dp to c.faint
                    else -> null
                }
                Box(
                    Modifier.size(32.dp).background(bg, CircleShape).let { m -> edge?.let { m.border(it.first, it.second, CircleShape) } ?: m },
                    contentAlignment = Alignment.Center,
                ) {
                    when (d.state) {
                        "done" -> Icon(Icons.Rounded.Check, null, Modifier.size(18.dp), tint = c.onBrand)
                        "frozen" -> Icon(Icons.Rounded.AcUnit, null, Modifier.size(16.dp), tint = c.ice)
                        "missed" -> Icon(Icons.Rounded.Remove, null, Modifier.size(16.dp), tint = c.muted)
                    }
                }
            }
        }
    }
}

/** "❄ 1 freeze" */
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
        Text("$n ${plural(n, "freeze")}", style = NB.type.label.copy(fontFeatureSettings = "tnum"), color = NB.colors.ink)
        Spacer(Modifier.width(10.dp))
    }
}

/**
 * The Today card: flame and count with the freezes, the practised line, and 12 weeks as one row of squares (one per week,
 * darker = more practice days; this week is ringed until today is done) so the mock hero below stays in view; the streak page
 * keeps the day-by-day grid. The row starts under the text, not centred.
 * At risk (after 18:00, nothing done, a streak of 2+) the line warns and a Drill button saves it. The card opens the streak screen.
 */
@Composable
fun StreakCard(s: Streak, nav: Nav, hour: Int = hourNow()) {
    val risk = atRisk(s, hour)
    val cols = remember(s.days) { heatColumns(s.days, weeks = 12) }
    val n = remember(cols) { cols.sumOf { col -> col.count { it != null && it.level > 0 } } }
    val weeks = remember(cols) { weekCells(cols) }
    val months = remember(cols) { monthLabels(cols) }
    NbCard(onClick = { nav.go(StreakRoute) }, onClickLabel = "Open your streak") {
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
        Text(practisedLine(s, risk, n), style = NB.type.bodySmall, color = if (risk) NB.colors.ink else NB.colors.muted)
        Spacer(Modifier.height(14.dp))
        // The line above already says it for TalkBack.
        Heatmap(
            weeks, weeks.lastOrNull()?.firstOrNull()?.day, gap = 4.dp, maxCell = 24.dp, months = months,
            modifier = Modifier.clearAndSetSemantics {}, align = Alignment.TopStart, todayDone = s.done,
        )
        if (risk) {
            Spacer(Modifier.height(14.dp))
            NbButton("Drill", { nav.requireAccount("Create an account to drill your mistakes.") { nav.go(Review()) } }, kind = BtnKind.Secondary, fill = true)
        }
    }
}
