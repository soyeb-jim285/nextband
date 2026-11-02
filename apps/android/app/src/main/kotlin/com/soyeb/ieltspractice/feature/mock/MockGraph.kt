package com.soyeb.ieltspractice.feature.mock

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.soyeb.ieltspractice.feature.StubPushed
import com.soyeb.ieltspractice.feature.StubTab
import com.soyeb.ieltspractice.nav.Nav
import com.soyeb.ieltspractice.nav.MockStart
import com.soyeb.ieltspractice.nav.MockHub
import com.soyeb.ieltspractice.nav.MockResult

fun NavGraphBuilder.mockGraph(nav: Nav) {
    composable<MockStart> { StubPushed("Full mock", nav) }
    composable<MockHub> { StubPushed("Full mock", nav) }
    composable<MockResult> { StubPushed("Mock result", nav) }
}
