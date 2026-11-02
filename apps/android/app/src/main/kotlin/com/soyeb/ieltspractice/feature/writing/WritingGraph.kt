package com.soyeb.ieltspractice.feature.writing

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.soyeb.ieltspractice.feature.StubPushed
import com.soyeb.ieltspractice.feature.StubTab
import com.soyeb.ieltspractice.nav.Nav
import com.soyeb.ieltspractice.nav.WritingTab
import com.soyeb.ieltspractice.nav.WritingTask

fun NavGraphBuilder.writingGraph(nav: Nav) {
    composable<WritingTab> { StubTab("Writing") }
    composable<WritingTask> { StubPushed("Writing", nav) }
}
