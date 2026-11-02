package com.soyeb.ieltspractice.feature.speaking

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.soyeb.ieltspractice.LocalDemo
import com.soyeb.ieltspractice.LocalGraph
import com.soyeb.ieltspractice.data.UploadState
import com.soyeb.ieltspractice.data.logic.sentenceCase
import com.soyeb.ieltspractice.data.model.Prompt
import com.soyeb.ieltspractice.data.net.ApiError
import com.soyeb.ieltspractice.design.LocalReducedMotion
import com.soyeb.ieltspractice.design.NB
import com.soyeb.ieltspractice.design.components.Banner
import com.soyeb.ieltspractice.design.components.BtnKind
import com.soyeb.ieltspractice.design.components.BtnSize
import com.soyeb.ieltspractice.design.components.CardTone
import com.soyeb.ieltspractice.design.components.ConfirmDialog
import com.soyeb.ieltspractice.design.components.EmptyState
import com.soyeb.ieltspractice.design.components.ErrorState
import com.soyeb.ieltspractice.design.components.IconTile
import com.soyeb.ieltspractice.design.components.NbButton
import com.soyeb.ieltspractice.design.components.NbCard
import com.soyeb.ieltspractice.design.components.NbListRow
import com.soyeb.ieltspractice.design.components.NbTextField
import com.soyeb.ieltspractice.design.components.Overline
import com.soyeb.ieltspractice.design.components.PushedScreen
import com.soyeb.ieltspractice.design.components.RowDivider
import com.soyeb.ieltspractice.design.components.RowGroup
import com.soyeb.ieltspractice.design.components.StickyActionBar
import com.soyeb.ieltspractice.design.components.Stat
import com.soyeb.ieltspractice.design.components.VDivider
import com.soyeb.ieltspractice.design.components.clockText
import com.soyeb.ieltspractice.design.Tone
import com.soyeb.ieltspractice.nav.AttemptResult
import com.soyeb.ieltspractice.nav.Nav
import com.soyeb.ieltspractice.nav.SpeakingSession
import kotlin.math.roundToInt

// The recorded practice test: permission, mic check, examiner question, record, Part 2 prep and talk, part transitions, uploads.
// Immersive: no bottom bar, the screen stays awake, back asks to leave.

