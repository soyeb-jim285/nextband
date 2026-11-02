package com.soyeb.ieltspractice.feature.lr

import com.soyeb.ieltspractice.data.logic.LISTENING_REVIEW_SECONDS
import com.soyeb.ieltspractice.data.model.LrGroup
import com.soyeb.ieltspractice.data.model.LrQuestion
import com.soyeb.ieltspractice.data.net.AppJson
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer

// Pure logic of the Listening & Reading runner (no Compose): highlights and notes, display settings, the clocks.
// Ported from the old app's LrMarks.kt / LrSettings.kt / LrAudio.kt / LrParts.kt; the stored JSON shapes are unchanged so existing data survives.
// Highlights and notes are stored per attempt on this device only (prefs key `marks:<attemptId>`), never sent anywhere.

// ---------------------------------------------------------------- highlights and notes

const val NOTE_MAX = 500

/**
 * One highlight, with an optional note. [region] is `passage:<part>:<paragraph>`, `q:<n>:<field>` or `grp:<from>:<field>`;
 * [s]/[e] are character offsets into that region's plain text. [x] caches the marked text for the Notes list.
 */
@Serializable
data class TextMark(val id: String, val region: String, val p: Int = 0, val s: Int, val e: Int, val note: String? = null, val x: String? = null)

class Sel(val region: String, val s: Int, val e: Int)

fun parseMarks(json: String?): List<TextMark> =
    runCatching { AppJson.decodeFromString(ListSerializer(TextMark.serializer()), json.orEmpty()) }.getOrDefault(emptyList())

fun marksJson(marks: List<TextMark>): String = AppJson.encodeToString(ListSerializer(TextMark.serializer()), marks)

private fun TextMark.hits(s: Int, e: Int) = this.s < e && s < this.e
private fun paraOf(region: String) = region.split(':').let { if (it[0] == "passage") it.getOrNull(2)?.toIntOrNull() ?: 0 else 0 }

/** A plain highlight merges with the plain highlights it overlaps. Over a note it does nothing (the note is edited instead). */
fun withHighlight(marks: List<TextMark>, region: String, s: Int, e: Int, id: String): List<TextMark> {
    if (e <= s) return marks
    val here = marks.filter { it.region == region && it.hits(s, e) }
    if (here.any { it.note != null }) return marks
    val lo = minOf(s, here.minOfOrNull { it.s } ?: s)
    val hi = maxOf(e, here.maxOfOrNull { it.e } ?: e)
    return marks.filter { m -> here.none { it.id == m.id } } + TextMark(id, region, paraOf(region), lo, hi)
}

/** Add a note over a selection. Over an existing note it edits that note; plain highlights under the selection are absorbed. */
fun withNote(marks: List<TextMark>, region: String, s: Int, e: Int, note: String, id: String): List<TextMark> {
    val text = note.trim().take(NOTE_MAX).ifEmpty { null }
    val here = marks.filter { it.region == region && it.hits(s, e) }
    val own = here.firstOrNull { it.note != null }
    if (own != null) return withNoteText(marks, own.id, note)
    val lo = minOf(s, here.minOfOrNull { it.s } ?: s)
    val hi = maxOf(e, here.maxOfOrNull { it.e } ?: e)
    return marks.filter { m -> here.none { it.id == m.id } } + TextMark(id, region, paraOf(region), lo, hi, text)
}

/** Set (or, when blank, clear) the note of one mark; a mark whose note is cleared stays as a plain highlight. */
fun withNoteText(marks: List<TextMark>, id: String, note: String): List<TextMark> =
    marks.map { if (it.id == id) it.copy(note = note.trim().take(NOTE_MAX).ifEmpty { null }) else it }

fun withoutMark(marks: List<TextMark>, id: String): List<TextMark> = marks.filter { it.id != id }

/** "Passage 2, paragraph 3" / "Question 14". */
fun markWhere(m: TextMark): String {
    val p = m.region.split(':')
    return when (p[0]) {
        "passage" -> "Passage ${p.getOrNull(1)}, paragraph ${(p.getOrNull(2)?.toIntOrNull() ?: 0) + 1}"
        "q", "grp" -> "Question ${p.getOrNull(1)}"
        else -> "Test"
    }
}

/** The question a q:/grp: mark belongs to, or null (passage marks). */
fun markQuestion(m: TextMark): Int? = m.region.split(':').let { if (it[0] == "q" || it[0] == "grp") it.getOrNull(1)?.toIntOrNull() else null }

/** The part a passage mark belongs to, or null. */
fun markPart(m: TextMark): Int? = m.region.split(':').let { if (it[0] == "passage") it.getOrNull(1)?.toIntOrNull() else null }

