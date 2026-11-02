package com.soyeb.ieltspractice.data.logic

import com.soyeb.ieltspractice.data.model.AttemptListItem
import com.soyeb.ieltspractice.data.model.Mock
import com.soyeb.ieltspractice.data.model.MockCambridge
import com.soyeb.ieltspractice.data.model.MockOptionsQuota
import com.soyeb.ieltspractice.data.model.MockSection
import java.time.Duration
import java.time.Instant
import kotlin.random.Random

// Pure copy/state logic for the full mock exam (port of web lib/mock.ts).

enum class MockTone { Neutral, Accent, Good, Warn, Bad }

object MockFlow {
    val skills = listOf("listening", "reading", "writing", "speaking")
    val label = mapOf("listening" to "Listening", "reading" to "Reading", "writing" to "Writing", "speaking" to "Speaking")

    /** How long a section runs, for the transition copy. */
    val time = mapOf(
        "listening" to "about 30 minutes plus a 2-minute check",
        "reading" to "60 minutes",
        "writing" to "60 minutes for both tasks",
        "speaking" to "11 to 14 minutes",
    )

    val rules = listOf(
        "Order: Listening, Reading, Writing, then Speaking.",
        "Times: Listening about 30 minutes plus a 2-minute check, Reading 60, Writing 60 for both tasks, Speaking 11 to 14.",
        "No pausing inside a timed section, as in the real test. The clock runs only inside a section.",
        "If you leave, you resume with the time already used kept.",
        "Speaking can wait: the mock stays open for 7 days.",
    )

    fun name(skill: String) = label[skill] ?: skill.replaceFirstChar { it.uppercase() }

    /** What just finished and what comes next, or null when nothing is left. */
    fun transition(next: String?): Pair<String, String>? {
        if (next == null) return null
        val prev = skills.getOrNull(skills.indexOf(next) - 1)
        val nextTime = "${name(next)}, ${time[next]}"
        return when {
            prev == null -> "Ready for Listening?" to "Next: $nextTime. Nothing is running yet. The clock starts when you press Start."
            next == "speaking" -> "Writing finished. Choose how to do Speaking." to "Speaking has no countdown, so you can take it now or later this week."
            else -> "${name(prev)} finished." to "Next: $nextTime. Nothing is running. The clock starts when you press Start."
        }
    }

    /** The status text of a section row; Speaking has its own "not taken yet" wording. */
    fun stateLabel(skill: String, state: String): Pair<String, MockTone> = when (state) {
        "todo" -> (if (skill == "speaking") "Not taken yet" else "Not started") to MockTone.Neutral
        "in_progress" -> "In progress" to MockTone.Accent
        "submitted" -> "Submitted" to MockTone.Accent
        "marking" -> "Being marked" to MockTone.Accent
        "done" -> "Marked" to MockTone.Good
        "failed" -> "Marking failed, retry" to MockTone.Bad
        "skipped" -> "Skipped" to MockTone.Neutral
        else -> state to MockTone.Neutral
    }

    /** A submitted section has a result page to open. */
    fun isFinished(state: String) = state in setOf("submitted", "marking", "done", "failed")

    /** The small line under a section's name: time used, or the Speaking mode. */
    fun meta(s: MockSection): String? {
        val used = s.elapsedS
        if (s.state == "in_progress" && used != null && used > 0) {
            val limit = s.limitS?.takeIf { it > 0 }?.let { " of ${Math.round(it / 60.0)}" }.orEmpty()
            return "${(used / 60).toInt()} min used$limit"
        }
        if (s.skill == "speaking" && s.mode != null && s.state != "skipped") return if (s.mode == "live") "Live examiner" else "Recorded test"
        return null
    }

    /** The sentence under the overall band (or its absence). */
    fun overallNote(m: Mock, target: Double): String = when {
        m.overall != null -> "Mean of your four section bands, to the nearest half band. Target ${fmt(target)}."
        m.status == "closed" -> "This mock was finished without Speaking, so it has no overall band."
        m.marking -> "Overall appears when all four sections are marked. Marking takes about a minute."
        else -> "Overall appears when all four sections are marked."
    }

    /** "Continue your mock test" meta line for the entry card. */
    fun continueLine(m: Mock): String = "${m.next?.let { "${name(it)} next" } ?: "Open"}. Pick up where you stopped."
}

/** All attempt ids of a writing or speaking session in the order they were taken (the history list is the only place the app can find them); falls back to the section's first attempt. */
fun sessionAttemptIds(items: List<AttemptListItem>, sessionId: String?, fallback: String): List<String> {
    val ids = if (sessionId == null) emptyList() else items.filter { it.sessionId == sessionId }.sortedBy { it.createdAt }.map { it.id }
    return ids.ifEmpty { listOf(fallback) }
}