@Composable
fun SpeakingSessionScreen(route: SpeakingSession, nav: Nav) {
    val graph = LocalGraph.current
    val demo = LocalDemo.current
    val ctx = LocalContext.current
    val hf = LocalHapticFeedback.current
    val vm: SessionModel = viewModel(factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = SessionModel(graph, route, demo) as T
    })
    val mic = rememberMicAccess()
    var exitOpen by remember { mutableStateOf(false) }
    var earlyOpen by remember { mutableStateOf(false) }

    KeepScreenOn()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.recheckPermission() }

    val states by graph.pending.states.collectAsState()
    fun stateOf(u: UploadItem): UploadState = vm.demoStates[u.id] ?: states[u.id] ?: UploadState.Uploading
    val doneIds = vm.uploads.mapNotNull { (stateOf(it) as? UploadState.Done)?.attemptId }
    val allDone = vm.phase == Phase.Finishing && vm.items.isNotEmpty() && vm.uploads.size == vm.items.size && doneIds.size == vm.uploads.size
    val notDone = vm.uploads.any { stateOf(it) !is UploadState.Done }
    val failedIds = vm.uploads.filter { stateOf(it) is UploadState.Failed }.joinToString(",") { it.id }

    fun leave() { vm.discardLive(); nav.back() }
    fun requestExit() {
        if (vm.recording || vm.phase == Phase.Prep || vm.index > 0 || notDone) exitOpen = true else leave()
    }
    BackHandler(enabled = !allDone) { requestExit() }

    // Every part uploaded: a mock test goes back to its hub, any other test opens its result (which polls until it is marked).
    LaunchedEffect(allDone) {
        if (!allDone) return@LaunchedEffect
        if (vm.mockId != null) nav.back() else nav.replace(AttemptResult(doneIds))
    }
    // An upload refused for a limit (no tests left, scoring paused): the same sheet as at the start, and the recording is safe.
    LaunchedEffect(failedIds) {
        if (failedIds.isEmpty() || demo != null) return@LaunchedEffect
        val q = runCatching { graph.api.refreshQuota() }.getOrNull() ?: return@LaunchedEffect
        val s = q.of("speaking")
        val code = s.blocked ?: return@LaunchedEffect
        nav.reportBlocked(ApiError(0, "", code = code, resetAt = s.resetAt, skill = "speaking", tier = q.tier), "speaking", keep = "Your recording is saved.")
    }
    // Haptic ticks: recording starts and stops, the last ten seconds of preparation.
    LaunchedEffect(vm.phase) {
        if (vm.phase == Phase.Recording || vm.phase == Phase.Prep) hf.performHapticFeedback(HapticFeedbackType.LongPress)
    }
    LaunchedEffect(vm.prepLeft <= 10 && vm.phase == Phase.Prep) {
        if (vm.prepLeft <= 10 && vm.phase == Phase.Prep) hf.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    val p = vm.current
    val title = when (vm.phase) {
        Phase.Finishing -> "Saving your answers"
        Phase.Loading, Phase.Empty, Phase.Failed -> "Speaking"
        Phase.Permission, Phase.MicCheck -> "Microphone"
        else -> partLabel(vm.items, vm.index)
    }
    val reduced = LocalReducedMotion.current

    PushedScreen(
        title, onBack = ::requestExit, close = true, scrollBehavior = null,
        actions = {
            if (vm.items.size > 1 && vm.phase in listOf(Phase.Ready, Phase.Prep, Phase.Recording)) {
                Text(
                    "${vm.index + 1}/${vm.items.size}", Modifier.padding(end = 16.dp).semantics { contentDescription = "Part ${vm.index + 1} of ${vm.items.size}" },
                    style = NB.type.num, color = NB.colors.muted,
                )
            }
        },
        bottomBar = {
            when (vm.phase) {
                Phase.Permission -> StickyActionBar {
                    if (vm.micDenied) NbButton("Open settings", { openAppSettings(ctx) }, fill = true, size = BtnSize.Large)
                    else NbButton("Allow microphone", { mic.ask { ok -> vm.onPermission(ok) } }, fill = true, size = BtnSize.Large, icon = Icons.Rounded.Mic)
                    NbButton("Not now", ::leave, kind = BtnKind.Text, fill = true)
                }
                Phase.MicCheck -> StickyActionBar {
                    val heard = micCheckLevel(vm.checkLoud, vm.checkSoft) == 2
                    NbButton("Continue", vm::finishMicCheck, fill = true, size = BtnSize.Large, enabled = heard)
                    if (!heard) NbButton("Skip the check", vm::finishMicCheck, kind = BtnKind.Text, fill = true)
                }
                Phase.Ready -> if (p != null) {
                    if (p.part == 2) StickyActionBar {
                        NbButton(
                            "Start 1-minute preparation", { mic.ask { ok -> if (ok) vm.beginPrep() else vm.onPermission(false) } },
                            fill = true, size = BtnSize.Large, enabled = vm.examiner != Examiner.Asking,
                        )
                    } else Dock {
                        RecordButton(
                            false, 0f, "Start recording", { mic.ask { ok -> if (ok) vm.startRecording() else vm.onPermission(false) } },
                            enabled = vm.examiner != Examiner.Asking,
                        )
                        Text("Tap to start. Your whole ${partLabel(vm.items, vm.index).substringBefore(',')} is one recording.", style = NB.type.caption, color = NB.colors.muted)
                    }
                }
                Phase.Prep -> StickyActionBar { NbButton("Start speaking now", vm::startRecording, fill = true, size = BtnSize.Large) }
                Phase.Recording -> if (p != null) RecordingDock(vm, p, onEarly = { earlyOpen = true })
                Phase.Failed, Phase.Empty -> StickyActionBar { NbButton("Back to speaking", nav::back, kind = BtnKind.Secondary, fill = true, size = BtnSize.Large) }
                else -> {}
            }
        },
    ) { pad ->
        Crossfade(vm.phase, Modifier.padding(pad).fillMaxSize(), animationSpec = tween(if (reduced) 0 else 220), label = "phase") { ph ->
            when (ph) {
                Phase.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator(color = NB.colors.brand)
                        Text("Loading questions", style = NB.type.bodySmall, color = NB.colors.muted)
                    }
                }
                Phase.Empty -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    EmptyState(Icons.Rounded.Mic, "No questions available yet", "The prompt bank has no speaking questions for this part. Try again later.")
                }
                Phase.Failed -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { ErrorState(vm.failMessage, vm::reload) }
                Phase.Permission -> PermissionBody(vm.micDenied)
                Phase.MicCheck -> MicCheckBody(vm, onRetry = vm::retryMicCheck)
                Phase.Transition -> if (p != null) TransitionBody(p.part, vm::continueFromTransition)
                Phase.Finishing -> FinishingBody(vm, ::stateOf, doneIds.size) { u -> vm.retry(u) }
                Phase.Ready, Phase.Prep, Phase.Recording -> if (p != null) SessionBody(vm, p, ph)
            }
        }
    }

    if (exitOpen) {
        ConfirmDialog(
            "Leave this test?", exitMessage(vm.recording, notDone), "Leave", onConfirm = ::leave, onDismiss = { exitOpen = false },
            dismiss = "Keep going", destructive = true,
        )
    }
    val cur = vm.current
    if (earlyOpen && cur != null) {
        val left = cur.questions.size - vm.question - 1
        ConfirmDialog(
            "Finish with ${vm.question + 1} of ${cur.questions.size} answered?",
            "Your whole Part ${cur.part} is one recording, so finishing now ends it and skips the last $left ${if (left == 1) "question" else "questions"}.",
            "Finish now", onConfirm = { earlyOpen = false; vm.finishPart() }, onDismiss = { earlyOpen = false }, dismiss = "Keep going",
        )
    }
}

