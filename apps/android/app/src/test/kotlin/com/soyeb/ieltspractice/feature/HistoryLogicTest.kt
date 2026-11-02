package com.soyeb.ieltspractice.feature

import com.soyeb.ieltspractice.design.Skill
import com.soyeb.ieltspractice.feature.history.HStatus
import com.soyeb.ieltspractice.feature.history.HistoryItem
import com.soyeb.ieltspractice.feature.history.bucketOf
import com.soyeb.ieltspractice.feature.history.durationText
import com.soyeb.ieltspractice.feature.history.mergeItems
import java.time.Instant
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HistoryLogicTest {
    private val now = Instant.parse("2026-10-03T18:00:00Z")
    private val utc = ZoneId.of("UTC")
    private fun at(s: String) = Instant.parse(s)

    private fun item(id: String, at: String, ms: Int? = null, skill: Skill = Skill.Writing) =
        HistoryItem(id, skill, "t", "Task 1", at, ms, HStatus.Done, 6.5, false)

    @Test fun bucketsByCalendarDay() {
        assertEquals("Today", bucketOf(at("2026-10-03T01:00:00Z"), now, utc))
        assertEquals("Yesterday", bucketOf(at("2026-10-02T23:59:00Z"), now, utc))
        assertEquals("This week", bucketOf(at("2026-09-28T10:00:00Z"), now, utc))
        assertEquals("Earlier, September", bucketOf(at("2026-09-20T10:00:00Z"), now, utc))
        assertEquals("Earlier, December 2025", bucketOf(at("2025-12-20T10:00:00Z"), now, utc))
        assertEquals("Earlier", bucketOf(null, now, utc))
    }

    @Test fun mergeSortsNewestFirstAndDropsDuplicates() {
        val merged = mergeItems(listOf(item("a", "2026-09-01T00:00:00Z"), item("b", "2026-09-03T00:00:00Z")), listOf(item("a", "2026-09-01T00:00:00Z")))
        assertEquals(listOf("b", "a"), merged.map { it.id })
    }

    @Test fun shortEditorTimeIsHidden() {
        assertNull(durationText(item("a", "", 30_000)))
        assertEquals("12 min", durationText(item("a", "", 12 * 60_000)))
        assertEquals("30 s", durationText(item("a", "", 30_000, Skill.Speaking)))
    }
}
