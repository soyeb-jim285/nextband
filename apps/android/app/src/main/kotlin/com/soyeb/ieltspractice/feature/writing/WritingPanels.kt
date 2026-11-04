package com.soyeb.ieltspractice.feature.writing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.soyeb.ieltspractice.data.logic.Band
import com.soyeb.ieltspractice.data.logic.Crit
import com.soyeb.ieltspractice.data.logic.DiffOp
import com.soyeb.ieltspractice.data.logic.categoryLabel
import com.soyeb.ieltspractice.data.logic.criterionLabel
import com.soyeb.ieltspractice.data.logic.errorTitle
import com.soyeb.ieltspractice.data.logic.fmt
import com.soyeb.ieltspractice.data.logic.formMatcher
import com.soyeb.ieltspractice.data.logic.mtld
import com.soyeb.ieltspractice.data.logic.repeatedWords
import com.soyeb.ieltspractice.data.logic.tokenize
import com.soyeb.ieltspractice.data.logic.wordDiff
import com.soyeb.ieltspractice.data.logic.ResScore
import com.soyeb.ieltspractice.data.model.AnalysisError
import com.soyeb.ieltspractice.data.model.AnalysisResult
import com.soyeb.ieltspractice.data.model.Comparison
import com.soyeb.ieltspractice.data.model.Repeated
import com.soyeb.ieltspractice.data.model.VocabUpgrade
import com.soyeb.ieltspractice.data.model.WritingStructure
import com.soyeb.ieltspractice.design.NB
import com.soyeb.ieltspractice.design.components.BtnKind
import com.soyeb.ieltspractice.design.components.BtnSize
import com.soyeb.ieltspractice.design.components.CardTone
import com.soyeb.ieltspractice.design.components.Meter
import com.soyeb.ieltspractice.design.components.NbButton
import com.soyeb.ieltspractice.design.components.NbCard
import com.soyeb.ieltspractice.design.components.NbChip
import com.soyeb.ieltspractice.design.components.NbListRow
import com.soyeb.ieltspractice.design.components.Overline
import com.soyeb.ieltspractice.design.components.RowDivider
import com.soyeb.ieltspractice.design.components.RowGroup
import com.soyeb.ieltspractice.design.components.SectionHeader
import com.soyeb.ieltspractice.design.components.Segmented
import com.soyeb.ieltspractice.design.components.StepRow
import com.soyeb.ieltspractice.design.components.StepState
import com.soyeb.ieltspractice.design.components.Stat
import com.soyeb.ieltspractice.design.components.Tag
import com.soyeb.ieltspractice.design.components.VDivider
import com.soyeb.ieltspractice.design.components.ChipRow
import com.soyeb.ieltspractice.feature.result.CriteriaCard
import com.soyeb.ieltspractice.feature.result.FixCard
import kotlin.math.max
import kotlin.math.roundToInt

// The five tabs of a writing result. Each is one block of the result list; the shell is in WritingResult.kt.

// MARK: Overview

@Composable
internal fun OverviewPanel(
    r: AnalysisResult, score: ResScore, target: Double, onCriterion: (String) -> Unit, onOpenAttempt: (String) -> Unit,
    onAddFixes: () -> Unit, fixesInDeck: Boolean, modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(NB.space.lg)) {
        CriteriaCard("writing", r.criteria, target, onOpen = onCriterion)
        val bands = Crit.order("writing").mapNotNull { r.criteria[it]?.band }
        if (bands.isNotEmpty()) {
            val avg = Band.round(bands.sum() / bands.size)
            Text(
                if (score.offTopic && avg > score.overall) "The average of these bands would be ${fmt(avg)}, capped at ${fmt(score.overall)} because the answer is off topic."
                else "Overall ${fmt(score.overall)} is the average of these ${bands.size} bands, rounded to the nearest half band. Tap a criterion for the evidence.",
                Modifier.padding(horizontal = 4.dp), style = NB.type.bodySmall, color = NB.colors.muted,
            )
        }
        r.comparison?.let { ComparisonCard(it, score.overall, it.parentAttemptId, onOpenAttempt) }
        if (r.topFixes.isNotEmpty()) {
            SectionHeader(if (r.topFixes.size == 1) "One thing to fix next" else "${r.topFixes.size} things to fix next", Modifier.padding(top = 12.dp))
            r.topFixes.forEachIndexed { i, f -> FixCard(i + 1, f) }
            NbButton(
                if (fixesInDeck) "Fixes are in your drills" else "Add top fixes to drills", onAddFixes,
                fill = true, kind = BtnKind.Secondary, enabled = !fixesInDeck,
            )
        }
    }
}

