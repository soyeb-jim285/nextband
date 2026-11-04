package com.soyeb.ieltspractice.feature.speaking

import com.soyeb.ieltspractice.data.demo.DemoFixtures
import com.soyeb.ieltspractice.data.logic.sentenceCase
import com.soyeb.ieltspractice.data.model.AttemptListItem
import com.soyeb.ieltspractice.data.model.Pronunciation
import com.soyeb.ieltspractice.data.model.Prompt
import com.soyeb.ieltspractice.data.net.AppJson
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Pure speaking-flow helpers: ring zones, labels, cue-card text, upload progress copy, recent-row folding, pronunciation lookups. */
class SpeakingLogicTest {
    private fun prompt(part: Int, title: String = "T", body: String = "", bullets: List<String>? = null) =
        Prompt(id = "p$part", skill = "speaking", part = part, title = title, body = body, bullets = bullets)

    private fun item(id: String, part: Int, overall: Double?, at: String, session: String? = null) =
        AttemptListItem(id, "Title $id", "speaking", part, "practice", "done", overall, at, sessionId = session)

    @Test fun zonesFollowThePartTargets() {
        assertEquals(Zone.Brand, zoneTone(1, 10.0))
        assertEquals(Zone.Good, zoneTone(1, 15.0))
        assertEquals(Zone.Good, zoneTone(1, 40.0))
        assertEquals(Zone.Warn, zoneTone(1, 41.0))
        assertEquals(Zone.Brand, zoneTone(2, 59.0))
        assertEquals(Zone.Warn, zoneTone(2, 75.0)) // P2 is only green from 1:30
        assertEquals(Zone.Good, zoneTone(2, 100.0))
        assertEquals(Zone.Warn, zoneTone(2, 121.0))
        assertEquals(Zone.Good, zoneTone(3, 45.0))
        assertEquals(Zone.Warn, zoneTone(3, 61.0))
    }

    @Test fun hintsMatchTheZones() {
        assertEquals("Aim for 15-40 s", zoneHint(1, 3))
        assertEquals("In the target zone", zoneHint(3, 45))
        assertEquals("Time to wrap up", zoneHint(3, 70))
        assertEquals("Good. Keep going to 1:30 or more", zoneHint(2, 75))
        assertEquals("In the target zone", zoneHint(2, 100))
    }

    @Test fun aResumedMockSkipsAnsweredQuestions() {
        val items = listOf(prompt(1).copy(id = "a"), prompt(1).copy(id = "b"), prompt(2), prompt(3))
        assertEquals(0, resumeIndex(items, emptySet()))
        assertEquals(2, resumeIndex(items, setOf("a", "b")))
        assertEquals(-1, resumeIndex(items, setOf("a", "b", "p2", "p3"))) // nothing left: straight to the upload screen, never Part 1 again
    }

    @Test fun recordingAttemptsAreNotAnswered() {
        fun a(prompt: String, status: String, sid: String? = "s") = AttemptListItem("id-$prompt", "t", "speaking", 1, "part1", status, createdAt = "", promptId = prompt, sessionId = sid)
        val list = listOf(a("a", "done"), a("b", "analyzing"), a("c", "recording"), a("d", "done", sid = "other"), a("e", "failed"))
        assertEquals(setOf("a", "b", "e"), answeredOnServer(list, "s"))
    }

    @Test fun partLabelsCountPart1Topics() {
        val items = listOf(prompt(1), prompt(1), prompt(2), prompt(3))
        assertEquals("Part 1, 1 of 2", partLabel(items, 0))
        assertEquals("Part 1, 2 of 2", partLabel(items, 1))
        assertEquals("Part 2", partLabel(items, 2))
        assertEquals("Speaking", partLabel(items, 9))
        assertEquals("Part 1", partLabel(listOf(prompt(1), prompt(2)), 0))
    }

    @Test fun exitMessageListsWhatIsAtRisk() {
        assertEquals("Answers already uploaded are still analysed.", exitMessage(false, false))
        assertEquals(
            "The answer you are recording now will be discarded. Recordings that have not uploaded stay on this device. Upload them from the Speaking page. Answers already uploaded are still analysed.",
            exitMessage(true, true),
        )
    }

    @Test fun uploadProgressCopy() {
        assertEquals("Uploading 1 of 3", finishProgress(3, 0, 0))
        assertEquals("Uploading 3 of 3", finishProgress(3, 2, 0))
        assertEquals("3 of 3 uploaded", finishProgress(3, 3, 0))
        assertEquals("1 of 3 uploaded, 1 failed", finishProgress(3, 1, 1))
    }

