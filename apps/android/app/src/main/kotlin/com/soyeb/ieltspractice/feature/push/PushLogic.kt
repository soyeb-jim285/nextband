package com.soyeb.ieltspractice.feature.push

import java.net.URI

// Pure push rules (unit-tested in PushLogicTest). Plan: docs/engagement/notifications-and-streaks.md §4, §6.

/** One Android notification channel. [importance] uses NotificationManagerCompat's IMPORTANCE_* values (2 low, 3 default, 4 high). */
data class PushChannel(val id: String, val name: String, val description: String, val importance: Int)

/** The five channels of the plan (§6). The server names the channel in each message's `data.channel`. */
val PUSH_CHANNELS = listOf(
    PushChannel("results", "Results", "Your band is ready, or a marking needs a retry", 3),
    PushChannel("streak", "Streak", "Before your streak ends tonight", 4),
    PushChannel("practice", "Practice reminders", "Drills that are due and skills you haven't practised lately", 3),
    PushChannel("exam", "Exam", "Countdown to your exam date", 3),
    PushChannel("weekly", "Weekly recap", "A short summary of your week", 2),
)

/** The channel a message goes on: the one it names, else Results (round B only sends results). */
fun channelFor(id: String?): String = PUSH_CHANNELS.firstOrNull { it.id == id }?.id ?: "results"

/** Where a push opens, from its `data.route` (a web path). */
sealed interface PushTarget {
    /** A writing or speaking result: one attempt, or a full Writing test (Task 1 + Task 2). */
    data class Attempts(val ids: List<String>) : PushTarget
    /** A speaking session (all its parts): the app finds the other attempts from the history list. */
    data class Session(val firstId: String, val sessionId: String) : PushTarget
    data class Mock(val mockId: String) : PushTarget
    data object Settings : PushTarget
}

private val ID = Regex("[A-Za-z0-9_-]{1,128}")

/** Maps the server's deep-link route to a screen; null (unknown or malformed) just opens the app. */
fun parsePushRoute(route: String?): PushTarget? {
    val uri = runCatching { URI(route ?: return null) }.getOrNull() ?: return null
    val seg = uri.path.orEmpty().split('/').filter { it.isNotEmpty() }
    val query = uri.rawQuery.orEmpty().split('&').mapNotNull { p -> p.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] } }.toMap()
    fun id(s: String?) = s?.takeIf { ID.matches(it) }
    return when {
        seg == listOf("settings") -> PushTarget.Settings
        seg.size == 2 && seg[0] == "mock" -> id(seg[1])?.let { PushTarget.Mock(it) }
        seg.size == 3 && seg[1] == "result" && seg[0] == "writing" -> {
            val first = id(seg[2]) ?: return null
            PushTarget.Attempts(listOfNotNull(first, id(query["pair"])))
        }
        seg.size == 3 && seg[1] == "result" && seg[0] == "speaking" -> {
            val first = id(seg[2]) ?: return null
            id(query["session"])?.let { PushTarget.Session(first, it) } ?: PushTarget.Attempts(listOf(first))
        }
        else -> null
    }
}

/** Updates-bell rows a push for [target] answers: opening the push marks them read (UpdatesBell keys). */
fun seenKeysFor(target: PushTarget?): List<String> = when (target) {
    is PushTarget.Attempts -> target.ids.flatMap { listOf("d:$it", "f:$it") }
    is PushTarget.Session -> listOf("d:${target.firstId}", "f:${target.firstId}")
    else -> emptyList()
}

const val PRIMER_WAIT_MS = 7L * 24 * 60 * 60 * 1000
const val PRIMER_MAX_REFUSALS = 2

/**
 * The permission explainer (§6): Android 13+ only, not while notifications are already allowed, never again after
 * [PRIMER_MAX_REFUSALS] "Not now"s (Settings keeps a row), and 7 days after the last one.
 */
fun shouldAskForPush(sdk: Int, enabled: Boolean, refusals: Int, lastRefusedAt: Long?, now: Long): Boolean =
    sdk >= 33 && !enabled && refusals < PRIMER_MAX_REFUSALS && (lastRefusedAt == null || now - lastRefusedAt >= PRIMER_WAIT_MS)

private val HHMM = Regex("([01]\\d|2[0-3]):([0-5]\\d)")

/** "HH:MM" to (hour, minute), or null. */
fun parseClock(s: String?): Pair<Int, Int>? = s?.let { HHMM.matchEntire(it) }?.let { it.groupValues[1].toInt() to it.groupValues[2].toInt() }

fun clockString(hour: Int, minute: Int): String = "%02d:%02d".format(hour, minute)

/** How a stored "HH:MM" reads on this phone: "22:30", or "10:30 pm" on a 12-hour phone. */
fun displayClock(s: String?, is24: Boolean): String {
    val (h, m) = parseClock(s) ?: return s.orEmpty()
    if (is24) return clockString(h, m)
    val h12 = if (h % 12 == 0) 12 else h % 12
    return "%d:%02d %s".format(h12, m, if (h < 12) "am" else "pm")
}