/** The bar under the recorder: a hairline, then whatever the state needs, centred. */
@Composable
private fun Dock(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Surface(color = NB.colors.surface, contentColor = NB.colors.ink, modifier = Modifier.fillMaxWidth()) {
        Column {
            HorizontalDivider(color = NB.colors.line)
            Column(
                Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = NB.space.gutter, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally, content = content,
            )
        }
    }
}

@Composable
private fun RecordingDock(vm: SessionModel, p: Prompt, onEarly: () -> Unit) {
    val more = p.part != 2 && vm.question + 1 < p.questions.size
    val asking = vm.examiner == Examiner.Asking
    Dock {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (more) NbButton("Finish part", onEarly, kind = BtnKind.Text, size = BtnSize.Small, enabled = !asking)
            }
            RecordButton(
                true, vm.level.toFloat(), "Stop recording and finish this part", { if (more) onEarly() else vm.finishPart() },
                enabled = !asking,
            )
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                if (more) NbButton("Next question", vm::nextQuestion, size = BtnSize.Medium, enabled = !asking)
                else NbButton(if (vm.index + 1 < vm.items.size) "Finish and continue" else "Finish", { vm.finishPart() }, size = BtnSize.Medium, enabled = !asking)
            }
        }
    }
}

// MARK: Permission and mic check

@Composable
private fun PermissionBody(denied: Boolean) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = NB.space.gutter * 1.2f, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        if (denied) IconTile(Icons.Rounded.MicOff, NB.colors.warnText, NB.colors.warnSoft, 80.dp)
        else IconTile(Icons.Rounded.Mic, NB.colors.speaking, NB.colors.speakingSoft, 80.dp)
        Text(
            if (denied) "Microphone access is off" else "Allow microphone access",
            style = NB.type.headline, color = NB.colors.ink, textAlign = TextAlign.Center, modifier = Modifier.semantics { heading() },
        )
        Text(
            if (denied) "NextBand can't hear your answers. Turn the microphone on in settings, then come back; the test will carry on from here."
            else "NextBand records your answers so you get a band and feedback on every sentence. The audio stays with your account and is used only for scoring.",
            style = NB.type.body, color = NB.colors.muted, textAlign = TextAlign.Center,
        )
        if (!denied) {
            NbCard(tone = CardTone.Inset) {
                listOf("Recorded only while you answer", "Used to score and show your transcript", "Delete any result from History").forEachIndexed { i, t ->
                    Row(Modifier.padding(vertical = if (i == 0) 0.dp else 8.dp), verticalAlignment = Alignment.Top) {
                        Icon(Icons.Rounded.CheckCircle, null, Modifier.size(20.dp), tint = NB.colors.goodText)
                        Spacer(Modifier.size(10.dp))
                        Text(t, style = NB.type.bodySmall, color = NB.colors.ink)
                    }
                }
            }
        }
    }
}

