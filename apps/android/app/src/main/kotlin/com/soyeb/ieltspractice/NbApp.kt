package com.soyeb.ieltspractice

import android.graphics.Color as AColor
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.soyeb.ieltspractice.data.demo.DemoConfig
import com.soyeb.ieltspractice.design.LocalReducedMotion
import com.soyeb.ieltspractice.design.NB
import com.soyeb.ieltspractice.design.NbTheme
import com.soyeb.ieltspractice.design.components.LocalSnack
import com.soyeb.ieltspractice.design.components.NbSnackbarHost
import com.soyeb.ieltspractice.feature.auth.authGraph
import com.soyeb.ieltspractice.feature.history.historyGraph
import com.soyeb.ieltspractice.feature.lr.lrGraph
import com.soyeb.ieltspractice.feature.mock.mockGraph
import com.soyeb.ieltspractice.feature.result.resultGraph
import com.soyeb.ieltspractice.feature.speaking.speakingGraph
import com.soyeb.ieltspractice.feature.study.studyGraph
import com.soyeb.ieltspractice.feature.today.todayGraph
import com.soyeb.ieltspractice.feature.writing.writingGraph
import com.soyeb.ieltspractice.feature.you.youGraph
import com.soyeb.ieltspractice.nav.BottomBar
import com.soyeb.ieltspractice.nav.GateHost
import com.soyeb.ieltspractice.nav.LocalNav
import com.soyeb.ieltspractice.nav.Nav
import com.soyeb.ieltspractice.nav.Tab
import com.soyeb.ieltspractice.nav.TodayTab
import com.soyeb.ieltspractice.nav.Welcome
import com.soyeb.ieltspractice.nav.startFor

/** The whole app: theme, one NavHost, the bottom bar on tab roots, the gate sheet and the snackbar host. */
@Composable
fun NbApp(graph: AppGraph, demo: DemoConfig? = null) {
    val appearance by graph.prefs.appearance.collectAsState(initial = "system")
    val systemDark = isSystemInDarkTheme()
    val dark = demo?.dark ?: when (appearance) { "dark" -> true; "light" -> false; else -> systemDark }

    // System bar icons follow the app theme (Settings > Appearance can override the device).
    val activity = LocalContext.current as? ComponentActivity
    LaunchedEffect(dark) {
        if (activity == null || demo != null) return@LaunchedEffect
        val style = if (dark) SystemBarStyle.dark(AColor.TRANSPARENT) else SystemBarStyle.light(AColor.TRANSPARENT, AColor.TRANSPARENT)
        activity.enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }

    CompositionLocalProvider(LocalGraph provides graph, LocalDemo provides demo) {
        NbTheme(dark) {
            LaunchedEffect(graph) { graph.api.restoreSession() }
            val ready by graph.api.ready.collectAsState()
            // null until read: render only the background so a returning user never sees Welcome flash.
            val onboarded by graph.prefs.onboardingDone.collectAsState(initial = null)
            val signedIn by graph.api.signedIn.collectAsState()
            val done = onboarded
            if (!ready || done == null) Box(Modifier.fillMaxSize().background(NB.colors.bg))
            else {
                val start = remember { demo?.screen?.let { startFor(it) } ?: if (done || signedIn) TodayTab else Welcome }
                AppShell(graph, start)
            }
        }
    }
}

private fun isTab(e: NavBackStackEntry) = Tab.entries.any { e.destination.hasRoute(it.route::class) }

@Composable
private fun AppShell(graph: AppGraph, start: Any) {
    val controller = rememberNavController()
    val nav = remember(controller) { Nav(controller, graph.api, graph.prefs, graph.scope) }
    val snack = remember { SnackbarHostState() }
    val entry by controller.currentBackStackEntryAsState()
    val current = Tab.entries.firstOrNull { t -> entry?.destination?.hasRoute(t.route::class) == true }
    val reduced = LocalReducedMotion.current

    // Due review cards badge on Today: refreshed whenever the user lands on a tab root.
    var due by remember { mutableIntStateOf(0) }
    val hasAccount by graph.api.hasAccount.collectAsState()
    LaunchedEffect(current, hasAccount) {
        if (current != null && hasAccount) due = runCatching { graph.api.dueCards().dueTotal }.getOrDefault(due)
        if (!hasAccount) due = 0
    }

    val d = if (reduced) 0 else 1
    val push: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        if (isTab(targetState) && isTab(initialState)) fadeIn(tween(180 * d, delayMillis = 60 * d))
        else slideInHorizontally(tween(300 * d)) { it / 8 } + fadeIn(tween(220 * d))
    }
    val pushOut: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        if (isTab(targetState) && isTab(initialState)) fadeOut(tween(90 * d))
        else slideOutHorizontally(tween(300 * d)) { -it / 12 } + fadeOut(tween(200 * d))
    }
    val pop: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        slideInHorizontally(tween(300 * d)) { -it / 12 } + fadeIn(tween(220 * d))
    }
    val popOut: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        slideOutHorizontally(tween(300 * d)) { it / 8 } + fadeOut(tween(200 * d))
    }

    CompositionLocalProvider(LocalNav provides nav, LocalSnack provides snack) {
        Scaffold(
            containerColor = NB.colors.bg,
            contentWindowInsets = WindowInsets(0),
            bottomBar = { if (current != null) BottomBar(current, nav::openTab, todayBadge = due) },
            snackbarHost = { NbSnackbarHost(snack) },
        ) { pad ->
            NavHost(
                controller, startDestination = start,
                modifier = Modifier.fillMaxSize().padding(bottom = pad.calculateBottomPadding()),
                enterTransition = push, exitTransition = pushOut, popEnterTransition = pop, popExitTransition = popOut,
            ) {
                authGraph(nav)
                todayGraph(nav)
                lrGraph(nav)
                writingGraph(nav)
                speakingGraph(nav)
                resultGraph(nav)
                mockGraph(nav)
                studyGraph(nav)
                historyGraph(nav)
                youGraph(nav)
            }
        }
        GateHost(nav)
    }
}
