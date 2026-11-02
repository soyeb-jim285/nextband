package com.soyeb.ieltspractice.feature.auth

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.soyeb.ieltspractice.feature.StubPushed
import com.soyeb.ieltspractice.feature.StubTab
import com.soyeb.ieltspractice.nav.Nav
import com.soyeb.ieltspractice.nav.Welcome
import com.soyeb.ieltspractice.nav.Auth
import com.soyeb.ieltspractice.nav.Onboarding

fun NavGraphBuilder.authGraph(nav: Nav) {
    composable<Welcome> { StubPushed("Welcome", nav) }
    composable<Auth> { StubPushed("Sign in", nav) }
    composable<Onboarding> { StubPushed("Onboarding", nav) }
}
