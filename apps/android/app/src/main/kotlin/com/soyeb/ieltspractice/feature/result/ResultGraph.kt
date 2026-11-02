package com.soyeb.ieltspractice.feature.result

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.soyeb.ieltspractice.feature.StubPushed
import com.soyeb.ieltspractice.feature.StubTab
import com.soyeb.ieltspractice.nav.Nav
import com.soyeb.ieltspractice.nav.AttemptResult

fun NavGraphBuilder.resultGraph(nav: Nav) {
    composable<AttemptResult> { StubPushed("Result", nav) }
}
