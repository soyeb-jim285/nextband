package com.soyeb.ieltspractice.feature.writing

import com.soyeb.ieltspractice.data.demo.DemoFixtures
import com.soyeb.ieltspractice.data.logic.minWords
import com.soyeb.ieltspractice.data.logic.taskLabel
import com.soyeb.ieltspractice.data.model.ChartSpec
import com.soyeb.ieltspractice.data.model.Prompt
import com.soyeb.ieltspractice.data.model.Series
import com.soyeb.ieltspractice.data.net.AppJson
import com.soyeb.ieltspractice.nav.WritingTask
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Plain JVM tests of the writing rules (word count, clock, warnings, submit floor, paste, drafts) and the chart maths. */
class WritingLogicTest {
    private fun prompt(part: Int, variant: String? = null) = Prompt(id = "p$part", skill = "writing", part = part, variant = variant, title = "t", body = "b")

    @Test fun wordCountMatchesCore() {
        assertEquals(3, wordCount("75% in 2017")) // numbers count
        assertEquals(8, wordCount("It cost \$20 - a rise of 5.5 points.")) // a lone dash does not
        assertEquals(0, wordCount("  \n - ... "))
        assertEquals(4, wordCount("one\ntwo\tthree   four"))
    }

    @Test fun minimumsAndClock() {
        assertEquals(150, minWords(1)); assertEquals(250, minWords(2))
        assertEquals(3600, examSeconds(listOf(prompt(1), prompt(2))))
        assertEquals(1200, examSeconds(listOf(prompt(1)))); assertEquals(2400, examSeconds(listOf(prompt(2))))
        assertEquals("Task 1 Academic", taskLabel(prompt(1, "academic"))); assertEquals("Task 1 General", taskLabel(prompt(1, "general")))
        assertEquals("Task 2", taskLabel(prompt(2)))
        // a wall-clock deadline: 10 s in, 1190 s left; a part-second rounds up; negative once overtime
        assertEquals(1190, secondsLeft(1200, 1_000, 11_000)); assertEquals(1191, secondsLeft(1200, 1_000, 10_001))
        assertEquals(0, secondsLeft(1200, 0, 1_200_000)); assertEquals(-5, secondsLeft(1200, 0, 1_205_000))
    }

    @Test fun warningsFireOnceAsTheClockPassesThem() {
        assertNull(timeEvent(400, 399))
        assertEquals(TimeEvent.Warn5, timeEvent(301, 300)); assertNull(timeEvent(300, 299))
        assertEquals(TimeEvent.Warn1, timeEvent(61, 60)); assertNull(timeEvent(60, 59))
        assertEquals(TimeEvent.TimeUp, timeEvent(1, 0)); assertNull(timeEvent(0, -1))
        assertEquals(TimeEvent.Warn5, timeEvent(500, 200)) // a jump past 5 minutes still warns
        assertEquals(TimeEvent.TimeUp, timeEvent(30, -5))
    }

    @Test fun wordBarStates() {
        assertEquals(WordBar(false, false, "Minimum 250 words", 0f), wordBar(0, 250))
        assertFalse(wordBar(224, 250).near); assertTrue(wordBar(225, 250).near) // 90%
        assertEquals("30 more to reach 250", wordBar(220, 250).hint)
        assertEquals(WordBar(true, false, "250-word minimum reached", 1f), wordBar(300, 250))
    }

    @Test fun submitConfirmation() {
        val ok = listOf(TaskCount(2, 260))
        assertNull(submitNote(ok, 100)); assertFalse(tooShort(ok)); assertNull(underMinimum(ok))
        assertEquals("Write at least a paragraph before submitting.", submitNote(listOf(TaskCount(2, SUBMIT_FLOOR - 1)), 100))
        assertFalse(tooShort(listOf(TaskCount(2, SUBMIT_FLOOR))))
        assertEquals("Write at least a paragraph for each task before submitting.", submitNote(listOf(TaskCount(1, 160), TaskCount(2, 5)), 100))
        assertEquals("Under 250 words costs Task Response marks. You have 2:05 left to add more.", submitNote(listOf(TaskCount(2, 100)), 125))
        assertEquals("Under 150 words costs Task Achievement marks.", submitNote(listOf(TaskCount(1, 100), TaskCount(2, 300)), 0))
        assertEquals(1, underMinimum(listOf(TaskCount(1, 100), TaskCount(2, 100)))?.part)
    }

