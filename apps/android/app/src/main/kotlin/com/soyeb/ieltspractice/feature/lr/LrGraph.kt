package com.soyeb.ieltspractice.feature.lr

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.soyeb.ieltspractice.feature.StubPushed
import com.soyeb.ieltspractice.feature.StubTab
import com.soyeb.ieltspractice.nav.Nav
import com.soyeb.ieltspractice.nav.ListeningTab
import com.soyeb.ieltspractice.nav.ReadingTab
import com.soyeb.ieltspractice.nav.LrTestRoute
import com.soyeb.ieltspractice.nav.LrRun
import com.soyeb.ieltspractice.nav.LrResult
import com.soyeb.ieltspractice.nav.Spelling

fun NavGraphBuilder.lrGraph(nav: Nav) {
    composable<ListeningTab> { StubTab("Listening") }
    composable<ReadingTab> { StubTab("Reading") }
    composable<LrTestRoute> { StubPushed("Test", nav) }
    composable<LrRun> { StubPushed("Test", nav) }
    composable<LrResult> { StubPushed("Result", nav) }
    composable<Spelling> { StubPushed("Spelling", nav) }
}
