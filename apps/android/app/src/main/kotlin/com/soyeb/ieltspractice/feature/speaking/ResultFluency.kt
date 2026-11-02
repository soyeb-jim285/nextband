package com.soyeb.ieltspractice.feature.speaking

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.soyeb.ieltspractice.data.logic.DisfluencyKind
import com.soyeb.ieltspractice.data.logic.DisfluencyKinds
import com.soyeb.ieltspractice.data.logic.FluStat
import com.soyeb.ieltspractice.data.logic.FluTone
import com.soyeb.ieltspractice.data.logic.MarkerType
import com.soyeb.ieltspractice.data.logic.Timeline
import com.soyeb.ieltspractice.data.logic.clock
import com.soyeb.ieltspractice.data.logic.fluStats
import com.soyeb.ieltspractice.data.logic.fluTooShort
import com.soyeb.ieltspractice.data.logic.fluUpTo
import com.soyeb.ieltspractice.data.logic.fmt
import com.soyeb.ieltspractice.data.logic.isLongPause
import com.soyeb.ieltspractice.data.logic.perMinute
import com.soyeb.ieltspractice.data.logic.wpmAt
import com.soyeb.ieltspractice.data.model.Criterion
import com.soyeb.ieltspractice.data.model.Disfluency
import com.soyeb.ieltspractice.data.model.SpeechMetrics
import com.soyeb.ieltspractice.design.NB
import com.soyeb.ieltspractice.design.Tone
import com.soyeb.ieltspractice.design.components.Banner
import com.soyeb.ieltspractice.design.components.NbCard
import com.soyeb.ieltspractice.design.components.SectionHeader
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// The Fluency tab (web FluencyPanel.tsx, iOS FluencyView): pace chart with the speaking timeline, pause strip, the measures against
// typical band-7 speech, then fillers, repeats and restarts. Every verdict is an icon plus words; colour is never the only cue.

