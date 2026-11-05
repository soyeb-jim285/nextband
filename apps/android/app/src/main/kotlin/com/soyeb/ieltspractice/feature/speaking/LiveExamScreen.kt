package com.soyeb.ieltspractice.feature.speaking

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.soyeb.ieltspractice.LocalDemo
import com.soyeb.ieltspractice.LocalGraph
import com.soyeb.ieltspractice.design.NB
import com.soyeb.ieltspractice.design.Tone
import com.soyeb.ieltspractice.design.components.Banner
import com.soyeb.ieltspractice.design.components.BtnKind
import com.soyeb.ieltspractice.design.components.BtnSize
import com.soyeb.ieltspractice.design.components.CardTone
import com.soyeb.ieltspractice.design.components.ConfirmDialog
import com.soyeb.ieltspractice.design.components.IconTile
import com.soyeb.ieltspractice.design.components.LocalSnack
import com.soyeb.ieltspractice.design.components.NbButton
import com.soyeb.ieltspractice.design.components.NbCard
import com.soyeb.ieltspractice.design.components.NbIconButton
import com.soyeb.ieltspractice.design.components.NbListRow
import com.soyeb.ieltspractice.design.components.Overline
import com.soyeb.ieltspractice.design.components.PushedScreen
import com.soyeb.ieltspractice.design.components.RowDivider
import com.soyeb.ieltspractice.design.components.RowGroup
import com.soyeb.ieltspractice.design.components.StickyActionBar
import com.soyeb.ieltspractice.design.components.clockText
import com.soyeb.ieltspractice.design.components.readable
import com.soyeb.ieltspractice.media.LiveStage
import com.soyeb.ieltspractice.media.phaseLabel
import com.soyeb.ieltspractice.nav.AttemptResult
import com.soyeb.ieltspractice.nav.LiveExam
import com.soyeb.ieltspractice.nav.Nav
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.soyeb.ieltspractice.media.LiveExam as LiveModel

// The live examiner (LiveKit): pre-screen with the mic check, then a voice orb stage. State comes from media/LiveExam.

@Composable
fun LiveExamScreen(route: LiveExam, nav: Nav) {
    val graph = LocalGraph.current
    // The question bank (Cambridge accounts can choose it) is read once, before the model is made.
    val source by produceState<List<String?>?>(null) {
        value = listOf(runCatching { graph.api.sourceParam(graph.prefs.speakingSource.first()) }.getOrNull())
    }
    val resolved = source
    if (resolved == null) {
        PushedScreen("Live examiner", nav::back, close = true, scrollBehavior = null) { pad ->
            Box(Modifier.padding(pad).fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = NB.colors.brand) }
        }
    } else LiveExamContent(route, nav, resolved[0])
}

