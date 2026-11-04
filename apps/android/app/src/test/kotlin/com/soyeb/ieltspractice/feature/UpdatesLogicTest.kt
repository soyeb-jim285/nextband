package com.soyeb.ieltspractice.feature

import com.soyeb.ieltspractice.design.Skill
import com.soyeb.ieltspractice.feature.history.HStatus
import com.soyeb.ieltspractice.feature.history.HistoryItem
import com.soyeb.ieltspractice.feature.updates.UpdateKind
import com.soyeb.ieltspractice.feature.updates.buildUpdates
import com.soyeb.ieltspractice.feature.updates.freshCount
import com.soyeb.ieltspractice.feature.updates.markSeen
import com.soyeb.ieltspractice.feature.updates.parseSeen
import java.time.Instant
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals

class UpdatesLogicTest {
    private val now = Instant.parse("2026-10-03T18:00:00Z")
    private val utc = ZoneId.of("UTC")

    private fun item(id: String, status: HStatus, at: String = "2026-10-03T10:00:00Z", band: Double? = 6.5) =
        HistoryItem(id, Skill.Writing, "t", "Task 2", at, null, status, band, false)

    private fun build(vararg items: HistoryItem, due: Int = 0, exam: String? = null) = buildUpdates(due, null, items.toList(), exam, now, utc)

    @Test fun resultFinishedAfterTheSheetWasOpenedStillLightsTheDot() {
        // Opened while marking: nothing alerts, so nothing is stored as read.
        val marking = build(item("w1", HStatus.Analysing, at = "2026-10-03T17:40:00Z"))
        assertEquals(0, freshCount(marking, emptyList()))
        val seen = markSeen(marking, emptyList())
        assertEquals(emptyList(), seen)
        // The band lands; the row keeps the attempt's createdAt but is still new.
        val done = build(item("w1", HStatus.Done, at = "2026-10-03T17:40:00Z"))
        assertEquals(1, freshCount(done, seen))
        assertEquals(0, freshCount(done, markSeen(done, seen)))
    }

    @Test fun failedMarkingAlertsAndIsListed() {
        val rows = build(item("w2", HStatus.Failed, band = null))
        assertEquals(listOf(UpdateKind.Failed), rows.map { it.kind })
        assertEquals(1, freshCount(rows, emptyList()))
    }

    @Test fun windowCapsAndReminders() {
        val old = item("old", HStatus.Done, at = "2026-09-29T10:00:00Z")
        val results = (1..6).map { item("r$it", HStatus.Done) }
        val rows = build(old, *results.toTypedArray(), due = 3, exam = "2026-10-10")
        assertEquals(4, rows.count { it.kind == UpdateKind.Result })
        assertEquals(listOf(3, 7), rows.filter { it.kind == UpdateKind.Cards || it.kind == UpdateKind.Exam }.map { it.n })
        assertEquals(4, freshCount(rows, emptyList())) // reminders never light the dot
        assertEquals(emptyList(), build(exam = "2026-12-01").map { it.kind })
    }

    @Test fun stuckMarkingCountsAsFailedSoPollingStops() {
        val rows = build(item("w3", HStatus.Analysing, at = "2026-10-03T16:30:00Z", band = null), item("w4", HStatus.Analysing, at = "2026-10-03T17:50:00Z", band = null))
        assertEquals(listOf(UpdateKind.Marking, UpdateKind.Failed), rows.map { it.kind })
        assertEquals(listOf("a:w4", "f:w3"), rows.map { it.key })
    }

    @Test fun examRowCarriesTheOpenMock() {
        val rows = buildUpdates(0, "m1", emptyList(), "2026-10-10", now, utc)
        assertEquals(listOf(UpdateKind.Mock, UpdateKind.Exam), rows.map { it.kind })
        assertEquals("m1", rows.last().mockId)
    }

    @Test fun seenRoundTripsAndStaysSmall() {
        val many = (1..60).map { "d:$it" }
        val rows = build(item("new", HStatus.Done))
        val next = markSeen(rows, many)
        assertEquals(50, next.size)
        assertEquals("d:new", next.first())
        assertEquals(next, parseSeen(next.joinToString(",")))
        assertEquals(emptyList(), parseSeen(null))
    }
}
