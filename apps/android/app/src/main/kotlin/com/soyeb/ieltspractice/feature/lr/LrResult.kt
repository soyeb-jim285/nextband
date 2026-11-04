package com.soyeb.ieltspractice.feature.lr

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.soyeb.ieltspractice.LocalDemo
import com.soyeb.ieltspractice.LocalGraph
import com.soyeb.ieltspractice.data.demo.lrCauses
import com.soyeb.ieltspractice.data.logic.Accuracy
import com.soyeb.ieltspractice.data.logic.FlatQ
import com.soyeb.ieltspractice.data.logic.accuracyBy
import com.soyeb.ieltspractice.data.logic.clock
import com.soyeb.ieltspractice.data.logic.flat
import com.soyeb.ieltspractice.data.logic.formatDate
import com.soyeb.ieltspractice.data.logic.lrWords
import com.soyeb.ieltspractice.data.logic.partsLabel
import com.soyeb.ieltspractice.data.logic.TextSpan
import com.soyeb.ieltspractice.data.logic.Timing
import com.soyeb.ieltspractice.data.logic.audioWindow
import com.soyeb.ieltspractice.data.logic.evidenceSpan
import com.soyeb.ieltspractice.data.logic.locatePhrase
import com.soyeb.ieltspractice.data.logic.sectionParagraphs
import com.soyeb.ieltspractice.data.logic.timingRows
import com.soyeb.ieltspractice.data.logic.typeLabel
import com.soyeb.ieltspractice.data.logic.wrongNote
import com.soyeb.ieltspractice.data.model.LrAttempt
import com.soyeb.ieltspractice.data.model.LrSection
import com.soyeb.ieltspractice.design.NB
import com.soyeb.ieltspractice.design.Skill
import com.soyeb.ieltspractice.design.Tone
import com.soyeb.ieltspractice.design.components.Banner
import com.soyeb.ieltspractice.design.components.BtnKind
import com.soyeb.ieltspractice.design.components.BtnSize
import com.soyeb.ieltspractice.design.components.CardTone
import com.soyeb.ieltspractice.design.components.ChipRow
import com.soyeb.ieltspractice.design.components.EmptyState
import com.soyeb.ieltspractice.design.components.ErrorState
import com.soyeb.ieltspractice.design.components.Load
import com.soyeb.ieltspractice.design.components.LocalSnack
import com.soyeb.ieltspractice.design.components.Meter
import com.soyeb.ieltspractice.design.components.NbButton
import com.soyeb.ieltspractice.design.components.NbCard
import com.soyeb.ieltspractice.design.components.NbChip
import com.soyeb.ieltspractice.design.components.NbSheet
import com.soyeb.ieltspractice.design.components.NbTabs
import com.soyeb.ieltspractice.design.components.PushedScreen
import com.soyeb.ieltspractice.design.components.RowDivider
import com.soyeb.ieltspractice.design.components.RowGroup
import com.soyeb.ieltspractice.design.components.SectionHeader
import com.soyeb.ieltspractice.design.components.SkeletonList
import com.soyeb.ieltspractice.design.components.StickyActionBar
import com.soyeb.ieltspractice.design.components.Tag
import com.soyeb.ieltspractice.design.components.rememberLoad
import com.soyeb.ieltspractice.design.components.readable
import com.soyeb.ieltspractice.feature.result.ResultHero
import com.soyeb.ieltspractice.nav.LrResult
import com.soyeb.ieltspractice.nav.LrRun
import com.soyeb.ieltspractice.nav.Nav
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

// The result of a submitted Listening or Reading attempt (web components/lr/Results.tsx): band and raw score, where marks went, every answer,
// and the passage or transcript with the place of each answer marked.

