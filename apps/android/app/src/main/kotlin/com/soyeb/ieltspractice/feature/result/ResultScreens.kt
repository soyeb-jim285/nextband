package com.soyeb.ieltspractice.feature.result

import com.soyeb.ieltspractice.nav.AttemptResult
import com.soyeb.ieltspractice.nav.ScreenSpec

/** Screenshot/demo rows for this feature (merged into nav/ScreenCatalog.kt). Fixture attempts: aw1 writing done (retry of aw0), as3 analysing, as4 failed, as1 speaking done. */
val resultScreens: List<ScreenSpec> = listOf(
    ScreenSpec("result-writing", AttemptResult.of("aw1"), listOf("Overview", "Overview-Sheet", "Essay", "Essay-Sheet", "Structure", "Language", "Improve")),
    ScreenSpec("result-analysing", AttemptResult.of("as3")),
    ScreenSpec("result-failed", AttemptResult.of("as4")),
    ScreenSpec("guest-result", AttemptResult.of("as1")),
)
