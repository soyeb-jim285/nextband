package com.soyeb.ieltspractice.feature.lr

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.soyeb.ieltspractice.design.Skill
import com.soyeb.ieltspractice.nav.ListeningTab
import com.soyeb.ieltspractice.nav.LrResult
import com.soyeb.ieltspractice.nav.LrRun
import com.soyeb.ieltspractice.nav.LrTestRoute
import com.soyeb.ieltspractice.nav.Nav
import com.soyeb.ieltspractice.nav.ReadingTab
import com.soyeb.ieltspractice.nav.Spelling

fun NavGraphBuilder.lrGraph(nav: Nav) {
    composable<ListeningTab> { LrHubScreen(Skill.Listening, nav) }
    composable<ReadingTab> { LrHubScreen(Skill.Reading, nav) }
    composable<LrTestRoute> { entry ->
        val r = entry.toRoute<LrTestRoute>()
        LrTestScreen(r.testId, r.skill, nav)
    }
    composable<LrRun> { entry -> LrRunScreen(entry.toRoute<LrRun>(), nav) }
    composable<LrResult> { entry -> LrResultScreen(entry.toRoute<LrResult>(), nav) }
    composable<Spelling> { SpellingScreen(nav) }
}
