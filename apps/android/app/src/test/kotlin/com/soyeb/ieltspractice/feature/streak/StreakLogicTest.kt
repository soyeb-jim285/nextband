package com.soyeb.ieltspractice.feature.streak

import com.soyeb.ieltspractice.data.demo.DemoToday
import com.soyeb.ieltspractice.data.model.Streak
import com.soyeb.ieltspractice.data.model.StreakDay
import com.soyeb.ieltspractice.data.net.AppJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StreakLogicTest {
    private fun day(d: String, level: Int = 0, frozen: Boolean = false) = StreakDay(d, level, frozen)

    @Test fun columnsStartOnMondayAndPadBothEnds() {
        // Wed 30 Sep 2026 .. Mon 5 Oct 2026
        val days = listOf("2026-09-30", "2026-10-01", "2026-10-02", "2026-10-03", "2026-10-04", "2026-10-05").map { day(it, 1) }
        val cols = heatColumns(days)
        assertEquals(2, cols.size)
        assertEquals(listOf(null, null), cols[0].take(2)) // Mon, Tue before the first day
        assertEquals("2026-09-30", cols[0][2]?.day)
        assertEquals("2026-10-05", cols[1][0]?.day)
        assertEquals(6, cols[1].count { it == null })
        assertTrue(cols.all { it.size == 7 })
        assertEquals(1, heatColumns(days, weeks = 1).size)
        assertTrue(heatColumns(emptyList()).isEmpty())
    }

    @Test fun rolesByLevelAndFreeze() {
        assertEquals(HeatRole.Empty, heatRole(day("2026-10-01")))
        assertEquals(HeatRole.Frozen, heatRole(day("2026-10-01", frozen = true)))
        assertEquals(HeatRole.Low, heatRole(day("2026-10-01", 1)))
        assertEquals(HeatRole.Mid, heatRole(day("2026-10-01", 2)))
        assertEquals(HeatRole.Full, heatRole(day("2026-10-01", 3)))
        assertEquals(HeatRole.Mid, heatRole(day("2026-10-01", 2, frozen = true))) // practised beats frozen
    }

    @Test fun atRiskOnlyAfterSixWithAStreakAndNothingDone() {
        val pending = Streak(current = 5, today = "pending")
        assertTrue(atRisk(pending, 18))
        assertFalse(atRisk(pending, 17))
        assertFalse(atRisk(pending.copy(today = "done"), 21))
        assertFalse(atRisk(pending.copy(current = 1), 21))
        assertEquals("Ends at midnight: a 2-minute drill saves it", practisedLine(pending, true))
        assertEquals("You practised 23 of the last 35 days · Longest 21", practisedLine(Streak(practicedLast35 = 23, longest = 21), false))
    }

    @Test fun copy() {
        assertEquals("Day 1. A new streak starts here", celebrationLine(1))
        assertEquals("Back-to-back days! 2 in a row", celebrationLine(2))
        assertEquals("7-day milestone. Brilliant work", celebrationLine(7))
        assertTrue(celebrationLine(12).contains("12"))
        assertEquals("1 freeze saved · next one at day 14", freezeLine(Streak(current = 12, freezes = 1)))
        assertEquals("No freezes yet · you earn one at day 7", freezeLine(Streak(current = 3)))
        assertEquals("Bring back your 12-day streak: practise today and tomorrow", repairLine(12, 0))
        assertEquals("12 day streak, practised today", flameDescription(Streak(current = 12, today = "done")))
        assertEquals("Wednesday, kept by a freeze", weekDayDescription("2026-09-30", "frozen"))
        assertEquals("W", weekLetter("2026-09-30"))
    }

    @Test fun milestones() {
        assertEquals(listOf(3, 7, 14, 30, 50, 100, 200, 365), MILESTONES)
        assertEquals(5f / 7, milestoneFraction(12, 14)) // from 7 to 14
        assertEquals(0f, milestoneFraction(0, 3))
        assertEquals(1f, milestoneFraction(400, null))
    }

    @Test fun monthLabelsOncePerMonth() {
        val days = (0 until 60).map { day(java.time.LocalDate.of(2026, 8, 20).plusDays(it.toLong()).toString()) }
        val labels = monthLabels(heatColumns(days)).filterNotNull()
        assertEquals(listOf("Aug", "Sep", "Oct"), labels)
    }

    @Test fun celebratesOnceWhenTheDayTurnsDone() {
        val done = Streak(current = 4, today = "done")
        assertTrue(shouldCelebrate(false, false, done, null, "2026-10-04")) // seen pending in this session, now done
        assertTrue(shouldCelebrate(null, true, done, "2026-10-03", "2026-10-04")) // first load is right after a test
        assertFalse(shouldCelebrate(null, false, done, null, "2026-10-04")) // opened the app after practising elsewhere
        assertFalse(shouldCelebrate(true, true, done, null, "2026-10-04")) // already done
        assertFalse(shouldCelebrate(false, true, done, "2026-10-04", "2026-10-04")) // shown today
        assertFalse(shouldCelebrate(false, true, done.copy(today = "pending"), null, "2026-10-04"))
    }

    @Test fun demoFixtureDecodes() {
        val s = AppJson.decodeFromString(Streak.serializer(), DemoToday.streak())
        assertEquals(182, s.days.size)
        assertEquals("2026-10-04", localToday(s))
        assertEquals(7, s.week.size)
        assertEquals("frozen", s.week[2].state)
        assertTrue(s.done && s.current == 12 && s.practicedLast35 in 15..35)
        // Tolerant: unknown fields (the old `month`) and missing ones decode.
        val old = AppJson.decodeFromString(Streak.serializer(), """{"current":3,"month":[{"day":"2026-10-01","level":1}],"repair":null}""")
        assertEquals(3, old.current)
        assertNull(old.repair)
        assertTrue(old.days.isEmpty())
    }
}
