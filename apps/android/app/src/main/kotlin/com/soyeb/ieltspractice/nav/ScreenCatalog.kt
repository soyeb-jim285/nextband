package com.soyeb.ieltspractice.nav

/**
 * Every screen by its demo/screenshot name. Drives demo launch (`--es screen <name>`) AND the Roborazzi ScreenshotTest
 * (light + dark each). [variants] become `LocalDemo.current?.tab` (sub-tab, scrolled state, open sheet…), one shot each.
 *
 * Name conventions read by data/demo/DemoCommunity: "guest-*" = signed out, "gate-*" / "quota-*" = limit states.
 * To cover a new screen or state: add a row. Nothing else.
 */
data class ScreenSpec(val name: String, val start: Any, val variants: List<String> = emptyList())

val screenCatalog: List<ScreenSpec> = listOf(
    ScreenSpec("today", TodayTab),
    ScreenSpec("listening", ListeningTab),
    ScreenSpec("reading", ReadingTab),
    ScreenSpec("writing", WritingTab),
    ScreenSpec("speaking", SpeakingTab),
    // Feature screens: each feature lists its own rows in feature/<name>/<Name>Screens.kt.
) + com.soyeb.ieltspractice.feature.auth.authScreens + com.soyeb.ieltspractice.feature.today.todayScreens + com.soyeb.ieltspractice.feature.lr.lrScreens + com.soyeb.ieltspractice.feature.writing.writingScreens + com.soyeb.ieltspractice.feature.speaking.speakingScreens + com.soyeb.ieltspractice.feature.result.resultScreens + com.soyeb.ieltspractice.feature.mock.mockScreens + com.soyeb.ieltspractice.feature.study.studyScreens + com.soyeb.ieltspractice.feature.history.historyScreens + com.soyeb.ieltspractice.feature.you.youScreens + com.soyeb.ieltspractice.feature.streak.streakScreens

/** The start destination for a demo screen name (tab roots fall back to Today). */
fun startFor(screen: String): Any = screenCatalog.firstOrNull { it.name == screen }?.start ?: TodayTab
