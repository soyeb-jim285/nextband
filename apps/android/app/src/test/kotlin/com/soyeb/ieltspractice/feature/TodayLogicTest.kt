package com.soyeb.ieltspractice.feature

import com.soyeb.ieltspractice.data.model.Predicted
import com.soyeb.ieltspractice.data.model.ProgressData
import com.soyeb.ieltspractice.data.model.TrendPoint
import com.soyeb.ieltspractice.data.model.Weakest
import com.soyeb.ieltspractice.design.Skill
import com.soyeb.ieltspractice.feature.today.NextAct
import com.soyeb.ieltspractice.feature.today.daysToExam
import com.soyeb.ieltspractice.feature.today.examChipText
import com.soyeb.ieltspractice.feature.today.greeting
import com.soyeb.ieltspractice.feature.today.pickUpNext
import com.soyeb.ieltspractice.feature.today.skillBands
import com.soyeb.ieltspractice.feature.today.subtitle
import java.time.Instant
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TodayLogicTest {
    private fun progress(weakest: Weakest? = null, predicted: Predicted = Predicted()) =
        ProgressData(emptyList(), 0, 0.0, 0, weakest = weakest, predicted = predicted)

    @Test fun greetingFollowsTheHour() {
        assertEquals("Hello, Maya", greeting(3, "Maya Rahman"))
        assertEquals("Good morning, Maya", greeting(9, "Maya Rahman"))
        assertEquals("Good afternoon", greeting(14, " "))
        assertEquals("Good evening, Maya", greeting(20, "Maya"))
    }

    @Test fun subtitleSkipsEmptyParts() {
        assertEquals("5 day streak · 96 min this week", subtitle(5, 96.0))
        assertEquals("12 min this week", subtitle(0, 12.4))
        assertNull(subtitle(0, 0.0))
    }

    @Test fun examCountdown() {
        val now = Instant.parse("2026-10-03T18:00:00Z")
        val dhaka = ZoneId.of("Asia/Dhaka") // already 4 Oct there
        assertEquals(23, daysToExam("2026-10-27", now, dhaka))
        assertEquals(0, daysToExam("2026-10-04", now, dhaka))
        assertNull(daysToExam("2026-10-01", now, dhaka))
        assertNull(daysToExam(null, now, dhaka))
        assertNull(daysToExam("soon", now, dhaka))
        assertEquals("1 day to your exam", examChipText(1))
        assertEquals("23 days to your exam", examChipText(23))
    }

    @Test fun overallNeedsTwoSkills() {
        val one = skillBands(progress(predicted = Predicted(speaking = 6.5)), null)
        assertNull(one.overall)
        val two = skillBands(progress(predicted = Predicted(speaking = 6.5, writing = 7.0)), null)
        assertEquals(7.0, two.overall) // 6.75 rounds up to 7
        assertEquals(2, two.count)
        assertEquals(6.5, two.of(Skill.Speaking))
    }

    @Test fun trendAveragesTheLastFive() {
        val trend = (1..7).map { TrendPoint("2026-09-0$it", it.toDouble(), skill = "writing") }
        val b = skillBands(ProgressData(trend, 0, 0.0, 7), null)
        assertEquals(5.0, b.writing) // mean of 3..7
    }

    @Test fun upNextFallsBackToTheWeakestCriterion() {
        val n = pickUpNext(null, progress(Weakest("p", 5.9)), null, 7.0)
        assertTrue(n.title.startsWith("Pronunciation"))
        assertEquals(NextAct.SpeakPart(1), n.act)
        val d = pickUpNext(null, progress(), null, 7.0)
        assertEquals(NextAct.SpeakFull, d.act)
    }
}