@Composable
private fun MicCheckBody(vm: SessionModel, onRetry: () -> Unit) {
    val c = NB.colors
    val state = micCheckLevel(vm.checkLoud, vm.checkSoft)
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = NB.space.gutter, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(NB.space.lg),
    ) {
        Overline("Mic check")
        Text("Say: “My name is…”", style = NB.type.headline, color = c.ink, modifier = Modifier.semantics { heading() })
        Text("Use your normal voice, with the phone about an arm's length away.", style = NB.type.body, color = c.muted)
        NbCard {
            MicMeter(vm.checkLevel.toFloat(), heard = state == 2, quiet = state == 1)
            Spacer(Modifier.height(14.dp))
            val (text, color) = when (state) {
                2 -> "We can hear you clearly" to c.goodText
                1 -> "Very quiet. Move closer or speak up" to c.warnText
                else -> "Waiting for your voice" to c.muted
            }
            Row(Modifier.semantics { liveRegion = LiveRegionMode.Polite }, verticalAlignment = Alignment.CenterVertically) {
                if (state == 2) {
                    Icon(Icons.Rounded.CheckCircle, null, Modifier.size(22.dp), tint = c.goodText)
                    Spacer(Modifier.size(8.dp))
                }
                Text(text, style = if (state == 0) NB.type.body else NB.type.title3, color = color)
            }
        }
        vm.startError?.let {
            Banner(it, Icons.Rounded.Warning, tone = Tone.Warn, action = "Try again", onAction = onRetry)
        }
        Text("Headphones are fine. A quiet room helps the transcript.", style = NB.type.bodySmall, color = c.muted)
    }
}

// MARK: Question, prep, recording

