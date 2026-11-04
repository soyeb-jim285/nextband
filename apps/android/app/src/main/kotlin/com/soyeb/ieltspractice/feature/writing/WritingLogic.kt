package com.soyeb.ieltspractice.feature.writing

import com.soyeb.ieltspractice.data.logic.clock
import com.soyeb.ieltspractice.data.logic.minWords
import com.soyeb.ieltspractice.data.model.Prompt
import com.soyeb.ieltspractice.data.net.AppJson
import com.soyeb.ieltspractice.nav.WritingTask
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlin.math.ceil

// Pure writing-exam rules shared by the hub, the editor and the result (ports of web lib/writing.ts, WritingExam.tsx and the old
// Android WritingLogic.kt). No Compose in here so everything is covered by WritingLogicTest.

/** Below this many words a manual submit is refused (web SUBMIT_FLOOR); the server rates 20 words or fewer Band 1 anyway. */
const val SUBMIT_FLOOR = 21

/** One edit that inserts this many characters at once is a paste (typing, swipe typing and word suggestions add far fewer). */
const val PASTE_CHARS = 30

/** Core `countWords`: whitespace-separated tokens that contain a letter or a digit (so "75%" counts, a lone dash does not). */
fun wordCount(s: String): Int = s.split(Regex("\\s+")).count { t -> t.any { it.isLetterOrDigit() } }

fun plural(n: Int) = if (n == 1) "1 word" else "$n words"

/** Salutation Cambridge prints under a General Training letter; null for other prompts and imported ones whose body already has it (web lib/writing.ts). */
fun letterOpening(p: Prompt): String? =
    if (p.part != 1 || p.variant != "general" || p.body.contains("Begin your letter", ignoreCase = true)) null
    else if (p.type == "letter-formal") "Dear Sir or Madam," else "Dear ..............,"

/** WRITING_SECONDS: a full test shares one 60-minute clock, Task 1 gets 20 minutes, Task 2 gets 40. */
fun examSeconds(prompts: List<Prompt>) = if (prompts.size > 1) 3600 else if (prompts.firstOrNull()?.part == 1) 1200 else 2400

/** Seconds left on a wall-clock deadline (negative once overtime). Derived from timestamps so backgrounding can't drift it. */
fun secondsLeft(total: Int, startedAtMs: Long, nowMs: Long): Int = ceil(total - (nowMs - startedAtMs) / 1000.0).toInt()

enum class TimeEvent { Warn5, Warn1, TimeUp }

/** The notice to show when the clock crosses 5 minutes, 1 minute or zero (each fires once, as the clock passes it). */
fun timeEvent(old: Int, new: Int): TimeEvent? = when {
    old > 300 && new <= 300 && new > 60 -> TimeEvent.Warn5
    old > 60 && new <= 60 && new > 0 -> TimeEvent.Warn1
    old > 0 && new <= 0 -> TimeEvent.TimeUp
    else -> null
}

/** The word meter: [reached] once the minimum is met, [near] from 90 %. Never an error state: an empty page is not a mistake. */
data class WordBar(val reached: Boolean, val near: Boolean, val hint: String, val progress: Float)

fun wordBar(words: Int, need: Int): WordBar {
    val reached = words >= need
    val hint = if (reached) "$need-word minimum reached" else if (words > 0) "${need - words} more to reach $need" else "Minimum $need words"
    return WordBar(reached, !reached && words >= need * 0.9, hint, minOf(words, need).toFloat() / need)
}

/** One task's state for the submit confirmation. */
data class TaskCount(val part: Int, val words: Int)

fun tooShort(tasks: List<TaskCount>) = tasks.any { it.words < SUBMIT_FLOOR }

fun underMinimum(tasks: List<TaskCount>) = tasks.firstOrNull { it.words < minWords(it.part) }

/** The line under the word counts in the submit confirmation (null when everything is fine). */
fun submitNote(tasks: List<TaskCount>, left: Int): String? {
    if (tooShort(tasks)) return "Write at least a paragraph${if (tasks.size > 1) " for each task" else ""} before submitting."
    val u = underMinimum(tasks) ?: return null
    val marks = if (u.part == 1) "Task Achievement" else "Task Response"
    val more = if (left > 0) ". You have ${clock(left)} left to add more" else ""
    return "Under ${minWords(u.part)} words costs $marks marks$more."
}

