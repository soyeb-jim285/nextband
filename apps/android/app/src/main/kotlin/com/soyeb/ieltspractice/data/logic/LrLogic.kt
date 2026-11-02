package com.soyeb.ieltspractice.data.logic

import com.soyeb.ieltspractice.data.model.*

/** Reading is 60 minutes; the listening exam: the checking time its recording announces (LrTest.checkEndsAt), else 2 minutes after the last recording. */
const val READING_SECONDS = 3600
const val LISTENING_REVIEW_SECONDS = 120

/** Exam reading clock: 60 minutes for the whole test, 20 per passage when taking only some. */
fun readingSeconds(parts: List<Int>?) = if (!parts.isNullOrEmpty()) 1200 * parts.size else READING_SECONDS

// ponytail: every test has 4 listening parts / 3 reading passages; the server rejects a part a test lacks
fun lrParts(skill: String) = if (skill == "listening") listOf(1, 2, 3, 4) else listOf(1, 2, 3)

/** "Part 2", "Passages 1, 3", or "Full test" when parts is null. */
fun partsLabel(skill: String, parts: List<Int>?): String =
    if (parts.isNullOrEmpty()) "Full test"
    else "${if (skill == "listening") "Part" else "Passage"}${if (parts.size > 1) "s" else ""} ${parts.joinToString(", ")}"

/** A question in test order with the part and group it belongs to. */
data class FlatQ(val n: Int, val part: Int, val group: LrGroup, val q: LrQuestion)

fun LrTest.flat(): List<FlatQ> = sections.flatMap { s -> s.groups.flatMap { g -> g.questions.map { FlatQ(it.n, s.part, g, it) } } }

fun Map<String, String>.isAnswered(n: Int) = this[n.toString()]?.isNotBlank() == true
fun Map<String, String>.answeredCount(t: LrTest) = t.flat().count { isAnswered(it.n) }

/** Returns [this] with question [n] set to [v] (blank removes it). */
fun Map<String, String>.withAnswer(n: Int, v: String): Map<String, String> = if (v.isBlank()) this - n.toString() else this + (n.toString() to v)

/** Names a group's question type for the accuracy table ("Matching headings", "Table completion"). */
fun typeLabel(g: LrGroup): String = when (g.type) {
    "tfng" -> "True / False / Not Given"
    "ynng" -> "Yes / No / Not Given"
    "mcq" -> "Multiple choice"
    "mcq-multi" -> "Multiple choice (more than one)"
    "match" -> when {
        g.image != null -> "Labelling a map or diagram"
        g.options?.any { Regex("^[ivx]+$", RegexOption.IGNORE_CASE).matches(it.key) } == true -> "Matching headings"
        else -> "Matching"
    }
    else -> {
        val t = "${g.title.orEmpty()} ${g.instructions}".lowercase()
        when {
            g.image != null -> "Labelling a map or diagram"
            g.options != null -> "Summary with a word box"
            "table" in t -> "Table completion"
            "flow" in t -> "Flow-chart completion"
            "summary" in t -> "Summary completion"
            "form" in t -> "Form completion"
            "notes" in t -> "Note completion"
            "sentence" in t -> "Sentence completion"
            else -> "Completion"
        }
    }
}

data class Accuracy(val label: String, val right: Int, val total: Int)

/** Accuracy per [key] (part, question type) from the marks of a submitted attempt, in first-seen order. */
fun accuracyBy(t: LrTest, marks: List<LrMark>, key: (FlatQ) -> String): List<Accuracy> {
    val ok = marks.associate { it.n to it.correct }
    val out = linkedMapOf<String, IntArray>()
    for (f in t.flat()) {
        val e = out.getOrPut(key(f)) { IntArray(2) }
        e[1]++
        if (ok[f.n] == true) e[0]++
    }
    return out.map { (k, v) -> Accuracy(k, v[0], v[1]) }
}

/** The letters picked in a choose-N group, in slot order. Picks are stored one per question slot of the group. */
fun multiPicks(g: LrGroup, r: Map<String, String>): List<String> = g.questions.mapNotNull { r[it.n.toString()]?.takeIf { v -> v.isNotBlank() } }

fun setMultiPicks(g: LrGroup, r: Map<String, String>, picks: List<String>): Map<String, String> {
    val next = r.toMutableMap()
    g.questions.forEachIndexed { i, q -> picks.getOrNull(i)?.let { next[q.n.toString()] = it } ?: next.remove(q.n.toString()) }
    return next
}

// ---- gap content markup: a markdown subset (tables, lists, paragraphs, **bold**) with {{n}} placeholders ----

sealed interface Inline {
    data class Text(val text: String) : Inline
    data class Bold(val text: String) : Inline
    /** [part] of [of] blanks of one question ("from {{7}} to {{7}}"); of = 1 is a plain gap. */
    data class Gap(val n: Int, val part: Int = 0, val of: Int = 1) : Inline
}
sealed interface Block {
    data class P(val inline: List<Inline>) : Block
    data class Items(val ordered: Boolean, val items: List<List<Inline>>) : Block
    data class Table(val head: List<List<Inline>>, val rows: List<List<List<Inline>>>) : Block
}

private val INLINE = Regex("""(\{\{\d+(?::\d+:\d+)?\}\}|\*\*[^*]+\*\*)""")
private val GAP = Regex("""^\{\{(\d+)(?::(\d+):(\d+))?\}\}$""")
private val PLAIN_GAP = Regex("""\{\{(\d+)\}\}""")
private val LIST_ITEM = Regex("""^([-•*]|\d+[.)])\s""")
private val RULE = Regex("""^:?-{2,}:?$""")