@Composable
private fun LiveExamContent(route: LiveExam, nav: Nav, source: String?) {
    val graph = LocalGraph.current
    val demo = LocalDemo.current
    val ctx = LocalContext.current
    val exam: LiveModel = viewModel(factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = LiveModel(graph.api, ctx.applicationContext, demo, route.mockId, source) as T
    })
    val access = rememberMicAccess()
    var confirmEnd by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    var captions by remember { mutableStateOf(false) }
    KeepScreenOn()

    fun checkMic() = access.ask { ok -> if (ok) exam.beginMicCheck() else exam.micDenied = true }
    // Already allowed: start the level meter on entry. Otherwise the system prompt waits for "Test microphone", after the pre-screen says where the audio goes.
    LaunchedEffect(Unit) { if (demo == null && access.granted()) exam.beginMicCheck() }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        exam.parkMicCheck(true)
        // Rotation also stops the activity; only really leaving the app ends a running test.
        if (ctx.findActivity()?.isChangingConfigurations != true) exam.leftApp()
    }
    LifecycleEventEffect(Lifecycle.Event.ON_START) { exam.parkMicCheck(false) }

    // Screenshots: Connecting and Ended are drawn over the model's pre-screen state.
    val demoTab = demo?.tab
    val stage: LiveStage = when (demoTab) {
        "Connecting" -> LiveStage.Running
        "Ended" -> LiveStage.Finished(listOf("as1", "as2", "as3"))
        else -> exam.stage
    }
    val connecting = stage == LiveStage.Running && !exam.examinerTalking && !exam.listening && !exam.thinking && exam.caption.isEmpty() && exam.phase != "p2-prep"

    // The server refused (no tests left): the same panel as before the test, over the failure message.
    LaunchedEffect(stage) { (stage as? LiveStage.Failed)?.error?.let { nav.reportBlocked(it, "speaking") } }

    val running = stage == LiveStage.Running
    val snack = LocalSnack.current
    // One exit rule for the gesture and the close button: confirm while running, hold (and say why) while uploading.
    // ponytail: after a failure the recordings live only in this ViewModel (not the pending-upload queue), and onCleared deletes them: confirm.
    val unsent = stage is LiveStage.Failed && exam.hasRecordings
    fun leave() {
        if (running) confirmEnd = true
        else if (stage == LiveStage.Uploading) graph.scope.launch { snack.showSnackbar("Saving your recording… one moment.") }
        else if (unsent) confirmDiscard = true
        else nav.back()
    }
    BackHandler(enabled = running || stage == LiveStage.Uploading || unsent) { leave() }
    val now by produceState(SystemClock.elapsedRealtime(), running, demo) {
        value = SystemClock.elapsedRealtime()
        while (running && demo == null) { delay(1000); value = SystemClock.elapsedRealtime() }
    }
    val elapsed = if (running) clockText(((now - exam.testStartedAt) / 1000).coerceAtLeast(0)) else null

    PushedScreen(
        "Live examiner", onBack = ::leave, close = true, scrollBehavior = null,
        actions = {
            if (elapsed != null) Text(
                elapsed, Modifier.padding(end = 16.dp).semantics { contentDescription = "Elapsed $elapsed" },
                style = NB.type.num, color = NB.colors.muted,
            )
        },
        bottomBar = {
            when (stage) {
                LiveStage.Ready -> StickyActionBar {
                    // Always enabled: before the mic is ready a press retries the permission check instead of starting.
                    NbButton(
                        if (exam.micReady) "Start test" else "Check microphone and start",
                        { if (exam.micReady) exam.start() else checkMic() }, fill = true, size = BtnSize.Large, enabled = !exam.micChecking,
                    )
                }
                LiveStage.Running -> StickyActionBar {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        NbIconButton(
                            Icons.Rounded.Subtitles, if (captions) "Hide captions" else "Show captions", { captions = !captions }, tonal = true,
                            tint = if (captions) NB.colors.brandText else null,
                        )
                        Box(Modifier.weight(1f)) {
                            if (exam.listening && exam.phase == "p2-talk") NbButton("I'm done", exam::endTurn, fill = true, size = BtnSize.Large)
                        }
                        NbButton("End test", { confirmEnd = true }, kind = BtnKind.Outline, size = BtnSize.Large)
                    }
                }
                is LiveStage.Failed -> StickyActionBar {
                    // No other examiner to fall back to: score what was recorded, or reconnect (not after a refusal, the limit panel covers that).
                    if (exam.hasRecordings) NbButton("Score what was recorded", exam::scoreRecorded, fill = true, size = BtnSize.Large)
                    else if (stage.error == null) NbButton("Reconnect examiner", exam::start, fill = true, size = BtnSize.Large)
                    NbButton("Back to speaking", ::leave, kind = BtnKind.Text, fill = true)
                }
                is LiveStage.Unattached -> StickyActionBar {
                    NbButton("Try again", exam::retryAttach, fill = true, size = BtnSize.Large)
                    NbButton("Back to your mock", nav::back, kind = BtnKind.Text, fill = true)
                }
                is LiveStage.Finished -> StickyActionBar {
                    if (route.mockId != null) NbButton("Back to your mock", nav::back, fill = true, size = BtnSize.Large)
                    else NbButton("See my score", { nav.replace(AttemptResult(stage.ids, fresh = true)) }, fill = true, size = BtnSize.Large)
                }
                else -> {}
            }
        },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (stage) {
                LiveStage.Ready -> PreScreen(exam, onCheck = ::checkMic)
                LiveStage.Running -> Stage(exam, now, captions, connecting)
                LiveStage.Uploading -> Column(
                    Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
                ) {
                    CircularProgressIndicator(color = NB.colors.brand)
                    Text("Uploading your recordings", style = NB.type.body, color = NB.colors.muted, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                }
                is LiveStage.Failed -> Message(
                    Icons.Rounded.Warning, NB.colors.warnText, NB.colors.warnSoft, "The test stopped", stage.message,
                )
                is LiveStage.Finished -> Ended(stage.ids.size, route.mockId != null)
                is LiveStage.Unattached -> Message(
                    Icons.Rounded.Warning, NB.colors.warnText, NB.colors.warnSoft, "Not added to your mock yet",
                    "Your answers were scored, but this test couldn't be linked to your mock. Check your connection and try again.",
                )
            }
        }
    }

    // After the test: show the summary for a moment, then open the result (a mock test goes back to its hub, the session is already linked to it).
    if (stage is LiveStage.Finished && demo == null) {
        LaunchedEffect(stage) {
            delay(2200)
            if (route.mockId != null) nav.back() else nav.replace(AttemptResult(stage.ids, fresh = true))
        }
    }
    if (confirmEnd) {
        ConfirmDialog(
            "End the test now?", "The parts you've already recorded will be scored. You can't resume this test.", "End test",
            onConfirm = { confirmEnd = false; exam.endTest() }, onDismiss = { confirmEnd = false }, dismiss = "Keep going", destructive = true,
        )
    }
    if (confirmDiscard) {
        ConfirmDialog(
            "Leave without scoring?", "The recordings still on this phone are deleted and can't be recovered. Parts already sent for scoring stay in your history, where you can delete them.", "Leave",
            onConfirm = { confirmDiscard = false; nav.back() }, onDismiss = { confirmDiscard = false }, dismiss = "Stay", destructive = true,
        )
    }
}

