package com.soyeb.ieltspractice.data.logic

import com.soyeb.ieltspractice.data.model.AttemptListItem
import com.soyeb.ieltspractice.data.model.Mock
import com.soyeb.ieltspractice.data.model.MockCambridge
import com.soyeb.ieltspractice.data.model.MockOptionsQuota
import com.soyeb.ieltspractice.data.model.MockSection
import com.soyeb.ieltspractice.data.model.SkillQuota
import java.time.Instant
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Mock hub, start and result helpers. */
class MockSessionTest {
    private fun h(id: String, session: String?, at: String) = AttemptListItem(id, "t", "writing", 1, "full", "done", 6.5, at, sessionId = session)

    @Test fun aSessionOpensEveryAttemptInTheOrderTheyWereTaken() {
        val items = listOf(h("w2", "s1", "2026-10-02T10:30:00Z"), h("x", "other", "2026-10-02T10:20:00Z"), h("w1", "s1", "2026-10-02T10:00:00Z"))
        assertEquals(listOf("w1", "w2"), sessionAttemptIds(items, "s1", "w1"))
        assertEquals(listOf("w1"), sessionAttemptIds(items, "gone", "w1"))
        assertEquals(listOf("w1"), sessionAttemptIds(items, null, "w1"))
    }

    @Test fun pickingACambridgeTest() {
        val list = listOf(MockCambridge("C17 T1", started = true), MockCambridge("C18 T2"), MockCambridge("C19 T1"))
        assertEquals("C18 T2", MockPick.defaultRef(list))
        assertEquals("C17 T1", MockPick.defaultRef(listOf(MockCambridge("C17 T1", started = true))))
        assertNull(MockPick.defaultRef(emptyList()))
        assertTrue(MockPick.surprise(list, Random(0)) in setOf("C18 T2", "C19 T1"))
        assertNull(MockPick.surprise(emptyList()))
    }

    @Test fun mocksLeftIsTheTighterAllowance() {
        fun q(w: Int?, s: Int?) = MockOptionsQuota(SkillQuota(limit = w?.let { 3 }, remaining = w, window = "day"), SkillQuota(limit = s?.let { 3 }, remaining = s, window = "day"))
        assertEquals(1, MockPick.left(q(2, 1)))
        assertEquals(2, MockPick.left(q(2, null)))
        assertNull(MockPick.left(q(null, null)))
        assertNull(MockPick.left(null))
        assertEquals("1 mock left today", MockPick.leftText(q(2, 1)))
        assertEquals("2 mocks left today", MockPick.leftText(q(2, null)))
        assertEquals("No mocks left today", MockPick.leftText(q(0, 1)))
        assertNull(MockPick.leftText(q(null, null)))
    }

    @Test fun theWeakestSectionNeedsABand() {
        val m = Mock("m", sections = listOf(MockSection("listening", band = 7.0), MockSection("reading", band = 6.0), MockSection("writing", band = 6.0), MockSection("speaking")))
        assertEquals("reading", MockFlow.weakest(m)?.skill)
        assertNull(MockFlow.weakest(Mock("m", sections = listOf(MockSection("listening")))))
    }

    @Test fun daysLeftRoundsUp() {
        val now = Instant.parse("2026-10-01T09:00:00Z")
        assertEquals(7, MockFlow.daysLeft("2026-10-08T09:00:00Z", now))
        assertEquals(1, MockFlow.daysLeft("2026-10-01T20:00:00Z", now))
        assertEquals(0, MockFlow.daysLeft("2026-09-30T09:00:00Z", now))
        assertNull(MockFlow.daysLeft("nope", now))
    }

    private fun mock(vararg states: Pair<String, Double?>, next: String?, status: String = "in_progress") = Mock(
        "m1", status = status, next = next,
        sections = MockFlow.skills.zip(states.toList()).map { (k, s) -> MockSection(k, s.first, s.second) },
    )

    @Test fun theHubCardNamesWhatIsDoneAndWhatIsNext() {
        val fresh = mock("todo" to null, "todo" to null, "todo" to null, "todo" to null, next = "listening")
        assertEquals("Ready when you are", MockFlow.hubCopy(fresh, 7.0).overline)
        assertEquals("Start Listening", MockFlow.startLabel(fresh))
        val between = mock("done" to 7.0, "todo" to null, "todo" to null, "todo" to null, next = "reading")
        val c = MockFlow.hubCopy(between, 7.0)
        assertEquals("Listening done", c.title)
        assertEquals("Reading is next · 60 minutes", c.detail)
        assertEquals("Start Reading", MockFlow.startLabel(between))
        val under = mock("done" to 7.0, "in_progress" to null, "todo" to null, "todo" to null, next = "reading")
        assertEquals("Continue Reading", MockFlow.startLabel(under))
        val speaking = mock("done" to 7.0, "done" to 7.0, "done" to 6.5, "todo" to null, next = "speaking")
        assertEquals("Choose how to do Speaking", MockFlow.startLabel(speaking))
        val done = mock("done" to 7.0, "done" to 7.0, "done" to 6.5, "done" to 6.0, next = null, status = "completed")
        assertNull(MockFlow.startLabel(done))
        assertEquals("Mock finished", MockFlow.hubCopy(done, 7.0).overline)
    }

    @Test fun stepperSubtitlesShowLengthThenState() {
        assertEquals("60 minutes", MockFlow.rowSubtitle(MockSection("reading")))
        assertEquals("About 30 minutes plus a 2-minute check", MockFlow.rowSubtitle(MockSection("listening")))
        assertEquals("In progress · 10 min used of 60", MockFlow.rowSubtitle(MockSection("reading", "in_progress", elapsedS = 600.0, limitS = 3600.0)))
        assertEquals("Marked", MockFlow.rowSubtitle(MockSection("listening", "done", 7.0)))
        assertEquals("Skipped", MockFlow.rowSubtitle(MockSection("speaking", "skipped")))
    }
}
