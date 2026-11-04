package com.soyeb.ieltspractice.feature.writing

import android.content.Intent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.soyeb.ieltspractice.LocalDemo
import com.soyeb.ieltspractice.LocalGraph
import com.soyeb.ieltspractice.data.logic.bandRange
import com.soyeb.ieltspractice.data.logic.clipWords
import com.soyeb.ieltspractice.data.logic.criterionLabel
import com.soyeb.ieltspractice.data.logic.errorTitle
import com.soyeb.ieltspractice.data.logic.fmt
import com.soyeb.ieltspractice.data.logic.minWords
import com.soyeb.ieltspractice.data.logic.resScore
import com.soyeb.ieltspractice.data.logic.sentenceCase
import com.soyeb.ieltspractice.data.logic.taskLabel
import com.soyeb.ieltspractice.data.logic.writingSessionOverall
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
import com.soyeb.ieltspractice.design.components.CardTone
import com.soyeb.ieltspractice.design.components.ConfirmDialog
import com.soyeb.ieltspractice.design.components.EmptyState
import com.soyeb.ieltspractice.design.components.LocalSnack
import com.soyeb.ieltspractice.design.components.NbButton
import com.soyeb.ieltspractice.design.components.NbCard
import com.soyeb.ieltspractice.design.components.NbIconButton
import com.soyeb.ieltspractice.design.components.NbTabs
import com.soyeb.ieltspractice.design.components.Overline
import com.soyeb.ieltspractice.design.components.PushedScreen
import com.soyeb.ieltspractice.design.components.Segmented
import com.soyeb.ieltspractice.design.components.StickyActionBar
import com.soyeb.ieltspractice.feature.result.ResultHero
import com.soyeb.ieltspractice.feature.study.SeenBefore
import com.soyeb.ieltspractice.nav.Nav
import com.soyeb.ieltspractice.nav.AttemptResult
import com.soyeb.ieltspractice.nav.WritingTask
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min

private val tabNames = listOf("Overview", "Essay", "Structure", "Language", "Improve")

