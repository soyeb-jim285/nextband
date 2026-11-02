package com.soyeb.ieltspractice.feature.study

import com.soyeb.ieltspractice.data.model.DueCard
import kotlin.math.roundToInt

// Pure logic for the study tools (no Compose): the review scheduler preview and the card text rules.

internal class Grade(val value: Int, val label: String)

/** Again, Hard, Good, Easy as the scheduler takes them (SM-2 quality 1, 3, 4, 5). */
internal val grades = listOf(Grade(1, "Again"), Grade(3, "Hard"), Grade(4, "Good"), Grade(5, "Easy"))

/** Port of packages/core srs.ts `review`: the interval in days the card would get for grade [g]. */
internal fun nextInterval(c: DueCard, g: Int): Int {
    if (g < 3) return 1
    val d = (5 - g).toDouble()
    val ease = maxOf(1.3, (c.ease ?: 2.5) + (0.1 - d * (0.08 + d * 0.02)))
    val reps = (c.reps ?: 0) + 1
    return if (reps == 1) 1 else if (reps == 2) 6 else Math.round((c.interval ?: 0.0) * ease).toInt()
}

internal fun daysLabel(n: Int): String = if (n == 1) "1 day" else if (n < 30) "$n days" else "${(n / 30.0).roundToInt()} mo"

/** What the grade bar shows under a button: Again comes back this session, the rest after [nextInterval] days. */
internal fun hint(c: DueCard, g: Int): String = if (g < 3) "This session" else daysLabel(nextInterval(c, g))

/** Fix cards are "title\n\nsentence" (server fixCard): the title is a label, only the sentence is the thing to improve. */
internal fun splitFixCard(card: DueCard): Pair<String?, String> {
    val i = card.front.indexOf("\n\n")
    return if (card.source == "fix" && i > 0) card.front.substring(0, i) to card.front.substring(i + 2) else null to card.front
}

private const val HEADPHONES = "🎧" // the server marks Listening spelling cards with this emoji; the app never shows it

/** Listening spelling cards ("<headphones> Listening · spell the word you heard…") keep the word as the first line of the back. */
internal fun heardWord(card: DueCard): String? =
    if (card.front.startsWith(HEADPHONES)) card.back.lineSequence().first().trim().ifEmpty { null } else null

/** The question text as shown: no marker emoji, fix-card title split off. */
internal fun cleanFront(card: DueCard): Pair<String?, String> {
    val (label, text) = splitFixCard(card)
    return label to text.removePrefix(HEADPHONES).trim()
}

internal val sourceLabel = mapOf("mistake" to "From your mistakes", "vocab" to "Vocabulary", "fix" to "Fix to practise")
internal val sourcePrompt = mapOf(
    "mistake" to "How would you correct this? Say or write it, then turn the card.",
    "vocab" to "Recall the meaning and use it in a sentence, then turn the card.",
    "fix" to "How would you improve this sentence? Say or write it, then turn the card.",
)

internal fun plural(n: Int, one: String, many: String = one + "s") = if (n == 1) one else many

/** Which Coach action opens which screen is decided in the UI; this names the default button text for an ActionType. */
internal fun actionLabel(action: String): String? = when (action) {
    "write-task2" -> "Write a Task 2 essay"
    "write-task1" -> "Write a Task 1"
    "speak-part" -> "Practise speaking"
    "speak-full" -> "Take a speaking test"
    "listening-test" -> "Take a Listening test"
    "reading-test" -> "Take a Reading test"
    "see-evidence" -> "See your mistakes"
    "review-deck" -> "Open your review deck"
    else -> null
}

/** Splits [items] into runs of neighbours that share a [key] (mistakes of one attempt sit together in the log). */
internal fun <T> runsBy(items: List<T>, key: (T) -> Any?): List<List<T>> {
    val out = mutableListOf<MutableList<T>>()
    for (it in items) {
        val last = out.lastOrNull()
        if (last != null && key(last.first()) == key(it)) last.add(it) else out.add(mutableListOf(it))
    }
    return out
}

/** The small line under a skill in the coach report: tests taken and where the trend is going. [trend]: improving | steady | slipping | calibrating. */
internal fun skillLine(tests: Int, trend: String, delta: Double?, needMore: Int): String {
    val taken = "$tests ${plural(tests, "test")}"
    val how = when (trend) {
        "improving" -> "Improving" + (delta?.let { " +" + com.soyeb.ieltspractice.data.logic.fmt(kotlin.math.abs(it)) } ?: "")
        "slipping" -> "Slipping" + (delta?.let { " -" + com.soyeb.ieltspractice.data.logic.fmt(kotlin.math.abs(it)) } ?: "")
        "steady" -> "Steady"
        else -> if (needMore > 0) "Needs $needMore more ${plural(needMore, "test")}" else "Still calibrating"
    }
    return "$taken · $how"
}
