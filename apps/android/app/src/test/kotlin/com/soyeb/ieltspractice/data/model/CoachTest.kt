package com.soyeb.ieltspractice.data.model

import com.soyeb.ieltspractice.data.net.AppJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** GET /api/coach: a ready report in the shape of apps/server/src/coach/types.ts, an empty one, and unknown or missing fields. */
class CoachTest {
    private val ready = """
        {"state":"ready","updating":false,"report":{
          "id":"r1","day":"2026-10-03","createdAt":"2026-10-03T06:00:00.000Z","hidden":["pattern:c2"],
          "facts":{"version":1,"generatedFor":"u1","target":7,"examDate":"2026-12-05","daysToExam":63,"totalTests":6,
            "skills":[{"skill":"writing","tests":3,"band":6.5,"range":[6,7],"trend":"improving","trendDelta":0.5,"needMore":0,"gap":0.5,
                       "series":[{"at":"2026-09-20","band":6},{"at":"2026-10-01","band":6.5}]},
                      {"skill":"reading","tests":0,"band":null,"range":null,"trend":"calibrating","trendDelta":null,"needMore":2,"gap":null,"series":[]}],
            "overall":{"band":6.5,"range":[6,7]},
            "candidates":[{"id":"c1","skill":"writing","kind":"grammar.articles","count":9,"tests":3,"share":0.3,"score":2.5,
                           "facts":{"perTest":3,"example":"a advice"},"evidence":{"attemptIds":["a1","a2"],"mistakeIds":["m1"]},"actions":["write-task2","review-deck"]},
                          {"id":"c2","skill":"reading","kind":"question-type:Matching headings","count":4,"tests":2,"share":0.5,"score":1,
                           "facts":{},"evidence":{"attemptIds":[],"lr":[{"attemptId":"l1","n":14}]},"actions":["see-evidence"]}],
            "win":null,
            "swaps":[{"id":"s1","skill":"writing","from":"good","to":["beneficial","valuable"],"count":3,"source":"overused","evidence":{"attemptIds":["a1"]}}],
            "spelling":[{"id":"sp1","rule":"double","count":2,"examples":[{"typed":"occured","correct":"occurred"}],"evidence":{"attemptIds":["l1"]}}],
            "patterns":{"listening":[],"reading":[],"writing":[],"speaking":[]},
            "timing":{"passage3Late":2},
            "reviewDue":12,
            "weekStats":{"tests":2,"minutes":95,"days":2,"prev":{"tests":1,"minutes":40,"days":1}},
            "futureField":true},
          "words":{"summary":"Writing is your fastest gain.",
            "focus":[{"candidateId":"c1","title":"Articles","why":"9 slips in 3 essays","action":"write-task2","label":"Write a Task 2"}],
            "win":"Your Task 2 structure improved.",
            "cheat":[{"candidateId":"c1","text":"a/an before a vowel sound"}],
            "plan":[{"day":"Mon","text":"One Task 2 essay","action":"write-task2"}]}}}
    """.trimIndent()

    @Test fun decodesAReadyReport() {
        val c = AppJson.decodeFromString(Coach.serializer(), ready)
        assertTrue(c.ready)
        val r = c.report!!
        assertEquals("r1", r.id)
        assertEquals(listOf("pattern:c2"), r.hidden)
        assertEquals(7.0, r.facts.target)
        assertEquals(63, r.facts.daysToExam)
        assertEquals(listOf(6.0, 7.0), r.facts.skills[0].range)
        assertNull(r.facts.skills[1].band)
        assertEquals("grammar.articles", r.facts.candidates[0].kind)
        assertEquals("a advice", r.facts.candidates[0].facts.getValue("example").content)
        assertEquals(14, r.facts.candidates[1].evidence.lr.single().n)
        assertNull(r.facts.win)
        assertEquals(listOf("beneficial", "valuable"), r.facts.swaps.single().to)
        assertEquals(setOf("listening", "reading", "writing", "speaking"), r.facts.patterns.keys)
        assertEquals(1, r.facts.weekStats.prev.tests)
        assertEquals("write-task2", r.words.focus.single().action)
        assertEquals("Mon", r.words.plan.single().day)
    }

    @Test fun decodesTheEmptyState() {
        val c = AppJson.decodeFromString(Coach.serializer(), """{"state":"empty","updating":true,"report":null}""")
        assertFalse(c.ready)
        assertTrue(c.updating)
        assertNull(c.report)
        // a report with nothing but its identity still decodes
        assertEquals("x", AppJson.decodeFromString(Coach.serializer(), """{"state":"ready","updating":false,"report":{"id":"x","day":"d","createdAt":"t","hidden":[]}}""").report?.id)
    }
}
