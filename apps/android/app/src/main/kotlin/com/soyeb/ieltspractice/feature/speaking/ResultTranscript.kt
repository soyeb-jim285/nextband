package com.soyeb.ieltspractice.feature.speaking

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import com.soyeb.ieltspractice.data.logic.ActiveSpot
import com.soyeb.ieltspractice.data.logic.DisfluencyKinds
import com.soyeb.ieltspractice.data.logic.MarkerType
import com.soyeb.ieltspractice.data.logic.Timeline
import com.soyeb.ieltspractice.data.logic.activeSpot
import com.soyeb.ieltspractice.data.logic.categoryLabel
import com.soyeb.ieltspractice.data.logic.formMatcher
import com.soyeb.ieltspractice.data.logic.isLongPause
import com.soyeb.ieltspractice.data.logic.pauseSec
import com.soyeb.ieltspractice.data.logic.questionHead
import com.soyeb.ieltspractice.data.logic.trimStretched
import com.soyeb.ieltspractice.data.logic.wordIndexAt
import com.soyeb.ieltspractice.data.model.AnalysisError
import com.soyeb.ieltspractice.data.model.AnalysisResult
import com.soyeb.ieltspractice.data.model.Pause
import com.soyeb.ieltspractice.data.model.Repeated
import com.soyeb.ieltspractice.data.model.Word
import com.soyeb.ieltspractice.design.NB
import com.soyeb.ieltspractice.design.Tone
import com.soyeb.ieltspractice.design.components.Banner
import com.soyeb.ieltspractice.design.components.ChipRow
import com.soyeb.ieltspractice.design.components.EmptyState
import com.soyeb.ieltspractice.design.components.NbCard
import com.soyeb.ieltspractice.design.components.SectionHeader
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

// The interactive transcript: filter chips with counts, tap a word to hear it, tap an underlined mistake for its explanation, typed
// tags for repeats / restarts, pause chips, "Also noted". Port of the old TranscriptPanel (iOS TranscriptView, web Transcript.tsx).

private enum class TrFilter(val label: String, val type: MarkerType?) {
    All("All", null), Grammar("Grammar", MarkerType.Grammar), Vocab("Vocabulary", MarkerType.Vocabulary),
    Pronunciation("Pronunciation", MarkerType.Pronunciation), Fluency("Fluency", MarkerType.Fluency), Other("Task & other", null), Pauses("Pauses", null),
}

/** Transcript filter for a mistake: its timeline type, else "other" (task, cohesion). */
private fun group(e: AnalysisError): TrFilter = when (MarkerType.of(e.category)) {
    MarkerType.Grammar -> TrFilter.Grammar
    MarkerType.Vocabulary -> TrFilter.Vocab
    MarkerType.Pronunciation -> TrFilter.Pronunciation
    MarkerType.Fluency -> TrFilter.Fluency
    null -> TrFilter.Other
}

/** A task or relevance note on a whole stretch (8+ words): drawn as a sentence tint, only when its filter is on. */
private fun isSentenceNote(e: AnalysisError) = group(e) == TrFilter.Other && e.end - e.start >= 7

/** A typed tag before the word where a repeat, restart, cut-off, held sound or unheard filler happens. */
private class TrMark(val short: String, val detail: String, val time: Double, val typed: Boolean = false)

private class TrToken(val id: Int, val word: Word) {
    val errors = mutableListOf<AnalysisError>()
    var unclearTier: Int? = null
    var filler = false
    var pauseAfter: Pause? = null
    val marks = mutableListOf<TrMark>()
}

private class TrSection(val n: Int?, val head: String, val rest: String, val range: IntRange)

/** Everything derived from the analysis, built once per result (the playback clock must not rebuild it). */
private class TrModel(r: AnalysisResult, timeline: Timeline) {
    val words: List<Word> = trimStretched(r.words.orEmpty())
    val tokens = words.mapIndexed { i, w -> TrToken(i, w) }
    val sections: List<TrSection>
    val unplaced: List<AnalysisError>
    val counts = mutableMapOf<TrFilter, Int>()
    val hasSentenceNotes: Boolean