/** One question with two blanks ("from {{7}} to {{7}}") becomes "{{7:0:2}} ... {{7:1:2}}", so each blank gets its own field (web numberGapParts). */
fun numberGapParts(md: String): String {
    val total = PLAIN_GAP.findAll(md).groupingBy { it.groupValues[1] }.eachCount()
    val seen = HashMap<String, Int>()
    return PLAIN_GAP.replace(md) { m ->
        val n = m.groupValues[1]
        val of = total.getValue(n)
        if (of > 1) { val k = seen.getOrDefault(n, 0); seen[n] = k + 1; "{{$n:$k:$of}}" } else m.value
    }
}

/** The answer to a multi-blank question is stored as "part / part"; marking folds "/" to a space. */
fun gapPart(v: String, part: Int): String = v.split(" / ").getOrElse(part) { "" }
fun setGapPart(v: String, part: Int, of: Int, text: String): String {
    val parts = List(of) { if (it == part) text else gapPart(v, it) }
    return if (parts.any { it.isNotBlank() }) parts.joinToString(" / ") else ""
}

fun parseInline(s: String): List<Inline> {
    val out = mutableListOf<Inline>()
    var at = 0
    for (m in INLINE.findAll(s)) {
        if (m.range.first > at) out += Inline.Text(s.substring(at, m.range.first))
        val g = GAP.matchEntire(m.value)
        out += if (g != null) Inline.Gap(g.groupValues[1].toInt(), g.groupValues[2].toIntOrNull() ?: 0, g.groupValues[3].toIntOrNull() ?: 1) else Inline.Bold(m.value.removeSurrounding("**"))
        at = m.range.last + 1
    }
    if (at < s.length) out += Inline.Text(s.substring(at))
    return out
}

fun parseContent(md: String): List<Block> {
    val lines = numberGapParts(md).split('\n')
    val blocks = mutableListOf<Block>()
    var i = 0
    while (i < lines.size) {
        val line = lines[i].trim()
        when {
            line.isEmpty() -> i++
            line.startsWith("|") -> {
                val rows = mutableListOf<List<String>>()
                while (i < lines.size && lines[i].trim().startsWith("|")) {
                    val cells = lines[i].trim().removePrefix("|").removeSuffix("|").split("|").map { it.trim() }
                    if (!cells.all { RULE.matches(it) }) rows += cells
                    i++
                }
                val head = rows.firstOrNull().orEmpty()
                blocks += Block.Table(head.map(::parseInline), rows.drop(1).map { r -> r.map(::parseInline) })
            }
            LIST_ITEM.containsMatchIn(line) -> {
                val ordered = line[0].isDigit()
                val items = mutableListOf<List<Inline>>()
                while (i < lines.size && LIST_ITEM.containsMatchIn(lines[i].trim())) {
                    items += parseInline(lines[i].trim().replace(Regex("""^([-•*]|\d+[.)])\s+"""), ""))
                    i++
                }
                blocks += Block.Items(ordered, items)
            }
            else -> { blocks += Block.P(parseInline(line)); i++ }
        }
    }
    return blocks
}

/** "C17 T2" gives book 17, test 2. */
fun parseRef(ref: String): Pair<Int, Int>? =
    Regex("""^C(\d+)\s*T(\d+)""", RegexOption.IGNORE_CASE).find(ref)?.let { it.groupValues[1].toInt() to it.groupValues[2].toInt() }

/** Answers that had the right idea but lost the mark (spelling, wording, the box); older attempts without causes count spelling/plural gaps. */
fun LrAnalysis?.slipCount(): Int =
    this?.causes?.firstOrNull { it.family == "slip" }?.questions?.size ?: this?.gaps.orEmpty().count { it.kind == "spelling" || it.kind == "plural" }

/** "Across your listening tests, … costs you the most" once at least 10 marks have been lost to a known cause, else null (web Results trend). */
fun causeTrend(skill: String, causes: List<LrCauseStat>): String? {
    val mine = causes.filter { it.skill == skill }
    val lost = mine.sumOf { it.count }
    val top = mine.firstOrNull() ?: return null
    return if (lost >= 10) "Across your $skill tests, \"${top.label.lowercase()}\" costs you the most: ${top.count} of $lost lost marks." else null
}

class LrHubGroup(val key: String, val heading: String, val tests: List<LrTestItem>)

/** Cambridge tests grouped by book (newest first, tests in order); our own tests under one heading at the end. */
fun lrHubGroups(items: List<LrTestItem>): List<LrHubGroup> {
    val books = sortedMapOf<Int, MutableList<LrTestItem>>(compareByDescending<Int> { it })
    val own = mutableListOf<LrTestItem>()
    val other = mutableListOf<LrTestItem>()
    for (t in items) {
        val r = if (t.source == "cambridge") parseRef(t.ref) else null
        when {
            r != null -> books.getOrPut(r.first) { mutableListOf() } += t
            t.source == "cambridge" -> other += t
            else -> own += t
        }
    }
    val out = books.map { (book, ts) -> LrHubGroup("c$book", "Cambridge IELTS $book", ts.sortedBy { parseRef(it.ref)!!.second }) }.toMutableList()
    if (other.isNotEmpty()) out += LrHubGroup("c", "Cambridge IELTS", other)
    if (own.isNotEmpty()) out += LrHubGroup("own", "Original practice tests", own)
    return out
}
