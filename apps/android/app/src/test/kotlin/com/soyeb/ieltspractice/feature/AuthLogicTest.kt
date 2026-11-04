package com.soyeb.ieltspractice.feature

import com.soyeb.ieltspractice.data.model.AppSettings
import com.soyeb.ieltspractice.data.model.EmailStatus
import com.soyeb.ieltspractice.data.net.ApiError
import com.soyeb.ieltspractice.feature.auth.AuthStep
import com.soyeb.ieltspractice.feature.auth.MISMATCH
import com.soyeb.ieltspractice.feature.auth.codeError
import com.soyeb.ieltspractice.feature.auth.emailError
import com.soyeb.ieltspractice.feature.auth.emailStatusLine
import com.soyeb.ieltspractice.feature.auth.isCodeError
import com.soyeb.ieltspractice.feature.auth.liveMismatch
import com.soyeb.ieltspractice.feature.auth.needsOnboarding
import com.soyeb.ieltspractice.feature.auth.otpDigits
import com.soyeb.ieltspractice.feature.auth.otpError
import com.soyeb.ieltspractice.feature.auth.resetErrors
import com.soyeb.ieltspractice.feature.auth.sendFailureKind
import com.soyeb.ieltspractice.feature.auth.signInErrors
import com.soyeb.ieltspractice.feature.auth.signUpErrors
import com.soyeb.ieltspractice.feature.auth.stepOf
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AuthLogicTest {
    @Test fun emailNeedsAnAddress() {
        assertEquals("Enter your email.", emailError("  "))
        assertEquals("Enter a valid email address, like name@example.com.", emailError("maya@example"))
        assertNull(emailError(" maya@example.com "))
    }

    @Test fun signUpSaysWhyEachFieldIsWrong() {
        val e = signUpErrors("", "maya@example.com", "short", "shorter")
        assertEquals("Enter your name.", e.name)
        assertNull(e.email)
        assertEquals("Use at least 8 characters.", e.password)
        assertEquals(MISMATCH, e.confirm)
        assertFalse(e.ok)
        assertTrue(signUpErrors("Maya", "maya@example.com", "longenough", "longenough").ok)
    }

    @Test fun signInOnlyNeedsSomething() {
        assertEquals("Enter your password.", signInErrors("maya@example.com", "").password)
        assertTrue(signInErrors("maya@example.com", "x").ok)
    }

    @Test fun resetNeedsSixDigitsAndAPair() {
        assertEquals("Enter all 6 digits.", resetErrors("123", "longenough", "longenough").code)
        assertTrue(resetErrors("123456", "longenough", "longenough").ok)
        assertNull(codeError("123456"))
    }

    @Test fun liveMismatchIgnoresAPrefix() {
        assertNull(liveMismatch("password1", "pass"))
        assertNull(liveMismatch("password1", ""))
        assertEquals(MISMATCH, liveMismatch("password1", "pasx"))
    }

    @Test fun codesKeepDigitsOnly() {
        assertEquals("123456", otpDigits("12 34-56 78"))
        assertEquals("", otpDigits("abc"))
        assertTrue(isCodeError("OTP_EXPIRED"))
        assertFalse(isCodeError("too_many_requests"))
        assertEquals("That code has expired. Request a new one.", otpError(400, "OTP_EXPIRED", ""))
        assertEquals("Too many requests. Wait a minute, then try again.", otpError(429, null, ""))
    }

    @Test fun onboardingFollowsTheAccountNotTheDevice() {
        assertTrue(needsOnboarding(AppSettings(), doneHere = false))
        assertFalse(needsOnboarding(AppSettings(), doneHere = true))
        assertFalse(needsOnboarding(AppSettings(targetBand = 6.5), doneHere = false))
        assertFalse(needsOnboarding(AppSettings(examDate = "2027-01-10"), doneHere = false))
    }

    @Test fun sendFailureKeepsTheReason() {
        assertEquals("rate_limited", sendFailureKind(ApiError(429, "Too many requests")))
        assertEquals("unavailable", sendFailureKind(ApiError(503, "Down")))
        assertEquals("network", sendFailureKind(ApiError(0, "Offline")))
        assertEquals("network", sendFailureKind(java.io.IOException()))
        assertEquals("", sendFailureKind(ApiError(400, "Bad email")))
        assertTrue(emailStatusLine(EmailStatus("failed", error = "rate_limited"), 0).first.contains("busy"))
    }

    @Test fun routeModeMapsToAStep() {
        assertEquals(AuthStep.SignUp, stepOf("signup"))
        assertEquals(AuthStep.SignIn, stepOf(null))
        assertEquals(AuthStep.SignIn, stepOf("nonsense"))
        assertTrue(AuthStep.SignIn.top)
        assertFalse(AuthStep.Verify.top)
    }

    @Test fun statusLine() {
        assertEquals("Sending the code…" to false, emailStatusLine(null, 0))
        val sent = EmailStatus("sent", sentAt = "2026-10-03T09:00:00Z", maskedEmail = "m•••@example.com")
        assertEquals("Code sent to m•••@example.com at 09:00. Check your spam folder if it doesn't show up." to false, emailStatusLine(sent, 0, ZoneId.of("UTC")))
        assertTrue(emailStatusLine(EmailStatus("failed", error = "rejected"), 0).second)
    }
}
