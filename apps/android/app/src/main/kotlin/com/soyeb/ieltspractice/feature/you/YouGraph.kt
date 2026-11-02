package com.soyeb.ieltspractice.feature.you

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.soyeb.ieltspractice.nav.Nav
import com.soyeb.ieltspractice.nav.Plan
import com.soyeb.ieltspractice.nav.SettingsRoute
import com.soyeb.ieltspractice.nav.You

fun NavGraphBuilder.youGraph(nav: Nav) {
    composable<You> { YouScreen(nav) }
    composable<SettingsRoute> { SettingsScreen(nav) }
    composable<Plan> { PlanScreen(nav) }
}