    @Test fun pasteIsAnEditThatInsertsALot() {
        assertEquals(1, insertedLength("hello", "hellos")); assertEquals(0, insertedLength("hello", "hello"))
        assertEquals(0, insertedLength("hello world", "hell world")) // a deletion inserts nothing
        assertEquals(8, insertedLength("a b", "a 1234567 b")) // "1234567 "
        val clip = "x".repeat(PASTE_CHARS)
        assertTrue(looksPasted("start end", "start $clip end"))
        assertTrue(looksPasted("", clip))
        assertFalse(looksPasted("start end", "start ${"x".repeat(PASTE_CHARS - 3)} end")) // a long word
        assertFalse(looksPasted("a long essay so far.", "a long essay so far. Then")) // typing and word suggestions are small
        assertTrue(looksPasted("a".repeat(100), "b".repeat(100))) // replacing a selection with pasted text
    }

    @Test fun draftsRoundTripAndReadOlderPlainText() {
        assertEquals(Draft("essay", "plan"), decodeDraft(encodeDraft(Draft("essay", "plan"))))
        assertEquals(Draft(), decodeDraft(null))
        assertEquals(Draft(text = "just the essay"), decodeDraft("just the essay")) // a plain-text draft is the essay
        assertEquals(Draft(text = "e"), decodeDraft("""{"text":"e"}"""))
        assertEquals("""{"text":"a\nb","plan":""}""", encodeDraft(Draft("a\nb")))
    }

    @Test fun draftIndexRoundTripsAndToleratesGarbage() {
        val items = listOf(DraftMeta("w2", 2, "Remote work", null, 120, 5L), DraftMeta("w1a", 1, "Energy", "academic", 40, 4L))
        assertEquals(items, decodeIndex(encodeIndex(items)))
        assertEquals(emptyList<DraftMeta>(), decodeIndex(null)); assertEquals(emptyList<DraftMeta>(), decodeIndex("not json"))
    }

    @Test fun aFullTestDraftResumesBothTasksTask1First() {
        assertEquals(WritingTask("task2", null, "w2"), resumeRoute(DraftMeta("w2", 2, "Remote work")))
        assertEquals(WritingTask("full", "academic", "w1a", "w2"), resumeRoute(DraftMeta("w2", 2, "Remote work", "academic", pairId = "w1a")))
        assertEquals(WritingTask("full", "academic", "w1a", "w2"), resumeRoute(DraftMeta("w1a", 1, "Energy", "academic", pairId = "w2")))
        val pair = listOf(DraftMeta("w1a", 1, "Energy", pairId = "w2"))
        assertEquals(pair, decodeIndex(encodeIndex(pair)))
    }

    @Test fun agoReadsLikeAPerson() {
        val now = 10_000_000_000L
        assertEquals("just now", ago(now - 20_000, now)); assertEquals("5 min ago", ago(now - 5 * 60_000, now))
        assertEquals("3 h ago", ago(now - 3 * 3_600_000, now)); assertEquals("2 d ago", ago(now - 2 * 86_400_000, now))
        assertEquals("just now", ago(now + 1000, now)) // clock skew
    }

    @Test fun sessionSurvivesSaving() {
        val chart = AppJson.parseToJsonElement("""{"kind":"table","title":"T","columns":["a"],"rows":[["x"]]}""")
        val s = ExamSession(listOf(prompt(1, "academic").copy(chart = chart)), 123L, "sid", "parent")
        val back = AppJson.decodeFromString(ExamSession.serializer(), AppJson.encodeToString(ExamSession.serializer(), s))
        assertEquals(s, back)
        assertEquals("table", back.prompts[0].chartSpec?.kind)
    }

