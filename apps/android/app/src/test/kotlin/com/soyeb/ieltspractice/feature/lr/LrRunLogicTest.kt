package com.soyeb.ieltspractice.feature.lr

import com.soyeb.ieltspractice.data.model.LrGroup
import com.soyeb.ieltspractice.data.model.LrQuestion
import com.soyeb.ieltspractice.data.net.AppJson
import com.soyeb.ieltspractice.data.model.LrTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The runner's pure logic: highlights and notes, display settings, the clocks. Ported from the old app's LrLogicTest. */
class LrRunLogicTest {
    @Test fun reviewTimeFollowsTheRecording() {
        // demo part lengths 412, 405, 420, 398 s: the checking time ends checkEndsAt seconds into the last part
        val parts = listOf(412.0, 405.0, 420.0, 398.0)
        assertEquals(600.0, examReviewSeconds(parts, 398.0 + 600)) // "ten minutes to transfer"
        assertEquals(0.0, examReviewSeconds(parts, 380.0)) // the minute is already in the file
        assertEquals(120.0, examReviewSeconds(parts, null)) // nothing announced: computer-delivered 2 minutes
        assertEquals(120.0, examReviewSeconds(null, 500.0)) // lengths not known yet
        assertEquals(600.0, ExamPlaylist(emptyList(), 0.0, ExamPhase.Review, checkEndsAt = 398.0 + 600).reviewSeconds)
        assertEquals(998.0, AppJson.decodeFromString(LrTest.serializer(), """{"skill":"listening","title":"T","checkEndsAt":998}""").checkEndsAt)
    }

    @Test fun plainHighlightsMergeAndNotesNeverDo() {
        val r = "passage:1:0"
        var m = withHighlight(emptyList(), r, 2, 6, "a")
        m = withHighlight(m, r, 5, 9, "b") // overlaps: one mark 2..9
        assertEquals(listOf(2 to 9), m.map { it.s to it.e })
        m = withHighlight(m, r, 20, 25, "c")
        assertEquals(2, m.size)
        m = withNote(m, r, 3, 8, "why", "n") // absorbs the plain highlight under it, keeps the other
        assertEquals(2, m.size)
        assertEquals("why", m.first { it.note != null }.note)
        assertEquals(m, withHighlight(m, r, 4, 6, "d")) // over a note: unchanged
        m = withNote(m, r, 4, 6, "edited", "e") // over a note: edits it
        assertEquals(listOf("edited"), m.mapNotNull { it.note })
        m = withNoteText(m, "n", "  ") // clearing the note leaves a plain highlight
        assertTrue(m.all { it.note == null })
        assertEquals(1, withoutMark(m, "n").size)
        assertEquals(500, withNoteText(m, "n", "x".repeat(900)).first { it.id == "n" }.note!!.length)
    }

    @Test fun marksRoundTripAsTheSharedJsonShape() {
        val m = listOf(TextMark("a", "q:14:text", 0, 1, 5, "n"), TextMark("b", "passage:2:3", 3, 0, 4))
        assertEquals(m, parseMarks(marksJson(m)))
        assertEquals(emptyList(), parseMarks("not json"))
        assertEquals(emptyList(), parseMarks(null))
        val web = parseMarks("""[{"id":"x","region":"passage:1:0","p":0,"s":2,"e":9}]""")
        assertEquals(TextMark("x", "passage:1:0", 0, 2, 9), web.single())
        assertEquals("Passage 2, paragraph 4", markWhere(m[1]))
        assertEquals(14, markQuestion(m[0]))
        assertEquals(2, markPart(m[1]))
    }

    @Test fun selectionIsFoundInItsRegion() {
        val regions = linkedMapOf("passage:1:0" to "The quick brown fox", "q:3:text" to "Which animal is quick?")
        val one = locateSelection(regions, "brown").single()
        assertEquals(Triple("passage:1:0", 10, 15), Triple(one.region, one.s, one.e))
        val two = locateSelection(regions, "fox\nWhich animal")
        assertEquals(listOf("passage:1:0", "q:3:text"), two.map { it.region })
        assertTrue(locateSelection(regions, "absent").isEmpty())
    }

    @Test fun settingsDefaultsAndScale() {
        assertEquals(LrSettings(), parseSettings(null))
        assertEquals(LrSettings(), parseSettings("""{"size":"huge","scheme":"neon"}"""))
        val s = LrSettings("xl", "yb")
        assertEquals(s, parseSettings(settingsJson(s)))
        assertEquals(listOf(1f, 1.25f, 1.5f), listOf("std", "lg", "xl").map { LrSettings(it).scale() })
    }

    @Test fun readingClockWarnsAtTenAndFiveMinutes() {
        assertEquals(listOf(0, 1, 1, 2, 2), listOf(601, 600, 301, 300, 0).map { readingTone(it, 3600) })
        assertEquals(listOf(true, true, false, true, false), listOf(600, 591, 590, 300, 250).map { readingPulse(it, 3600) })
        assertEquals(1, readingTone(450, 1200)) // one passage: 20 minutes, still above 10
        assertEquals(0, readingTone(100, 600)) // a limit of 10 minutes or less never warns
        assertEquals(false, readingPulse(300, 600))
    }

    @Test fun flagsRoundTrip() {
        assertEquals(setOf(3, 7, 12), parseFlags(flagsText(setOf(12, 3, 7))))
        assertEquals("3,7,12", flagsText(setOf(12, 3, 7)))
        assertEquals(emptySet(), parseFlags(null))
        assertEquals(setOf(1, 2), parseFlags("1, x, 2"))
    }

    @Test fun promptsComeFromTheQuestionOrItsRowOfTheContent() {
        val g = LrGroup(from = 7, to = 8, type = "gap", content = "Intro\n\n| Day | Time |\n|---|---|\n| Monday | {{7}} |\n- Opens at {{8}} sharp", questions = listOf(LrQuestion(7), LrQuestion(8)))
        assertEquals("Monday ____", questionPrompt(g, g.questions[0]))
        assertEquals("Opens at ____ sharp", questionPrompt(g, g.questions[1]))
        val t = LrGroup(from = 1, to = 1, type = "tfng", questions = listOf(LrQuestion(1, "Beavers were hunted.")))
        assertEquals("Beavers were hunted.", questionPrompt(t, t.questions[0]))
        assertEquals("Question 9", questionPrompt(LrGroup(from = 9, to = 9, type = "gap"), LrQuestion(9)))
        assertEquals("Do the statements agree?", cleanInstructions("Questions 1-6. Do the statements agree?"))
        assertEquals(listOf("YES", "NO", "NOT GIVEN"), trioKeys("ynng"))
        assertEquals("Not given", trioLabel("NOT GIVEN"))
    }

    @Test fun spellingIgnoresCaseAndSpaces() {
        assertTrue(spellingRight("  Sediment ", "sediment"))
        assertTrue(!spellingRight("sedement", "sediment"))
    }
}
