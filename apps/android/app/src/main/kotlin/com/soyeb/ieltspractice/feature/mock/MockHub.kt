package com.soyeb.ieltspractice.feature.mock

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.soyeb.ieltspractice.LocalDemo
import com.soyeb.ieltspractice.LocalGraph
import com.soyeb.ieltspractice.data.logic.MockFlow
import com.soyeb.ieltspractice.data.logic.daysLeft
import com.soyeb.ieltspractice.data.logic.hubCopy
import com.soyeb.ieltspractice.data.logic.rowSubtitle
import com.soyeb.ieltspractice.data.logic.sessionAttemptIds
import com.soyeb.ieltspractice.data.logic.startLabel
import com.soyeb.ieltspractice.data.model.Mock
import com.soyeb.ieltspractice.data.model.MockSection
import com.soyeb.ieltspractice.data.net.Api
import com.soyeb.ieltspractice.data.net.ApiError
import com.soyeb.ieltspractice.design.NB
import com.soyeb.ieltspractice.design.Skill
import com.soyeb.ieltspractice.design.Tone
import com.soyeb.ieltspractice.design.components.BandPill
import com.soyeb.ieltspractice.design.components.BandRing
import com.soyeb.ieltspractice.design.components.Banner
import com.soyeb.ieltspractice.design.components.BtnKind
import com.soyeb.ieltspractice.design.components.BtnSize
import com.soyeb.ieltspractice.design.components.CardTone
import com.soyeb.ieltspractice.design.components.ConfirmDialog
import com.soyeb.ieltspractice.design.components.LoadContent
import com.soyeb.ieltspractice.design.components.NbButton
import com.soyeb.ieltspractice.design.components.NbCard
import com.soyeb.ieltspractice.design.components.NbIconButton
import com.soyeb.ieltspractice.design.components.NbListRow
import com.soyeb.ieltspractice.design.components.NbSheet
import com.soyeb.ieltspractice.design.components.Overline
import com.soyeb.ieltspractice.design.components.PollWhileVisible
import com.soyeb.ieltspractice.design.components.PushedList
import com.soyeb.ieltspractice.design.components.PushedScreen
import com.soyeb.ieltspractice.design.components.RowDivider
import com.soyeb.ieltspractice.design.components.RowGroup
import com.soyeb.ieltspractice.design.components.SectionHeader
import com.soyeb.ieltspractice.design.components.SkillTile
import com.soyeb.ieltspractice.design.components.StepRow
import com.soyeb.ieltspractice.design.components.StepState
import com.soyeb.ieltspractice.design.components.StickyActionBar
import com.soyeb.ieltspractice.design.components.rememberLoad
import com.soyeb.ieltspractice.nav.AttemptResult
import com.soyeb.ieltspractice.nav.LiveExam
import com.soyeb.ieltspractice.nav.LrResult
import com.soyeb.ieltspractice.nav.LrRun
import com.soyeb.ieltspractice.nav.MockHub
import com.soyeb.ieltspractice.nav.MockResult
import com.soyeb.ieltspractice.nav.Nav
import com.soyeb.ieltspractice.nav.SpeakingSession
import com.soyeb.ieltspractice.nav.WritingTask
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The home of a running mock: the four sections as a stepper, the one thing to do next, and the way out.
 * Every runner returns here; this screen loads the mock again each time it comes back.
 */
