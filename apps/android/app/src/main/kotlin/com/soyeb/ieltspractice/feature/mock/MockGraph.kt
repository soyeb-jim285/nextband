package com.soyeb.ieltspractice.feature.mock

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraphBuilder
import com.soyeb.ieltspractice.design.NB
import com.soyeb.ieltspractice.design.Shared
import com.soyeb.ieltspractice.design.sharedBox
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.soyeb.ieltspractice.nav.MockHub
import com.soyeb.ieltspractice.nav.MockResult
import com.soyeb.ieltspractice.nav.MockStart
import com.soyeb.ieltspractice.nav.Nav

/** Shared-element key: the Today mock card and the mock screens. */
const val MOCK_HERO = "mock-hero"

fun NavGraphBuilder.mockGraph(nav: Nav) {
    // The Today mock card grows into these two screens (container transform, see design/Motion.kt sharedBox).
    composable<MockStart> { Shared { Box(Modifier.fillMaxSize().sharedBox(MOCK_HERO, NB.shape.card)) { MockStartScreen(nav) } } }
    composable<MockHub> { entry -> Shared { Box(Modifier.fillMaxSize().sharedBox(MOCK_HERO, NB.shape.card)) { MockHubScreen(nav, entry.toRoute<MockHub>()) } } }
    composable<MockResult> { entry -> MockResultScreen(nav, entry.toRoute<MockResult>()) }
}
