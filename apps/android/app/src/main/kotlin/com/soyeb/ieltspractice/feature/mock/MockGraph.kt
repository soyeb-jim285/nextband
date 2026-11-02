package com.soyeb.ieltspractice.feature.mock

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.soyeb.ieltspractice.nav.MockHub
import com.soyeb.ieltspractice.nav.MockResult
import com.soyeb.ieltspractice.nav.MockStart
import com.soyeb.ieltspractice.nav.Nav

fun NavGraphBuilder.mockGraph(nav: Nav) {
    composable<MockStart> { MockStartScreen(nav) }
    composable<MockHub> { entry -> MockHubScreen(nav, entry.toRoute<MockHub>()) }
    composable<MockResult> { entry -> MockResultScreen(nav, entry.toRoute<MockResult>()) }
}
