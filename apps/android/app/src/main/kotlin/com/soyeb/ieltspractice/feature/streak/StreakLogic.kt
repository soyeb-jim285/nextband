package com.soyeb.ieltspractice.feature.streak

import com.soyeb.ieltspractice.data.model.Streak
import com.soyeb.ieltspractice.data.model.StreakDay
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

// Pure streak rules and copy (docs/engagement/notifications-and-streaks.md §3). The screens map these to colours and layout.

val MILESTONES = listOf(3, 7, 14, 30, 50, 100, 200, 365)

/** How a heatmap cell is filled. Levels differ by fill strength (brandSoft → brand), frozen days by an ice tint and a ❄. */
enum class HeatRole { Empty, Low, Mid, Full, Frozen }

fun heatRole(d: StreakDay): HeatRole = when {
    d.level <= 0 && d.frozen -> HeatRole.Frozen
    d.level <= 0 -> HeatRole.Empty
    d.level == 1 -> HeatRole.Low
    d.level == 2 -> HeatRole.Mid
    else -> HeatRole.Full
}

private fun date(s: String): LocalDate? = runCatching { LocalDate.parse(s) }.getOrNull()

/**
 * The heatmap as week columns, Monday at the top. The first column is padded with nulls before the oldest day, the last
 * after today. [weeks] keeps only the newest columns (the Today card shows 5).
 */
fun heatColumns(days: List<StreakDay>, weeks: Int? = null): List<List<StreakDay?>> {
    val first = days.firstOrNull()?.let { date(it.day) } ?: return emptyList()
    val lead = first.dayOfWeek.value - 1 // Monday = 0
    val cells: List<StreakDay?> = List(lead) { null } + days
    val cols = cells.chunked(7).map { it + List(7 - it.size) { null } }
    return if (weeks != null) cols.takeLast(weeks) else cols
}

/** A month name over the first column where that month starts ("Oct"), else null. Same length as [cols]. */
fun monthLabels(cols: List<List<StreakDay?>>): List<String?> {
    var last: Int? = null
    return cols.map { col ->
        val d = col.firstNotNullOfOrNull { it?.let { c -> date(c.day) } } ?: return@map null
        // A column that holds the 1st of a month labels that month; the first column labels its own month.
        val start = col.mapNotNull { it?.let { c -> date(c.day) } }.firstOrNull { it.dayOfMonth == 1 } ?: d
        val m = start.monthValue
        if (m == last) null else { last = m; start.month.getDisplayName(TextStyle.SHORT, Locale.US) }
    }
}

/** TalkBack summary of one week column: "Week of 28 Sep: 5 practice days, 1 frozen". */
fun columnDescription(col: List<StreakDay?>): String {
    val days = col.filterNotNull()
    val start = days.firstOrNull()?.let { date(it.day) } ?: return ""
    val practised = days.count { it.level > 0 }
    val frozen = days.count { it.level <= 0 && it.frozen }
    val head = "Week of ${start.dayOfMonth} ${start.month.getDisplayName(TextStyle.SHORT, Locale.US)}: $practised practice ${plural(practised, "day")}"
    return if (frozen > 0) "$head, $frozen frozen" else head
}

fun plural(n: Int, word: String) = if (n == 1) word else "${word}s"

/** After 18:00 local with nothing done today, a streak worth keeping (2+) is at risk. */
fun atRisk(s: Streak, hour: Int): Boolean = !s.done && s.current >= 2 && hour >= 18

/** The Today card line. */
fun practisedLine(s: Streak, risk: Boolean): String =
    if (risk) "Ends at midnight: a 2-minute drill saves it"
    else "You practised ${s.practicedLast35} of the last 35 days · Longest ${s.longest}"

/** "1 freeze saved · next one at day 14". One freeze per 7 days in a row, at most 2 held. */
fun freezeLine(s: Streak): String {
    val next = (s.current / 7 + 1) * 7
    return when {
        s.freezes >= 2 -> "2 freezes saved, the most you can hold"
        s.freezes == 1 -> "1 freeze saved · next one at day $next"
        else -> "No freezes yet · you earn one at day $next"
    }
}

/** Progress from the last milestone passed to the next one, 0..1; 1 past the last milestone. */
fun milestoneFraction(current: Int, next: Int?): Float {
    if (next == null) return 1f
    val prev = MILESTONES.lastOrNull { it <= current && it < next } ?: 0
    return ((current - prev).toFloat() / (next - prev)).coerceIn(0f, 1f)
}

fun repairLine(target: Int, daysDone: Int): String =
    if (daysDone <= 0) "Bring back your $target-day streak: practise today and tomorrow"
    else "One more day brings back your $target-day streak"

/** "12 day streak, practised today" (the flame's content description). */
fun flameDescription(s: Streak): String =
    "${s.current} day streak, ${if (s.done) "practised today" else "not practised yet today"}"

/** "Monday, practised" for one week-strip day. */
fun weekDayDescription(day: String, state: String): String {
    val name = date(day)?.dayOfWeek?.getDisplayName(TextStyle.FULL, Locale.UK) ?: day
    val what = when (state) {
        "done" -> "practised"
        "frozen" -> "kept by a freeze"
        "missed" -> "missed"
        "today" -> "today, not practised yet"
        else -> "still to come"
    }
    return "$name, $what"
}

/** "M" for a Monday. */
fun weekLetter(day: String): String = date(day)?.dayOfWeek?.name?.take(1).orEmpty()

private val CHEERS = listOf(
    "Day %d. You're on fire",
    "%d days in a row. Keep it going",
    "Day %d done. Your band thanks you",
    "%d days straight. That's how bands move",
    "Day %d. Small steps, every day",
    "%d days and counting",
    "Day %d in the bag",
    "%d days. You keep showing up",
)

/** The celebration headline. Day 1 and 2 have their own lines; milestones say so; the rest rotate by day. */
fun celebrationLine(n: Int): String = when {
    n <= 1 -> "Day 1. A new streak starts here"
    n == 2 -> "Back-to-back days! 2 in a row"
    n in MILESTONES -> "$n-day milestone. Brilliant work"
    else -> CHEERS[n % CHEERS.size].format(n)
}

/**
 * Celebrate once per local day, when the streak has just turned done. [prevDone]: what this app last saw today (null =
 * nothing loaded yet). With nothing seen before, only a finished test or drill ([afterPractice]) counts as the flip, so
 * opening the app after practising on the web stays quiet.
 */
fun shouldCelebrate(prevDone: Boolean?, afterPractice: Boolean, now: Streak, lastShown: String?, today: String?): Boolean =
    now.done && now.current > 0 && today != null && lastShown != today && (prevDone == false || (prevDone == null && afterPractice))

/** The student's local today: the newest heatmap day (the server's time zone), else the week strip's done/today day. */
fun localToday(s: Streak): String? = s.days.lastOrNull()?.day ?: s.week.firstOrNull { it.state == "today" }?.day
