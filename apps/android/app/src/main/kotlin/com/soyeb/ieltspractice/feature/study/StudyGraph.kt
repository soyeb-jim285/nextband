package com.soyeb.ieltspractice.feature.study

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.soyeb.ieltspractice.nav.Bank
import com.soyeb.ieltspractice.nav.CoachRoute
import com.soyeb.ieltspractice.nav.Mistakes
import com.soyeb.ieltspractice.nav.Nav
import com.soyeb.ieltspractice.nav.Review

fun NavGraphBuilder.studyGraph(nav: Nav) {
    composable<Review> { entry -> ReviewScreen(nav, entry.toRoute<Review>()) }
    composable<Mistakes> { entry -> MistakesScreen(nav, entry.toRoute<Mistakes>()) }
    composable<CoachRoute> { CoachScreen(nav) }
    composable<Bank> { entry -> BankScreen(nav, entry.toRoute<Bank>()) }
}