    @Test fun cueIntroDropsTheTitleAndTheLeadWhenThereAreBullets() {
        val p = prompt(2, "Describe a book", "Describe a book\nYou should say:", listOf("what it was"))
        assertEquals("", cueIntro(p))
        assertEquals("You should say:", cueIntro(p.copy(bullets = null)))
        assertEquals("Say why.", cueIntro(p.copy(body = "Describe a book\nSay why.\nYou should say:")))
    }

    @Test fun sentenceCaseOnlyFixesAllCaps() {
        assertEquals("Your hometown", sentenceCase("YOUR HOMETOWN"))
        assertEquals("Your Hometown", sentenceCase("Your Hometown"))
        assertEquals("A", sentenceCase("A"))
    }

    @Test fun relativeTimeReadsLikeTheWeb() {
        val now = 10_000_000_000L
        assertEquals("just now", relativeTime(now - 30_000, now))
        assertEquals("1 minute ago", relativeTime(now - 60_000, now))
        assertEquals("25 minutes ago", relativeTime(now - 25 * 60_000, now))
        assertEquals("3 hours ago", relativeTime(now - 3 * 3_600_000, now))
        assertEquals("2 days ago", relativeTime(now - 2 * 86_400_000, now))
    }

    @Test fun micCheckNeedsAboutASecondOfSpeech() {
        assertEquals(0, micCheckLevel(3, 5))
        assertEquals(1, micCheckLevel(3, 25))
        assertEquals(2, micCheckLevel(20, 0))
    }

    @Test fun recentFoldsASessionIntoOneRow() {
        val rows = groupRecent(
            listOf(
                item("a3", 3, 6.0, "2026-10-02T10:20:00Z", "s1"),
                item("a2", 2, 7.0, "2026-10-02T10:10:00Z", "s1"),
                item("a1", 1, 6.5, "2026-10-02T10:00:00Z", "s1"),
                item("b", 2, 5.5, "2026-10-01T10:00:00Z"),
                item("c", 1, null, "2026-09-30T10:00:00Z", "s2"),
            ),
        )
        assertEquals(listOf("Full test", "Title b", "Title c"), rows.map { it.title })
        assertEquals(listOf("a1", "a2", "a3"), rows[0].ids)
        assertEquals(6.5, rows[0].overall) // mean 6.5
        assertEquals(3, rows[0].parts)
        assertEquals(1, rows[2].parts)
    }

    @Test fun pronunciationTipAndDictionary() {
        val json = """{"unclear":[],"audio":{"accuracy":82.4,"words":[{"word":"three","score":40,"accent":"gb","start":1.2,"end":1.6,"phones":[{"expected":"θ","heard":"t","ok":false},{"expected":"ɹ","heard":"ɹ","ok":true}]}],"confusions":[{"expected":"θ","heard":"t","count":3}],"wordsPerMinute":null}}"""
        val p = AppJson.decodeFromString(Pronunciation.serializer(), json).audio!!
        assertEquals(true, pronTip(p.words[0])?.startsWith("Put your tongue"))
        val d = parseDictionary("""[{"phonetic":"/θɹiː/","phonetics":[{"text":"/θɹiː/","audio":""},{"audio":"https://x/three-uk.mp3"},{"audio":"https://x/three-us.mp3"}]}]""")
        assertEquals("https://x/three-uk.mp3", d.uk)
        assertEquals("https://x/three-us.mp3", d.us)
        assertEquals("/θɹiː/", d.ipa)
        assertEquals("https://x/a.mp3", parseDictionary("""[{"phonetics":[{"text":"/a/","audio":"https://x/a.mp3"}]}]""").us)
        assertNull(parseDictionary("""{"title":"No Definitions Found"}""").uk)
    }

    @Test fun speakingFixturesDecodeAsPrompts() {
        val fx = DemoFixtures.parse(File("../../ios/IELTS/Demo/fixtures.json").readText())
        val p2 = AppJson.decodeFromString(Prompt.serializer(), fx.getValue("/api/prompts/random?part=2&skill=speaking"))
        assertEquals(2, p2.part)
        assertEquals(listOf(p2.title), p2.questions)
        assertEquals("", cueIntro(p2))
        assertEquals("and explain how it changed the way you think.", cueClosing(p2))
        assertEquals("and explain how it changed the way you think.", cueIntro(p2.copy(bullets = null)))
        assertEquals("", cueClosing(p2.copy(bullets = null)))
    }
}
