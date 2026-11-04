package com.soyeb.ieltspractice.data.model

import kotlinx.serialization.Serializable

// GET /api/me (components.schemas.Me). Every field beyond the original contract is defaulted so older servers and fixtures still decode.

@Serializable data class User(
    val id: String, val email: String = "", val name: String = "", val emailVerified: Boolean = false,
    /** A guest (anonymous session): no account yet. Their `email` is empty; never show it. */
    val isAnonymous: Boolean = false,
)

/** GET/PUT /api/settings. PUT goes through [com.soyeb.ieltspractice.data.net.Api.saveSettings], which sends only the fields that change. */
@Serializable data class AppSettings(
    val targetBand: Double = 7.0,
    val writingAutoSubmit: Boolean = true, // server DEFAULT_SETTINGS
    val blockPaste: Boolean = true,
    val productEmails: Boolean = false,
    /** `yyyy-MM-dd` or null. */
    val examDate: String? = null,
    val timeZone: String = "",
)

/** Listening and Reading tests the caller can open, of all a plan can open. */
@Serializable data class LrOpen(val open: Int = 0, val total: Int = 0)
@Serializable data class LrAccess(val listening: LrOpen = LrOpen(), val reading: LrOpen = LrOpen())

@Serializable data class Me(
    val user: User,
    val settings: AppSettings = AppSettings(),
    val cambridgeAccess: Boolean = false,
    /** The site owner: the web shows the Admin area. */
    val isOwner: Boolean = false,
    // Community mode: the same fields as GET /api/quota.
    /** guest | community | subscriber | unlimited. A plain string so a tier this app doesn't know yet still decodes. */
    val tier: String? = null,
    /** ISO end of the paid plan (tier subscriber, or unlimited with a plan too); otherwise null. */
    val subscribedUntil: String? = null,
    val speaking: SkillQuota? = null,
    val writing: SkillQuota? = null,
    val examiner: SkillQuota? = null,
    val live: Boolean = false,
    val lr: LrAccess? = null,
) {
    /** The quota block of `/api/me`, or null from a server that predates community mode. */
    fun quota(): Quota? = tier?.let {
        Quota(it, speaking ?: SkillQuota(), writing ?: SkillQuota(), examiner ?: SkillQuota(), live, subscribedUntil, lr ?: LrAccess())
    }
}

/** An empty `{}` body (writes that return nothing useful). */
@Serializable class Empty

/** GET /api/auth-email/status: what happened to the code email. [error] is a short kind (rate_limited, rejected, network, unavailable), never provider text. */
@Serializable data class EmailStatus(
    val status: String = "none", val sentAt: String? = null, val maskedEmail: String? = null,
    val resendAvailableIn: Int = 0, val alreadySent: Boolean = false, val error: String? = null,
)
