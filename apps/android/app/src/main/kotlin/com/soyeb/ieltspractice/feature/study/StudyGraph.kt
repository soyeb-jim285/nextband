package com.soyeb.ieltspractice.feature.study

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.soyeb.ieltspractice.feature.StubPushed
import com.soyeb.ieltspractice.feature.StubTab
import com.soyeb.ieltspractice.nav.Nav
import com.soyeb.ieltspractice.nav.Review
import com.soyeb.ieltspractice.nav.Mistakes
import com.soyeb.ieltspractice.nav.CoachRoute
import com.soyeb.ieltspractice.nav.Bank

fun NavGraphBuilder.studyGraph(nav: Nav) {
    composable<Review> { StubPushed("Review", nav) }
    composable<Mistakes> { StubPushed("Mistakes", nav) }
    composable<CoachRoute> { StubPushed("Coach", nav) }
    composable<Bank> { StubPushed("Prompt bank", nav) }
}
