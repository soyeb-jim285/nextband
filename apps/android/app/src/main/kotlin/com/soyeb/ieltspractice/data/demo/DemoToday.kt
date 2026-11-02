package com.soyeb.ieltspractice.data.demo

import com.soyeb.ieltspractice.data.net.AppJson
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

/**
 * Demo answers Today needs that the shared fixtures lack: an exam date, a coach report and an open full mock.
 * Applies only to the Today screens (`today`, `today-lower`, `today-nomock`, `today-new`); "today-new" is a first-run account with nothing yet.
 */
object DemoToday {
    private val screens = setOf("today", "today-lower", "today-nomock", "today-new")

    private const val OPEN_MOCK = """{"mock":{"id":"mk-open","variant":"academic","source":"generated","status":"in_progress","startedAt":"2026-10-02T09:00:00.000Z","expiresAt":"2026-10-09T09:00:00.000Z","next":"reading","overall":null,"sections":[{"skill":"listening","state":"done","band":7.0},{"skill":"reading","state":"todo"},{"skill":"writing","state":"todo"},{"skill":"speaking","state":"todo"}]}}"""

    private const val COACH = """{"state":"ready","updating":false,"report":{"id":"rep1","day":"2026-10-03","createdAt":"2026-10-03T09:00:00.000Z","hidden":[],"facts":{"target":7.0},"words":{"summary":"Your Speaking is steady. Articles are the quickest gain.","focus":[{"candidateId":"c1","title":"Articles are costing you marks","why":"You dropped or added an article 9 times across your last 4 tests, mostly in Task 2 essays.","action":"write-task2","label":"Write a Task 2 essay"}]}}}"""

    fun apply(out: MutableMap<String, String>, screen: String?) {
        if (screen !in screens) return
        val fresh = screen == "today-new"
        out["/api/mock/current"] = if (screen == "today-nomock" || fresh) """{"mock":null}""" else OPEN_MOCK
        out["/api/coach"] = if (fresh) """{"state":"empty"}""" else COACH
        if (!fresh) out["/api/me"]?.let { text ->
            val me = AppJson.parseToJsonElement(text).jsonObject
            val settings = me["settings"]?.jsonObject ?: JsonObject(emptyMap())
            out["/api/me"] = JsonObject(me + ("settings" to JsonObject(settings + ("examDate" to JsonPrimitive("2026-10-27"))))).toString()
        }
        if (fresh) {
            out["/api/progress"] = """{"trend":[],"streak":0,"minutesThisWeek":0,"attempts":0,"predicted":{}}"""
            out["/api/lr/progress"] = "{}"
            out["/api/attempts"] = """{"items":[],"total":0}"""
            out["/api/attempts?page=1"] = """{"items":[],"total":0}"""
            out["/api/lr/attempts"] = """{"items":[]}"""
            out["/api/cards/due"] = """{"items":[]}"""
            out["/api/mock"] = """{"items":[]}"""
        }
    }
}