@Composable
fun LrResultScreen(route: LrResult, nav: Nav) {
    val graph = LocalGraph.current
    val load = rememberLoad(route.attemptId) { graph.api.lrAttempt(route.attemptId) }
    val flags by produceState(emptySet<Int>(), route.attemptId) { value = parseFlags(graph.prefs.getString("flags:${route.attemptId}")) }
    when (val s = load.state) {
        Load.Loading -> PushedScreen("Result", nav::back) { pad -> SkeletonList(Modifier.padding(pad)) }
        is Load.Failed -> PushedScreen("Result", nav::back) { pad -> ErrorState(s.message, load.reload, Modifier.padding(pad).verticalScroll(rememberScrollState())) }
        is Load.Ready -> {
            val a = s.value
            if (!a.submitted || a.marks == null) {
                LaunchedEffect(a.id) { nav.replace(LrRun(a.id)) }
                PushedScreen("Result", nav::back) { pad -> SkeletonList(Modifier.padding(pad)) }
            } else ResultBody(a, flags, nav)
        }
    }
}

@Composable
private fun meterColor(right: Int, total: Int) = with(NB.colors) {
    val r = if (total > 0) right.toDouble() / total else 0.0
    if (r >= 0.75) good else if (r >= 0.5) warn else bad
}

/** "B. Heading text" for a choice, the raw answer otherwise; "No answer" when blank. */
private fun shown(f: FlatQ, raw: String): String {
    if (raw.isBlank()) return "No answer"
    if (f.group.type == "tfng" || f.group.type == "ynng") return trioLabel(raw.uppercase())
    val text = (f.q.options ?: f.group.options)?.firstOrNull { it.key.equals(raw, true) }?.text?.takeIf { it.isNotBlank() }
    return if (text != null) "$raw. $text" else raw
}

@Composable
private fun ResultBody(a: LrAttempt, flags: Set<Int>, nav: Nav) {
    val graph = LocalGraph.current
    val api = graph.api
    val demo = LocalDemo.current
    val dv = demo?.tab.orEmpty()
    val scope = rememberCoroutineScope()
    val snack = LocalSnack.current
    val me by api.me.collectAsState()
    val target = me?.settings?.targetBand ?: 7.0
    val test = a.test
    val listening = test.listening
    val skill = if (listening) Skill.Listening else Skill.Reading
    val noun = if (listening) "Part" else "Passage"
    val marks = remember(a) { a.marks.orEmpty().associateBy { it.n } }
    val flat = remember(test) { test.flat() }
    val wrong = remember(a) { flat.filter { marks[it.n]?.correct != true }.map { it.n } }
    val firstWrong = wrong.firstOrNull()

    var tab by rememberSaveable { mutableIntStateOf(when (dv) { "Answers", "Detail" -> 1; "Passage", "Transcript" -> 2; else -> 0 }) }
    var filter by rememberSaveable { mutableIntStateOf(if (dv == "Answers" || dv == "Detail") 1 else 0) }
    var detail by remember { mutableStateOf<Int?>(if (dv == "Detail") firstWrong else null) }
    var focusQ by remember { mutableStateOf<Int?>(if (dv == "Passage" || dv == "Transcript") firstWrong else null) }
    var partIdx by rememberSaveable { mutableIntStateOf(focusQ?.let { n -> test.sections.indexOfFirst { s -> s.groups.any { n in it.from..it.to } }.coerceAtLeast(0) } ?: 0) }
    var cue by remember { mutableStateOf<AudioCue?>(null) }
    var cueId by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }

    fun retake() {
        if (busy || a.testId.isBlank()) return
        busy = true
        scope.launch {
            try {
                val r = api.lrStart(a.testId, a.mode, a.parts)
                nav.replace(LrRun(r.id))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                snack.showSnackbar(e.message?.takeIf { it.isNotBlank() } ?: "Couldn't start a new attempt. Try again.")
            } finally { busy = false }
        }
    }
    fun showInText(n: Int) {
        val f = flat.firstOrNull { it.n == n } ?: return
        partIdx = test.sections.indexOfFirst { it.part == f.part }.coerceAtLeast(0)
        focusQ = n
        tab = 2
        detail = null
    }
    fun playMoment(n: Int) {
        val f = flat.firstOrNull { it.n == n } ?: return
        val s = test.sections.firstOrNull { it.part == f.part } ?: return
        val w = audioWindow(s.timingRows, f.q) ?: return
        partIdx = test.sections.indexOf(s)
        cue = AudioCue(w.from, w.to, ++cueId)
        focusQ = n
        tab = 2
        detail = null
    }

    PushedScreen(
        title = test.title.removePrefix("Original practice: "),
        onBack = nav::back,
        bottomBar = {
            StickyActionBar {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NbButton("Retake", ::retake, modifier = Modifier.weight(1f), size = BtnSize.Large, loading = busy, kind = if (wrong.isEmpty()) BtnKind.Primary else BtnKind.Secondary)
                    if (wrong.isNotEmpty()) NbButton("Review mistakes", { filter = 1; tab = 1 }, modifier = Modifier.weight(1f), size = BtnSize.Large, haptic = true)
                }
            }
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(top = pad.calculateTopPadding())) {
            NbTabs(listOf("Overview", "Answers", if (listening) "Transcript" else "Passage"), tab, { tab = it })
            val bottom = PaddingValues(bottom = pad.calculateBottomPadding())
            when (tab) {
                0 -> OverviewTab(a, skill, noun, target, flat, marks, bottom) { detail = it }
                1 -> AnswersTab(a, flat, marks, flags, filter, { filter = it }, noun, bottom) { detail = it }
                else -> TextTab(a, section = test.sections[partIdx.coerceIn(0, test.sections.lastIndex)], partIdx = partIdx, onPart = { partIdx = it; focusQ = null }, noun = noun, focusQ = focusQ, marks = marks, cue = cue, pad = bottom)
            }
        }
    }

    detail?.let { n ->
        val f = flat.firstOrNull { it.n == n }
        if (f != null) DetailSheet(a, f, marks[n], listening, onShow = { showInText(n) }, onPlay = { playMoment(n) }, onDismiss = { detail = null })
    }
}

