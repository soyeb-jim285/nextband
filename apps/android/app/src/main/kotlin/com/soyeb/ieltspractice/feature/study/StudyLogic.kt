package com.soyeb.ieltspractice.feature.study

import com.soyeb.ieltspractice.data.model.DueCard
import kotlin.math.roundToInt

// Pure logic for the study tools (no Compose): the drill scheduler preview and the card text rules.

/** The two drill verdicts as the scheduler takes them (SM-2 quality): Not yet comes back this session and tomorrow. */
internal const val NOT_YET = 1
internal const val GOT_IT = 4

/** A drill session is this many cards; "Keep going" starts another. */
internal const val SESSION = 10

/** Port of packages/core srs.ts `review`: the interval in days the card would get for grade [g]. */
internal fun nextInterval(c: DueCard, g: Int): Int {
    if (g < 3) return 1
    val d = (5 - g).toDouble()
    val ease = maxOf(1.3, (c.ease ?: 2.5) + (0.1 - d * (0.08 + d * 0.02)))
    val reps = (c.reps ?: 0) + 1
    return if (reps == 1) 1 else if (reps == 2) 6 else Math.round((c.interval ?: 0.0) * ease).toInt()
}

internal fun daysLabel(n: Int): String = if (n == 1) "1 day" else if (n < 30) "$n days" else "${(n / 30.0).roundToInt()} mo"

/** Under Got it: when the card comes back. */
internal fun gotItHint(c: DueCard): String = nextInterval(c, GOT_IT).let { if (it == 1) "tomorrow" else "in ${daysLabel(it)}" }

/** What to type, and the why under it. Backs are "correction — why" (server mistakeCard), "correction\n\nwhy" (result pages, fix cards) or "word\nYou wrote: x" (spelling). */
internal fun answerOf(back: String): Pair<String, String> {
    val m = Regex(""" — |\n\n|\n(?=You (?:wrote|typed):)""").find(back) ?: return back.trim() to ""
    return back.substring(0, m.range.first).trim() to back.substring(m.range.last + 1).trim()
}

/** The text to correct, with a label: a fix card's title, or "Listening · spell the word you heard" for spelling cards. */
internal fun promptOf(c: DueCard): Pair<String?, String> {
    val cut = if (c.source == "fix") c.front.indexOf("\n\n") else -1
    if (cut > 0) return c.front.substring(0, cut) to c.front.substring(cut + 2)
    // server lr.ts spellingCards: "🎧 Listening · spell the word you heard: 'p__k' (4 letters)"
    Regex("""^(?:🎧|📖) (.+?): '(.+?)'([\s\S]*)$""").find(c.front)?.let { m ->
        return m.groupValues[1].replaceFirstChar { it.uppercase() } to m.groupValues[2] + m.groupValues[3]
    }
    return null to c.front.trim().removePrefix("🎧").removePrefix("📖").trim().removeSurrounding("Fix: \"", "\"")
}

private fun norm(s: String) = s.lowercase().replace(Regex("""[^\p{L}\p{N}' ]+"""), " ").replace(Regex("""\s+"""), " ").trim()

/** Same words, ignoring case, punctuation and spacing. */
internal fun sameAnswer(typed: String, answer: String) = norm(typed) == norm(answer)

/** A spelling card from the Listening test: its word can be heard. */
internal fun isHeard(c: DueCard) = c.category == "spelling" && c.front.startsWith("🎧")

internal enum class Change { Same, Gone, Added }

/** Word diff of what was typed against the answer (LCS over words), for "your version, with the changes it needs". */
internal fun wordDiff(typed: String, answer: String): List<Pair<Change, String>> {
    val a = typed.trim().split(Regex("""\s+""")).filter { it.isNotEmpty() }
    val b = answer.trim().split(Regex("""\s+""")).filter { it.isNotEmpty() }
    val k = Array(a.size + 1) { IntArray(b.size + 1) }
    for (i in a.indices.reversed()) for (j in b.indices.reversed())
        k[i][j] = if (a[i].equals(b[j], true)) k[i + 1][j + 1] + 1 else maxOf(k[i + 1][j], k[i][j + 1])
    val out = mutableListOf<Pair<Change, String>>()
    var i = 0; var j = 0
    while (i < a.size || j < b.size) when {
        i < a.size && j < b.size && a[i].equals(b[j], true) -> { out += Change.Same to b[j]; i++; j++ }
        // removals before additions, so "~~living~~ have lived" reads in order
        i < a.size && (j == b.size || k[i + 1][j] >= k[i][j + 1]) -> { out += Change.Gone to a[i]; i++ }
        else -> { out += Change.Added to b[j]; j++ }
    }
    return out
}

/** "also in 3 of your last 5 tests" for a recurring mistake. */
internal fun seenLine(before: Int, outOf: Int): String = when {
    before == outOf && outOf == 1 -> "also in your last test"
    before == outOf -> "also in all of your last $outOf tests"
    else -> "also in $before of your last $outOf ${plural(outOf, "test")}"
}

/** A pattern's trend from mistakes per test, newest 3 tests against the 3 before: null when either is unknown or both are 0. */
internal fun trendLine(recent: Double?, before: Double?): String? {
    if (recent == null || before == null || (recent == 0.0 && before == 0.0)) return null
    val f = { x: Double -> com.soyeb.ieltspractice.data.logic.fmtNum(x) }
    return when {
        recent < before -> "Down to ${f(recent)} a test from ${f(before)}"
        recent > before -> "Up to ${f(recent)} a test from ${f(before)}"
        else -> "Steady at ${f(recent)} a test"
    }
}

internal fun plural(n: Int, one: String, many: String = one + "s") = if (n == 1) one else many

/** Which Coach action opens which screen is decided in the UI; this names the default button text for an ActionType. */
internal fun actionLabel(action: String): String? = when (action) {
    "write-task2" -> "Write a Task 2 essay"
    "write-task1" -> "Write a Task 1"
    "speak-part" -> "Practise speaking"
    "speak-full" -> "Take a speaking test"
    "listening-test" -> "Take a Listening test"
    "reading-test" -> "Take a Reading test"
    "see-evidence" -> "See your patterns"
    "review-deck" -> "Drill your mistakes"
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
