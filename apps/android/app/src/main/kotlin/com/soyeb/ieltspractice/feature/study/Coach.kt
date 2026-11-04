package com.soyeb.ieltspractice.feature.study

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.ThumbDown
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.soyeb.ieltspractice.LocalDemo
import com.soyeb.ieltspractice.LocalGraph
import com.soyeb.ieltspractice.data.logic.formatDate
import com.soyeb.ieltspractice.data.model.Coach
import com.soyeb.ieltspractice.data.model.CoachCandidate
import com.soyeb.ieltspractice.data.model.CoachFocus
import com.soyeb.ieltspractice.data.model.CoachReport
import com.soyeb.ieltspractice.design.NB
import com.soyeb.ieltspractice.design.Skill
import com.soyeb.ieltspractice.design.Tone
import com.soyeb.ieltspractice.design.components.Banner
import com.soyeb.ieltspractice.design.components.BandPill
import com.soyeb.ieltspractice.design.components.BandRing
import com.soyeb.ieltspractice.design.components.BtnKind
import com.soyeb.ieltspractice.design.components.BtnSize
import com.soyeb.ieltspractice.design.components.CardTone
import com.soyeb.ieltspractice.design.components.IconTile
import com.soyeb.ieltspractice.design.components.LoadContent
import com.soyeb.ieltspractice.design.components.LocalSnack
import com.soyeb.ieltspractice.design.components.NbButton
import com.soyeb.ieltspractice.design.components.NbCard
import com.soyeb.ieltspractice.design.components.NbIconButton
import com.soyeb.ieltspractice.design.components.NbListRow
import com.soyeb.ieltspractice.design.components.Overline
import com.soyeb.ieltspractice.design.components.PollWhileVisible
import com.soyeb.ieltspractice.design.components.PushedList
import com.soyeb.ieltspractice.design.components.PushedScreen
import com.soyeb.ieltspractice.design.components.RowDivider
import com.soyeb.ieltspractice.design.components.RowGroup
import com.soyeb.ieltspractice.design.components.SectionHeader
import com.soyeb.ieltspractice.design.components.SkillTile
import com.soyeb.ieltspractice.design.components.Stat
import com.soyeb.ieltspractice.design.components.StickyActionBar
import com.soyeb.ieltspractice.design.components.VDivider
import com.soyeb.ieltspractice.design.components.rememberLoad
import com.soyeb.ieltspractice.nav.AttemptResult
import com.soyeb.ieltspractice.nav.Mistakes
import com.soyeb.ieltspractice.nav.Nav
import com.soyeb.ieltspractice.nav.LrResult
import com.soyeb.ieltspractice.nav.MockStart
import com.soyeb.ieltspractice.nav.Review
import com.soyeb.ieltspractice.nav.SpeakingSession
import com.soyeb.ieltspractice.nav.Tab
import com.soyeb.ieltspractice.nav.WritingTask
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.intOrNull

/** The coach report: where marks are lost, what to do about it, and a plan for the week. */
@Composable
fun CoachScreen(nav: Nav) {
    val g = LocalGraph.current
    val api = g.api
    val account by api.hasAccount.collectAsState()
    val loader = rememberLoad(account) { if (account) api.coach() else Coach() }
    val coach = loader.value
    val demo = LocalDemo.current != null

    // A fresh report is being written: look again every 5 s until it lands. (Not in screenshots.)
    val updating = coach?.updating == true
    PollWhileVisible(updating && !demo) { loader.reload() }

    PushedScreen(
        "Study plan", nav::back,
        bottomBar = {
            if (account && coach != null && !coach.ready) StickyActionBar {
                // A short test first: any mix of tests unlocks the coach, a full mock is the heaviest way there.
                NbButton("Take a Listening test", { nav.openTab(Tab.Listening) }, fill = true, size = BtnSize.Large)
                NbButton("Take a full mock", { nav.go(MockStart) }, kind = BtnKind.Text, fill = true)
            }
        },
    ) { pad ->
        if (!account) {
            GuestGate(
                Icons.Rounded.Lightbulb, "Your coach",
                "Create an account and the coach will read your results, find what keeps costing you marks and plan your week.",
                nav, "Create an account to get your coach report", Modifier.padding(pad),
            )
        } else LoadContent(loader, Modifier.padding(pad)) { c ->
            val report = c.report
            if (c.ready && report != null) ReportBody(nav, pad, c, report) else EmptyBody(pad, c.updating)
        }
    }
}

