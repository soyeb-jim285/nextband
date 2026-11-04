package com.soyeb.ieltspractice.feature.auth

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.soyeb.ieltspractice.nav.Auth
import com.soyeb.ieltspractice.nav.Nav
import com.soyeb.ieltspractice.nav.Onboarding
import com.soyeb.ieltspractice.nav.Welcome

fun NavGraphBuilder.authGraph(nav: Nav) {
    composable<Welcome> { WelcomeScreen(nav) }
    composable<Auth> { entry ->
        val r = entry.toRoute<Auth>()
        AuthScreen(nav, r.mode, r.reason, r.email)
    }
    composable<Onboarding> { OnboardingScreen(nav) }
}