/** Contract used by feature/result/ResultScreen: the finished writing result (one task, or both tasks of a full test). Owned by the writing feature. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WritingResult(attempts: List<Attempt>, nav: Nav) {
    val graph = LocalGraph.current
    val api = graph.api
    val demo = LocalDemo.current
    val snack = LocalSnack.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val me by api.me.collectAsState()
    val target = me?.settings?.targetBand ?: 7.0
    val ordered = remember(attempts) { attempts.sortedBy { it.part } }
    val multi = ordered.size > 1
    var sel by rememberSaveable { mutableIntStateOf(0) }
    val a = ordered[sel.coerceIn(ordered.indices)]
    val r = a.analysis
    val demoParts = demo?.tab?.split('-').orEmpty()
    var tab by rememberSaveable { mutableIntStateOf(tabNames.indexOf(demoParts.firstOrNull() ?: "").coerceAtLeast(0)) }
    var errorSheet by remember { mutableStateOf<AnalysisError?>(if ("Sheet" in demoParts && demoParts.first() == "Essay") r?.errors?.firstOrNull() else null) }
    var criterionSheet by remember { mutableStateOf<String?>(if ("Sheet" in demoParts && demoParts.first() == "Overview") "ta" else null) }
    var lean by remember { mutableStateOf<Repeated?>(null) }
    var showDiff by remember { mutableStateOf(false) }
    var added by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val parentText by produceState<String?>(null, a.parentAttemptId) {
        value = a.parentAttemptId?.let { id -> runCatching { api.attempt(id) }.getOrNull()?.answerText }
    }

    fun say(text: String) { scope.launch { snack.currentSnackbarData?.dismiss(); snack.showSnackbar(text) } }
    fun taskRoute(parent: Boolean) = WritingTask(
        task = if (a.part == 1) "task1" else "task2", variant = a.prompt.variant, promptId = a.promptId, parentId = if (parent) a.id else null,
    )

    val gut = Modifier.padding(horizontal = NB.space.gutter)
    val score = r?.let { resScore(it) }

    Box(Modifier.fillMaxSize()) {
        PushedScreen(
            title = "Writing result", onBack = nav::back,
            actions = {
                Box {
                    NbIconButton(Icons.Rounded.MoreVert, "More options", { menu = true })
                    DropdownMenu(menu, { menu = false }, containerColor = NB.colors.surface) {
                        DropdownMenuItem(
                            text = { Text("Share", style = NB.type.body) },
                            onClick = {
                                menu = false
                                val text = "I got band ${fmt(score?.overall ?: 0.0)} on an IELTS ${taskLabel(a.prompt)} writing task with NextBand. nextband.app"
                                val send = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) }
                                runCatching { context.startActivity(Intent.createChooser(send, "Share result")) }
                            },
                        )
                        DropdownMenuItem(text = { Text("Delete result", style = NB.type.body, color = NB.colors.badText) }, onClick = { menu = false; confirmDelete = true })
                    }
                }
            },
            bottomBar = {
                StickyActionBar {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NbButton("Retake this prompt", { nav.startTest("writing") { nav.go(taskRoute(true)) } }, Modifier.weight(1f), kind = BtnKind.Secondary, size = BtnSize.Large)
                        NbButton(
                            "Write another",
                            { nav.startTest("writing") { nav.go(if (multi) WritingTask("full", a.prompt.variant) else WritingTask(taskRoute(false).task, a.prompt.variant)) } },
                            Modifier.weight(1f), size = BtnSize.Large, haptic = true,
                        )
                    }
                }
            },
        ) { pad ->
            if (r == null || score == null) {
                EmptyState(Icons.Rounded.Warning, "No marking yet", "This answer has no analysis.", Modifier.padding(pad))
                return@PushedScreen
            }
            val essay = r.text ?: a.text.orEmpty()
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = pad.calculateTopPadding() + 4.dp, bottom = pad.calculateBottomPadding() + 32.dp),
                verticalArrangement = Arrangement.spacedBy(NB.space.lg),
            ) {
                if (multi) item {
                    val combined = writingSessionOverall(ordered)
                    Column(gut, verticalArrangement = Arrangement.spacedBy(NB.space.md)) {
                        Segmented(
                            ordered.map { t -> "Task ${t.part}" + (t.analysis?.let { x -> " · ${fmt(resScore(x).overall)}" } ?: "") }, sel,
                            { sel = it; lean = null },
                        )
                        if (combined != null) NbCard(tone = CardTone.Inset) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("Writing band for this test", style = NB.type.title3, color = NB.colors.ink)
                                    Text("Task 2 counts twice as much as Task 1.", style = NB.type.caption, color = NB.colors.muted)
                                }
                                BandPill(combined, target, large = true)
                            }
                        }
                    }
                }
                item {
                    val words = r.textMetrics?.words ?: wordCount(essay)
                    val note = listOfNotNull(
                        "Task ${a.part}", plural(words), minutesText(a.durationMs), if (a.overtime == true) "Overtime" else null,
                        if (r.calibrated == false) "AI estimate" else null,
                    ).joinToString(" · ")
                    val range = if (score.overall > 0 && score.range.size == 2) bandRange(max(score.range[0], score.overall - 1), min(score.range[1], score.overall + 1)) else null
                    ResultHero(
                        Skill.Writing, "Writing · ${taskLabel(a.prompt)}", clipWords(sentenceCase(a.prompt.title), 70), score.overall, target, gut,
                        range = range, note = note,
                    )
                }
                item {
                    val words = r.textMetrics?.words ?: wordCount(essay)
                    val ta = if (a.part == 1) "Task Achievement" else "Task Response"
                    val under = r.tooShort != true && words < minWords(a.part)
                    val kind = if (a.part == 1) "answer" else "essay"
                    Column(gut, verticalArrangement = Arrangement.spacedBy(NB.space.sm)) {
                        if (r.tooShort == true) Banner(
                            "Responses of 20 words or fewer are rated Band 1 on every criterion. Aim for at least ${minWords(a.part)} words.",
                            Icons.Rounded.Warning, tone = Tone.Warn,
                        )
                        else {
                            if (score.offTopic) Banner(
                                "Your $kind doesn't answer this question, so your overall band can't go above ${fmt((r.criteria["ta"]?.band ?: 0.0) + 1)}: one band over your $ta score.",
                                Icons.Rounded.Warning, tone = Tone.Bad, action = "Rewrite", onAction = { nav.startTest("writing") { nav.go(taskRoute(true)) } },
                            )
                            if (under) Banner("You wrote $words of the ${minWords(a.part)} words required, which lowers $ta.", Icons.Rounded.Warning, tone = Tone.Warn)
                        }
                    }
                }
                stickyHeader { NbTabs(tabNames, tab, { tab = it }) }
                item {
                    when (tab) {
                        0 -> Column { OverviewPanel(
                            r, score, target, { criterionSheet = it }, { nav.go(AttemptResult.of(it)) },
                            onAddFixes = {
                                nav.requireAccount("Create an account to drill your mistakes") {
                                    scope.launch {
                                        try {
                                            api.addCards(r.topFixes.map { CardInput("${it.title}\n\n${it.before}", "${it.after}\n\n${it.why}", "fix") })
                                            added = true
                                            say("Added ${r.topFixes.size} ${if (r.topFixes.size == 1) "fix" else "fixes"} to your drills")
                                        } catch (e: CancellationException) { throw e } catch (e: Exception) { say(e.message ?: "Couldn't add the cards.") }
                                    }
                                }
                            },
                            fixesInDeck = added || a.topFixesInDeck, modifier = gut,
                        ); SeenBefore(a.id, nav, gut.padding(top = NB.space.lg)) }
                        1 -> if (essay.isBlank()) EmptyState(Icons.Rounded.Warning, "No essay text", "Nothing was written for this task.")
                        else EssayPanel(r, essay, lean, { lean = null }, { errorSheet = it }, gut)
                        2 -> r.structure?.let { StructurePanel(it, gut) }
                            ?: EmptyState(Icons.Rounded.Warning, "No structure map", "The answer was too short to map its paragraphs.")
                        3 -> LanguagePanel(r, essay, lean, { w -> lean = if (lean?.word == w.word) null else w; if (lean != null) tab = 1 }, gut)
                        else -> ImprovePanel(r, essay, parentText, showDiff, { showDiff = it }, gut)
                    }
                }
            }
        }

        errorSheet?.let { x ->
            SheetOrInline(
                onDismiss = { errorSheet = null }, title = errorTitle(x.category),
                actions = {
                    NbButton("Add to drills", {
                        errorSheet = null
                        nav.requireAccount("Create an account to drill your mistakes") {
                            scope.launch {
                                try {
                                    api.addCard(x.original, "${x.correction} — ${x.explanation}", "mistake", x.category)
                                    say("Added to your drills")
                                } catch (e: CancellationException) { throw e } catch (e: Exception) { say(e.message ?: "Couldn't add the card.") }
                            }
                        }
                    }, fill = true, size = BtnSize.Large, kind = BtnKind.Secondary)
                },
            ) {
                SeverityTag(x.severity)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(x.original, style = NB.type.reading, color = NB.colors.badText, textDecoration = TextDecoration.LineThrough)
                    Text(x.correction, style = NB.type.reading, color = NB.colors.goodText)
                }
                Text(x.explanation, style = NB.type.body, color = NB.colors.ink)
            }
        }
        criterionSheet?.let { k ->
            val cr = r?.criteria?.get(k)
            if (cr != null) SheetOrInline(
                onDismiss = { criterionSheet = null }, title = criterionLabel(k),
                subtitle = "Band ${fmt(cr.band)}" + if (cr.range.size == 2) " · likely ${bandRange(cr.range[0], cr.range[1])}" else "",
            ) {
                Text(cr.summary, style = NB.type.body, color = NB.colors.ink)
                if (cr.evidence.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Overline("Evidence from your answer")
                    cr.evidence.forEach { q ->
                        Box(
                            Modifier.fillMaxWidth().background(NB.colors.surface2, NB.shape.tile).padding(horizontal = 12.dp, vertical = 8.dp),
                        ) { Text(cleanQuote(q), style = NB.type.readingSmall, color = NB.colors.ink) }
                    }
                }
                if (cr.descriptor.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Overline("Band descriptor")
                    Text(cr.descriptor, style = NB.type.bodySmall, color = NB.colors.muted)
                }
            }
        }
        if (confirmDelete) ConfirmDialog(
            title = "Delete this result?",
            body = if (multi) "Both tasks and their marking are removed from your history. The tests aren't given back." else "The essay and its marking are removed from your history. The test isn't given back.",
            confirm = "Delete", destructive = true,
            onConfirm = {
                confirmDelete = false
                scope.launch {
                    try {
                        ordered.forEach { api.deleteAttempt(it.id) }
                        nav.back()
                    } catch (e: CancellationException) { throw e } catch (e: Exception) { say(e.message ?: "Couldn't delete this result.") }
                }
            },
            onDismiss = { confirmDelete = false },
        )
    }
}