// ---------------------------------------------------------------- overview

@Composable
private fun OverviewTab(
    a: LrAttempt, skill: Skill, noun: String, target: Double, flat: List<FlatQ>, marks: Map<Int, com.soyeb.ieltspractice.data.model.LrMark>,
    pad: PaddingValues, onOpen: (Int) -> Unit,
) {
    val c = NB.colors
    val test = a.test
    val partial = a.parts != null
    val total = a.total ?: flat.size
    val raw = a.raw ?: marks.values.count { it.correct }
    val byPart = test.sections.map { s ->
        val qs = s.groups.flatMap { g -> g.questions.map { marks[it.n] } }
        Accuracy("$noun ${s.part}", qs.count { it?.correct == true }, qs.size)
    }
    val byType = accuracyBy(test, a.marks.orEmpty()) { typeLabel(it.group) }.sortedBy { if (it.total > 0) it.right.toDouble() / it.total else 0.0 }
    val causes = a.analysis?.causes.orEmpty().ifEmpty { lrCauses(a) }
    val wrongCount = flat.count { marks[it.n]?.correct != true }
    val st = a.stats
    LazyColumn(
        Modifier.fillMaxSize().readable(),
        contentPadding = PaddingValues(start = NB.space.gutter, end = NB.space.gutter, top = 16.dp, bottom = pad.calculateBottomPadding() + 24.dp),
        verticalArrangement = Arrangement.spacedBy(NB.space.lg),
    ) {
        item {
            ResultHero(
                skill = skill,
                overline = listOf(skill.label, if (test.variant == "general") "General Training" else "Academic", if (a.exam) "Exam" else "Practice").joinToString(" · "),
                title = test.title.removePrefix("Original practice: "),
                band = if (partial) null else a.band, target = target,
                note = if (partial) "${partsLabel(test.skill, a.parts)} only: $raw / $total correct. Take the full test for a band." else "$raw / $total correct",
            )
        }
        if (causes.isNotEmpty() && wrongCount > 0) {
            item { SectionHeader("What went wrong") }
            item {
                RowGroup {
                    causes.forEachIndexed { i, cause ->
                        if (i > 0) RowDivider()
                        Column(Modifier.fillMaxWidth().padding(horizontal = NB.space.lg, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(cause.label, style = NB.type.title3, color = c.ink, modifier = Modifier.weight(1f))
                                Text("${cause.questions.size}", style = NB.type.num, color = c.ink)
                            }
                            Meter(cause.questions.size.toDouble(), max = wrongCount.toDouble().coerceAtLeast(1.0), color = c.warn, height = 6.dp)
                            if (cause.message.isNotBlank()) Text(cause.message, style = NB.type.bodySmall, color = c.muted)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                cause.questions.forEach { n -> NbChip("Q $n", false, { onOpen(n) }) }
                            }
                        }
                    }
                }
            }
        }
        item { SectionHeader("By ${noun.lowercase()}") }
        item { AccuracyCard(byPart) }
        if (byType.isNotEmpty()) {
            item { SectionHeader("By question type") }
            item { AccuracyCard(byType) }
        }
        if (st != null && st.partS.values.sum() >= 5) {
            item { SectionHeader("How you used your time") }
            item {
                RowGroup {
                    test.sections.forEachIndexed { i, s ->
                        val sec = st.partS[s.part.toString()] ?: 0.0
                        if (i > 0) RowDivider()
                        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = NB.space.lg, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("$noun ${s.part}", style = NB.type.body, color = c.ink, modifier = Modifier.weight(1f))
                            Text(clock(sec.toInt()), style = NB.type.num, color = c.ink)
                        }
                    }
                    val changed = st.changes.values.sum()
                    if (changed > 0 || st.late.isNotEmpty()) {
                        RowDivider()
                        Column(Modifier.padding(horizontal = NB.space.lg, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            if (changed > 0) Text("You changed $changed ${if (changed == 1) "answer" else "answers"}.", style = NB.type.bodySmall, color = c.muted)
                            if (st.late.isNotEmpty()) Text(
                                if (a.exam && a.test.listening) "${st.late.size} answered after the recording ended." else "${st.late.size} answered in the last 5 minutes.",
                                style = NB.type.bodySmall, color = c.muted,
                            )
                        }
                    }
                }
            }
        }
        a.submittedAt?.let { s -> formatDate(s).takeIf { it.isNotEmpty() }?.let { d -> item { Text("Submitted $d", style = NB.type.caption, color = c.muted, modifier = Modifier.padding(horizontal = 4.dp)) } } }
    }
}