@Composable
private fun SessionBody(vm: SessionModel, p: Prompt, phase: Phase) {
    val c = NB.colors
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = NB.space.gutter, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(NB.space.lg),
    ) {
        vm.startError?.let { Banner(it, Icons.Rounded.Warning, tone = Tone.Bad) }
        if (p.part == 2) {
            CueCard(p)
            when (phase) {
                Phase.Ready -> {
                    if (vm.examiner == Examiner.Asking) ExaminerLine("The examiner is introducing the card", true)
                    NbCard(tone = CardTone.Inset) {
                        Row(Modifier.fillMaxWidth()) {
                            Stat(clockText(PREP_SECONDS.toLong()), "Preparation", Modifier.weight(1f))
                            VDivider()
                            Stat("up to ${clockText(P2_MAX_SECONDS.toLong())}", "Speaking", Modifier.weight(1f).padding(start = 16.dp))
                        }
                        Spacer(Modifier.height(10.dp))
                        Text("Talk for 1 to 2 minutes about the card. Make notes while you prepare. Recording stops at 2:00.", style = NB.type.bodySmall, color = c.muted)
                    }
                }
                Phase.Prep -> {
                    val low = vm.prepLeft <= 10
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        TimerRing(
                            vm.prepLeft.toFloat() / PREP_SECONDS, if (low) c.warn else c.brand, 132.dp, 8.dp,
                            Modifier.clearAndSetSemantics { contentDescription = "Preparation time left ${clockText(vm.prepLeft.toLong())}" },
                        ) {
                            Text(clockText(vm.prepLeft.toLong()), style = NB.type.band(30), color = if (low) c.warnText else c.ink)
                        }
                    }
                    NbTextField(vm.notes, { vm.notes = it }, "Notes", singleLine = false, placeholder = "Key words, examples")
                    Text("Only you see these. Recording starts by itself when the minute is up.", style = NB.type.bodySmall, color = c.muted)
                }
                else -> {
                    val left = (P2_MAX_SECONDS - vm.elapsed).coerceIn(0.0, P2_MAX_SECONDS)
                    val zone = zoneTone(2, vm.elapsed)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        TimerRing(
                            (left / P2_MAX_SECONDS).toFloat(), zoneColor(zone), 148.dp, 8.dp,
                            Modifier.clearAndSetSemantics { contentDescription = "Speaking time left ${clockText(left.toLong())}" },
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(clockText(left.roundToInt().toLong()), style = NB.type.band(32), color = c.ink)
                                Text("left", style = NB.type.caption, color = c.muted)
                            }
                        }
                    }
                    RecordingStatus(vm, 2, vm.elapsed)
                    if (vm.notes.isNotEmpty()) NbCard(tone = CardTone.Inset) {
                        Overline("Your notes")
                        Spacer(Modifier.height(6.dp))
                        Text(vm.notes, style = NB.type.readingSmall, color = c.ink)
                    }
                }
            }
        } else {
            QuestionBlock(vm, p)
            if (phase == Phase.Ready) {
                Text(
                    if (p.part == 1) "Short answers about you. The examiner reads each question aloud, then you speak. Tap Next question when you have answered."
                    else "A discussion linked to Part 2. Develop each answer with reasons and examples, then tap Next question.",
                    style = NB.type.bodySmall, color = c.muted,
                )
            } else {
                val seconds = (vm.elapsed - vm.questionStart).coerceAtLeast(0.0)
                if (vm.examiner != Examiner.Asking) {
                    val zone = zoneTone(p.part, seconds)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        TimerRing(
                            (seconds / ringMax(p.part)).toFloat(), zoneColor(zone), 132.dp, 8.dp,
                            Modifier.clearAndSetSemantics { contentDescription = "Answer time ${clockText(seconds.toLong())}" },
                        ) {
                            Text(clockText(seconds.toLong()), style = NB.type.band(30), color = c.ink)
                        }
                    }
                    RecordingStatus(vm, p.part, seconds)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun zoneColor(z: Zone): Color = when (z) { Zone.Brand -> NB.colors.brand; Zone.Warn -> NB.colors.warn; Zone.Good -> NB.colors.good }

/** Level bars, the pace chip and the zone hint under the timer. */
@Composable
private fun RecordingStatus(vm: SessionModel, part: Int, seconds: Double) {
    val c = NB.colors
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.semantics(mergeDescendants = true) {}) {
            Box(Modifier.size(10.dp).background(c.bad, androidx.compose.foundation.shape.CircleShape))
            Text("Recording", style = NB.type.label, color = c.badText)
            if (vm.examiner == Examiner.Cue) Text("Speak now", style = NB.type.label, color = c.goodText)
        }
        LevelBars(vm.levels, Modifier.fillMaxWidth(0.8f))
        val wpm = vm.liveWpm
        Text(
            when {
                vm.elapsed < 5 || wpm == 0 -> "Measuring pace…"
                wpm in 120..170 -> "About $wpm words a minute, steady"
                else -> "About $wpm words a minute, ${if (wpm < 120) "a little slow" else "a little fast"}"
            },
            style = NB.type.bodySmall, color = if (vm.elapsed >= 5 && wpm != 0 && wpm !in 120..170) c.warnText else c.muted,
        )
        Text(zoneHint(part, seconds.toInt()), Modifier.semantics { liveRegion = LiveRegionMode.Polite }, style = NB.type.bodySmall, color = c.muted)
        // keeps its height so the layout does not jump when the nudge appears
        val nudge = vm.silence >= 3
        Row(
            Modifier.alpha(if (nudge) 1f else 0f).let { if (nudge) it else it.clearAndSetSemantics {} },
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(Icons.Rounded.Lightbulb, null, Modifier.size(18.dp), tint = c.brandText)
            Text("Keep going. Add a reason or an example.", style = NB.type.label, color = c.brandText)
        }
    }
}

@Composable
private fun ExaminerLine(text: String, active: Boolean) {
    Row(
        Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }, verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ExaminerDisc(active, discSize = 32.dp)
        Text(text, style = NB.type.label, color = NB.colors.brandText)
    }
}

