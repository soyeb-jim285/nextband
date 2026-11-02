package com.soyeb.ieltspractice.feature.history

import com.soyeb.ieltspractice.nav.History
import com.soyeb.ieltspractice.nav.ScreenSpec

/** Screenshot/demo rows for this feature (merged into nav/ScreenCatalog.kt). */
val historyScreens: List<ScreenSpec> = listOf(
    ScreenSpec("history", History()),
    ScreenSpec("history-listening", History("listening")),
    ScreenSpec("guest-history", History()),
)