@Composable
private fun AccuracyCard(rows: List<Accuracy>) {
    val c = NB.colors
    RowGroup {
        rows.forEachIndexed { i, r ->
            if (i > 0) RowDivider()
            Column(
                Modifier.fillMaxWidth().padding(horizontal = NB.space.lg, vertical = 10.dp).semantics(mergeDescendants = true) { contentDescription = "${r.label}: ${r.right} of ${r.total} correct" },
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(r.label, style = NB.type.body, color = c.ink, modifier = Modifier.weight(1f))
                    Text("${r.right}/${r.total}", style = NB.type.num, color = c.ink)
                }
                Meter(r.right.toDouble(), max = r.total.toDouble().coerceAtLeast(1.0), color = meterColor(r.right, r.total), height = 6.dp)
            }
        }
    }
}

// ---------------------------------------------------------------- answers

@Composable
private fun AnswersTab(
    a: LrAttempt, flat: List<FlatQ>, marks: Map<Int, com.soyeb.ieltspractice.data.model.LrMark>, flags: Set<Int>, filter: Int, onFilter: (Int) -> Unit,
    noun: String, pad: PaddingValues, onOpen: (Int) -> Unit,
) {
    val c = NB.colors
    val wrongN = flat.count { marks[it.n]?.correct != true }
    val flaggedN = flat.count { it.n in flags }
    Column(Modifier.fillMaxSize()) {
        ChipRow(
            listOf("All ${flat.size}", "Wrong $wrongN", "Flagged $flaggedN"), setOf(filter), { onFilter(it) },
            Modifier.padding(vertical = 10.dp),
        )
        val rows = flat.filter { f ->
            when (filter) { 1 -> marks[f.n]?.correct != true; 2 -> f.n in flags; else -> true }
        }
        if (rows.isEmpty()) EmptyState(
            if (filter == 2) Icons.Rounded.Flag else Icons.Rounded.Check,
            if (filter == 2) "Nothing flagged" else "No wrong answers",
            if (filter == 2) "Questions you flag during a test show up here." else "Every answer was correct.",
        ) else LazyColumn(
            Modifier.fillMaxSize().readable(),
            contentPadding = PaddingValues(start = NB.space.gutter, end = NB.space.gutter, bottom = pad.calculateBottomPadding() + 24.dp),
            verticalArrangement = Arrangement.spacedBy(NB.space.lg),
        ) {
            rows.groupBy { it.part }.forEach { (part, list) ->
                item(key = "h$part") { SectionHeader("$noun $part") }
                item(key = "g$part") {
                    RowGroup {
                        list.forEachIndexed { i, f ->
                            if (i > 0) RowDivider()
                            AnswerRow(f, marks[f.n], f.n in flags) { onOpen(f.n) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AnswerRow(f: FlatQ, m: com.soyeb.ieltspractice.data.model.LrMark?, flagged: Boolean, onClick: () -> Unit) {
    val c = NB.colors
    val ok = m?.correct == true
    val given = m?.given.orEmpty()
    val correct = m?.answer.orEmpty().joinToString(" / ") { shown(f, it) }
    val desc = "Question ${f.n}, ${if (ok) "correct" else "wrong"}. Your answer: ${shown(f, given)}." + if (!ok) " Correct answer: $correct." else ""
    Surface(color = androidx.compose.ui.graphics.Color.Transparent, contentColor = c.ink, modifier = Modifier.clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onClick).semantics(mergeDescendants = true) { contentDescription = desc }) {
        Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = NB.space.lg, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(36.dp).background(if (ok) c.goodSoft else c.badSoft, CircleShape), contentAlignment = Alignment.Center) {
                Text("${f.n}", style = NB.type.num, color = if (ok) c.goodText else c.badText)
            }
            Column(Modifier.weight(1f).padding(start = 14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(if (ok) Icons.Rounded.Check else Icons.Rounded.Close, null, Modifier.size(16.dp), tint = if (ok) c.goodText else c.badText)
                    Text(shown(f, given), style = NB.type.title3, color = if (given.isBlank()) c.muted else c.ink, maxLines = 2)
                    if (flagged) Icon(Icons.Rounded.Flag, "Flagged", Modifier.size(14.dp), tint = c.warnText)
                }
                if (!ok) Text("Correct: $correct", style = NB.type.bodySmall, color = c.muted, maxLines = 2)
            }
        }
    }
}

/** The question in context: your answer and the right one, why, the wording match, and the place in the text. */
@Composable
private fun DetailSheet(a: LrAttempt, f: FlatQ, m: com.soyeb.ieltspractice.data.model.LrMark?, listening: Boolean, onShow: () -> Unit, onPlay: () -> Unit, onDismiss: () -> Unit) {
    val c = NB.colors
    val ok = m?.correct == true
    val section = a.test.sections.firstOrNull { it.part == f.part }
    val span = remember(f.n) { section?.let { evidenceSpan(sectionParagraphs(it), f.q, f.group.type == "gap") } }
    val canPlay = listening && section != null && audioWindow(section.timingRows, f.q) != null
    val entry = a.analysis?.gaps.orEmpty().firstOrNull { it.n == f.n }
    val review = f.q.review
    val given = m?.given.orEmpty()
    NbSheet(
        onDismiss, title = "Question ${f.n}", subtitle = "${if (listening) "Part" else "Passage"} ${f.part} · ${typeLabel(f.group)}",
        actions = {
            if (span != null) NbButton(if (listening) "Show in transcript" else "Show in passage", onShow, size = BtnSize.Large, fill = true)
            if (canPlay) NbButton("Play this moment", onPlay, kind = BtnKind.Secondary, fill = true, icon = Icons.Rounded.PlayArrow)
        },
    ) {
        Tag(if (ok) "Correct" else if (given.isBlank()) "No answer" else "Wrong", fg = if (ok) c.goodText else c.badText, bg = if (ok) c.goodSoft else c.badSoft, icon = if (ok) Icons.Rounded.Check else Icons.Rounded.Close)
        Text(questionPrompt(f.group, f.q), style = NB.type.body, color = c.ink)
        NbCard(tone = CardTone.Inset) {
            Text("Your answer", style = NB.type.caption, color = c.muted)
            Text(shown(f, given), style = NB.type.title3, color = if (ok) c.goodText else if (given.isBlank()) c.muted else c.badText)
            if (!ok) {
                Text("Correct answer", style = NB.type.caption, color = c.muted, modifier = Modifier.padding(top = 10.dp))
                Text(m?.answer.orEmpty().joinToString(" / ") { shown(f, it) }, style = NB.type.title3, color = c.goodText)
            }
        }
        if (!ok && entry != null) Banner("${entry.label}. ${entry.message}", Icons.Rounded.Flag, tone = Tone.Warn)
        review?.why?.takeIf { it.isNotBlank() }?.let {
            Text("Why", style = NB.type.title3, color = c.ink, modifier = Modifier.semantics { heading() })
            Text(it, style = NB.type.bodySmall, color = c.muted)
        }
        if (!ok) wrongNote(f.q, given)?.let {
            Text("Why your answer is wrong", style = NB.type.title3, color = c.ink, modifier = Modifier.semantics { heading() })
            Text(it, style = NB.type.bodySmall, color = c.muted)
        }
        review?.paraphrase?.takeIf { it.isNotEmpty() }?.let { pairs ->
            Text("Same idea, different words", style = NB.type.title3, color = c.ink, modifier = Modifier.semantics { heading() })
            pairs.forEach { p -> if (p.size >= 2) Text("“${p[0]}” → “${p[1]}”", style = NB.type.readingSmall, color = c.ink) }
        }
        if (section != null && span != null) {
            val paras = sectionParagraphs(section)
            val text = paras.getOrNull(span.p).orEmpty()
            val from = (span.s - 140).coerceAtLeast(0)
            val to = (span.e + 140).coerceAtMost(text.length)
            Text(if (listening) "In the recording" else "In the passage", style = NB.type.title3, color = c.ink, modifier = Modifier.semantics { heading() })
            if (text.isNotEmpty()) EvidenceText(
                (if (from > 0) "…" else "") + text.substring(from, to) + (if (to < text.length) "…" else ""),
                span.s - from + if (from > 0) 1 else 0, span.e - from + if (from > 0) 1 else 0, NB.type.readingSmall,
            )
        }
    }
}

// ---------------------------------------------------------------- passage / transcript

/** The first few words of a transcript line, to find it in the word timings. */
private fun lineStart(timings: List<Timing>, line: String): Double? {
    val w = lrWords(line).take(8).joinToString(" ")
    return if (w.isBlank()) null else locatePhrase(timings, w)?.start
}

@Composable
private fun TextTab(
    a: LrAttempt, section: LrSection, partIdx: Int, onPart: (Int) -> Unit, noun: String, focusQ: Int?,
    marks: Map<Int, com.soyeb.ieltspractice.data.model.LrMark>, cue: AudioCue?, pad: PaddingValues,
) {
    val c = NB.colors
    val listening = a.test.listening
    val state = rememberLazyListState()
    val paras = remember(section) { sectionParagraphs(section) }
    val span: TextSpan? = remember(section, focusQ) {
        focusQ?.let { n -> section.groups.flatMap { g -> g.questions.map { g to it } }.firstOrNull { it.second.n == n } }
            ?.let { (g, q) -> evidenceSpan(paras, q, g.type == "gap") }
    }
    // Scroll to the marked place: header items first, then one item per paragraph.
    val headerItems = if (listening) 2 else 1
    LaunchedEffect(span, section.part) {
        span?.let { state.scrollToItem(headerItems + if (listening) paras.take(it.p).count { l -> l.isNotBlank() } else it.p) }
    }
    val url = a.assets[section.audio.orEmpty()].orEmpty()
    val player = if (listening) rememberLrPlayer(url, null, cue) else null
    val timings = remember(section) { section.timingRows }
    val starts = remember(section) { if (listening) paras.map { lineStart(timings, it) } else emptyList() }
    val activeLine by remember(player, starts) {
        derivedStateOf {
            val pos = (player?.position ?: 0.0) + 0.25
            var at = -1
            starts.forEachIndexed { i, s -> if (s != null && s <= pos) at = i }
            if (player?.playing == true || (player?.position ?: 0.0) > 0.5) at else -1
        }
    }
    Column(Modifier.fillMaxSize()) {
        ChipRow(
            a.test.sections.map { "$noun ${it.part}" }, setOf(partIdx), { onPart(it) },
            Modifier.padding(vertical = 10.dp),
        )
        LazyColumn(
            Modifier.fillMaxSize().readable(), state = state,
            contentPadding = PaddingValues(start = NB.space.gutter, end = NB.space.gutter, bottom = pad.calculateBottomPadding() + 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (listening) {
                item(key = "player") { NbCard { if (player != null) PracticePlayer(player, "Part ${section.part}") } }
                item(key = "hint") {
                    Text(
                        if (section.transcript == null) "The transcript is not available for this part." else "Tap a line to play it. Pick a question in Answers to mark where its answer is.",
                        style = NB.type.bodySmall, color = c.muted,
                    )
                }
            } else item(key = "title") {
                Text(section.passage?.title.orEmpty(), style = NB.type.readingTitle, color = c.ink, modifier = Modifier.semantics { heading() })
            }
            if (listening) {
                paras.forEachIndexed { i, line ->
                    if (line.isBlank()) return@forEachIndexed
                    item(key = "p$i") {
                        val on = i == activeLine
                        val start = starts.getOrNull(i)
                        Surface(
                            onClick = { if (start != null && player != null) player.seek(start) }, enabled = start != null,
                            shape = NB.shape.tile, color = if (on) c.brandSoft else androidx.compose.ui.graphics.Color.Transparent, contentColor = c.ink,
                        ) {
                            Box(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp)) {
                                if (span != null && span.p == i) EvidenceText(line, span.s, span.e, NB.type.readingSmall)
                                else Text(line, style = NB.type.readingSmall, color = c.ink)
                            }
                        }
                    }
                }
            } else {
                section.passage?.paragraphs?.forEachIndexed { i, para ->
                    item(key = "p$i") {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (!para.label.isNullOrBlank()) Text(para.label, Modifier.width(22.dp).padding(top = 2.dp), style = NB.type.num, color = c.brandText)
                            if (span != null && span.p == i) EvidenceText(para.text, span.s, span.e, NB.type.reading, Modifier.weight(1f))
                            else Text(para.text, style = NB.type.reading, color = c.ink, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
            section.vocab.orEmpty().takeIf { it.isNotEmpty() }?.let { words ->
                item(key = "vocab-h") { SectionHeader("Key words", Modifier.padding(top = 12.dp)) }
                item(key = "vocab") {
                    RowGroup {
                        words.forEachIndexed { i, v ->
                            if (i > 0) RowDivider()
                            Column(Modifier.fillMaxWidth().padding(horizontal = NB.space.lg, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(v.word, style = NB.type.title3, color = c.ink)
                                if (v.meaning.isNotBlank()) Text(v.meaning, style = NB.type.bodySmall, color = c.muted)
                                v.example?.takeIf { it.isNotBlank() }?.let { Text("“$it”", style = NB.type.readingSmall, color = c.muted) }
                            }
                        }
                    }
                }
            }
        }
    }
}
