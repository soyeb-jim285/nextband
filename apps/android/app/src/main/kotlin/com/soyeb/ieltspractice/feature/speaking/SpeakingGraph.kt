package com.soyeb.ieltspractice.feature.speaking

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.soyeb.ieltspractice.nav.LiveExam
import com.soyeb.ieltspractice.nav.Nav
import com.soyeb.ieltspractice.nav.SpeakingSession
import com.soyeb.ieltspractice.nav.SpeakingTab

fun NavGraphBuilder.speakingGraph(nav: Nav) {
    composable<SpeakingTab> { SpeakingTabScreen(nav) }
    composable<SpeakingSession> { e -> SpeakingSessionScreen(e.toRoute<SpeakingSession>(), nav) }
    composable<LiveExam> { e -> LiveExamScreen(e.toRoute<LiveExam>(), nav) }
}
