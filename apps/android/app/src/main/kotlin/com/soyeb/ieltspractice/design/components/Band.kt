package com.soyeb.ieltspractice.design.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.soyeb.ieltspractice.design.LocalReducedMotion
import com.soyeb.ieltspractice.design.NB
import com.soyeb.ieltspractice.design.Tone
import com.soyeb.ieltspractice.design.bandTone
import com.soyeb.ieltspractice.design.fg
import com.soyeb.ieltspractice.design.fill
import com.soyeb.ieltspractice.design.fmtBand
import com.soyeb.ieltspractice.design.soft

/** "6.5" on a soft tone fill. Neutral when there's no target. */
@Composable
fun BandPill(band: Double?, target: Double? = null, modifier: Modifier = Modifier, large: Boolean = false) {
    val tone = bandTone(band, target)
    Box(
        modifier.clip(NB.shape.chip).background(if (tone == Tone.Neutral) NB.colors.surface2 else tone.soft())
            .padding(horizontal = if (large) 10.dp else 8.dp, vertical = if (large) 4.dp else 2.dp)
            .semantics { contentDescription = "Band ${fmtBand(band)}" },
    ) {
        Text(fmtBand(band), style = if (large) NB.type.num else NB.type.label.copy(fontFeatureSettings = "tnum"), color = tone.fg())
    }
}

/**
 * A ring that fills to band/9 with the number in the middle. Used on result heroes and the Today overview.
 * Animates in once (skipped under reduced motion).
 */
@Composable
fun BandRing(
    band: Double?,
    target: Double? = null,
    size: Dp = 120.dp,
    stroke: Dp = 10.dp,
    label: String? = null,
    modifier: Modifier = Modifier,
    color: Color? = null,
) {
    val tone = bandTone(band, target)
    val fillColor = color ?: tone.fill()
    val track = NB.colors.surface2
    val reduced = LocalReducedMotion.current
    val progress = remember { Animatable(if (reduced) ((band ?: 0.0) / 9.0).toFloat() else 0f) }
    LaunchedEffect(band) { progress.animateTo(((band ?: 0.0) / 9.0).toFloat().coerceIn(0f, 1f), if (reduced) tween(0) else tween(700)) }
    Box(
        modifier.size(size).clearAndSetSemantics { contentDescription = (label?.let { "$it " } ?: "") + "band ${fmtBand(band)}" },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val sw = stroke.toPx()
            val inset = sw / 2
            val arcSize = androidx.compose.ui.geometry.Size(this.size.width - sw, this.size.height - sw)
            val tl = androidx.compose.ui.geometry.Offset(inset, inset)
            drawArc(track, -90f, 360f, false, tl, arcSize, style = Stroke(sw))
            drawArc(fillColor, -90f, 360f * progress.value, false, tl, arcSize, style = Stroke(sw, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(fmtBand(band), style = NB.type.band((size.value * 0.3f).toInt().coerceAtLeast(14)), color = NB.colors.ink)
            if (label != null && size >= 96.dp) Text(label, style = NB.type.caption, color = NB.colors.muted)
        }
    }
}

/**
 * Horizontal meter with an optional target tick. [value] and [target] are on 0..[max].
 * Used for criteria (max 9), raw scores (max 40), quotas.
 */
@Composable
fun Meter(
    value: Double?,
    modifier: Modifier = Modifier,
    max: Double = 9.0,
    target: Double? = null,
    color: Color = NB.colors.brand,
    height: Dp = 8.dp,
) {
    val track = NB.colors.surface2
    val tick = NB.colors.ink
    BoxWithConstraints(modifier.fillMaxWidth().height(height + 8.dp), contentAlignment = Alignment.CenterStart) {
        val w = maxWidth
        Box(Modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(height)).background(track)) {
            val f = ((value ?: 0.0) / max).toFloat().coerceIn(0f, 1f)
            Box(Modifier.fillMaxHeight().fillMaxWidth(f).clip(RoundedCornerShape(height)).background(color))
        }
        if (target != null) {
            val x = w * (target / max).toFloat().coerceIn(0f, 1f)
            Box(Modifier.offset(x = x - 1.dp).width(2.dp).fillMaxHeight().background(tick, RoundedCornerShape(1.dp)))
        }
    }
}

/** "Fluency & coherence   6.5" over a meter with the target tick. */
@Composable
fun CriterionBar(label: String, band: Double?, target: Double? = null, modifier: Modifier = Modifier) {
    val tone = bandTone(band, target)
    Column(modifier.fillMaxWidth().semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = NB.type.bodySmall, color = NB.colors.ink, modifier = Modifier.weight(1f))
            Text(fmtBand(band), style = NB.type.num, color = tone.fg())
        }
        Meter(band, target = target, color = tone.fill())
    }
}

/**
 * A band over time, compact (web BandTrend compact): a 2 dp line, the latest point as a dot, the target dashed. Oldest first.
 * Needs two points; one test is not a trend. Decorative for screen readers: [description] says what it shows.
 */
@Composable
fun Sparkline(bands: List<Double>, target: Double, color: Color, description: String, modifier: Modifier = Modifier) {
    if (bands.size < 2) return
    val c = NB.colors
    val lo = (minOf(bands.min(), target) - 0.5).coerceAtLeast(0.0)
    val hi = (maxOf(bands.max(), target) + 0.5).coerceAtMost(9.0)
    Canvas(modifier.clearAndSetSemantics { contentDescription = description }) {
        val pad = 4.dp.toPx()
        val w = size.width - pad * 2
        val h = size.height - pad * 2
        fun x(i: Int) = pad + w * i / (bands.size - 1)
        fun y(v: Double) = pad + h * (1 - ((v - lo) / (hi - lo)).toFloat())
        drawLine(
            c.line, androidx.compose.ui.geometry.Offset(pad, y(target)), androidx.compose.ui.geometry.Offset(pad + w, y(target)),
            strokeWidth = 1.dp.toPx(), pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())),
        )
        val path = androidx.compose.ui.graphics.Path().apply {
            bands.forEachIndexed { i, v -> if (i == 0) moveTo(x(i), y(v)) else lineTo(x(i), y(v)) }
        }
        drawPath(path, color, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
        drawCircle(color, 3.dp.toPx(), androidx.compose.ui.geometry.Offset(x(bands.lastIndex), y(bands.last())))
    }
}
