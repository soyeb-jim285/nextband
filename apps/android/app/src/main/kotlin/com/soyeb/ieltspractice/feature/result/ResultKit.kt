package com.soyeb.ieltspractice.feature.result

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.soyeb.ieltspractice.data.logic.Crit
import com.soyeb.ieltspractice.data.logic.criterionLabel
import com.soyeb.ieltspractice.data.model.Criterion
import com.soyeb.ieltspractice.data.model.Fix
import com.soyeb.ieltspractice.design.LocalReducedMotion
import com.soyeb.ieltspractice.design.NB
import com.soyeb.ieltspractice.design.Skill
import com.soyeb.ieltspractice.design.bandTone
import com.soyeb.ieltspractice.design.components.BandRing
import com.soyeb.ieltspractice.design.components.Banner
import com.soyeb.ieltspractice.design.components.BtnKind
import com.soyeb.ieltspractice.design.components.CardTone
import com.soyeb.ieltspractice.design.components.CriterionBar
import com.soyeb.ieltspractice.design.components.IconTile
import com.soyeb.ieltspractice.design.components.NbButton
import com.soyeb.ieltspractice.design.components.NbCard
import com.soyeb.ieltspractice.design.components.Overline
import com.soyeb.ieltspractice.design.components.SkillTile
import com.soyeb.ieltspractice.design.fg
import com.soyeb.ieltspractice.design.fmtBand
import com.soyeb.ieltspractice.nav.Auth
import com.soyeb.ieltspractice.nav.Nav

// Shared pieces of every result screen (speaking, writing, listening/reading, mock). One look for "here is your band".

/**
 * The top of a result: skill tile + overline, a big band ring, and how it compares with the target.
 * [range] (e.g. 6.0–7.0) shows the honest uncertainty under the number.
 */
@Composable
fun ResultHero(
    skill: Skill,
    overline: String,
    title: String,
    band: Double?,
    target: Double?,
    modifier: Modifier = Modifier,
    range: String? = null,
    note: String? = null,
) {
    NbCard(modifier, tone = CardTone.Default) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SkillTile(skill, 36.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Overline(overline)
                Text(title, style = NB.type.title3, color = NB.colors.ink, maxLines = 2)
            }
        }
        Spacer(Modifier.size(20.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            BandRing(band, target, size = 116.dp, stroke = 10.dp, label = "Band")
            Spacer(Modifier.width(20.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val tone = bandTone(band, target)
                if (target != null && band != null) {
                    val diff = band - target
                    Text(
                        when {
                            diff >= 0 -> "At or above your ${fmtBand(target)} target"
                            else -> "${fmtBand(-diff)} below your ${fmtBand(target)} target"
                        },
                        style = NB.type.title3, color = tone.fg(),
                    )
                }
                if (range != null) Text("Likely range $range", style = NB.type.bodySmall, color = NB.colors.muted)
                if (note != null) Text(note, style = NB.type.bodySmall, color = NB.colors.muted)
            }
        }
    }
}

