package com.soyeb.ieltspractice.feature.study

import com.soyeb.ieltspractice.data.model.DueCard
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StudyLogicTest {
    private fun card(front: String = "f", back: String = "b", source: String? = null, ease: Double? = null, interval: Double? = null, reps: Int? = null, category: String? = null) =
        DueCard("c", front, back, source, ease, interval, reps, category)

    @Test fun againAlwaysComesBackNextDay() {
        assertEquals(1, nextInterval(card(reps = 5, interval = 30.0), 1))
        assertEquals("tomorrow", gotItHint(card(reps = 0)))
        assertEquals("in 6 days", gotItHint(card(reps = 1)))
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
        assertEquals("Fix the tense" to "I go yesterday.", promptOf(card(front = "Fix the tense\n\nI go yesterday.", source = "fix")))
        assertEquals(null to "a\n\nb", promptOf(card(front = "a\n\nb", source = "mistake")))
        assertEquals(null to "in the society", promptOf(card(front = "Fix: \"in the society\"", source = "mistake")))
    }

    @Test fun spellingCardsShowTheirHint() {
        val spell = card(front = "🎧 Listening · spell the word you heard: 's______t' (8 letters)", back = "sediment\nYou wrote: sedement", category = "spelling")
        assertEquals("Listening · spell the word you heard" to "s______t (8 letters)", promptOf(spell))
        assertEquals("sediment" to "You wrote: sedement", answerOf(spell.back))
        assertTrue(isHeard(spell))
        // older cards: the hint on a second line
        assertEquals("Listening · spell the word you heard" to "p__k\nHint: 4 letters", promptOf(card(front = "🎧 Listening · spell the word you heard: 'p__k'\nHint: 4 letters")))
        assertFalse(isHeard(card(front = "plain", category = "spelling")))
    }

    @Test fun answersSplitFromTheirReason() {
        assertEquals("do research" to "Uncountable.", answerOf("do research — Uncountable."))
        assertEquals("do research" to "Uncountable.", answerOf("do research\n\nUncountable."))
        assertEquals("do research" to "", answerOf(" do research "))
    }

    @Test fun answersMatchIgnoringCaseAndPunctuation() {
        assertTrue(sameAnswer("I have lived there since 2015", "I have lived there since 2015."))
        assertTrue(sameAnswer("  i HAVE lived  there since 2015 ", "I have lived there since 2015"))
        assertFalse(sameAnswer("I lived there since 2015", "I have lived there since 2015"))
    }

    @Test fun diffShowsWhatToRemoveAndAdd() {
        assertEquals(
            listOf(Change.Same to "I", Change.Gone to "living", Change.Added to "have", Change.Added to "lived", Change.Same to "there"),
            wordDiff("I living there", "I have lived there"),
        )
        assertEquals(listOf(Change.Added to "word"), wordDiff("", "word"))
    }

    @Test fun recurringAndTrendLinesRead() {
        assertEquals("also in your last test", seenLine(1, 1))
        assertEquals("also in all of your last 4 tests", seenLine(4, 4))
        assertEquals("also in 2 of your last 5 tests", seenLine(2, 5))
        assertEquals("Down to 1.3 a test from 3", trendLine(1.3, 3.0))
        assertNull(trendLine(0.0, 0.0))
        assertNull(trendLine(null, 2.0))
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
