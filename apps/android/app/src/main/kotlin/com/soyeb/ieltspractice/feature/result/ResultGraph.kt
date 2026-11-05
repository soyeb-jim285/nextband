package com.soyeb.ieltspractice.feature.result

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.soyeb.ieltspractice.feature.streak.StreakAfterPractice
import com.soyeb.ieltspractice.nav.AttemptResult
import com.soyeb.ieltspractice.nav.Nav

fun NavGraphBuilder.resultGraph(nav: Nav) {
    composable<AttemptResult> { entry ->
        val r = entry.toRoute<AttemptResult>()
        StreakAfterPractice()
        if (r.fresh) com.soyeb.ieltspractice.feature.push.PushAskAfterResult()
        AttemptResultScreen(r.ids, nav)
    }
}
