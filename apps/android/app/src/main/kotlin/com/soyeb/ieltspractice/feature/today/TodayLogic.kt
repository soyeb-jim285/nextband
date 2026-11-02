package com.soyeb.ieltspractice.feature.today

import com.soyeb.ieltspractice.data.logic.Band
import com.soyeb.ieltspractice.data.logic.Clocks
import com.soyeb.ieltspractice.data.logic.Crit
import com.soyeb.ieltspractice.data.logic.fmt
import com.soyeb.ieltspractice.data.model.Coach
import com.soyeb.ieltspractice.data.model.LrProgress
import com.soyeb.ieltspractice.data.model.ProgressData
import com.soyeb.ieltspractice.design.Skill
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** "Good evening, Maya"; Hello between midnight and 5. [name] may be blank (no name yet). */
fun greeting(hour: Int, name: String?): String {
    val hello = when {
        hour < 5 -> "Hello"
        hour < 12 -> "Good morning"
        hour < 18 -> "Good afternoon"
        else -> "Good evening"
    }
    val first = name?.trim()?.split(Regex("\\s+"))?.firstOrNull().orEmpty()
    return if (first.isEmpty()) hello else "$hello, $first"
}

fun hourNow(now: Instant = Clocks.now(), zone: ZoneId = Clocks.zone()): Int = now.atZone(zone).hour

/** "5 day streak · 96 min this week"; parts that are zero are left out. */
fun subtitle(streak: Int, minutesThisWeek: Double): String? {
    val parts = listOfNotNull(
        if (streak > 0) "$streak day streak" else null,
        if (minutesThisWeek >= 1) "${Math.round(minutesThisWeek)} min this week" else null,
    )
    return parts.joinToString(" · ").ifEmpty { null }
}

/** Whole days from today to the exam date (`yyyy-MM-dd`); null when unset, unreadable or already past. */
fun daysToExam(examDate: String?, now: Instant = Clocks.now(), zone: ZoneId = Clocks.zone()): Int? {
    val d = examDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return null
    val days = Duration.between(now.atZone(zone).toLocalDate().atStartOfDay(), d.atStartOfDay()).toDays().toInt()
    return days.takeIf { it >= 0 }
}

fun examChipText(days: Int): String = when (days) {
    0 -> "Your exam is today"
    1 -> "1 day to your exam"
    else -> "$days days to your exam"
}

/** Mean of the last up to 5 bands (oldest first), to the nearest half band. */
fun averageBand(bands: List<Double>): Double? = bands.takeLast(5).takeIf { it.isNotEmpty() }?.let { Band.round(it.sum() / it.size) }

/** The four predicted skill bands. Speaking and Writing use the server's prediction, Listening and Reading average their recent tests. */
data class SkillBands(val listening: Double?, val reading: Double?, val writing: Double?, val speaking: Double?) {
    fun of(skill: Skill): Double? = when (skill) {
        Skill.Listening -> listening; Skill.Reading -> reading; Skill.Writing -> writing; Skill.Speaking -> speaking
    }
    val count: Int get() = Skill.entries.count { of(it) != null }
    /** Needs two skills so one lucky test never poses as an overall band (web overallEstimate). */
    val overall: Double? get() = Skill.entries.mapNotNull { of(it) }.takeIf { it.size >= 2 }?.let { Band.round(it.sum() / it.size) }
}

fun skillBands(p: ProgressData?, lr: LrProgress?): SkillBands {
    fun trend(skill: String) = p?.trend.orEmpty().filter { it.skill == skill }.map { it.overall }
    fun lrTrend(skill: String) = lr?.trend.orEmpty().filter { it.skill == skill }.map { it.band }
    return SkillBands(
        listening = averageBand(lrTrend("listening")),
        reading = averageBand(lrTrend("reading")),
        writing = p?.predicted?.writing ?: averageBand(trend("writing")),
        speaking = p?.predicted?.speaking ?: averageBand(trend("speaking")),
    )
}

/** What the Up next button does. The screen maps it to navigation. */
sealed interface NextAct {
    data class WriteTask(val task: Int) : NextAct
    data class SpeakPart(val part: Int) : NextAct
    data object SpeakFull : NextAct
    data class OpenTab(val skill: Skill) : NextAct
    data class LrTest(val id: String, val skill: String) : NextAct
    data class Result(val id: String) : NextAct
    data object ReviewDeck : NextAct
    data object OpenCoach : NextAct
}

data class UpNext(val title: String, val body: String, val cta: String, val skill: Skill?, val act: NextAct, val fromCoach: Boolean = false)

/** Coach action strings (apps/server/src/coach/types.ts ActionType) to what the app can do. */
fun coachAct(action: String): NextAct = when (action) {
    "write-task1" -> NextAct.WriteTask(1)
    "write-task2" -> NextAct.WriteTask(2)
    "speak-part" -> NextAct.SpeakPart(2)
    "speak-full" -> NextAct.SpeakFull
    "listening-test" -> NextAct.OpenTab(Skill.Listening)
    "reading-test" -> NextAct.OpenTab(Skill.Reading)
    "review-deck" -> NextAct.ReviewDeck
    else -> NextAct.OpenCoach
}

private fun actSkill(a: NextAct): Skill? = when (a) {
    is NextAct.WriteTask -> Skill.Writing
    is NextAct.SpeakPart, NextAct.SpeakFull -> Skill.Speaking
    is NextAct.OpenTab -> a.skill
    is NextAct.LrTest -> Skill.of(a.skill)
    else -> null
}

/**
 * The one thing to do next. Order: the coach's top focus, a result that failed marking, your weakest criterion,
 * the suggested Listening/Reading test, then a plain full speaking test.
 */
fun pickUpNext(coach: Coach?, p: ProgressData?, lr: LrProgress?, target: Double): UpNext {
    val report = coach?.takeIf { it.ready }?.report
    val focus = report?.words?.focus?.firstOrNull { "focus:${it.candidateId}" !in report.hidden && it.title.isNotBlank() }
    if (focus != null) {
        val act = coachAct(focus.action)
        return UpNext(focus.title, focus.why, focus.label.ifBlank { "Start" }, actSkill(act), act, fromCoach = true)
    }
    p?.lastFailed?.let { f ->
        return UpNext("Your last result didn't finish marking", "Your answer is saved. Open it to try marking again.", "Open result", Skill.of(f.skill), NextAct.Result(f.id))
    }
    val w = p?.weakest
    if (w != null && w.key in Crit.labels) {
        val skill = w.skill ?: if (w.key == "ta" || w.key == "cc") "writing" else "speaking"
        val avg = Math.round(w.avg * 2) / 2.0
        val gap = if (avg < target) ", ${fmt(target - avg)} below your ${fmt(target)} target" else ""
        val act = if (skill == "writing") NextAct.WriteTask(2) else NextAct.SpeakPart(if (w.key == "p") 1 else 2)
        return UpNext(
            "${Crit.label(w.key)} is holding your band back",
            "You average ${fmt(avg)} here$gap. Focused practice there moves it fastest.",
            if (act is NextAct.WriteTask) "Write a Task 2 essay" else "Practise Part ${(act as NextAct.SpeakPart).part}",
            Skill.of(skill), act,
        )
    }
    lr?.suggested?.let { s ->
        return UpNext("${s.label} is your weakest question type", "${s.title} has ${s.count} of them.", "Practise ${s.label.lowercase()}", Skill.of(s.skill), NextAct.LrTest(s.id, s.skill))
    }
    return UpNext("Ready for another round?", "A full speaking test gives the most complete picture of your band.", "Start a speaking test", Skill.Speaking, NextAct.SpeakFull)
}