// MARK: Pre-screen

private class Step(val part: Int, val name: String, val time: String, val text: String)

private val steps = listOf(
    Step(1, "Interview", "4–5 min", "Questions about you and familiar topics."),
    Step(2, "Long turn", "3–4 min", "One minute to prepare from a cue card, then speak for up to two minutes."),
    Step(3, "Discussion", "4–5 min", "A discussion of broader ideas linked to Part 2."),
)

@Composable
private fun PreScreen(exam: LiveModel, onCheck: () -> Unit) {
    val c = NB.colors
    Column(
        Modifier.fillMaxSize().readable().verticalScroll(rememberScrollState()).padding(horizontal = NB.space.gutter, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(NB.space.lg),
    ) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            VoiceOrb(OrbMode.Idle, { 0f }, orbSize = 150.dp)
            Text("Talk to a live examiner", style = NB.type.headline, color = c.ink, textAlign = TextAlign.Center, modifier = Modifier.semantics { heading() })
            Text(
                "A full speaking test with a voice examiner, about 11–14 minutes, like the real thing.",
                style = NB.type.body, color = c.muted, textAlign = TextAlign.Center,
            )
        }
        Overline("Microphone check", Modifier.semantics { heading() })
        NbCard {
            Row(verticalAlignment = Alignment.Top) {
                Icon(Icons.Rounded.Headphones, null, Modifier.size(22.dp), tint = c.muted)
                Spacer(Modifier.width(12.dp))
                Text("Headphones work best, so the examiner's voice doesn't reach your microphone. Find a quiet room.", style = NB.type.bodySmall, color = c.ink)
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "The microphone check stays on this phone. During the test your voice is streamed to an AI voice examiner and recorded so your answers can be scored. Delete any result from History.",
                style = NB.type.bodySmall, color = c.muted,
            )
            Spacer(Modifier.height(14.dp))
            if (exam.micReady) {
                MicMeter(exam.level.toFloat(), heard = exam.micHeard, quiet = false)
                Spacer(Modifier.height(10.dp))
                Row(Modifier.semantics { liveRegion = LiveRegionMode.Polite }, verticalAlignment = Alignment.CenterVertically) {
                    if (exam.micHeard) {
                        Icon(Icons.Rounded.CheckCircle, null, Modifier.size(20.dp), tint = c.goodText)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(
                        if (exam.micHeard) "We can hear you clearly" else "Say a few words, like your name",
                        style = if (exam.micHeard) NB.type.title3 else NB.type.bodySmall, color = if (exam.micHeard) c.goodText else c.muted,
                    )
                }
            } else {
                NbButton(if (exam.micChecking) "Checking…" else "Test microphone", onCheck, kind = BtnKind.Secondary, enabled = !exam.micChecking)
                exam.micError?.let { Spacer(Modifier.height(8.dp)); Text(it, style = NB.type.bodySmall, color = c.badText) }
            }
        }
        if (exam.micDenied) {
            val ctx = LocalContext.current
            Banner(
                "Microphone access is off. Turn it on in settings to take the test.", Icons.Rounded.Warning, tone = Tone.Warn,
                action = "Settings", onAction = { openAppSettings(ctx) },
            )
        }
        Overline("How it runs", Modifier.semantics { heading() })
        RowGroup {
            steps.forEachIndexed { i, s ->
                if (i > 0) RowDivider()
                NbListRow(
                    title = "Part ${s.part} · ${s.name}", subtitle = s.text,
                    trailing = { Text(s.time, style = NB.type.caption.copy(fontFeatureSettings = "tnum"), color = c.muted) },
                    modifier = Modifier.semantics(mergeDescendants = true) {},
                )
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

// MARK: Stage

@Composable
private fun PartStepper(phase: String) {
    val c = NB.colors
    val cur = when (phase) { "p2-prep", "p2-talk", "p2-follow" -> 2; "p3" -> 3; "closing" -> 4; else -> 1 }
    Row(
        Modifier.semantics(mergeDescendants = true) { contentDescription = "Part ${cur.coerceAtMost(3)} of 3" },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        (1..3).forEach { n ->
            val done = n < cur
            val now = n == cur
            Box(
                Modifier.size(30.dp)
                    .background(if (done) c.brand else if (now) c.brandSoft else c.surface2, CircleShape)
                    .let { if (now) it.border(2.dp, c.brand, CircleShape) else it },
                contentAlignment = Alignment.Center,
            ) {
                if (done) Icon(Icons.Rounded.Check, null, Modifier.size(16.dp), tint = c.onBrand)
                else Text("$n", style = NB.type.label, color = if (now) c.brandText else c.muted)
            }
            if (n < 3) Box(Modifier.width(22.dp).height(2.dp).background(if (done) c.brand else c.line))
        }
    }
}

@Composable
private fun Stage(exam: LiveModel, now: Long, captions: Boolean, connecting: Boolean) {
    val c = NB.colors
    val part2 = exam.phase == "p2-prep" || exam.phase == "p2-talk" || exam.phase == "p2-follow"
    val card = exam.cueCard
    val mode = when {
        connecting -> OrbMode.Connecting
        exam.thinking -> OrbMode.Thinking
        exam.examinerTalking -> OrbMode.Speaking
        exam.listening -> OrbMode.Listening
        else -> OrbMode.Idle
    }
    val status = if (connecting) "Connecting to your examiner" else exam.statusText
    Column(
        Modifier.fillMaxSize().readable().verticalScroll(rememberScrollState()).padding(horizontal = NB.space.gutter, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(NB.space.lg),
    ) {
        PartStepper(exam.phase)
        Text(phaseLabel(exam.phase), style = NB.type.label, color = c.muted, textAlign = TextAlign.Center, modifier = Modifier.semantics { heading() })
        VoiceOrb(mode, { exam.level.toFloat() }, orbSize = if (part2 && card != null) 170.dp else 240.dp)
        Text(
            status.ifEmpty { " " }, style = NB.type.title1, color = c.ink, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        )
        if (captions && exam.caption.isNotEmpty()) {
            NbCard(tone = CardTone.Inset) {
                Overline("Examiner")
                Spacer(Modifier.height(4.dp))
                Text(exam.caption, style = NB.type.readingSmall, color = c.ink)
            }
        }
        if (part2 && card != null) {
            CueCard(card)
            if (exam.phase == "p2-prep") {
                val low = exam.prepLeft <= 10
                TimerRing(
                    exam.prepLeft.coerceIn(0, 60) / 60f, if (low) c.warn else c.brand, 104.dp, 7.dp,
                    Modifier.clearAndSetSemantics { contentDescription = "Preparation time left ${clockText(exam.prepLeft.toLong())}" },
                ) { Text(clockText(exam.prepLeft.toLong()), style = NB.type.band(24), color = if (low) c.warnText else c.ink) }
                Text("Use this minute to prepare. The examiner will ask you to start.", style = NB.type.bodySmall, color = c.muted, textAlign = TextAlign.Center)
            } else if (exam.phase == "p2-talk") {
                val s = ((now - exam.phaseStartedAt) / 1000).toInt().coerceIn(0, 120)
                TimerRing(
                    s / 120f, c.brand, 104.dp, 7.dp,
                    Modifier.clearAndSetSemantics { contentDescription = "Speaking time ${clockText(s.toLong())} of 2:00" },
                ) { Text(clockText(s.toLong()), style = NB.type.band(24), color = c.ink) }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

// MARK: Failed, ended

@Composable
private fun Message(icon: androidx.compose.ui.graphics.vector.ImageVector, fg: androidx.compose.ui.graphics.Color, bg: androidx.compose.ui.graphics.Color, title: String, body: String) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = NB.space.gutter * 1.4f),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        IconTile(icon, fg, bg, 72.dp)
        Text(title, style = NB.type.headline, color = NB.colors.ink, textAlign = TextAlign.Center, modifier = Modifier.semantics { heading() })
        Text(body, style = NB.type.body, color = NB.colors.muted, textAlign = TextAlign.Center)
    }
}

@Composable
private fun Ended(parts: Int, mock: Boolean) {
    val c = NB.colors
    Column(
        Modifier.fillMaxSize().padding(horizontal = NB.space.gutter),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
    ) {
        IconTile(Icons.Rounded.CheckCircle, c.goodText, c.goodSoft, 72.dp)
        Text("Test complete", style = NB.type.headline, color = c.ink, modifier = Modifier.semantics { heading(); liveRegion = LiveRegionMode.Polite })
        Text(
            if (mock) "Your recordings are uploaded. Your mock continues from the hub."
            else "Your recordings are uploaded. Marking usually takes under a minute.",
            style = NB.type.body, color = c.muted, textAlign = TextAlign.Center,
        )
        RowGroup {
            (1..parts.coerceIn(1, 3)).forEachIndexed { i, n ->
                if (i > 0) RowDivider()
                NbListRow(
                    title = "Part $n · ${steps[n - 1].name}", subtitle = "Recorded and sent for marking",
                    leading = { Icon(Icons.Rounded.CheckCircle, null, tint = c.goodText) },
                )
            }
        }
    }
}