@Composable
fun FluencyPanel(
    metrics: SpeechMetrics, player: ResultPlayer, timeline: Timeline, focus: String?, onFocus: (String?) -> Unit,
    fc: Criterion?, target: Double,
) {
    val c = NB.colors
    val tooShort = fluTooShort(metrics)
    val pauseCount = metrics.pauses.size
    val longPauses = metrics.pauses.count(::isLongPause)
    val stats = fluStats(metrics)
    Column(verticalArrangement = Arrangement.spacedBy(NB.space.lg)) {
        if (fc != null && fc.band < target && !tooShort && stats.none { it.tone == FluTone.Bad }) {
            Banner(
                "Your delivery measures look fine, but Fluency & Coherence is ${fmt(fc.band)}. The band is limited by something these numbers don't capture, such as answer length, relevance or how ideas connect. ${fc.summary}",
                Icons.Rounded.Info,
            )
        }
        Column { SectionHeader("Pace") }
        if (metrics.wpmSeries.size < 2) {
            NbCard { Text("This answer is too short for a pace chart (it needs at least 15 seconds).", style = NB.type.bodySmall, color = c.muted) }
        } else PaceCard(metrics, timeline, player, focus, onFocus)

        Column {
            SectionHeader("Pauses")
            Text(
                "$pauseCount ${if (pauseCount == 1) "pause" else "pauses"}, $longPauses long, ${metrics.midClausePauses} mid-clause.${if (player.isLoaded) " Tap one to hear it." else ""}",
                style = NB.type.bodySmall, color = c.muted, modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
        NbCard {
            Strip(
                metrics.durationS, metrics.pauses.map { p -> Mark(p.start, p.end, if (isLongPause(p)) c.bad else c.warn.copy(alpha = 0.6f)) },
                player, "$pauseCount pauses, $longPauses long",
            ) { player.seek(max(0.0, it - 1)) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("0:00", style = NB.type.caption.copy(fontFeatureSettings = "tnum"), color = c.muted)
                Text(clock(metrics.durationS.roundToInt()), style = NB.type.caption.copy(fontFeatureSettings = "tnum"), color = c.muted)
            }
            Swatches(listOf(c.warn.copy(alpha = 0.6f) to "Short, 0.25–1 s", c.bad to "Long, 1 s or more"))
        }
        Column {
            SectionHeader("Fluency measures")
            Text(
                if (tooShort) "Not enough speech to measure. Answer for at least 20 seconds to see these." else "Compared with typical band-7 speech, not the score.",
                style = NB.type.bodySmall, color = c.muted, modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
        StatGrid(stats)
        metrics.fluency?.let { Disfluencies(it.events, metrics, player, tooShort) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Swatches(items: List<Pair<Color, String>>) {
    FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items.forEach { (color, label) ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(width = 12.dp, height = 10.dp).background(color, RoundedCornerShape(2.dp)))
                Text(label, style = NB.type.caption, color = NB.colors.muted)
            }
        }
    }
}

// MARK: Verdict labels

@Composable
private fun IndicatorLabel(t: FluTone) {
    val c = NB.colors
    val (icon, text, color) = when (t) {
        FluTone.Good -> Triple(Icons.Rounded.CheckCircle, "On target", c.goodText)
        FluTone.Warn -> Triple(Icons.Rounded.Warning, "Watch", c.warnText)
        FluTone.Bad -> Triple(Icons.Rounded.Close, "Work on this", c.badText)
        FluTone.Na -> return
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(icon, null, Modifier.size(14.dp), tint = color)
        Text(text, style = NB.type.caption, color = color)
    }
}

// MARK: Measures

@Composable
private fun StatGrid(stats: List<FluStat>) {
    val c = NB.colors
    NbCard(padding = PaddingValues(0.dp)) {
        stats.chunked(2).forEachIndexed { i, pair ->
            if (i > 0) HorizontalDivider(color = c.line)
            Row(Modifier.height(IntrinsicSize.Min)) {
                StatCell(pair[0], Modifier.weight(1f))
                Box(Modifier.width(1.dp).fillMaxHeight().background(c.line))
                if (pair.size > 1) StatCell(pair[1], Modifier.weight(1f)) else Box(Modifier.weight(1f))
            }
        }
    }
}

/** One measure: label, the value, and where it sits against typical band-7 speech. Tap for what it means. */
@Composable
private fun StatCell(stat: FluStat, modifier: Modifier) {
    val c = NB.colors
    var showInfo by remember { mutableStateOf(false) }
    Column(
        modifier.clickable(role = Role.Button) { showInfo = !showInfo }
            .semantics { stateDescription = if (showInfo) "Explanation shown" else "Explanation hidden" }
            .padding(start = 14.dp, end = 10.dp, top = 12.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stat.label, Modifier.weight(1f, fill = false), style = NB.type.bodySmall, color = c.muted)
            Icon(Icons.Rounded.Info, null, Modifier.padding(start = 4.dp).size(14.dp), tint = c.faint)
        }
        Text(stat.value, style = NB.type.band(20), color = c.ink)
        IndicatorLabel(stat.tone)
        if (showInfo) Text(stat.info, style = NB.type.caption, color = c.muted, modifier = Modifier.padding(top = 4.dp))
    }
}

// MARK: Strips

private class Mark(val start: Double, val end: Double, val color: Color)

/** Whole-recording bar with marks placed on it. Tap near a mark to hear it; the playhead follows playback. */
@Composable
private fun Strip(durationS: Double, marks: List<Mark>, player: ResultPlayer, summary: String, onPick: (Double) -> Unit) {
    val c = NB.colors
    val d = max(durationS, 1.0)
    Canvas(
        Modifier.fillMaxWidth().height(44.dp).semantics { contentDescription = summary }
            .pointerInput(marks, d) {
                detectTapGestures { off ->
                    val w = size.width.toFloat()
                    var best: Double? = null
                    var bestDist = 22.dp.toPx()
                    for (m in marks) {
                        val dist = abs(w * ((m.start + (m.end - m.start) / 2) / d).toFloat() - off.x)
                        if (dist <= bestDist) { bestDist = dist; best = m.start }
                    }
                    best?.let(onPick)
                }
            },
    ) {
        drawRoundRect(c.surface2, Offset.Zero, size, CornerRadius(8.dp.toPx()))
        val top = (size.height - 28.dp.toPx()) / 2
        for (m in marks) {
            val x = min(max(size.width - 4.dp.toPx(), 0f), size.width * (m.start / d).toFloat())
            val w = max(4.dp.toPx(), size.width * ((m.end - m.start) / d).toFloat())
            drawRoundRect(m.color, Offset(x, top), Size(w, 28.dp.toPx()), CornerRadius(3.dp.toPx()))
        }
        if (player.isLoaded) {
            val x = min(size.width - 2.dp.toPx(), size.width * (max(player.currentTime, 0.0) / d).toFloat())
            drawRect(c.brand, Offset(x, (size.height - 40.dp.toPx()) / 2), Size(2.dp.toPx(), 40.dp.toPx()))
        }
    }
}

// MARK: Fillers, repeats and restarts

@Composable
private fun kindColor(kind: String): Color = with(NB.colors) {
    when (kind) {
        "filled" -> muted
        "repetition" -> listening
        "repair" -> bad
        "false_start" -> warn
        "partial" -> faint
        "prolongation" -> reading
        else -> muted
    }
}

@Composable
private fun Disfluencies(events: List<Disfluency>, metrics: SpeechMetrics, player: ResultPlayer, tooShort: Boolean) {
    val c = NB.colors
    val kinds = DisfluencyKinds.all.filter { k -> k.core || events.any { it.kind == k.key } }
    Column {
        SectionHeader("Fillers, repeats and restarts")
        Text(
            if (player.isLoaded) "Each mark is one event along your answer. Tap a mark to hear it." else "Each mark is one event along your answer.",
            style = NB.type.bodySmall, color = c.muted, modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
    NbCard {
        Strip(
            metrics.durationS, events.map { Mark(it.start, max(it.end, it.start + 0.15), kindColor(it.kind)) }, player,
            "${events.size} fillers, repeats and restarts along the recording",
        ) { player.seek(max(0.0, it - 1)) }
        Swatches(kinds.filter { k -> events.any { it.kind == k.key } }.map { kindColor(it.key) to it.label })
    }
    kinds.forEach { Breakdown(it, events, metrics, tooShort) }
}

/** One card per disfluency type: how many, how often, and what is normal versus what hurts coherence. */
@Composable
private fun Breakdown(k: DisfluencyKind, events: List<Disfluency>, metrics: SpeechMetrics, tooShort: Boolean) {
    val c = NB.colors
    val n = events.count { it.kind == k.key }
    val perMin = perMinute(n, max(metrics.durationS, 1.0))
    val tone = if (tooShort) FluTone.Na else fluUpTo(perMin, k.rate[0], k.rate[1])
    var open by remember { mutableStateOf(false) }
    NbCard {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(k.label, style = NB.type.title3, color = c.ink)
                Text(k.what, style = NB.type.bodySmall, color = c.muted)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("$n", style = NB.type.band(22), color = c.ink)
                if (!tooShort) Text("${fmt(perMin)}/min", style = NB.type.caption.copy(fontFeatureSettings = "tnum"), color = c.muted)
            }
        }
        IndicatorLabel(tone)
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button) { open = !open }
                .semantics { stateDescription = if (open) "Expanded" else "Collapsed" },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("When is this a problem?", Modifier.weight(1f), style = NB.type.label, color = c.brandText)
            Icon(if (open) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown, null, tint = c.muted)
        }
        if (open) Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(labelled("Normal: ", k.normal), style = NB.type.bodySmall)
            Text(labelled("Hurts when: ", k.harmful), style = NB.type.bodySmall)
        }
    }
}

