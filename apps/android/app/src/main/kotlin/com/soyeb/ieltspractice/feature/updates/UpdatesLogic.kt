package com.soyeb.ieltspractice.feature.updates

import com.soyeb.ieltspractice.data.logic.Clocks
import com.soyeb.ieltspractice.feature.history.HStatus
import com.soyeb.ieltspractice.feature.history.HistoryItem
import com.soyeb.ieltspractice.feature.today.daysToExam
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

enum class UpdateKind { Marking, Failed, Result, Cards, Mock, Exam }

/** One line in the Updates sheet. [item] for attempt rows; [n] = cards due or days to the exam; [mockId] for Mock. */
data class UpdateRow(val kind: UpdateKind, val key: String, val item: HistoryItem? = null, val n: Int = 0, val mockId: String? = null) {
    /** Results and failed markings light the dot until seen. A marking the learner just started and standing reminders don't. */
    val alerts: Boolean get() = kind == UpdateKind.Result || kind == UpdateKind.Failed
}

/** [attempts] newest first (mergeItems). Attempts from the last 3 days; at most 4 results. */
fun buildUpdates(due: Int, mockId: String?, attempts: List<HistoryItem>, examDate: String?, now: Instant = Clocks.now(), zone: ZoneId = Clocks.zone()): List<UpdateRow> {
    val since = now.minus(Duration.ofDays(3))
    val recent = attempts.filter { (it.instant ?: Instant.EPOCH) > since }
    return buildList {
        recent.filter { it.status == HStatus.Analysing }.forEach { add(UpdateRow(UpdateKind.Marking, "a:${it.id}", it)) }
        recent.filter { it.status == HStatus.Failed }.forEach { add(UpdateRow(UpdateKind.Failed, "f:${it.id}", it)) }
        recent.filter { it.status == HStatus.Done && it.band != null }.take(4).forEach { add(UpdateRow(UpdateKind.Result, "d:${it.id}", it)) }
        if (due > 0) add(UpdateRow(UpdateKind.Cards, "cards", n = due))
        if (mockId != null) add(UpdateRow(UpdateKind.Mock, "mock", mockId = mockId))
        daysToExam(examDate, now, zone)?.takeIf { it in 0..30 }?.let { add(UpdateRow(UpdateKind.Exam, "exam", n = it)) }
    }
}

// ponytail: seen state is a list of row keys, not a timestamp: a result's time is when the attempt was made, not when marking
// finished, so a watermark set while it was still marking would hide the band when it arrives.

fun freshCount(rows: List<UpdateRow>, seen: Collection<String>): Int = rows.count { it.alerts && it.key !in seen }

/** Keys to store after [rows] were shown: newest first, capped so the pref stays small (older rows have left the 3-day window). */
fun markSeen(rows: List<UpdateRow>, seen: List<String>): List<String> =
    (rows.filter { it.alerts }.map { it.key } + seen).distinct().take(50)

fun parseSeen(s: String?): List<String> = s?.split(',')?.filter { it.isNotBlank() }.orEmpty()
