package com.soyeb.ieltspractice.feature.history

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.soyeb.ieltspractice.feature.StubPushed
import com.soyeb.ieltspractice.feature.StubTab
import com.soyeb.ieltspractice.nav.Nav
import com.soyeb.ieltspractice.nav.History

fun NavGraphBuilder.historyGraph(nav: Nav) {
    composable<History> { StubPushed("History", nav) }
}
