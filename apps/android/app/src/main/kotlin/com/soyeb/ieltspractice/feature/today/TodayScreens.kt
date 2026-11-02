package com.soyeb.ieltspractice.feature.today

import com.soyeb.ieltspractice.nav.ScreenSpec
import com.soyeb.ieltspractice.nav.TodayTab

/**
 * Screenshot/demo rows for this feature (merged into nav/ScreenCatalog.kt). The shell's own "today" row stays.
 * today-lower: scrolled to the bottom; today-nomock: no open mock (start card + last mock band); today-new: a first-run account;
 * guest-today: signed out; guest-recent-today: a guest session with tests taken.
 */
val todayScreens: List<ScreenSpec> = listOf(
    ScreenSpec("today-lower", TodayTab),
    ScreenSpec("today-nomock", TodayTab),
    ScreenSpec("today-new", TodayTab),
    ScreenSpec("guest-today", TodayTab),
    ScreenSpec("guest-recent-today", TodayTab),
)
