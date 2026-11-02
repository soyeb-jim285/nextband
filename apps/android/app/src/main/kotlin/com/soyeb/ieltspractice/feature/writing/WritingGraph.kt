package com.soyeb.ieltspractice.feature.writing

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.soyeb.ieltspractice.nav.Nav
import com.soyeb.ieltspractice.nav.WritingTab
import com.soyeb.ieltspractice.nav.WritingTask

fun NavGraphBuilder.writingGraph(nav: Nav) {
    composable<WritingTab> { WritingHub(nav) }
    composable<WritingTask> { entry -> WritingTaskScreen(entry.toRoute<WritingTask>(), nav) }
}