/** How many characters [new] inserted into [old] in one edit (common prefix and suffix removed; a replacement counts the new text). */
fun insertedLength(old: String, new: String): Int {
    val max = minOf(old.length, new.length)
    var p = 0
    while (p < max && old[p] == new[p]) p++
    var s = 0
    while (s < max - p && old[old.length - 1 - s] == new[new.length - 1 - s]) s++
    return new.length - p - s
}

fun looksPasted(old: String, new: String) = insertedLength(old, new) >= PASTE_CHARS

// MARK: Drafts

/** Essay and plan live under one key per prompt as JSON (`draft:{promptId}`, same shape as web and the old app). */
@Serializable data class Draft(val text: String = "", val plan: String = "")

fun encodeDraft(d: Draft): String = AppJson.encodeToString(Draft.serializer(), d)

/** An older plain-text draft is read as the essay. */
fun decodeDraft(raw: String?): Draft {
    if (raw == null) return Draft()
    return runCatching { AppJson.decodeFromString(Draft.serializer(), raw) }.getOrElse { Draft(text = raw) }
}

/** What the hub needs to list and resume a draft without loading the prompt. */
@Serializable data class DraftMeta(
    val promptId: String,
    val part: Int,
    val title: String,
    val variant: String? = null,
    val words: Int = 0,
    val updatedAt: Long = 0,
    /** A full test's other task, so the hub reopens both together. */
    val pairId: String? = null,
)

/** The route that resumes [d]: its full test (both tasks, Task 1 first) when it was written in one, else the single task. */
fun resumeRoute(d: DraftMeta): WritingTask {
    val pair = d.pairId ?: return WritingTask(if (d.part == 1) "task1" else "task2", d.variant, d.promptId)
    return if (d.part == 1) WritingTask("full", d.variant, d.promptId, pair) else WritingTask("full", d.variant, pair, d.promptId)
}

fun encodeIndex(items: List<DraftMeta>): String = AppJson.encodeToString(ListSerializer(DraftMeta.serializer()), items)

fun decodeIndex(raw: String?): List<DraftMeta> =
    if (raw == null) emptyList() else runCatching { AppJson.decodeFromString(ListSerializer(DraftMeta.serializer()), raw) }.getOrDefault(emptyList())

/** "just now", "12 min ago", "3 h ago", "2 d ago". */
fun ago(thenMs: Long, nowMs: Long): String {
    val m = ((nowMs - thenMs) / 60_000).coerceAtLeast(0)
    return when {
        m < 1 -> "just now"
        m < 60 -> "$m min ago"
        m < 24 * 60 -> "${m / 60} h ago"
        else -> "${m / (24 * 60)} d ago"
    }
}

/** A running exam: the prompts and the wall-clock start, saved across configuration changes so a rotation never reshuffles the task. */
@Serializable data class ExamSession(val prompts: List<Prompt>, val startedAt: Long, val sessionId: String, val parentId: String? = null, val mockId: String? = null)

/** The task title unless the body already opens with it or the figure repeats it (seeded titles are the body's first sentence, cut with an ellipsis). */
fun showPromptTitle(p: Prompt, chartTitle: String?): Boolean {
    val stem = p.title.removeSuffix("…")
    return !p.body.startsWith(stem) && chartTitle != p.title
}

// MARK: Result helpers

/** "38 min" (rounded, at least 1) for a time in the editor; null when unknown. */
fun minutesText(ms: Int?): String? = ms?.takeIf { it > 0 }?.let { "${maxOf(1, Math.round(it / 60000.0).toInt())} min" }

/** Sentences by length: short (under 10 words), medium (10 to 20) and long (over 20), for the variety bars. */
data class SentenceMix(val short: Int, val medium: Int, val long: Int) {
    val total get() = short + medium + long
}

fun sentenceMix(text: String): SentenceMix {
    var s = 0; var m = 0; var l = 0
    for (sentence in text.split(Regex("(?<=[.!?])\\s+|\\n+"))) {
        val n = wordCount(sentence)
        if (n == 0) continue
        if (n < 10) s++ else if (n <= 20) m++ else l++
    }
    return SentenceMix(s, m, l)
}
