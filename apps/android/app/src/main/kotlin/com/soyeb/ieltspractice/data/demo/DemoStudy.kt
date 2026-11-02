package com.soyeb.ieltspractice.data.demo

import com.soyeb.ieltspractice.data.model.Coach
import com.soyeb.ieltspractice.data.model.CoachCandidate
import com.soyeb.ieltspractice.data.model.CoachCheat
import com.soyeb.ieltspractice.data.model.CoachEvidence
import com.soyeb.ieltspractice.data.model.CoachFacts
import com.soyeb.ieltspractice.data.model.CoachFocus
import com.soyeb.ieltspractice.data.model.CoachOverall
import com.soyeb.ieltspractice.data.model.CoachPlanDay
import com.soyeb.ieltspractice.data.model.CoachReport
import com.soyeb.ieltspractice.data.model.CoachSkillFacts
import com.soyeb.ieltspractice.data.model.CoachWords
import com.soyeb.ieltspractice.data.model.CurrentMock
import com.soyeb.ieltspractice.data.model.DueCard
import com.soyeb.ieltspractice.data.model.DueResponse
import com.soyeb.ieltspractice.data.model.Mock
import com.soyeb.ieltspractice.data.model.MockCambridge
import com.soyeb.ieltspractice.data.model.MockOptions
import com.soyeb.ieltspractice.data.model.MockOptionsQuota
import com.soyeb.ieltspractice.data.model.MockSection
import com.soyeb.ieltspractice.data.model.SkillQuota
import com.soyeb.ieltspractice.data.net.AppJson
import kotlinx.serialization.json.JsonPrimitive

/**
 * Demo answers for the full mock and the study tools (review, coach) that the shared fixtures lack.
 * Variants come from the demo `tab` (see feature/mock/MockScreens.kt and feature/study/StudyScreens.kt).
 */
object DemoStudy {
    /** The one mock every `mock-*` screenshot opens; its state depends on the variant. */
    const val MOCK_ID = "mkdemo"

    fun apply(out: MutableMap<String, String>, screen: String?, tab: String?) {
        when {
            screen?.startsWith("mock") == true -> mock(out, screen, tab)
            screen == "review" -> out["/api/cards/due"] = AppJson.encodeToString(DueResponse.serializer(), cards(tab))
            screen == "coach" -> out["/api/coach"] = AppJson.encodeToString(Coach.serializer(), coach(tab))
        }
    }

    // MARK: Mock

    private fun sec(
        skill: String, state: String, band: Double? = null, attempt: String? = null, session: String? = null,
        elapsed: Double? = null, limit: Double? = null, mode: String? = null,
    ) = MockSection(skill, state, band, attempt, session, elapsed, limit, mode)

    /** [variant]: Listening (fresh) | Transition | Writing | Speaking / Choice | Done. */
    fun mockFor(variant: String?): Mock {
        val base = Mock(
            MOCK_ID, variant = "academic", source = "cambridge", ref = "C19 T1",
            startedAt = "2026-10-02T09:30:00.000Z", expiresAt = "2026-10-09T09:30:00.000Z",
        )
        val l = sec("listening", "done", 7.5, "l1")
        val r = sec("reading", "done", 6.5, "r1")
        return when (variant) {
            "Transition" -> base.copy(
                next = "reading",
                sections = listOf(l, sec("reading", "todo", limit = 3600.0), sec("writing", "todo", limit = 3600.0), sec("speaking", "todo")),
            )
            "Writing" -> base.copy(
                next = "writing",
                sections = listOf(l, r, sec("writing", "in_progress", elapsed = 1260.0, limit = 3600.0), sec("speaking", "todo")),
            )
            "Speaking", "Choice" -> base.copy(
                next = "speaking",
                sections = listOf(l, r, sec("writing", "done", 6.0, "aw1", "ws1"), sec("speaking", "todo")),
            )
            "Done" -> base.copy(
                status = "completed", completedAt = "2026-10-03T10:40:00.000Z", next = null, overall = 6.5,
                sections = listOf(l, r, sec("writing", "done", 6.0, "aw1", "ws1"), sec("speaking", "done", 6.5, "as1", "ss1", mode = "recorded")),
            )
            else -> base.copy(
                next = "listening",
                sections = listOf(sec("listening", "todo", limit = 1800.0), sec("reading", "todo", limit = 3600.0), sec("writing", "todo", limit = 3600.0), sec("speaking", "todo")),
            )
        }
    }

    private fun mock(out: MutableMap<String, String>, screen: String?, tab: String?) {
        val resume = screen == "mock-start-resume"
        out["/api/mock/current"] = AppJson.encodeToString(CurrentMock.serializer(), CurrentMock(if (resume) mockFor("Transition") else null))
        val q = MockOptionsQuota(SkillQuota(0, 1, 1, null, "day"), SkillQuota(0, 1, 1, null, "day"))
        val opts = AppJson.encodeToString(
            MockOptions.serializer(),
            MockOptions(listOf(MockCambridge("C18 T2", started = true), MockCambridge("C19 T1"), MockCambridge("C19 T2"), MockCambridge("C20 T1")), own = true, quota = q),
        )
        out["/api/mock/options"] = opts
        out["/api/mock/options?variant=academic"] = opts
        out["/api/mock/options?variant=general"] = opts
        out["/api/mock/$MOCK_ID"] = AppJson.encodeToString(Mock.serializer(), mockFor(if (screen == "mock-result") "Done" else tab))
    }

