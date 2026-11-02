package com.soyeb.ieltspractice.data.logic

import com.soyeb.ieltspractice.data.model.Codes
import com.soyeb.ieltspractice.data.model.Quota
import com.soyeb.ieltspractice.data.net.ApiError
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.TextStyle
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

// Plans and limits (docs/community.md): quotas and the copy for every limit. Pure Kotlin so the rules
// are unit-tested (CommunityTest). 

/** Where "now" and the time zone come from. Demo mode pins both so screenshots are stable. */
object Clocks {
    var now: () -> Instant = { Instant.now() }
    var zone: () -> ZoneId = { ZoneId.systemDefault() }
}

// MARK: Reset time and quota labels

object QuotaCopy {
    private val timeFmt = DateTimeFormatter.ofPattern("H:mm", Locale.US)

    /** "Resets in 5 h" under a day (minutes under an hour), otherwise "Resets Monday 6:00" in local time. Null without a resetAt. */
    fun resets(resetAt: String?, now: Instant = Clocks.now(), zone: ZoneId = Clocks.zone()): String? {
        val at = parse(resetAt) ?: return null
        val minutes = (at.epochSecond - now.epochSecond + 59) / 60
        return when {
            minutes <= 1 -> "Resets in a moment" // up to a minute away, like the web and iOS
            minutes < 60 -> "Resets in $minutes min"
            minutes < 24 * 60 -> "Resets in ${(minutes / 60.0).roundToInt().coerceAtLeast(1)} h"
            else -> {
                val local = at.atZone(zone)
                "Resets ${local.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.US)} ${timeFmt.format(local)}"
            }
        }
    }

    /** Same as [resets] without the leading word: "in 5 h", "Monday 6:00" (for sentences like "It resets in 5 h."). */
    fun resetsWhen(resetAt: String?, now: Instant = Clocks.now(), zone: ZoneId = Clocks.zone()): String? =
        resets(resetAt, now, zone)?.removePrefix("Resets ")?.let { if (it == "in a moment") "in a moment" else it }

    /** "today", "this week" or "this month" for a window. */
    fun windowWord(window: String?) = when (window) { "month" -> "this month"; "week" -> "this week"; else -> "today" }

    /** The inline line under a Start button: "1 test left today", "No tests left. Resets Monday 6:00", "Unlimited". */
    fun left(q: Quota, skill: String, now: Instant = Clocks.now(), zone: ZoneId = Clocks.zone()): String {
        val s = q.of(skill)
        if (q.unlimited || s.limit == null) return "Unlimited"
        when (s.blocked) {
            Codes.BALANCE -> return "Scoring is paused for now"
            Codes.BUSY -> return "Busy right now. Try again in a few minutes"
        }
        val remaining = s.remaining ?: (s.limit - s.used).coerceAtLeast(0)
        val whenText = windowWord(s.window)
        if (remaining <= 0) return listOfNotNull("No tests left.", resets(s.resetAt, now, zone)).joinToString(" ")
        return "$remaining test${if (remaining == 1) "" else "s"} left $whenText"
    }

    /** Short form for a chip or summary row: "1 left today". */
    fun leftShort(q: Quota, skill: String): String {
        val s = q.of(skill)
        if (q.unlimited || s.limit == null) return "Unlimited"
        val remaining = s.remaining ?: (s.limit - s.used).coerceAtLeast(0)
        return if (remaining <= 0) "None left" else "$remaining left ${windowWord(s.window)}"
    }

    private fun parse(iso: String?): Instant? = iso?.let { runCatching { Instant.parse(it) }.getOrNull() }
}

// MARK: The fair-use acknowledgement (once per user per UTC day)

object FairUse {
    /** `userKey` is the user id, or "guest" for a guest session (per device). */
    fun ackKey(userKey: String, now: Instant = Clocks.now()): String = userKey + "@" + now.atOffset(ZoneOffset.UTC).toLocalDate()

