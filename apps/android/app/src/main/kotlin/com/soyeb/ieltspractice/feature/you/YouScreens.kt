package com.soyeb.ieltspractice.feature.you

import com.soyeb.ieltspractice.nav.NotificationsRoute
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
    // Notifications: the screen, the phone's permission off, and the time sheets. push-primer: the explainer over Today.
    ScreenSpec("notifications", NotificationsRoute, listOf("On", "Off", "QuietStart", "RemindAt")),
    ScreenSpec("push-primer", com.soyeb.ieltspractice.nav.TodayTab),
    // Debug renders the web channel (WEB_CHECKOUT true: prices and PayStation), so these are named "-web": never use them for
    // the Play listing or review. The store Plan screen: record with -Pnextband.channel=play.
    ScreenSpec("plan-web", Plan),
    ScreenSpec("plan-web-checkout", Plan, listOf("Checkout")),
)
