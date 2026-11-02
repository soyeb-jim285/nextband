package com.soyeb.ieltspractice.feature.study

import com.soyeb.ieltspractice.nav.Bank
import com.soyeb.ieltspractice.nav.CoachRoute
import com.soyeb.ieltspractice.nav.Mistakes
import com.soyeb.ieltspractice.nav.Review
import com.soyeb.ieltspractice.nav.ScreenSpec

/** Screenshot/demo rows for this feature (merged into nav/ScreenCatalog.kt). Data comes from data/demo/DemoStudy.kt. */
val studyScreens: List<ScreenSpec> = listOf(
    ScreenSpec("review", Review, listOf("Question", "Revealed", "Done", "Empty")),
    ScreenSpec("mistakes", Mistakes(), listOf("All", "Sheet")),
    ScreenSpec("coach", CoachRoute, listOf("Ready", "Updating", "Empty")),
    ScreenSpec("bank", Bank("speaking")),
    ScreenSpec("bank-writing", Bank("writing")),
    ScreenSpec("bank-sheet", Bank("speaking")),
)