/** Previous overall to this overall with per-criterion changes, and a link to the previous try (web ComparisonStrip). */
@Composable
private fun ComparisonCard(c: Comparison, overall: Double, parentId: String, onOpen: (String) -> Unit) {
    val change = Math.round((overall - c.parentOverall) * 10) / 10.0
    NbCard(tone = CardTone.Inset, onClick = { onOpen(parentId) }) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Last try", style = NB.type.bodySmall, color = NB.colors.muted)
            Text(fmt(c.parentOverall), style = NB.type.num, color = NB.colors.ink)
            Text("to", style = NB.type.bodySmall, color = NB.colors.muted)
            Text(fmt(overall), style = NB.type.num, color = NB.colors.ink)
            Delta(change)
        }
        val shown = Crit.order("writing").filter { c.deltas[it] != null }
        if (shown.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                shown.forEach { k ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(criterionLabel(k), style = NB.type.caption, color = NB.colors.muted)
                        Delta(c.deltas[k] ?: 0.0)
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text("View previous attempt", style = NB.type.label, color = NB.colors.brandText)
    }
}

@Composable
private fun Delta(d: Double) {
    val up = d > 0
    val text = if (d == 0.0) "same" else (if (up) "+" else "-") + fmt(kotlin.math.abs(d))
    Text(text, style = NB.type.label.copy(fontFeatureSettings = "tnum"), color = if (d == 0.0) NB.colors.muted else if (up) NB.colors.goodText else NB.colors.badText)
}

// MARK: Essay

/** The essay with every mistake marked in place (spans are UTF-16 offsets, matching JS string indices). Tap a mark for the fix. */
@Composable
internal fun EssayPanel(
    r: AnalysisResult, text: String, lean: Repeated?, onClearLean: () -> Unit, onSelect: (AnalysisError) -> Unit, modifier: Modifier = Modifier,
) {
    val c = NB.colors
    var filter by remember { mutableStateOf<String?>(null) }
    fun group(x: AnalysisError) = x.category.substringBefore('.')
    val groups = r.errors.groupBy(::group).toList().sortedWith(compareByDescending<Pair<String, List<AnalysisError>>> { it.second.size }.thenBy { it.first })
    val visible = if (filter == null) r.errors else r.errors.filter { group(it) == filter }
    fun located(x: AnalysisError) = x.start >= 0 && x.end > x.start && x.end <= text.length
    val body = remember(text, visible, lean, c.isDark) {
        buildAnnotatedString {
            append(text)
            // Every use of the leaned-on word, drawn first so an error mark stays on top of it.
            if (lean != null) {
                val match = formMatcher(lean)
                for (m in Regex("[A-Za-z]+(?:'[A-Za-z]+)?").findAll(text)) if (match(m.value)) addStyle(SpanStyle(background = c.brandSoft, fontWeight = FontWeight.SemiBold), m.range.first, m.range.last + 1)
            }
            for (x in visible.filter(::located)) {
                val major = x.severity == "major"
                val style = SpanStyle(color = if (major) c.badText else c.warnText, background = if (major) c.badSoft else c.warnSoft, textDecoration = TextDecoration.Underline)
                addLink(LinkAnnotation.Clickable("err-${x.id}", TextLinkStyles(style)) { onSelect(x) }, x.start, x.end)
            }
        }
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(NB.space.lg)) {
        if (r.errors.isNotEmpty()) {
            val labels = listOf("All ${r.errors.size}") + groups.map { "${categoryLabel(it.first)} ${it.second.size}" }
            val selected = if (filter == null) 0 else groups.indexOfFirst { it.first == filter } + 1
            ChipRow(labels, setOf(selected), { i -> filter = if (i == 0 || filter == groups[i - 1].first) null else groups[i - 1].first }, contentPadding = PaddingValues(0.dp))
        }
        if (lean != null) NbChip("Highlighting \"${lean.word}\" · tap to clear", true, onClearLean, icon = Icons.Rounded.Close)
        NbCard { Text(body, style = NB.type.reading, color = c.ink) }
        if (r.errors.isNotEmpty()) {
            Text("Underlined text has a mistake: red for major, amber for minor. Tap it to see why.", Modifier.padding(horizontal = 4.dp), style = NB.type.caption, color = c.muted)
            SectionHeader(if (visible.size == 1) "1 mistake" else "${visible.size} mistakes")
            if (visible.isEmpty()) Text("No mistakes in this category.", style = NB.type.bodySmall, color = c.muted)
            else RowGroup {
                visible.forEachIndexed { i, x ->
                    if (i > 0) RowDivider()
                    NbListRow(
                        errorTitle(x.category),
                        subtitle = "${x.original} → ${x.correction}",
                        trailing = { SeverityTag(x.severity) },
                        chevron = true,
                        onClick = { onSelect(x) },
                    )
                }
            }
        }
    }
}