    @Test fun titleAndResultHelpers() {
        val p = prompt(1).copy(title = "The graph below shows the percentage…", body = "The graph below shows the percentage of electricity.")
        assertFalse(showPromptTitle(p, null)) // seeded title = the body's first words
        assertFalse(showPromptTitle(p.copy(title = "Renewables", body = "x"), "Renewables")) // the figure repeats it
        assertTrue(showPromptTitle(p.copy(title = "Renewables", body = "x"), "Other"))
        assertEquals("37 min", minutesText(2_220_000)); assertEquals("1 min", minutesText(10_000)); assertNull(minutesText(null)); assertNull(minutesText(0))
        assertEquals("Dear Sir or Madam,", letterOpening(prompt(1, "general").copy(type = "letter-formal")))
        assertNull(letterOpening(prompt(1, "academic")))
    }

    @Test fun sentenceMixBucketsByLength() {
        val text = "Short one. This sentence has exactly eleven words in it, so it is medium length. " +
            "This one keeps going and going with many many extra words so that it ends up well past twenty words in total length.\nA tiny one!"
        assertEquals(SentenceMix(short = 2, medium = 1, long = 1), sentenceMix(text))
        assertEquals(SentenceMix(0, 0, 0), sentenceMix("  "))
    }

    @Test fun niceTicksMatchWeb() {
        assertEquals(listOf(0.0, 20.0, 40.0, 60.0, 80.0, 100.0), niceTicks(0.0, 87.0))
        assertEquals(listOf(0.0, 1.0, 2.0, 3.0, 4.0, 5.0), niceTicks(0.0, 4.3))
        assertEquals(listOf(0.0, 250.0, 500.0, 750.0, 1000.0, 1250.0), niceTicks(0.0, 1200.0))
        assertEquals(listOf(-20.0, -10.0, 0.0, 10.0, 20.0, 30.0), niceTicks(-12.0, 30.0))
        assertEquals(listOf(0.0, 0.2, 0.4, 0.6, 0.8, 1.0), niceTicks(0.0, 0.0))
    }

    @Test fun chartLabelsAndSummary() {
        val line = ChartSpec(kind = "line", yLabel = "Share", unit = "%", categories = listOf("2000", "2005"), series = listOf(Series("DK", listOf(15.0, 22.5))))
        assertEquals("Share (%)", yAxisTitle(line)); assertEquals("%", yAxisTitle(line.copy(yLabel = "%")))
        assertEquals("%", yAxisTitle(line.copy(yLabel = ""))); assertEquals("Share", yAxisTitle(line.copy(unit = "")))
        assertEquals("line chart. DK: 2000 15, 2005 22.5 (%)", describe(line))
        assertEquals("process, 2 steps. 1, a. 2, b", describe(ChartSpec(kind = "process", steps = listOf("a", "b"))))
    }

    @Test fun everyWritingFixtureDrawsFromAKnownChartKind() {
        val fixtures = DemoFixtures.parse(File("../../ios/IELTS/Demo/fixtures.json").readText())
        val prompts = fixtures.filterKeys { it != "/api/prompts/meta" && Regex("^/api/prompts/[a-z0-9]+$").matches(it) }.values.map { AppJson.decodeFromString(Prompt.serializer(), it) }.filter { it.skill == "writing" }
        assertTrue(prompts.size >= 8, "expected the writing prompt fixtures, found ${prompts.map { it.id }}")
        val kinds = prompts.mapNotNull { it.chartSpec?.kind }.toSet()
        assertEquals(setOf("line", "bar", "pie", "table", "process", "map"), kinds)
        prompts.filter { it.part == 1 && it.variant == "academic" }.forEach { assertNotNull(it.chartSpec, "${it.id} should decode a chart") }
    }
}