/** Part 1/3: which question this is, and the examiner with the question (heard, with the text one tap away, when there is a recording). */
@Composable
private fun QuestionBlock(vm: SessionModel, p: Prompt) {
    val c = NB.colors
    val n = p.questions.size
    val q = vm.question.coerceIn(0, maxOf(n - 1, 0))
    val text = p.questions.getOrNull(q) ?: p.title
    val spoken = vm.spoken(p, q)
    var shown by remember(vm.index, q) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(subTopic(p, q) ?: sentenceCase(p.topic ?: p.title), Modifier.weight(1f), style = NB.type.label, color = c.ink)
            if (n > 1) Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Question ${q + 1} of $n", style = NB.type.caption.copy(fontFeatureSettings = "tnum"), color = c.muted)
                Row(Modifier.clearAndSetSemantics {}, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    repeat(n) { i ->
                        Box(Modifier.size(width = 16.dp, height = 4.dp).background(if (i < q) c.muted else if (i == q) c.brand else c.surface3, RoundedCornerShape(2.dp)))
                    }
                }
            }
        }
        NbCard {
            Row(verticalAlignment = Alignment.Top) {
                ExaminerDisc(vm.examiner == Examiner.Asking)
                Spacer(Modifier.size(14.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Overline(if (vm.examiner == Examiner.Asking) "Examiner is asking" else "Examiner")
                    if (spoken && !shown) {
                        Text(
                            "Listen to the examiner", style = NB.type.title1, color = c.muted,
                            modifier = Modifier.clearAndSetSemantics { contentDescription = text; heading() },
                        )
                        NbButton("Show question", { shown = true }, kind = BtnKind.Text, size = BtnSize.Small)
                    } else {
                        Text(text, style = NB.type.readingTitle, color = c.ink, modifier = Modifier.semantics { heading(); liveRegion = LiveRegionMode.Polite })
                    }
                }
            }
        }
    }
}

// MARK: Part transition

@Composable
private fun TransitionBody(part: Int, onContinue: () -> Unit) {
    val c = NB.colors
    Column(
        Modifier.fillMaxSize().padding(horizontal = NB.space.gutter * 1.4f).semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        Overline("Next up", color = c.speaking)
        Text("Part $part", style = NB.type.display, color = c.ink)
        Text(partName(part), style = NB.type.title1, color = c.ink)
        Text(partBlurb(part), style = NB.type.body, color = c.muted, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        NbButton("Continue", onContinue, kind = BtnKind.Secondary)
    }
}

// MARK: Saving

@Composable
private fun FinishingBody(vm: SessionModel, stateOf: (UploadItem) -> UploadState, done: Int, onRetry: (UploadItem) -> Unit) {
    val c = NB.colors
    val total = vm.items.size
    val failed = vm.uploads.filter { stateOf(it) is UploadState.Failed }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = NB.space.gutter, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(NB.space.lg),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                if (failed.isEmpty()) "Uploading your answers" else "Some answers need another try",
                style = NB.type.headline, color = c.ink, modifier = Modifier.semantics { heading() },
            )
            Text(
                if (failed.isEmpty()) "Marking starts as soon as each recording arrives." else "Your recordings are still here. Retry to send them.",
                style = NB.type.body, color = c.muted,
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(finishProgress(total, done, failed.size), style = NB.type.caption.copy(fontFeatureSettings = "tnum"), color = c.muted)
            LinearProgressIndicator(
                progress = { done.toFloat() / maxOf(total, 1) },
                modifier = Modifier.fillMaxWidth().height(6.dp).semantics { contentDescription = "$done of $total recordings uploaded" },
                color = if (failed.isEmpty()) c.brand else c.warn, trackColor = c.surface2, drawStopIndicator = {},
            )
        }
        RowGroup {
            vm.uploads.forEachIndexed { i, u ->
                if (i > 0) RowDivider()
                val state = stateOf(u)
                NbListRow(
                    title = u.label,
                    subtitle = when (state) { is UploadState.Failed -> state.message; UploadState.Uploading -> "Uploading…"; is UploadState.Done -> "Uploaded" },
                    leading = {
                        when (state) {
                            is UploadState.Done -> Icon(Icons.Rounded.CheckCircle, "Uploaded", tint = c.goodText)
                            is UploadState.Failed -> Icon(Icons.Rounded.Warning, "Failed", tint = c.badText)
                            UploadState.Uploading -> CircularProgressIndicator(Modifier.size(22.dp).semantics { contentDescription = "Uploading" }, strokeWidth = 2.dp, color = c.brand)
                        }
                    },
                    trailing = if (state is UploadState.Failed) ({ NbButton("Retry", { onRetry(u) }, kind = BtnKind.Secondary, size = BtnSize.Small) }) else null,
                )
            }
        }
        if (failed.isNotEmpty()) {
            Banner(
                "Your recording is saved on this device. If it keeps failing, leave and upload it later from the Speaking page, even after a restart.",
                Icons.Rounded.Warning, tone = Tone.Warn,
            )
            NbButton("Retry upload", { failed.forEach(onRetry) }, fill = true)
        }
    }
}