    // MARK: Review

    private fun card(id: String, front: String, back: String, source: String, reps: Int, interval: Double) =
        DueCard(id, front, back, source, 2.5, interval, reps)

    /** [tab]: Question | Revealed | Done | Empty. */
    private fun cards(tab: String?): DueResponse = when (tab) {
        "Done" -> DueResponse(cards = emptyList(), total = 0, deck = 14)
        "Empty" -> DueResponse(cards = emptyList(), total = 0, deck = 0)
        else -> DueResponse(
            cards = listOf(
                card("c1", "I live there since 2015", "I have lived there since 2015\nPresent perfect with \"since\" for something that is still true.", "mistake", 2, 6.0),
                card("c2", "meticulous", "Very careful and exact about small details.\nHer meticulous notes made the revision easy.", "vocab", 1, 1.0),
                card("c3", "Make it stronger\n\nThe graph shows a big increase in sales.", "The graph shows a sharp rise in sales.", "fix", 3, 12.0),
                card("c4", "🎧 Listening · spell the word you heard (sediment)", "sediment\nYou typed: sedement", "mistake", 0, 0.0),
                card("c5", "make a research", "do research\n\"Research\" is uncountable and takes \"do\".", "mistake", 2, 6.0),
                card("c6", "really changed", "transformed, reshaped\nStronger verbs for band 7 and above.", "vocab", 4, 20.0),
            ),
            total = 6, deck = 14,
        )
    }

    // MARK: Coach

    private fun cand(id: String, skill: String, kind: String, count: Int, tests: Int, actions: List<String>, part: Int? = null) = CoachCandidate(
        id, skill, kind, count, tests, share = 0.3, score = 0.8,
        facts = part?.let { mapOf("part" to JsonPrimitive(it)) } ?: emptyMap(),
        evidence = CoachEvidence(attemptIds = listOf("aw1"), mistakeIds = listOf("m0")), actions = actions,
    )

    private fun skillFacts(skill: String, tests: Int, band: Double, trend: String, delta: Double?, need: Int = 0) =
        CoachSkillFacts(skill, tests, band, listOf(band - 0.5, band + 0.5), trend, delta, need)

    private fun report() = CoachReport(
        id = "rp1", day = "2026-10-03", createdAt = "2026-10-03T06:00:00.000Z",
        facts = CoachFacts(
            generatedFor = "demo", target = 7.0, examDate = "2026-11-14", daysToExam = 42, totalTests = 11,
            skills = listOf(
                skillFacts("listening", 3, 7.0, "steady", 0.0),
                skillFacts("reading", 3, 6.5, "improving", 0.5),
                skillFacts("writing", 3, 6.0, "slipping", -0.5),
                skillFacts("speaking", 2, 6.5, "calibrating", null, need = 1),
            ),
            overall = CoachOverall(6.5, listOf(6.0, 7.0)),
            candidates = listOf(
                cand("c1", "writing", "grammar.articles", 9, 3, listOf("write-task2"), part = 2),
                cand("c2", "reading", "question-type:Matching headings", 5, 3, listOf("reading-test")),
                cand("c3", "speaking", "fluency.pauses", 7, 2, listOf("speak-part"), part = 2),
            ),
        ),
        words = CoachWords(
            summary = "You are close to band 7 in Listening and Reading. Writing is the gap, and articles are the quickest gain.",
            focus = listOf(
                CoachFocus("c1", "Articles are costing you marks", "You dropped or added an article 9 times across your last 3 essays, mostly before places and unique things.", "write-task2", "Write a Task 2 essay"),
                CoachFocus("c2", "Matching headings is slowing Reading", "You missed 5 of 8 matching headings questions. Read the first and last sentence of each paragraph before you choose.", "reading-test", "Take a Reading test"),
                CoachFocus("c3", "Long pauses in Part 2", "You paused for more than 2 seconds 7 times in two Part 2 answers. Plan three points and talk through them.", "speak-part", "Practise Part 2"),
            ),
            win = "Your Listening has held at 7.0 for three tests in a row.",
            cheat = listOf(
                CoachCheat("c1", "Before a place name, ask: does the reader know which one? If yes, use the."),
                CoachCheat("c2", "Headings match the main idea of a paragraph, not a single detail."),
            ),
            plan = listOf(
                CoachPlanDay("Mon", "Write a Task 2 essay in 40 minutes and underline every article.", "write-task2"),
                CoachPlanDay("Tue", "Do one Reading passage and write the main idea of each paragraph first.", "reading-test"),
                CoachPlanDay("Wed", "Review your due cards. Ten minutes is enough.", "review-deck"),
                CoachPlanDay("Thu", "Record a Part 2 answer for the full two minutes.", "speak-part"),
                CoachPlanDay("Fri", "Take a Listening test and check spelling before you submit.", "listening-test"),
                CoachPlanDay("Sat", "Rewrite last Monday's essay with the fixes from your report.", "write-task2"),
                CoachPlanDay("Sun", "Rest, then look through your mistakes.", "see-evidence"),
            ),
        ),
    )

    /** [tab]: Ready | Updating | Empty. */
    private fun coach(tab: String?): Coach = when (tab) {
        "Empty" -> Coach("empty")
        "Updating" -> Coach("ready", true, report())
        else -> Coach("ready", false, report())
    }
}