@Composable
private fun labelled(label: String, text: String): AnnotatedString = buildAnnotatedString {
    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = NB.colors.ink)) { append(label) }
    withStyle(SpanStyle(color = NB.colors.muted)) { append(text) }
}

// MARK: Pace chart with the speaking timeline (web WpmChart in FluencyPanel.tsx)

/**
 * Pace over time with the typical band-7 zone. Mistakes sit on the line (shape and colour per type), questions are dashed dividers, long
 * pauses are shaded, and the teal line follows the audio. Without a recording the playhead is hidden and tapping just selects.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PaceCard(metrics: SpeechMetrics, timeline: Timeline, player: ResultPlayer, focus: String?, onFocus: (String?) -> Unit) {
    val c = NB.colors
    val series = metrics.wpmSeries
    val duration = max(metrics.durationS, 1.0)
    val yMax = max(200.0, ceil((series.maxOfOrNull { it.wpm } ?: 0.0) / 50.0) * 50.0 + 20.0)
    val colors = MarkerType.entries.associateWith { it.color() }
    val focused = focus?.let { id -> timeline.markers.firstOrNull { it.id == id } }
    val lowest = series.minOfOrNull { it.wpm }?.roundToInt()
    val highest = series.maxOfOrNull { it.wpm }?.roundToInt()
    NbCard {
        Canvas(
            Modifier.fillMaxWidth().height(180.dp)
                .semantics { contentDescription = "Pace over time, from about $lowest to $highest words a minute. The typical band 7 range is 120 to 170." }
                .pointerInput(timeline, duration, player.isLoaded) {
                    detectTapGestures { off ->
                        val w = size.width.toFloat()
                        val near = timeline.markers.minByOrNull { abs(w * (it.t / duration).toFloat() - off.x) }
                        if (near != null && abs(w * (near.t / duration).toFloat() - off.x) <= 24.dp.toPx()) {
                            onFocus(near.id)
                            if (player.isLoaded) player.seek(max(0.0, near.t - 0.3))
                        } else {
                            onFocus(null)
                            if (player.isLoaded) player.seek(duration * (off.x / w))
                        }
                    }
                },
        ) {
            val w = size.width
            val h = size.height
            fun x(t: Double) = (w * (t / duration)).toFloat()
            fun y(v: Double) = (h - (v / yMax * h)).toFloat()
            // typical band-7 pace
            drawRect(c.brand.copy(alpha = 0.12f), Offset(0f, y(170.0)), Size(w, y(120.0) - y(170.0)))
            for (p in timeline.pauses) drawRect(c.bad.copy(alpha = 0.14f), Offset(x(p.start), 0f), Size(max(2.dp.toPx(), x(p.end) - x(p.start)), h))
            for (q in timeline.questions.drop(1)) {
                drawLine(c.faint, Offset(x(q.start), 0f), Offset(x(q.start), h), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
            }
            val path = Path()
            series.forEachIndexed { i, pt ->
                val px = x(pt.t + 5)
                if (i == 0) path.moveTo(px, y(pt.wpm)) else path.lineTo(px, y(pt.wpm))
            }
            drawPath(path, c.ink.copy(alpha = 0.85f), style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            for (m in timeline.markers) {
                val at = Offset(x(m.t), y(wpmAt(series, m.t)))
                if (m.id == focus) drawCircle(c.ink, 9.dp.toPx(), at, style = Stroke(2.dp.toPx()))
                drawMarker(m.type, at, 5.dp.toPx(), colors.getValue(m.type), c.surface)
            }
            if (player.isLoaded) {
                val px = x(player.currentTime.coerceIn(0.0, duration))
                drawLine(c.brand, Offset(px, 0f), Offset(px, h), 2.dp.toPx())
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("0:00", style = NB.type.caption.copy(fontFeatureSettings = "tnum"), color = c.muted)
            Text(clock(duration.roundToInt()), style = NB.type.caption.copy(fontFeatureSettings = "tnum"), color = c.muted)
        }
        FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            MarkerType.entries.filter { timeline.count(it) > 0 }.forEach { t ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    MarkerGlyph(t, 9.dp)
                    Text("${t.label} ${timeline.count(t)}", style = NB.type.caption, color = c.muted)
                }
            }
        }
        Swatches(listOf(c.brand.copy(alpha = 0.3f) to "Typical band 7: 120–170 wpm", c.bad.copy(alpha = 0.3f) to "Long pause"))
        if (focused != null) {
            Text(
                "${focused.type.label} at ${clock(focused.t.roundToInt())}: ${focused.label}",
                style = NB.type.bodySmall, color = c.ink, modifier = Modifier.padding(top = 10.dp),
            )
        }
    }
}