/** Where [picked] sits in the visible [regions] (first match; a selection spanning lines gives one entry per line). */
fun locateSelection(regions: Map<String, String>, picked: String): List<Sel> {
    val clean = picked.replace("\u2020", "")
    fun find(piece: String): Sel? {
        val t = piece.trim()
        if (t.isEmpty()) return null
        for ((r, text) in regions) { val i = text.indexOf(t); if (i >= 0) return Sel(r, i, i + t.length) }
        return null
    }
    find(clean)?.let { return listOf(it) }
    return clean.split('\n').mapNotNull(::find).distinctBy { it.region + ":" + it.s }
}

/** Flagged question numbers are stored as "3,7,12". */
fun parseFlags(s: String?): Set<Int> = s.orEmpty().split(',').mapNotNull { it.trim().toIntOrNull() }.toSet()
fun flagsText(f: Set<Int>): String = f.sorted().joinToString(",")

// ---------------------------------------------------------------- display settings

/** Text size and colour scheme of the test content only; remembered per device (prefs key `settings`, same JSON as the web `lr:settings`). */
@Serializable
data class LrSettings(val size: String = "std", val scheme: String = "std")

private val SIZES = setOf("std", "lg", "xl")
private val SCHEMES = setOf("std", "bw", "cream", "yb")

/** Anything unreadable or unknown is the default. */
fun parseSettings(json: String?): LrSettings {
    val s = runCatching { AppJson.decodeFromString(LrSettings.serializer(), json.orEmpty()) }.getOrDefault(LrSettings())
    return LrSettings(if (s.size in SIZES) s.size else "std", if (s.scheme in SCHEMES) s.scheme else "std")
}

fun settingsJson(s: LrSettings): String = AppJson.encodeToString(LrSettings.serializer(), s)

fun LrSettings.scale(): Float = when (size) { "lg" -> 1.25f; "xl" -> 1.5f; else -> 1f }

// ---------------------------------------------------------------- clocks

/** Reading exam clock tone: 0 neutral, 1 warn under 10:00, 2 strong under 5:00. Only when the limit is above 10 minutes. */
fun readingTone(left: Int, limit: Int): Int = if (limit <= 600) 0 else if (left <= 300) 2 else if (left <= 600) 1 else 0

/** The first 10 seconds after each of the two marks: the clock announces itself. */
fun readingPulse(left: Int, limit: Int): Boolean = limit > 600 && (left in 291..300 || left in 591..600)

/**
 * Listening exam: seconds of checking time after the last recording. Follows the recording ("ten minutes to transfer your answers",
 * "one minute to check", often already silence in the file): [checkEndsAt] is seconds into the last part's audio. Null: 2 minutes.
 */
fun examReviewSeconds(durations: List<Double>?, checkEndsAt: Double?): Double {
    val c = checkEndsAt ?: return LISTENING_REVIEW_SECONDS.toDouble()
    if (durations.isNullOrEmpty()) return LISTENING_REVIEW_SECONDS.toDouble()
    val lastStart = durations.dropLast(1).sum()
    return maxOf(0.0, Math.round(lastStart + c - durations.sum()).toDouble())
}

enum class ExamPhase { Idle, Audio, Review }

// ---------------------------------------------------------------- questions

/** The questions of one part, labelled for the navigator. */
class NavPart(val part: Int, val label: String, val questions: List<Int>)

/** "True / False / Not Given" statements: the three keys of the group. */
fun trioKeys(type: String): List<String> = if (type == "tfng") listOf("TRUE", "FALSE", "NOT GIVEN") else listOf("YES", "NO", "NOT GIVEN")

fun trioLabel(k: String): String = if (k == "NOT GIVEN") "Not given" else k.lowercase().replaceFirstChar { it.uppercase() }

private val RANGE_PREFIX = Regex("""^Questions? [\d\s–\-and]+\.\s*""", RegexOption.IGNORE_CASE)

/** The group's instructions without the leading "Questions 1-6." (the screen prints its own heading). */
fun cleanInstructions(s: String): String = s.replace(RANGE_PREFIX, "")

/** A short line for the sheet peek and the answers list: the question's own text, else its row of the content, else the group title. */
fun questionPrompt(g: LrGroup, q: LrQuestion): String {
    q.text?.takeIf { it.isNotBlank() }?.let { return it.replace(Regex("""\{\{\d+(:\d+:\d+)?\}\}"""), "____").replace("**", "") }
    val line = g.content?.lines()?.firstOrNull { Regex("""\{\{${q.n}(:\d+:\d+)?\}\}""").containsMatchIn(it) }
    if (line != null) {
        val t = line.replace(Regex("""\{\{\d+(:\d+:\d+)?\}\}"""), "____").replace("**", "").replace("|", "  ").replace(Regex("""^\s*([-•*]|\d+[.)])\s+"""), "").replace(Regex("""\s+"""), " ").trim()
        if (t.isNotBlank()) return t
    }
    return g.title?.takeIf { it.isNotBlank() } ?: "Question ${q.n}"
}