/** Choosing and counting for the mock start screen. */
object MockPick {
    /** The lowest-listed Cambridge test the person has not started a mock on, else the first one. */
    fun defaultRef(options: List<MockCambridge>): String? = (options.firstOrNull { !it.started } ?: options.firstOrNull())?.ref

    /** "Surprise me": any test not started yet, else any. */
    fun surprise(options: List<MockCambridge>, random: Random = Random.Default): String? =
        options.filter { !it.started }.ifEmpty { options }.randomOrNull(random)?.ref

    /** Mocks the person can still start: the smaller of the writing and speaking allowances. Null = no limit. */
    fun left(q: MockOptionsQuota?): Int? {
        if (q == null) return null
        return listOf(q.writing, q.speaking).filter { it.limit != null }.minOfOrNull { it.remaining ?: 0 }
    }

    fun blocked(q: MockOptionsQuota?): Boolean = q != null && (q.writing.blocked != null || q.speaking.blocked != null)

    /** "1 mock left today", "No mocks left this week", null when unlimited. */
    fun leftText(q: MockOptionsQuota?): String? {
        val n = left(q) ?: return null
        val window = listOf(q?.writing, q?.speaking).firstNotNullOfOrNull { it?.window }
        val when_ = if (window == "week") "this week" else if (window == "month") "this month" else "today"
        return if (n <= 0) "No mocks left $when_" else "$n ${if (n == 1) "mock" else "mocks"} left $when_"
    }
}

/** Result-page helpers. Bands always come from the server; these only choose and word things. */
fun MockFlow.weakest(m: Mock): MockSection? = m.sections.filter { it.band != null }.minByOrNull { it.band ?: 99.0 }

/** One plain next step for the weakest section. */
fun MockFlow.workOn(skill: String): String = when (skill) {
    "listening" -> "Do one timed Listening part a day, then check spelling and plurals before you read the key."
    "reading" -> "Practise one passage in 20 minutes: skim first, then find where each answer is stated."
    "writing" -> "Write a Task 2 essay in 40 minutes, with a 5-minute plan, then fix the top two errors."
    else -> "Record a Part 2 answer for the full two minutes, then listen back for pauses and repeated words."
}

/** Whole days until [expiresAt] (ISO), at least 0; null when it can't be read. */
fun MockFlow.daysLeft(expiresAt: String, now: Instant = Clocks.now()): Int? =
    runCatching { Duration.between(now, Instant.parse(expiresAt)).toHours().coerceAtLeast(0).let { ((it + 23) / 24).toInt() } }.getOrNull()

/** The card at the top of the hub. [detail] is the one-line fact under the title (what is next and for how long). */
data class HubCopy(val overline: String, val title: String, val detail: String?, val body: String)

fun MockFlow.hubCopy(m: Mock, target: Double): HubCopy {
    val next = m.next
    if (!m.open || next == null) return HubCopy(
        if (m.status == "closed") "Mock closed" else "Mock finished",
        if (m.overall != null) "Your overall band is ready" else "Your sections are in",
        null, overallNote(m, target),
    )
    val prev = skills.getOrNull(skills.indexOf(next) - 1)
    return when {
        next == "speaking" -> HubCopy("Last section", "Writing done", "Choose how to do Speaking", "Speaking has no countdown, so you can take it now or later this week.")
        m.section(next)?.state == "in_progress" -> HubCopy("In progress", "${name(next)} is under way", null, "You resume with the time you have used.")
        prev == null -> HubCopy("Ready when you are", "Start with ${name(next)}", "${name(next)} is first · ${time[next]}", "Nothing is running yet. The clock starts when you press Start.")
        else -> HubCopy("Take a breath", "${name(prev)} done", "${name(next)} is next · ${time[next]}", "Nothing is running. The clock starts when you press Start.")
    }
}

/** The hub's main button: "Start Reading", "Continue Writing", "Choose how to do Speaking"; null once nothing is left. */
fun MockFlow.startLabel(m: Mock): String? {
    val next = m.next ?: return null
    if (!m.open) return null
    if (next == "speaking") return "Choose how to do Speaking"
    return (if (m.section(next)?.state == "in_progress") "Continue " else "Start ") + name(next)
}

/** The line under a section in the stepper: its length while waiting, else its state and time used. */
fun MockFlow.rowSubtitle(s: MockSection): String {
    val label = stateLabel(s.skill, s.state).first
    return when (s.state) {
        "todo" -> time[s.skill]?.replaceFirstChar { it.uppercase() } ?: label
        "skipped" -> label
        else -> listOfNotNull(label, meta(s)).joinToString(" · ")
    }
}
