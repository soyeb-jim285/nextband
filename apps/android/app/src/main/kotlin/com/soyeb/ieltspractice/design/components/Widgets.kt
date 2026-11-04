package com.soyeb.ieltspractice.design.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import com.soyeb.ieltspractice.design.PressWash
import com.soyeb.ieltspractice.design.pressScale
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.soyeb.ieltspractice.design.NB
import com.soyeb.ieltspractice.design.Skill
import com.soyeb.ieltspractice.design.color

/** "mm:ss" (or "h:mm:ss"). */
fun clockText(seconds: Long): String {
    val s = seconds.coerceAtLeast(0)
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) String.format(java.util.Locale.US, "%d:%02d:%02d", h, m, sec) else String.format(java.util.Locale.US, "%02d:%02d", m, sec)
}

/**
 * Spoken time left for TalkBack. Whole minutes, then "under a minute" in the last one (not "0 minutes"). It stays coarse
 * on purpose: the pill is a live region in the warn/bad zones, so a per-second description would announce every tick.
 */
internal fun timeLeftWords(seconds: Long): String = when {
    seconds < 60 -> "under a minute"
    seconds < 120 -> "1 minute"
    else -> "${seconds / 60} minutes"
}

/**
 * Countdown pill. Neutral, then warn under [warnAt] seconds, bad under [badAt]; a haptic tick when crossing each threshold.
 * Announces only at the thresholds (not every second) for TalkBack.
 */
@Composable
fun TimerPill(secondsLeft: Long, modifier: Modifier = Modifier, warnAt: Long = 300, badAt: Long = 60, label: String = "Time left") {
    val c = NB.colors
    val (fg, bg) = when {
        secondsLeft <= badAt -> c.badText to c.badSoft
        secondsLeft <= warnAt -> c.warnText to c.warnSoft
        else -> c.ink to c.surface2
    }
    val hf = LocalHapticFeedback.current
    val zone = if (secondsLeft <= badAt) 2 else if (secondsLeft <= warnAt) 1 else 0
    LaunchedEffect(zone) { if (zone > 0) hf.performHapticFeedback(HapticFeedbackType.LongPress) }
    Row(
        modifier.heightIn(min = 36.dp).background(bg, NB.shape.pill).padding(horizontal = 12.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = "$label ${timeLeftWords(secondsLeft)}"
                if (zone > 0) liveRegion = LiveRegionMode.Polite
            },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(Icons.Rounded.Timer, null, Modifier.size(18.dp), tint = fg)
        Text(clockText(secondsLeft), style = NB.type.num, color = fg)
    }
}

/**
 * The slim full-mock entry at the top of each skill tab. [resumeText] (e.g. "Reading next") switches it to resume mode.
 */
@Composable
fun MockStrip(onClick: () -> Unit, modifier: Modifier = Modifier, resumeText: String? = null) {
    val c = NB.colors
    val src = remember { MutableInteractionSource() }
    Surface(shape = NB.shape.tile, color = c.surface, contentColor = c.ink, modifier = modifier.fillMaxWidth().pressScale(src, 0.985f)
        .let { if (!c.isDark) it.border(1.dp, c.line, NB.shape.tile) else it }
        .clip(NB.shape.tile).clickable(src, PressWash, role = Role.Button, onClick = onClick)) {
        Row(Modifier.heightIn(min = 56.dp).padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            IconTile(Icons.Rounded.WorkspacePremium, c.brandText, c.brandSoft, 36.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(if (resumeText != null) "Resume your full mock" else "Full mock test", style = NB.type.title3, color = c.ink)
                Text(resumeText ?: "All four tests · about 2 h 45 m", style = NB.type.caption, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            SkillDots()
            Spacer(Modifier.width(6.dp))
            Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, Modifier.size(20.dp), tint = c.brandText)
        }
    }
}

/** Four coloured dots L R W S: a compact "all four tests" signal. */
@Composable
fun SkillDots(modifier: Modifier = Modifier, done: Set<Skill> = emptySet()) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        Skill.entries.forEach { s ->
            Box(Modifier.size(8.dp).background(if (done.isEmpty() || s in done) s.color else NB.colors.surface3, CircleShape))
        }
    }
}

enum class StepState { Done, Current, Upcoming }

/**
 * One row of a vertical stepper (mock hub, onboarding). Draws the rail between steps; [last] hides the rail below.
 */
@Composable
fun StepRow(
    index: Int,
    title: String,
    subtitle: String?,
    state: StepState,
    last: Boolean,
    modifier: Modifier = Modifier,
    accent: Color = NB.colors.brand,
    trailing: @Composable () -> Unit = {},
) {
    val c = NB.colors
    Row(modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Column(Modifier.width(36.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(32.dp).background(
                    when (state) { StepState.Done -> accent; StepState.Current -> c.brandSoft; StepState.Upcoming -> c.surface2 }, CircleShape,
                ).let { if (state == StepState.Current) it.border(2.dp, accent, CircleShape) else it },
                contentAlignment = Alignment.Center,
            ) {
                if (state == StepState.Done) Icon(Icons.Rounded.Check, "Done", Modifier.size(18.dp), tint = c.onBrand)
                else Text("$index", style = NB.type.label, color = if (state == StepState.Current) c.brandText else c.muted)
            }
            if (!last) Box(Modifier.width(2.dp).weight(1f).padding(vertical = 4.dp).background(if (state == StepState.Done) accent else c.line))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f).padding(top = 4.dp, bottom = if (last) 0.dp else 24.dp)) {
            Text(title, style = NB.type.title3, color = if (state == StepState.Upcoming) c.muted else c.ink)
            if (subtitle != null) Text(subtitle, style = NB.type.bodySmall, color = c.muted)
            trailing()
        }
    }
}

/** Small stat: value over a caption. */
@Composable
fun Stat(value: String, caption: String, modifier: Modifier = Modifier, valueColor: Color = NB.colors.ink) {
    Column(modifier.semantics(mergeDescendants = true) {}) {
        Text(value, style = NB.type.title1.copy(fontFeatureSettings = "tnum"), color = valueColor)
        Text(caption, style = NB.type.caption, color = NB.colors.muted)
    }
}

/** Thin vertical hairline between [Stat]s in a row. */
@Composable
fun VDivider(modifier: Modifier = Modifier) {
    Box(modifier.width(1.dp).height(32.dp).background(NB.colors.line))
}