    init {
        for (e in r.errors) if (e.start in tokens.indices) for (i in e.start..min(max(e.start, e.end), tokens.lastIndex)) tokens[i].errors += e
        r.metrics?.let { m ->
            for (p in m.pauses) {
                val i = tokens.indexOfFirst { abs(it.word.end - p.start) < 0.001 }.takeIf { it >= 0 } ?: tokens.indexOfLast { it.word.end <= p.start + 0.001 }
                if (i >= 0) tokens[i].pauseAfter = p
            }
            for (f in m.fillers) if (f.kind == "lexical") {
                val i = tokens.indexOfFirst { abs(it.word.start - f.time) < 0.001 }
                if (i < 0) continue
                tokens[i].filler = true
                if (' ' in f.word && i + 1 < tokens.size) tokens[i + 1].filler = true
            }
            for (u in m.unclear) if (u.wordIdx in tokens.indices) tokens[u.wordIdx].unclearTier = u.tier
            for (e in m.fluency?.events.orEmpty()) {
                if (e.kind == "filled" && "stt" in e.sources) continue // already marked on its word
                if (tokens.isEmpty()) break
                val i = tokens.indexOfFirst { it.word.end > e.start - 0.02 }.takeIf { it >= 0 } ?: tokens.lastIndex
                val filled = e.kind == "filled"
                val what = if (filled) "Filler (um, uh, er) heard in the audio here; speech recognition left it out" else DisfluencyKinds.all.firstOrNull { it.key == e.kind }?.what ?: "Disfluency"
                tokens[i].marks += TrMark(Timeline.disfluencyShort[e.kind] ?: e.kind, what, e.start, filled)
            }
        }
        unplaced = r.errors.filter { it.start !in tokens.indices }
        hasSentenceNotes = r.errors.any(::isSentenceNote)
        for (f in TrFilter.entries) f.type?.let { counts[f] = timeline.count(it) }
        counts[TrFilter.Other] = r.errors.count { group(it) == TrFilter.Other }
        counts[TrFilter.Pauses] = r.metrics?.pauses?.size ?: 0

        // One section per question, headed "Q1. ..."; without question boundaries, one headless block.
        val qs = r.questions.orEmpty().withIndex().filter { it.value.startWord in tokens.indices }.sortedBy { it.value.startWord }
        sections = if (qs.isEmpty()) {
            if (tokens.isEmpty()) emptyList() else listOf(TrSection(null, "", "", tokens.indices))
        } else qs.mapIndexed { j, q ->
            val from = if (j == 0) 0 else q.value.startWord
            val to = if (j + 1 < qs.size) qs[j + 1].value.startWord else tokens.size
            val (head, rest) = questionHead(q.value.text)
            TrSection(q.index + 1, head, rest, from until max(from, to))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TranscriptPanel(
    result: AnalysisResult, player: ResultPlayer, timeline: Timeline, focus: String?, lean: Repeated?,
    onSelect: (AnalysisError) -> Unit, onAlsoNoted: @Composable (AnalysisError) -> Unit,
) {
    val c = NB.colors
    val model = remember(result, timeline) { TrModel(result, timeline) }
    var filter by remember { mutableStateOf(TrFilter.All) }
    var markDetail by remember { mutableStateOf<String?>(null) }
    val leaned = remember(lean) { lean?.let(::formMatcher) }
    val colors = MarkerType.entries.associateWith { it.color() }
    // Index of the word playing now (-1 for none), and of the word whose pause is playing. Derived, so the clock only recomposes the words that change.
    val spot = remember(player, model) {
        derivedStateOf { if (player.isLoaded && player.isPlaying) activeSpot(model.words, player.currentTime) else ActiveSpot(-1, false) }
    }
    val now = remember(spot) { derivedStateOf { if (spot.value.pause) -1 else spot.value.word } }
    val nowPause = remember(spot) { derivedStateOf { if (spot.value.pause) spot.value.word else -1 } }
    val focusWord = focus?.let { id -> timeline.markers.firstOrNull { it.id == id } }?.let { max(wordIndexAt(model.words, it.t), 0) }

    if (model.tokens.isEmpty() && model.unplaced.isEmpty()) {
        EmptyState(Icons.Rounded.Info, "No transcript", "No transcript is available for this answer.")
        return
    }
    val filters = TrFilter.entries.filter { it == TrFilter.All || it == filter || (model.counts[it] ?: 0) > 0 }
    Column(verticalArrangement = Arrangement.spacedBy(NB.space.lg)) {
        ChipRow(
            filters.map { f -> if (f == TrFilter.All) f.label else "${f.label} ${model.counts[f] ?: 0}" },
            setOf(filters.indexOf(filter).coerceAtLeast(0)), { i -> filter = filters[i] }, contentPadding = PaddingValues(0.dp),
        )
        Legend(model, timeline, player.isLoaded, colors)
        markDetail?.let { Banner(it, Icons.Rounded.Info) }
        model.sections.forEach { s ->
            NbCard {
                if (s.n != null) {
                    Text("Q${s.n}. ${s.head}", style = NB.type.title3, color = c.ink)
                    if (s.rest.isNotEmpty()) Text(s.rest, style = NB.type.bodySmall, color = c.muted)
                    Spacer(Modifier.height(10.dp))
                }
                // Words flow left-aligned like prose; the spare space at the end of a line stays there (no justified gaps).
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp, androidx.compose.ui.Alignment.Start), verticalArrangement = Arrangement.spacedBy(6.dp), itemVerticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    for (i in s.range) WordView(
                        model.tokens[i], filter, now, nowPause, focusWord, colors, player, leaned, onSelect,
                    ) { d -> markDetail = if (markDetail == d) null else d }
                }
            }
        }
        if (model.unplaced.isNotEmpty()) {
            SectionHeader("Also noted")
            model.unplaced.forEach { err -> NbCard { onAlsoNoted(err) } }
        }
    }
}

// MARK: Legend

/** What the marks mean; only the marks that appear in this transcript. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Legend(model: TrModel, timeline: Timeline, loaded: Boolean, colors: Map<MarkerType, Color>) {
    val c = NB.colors
    FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp), itemVerticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        MarkerType.entries.filter { timeline.count(it) > 0 || it == MarkerType.Grammar || it == MarkerType.Vocabulary }.forEach { t ->
            LegendItem(t.label.lowercase()) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    MarkerGlyph(t, 9.dp)
                    Text("word", Modifier.underline(colors.getValue(t), t.underlineDashes()), style = NB.type.caption, color = c.ink)
                }
            }
        }
        LegendItem("long pause") { Text("pause 1.3s", Modifier.background(c.badSoft, CircleShape).padding(horizontal = 6.dp, vertical = 1.dp), style = NB.type.caption, color = c.badText) }
        if (model.hasSentenceNotes) LegendItem("task note") {
            Text("…", Modifier.background(c.warnSoft, RoundedCornerShape(4.dp)).padding(horizontal = 4.dp), style = NB.type.caption, color = c.ink)
        }
        if (loaded) LegendItem("playing now; tap a word to hear it") {
            Text("word", Modifier.background(c.brandSoft, RoundedCornerShape(3.dp)).padding(horizontal = 3.dp), style = NB.type.caption, color = c.ink)
        }
    }
}

@Composable
private fun LegendItem(label: String, sample: @Composable () -> Unit) {
    Row(
        Modifier.semantics(mergeDescendants = true) { contentDescription = "Legend: $label" },
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        sample()
        Text(label, style = NB.type.caption, color = NB.colors.muted)
    }
}

/** A dashed (or solid, when [dashes] is null) underline in [color]: the type reads without colour. */
private fun Modifier.underline(color: Color, dashes: FloatArray?): Modifier = drawBehind {
    val y = size.height - 2.dp.toPx()
    drawLine(
        color, Offset(0f, y), Offset(size.width, y), 1.5.dp.toPx(),
        pathEffect = dashes?.let { d -> PathEffect.dashPathEffect(FloatArray(d.size) { i -> d[i].dp.toPx() }) },
    )
}

// MARK: Words

@Composable
private fun WordView(
    t: TrToken, filter: TrFilter, now: State<Int>, nowPause: State<Int>, focusWord: Int?, colors: Map<MarkerType, Color>, player: ResultPlayer,
    leaned: ((String) -> Boolean)?, onSelect: (AnalysisError) -> Unit, onMark: (String) -> Unit,
) {
    val c = NB.colors
    // Sentence-wide notes would drown word-level marks, so they only show under their own filter.
    val err = t.errors.firstOrNull { !isSentenceNote(it) || filter == group(it) }
    val sentence = err?.let(::isSentenceNote) ?: false
    var type = err?.let { MarkerType.of(it.category) }
    if (err == null && t.unclearTier != null && !t.filler) type = MarkerType.Pronunciation
    if (err == null && t.filler) type = MarkerType.Fluency // fillers stay in the text with the fluency underline
    val underlineColor: Color? = if (sentence) null else (type?.let { colors.getValue(it) } ?: if (err != null) c.muted else null)
    val isNow by remember(t.id) { derivedStateOf { now.value == t.id } }
    val picked = t.id == focusWord
    val matches = when (filter) {
        TrFilter.All -> true
        TrFilter.Pauses -> false // words dim; the pause chips light up
        TrFilter.Other -> t.errors.any { group(it) == TrFilter.Other }
        TrFilter.Pronunciation -> t.unclearTier != null || t.errors.any { group(it) == TrFilter.Pronunciation }
        TrFilter.Fluency -> t.filler || t.marks.isNotEmpty() || t.errors.any { group(it) == TrFilter.Fluency }
        TrFilter.Grammar, TrFilter.Vocab -> t.errors.any { group(it) == filter }
    }
    val background = if (isNow) c.brandSoft else if (leaned?.invoke(t.word.w) == true) c.warn.copy(alpha = 0.3f) else if (sentence) c.warnSoft else Color.Transparent
    val shape = RoundedCornerShape(4.dp)
    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        t.marks.forEach { m ->
            if (m.typed) TypedFiller(m, filter, onMark, player, colors.getValue(MarkerType.Fluency)) else MarkChip(m, filter, onMark, player)
        }
        val label = if (err == null) t.word.w else "${t.word.w}, ${categoryLabel(err.category)} mistake"
        Text(
            t.word.w,
            Modifier.alpha(if (filter == TrFilter.All || matches) 1f else 0.35f)
                .background(background, shape)
                .let { if (picked) it.border(1.5.dp, c.ink, shape) else it }
                .let { if (underlineColor != null) it.underline(underlineColor, type?.underlineDashes()) else it }
                .clickable(role = Role.Button) { if (err != null) onSelect(err) else player.seek(max(0.0, t.word.start - 0.3)) }
                .semantics { contentDescription = label }
                .padding(horizontal = 2.dp),
            style = NB.type.readingSmall, color = c.ink,
        )
        t.pauseAfter?.let { p -> if (isLongPause(p) || filter == TrFilter.Pauses) PauseChip(p, filter, player, nowPause.value == t.id) }
    }
}

