package com.soyeb.ieltspractice.feature.speaking

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.Lifecycle
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.soyeb.ieltspractice.LocalDemo
import com.soyeb.ieltspractice.LocalGraph
import com.soyeb.ieltspractice.data.logic.Band
import com.soyeb.ieltspractice.data.logic.Crit
import com.soyeb.ieltspractice.data.logic.DiffOp
import com.soyeb.ieltspractice.data.logic.Timeline
import com.soyeb.ieltspractice.data.logic.bandRange
import com.soyeb.ieltspractice.data.logic.criterionLabel
import com.soyeb.ieltspractice.data.logic.formatDate
import com.soyeb.ieltspractice.data.logic.formatDuration
import com.soyeb.ieltspractice.data.logic.notAssessed
import com.soyeb.ieltspractice.data.logic.offTopic
import com.soyeb.ieltspractice.data.logic.offTopicPenaltyText
import com.soyeb.ieltspractice.data.logic.pronUnsupported
import com.soyeb.ieltspractice.data.logic.sentenceCase
import com.soyeb.ieltspractice.data.logic.speakingSessionOverall
import com.soyeb.ieltspractice.data.logic.wordDiff
import com.soyeb.ieltspractice.data.model.AnalysisError
import com.soyeb.ieltspractice.data.model.Attempt
import com.soyeb.ieltspractice.data.model.CardInput
import com.soyeb.ieltspractice.data.model.Repeated
import com.soyeb.ieltspractice.design.NB
import com.soyeb.ieltspractice.design.Skill
import com.soyeb.ieltspractice.design.Tone
import com.soyeb.ieltspractice.design.components.Banner
import com.soyeb.ieltspractice.design.components.BandPill
import com.soyeb.ieltspractice.design.components.BtnKind
import com.soyeb.ieltspractice.design.components.BtnSize
import com.soyeb.ieltspractice.design.components.ChipRow
import com.soyeb.ieltspractice.design.components.EmptyState
import com.soyeb.ieltspractice.design.components.LocalSnack
import com.soyeb.ieltspractice.design.components.NbButton
import com.soyeb.ieltspractice.design.components.NbCard
import com.soyeb.ieltspractice.design.components.NbListRow
import com.soyeb.ieltspractice.design.components.NbSheet
import com.soyeb.ieltspractice.design.components.NbTabs
import com.soyeb.ieltspractice.design.components.PushedList
import com.soyeb.ieltspractice.design.components.PushedScreen
import com.soyeb.ieltspractice.design.components.RowDivider
import com.soyeb.ieltspractice.design.components.RowGroup
import com.soyeb.ieltspractice.design.components.Segmented
import com.soyeb.ieltspractice.design.components.SectionHeader
import com.soyeb.ieltspractice.design.components.StickyActionBar
import com.soyeb.ieltspractice.design.components.Tag
import com.soyeb.ieltspractice.design.fg
import com.soyeb.ieltspractice.design.bandTone
import com.soyeb.ieltspractice.feature.result.CriteriaCard
import com.soyeb.ieltspractice.feature.result.FixCard
import com.soyeb.ieltspractice.feature.result.NoSpeechState
import com.soyeb.ieltspractice.feature.result.ResultHero
import com.soyeb.ieltspractice.feature.study.SeenBefore
import com.soyeb.ieltspractice.nav.Nav
import com.soyeb.ieltspractice.nav.SpeakingSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Screenshot variants: `tab` is "Overview", "Transcript", "Fluency", ... and "Transcript-Sheet" opens the first mistake. */
private class DemoView(val tab: String, val sheet: Boolean) {
    companion object {
        fun parse(raw: String?): DemoView? = raw?.let { r -> r.split('-').let { DemoView(it[0], it.drop(1).any { x -> x == "Sheet" }) } }
    }
}

/**
 * Contract used by feature/result/ResultScreen: the finished speaking result for one attempt or a multi-part session
 * ([attempts] in part order, every one `status == "done"`). Owned by the speaking feature.
 */
