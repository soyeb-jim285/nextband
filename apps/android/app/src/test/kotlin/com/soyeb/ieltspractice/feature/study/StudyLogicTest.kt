package com.soyeb.ieltspractice.feature.study

import com.soyeb.ieltspractice.data.model.DueCard
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StudyLogicTest {
    private fun card(front: String = "f", back: String = "b", source: String? = null, ease: Double? = null, interval: Double? = null, reps: Int? = null) =
        DueCard("c", front, back, source, ease, interval, reps)

    @Test fun againAlwaysComesBackNextDay() {
        assertEquals(1, nextInterval(card(reps = 5, interval = 30.0), 1))
        assertEquals("This session", hint(card(), 1))
    }

    @Test fun firstAndSecondRepetitionsAreFixed() {
        assertEquals(1, nextInterval(card(reps = 0), 4))
        assertEquals(6, nextInterval(card(reps = 1), 4))
    }

    @Test fun laterRepetitionsScaleByEase() {
        // grade 4: ease 2.5 + (0.1 - 1 * (0.08 + 0.02)) = 2.5
        assertEquals(25, nextInterval(card(reps = 2, interval = 10.0, ease = 2.5), 4))
        // easy raises it, hard lowers it
        assertEquals(26, nextInterval(card(reps = 2, interval = 10.0, ease = 2.5), 5))
        assertEquals(24, nextInterval(card(reps = 2, interval = 10.0, ease = 2.5), 3))
    }

    @Test fun daysReadNaturally() {
        assertEquals("1 day", daysLabel(1))
        assertEquals("6 days", daysLabel(6))
        assertEquals("2 mo", daysLabel(60))
    }

    @Test fun fixCardsSplitTheirTitle() {
        assertEquals("Fix the tense" to "I go yesterday.", splitFixCard(card(front = "Fix the tense\n\nI go yesterday.", source = "fix")))
        assertEquals(null to "a\n\nb", splitFixCard(card(front = "a\n\nb", source = "mistake")))
    }

    @Test fun spellingCardsKeepTheWordOnTheBack() {
        val spell = card(front = "🎧 Listening · spell the word you heard", back = "sediment\nrock layers")
        assertEquals("sediment", heardWord(spell))
        assertEquals("Listening · spell the word you heard", cleanFront(spell).second)
        assertNull(heardWord(card(front = "plain")))
    }

    @Test fun neighboursWithTheSameKeyShareARun() {
        assertEquals(listOf(listOf(1, 1), listOf(2), listOf(1)), runsBy(listOf(1, 1, 2, 1)) { it })
        assertEquals(emptyList(), runsBy(emptyList<Int>()) { it })
    }

    @Test fun skillLinesSayWhereTheTrendGoes() {
        assertEquals("4 tests · Improving +0.5", skillLine(4, "improving", 0.5, 0))
        assertEquals("2 tests · Slipping -0.5", skillLine(2, "slipping", -0.5, 0))
        assertEquals("1 test · Needs 2 more tests", skillLine(1, "calibrating", null, 2))
        assertEquals("3 tests · Steady", skillLine(3, "steady", null, 0))
    }
}
