package com.soyeb.ieltspractice.feature.mock

import com.soyeb.ieltspractice.data.demo.DemoStudy
import com.soyeb.ieltspractice.nav.MockHub
import com.soyeb.ieltspractice.nav.MockResult
import com.soyeb.ieltspractice.nav.MockStart
import com.soyeb.ieltspractice.nav.ScreenSpec

/** Screenshot/demo rows for this feature (merged into nav/ScreenCatalog.kt). Data comes from data/demo/DemoStudy.kt. */
val mockScreens: List<ScreenSpec> = listOf(
    ScreenSpec("mock-start", MockStart),
    ScreenSpec("mock-start-resume", MockStart),
    // Listening = a fresh mock; Choice = the Speaking mode sheet open.
    ScreenSpec("mock-hub", MockHub(DemoStudy.MOCK_ID), listOf("Listening", "Transition", "Writing", "Speaking", "Choice", "Done")),
    ScreenSpec("mock-result", MockResult(DemoStudy.MOCK_ID)),
)
