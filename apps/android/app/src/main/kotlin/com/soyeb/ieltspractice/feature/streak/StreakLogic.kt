package com.soyeb.ieltspractice.feature.streak

import com.soyeb.ieltspractice.data.model.Streak
import com.soyeb.ieltspractice.data.model.StreakDay
import java.time.LocalDate
import java.time.format.DateTimeFormatter
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
 * after today. [weeks] keeps exactly that many of the newest columns, padding empty ones on the left when history is short.
 */
fun heatColumns(days: List<StreakDay>, weeks: Int? = null): List<List<StreakDay?>> {
    val first = days.firstOrNull()?.let { date(it.day) } ?: return emptyList()
    val lead = first.dayOfWeek.value - 1 // Monday = 0
    val cells: List<StreakDay?> = List(lead) { null } + days
    val cols = cells.chunked(7).map { it + List(7 - it.size) { null } }
    if (weeks == null) return cols
    return List((weeks - cols.size).coerceAtLeast(0)) { List<StreakDay?>(7) { null } } + cols.takeLast(weeks)
}

/**
 * A month name over the column that holds the 1st of that month ("Oct"), else null. Same length as [cols]. The first
 * column names its own month only when it starts before the 22nd, so its label never runs into the next month's.
 */
fun monthLabels(cols: List<List<StreakDay?>>): List<String?> = cols.mapIndexed { i, col ->
    val days = col.mapNotNull { it?.let { c -> date(c.day) } }
    val start = days.firstOrNull { it.dayOfMonth == 1 } ?: days.firstOrNull()?.takeIf { i == 0 && it.dayOfMonth < 22 }
    start?.month?.getDisplayName(TextStyle.SHORT, Locale.US)
}

private val LEVEL_WORDS = listOf("no practice", "drills", "a test", "a test and drills")

/** "Sun 4 Oct: a test and drills" for one heatmap square (its TalkBack label and the line a tap shows). */
fun dayDescription(d: StreakDay, today: String?): String {
    // US short names: en-GB says "Sept".
    val name = date(d.day)?.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.US)) ?: d.day
    val what = when {
        d.level > 0 -> LEVEL_WORDS[d.level.coerceAtMost(3)]
        d.frozen -> "kept by a freeze"
        d.day == today -> "not practised yet"
        else -> LEVEL_WORDS[0]
    }
    return "${if (d.day == today) "Today, " else ""}$name: $what"
}

/**
 * One TalkBack stop per heatmap week: "Week of 4 Aug, 2 practice days. Mon 4 Aug: drills. Wed 6 Aug: a test", naming only
 * the days with something to say (practice, a freeze, today). Empty for a padding column.
 */
fun weekDescription(col: List<StreakDay?>, today: String?): String {
    val days = col.filterNotNull()
    val monday = days.firstOrNull()?.let { date(it.day) }?.let { it.minusDays(it.dayOfWeek.value - 1L) } ?: return ""
    val n = days.count { it.level > 0 }
    val notes = days.filter { it.level > 0 || it.frozen || it.day == today }.map { dayDescription(it, today) }
    return (listOf("Week of ${monday.format(DateTimeFormatter.ofPattern("d MMM", Locale.US))}, $n practice ${plural(n, "day")}") + notes).joinToString(". ")
}

/** Practice days so far in today's calendar month. */
fun practisedThisMonth(s: Streak): Int {
    val month = localToday(s)?.take(7) ?: return 0
    return s.days.count { it.day.startsWith(month) && it.level > 0 }
}

fun plural(n: Int, word: String) = if (n == 1) word else "${word}s"

/** After 18:00 local with nothing done today, a streak worth keeping (2+) is at risk. */
fun atRisk(s: Streak, hour: Int): Boolean = !s.done && s.current >= 2 && hour >= 18

/** The Today card line. [days]: practice days in the card's 12-week map, so the line matches what it shows. */
fun practisedLine(s: Streak, risk: Boolean, days: Int): String =
    if (risk) "Ends at midnight: a 2-minute drill saves it"
    else "$days practice ${plural(days, "day")} in the last 12 weeks · Longest ${s.longest}"

/** Under the freeze count: "next at day 14". One freeze per 7 days in a row, at most 2 held. */
fun freezeNote(s: Streak): String = if (s.freezes >= 2) "the most you can hold" else "next at day ${(s.current / 7 + 1) * 7}"

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
