package com.soyeb.ieltspractice.feature.history

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.soyeb.ieltspractice.nav.History
import com.soyeb.ieltspractice.nav.Nav

fun NavGraphBuilder.historyGraph(nav: Nav) {
    composable<History> { entry -> HistoryScreen(nav, entry.toRoute<History>().skill) }
}