@Composable
internal fun SeverityTag(severity: String) {
    val major = severity == "major"
    Tag(severity.replaceFirstChar { it.uppercase() }, fg = if (major) NB.colors.badText else NB.colors.warnText, bg = if (major) NB.colors.badSoft else NB.colors.warnSoft)
}

// MARK: Structure

private val roles = mapOf(
    "intro" to "Introduction", "introduction" to "Introduction", "overview" to "Overview", "body" to "Body", "conclusion" to "Conclusion",
    "greeting" to "Greeting", "closing" to "Closing", "other" to "Other",
)

@Composable
internal fun StructurePanel(s: WritingStructure, modifier: Modifier = Modifier) {
    val c = NB.colors
    Column(modifier, verticalArrangement = Arrangement.spacedBy(NB.space.lg)) {
        if (s.overview != null || s.position != null || s.planFollowed != null) {
            SectionHeader("Checks")
            RowGroup {
                var first = true
                fun gap(): Boolean { val g = !first; first = false; return g }
                s.overview?.let { o ->
                    if (gap()) RowDivider()
                    CheckRow("Overview", listOf("Overview present" to o.present, "States the main trends" to o.mainTrends, "No detailed figures in it" to o.noData), o.note)
                }
                s.position?.let { p ->
                    if (gap()) RowDivider()
                    CheckRow("Position", listOf("Clear position" to p.clear, "Consistent throughout" to p.consistent), p.note)
                }
                s.planFollowed?.let { p ->
                    if (gap()) RowDivider()
                    CheckRow("Your plan", listOf("Essay followed the plan" to p.followed), p.note)
                }
            }
        }
        SectionHeader("Paragraph map")
        if (s.paragraphs.isEmpty()) {
            Text("No paragraphs were detected. Separate paragraphs with a blank line.", style = NB.type.bodySmall, color = c.muted)
        } else NbCard {
            s.paragraphs.forEachIndexed { i, p ->
                StepRow(
                    i + 1, (roles[p.role.lowercase()] ?: p.role.replaceFirstChar { it.uppercase() }) + if (p.ok) "" else " · needs work",
                    p.note.takeIf { it.isNotEmpty() }, if (p.ok) StepState.Done else StepState.Current, last = i == s.paragraphs.lastIndex,
                    accent = if (p.ok) c.brand else c.warn,
                ) {
                    if (p.topicSentence.isNotEmpty()) Text("“${p.topicSentence}”", Modifier.padding(top = 6.dp), style = NB.type.readingSmall, color = c.ink)
                }
            }
        }
    }
}

@Composable
private fun CheckRow(title: String, checks: List<Pair<String, Boolean>>, note: String) {
    val c = NB.colors
    Column(Modifier.fillMaxWidth().padding(NB.space.lg), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = NB.type.title3, color = c.ink)
        checks.forEach { (label, ok) ->
            Row(
                Modifier.semantics(mergeDescendants = true) { contentDescription = "$label: ${if (ok) "yes" else "no"}" },
                horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(if (ok) Icons.Rounded.Check else Icons.Rounded.Close, null, Modifier.size(18.dp), tint = if (ok) c.goodText else c.badText)
                Text(label, style = NB.type.bodySmall, color = if (ok) c.goodText else c.badText)
            }
        }
        if (note.isNotEmpty()) Text(note, style = NB.type.bodySmall, color = c.muted)
    }
}

// MARK: Language