@Composable
fun MockHubScreen(nav: Nav, route: MockHub) {
    val g = LocalGraph.current
    val api = g.api
    val scope = rememberCoroutineScope()
    val demo = LocalDemo.current
    val me by api.me.collectAsState()
    val quota by api.quota.collectAsState()
    val target = me?.settings?.targetBand ?: 7.0
    val live = quota?.live ?: me?.live ?: false

    val loader = rememberLoad(route.mockId) { api.mockGet(route.mockId) }
    val m = loader.value
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var choose by remember { mutableStateOf(demo?.tab == "Choice") }
    var confirm by remember { mutableStateOf<String?>(null) } // "close" | "abandon"
    var menu by remember { mutableStateOf(false) }

    // Marking runs in the background: look again every 5 s until it is done. (Not in screenshots.)
    val marking = m?.marking == true
    PollWhileVisible(marking && demo == null) { loader.reload() }

    fun act(skill: String, block: suspend () -> Unit) {
        busy = true
        error = null
        scope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: ApiError) {
                if (!nav.reportBlocked(e, skill)) error = e.message
            } catch (e: Exception) {
                error = e.message?.takeIf { it.isNotBlank() } ?: "Something went wrong. Check your connection and try again."
            } finally {
                busy = false
            }
        }
    }

    fun start(mock: Mock, skill: String) {
        when (skill) {
            "listening", "reading" -> act(skill) { nav.go(LrRun(api.mockStartSection(mock.id, skill), mock.id)) }
            "writing" -> nav.go(WritingTask("full", mockId = mock.id)) // the editor starts the section (mockWritingStart) itself
            else -> choose = true
        }
    }

    val finished = m != null && (!m.open || m.next == null)

    PushedScreen(
        "Full mock", nav::back, close = true,
        actions = {
            if (m != null && m.open) Box {
                NbIconButton(Icons.Rounded.MoreVert, "More options", { menu = true })
                DropdownMenu(menu, { menu = false }, containerColor = NB.colors.surface) {
                    if (m.next == "speaking") DropdownMenuItem(text = { Text("Finish without Speaking") }, onClick = { menu = false; confirm = "close" })
                    DropdownMenuItem(text = { Text("Abandon mock", color = NB.colors.badText) }, onClick = { menu = false; confirm = "abandon" })
                }
            }
        },
        bottomBar = {
            if (m != null) {
                val label = MockFlow.startLabel(m)
                if (finished) StickyActionBar {
                    NbButton("See your result", { nav.go(MockResult(m.id)) }, fill = true, size = BtnSize.Large)
                } else if (label != null) StickyActionBar {
                    NbButton(label, { start(m, m.next.orEmpty()) }, fill = true, size = BtnSize.Large, loading = busy, haptic = true)
                    if (m.next == "speaking") NbButton("Take Speaking later", { nav.back() }, kind = BtnKind.Text, fill = true)
                }
            }
        },
    ) { pad ->
        LoadContent(loader, Modifier.padding(pad)) { mock ->
            PushedList(pad) {
                error?.let { msg -> item { Banner(msg, Icons.Rounded.Info, tone = Tone.Bad) } }
                item { NextCard(mock, target) }
                item { SectionHeader("Your sections") }
                item {
                    NbCard {
                        mock.sections.forEachIndexed { i, s ->
                            SectionStep(mock, s, i, target) { openSection(nav, scope, api, s) }
                        }
                    }
                }
                if (mock.marking) item {
                    Text("Marking runs in the background. This page updates by itself.", style = NB.type.bodySmall, color = NB.colors.muted)
                }
                if (mock.open) MockFlow.daysLeft(mock.expiresAt)?.let { d ->
                    item {
                        Text(
                            if (d <= 0) "This mock closes today." else "This mock stays open for $d more ${if (d == 1) "day" else "days"}.",
                            style = NB.type.caption, color = NB.colors.muted,
                        )
                    }
                }
            }
        }
    }

    if (choose && m != null) {
        NbSheet(
            onDismiss = { choose = false },
            title = "Speaking",
            subtitle = "There is no countdown. Pick how you want to do it.",
        ) {
            RowGroup {
                NbListRow(
                    "Recorded test", subtitle = "11 to 14 minutes. The examiner reads the questions and you record each answer.",
                    leading = { SkillTile(Skill.Speaking, 40.dp) },
                    onClick = {
                        choose = false
                        val go = { act("speaking") { api.mockChoose(m.id, "recorded"); nav.go(SpeakingSession("full", mockId = m.id)) } }
                        // Continuing a recorded test already paid for: mockChoose checks this session, not the allowance for a new test.
                        val resume = m.section("speaking")?.let { it.state == "in_progress" && it.mode == "recorded" } == true
                        if (resume) go() else nav.startTest("speaking", go)
                    },
                )
                RowDivider()
                NbListRow(
                    "Live examiner",
                    subtitle = if (live) "11 to 14 minutes, spoken. An AI examiner asks the questions and follows up." else "Not available right now.",
                    leading = { SkillTile(Skill.Speaking, 40.dp) },
                    titleColor = if (live) NB.colors.ink else NB.colors.muted,
                    onClick = if (live) ({
                        choose = false
                        nav.startLive { act("speaking") { api.mockChoose(m.id, "live"); nav.go(LiveExam(m.id)) } }
                    }) else null,
                )
                RowDivider()
                NbListRow(
                    "Take Speaking later", subtitle = "The mock stays open${MockFlow.daysLeft(m.expiresAt)?.let { " for $it more ${if (it == 1) "day" else "days"}" }.orEmpty()}.",
                    onClick = { choose = false; nav.back() },
                )
            }
        }
    }

    if (confirm == "close" && m != null) {
        ConfirmDialog(
            "Finish without Speaking?", "The mock closes with Speaking skipped and no overall band. Your other sections stay in your history.", "Finish",
            onConfirm = {
                confirm = null
                act("speaking") { api.mockClose(m.id); loader.reload() }
            },
            onDismiss = { confirm = null }, dismiss = "Keep going",
        )
    }
    if (confirm == "abandon" && m != null) {
        ConfirmDialog(
            "Abandon this mock?", "The mock is removed. Sections you already took stay in your history as normal practice.", "Abandon",
            onConfirm = {
                confirm = null
                act("writing") { api.mockAbandon(m.id); nav.back() }
            },
            onDismiss = { confirm = null }, dismiss = "Keep it", destructive = true,
        )
    }
}

