package com.soyeb.ieltspractice.feature.streak

import com.soyeb.ieltspractice.nav.ScreenSpec
import com.soyeb.ieltspractice.nav.StreakRoute
import com.soyeb.ieltspractice.nav.TodayTab

/**
 * Screenshot/demo rows for this feature (merged into nav/ScreenCatalog.kt). Data: data/demo/DemoToday.kt `streak`.
 * Today with the card is the shell's own "today" row. streak-celebration: the daily sheet over Today.
 */
val streakScreens: List<ScreenSpec> = listOf(
    ScreenSpec("streak", StreakRoute),
    ScreenSpec("streak-celebration", TodayTab),
)
