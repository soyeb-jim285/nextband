package com.soyeb.ieltspractice.feature.auth

import com.soyeb.ieltspractice.data.model.AppSettings
import com.soyeb.ieltspractice.data.model.EmailStatus
import com.soyeb.ieltspractice.data.net.ApiError
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// Pure rules of the sign-in flow (ported from the old LoginScreen / ShellLogic; web lib/auth.ts). Tested in AuthLogicTest.

enum class AuthStep { SignIn, SignUp, Verify, Forgot, Reset;
    /** Sign-in and sign-up are the flow's entry points: Close leaves the flow. Other steps go back to sign-in. */
    val top get() = this == SignIn || this == SignUp
}

/** [mode] is the route argument (signin | signup | verify | forgot | reset). Anything else is sign-in. */
fun stepOf(mode: String?): AuthStep = when (mode?.lowercase()) {
    "signup" -> AuthStep.SignUp; "verify" -> AuthStep.Verify; "forgot" -> AuthStep.Forgot; "reset" -> AuthStep.Reset
    else -> AuthStep.SignIn
}

/** What is wrong with each field once the button was pressed (the form says why instead of greying the button). Null = fine. */
data class FieldErrors(
    val name: String? = null, val email: String? = null, val password: String? = null, val confirm: String? = null, val code: String? = null,
) {
    val ok: Boolean get() = name == null && email == null && password == null && confirm == null && code == null
}

const val MISMATCH = "The two passwords don't match."
private val EMAIL = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")

fun emailError(email: String): String? = when {
    email.isBlank() -> "Enter your email."
    !EMAIL.matches(email.trim()) -> "Enter a valid email address, like name@example.com."
    else -> null
}

/** A new password: 8 to 128 characters (the server's limits). */
fun newPasswordError(p: String): String? = when {
    p.isEmpty() -> "Enter a password."
    p.length < 8 -> "Use at least 8 characters."
    p.length > 128 -> "Use 128 characters or fewer."
    else -> null
}

fun confirmError(password: String, confirm: String): String? = when {
    confirm.isEmpty() -> "Confirm your password."
    confirm != password -> MISMATCH
    else -> null
}

/** Before submitting: a confirmation that stopped being a prefix of the password is already wrong (typing a prefix is not flagged). */
fun liveMismatch(password: String, confirm: String): String? = if (confirm.isNotEmpty() && !password.startsWith(confirm)) MISMATCH else null

fun codeError(code: String): String? = if (code.length < 6) "Enter all 6 digits." else null

fun signInErrors(email: String, password: String) = FieldErrors(email = emailError(email), password = if (password.isEmpty()) "Enter your password." else null)

fun signUpErrors(name: String, email: String, password: String, confirm: String) = FieldErrors(
    name = if (name.isBlank()) "Enter your name." else null, email = emailError(email), password = newPasswordError(password), confirm = confirmError(password, confirm),
)

fun resetErrors(code: String, password: String, confirm: String) =
    FieldErrors(code = codeError(code), password = newPasswordError(password), confirm = confirmError(password, confirm))

/** Server codes that belong under the code boxes rather than in the banner. */
fun isCodeError(code: String?): Boolean = code == "INVALID_OTP" || code == "OTP_EXPIRED" || code == "TOO_MANY_ATTEMPTS"

/** Digits only, at most six: what the code field keeps from typing or paste. */
fun otpDigits(s: String): String = s.filter { it in '0'..'9' }.take(6)

/** Wording for the 6-digit code flow. */
fun otpError(status: Int, code: String?, message: String): String = when {
    code == "INVALID_OTP" -> "That code isn't right. Check it and try again."
    code == "OTP_EXPIRED" -> "That code has expired. Request a new one."
    code == "TOO_MANY_ATTEMPTS" -> "Too many wrong tries. Request a new code."
    code == "otp_locked" -> message.ifEmpty { "Too many wrong codes. Try again later." }
    status == 429 -> "Too many requests. Wait a minute, then try again."
    else -> message.ifEmpty { "Something went wrong. Try again." }
}

/**
 * After a sign-in: open goal setup unless this account finished it on this device ([doneHere]) or already has goals
 * from elsewhere (a non-default target or an exam date). Not the device-wide flag: a second person on the phone needs their own setup.
 */
fun needsOnboarding(s: AppSettings, doneHere: Boolean): Boolean =
    !doneHere && s.examDate == null && s.targetBand == AppSettings().targetBand

/** Per-account "finished goal setup on this device" key in Prefs' string store. */
fun onboardedKey(userId: String) = "onboarded:$userId"

/** Why a request to send a code failed, as an [EmailStatus.error] kind for [emailStatusLine] ("" when no reason fits). */
fun sendFailureKind(x: Throwable): String = when {
    x !is ApiError || x.status == 0 -> "network"
    x.status == 429 -> "rate_limited"
    x.status >= 500 -> "unavailable"
    else -> ""
}

/** The status line under the code boxes: text and whether it is a failure. Same wording as the web's StatusLine. */
fun emailStatusLine(s: EmailStatus?, nowMs: Long, zone: ZoneId = ZoneId.systemDefault()): Pair<String, Boolean> {
    if (s == null || s.status == "none") return "Sending the code…" to false
    if (s.status == "failed") {
        val why = when (s.error) {
            "rate_limited" -> " (the email service is busy)"; "rejected" -> " (the address was refused)"
            "network" -> " (the email service could not be reached)"; "unavailable" -> " (the email service is down)"; else -> ""
        }
        return "We couldn't send the email$why. Try again." to true
    }
    val to = s.maskedEmail ?: "your address"
    val at = s.sentAt?.let { runCatching { Instant.parse(it) }.getOrNull() }
    if (s.alreadySent && at != null) {
        val ago = maxOf(0L, (nowMs - at.toEpochMilli() + 500) / 1000)
        val resend = if (s.resendAvailableIn > 0) "; you can resend in ${s.resendAvailableIn} s" else ""
        return "Code already sent $ago s ago to $to; it is still valid. Check spam$resend." to false
    }
    val time = at?.let { " at " + DateTimeFormatter.ofPattern("HH:mm").format(it.atZone(zone)) } ?: ""
    return "Code sent to $to$time. Check your spam folder if it doesn't show up." to false
}