/** What to do now: the transition between sections, the Speaking prompt, or the finished mock. */
@Composable
private fun NextCard(m: Mock, target: Double) {
    val c = MockFlow.hubCopy(m, target)
    NbCard(Modifier.animateContentSize(), tone = CardTone.Hero) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
                Overline(c.overline, color = NB.colors.brandText)
                Text(c.title, style = NB.type.title1, color = NB.colors.ink, modifier = Modifier.padding(top = 4.dp))
                c.detail?.let { Text(it, style = NB.type.title3, color = NB.colors.ink, modifier = Modifier.padding(top = 4.dp)) }
                Text(c.body, style = NB.type.bodySmall, color = NB.colors.ink, modifier = Modifier.padding(top = 8.dp))
            }
            if (!m.open || m.next == null) m.overall?.let { BandRing(it, target, size = 96.dp, stroke = 8.dp, label = "Overall", modifier = Modifier.padding(start = 16.dp)) }
        }
    }
}

private fun stepState(m: Mock, s: MockSection): StepState = when {
    MockFlow.isFinished(s.state) -> StepState.Done
    m.open && s.skill == m.next -> StepState.Current
    else -> StepState.Upcoming
}

@Composable
private fun SectionStep(m: Mock, s: MockSection, i: Int, target: Double, onOpen: () -> Unit) {
    val linked = s.attemptId != null && MockFlow.isFinished(s.state)
    val state = MockFlow.stateLabel(s.skill, s.state).first
    val desc = "${MockFlow.name(s.skill)}, $state" + (s.band?.let { ", band ${com.soyeb.ieltspractice.design.fmtBand(it)}" } ?: "")
    Box(
        Modifier.fillMaxWidth()
            .let { if (linked) it.clickable(role = Role.Button, onClickLabel = "Open result", onClick = onOpen) else it }
            .semantics(mergeDescendants = true) { contentDescription = desc },
    ) {
        StepRow(
            i + 1, MockFlow.name(s.skill), MockFlow.rowSubtitle(s), stepState(m, s), last = i == m.sections.lastIndex,
            trailing = {
                if (s.band != null || linked) Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (s.band != null) BandPill(s.band, target)
                    if (linked) Text("View result", style = NB.type.caption, color = NB.colors.brandText)
                }
            },
        )
    }
}

/** The normal result page of a finished section: Listening and Reading by attempt, Writing and Speaking with every attempt of their session. */
internal fun openSection(nav: Nav, scope: CoroutineScope, api: Api, s: MockSection) {
    val id = s.attemptId ?: return
    if (!MockFlow.isFinished(s.state)) return
    if (s.skill == "listening" || s.skill == "reading") { nav.go(LrResult(id)); return }
    // The history list is the only place to find the rest of the session; if it fails, the first attempt still opens.
    scope.launch {
        val items = try { api.attempts(s.skill, 1).items } catch (e: CancellationException) { throw e } catch (e: Exception) { emptyList() }
        nav.go(AttemptResult(sessionAttemptIds(items, s.sessionId, id)))
    }
}