@Composable
internal fun LanguagePanel(
    r: AnalysisResult, text: String, lean: Repeated?, onLean: (Repeated) -> Unit, modifier: Modifier = Modifier,
) {
    val c = NB.colors
    val tokens = remember(text) { tokenize(text) }
    val m = r.textMetrics
    val mix = remember(text) { sentenceMix(text) }
    val words = m?.words ?: wordCount(text)
    val sentences = m?.sentences ?: mix.total
    val paragraphs = m?.paragraphs ?: text.split(Regex("\\n\\s*\\n")).count { it.isNotBlank() }
    val avg = m?.avgSentenceLen ?: if (sentences > 0) words.toDouble() / sentences else 0.0
    val mt = m?.mtld ?: mtld(tokens)
    val ttr = m?.ttr ?: if (tokens.isNotEmpty()) tokens.toSet().size.toDouble() / tokens.size else 0.0
    val repeated = m?.repeated?.takeIf { it.isNotEmpty() } ?: remember(tokens) { repeatedWords(tokens) }
    val errorGroups = r.errors.groupBy { it.category }.toList().sortedWith(compareByDescending<Pair<String, List<AnalysisError>>> { it.second.size }.thenBy { it.first })
    val upgrades = r.vocabUpgrades.orEmpty().mapNotNull { v ->
        val better = v.better.filter { it.trim().lowercase() != v.original.trim().lowercase() }
        if (better.isEmpty()) null else VocabUpgrade(v.original, better, v.note)
    }
    val (rangeWord, rangeFg, rangeBg) = if (mt >= 80) Triple("Wide range", c.goodText, c.goodSoft) else if (mt >= 55) Triple("Adequate range", c.warnText, c.warnSoft) else Triple("Limited range", c.badText, c.badSoft)

    Column(modifier, verticalArrangement = Arrangement.spacedBy(NB.space.lg)) {
        SectionHeader("At a glance")
        NbCard {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Stat("$words", "Words", Modifier.weight(1f)); VDivider()
                Stat("$sentences", "Sentences", Modifier.weight(1f).padding(start = 16.dp)); VDivider()
                Stat("$paragraphs", "Paragraphs", Modifier.weight(1f).padding(start = 16.dp))
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Stat(fmt(avg), "Words per sentence", Modifier.weight(1f)); VDivider()
                Stat("${(ttr * 100).roundToInt()}%", "Unique words", Modifier.weight(1f).padding(start = 16.dp)); VDivider()
                Stat("${mt.roundToInt()}", "Range score", Modifier.weight(1f).padding(start = 16.dp))
            }
            Spacer(Modifier.height(12.dp))
            Tag(rangeWord, fg = rangeFg, bg = rangeBg)
            Spacer(Modifier.height(8.dp))
            Text(
                "Range score (MTLD) is how long you keep using new words before repeating yourself. Higher is more varied. These are rough guides, not band cut-offs.",
                style = NB.type.caption, color = c.muted,
            )
        }

        if (mix.total > 0) {
            SectionHeader("Sentence variety", Modifier.padding(top = 12.dp))
            NbCard {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    CountBar("Short, under 10 words", mix.short, mix.total)
                    CountBar("Medium, 10 to 20 words", mix.medium, mix.total)
                    CountBar("Long, over 20 words", mix.long, mix.total)
                }
                Spacer(Modifier.height(10.dp))
                Text("Band 7 and above usually mixes all three, with complex sentences that stay accurate.", style = NB.type.caption, color = c.muted)
            }
        }

        if (errorGroups.isNotEmpty()) {
            SectionHeader("Mistakes by type", Modifier.padding(top = 12.dp))
            NbCard {
                val top = max(3, errorGroups.first().second.size)
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    errorGroups.forEach { (cat, list) -> CountBar(categoryLabel(cat), list.size, top) }
                }
            }
        }

        val linkers = m?.linkers.orEmpty().sortedByDescending { it.count }
        if (linkers.isNotEmpty()) {
            SectionHeader("Linking words", Modifier.padding(top = 12.dp))
            NbCard {
                val top = max(3, linkers.first().count)
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    linkers.forEach { l -> CountBar(l.word, l.count, top, flag = if (l.overused && l.count >= 2) "Overused" else null) }
                }
                Spacer(Modifier.height(10.dp))
                Text("Examiners mark down mechanical linking. Swap some for referencing, like \"this trend\" or \"such policies\".", style = NB.type.caption, color = c.muted)
            }
        }

        if (repeated.isNotEmpty()) {
            SectionHeader("Repeated words", Modifier.padding(top = 12.dp))
            Text("Tap a word to highlight every use in your essay.", Modifier.padding(horizontal = 4.dp), style = NB.type.bodySmall, color = c.muted)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                repeated.forEach { rw -> NbChip("${rw.word} · ${rw.count}", lean?.word == rw.word, { onLean(rw) }) }
            }
        }

        if (upgrades.isNotEmpty()) {
            SectionHeader("Vocabulary upgrades", Modifier.padding(top = 12.dp))
            RowGroup {
                upgrades.forEachIndexed { i, v ->
                    if (i > 0) RowDivider()
                    NbListRow("${v.original} → ${v.better.joinToString(" / ")}", subtitle = v.note.takeIf { it.isNotEmpty() })
                }
            }
        }
    }
}