@Composable
private fun EmptyBody(pad: androidx.compose.foundation.layout.PaddingValues, updating: Boolean) {
    PushedList(pad) {
        if (updating) item { Banner("Your first report is being written. This page updates by itself.", Icons.Rounded.Autorenew) }
        item {
            NbCard(tone = CardTone.Hero) {
                Overline("Your coach", color = NB.colors.brandText)
                Text("A plan built from your results", style = NB.type.title1, color = NB.colors.ink, modifier = Modifier.padding(top = 4.dp))
                Text(
                    "The coach reads your finished tests and the mistakes in them, finds what keeps costing you marks, and writes a short report with a plan for the week.",
                    style = NB.type.body, color = NB.colors.ink, modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
        item { SectionHeader("What it needs") }
        item {
            RowGroup {
                NeedRow(Icons.Rounded.CheckCircle, "Finish a few tests", "Any mix of Listening, Reading, Writing and Speaking.")
                RowDivider()
                NeedRow(Icons.Rounded.Timer, "Let the marking finish", "Writing and Speaking are marked in about a minute.")
                RowDivider()
                NeedRow(Icons.Rounded.Autorenew, "Come back", "The report updates itself after new tests.")
            }
        }
    }
}

@Composable
private fun NeedRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, body: String) {
    NbListRow(title, subtitle = body, leading = { IconTile(icon, NB.colors.brandText, NB.colors.brandSoft, 40.dp) })
}

@Composable
private fun ReportBody(nav: Nav, pad: androidx.compose.foundation.layout.PaddingValues, c: Coach, r: CoachReport) {
    val g = LocalGraph.current
    val scope = rememberCoroutineScope()
    val snack = LocalSnack.current
    val facts = r.facts
    val words = r.words
    val localHidden = remember(r.id) { mutableStateListOf<String>() }
    fun hidden(key: String) = key in r.hidden || key in localHidden

    fun wrong(key: String) {
        scope.launch {
            val msg = try {
                g.api.coachFeedback(r.id, key)
                localHidden.add(key)
                "Thanks. We have hidden this one."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                e.message?.takeIf { it.isNotBlank() } ?: "Couldn't send that. Try again."
            }
            snack.showSnackbar(msg)
        }
    }

    val focus = words.focus.filter { !hidden("focus:${it.candidateId}") }
    val cheat = words.cheat.filter { !hidden("cheat:${it.candidateId}") }

    PushedList(pad) {
        if (c.updating) item { Banner("A fresh report is being written. This page updates by itself.", Icons.Rounded.Autorenew) }
        item { SummaryCard(r) }
        words.win?.takeIf { it.isNotBlank() }?.let { w -> item { Banner(w, Icons.Rounded.WorkspacePremium, tone = Tone.Good) } }

        if (focus.isNotEmpty()) {
            item { SectionHeader("What to work on") }
            items(focus.size, key = { "focus:" + focus[it].candidateId }) { i ->
                val f = focus[i]
                FocusCard(nav, scope, f, facts.candidates.firstOrNull { it.id == f.candidateId }, onWrong = { wrong("focus:${f.candidateId}") })
            }
        }

        if (words.plan.isNotEmpty()) {
            item { SectionHeader("Your week") }
            item { PlanStrip(nav, scope, r) }
        }

        if (facts.skills.isNotEmpty()) {
            item { SectionHeader("By skill") }
            item {
                RowGroup {
                    facts.skills.forEachIndexed { i, s ->
                        if (i > 0) RowDivider()
                        val sk = Skill.of(s.skill)
                        val lead: (@Composable () -> Unit)? = if (sk != null) ({ SkillTile(sk, 40.dp) }) else null
                        NbListRow(
                            title = sk?.label ?: s.skill.replaceFirstChar { it.uppercase() },
                            subtitle = skillLine(s.tests, s.trend, s.trendDelta, s.needMore),
                            leading = lead,
                            trailing = { BandPill(s.band, facts.target, large = true) },
                        )
                    }
                }
            }
        }

        if (cheat.isNotEmpty()) {
            item { SectionHeader("Cheat sheet") }
            item {
                RowGroup {
                    cheat.forEachIndexed { i, ch ->
                        if (i > 0) RowDivider()
                        Row(Modifier.fillMaxWidth().padding(start = NB.space.lg, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(ch.text, style = NB.type.body, color = NB.colors.ink, modifier = Modifier.weight(1f).padding(vertical = 8.dp))
                            NbIconButton(Icons.Rounded.ThumbDown, "This doesn't look right", { wrong("cheat:${ch.candidateId}") })
                        }
                    }
                }
            }
        }

        item {
            Text(
                "Written ${formatDate(r.createdAt).ifEmpty { "recently" }}. It updates after you finish new tests.",
                style = NB.type.caption, color = NB.colors.muted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun SummaryCard(r: CoachReport) {
    val f = r.facts
    NbCard(tone = CardTone.Hero) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Overline("Your coach", color = NB.colors.brandText)
                Text(r.words.summary.ifBlank { "Here is where your marks are going." }, style = NB.type.body, color = NB.colors.ink)
            }
            f.overall?.let { BandRing(it.band, f.target, size = 92.dp, stroke = 8.dp, label = "Overall") }
        }
        val days = f.daysToExam
        if (f.totalTests > 0 || f.target != null || days != null) {
            Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Stat("${f.totalTests}", plural(f.totalTests, "test") + " taken")
                f.target?.let { VDivider(); Stat(com.soyeb.ieltspractice.design.fmtBand(it), "Target") }
                days?.let { VDivider(); Stat("$it", if (it == 1) "day to exam" else "days to exam") }
            }
        }
    }
}

@Composable
private fun FocusCard(nav: Nav, scope: CoroutineScope, f: CoachFocus, cand: CoachCandidate?, onWrong: () -> Unit) {
    val g = LocalGraph.current
    var open by remember { mutableStateOf(false) }
    val sk = Skill.of(cand?.skill)
    val part = cand?.facts?.get("part")?.intOrNull
    val label = f.label.ifBlank { actionLabel(f.action) }
    val hasAction = actionLabel(f.action) != null
    NbCard(Modifier.animateContentSize()) {
        Row(verticalAlignment = Alignment.Top) {
            if (sk != null) { SkillTile(sk, 36.dp); Spacer(Modifier.width(12.dp)) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (sk != null) Overline(sk.label)
                Text(f.title, style = NB.type.title3, color = NB.colors.ink)
            }
            NbIconButton(Icons.Rounded.ThumbDown, "This doesn't look right", onWrong)
        }
        Text(f.why, style = NB.type.bodySmall, color = NB.colors.muted, modifier = Modifier.padding(top = 4.dp))
        if (hasAction && label != null) {
            NbButton(label, { openAction(nav, scope, { g.prefs.testType.first() }, f.action, part) }, kind = BtnKind.Secondary, modifier = Modifier.padding(top = 12.dp))
        }
        if (cand != null) {
            NbButton(
                if (open) "Hide the evidence" else "Why the coach says this", { open = !open }, kind = BtnKind.Text, size = BtnSize.Small,
                trailingIcon = if (open) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
            )
            if (open) Evidence(nav, cand)
        }
    }
}

@Composable
private fun Evidence(nav: Nav, cand: CoachCandidate) {
    Column(Modifier.fillMaxWidth().padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (cand.count > 0) Text(
            "Seen ${cand.count} ${plural(cand.count, "time")} across ${cand.tests} ${plural(cand.tests, "test")}.",
            style = NB.type.bodySmall, color = NB.colors.ink,
        )
        val ev = cand.evidence
        ev.attemptIds.firstOrNull()?.let { id -> NbButton("Open an example", { nav.go(AttemptResult.of(id)) }, kind = BtnKind.Text, size = BtnSize.Small) }
        ev.lr.firstOrNull()?.let { ref -> NbButton("Open an example question", { nav.go(LrResult(ref.attemptId)) }, kind = BtnKind.Text, size = BtnSize.Small) }
        if (ev.mistakeIds.isNotEmpty()) NbButton("See these in Patterns", { nav.go(Mistakes()) }, kind = BtnKind.Text, size = BtnSize.Small)
    }
}

/** Seven days as a strip you can tick off; the ticks stay on this device. */
@Composable
private fun PlanStrip(nav: Nav, scope: CoroutineScope, r: CoachReport) {
    val g = LocalGraph.current
    val plan = r.words.plan
    val key = "coachPlan:${r.id}"
    val saved by g.prefs.string(key).collectAsState(initial = null)
    val done = saved?.split(",")?.mapNotNull { it.trim().toIntOrNull() }?.toSet().orEmpty()
    var sel by remember(r.id) { mutableStateOf(0) }
    val day = plan.getOrNull(sel.coerceIn(0, plan.lastIndex)) ?: return
    fun toggle(i: Int) {
        val next = if (i in done) done - i else done + i
        scope.launch { g.prefs.setString(key, next.sorted().joinToString(",").ifEmpty { null }) }
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            plan.forEachIndexed { i, d ->
                val on = i == sel
                val ticked = i in done
                Surface(
                    onClick = { sel = i }, shape = NB.shape.tile,
                    color = if (on) NB.colors.brandSoft else NB.colors.surface, contentColor = NB.colors.ink,
                    border = androidx.compose.foundation.BorderStroke(1.dp, if (on) NB.colors.brand else NB.colors.line),
                    modifier = Modifier.width(72.dp).heightIn(min = 72.dp).semantics {
                        selected = on
                        contentDescription = "${d.day}, ${if (ticked) "done" else "not done"}"
                    },
                ) {
                    Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically)) {
                        Text(d.day, style = NB.type.caption, color = if (on) NB.colors.brandText else NB.colors.muted, maxLines = 1)
                        Icon(
                            if (ticked) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked, null,
                            Modifier.size(24.dp), tint = if (ticked) NB.colors.good else NB.colors.faint,
                        )
                    }
                }
            }
        }
        NbCard {
            Overline(day.day)
            Text(day.text, style = NB.type.body, color = NB.colors.ink, modifier = Modifier.padding(top = 4.dp))
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                val label = actionLabel(day.action)
                if (label != null) NbButton(label, { openAction(nav, scope, { g.prefs.testType.first() }, day.action, null) }, kind = BtnKind.Secondary, size = BtnSize.Small)
                Spacer(Modifier.weight(1f))
                NbButton(
                    if (sel in done) "Done" else "Mark done", { toggle(sel) },
                    kind = if (sel in done) BtnKind.Outline else BtnKind.Text, size = BtnSize.Small,
                    icon = if (sel in done) Icons.Rounded.Check else null, haptic = true,
                )
            }
        }
    }
}

/** Where a coach action leads. Writing and speaking go through the quota check like any other start. */
private fun openAction(nav: Nav, scope: CoroutineScope, variant: suspend () -> String, action: String, part: Int?) {
    when (action) {
        "write-task2" -> nav.startTest("writing") { nav.go(WritingTask("task2")) }
        "write-task1" -> scope.launch { val v = variant(); nav.startTest("writing") { nav.go(WritingTask("task1", v)) } }
        "speak-part" -> nav.startTest("speaking") { nav.go(SpeakingSession("part${(part ?: 2).coerceIn(1, 3)}")) }
        "speak-full" -> nav.startTest("speaking") { nav.go(SpeakingSession("full")) }
        "listening-test" -> nav.openTab(Tab.Listening)
        "reading-test" -> nav.openTab(Tab.Reading)
        "see-evidence" -> nav.go(Mistakes())
        "review-deck" -> nav.go(Review())
    }
}
