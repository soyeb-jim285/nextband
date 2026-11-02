package com.soyeb.ieltspractice.feature.you

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.soyeb.ieltspractice.feature.StubPushed
import com.soyeb.ieltspractice.feature.StubTab
import com.soyeb.ieltspractice.nav.Nav
import com.soyeb.ieltspractice.nav.You
import com.soyeb.ieltspractice.nav.SettingsRoute
import com.soyeb.ieltspractice.nav.Plan

fun NavGraphBuilder.youGraph(nav: Nav) {
    composable<You> { StubPushed("You", nav) }
    composable<SettingsRoute> { StubPushed("Settings", nav) }
    composable<Plan> { StubPushed("Plan", nav) }
}
