package com.soyeb.ieltspractice.feature.speaking

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.soyeb.ieltspractice.feature.StubPushed
import com.soyeb.ieltspractice.feature.StubTab
import com.soyeb.ieltspractice.nav.Nav
import com.soyeb.ieltspractice.nav.SpeakingTab
import com.soyeb.ieltspractice.nav.SpeakingSession
import com.soyeb.ieltspractice.nav.LiveExam

fun NavGraphBuilder.speakingGraph(nav: Nav) {
    composable<SpeakingTab> { StubTab("Speaking") }
    composable<SpeakingSession> { StubPushed("Speaking", nav) }
    composable<LiveExam> { StubPushed("Live examiner", nav) }
}