@Composable
fun SpeakingResult(attempts: List<Attempt>, nav: Nav) {
    val graph = LocalGraph.current
    val demo = LocalDemo.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val snack = LocalSnack.current
    val me by graph.api.me.collectAsState()
    val target = me?.settings?.targetBand ?: 7.0
    val parts = remember(attempts) { attempts.sortedBy { it.part } }
    if (parts.isEmpty()) {
        PushedScreen("Speaking result", nav::back, scrollBehavior = null) { pad ->
            EmptyState(Icons.Rounded.Mic, "No result", "This result isn't available.", Modifier.padding(pad).verticalScroll(rememberScrollState()))
        }
        return
    }
    val demoView = remember { DemoView.parse(demo?.tab) }
    var sel by rememberSaveable { mutableStateOf(0) }
    val attempt = parts[sel.coerceIn(0, parts.lastIndex)]
    val r = attempt.analysis
    val ok = attempt.status == "done" && r != null && !notAssessed(r)
    val pron = r?.pronunciation?.audio
    val tabs = listOfNotNull("Overview", "Transcript", "Fluency", "Language", if (pron != null) "Pronunciation" else null, "Improve")
    var tab by rememberSaveable { mutableStateOf(demoView?.tab ?: "Overview") }
    val current = if (tab in tabs) tab else "Overview"
    val timeline = remember(r) { r?.let { Timeline.of(it) } ?: Timeline.Empty }
    var focus by remember { mutableStateOf<String?>(null) }
    var lean by remember { mutableStateOf<Repeated?>(null) }
    var selectedError by remember(attempt.id) { mutableStateOf(if (demoView?.sheet == true) r?.errors?.firstOrNull() else null) }
    var compare by rememberSaveable { mutableStateOf(false) }
    var addedFixes by remember(attempt.id) { mutableStateOf(attempt.topFixesInDeck) }
    var listen by rememberSaveable { mutableStateOf(0) }

    // The recording, and (live examiner) the whole conversation. Demo mode has no engine: the player looks loaded at a fixed spot.
    val player = remember(attempt.id) { ResultPlayer(if (demo != null && ok) r?.metrics?.durationS else null) }
    DisposableEffect(player) { onDispose { player.release() } }
    LaunchedEffect(attempt.id, attempt.audioUrl) {
        val url = attempt.audioUrl
        if (url != null && demo == null) { player.load(ctx, url); player.poll() }
    }
    val conversation = remember(attempt.id) { ResultPlayer() }
    DisposableEffect(conversation) { onDispose { conversation.release() } }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { if (demo == null) { player.pause(); conversation.pause() } }
    LaunchedEffect(listen, attempt.conversationUrl) {
        val url = attempt.conversationUrl
        if (listen == 1 && url != null && demo == null) { conversation.load(ctx, url); conversation.poll() }
    }

    fun addMistake(err: AnalysisError) {
        scope.launch {
            val msg = try {
                graph.api.addCard(err.original.ifBlank { categoryText(err) }, listOf(err.correction, err.explanation).filter { it.isNotBlank() }.joinToString(" — "), "mistake", err.category)
                "Added to your drills"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                e.message ?: "Couldn't add the card."
            }
            snack.showSnackbar(msg)
        }
    }
    fun addFixes() {
        val fixes = r?.topFixes.orEmpty()
        scope.launch {
            val msg = try {
                graph.api.addCards(fixes.map { CardInput("${it.title}\n\n${it.before}".trim(), "${it.after}\n\n${it.why}".trim(), "fix") })
                addedFixes = true
                "Added ${fixes.size} ${if (fixes.size == 1) "fix" else "fixes"} to your drills"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                e.message ?: "Couldn't add the cards."
            }
            snack.showSnackbar(msg)
        }
    }
    fun practise() = nav.startTest("speaking") {
        nav.go(SpeakingSession(modeForPart(attempt.part), promptId = attempt.promptId, parentId = attempt.id))
    }

    val showAudio = ok && current in listOf("Transcript", "Fluency", "Language", "Pronunciation") && (player.isLoaded || attempt.conversationUrl != null)

    PushedScreen(
        "Speaking result", nav::back, scrollBehavior = null,
        bottomBar = {
            if (ok && current == "Improve") StickyActionBar {
                NbButton("Practise this again", ::practise, fill = true, size = BtnSize.Large)
            }
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            if (parts.size > 1) ChipRow(
                parts.map { "Part ${it.part}" }, setOf(sel.coerceIn(0, parts.lastIndex)), { sel = it; focus = null },
                Modifier.padding(top = 4.dp, bottom = 8.dp),
            )
            if (ok) NbTabs(tabs, tabs.indexOf(current), { tab = tabs[it] })
            if (showAudio) {
                if (attempt.conversationUrl != null) Segmented(
                    listOf("Your answers", "With the examiner"), listen, { i -> if (i == 1) player.pause() else conversation.pause(); listen = i },
                    Modifier.padding(horizontal = NB.space.gutter, vertical = 8.dp),
                )
                if (listen == 1 && attempt.conversationUrl != null) ResultAudioBar(conversation, Timeline.Empty)
                else if (player.isLoaded) ResultAudioBar(player, timeline)
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                key(sel, current) {
                    PushedList(PaddingValues(0.dp), state = rememberLazyListState()) {
                        when {
                            r == null || attempt.status != "done" -> item {
                                EmptyState(Icons.Rounded.Info, "No analysis", "This answer has no analysis yet. Check History in a minute.")
                            }
                            notAssessed(r) -> item { NoSpeechState(onRetake = ::practise) }
                            else -> when (current) {
                                "Overview" -> {
                                    item { Hero(parts, attempt, target) }
                                    if (parts.size > 1) {
                                        item { PartsCard(parts, sel.coerceIn(0, parts.lastIndex), target) { sel = it } }
                                        item { SectionHeader("Part ${attempt.part} in detail") }
                                    }
                                    offTopic(r)?.let { (off, total) ->
                                        item {
                                            Banner(
                                                "$off of $total answers didn't address the question.${offTopicPenaltyText(r)}", Icons.Rounded.Warning, tone = Tone.Warn,
                                                action = "See why", onAction = { tab = "Language" },
                                            )
                                        }
                                    }
                                    if (r.calibrated == false) item {
                                        Banner("Marked by an AI model that isn't validated yet, so treat this band as a guide: the likely range is wider than usual.", Icons.Rounded.Info)
                                    }
                                    item {
                                        CriteriaCard("speaking", r.criteria, target, onOpen = { k ->
                                            tab = if (k == "fc") "Fluency" else if (k == "p" && pron != null) "Pronunciation" else "Language"
                                        })
                                    }
                                    item { Notes(r, target) }
                                    item { SeenBefore(attempt.id, nav, Modifier.padding(horizontal = NB.space.gutter)) }
                                }
                                "Transcript" -> item {
                                    TranscriptPanel(
                                        r, player, timeline, focus, lean, onSelect = { selectedError = it },
                                        onAlsoNoted = { err -> ErrorDetails(err, playAction(player, err), { addMistake(err) }) },
                                    )
                                }
                                "Fluency" -> item {
                                    val m = r.metrics
                                    if (m == null) EmptyState(Icons.Rounded.Info, "No fluency data", "This result was saved before fluency measures existed.")
                                    else FluencyPanel(m, player, timeline, focus, { focus = it }, r.criteria["fc"], target)
                                }
                                "Language" -> item {
                                    LanguagePanel(r, player, lean, { w -> lean = w; if (w != null) tab = "Transcript" }, onSelect = { selectedError = it })
                                }
                                "Pronunciation" -> item { if (pron != null) PronunciationPanel(pron, player) }
                                else -> {
                                    item { SectionHeader("Top fixes") }
                                    if (r.topFixes.isEmpty()) item { Text("No fixes this time.", style = NB.type.bodySmall, color = NB.colors.muted) }
                                    r.topFixes.forEachIndexed { i, f -> item { FixCard(i + 1, f) } }
                                    if (r.topFixes.isNotEmpty()) item {
                                        NbButton(
                                            if (addedFixes) "Added to your drills" else "Add fixes to drills", ::addFixes, kind = BtnKind.Secondary,
                                            enabled = !addedFixes, fill = true,
                                        )
                                    }
                                    item { SectionHeader("Model answer") }
                                    item { ModelAnswer(attempt, r.rewrite.text, r.rewrite.note, compare) { compare = it } }
                                }
                            }
                        }
                    }
                }
                // A real bottom sheet opens in its own window, which a screenshot cannot see: demo mode draws the same content inline.
                selectedError?.let { err ->
                    val body: @Composable () -> Unit = {
                        ErrorDetails(err, playAction(player, err), { addMistake(err) })
                    }
                    if (demo != null) Surface(
                        Modifier.align(Alignment.BottomCenter).fillMaxWidth().border(1.dp, NB.colors.line, NB.shape.sheet),
                        shape = NB.shape.sheet, color = NB.colors.surface,
                    ) {
                        Column(Modifier.padding(horizontal = NB.space.gutter, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(categoryText(err), style = NB.type.title1, color = NB.colors.ink)
                            body()
                        }
                    }
                    else NbSheet({ selectedError = null }, title = categoryText(err)) { body() }
                }
            }
        }
    }
}

private fun categoryText(err: AnalysisError) = com.soyeb.ieltspractice.data.logic.errorTitle(err.category)

// MARK: Overview

@Composable
private fun Hero(parts: List<Attempt>, attempt: Attempt, target: Double) {
    val r = attempt.analysis ?: return
    if (parts.size > 1) {
        val overall = speakingSessionOverall(parts)
        val ranges = parts.mapNotNull { a -> a.analysis?.takeIf { !notAssessed(it) }?.range?.takeIf { it.size >= 2 } }
        val range = if (ranges.isEmpty()) null else bandRange(Band.round(ranges.map { it[0] }.average()), Band.round(ranges.map { it[1] }.average()))
        val ms = parts.sumOf { it.durationMs ?: 0 }
        ResultHero(
            Skill.Speaking, "Speaking · Full test", "Full test · ${maxOf(1, (ms / 60000.0).roundToInt())} min", overall?.first ?: r.overall, target,
            range = range, note = overall?.second?.let { "Scored on $it of ${parts.size} parts" },
        )
    } else {
        val title = sentenceCase(attempt.prompt.title).let { if (attempt.part == 1) "Part 1: $it" else it }
        ResultHero(
            Skill.Speaking, "Speaking · Part ${attempt.part}", title, r.overall, target,
            range = r.range.takeIf { it.size >= 2 }?.let { bandRange(it[0], it[1]) },
            note = listOfNotNull(formatDate(attempt.createdAt).ifEmpty { null }, attempt.durationMs?.takeIf { it > 0 }?.let(::formatDuration)).joinToString(" · ").ifEmpty { null },
        )
    }
}

@Composable
private fun PartsCard(parts: List<Attempt>, selected: Int, target: Double, onPick: (Int) -> Unit) {
    RowGroup {
        parts.forEachIndexed { i, a ->
            if (i > 0) RowDivider()
            NbListRow(
                title = "Part ${a.part} · ${partName(a.part)}",
                subtitle = listOfNotNull(sentenceCase(a.prompt.title), a.durationMs?.takeIf { it > 0 }?.let(::formatDuration)).joinToString(" · "),
                trailing = {
                    if (i == selected) Tag("Viewing", Modifier.padding(end = 8.dp), fg = NB.colors.brandText, bg = NB.colors.brandSoft)
                    BandPill(a.analysis?.overall, target)
                },
                chevron = false,
                onClick = { onPick(i) },
            )
        }
    }
}

/** What the examiner noted on each criterion, in words. */
@Composable
private fun Notes(r: com.soyeb.ieltspractice.data.model.AnalysisResult, target: Double) {
    RowGroup {
        Crit.order("speaking").filter { it in r.criteria }.forEachIndexed { i, k ->
            if (i > 0) RowDivider()
            val c = r.criteria.getValue(k)
            val extra = if (pronUnsupported(r, k)) " Pronunciation is judged from the audio alone, so treat it as a guide." else ""
            NbListRow(
                title = criterionLabel(k), subtitle = c.summary + extra,
                trailing = { BandPill(c.band, target) },
            )
        }
    }
}

// MARK: Improve

@Composable
private fun ModelAnswer(attempt: Attempt, model: String, note: String, compare: Boolean, onCompare: (Boolean) -> Unit) {
    val c = NB.colors
    val yours = attempt.answerText.orEmpty()
    NbCard {
        if (yours.isNotBlank()) {
            Segmented(listOf("Model answer", "Compare with yours"), if (compare) 1 else 0, { onCompare(it == 1) })
            Spacer(Modifier.height(14.dp))
        }
        if (compare && yours.isNotBlank()) {
            val runs = remember(yours, model) { wordDiff(yours, model) }
            Text(
                buildAnnotatedString {
                    runs.forEachIndexed { i, run ->
                        if (i > 0) append(" ")
                        when (run.op) {
                            DiffOp.Same -> append(run.text)
                            DiffOp.Removed -> withStyle(SpanStyle(color = c.badText, textDecoration = TextDecoration.LineThrough)) { append(run.text) }
                            DiffOp.Added -> withStyle(SpanStyle(color = c.goodText, fontWeight = FontWeight.SemiBold, textDecoration = TextDecoration.Underline)) { append(run.text) }
                        }
                    }
                },
                style = NB.type.reading,
            )
            Text(
                "Struck-through words are in your answer only; underlined words are in the model answer only.",
                style = NB.type.caption, color = c.muted, modifier = Modifier.padding(top = 10.dp),
            )
        } else {
            Text(model, style = NB.type.reading, color = c.ink)
        }
        if (note.isNotBlank()) Text(note, style = NB.type.bodySmall, color = c.muted, modifier = Modifier.padding(top = 12.dp))
    }
}
