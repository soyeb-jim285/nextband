package com.soyeb.ieltspractice.feature.today

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.soyeb.ieltspractice.feature.StubPushed
import com.soyeb.ieltspractice.feature.StubTab
import com.soyeb.ieltspractice.nav.Nav
import com.soyeb.ieltspractice.nav.TodayTab

fun NavGraphBuilder.todayGraph(nav: Nav) {
    composable<TodayTab> { StubTab("Today") }
}
