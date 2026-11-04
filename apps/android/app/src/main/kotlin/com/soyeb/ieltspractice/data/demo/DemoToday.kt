package com.soyeb.ieltspractice.data.demo

import com.soyeb.ieltspractice.data.model.Streak
import com.soyeb.ieltspractice.data.model.StreakDay
import com.soyeb.ieltspractice.data.model.StreakWeekDay
import com.soyeb.ieltspractice.data.net.AppJson
import java.time.LocalDate
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

    /**
     * `/api/streak` at the pinned demo day (Sun 4 Oct 2026 in Dhaka, see DemoCommunity.pinClock): a 12-day streak done today, one
     * freeze saved and one used on Wed 30 Sep, a 21-day best in the summer, and a believable on-and-off half year before that.
     */
    fun streak(fresh: Boolean = false): String {
        val today = LocalDate.of(2026, 10, 4)
        val frozenDay = LocalDate.of(2026, 9, 30)
        val streakStart = LocalDate.of(2026, 9, 22) // 12 practice days plus the frozen one
        val bestStart = LocalDate.of(2026, 7, 6) // 21 days in a row
        val days = (181 downTo 0).map { back ->
            val d = today.minusDays(back.toLong())
            val i = d.toEpochDay().toInt()
            val level = when {
                fresh -> 0
                d == frozenDay -> 0
                !d.isBefore(streakStart) -> listOf(1, 2, 3, 1, 3, 2)[i % 6]
                !d.isBefore(bestStart) && d.isBefore(bestStart.plusDays(21)) -> listOf(2, 1, 3, 1, 2)[i % 5]
                d == streakStart.minusDays(1) || d == bestStart.minusDays(1) || d == bestStart.plusDays(21) -> 0
                (i * 37 + 11) % 10 < 5 -> listOf(1, 1, 2, 3)[i % 4] // about half the other days, mostly drills
                else -> 0
            }
            StreakDay(d.toString(), level, frozen = !fresh && d == frozenDay)
        }
        val monday = today.minusDays((today.dayOfWeek.value - 1).toLong())
        val week = (0..6).map { k ->
            val d = monday.plusDays(k.toLong())
            val level = days.firstOrNull { it.day == d.toString() }
            val state = when {
                d.isAfter(today) -> "future"
                level != null && level.level > 0 -> "done"
                d == today -> "today"
                level?.frozen == true -> "frozen"
                else -> "missed"
            }
            StreakWeekDay(d.toString(), state)
        }
        val s = if (fresh) Streak(today = "pending", week = week, days = days, nextMilestone = 3)
        else Streak(
            current = 12, longest = 21, freezes = 1, today = "done", week = week, days = days,
            practicedLast35 = days.takeLast(35).count { it.level > 0 }, nextMilestone = 14,
        )
        return AppJson.encodeToString(Streak.serializer(), s)
    }

    fun apply(out: MutableMap<String, String>, screen: String?) {
        if (screen !in screens) return
        val fresh = screen == "today-new"
        out["/api/mock/current"] = if (screen == "today-nomock" || fresh) """{"mock":null}""" else OPEN_MOCK
        // Resume on the Today card opens the hub, which loads the mock by id.
        out["/api/mock/mk-open"] = AppJson.parseToJsonElement(OPEN_MOCK).jsonObject["mock"].toString()
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
