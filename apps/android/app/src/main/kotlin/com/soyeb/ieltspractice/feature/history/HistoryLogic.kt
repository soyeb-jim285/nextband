package com.soyeb.ieltspractice.feature.history

import com.soyeb.ieltspractice.data.logic.Clocks
import com.soyeb.ieltspractice.data.logic.partsLabel
import com.soyeb.ieltspractice.data.model.AttemptListItem
import com.soyeb.ieltspractice.data.model.LrAttemptItem
import com.soyeb.ieltspractice.data.model.Mock
import com.soyeb.ieltspractice.design.Skill
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Where a result stands. Open = a recording never submitted, or a Listening/Reading attempt still in progress. */
enum class HStatus { Done, Analysing, Failed, Open }

/** One row of History and of Today's recent results: a speaking/writing attempt or a Listening/Reading attempt, same shape. */
data class HistoryItem(
    val id: String,
    val skill: Skill,
    val title: String,
    /** "Part 2", "Task 1", "Full test", "Passages 1, 3". */
    val detail: String,
    /** ISO instant; "" when unknown. */
    val at: String,
    val durationMs: Int?,
    val status: HStatus,
    val band: Double?,
    /** Listening or Reading (opens LrResult / LrRun, deletes with lrDelete). */
    val lr: Boolean,
    /** A full mock test: [id] is the mock id (opens MockHub while open, MockResult after; not deletable here). */
    val mock: Boolean = false,
) {
    val instant: Instant? get() = runCatching { Instant.parse(at) }.getOrNull()
}

fun AttemptListItem.toItem() = HistoryItem(
    id = id, skill = Skill.of(skill) ?: Skill.Speaking, title = promptTitle,
    detail = "${if (skill == "writing") "Task" else "Part"} $part", at = createdAt, durationMs = durationMs,
    status = when (status) { "done" -> HStatus.Done; "failed" -> HStatus.Failed; "recording" -> HStatus.Open; else -> HStatus.Analysing },
    band = overall, lr = false,
)

fun LrAttemptItem.toItem() = HistoryItem(
    id = id, skill = Skill.of(skill) ?: Skill.Listening, title = title, detail = partsLabel(skill, parts),
    at = submittedAt ?: startedAt, durationMs = null,
    status = if (status == "submitted") HStatus.Done else HStatus.Open, band = band, lr = true,
)

fun Mock.toItem() = HistoryItem(
    id = id, skill = Skill.Listening, title = "Full mock test",
    detail = ref?.let { "Cambridge $it" } ?: if (variant == "general") "General Training" else "Academic",
    at = completedAt ?: startedAt, durationMs = null,
    status = if (open) HStatus.Open else if (marking) HStatus.Analysing else HStatus.Done, band = overall, lr = false, mock = true,
)

/** Newest first; an id seen twice (a paged fixture repeating itself) is kept once. */
fun mergeItems(vararg lists: List<HistoryItem>): List<HistoryItem> =
    lists.flatMap { it }.distinctBy { it.id }.sortedByDescending { it.instant ?: Instant.EPOCH }

/** Editor time under a minute is a pasted or abandoned essay; speaking recordings are short by design (web shownDuration). */
fun durationText(item: HistoryItem): String? {
    val ms = item.durationMs ?: return null
    if (ms <= 0 || (item.skill != Skill.Speaking && ms < 60_000)) return null
    val s = ms / 1000
    return if (s < 60) "$s s" else "${(s + 30) / 60} min"
}

private val dayFmt = DateTimeFormatter.ofPattern("d MMM", Locale.US)
private val monthFmt = DateTimeFormatter.ofPattern("MMMM", Locale.US)
private val monthYearFmt = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.US)

fun dateText(at: Instant?, zone: ZoneId = Clocks.zone()): String? = at?.atZone(zone)?.format(dayFmt)

/** Sticky header text: Today, Yesterday, This week (the last 6 days), then "Earlier, September" (year added when not this one). */
fun bucketOf(at: Instant?, now: Instant = Clocks.now(), zone: ZoneId = Clocks.zone()): String {
    if (at == null) return "Earlier"
    val d = at.atZone(zone).toLocalDate()
    val today = now.atZone(zone).toLocalDate()
    val ago = Duration.between(d.atStartOfDay(), today.atStartOfDay()).toDays()
    return when {
        ago <= 0 -> "Today"
        ago == 1L -> "Yesterday"
        ago < 7 -> "This week"
        d.year == today.year -> "Earlier, ${d.format(monthFmt)}"
        else -> "Earlier, ${d.format(monthYearFmt)}"
    }
}

/** Row subtitle: "Part 2 · 30 Sep · 12 min". */
fun metaLine(item: HistoryItem, zone: ZoneId = Clocks.zone()): String =
    listOfNotNull(item.detail, dateText(item.instant, zone), durationText(item)).joinToString(" · ")

/** The History skill filter: null = all. Listening and Reading only exist as L/R attempts; Writing and Speaking only as the other list. */
val historyFilters = listOf<Skill?>(null, Skill.Listening, Skill.Reading, Skill.Writing, Skill.Speaking)

/** The bottom-bar tab of a skill. */
fun skillTab(skill: Skill): com.soyeb.ieltspractice.nav.Tab = com.soyeb.ieltspractice.nav.Tab.entries.first { it.skill == skill }
