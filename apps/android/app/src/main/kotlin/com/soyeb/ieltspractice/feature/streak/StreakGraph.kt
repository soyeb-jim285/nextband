package com.soyeb.ieltspractice.feature.streak

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.soyeb.ieltspractice.nav.Nav
import com.soyeb.ieltspractice.nav.StreakRoute

fun NavGraphBuilder.streakGraph(nav: Nav) {
    composable<StreakRoute> { StreakScreen(nav) }
}