/** The four criteria as meters with the target tick, in the official order for [skill] ("speaking" | "writing"). */
@Composable
fun CriteriaCard(skill: String, criteria: Map<String, Criterion>, target: Double?, modifier: Modifier = Modifier, onOpen: ((String) -> Unit)? = null) {
    NbCard(modifier) {
        Text("By criterion", style = NB.type.title2, color = NB.colors.ink)
        Spacer(Modifier.size(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            val keys = Crit.order(skill).filter { it in criteria } + criteria.keys.filter { it !in Crit.order(skill) }
            keys.forEach { k ->
                val c = criteria.getValue(k)
                val bar: @Composable () -> Unit = { CriterionBar(criterionLabel(k), c.band, target) }
                if (onOpen != null) NbCard(tone = CardTone.Inset, onClick = { onOpen(k) }, padding = androidx.compose.foundation.layout.PaddingValues(12.dp)) { bar() }
                else bar()
            }
        }
    }
}

/** One "top fix": why, then before → after. */
@Composable
fun FixCard(index: Int, fix: Fix, modifier: Modifier = Modifier) {
    NbCard(modifier) {
        Row(verticalAlignment = Alignment.Top) {
            Text("$index", style = NB.type.title2.copy(fontFeatureSettings = "tnum"), color = NB.colors.brandText, modifier = Modifier.width(28.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(fix.title, style = NB.type.title3, color = NB.colors.ink)
                Text(fix.why, style = NB.type.bodySmall, color = NB.colors.muted)
                if (fix.before.isNotBlank()) Text(fix.before, style = NB.type.readingSmall, color = NB.colors.badText)
                if (fix.after.isNotBlank()) Text(fix.after, style = NB.type.readingSmall, color = NB.colors.goodText)
            }
        }
    }
}

/** While the server analyses. [stage]: transcribing | analyzing | feedback | scoring | finalizing. */
@Composable
fun AnalysingState(stage: String?, skill: Skill, modifier: Modifier = Modifier) {
    val steps = listOf("transcribing" to "Transcribing", "analyzing" to "Reading your answer", "feedback" to "Writing feedback", "scoring" to "Scoring", "finalizing" to "Finishing up")
        .let { if (skill == Skill.Writing) it.drop(1) else it }
    val at = steps.indexOfFirst { it.first == stage }.coerceAtLeast(0)
    val reduced = LocalReducedMotion.current
    val pulse = if (reduced) 1f else rememberInfiniteTransition(label = "an").animateFloat(0.55f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "p").value
    Column(
        modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 48.dp).semantics { liveRegion = LiveRegionMode.Polite },
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        IconTile(Icons.Rounded.AutoAwesome, NB.colors.brandText, NB.colors.brandSoft, 72.dp, Modifier.alpha(pulse))
        Text("Marking your ${skill.label.lowercase()}", style = NB.type.title1, color = NB.colors.ink, textAlign = TextAlign.Center)
        Text("Usually under a minute. You can leave this screen; the result will be in History.", style = NB.type.bodySmall, color = NB.colors.muted, textAlign = TextAlign.Center)
        LinearProgressIndicator(
            progress = { (at + 1f) / steps.size }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            color = NB.colors.brand, trackColor = NB.colors.surface2,
        )
        Text(steps[at].second + "…", style = NB.type.label, color = NB.colors.brandText)
    }
}

/** Marking failed. [retryable] false = a server-side problem; retrying now won't help. */
@Composable
fun FailedState(message: String?, retryable: Boolean, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        IconTile(Icons.Rounded.ErrorOutline, NB.colors.badText, NB.colors.badSoft, 72.dp)
        Text("We couldn't mark this one", style = NB.type.title1, color = NB.colors.ink, textAlign = TextAlign.Center)
        Text(
            message?.takeIf { it.isNotBlank() } ?: if (retryable) "Something went wrong while marking. Your answer is saved." else "Marking is paused for now. Your answer is saved; try again later.",
            style = NB.type.bodySmall, color = NB.colors.muted, textAlign = TextAlign.Center,
        )
        if (retryable) NbButton("Try marking again", onRetry, icon = Icons.Rounded.Refresh)
    }
}

/** Speaking with no usable speech. */
@Composable
fun NoSpeechState(onRetake: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        IconTile(Icons.Rounded.MicOff, NB.colors.warnText, NB.colors.warnSoft, 72.dp)
        Text("We couldn't hear an answer", style = NB.type.title1, color = NB.colors.ink, textAlign = TextAlign.Center)
        Text("Check your microphone, hold the phone a little closer and try again.", style = NB.type.bodySmall, color = NB.colors.muted, textAlign = TextAlign.Center)
        NbButton("Try again", onRetake, kind = BtnKind.Secondary)
    }
}

/** Under a guest's result: it is kept for a while, but only an account keeps it for good. */
@Composable
fun GuestResultBar(nav: Nav, modifier: Modifier = Modifier) {
    Surface(modifier.fillMaxWidth(), color = NB.colors.surface, contentColor = NB.colors.ink) {
        Column {
            HorizontalDivider(color = NB.colors.line)
            Banner(
                "Create an account to keep this result.", Icons.Rounded.Person,
                Modifier.navigationBarsPadding().padding(horizontal = NB.space.gutter, vertical = 10.dp),
                action = "Create account", onAction = { nav.go(Auth("signup", "Create an account to keep this result")) },
            )
        }
    }
}