    const val TITLE = "Please practise fairly"

    /** The dialog text as short paragraphs (one string when joined with a space: the same words on every platform). */
    fun paragraphs(q: Quota, skill: String, guest: Boolean): List<String> {
        val s = q.of(skill)
        val n = s.remaining ?: 0
        val unit = if (skill == "examiner") "live session" else "$skill test"
        val left = "You have $n $unit${if (n == 1) "" else "s"} left ${QuotaCopy.windowWord(s.window)}."
        return listOfNotNull(
            "Free tests are shared by everyone. Please don't abuse them: no spamming tests and no automated use.",
            left,
            if (guest) "Create a free account for more tests." else null,
        )
    }

    fun body(q: Quota, skill: String, guest: Boolean): String = paragraphs(q, skill, guest).joinToString(" ")
}

// MARK: The limit panels

enum class GateAction { Account, Close }

/** The text and buttons of a "you can't start this test" panel. [primary] is the one teal button. */
data class GateCopy(val title: String, val body: String, val primary: Pair<String, GateAction>?, val secondary: Pair<String, GateAction>)

object GateText {
    /**
     * The same titles and bodies as the web and iOS (docs/community.md, panel copy). [code] is the server's code, [tier] guest/community/subscriber/unlimited.
     * [keep] is a note for a test that was already under way ("Your essay is still here.").
     * [skill] is kept for the callers; the copy no longer names it.
     */
    @Suppress("UNUSED_PARAMETER")
    fun copy(code: String, skill: String, tier: String, resetAt: String?, keep: String? = null,
             now: Instant = Clocks.now(), zone: ZoneId = Clocks.zone()): GateCopy {
        val guest = tier == "guest"
        fun withKeep(s: String) = if (keep == null) s else "$s $keep"
        val whenText = QuotaCopy.resetsWhen(resetAt, now, zone)
        return when (code) {
            Codes.QUOTA -> GateCopy(
                if (guest) "You've used your free tests for now" else "You've used all your tests for now",
                withKeep("It resets ${whenText ?: "soon"}."),
                if (guest) "Create a free account for more" to GateAction.Account else null,
                (if (guest) "Not now" else "Wait for the reset") to GateAction.Close,
            )
            Codes.BALANCE -> GateCopy(
                "Scoring is paused for now",
                withKeep("Scoring is paused for a short while. Please try again later."),
                null, "Close" to GateAction.Close,
            )
            Codes.BUSY -> GateCopy(
                "A lot of people are practising right now",
                withKeep("Try again in a few minutes."),
                null, "Close" to GateAction.Close,
            )
            Codes.LIVE -> GateCopy(
                "The live examiner isn't available right now",
                withKeep("Try again later, or take the recorded test instead."),
                null, "Close" to GateAction.Close,
            )
            else -> GateCopy(
                "You're going a bit fast",
                withKeep("Try again in a moment."),
                null, "Close" to GateAction.Close,
            )
        }
    }

    /** One sentence for a place with no panel (an upload that was refused): why, when it resets, and what was kept. */
    fun sentence(e: ApiError, keep: String): String {
        val c = copy(e.code.orEmpty(), e.skill ?: "speaking", e.tier ?: "community", e.resetAt)
        return "${c.title}. ${c.body} $keep"
    }
}

// MARK: Panels in flight (what AppNav shows over the current screen)

sealed interface Gate {
    /** A test can't start (or finish) now. [keep] says what was kept ("Your essay is still here."). */
    data class Blocked(
        val code: String, val skill: String, val tier: String, val resetAt: String?, val keep: String? = null,
    ) : Gate

    /** The fair-use dialog for the free tiers. [onStart] continues into the test. */
    data class FairUse(val skill: String, val quota: Quota, val guest: Boolean, val onStart: () -> Unit) : Gate

    /** Anything else worth a sentence (offline while checking, a guest session that couldn't start). */
    data class Message(val title: String, val body: String) : Gate
}