/** A label, its count and a small bar. [flag] adds a warn tag. */
@Composable
private fun CountBar(label: String, count: Int, max: Int, flag: String? = null) {
    Column(Modifier.semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(label, Modifier.weight(1f, fill = false), style = NB.type.bodySmall, color = NB.colors.ink)
            if (flag != null) Tag(flag, fg = NB.colors.warnText, bg = NB.colors.warnSoft)
            Spacer(Modifier.weight(1f))
            Text("$count", style = NB.type.num, color = NB.colors.ink)
        }
        Meter(count.toDouble(), max = max.toDouble().coerceAtLeast(1.0), color = if (flag != null) NB.colors.warn else NB.colors.brand, height = 6.dp)
    }
}

// MARK: Improve

@Composable
internal fun ImprovePanel(
    r: AnalysisResult, essay: String, parentText: String?, showDiff: Boolean, onShowDiff: (Boolean) -> Unit, modifier: Modifier = Modifier,
) {
    val c = NB.colors
    Column(modifier, verticalArrangement = Arrangement.spacedBy(NB.space.lg)) {
        if (r.rewrite.text.isEmpty()) {
            Text("There's no rewrite for this answer. Write a full-length answer to get a version one band higher to compare against.", style = NB.type.bodySmall, color = c.muted)
        } else {
            SectionHeader("One band higher")
            Text(
                r.rewrite.note.ifEmpty { "Study what changed and why. Don't memorise it: examiners recognise learned essays." },
                Modifier.padding(horizontal = 4.dp), style = NB.type.bodySmall, color = c.muted,
            )
            Segmented(listOf("Clean rewrite", "Show changes"), if (showDiff) 1 else 0, { onShowDiff(it == 1) })
            NbCard {
                if (showDiff) {
                    Text(diffText(essay, r.rewrite.text), style = NB.type.reading, color = c.ink)
                    Spacer(Modifier.height(12.dp))
                    Text("Struck through is cut from your essay. Highlighted is added.", style = NB.type.caption, color = c.muted)
                } else Text(r.rewrite.text, style = NB.type.reading, color = c.ink)
            }
        }
        if (parentText != null && essay.isNotBlank()) {
            SectionHeader("Since your last attempt", Modifier.padding(top = 12.dp))
            NbCard { Text(diffText(parentText, essay), style = NB.type.reading, color = c.ink) }
        }
    }
}

@Composable
private fun diffText(old: String, new: String): androidx.compose.ui.text.AnnotatedString {
    val c = NB.colors
    return remember(old, new, c.isDark) {
        buildAnnotatedString {
            wordDiff(old, new).forEach { run ->
                when (run.op) {
                    DiffOp.Same -> append(run.text + " ")
                    DiffOp.Removed -> withStyle(SpanStyle(color = c.badText, textDecoration = TextDecoration.LineThrough)) { append(run.text + " ") }
                    DiffOp.Added -> withStyle(SpanStyle(color = c.goodText, background = c.goodSoft, fontWeight = FontWeight.Medium)) { append(run.text + " ") }
                }
            }
        }
    }
}

/** Evidence quotes come with their own quote marks; show them in curly quotes once. */
internal fun cleanQuote(q: String): String = "“" + q.trim().trim('"', '“', '”') + "”"
