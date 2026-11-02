package com.soyeb.ieltspractice.data.model

import kotlinx.serialization.Serializable

// Plans and limits (docs/community.md). The copy for every limit is in data/logic/Community.kt.

/** One counter (speaking, writing or examiner). Limit, remaining, resetAt and window are null when unlimited. */
@Serializable data class SkillQuota(
    val used: Int = 0,
    val limit: Int? = null,
    val remaining: Int? = null,
    val resetAt: String? = null,
    val window: String? = null, // "day" | "week" | "month"
    /** quota_exceeded | community_balance_exhausted | community_busy | null (go). */
    val blocked: String? = null,
)

/** `GET /api/quota` (and the same fields inside `GET /api/me`). */
@Serializable data class Quota(
    /** guest | community | subscriber | unlimited. A plain string so a tier this app doesn't know yet still decodes. */
    val tier: String = "guest",
    val speaking: SkillQuota = SkillQuota(),
    val writing: SkillQuota = SkillQuota(),
    /** Live examiner sessions (LiveKit). */
    val examiner: SkillQuota = SkillQuota(),
    /** The live examiner (LiveKit, on the server's keys) is set up on the server. */
    val live: Boolean = false,
    /** ISO end of the paid plan when on it; otherwise null. */
    val subscribedUntil: String? = null,
    val lr: LrAccess = LrAccess(),
) {
    /** [skill] is speaking, writing or examiner (a live session). */
    fun of(skill: String) = when (skill) { "writing" -> writing; "examiner" -> examiner; else -> speaking }
    /** The owner or an admin grant: no limits at all. */
    val unlimited get() = tier == "unlimited"
    /** The free tiers, which see the fair-use note. */
    val free get() = tier == "guest" || tier == "community"
    val subscriber get() = tier == "subscriber"
}

/** Error codes the server answers with (body `{ error, code }`). */
object Codes {
    const val QUOTA = "quota_exceeded"
    const val BALANCE = "community_balance_exhausted"
    const val BUSY = "community_busy"
    const val FAST = "too_many_requests"
    /** The live examiner can't run (not set up on the server). */
    const val LIVE = "live_unavailable" // set by the app, not the server
    const val ACCOUNT = "account_required"
    /** The codes that mean "a test can't start or finish now". */
    val limits = setOf(QUOTA, BALANCE, BUSY, FAST, LIVE)
}

/** POST /api/pay/checkout: open [url] in a browser. */
@Serializable data class CheckoutStarted(val url: String)
