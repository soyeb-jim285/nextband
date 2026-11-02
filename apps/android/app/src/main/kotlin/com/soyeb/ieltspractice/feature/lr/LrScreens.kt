package com.soyeb.ieltspractice.feature.lr

import com.soyeb.ieltspractice.nav.ListeningTab
import com.soyeb.ieltspractice.nav.LrResult
import com.soyeb.ieltspractice.nav.LrRun
import com.soyeb.ieltspractice.nav.ReadingTab
import com.soyeb.ieltspractice.nav.ScreenSpec
import com.soyeb.ieltspractice.nav.Spelling

/**
 * Screenshot/demo rows for this feature (merged into nav/ScreenCatalog.kt). The variants are read as `LocalDemo.current?.tab`:
 * a scrolled-to question, an open sheet, a result tab. Fixtures: lra-r (reading exam, in progress), lra-l (listening practice),
 * lra-le (listening exam), lra-rs (reading result), lra-ls (listening result).
 */
val lrScreens: List<ScreenSpec> = listOf(
    // Hubs with the test sheet open (the plain hubs are "listening" and "reading" in the shell catalog).
    ScreenSpec("listening-sheet", ListeningTab),
    ScreenSpec("reading-sheet", ReadingTab),
    // Runners.
    ScreenSpec("lr-reading", LrRun("lra-r"), listOf("Passage", "Questions", "Matching", "WordBox", "Navigator", "Submit", "Highlight", "Settings")),
    ScreenSpec("lr-listening", LrRun("lra-l"), listOf("Practice", "Map", "Matching", "Navigator")),
    ScreenSpec("lr-listening-exam", LrRun("lra-le"), listOf("Gate", "Playing", "Review")),
    // Results.
    ScreenSpec("lr-result", LrResult("lra-rs"), listOf("Overview", "Answers", "Passage", "Detail")),
    ScreenSpec("lr-result-listening", LrResult("lra-ls"), listOf("Overview", "Answers", "Transcript", "Detail")),
    // Spelling drill.
    ScreenSpec("spelling", Spelling, listOf("Word", "Wrong", "Right", "Done")),
)
