package com.soyeb.ieltspractice.feature.today

import com.soyeb.ieltspractice.design.Shared
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.soyeb.ieltspractice.nav.Nav
import com.soyeb.ieltspractice.nav.TodayTab

fun NavGraphBuilder.todayGraph(nav: Nav) {
    composable<TodayTab> { Shared { TodayRoute(nav) } }
}