/** A filled pause the recogniser dropped, typed where it was heard: italic "um" with the fluency underline; tap to hear it. */
@Composable
private fun TypedFiller(m: TrMark, filter: TrFilter, onMark: (String) -> Unit, player: ResultPlayer, color: Color) {
    Text(
        "um",
        Modifier.alpha(if (filter == TrFilter.All || filter == TrFilter.Fluency) 1f else 0.35f)
            .underline(color, MarkerType.Fluency.underlineDashes())
            .clickable(role = Role.Button) { onMark(m.detail); player.seek(max(0.0, m.time - 0.3)) }
            .semantics { contentDescription = m.detail }
            .padding(horizontal = 2.dp),
        style = NB.type.readingSmall.copy(fontStyle = FontStyle.Italic), color = NB.colors.ink,
    )
}

@Composable
private fun MarkChip(m: TrMark, filter: TrFilter, onMark: (String) -> Unit, player: ResultPlayer) {
    val c = NB.colors
    Row(
        Modifier.alpha(if (filter == TrFilter.All || filter == TrFilter.Fluency) 1f else 0.35f)
            .clip(CircleShape).background(c.surface2)
            .clickable(role = Role.Button) { onMark(m.detail); player.seek(max(0.0, m.time - 0.3)) }
            .semantics { contentDescription = m.detail }
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        MarkerGlyph(MarkerType.Fluency, 7.dp)
        Text(m.short, style = NB.type.caption, color = c.muted)
    }
}

@Composable
private fun PauseChip(p: Pause, filter: TrFilter, player: ResultPlayer, active: Boolean) {
    val c = NB.colors
    val long = isLongPause(p)
    val color = if (long) c.badText else c.muted
    Text(
        "pause ${pauseSec(p)}s",
        Modifier.background(if (long) c.badSoft else c.surface2, CircleShape)
            .let { if (active) it.border(2.dp, c.brand, CircleShape) else if (filter == TrFilter.Pauses) it.border(1.dp, color, CircleShape) else it }
            .clickable(role = Role.Button) { player.seek(max(0.0, p.start - 0.3)) }
            .semantics { contentDescription = "${if (long) "Long pause" else "Pause"}, ${pauseSec(p)} seconds" }
            .padding(horizontal = 6.dp, vertical = 1.dp),
        style = NB.type.caption.copy(fontFeatureSettings = "tnum"), color = color,
    )
}
