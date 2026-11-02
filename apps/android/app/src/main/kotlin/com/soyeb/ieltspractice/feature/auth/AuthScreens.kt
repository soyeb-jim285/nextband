package com.soyeb.ieltspractice.feature.auth

import com.soyeb.ieltspractice.nav.Auth
import com.soyeb.ieltspractice.nav.Onboarding
import com.soyeb.ieltspractice.nav.ScreenSpec
import com.soyeb.ieltspractice.nav.Welcome

/**
 * Screenshot/demo rows for this feature (merged into nav/ScreenCatalog.kt). Variants pick the step.
 * AppGraph signs the demo out for welcome / auth / onboarding.
 */
val authScreens: List<ScreenSpec> = listOf(
    ScreenSpec("welcome", Welcome),
    ScreenSpec("auth", Auth("signin"), listOf("SignIn", "Signup", "Verify", "Forgot", "Reset")),
    ScreenSpec("onboarding", Onboarding, listOf("Type", "Band", "Date")),
)
