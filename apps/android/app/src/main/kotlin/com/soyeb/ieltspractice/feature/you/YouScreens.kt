package com.soyeb.ieltspractice.feature.you

import com.soyeb.ieltspractice.nav.Plan
import com.soyeb.ieltspractice.nav.ScreenSpec
import com.soyeb.ieltspractice.nav.SettingsRoute
import com.soyeb.ieltspractice.nav.You

/** Screenshot/demo rows for this feature (merged into nav/ScreenCatalog.kt). Variants open a sheet or dialog. */
val youScreens: List<ScreenSpec> = listOf(
    ScreenSpec("you", You),
    ScreenSpec("you-sheets", You, listOf("Band", "Type", "Feedback", "Legal")),
    ScreenSpec("guest-you", You),
    ScreenSpec("settings", SettingsRoute),
    ScreenSpec("settings-delete", SettingsRoute, listOf("Delete")),
    ScreenSpec("plan", Plan),
    ScreenSpec("plan-checkout", Plan, listOf("Checkout")),
)
