package com.soyeb.ieltspractice.data.logic

import com.soyeb.ieltspractice.data.model.Mock
import com.soyeb.ieltspractice.data.model.MockSection

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
