package com.soyeb.ieltspractice

import android.app.UiModeManager
import android.graphics.Color as AColor
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import com.soyeb.ieltspractice.design.NbMotion
import androidx.compose.animation.fadeIn
import com.soyeb.ieltspractice.design.LocalSharedScope
import androidx.compose.ui.unit.IntOffset
import androidx.compose.animation.scaleIn
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import com.soyeb.ieltspractice.design.components.NbSplash
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
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import com.soyeb.ieltspractice.nav.Auth
import com.soyeb.ieltspractice.nav.BottomBar
import com.soyeb.ieltspractice.nav.GateHost
import com.soyeb.ieltspractice.nav.LocalNav
import com.soyeb.ieltspractice.nav.Nav
import com.soyeb.ieltspractice.nav.NavHolder
import com.soyeb.ieltspractice.nav.Plan
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
    // API 31+: hand the saved choice to the system, so the splash, window background and first frame (via
    // isSystemInDarkTheme) already match it on the next launch instead of flipping when DataStore emits.
    // Collects stored values only, never the "system" placeholder above. ponytail: API 26-30 keep the one-frame flip.
    LaunchedEffect(activity) {
        if (activity == null || demo != null || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return@LaunchedEffect
        val ui = activity.getSystemService(UiModeManager::class.java) ?: return@LaunchedEffect
        graph.prefs.appearance.collect { a ->
            ui.setApplicationNightMode(when (a) { "dark" -> UiModeManager.MODE_NIGHT_YES; "light" -> UiModeManager.MODE_NIGHT_NO; else -> UiModeManager.MODE_NIGHT_AUTO })
        }
    }
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
            // Once per process (saveable: not again on rotation); never in demo/screenshot runs (demo screen "splash" forces it, for the video). It also covers session restore.
            var splash by rememberSaveable { mutableStateOf(demo == null || demo.screen == "splash") }
            var reveal by rememberSaveable { mutableStateOf(!splash) }
            Box(Modifier.fillMaxSize()) {
                if (!ready || done == null || !reveal) Box(Modifier.fillMaxSize().background(NB.colors.bg))
                else {
                    val start = remember { demo?.screen?.let { startFor(it) } ?: if (done || signedIn) TodayTab else Welcome }
                    AppShell(graph, start)
                }
                if (splash) NbSplash(onReveal = { reveal = true }, onDone = { splash = false })
            }
        }
    }
}

private fun isTab(e: NavBackStackEntry) = Tab.entries.any { e.destination.hasRoute(it.route::class) }

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun AppShell(graph: AppGraph, start: Any) {
    val controller = rememberNavController()
    val holder = viewModel<NavHolder>()
    val nav = (holder.nav ?: Nav(controller, graph.api, graph.prefs, graph.scope).also { holder.nav = it }).also { it.controller = controller }
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

    // Back in the foreground: re-read the plan and limits (/api/me carries the quota), so Pro bought on nextband.app shows
    // without a restart. Also runs once when the shell first appears; never in demo/screenshot runs (fixtures stay put).
    val live = LocalDemo.current == null
    val pausedAt = remember { longArrayOf(0L) }
    LifecycleResumeEffect(hasAccount) {
        // Plan runs its own recheck on resume (with a "Checking…" state), so skip it there; skip too when /api/me just
        // answered (cold start: restoreSession has loaded it). Away 10 s or more (long enough to buy Pro on the website)
        // always re-reads, however fresh the last answer was; a quick permission dialog keeps the 30 s cache.
        val onPlan = controller.currentBackStackEntry?.destination?.hasRoute(Plan::class) == true
        val away = pausedAt[0] > 0 && System.currentTimeMillis() - pausedAt[0] >= 10_000
        if (hasAccount && live && !onPlan) graph.scope.launch { runCatching { graph.api.loadMeIfStale(if (away) 0L else 30_000L) } }
        onPauseOrDispose { pausedAt[0] = System.currentTimeMillis() }
    }

    // The server ended the session (expired account, purged guest): say so instead of quietly flipping to the guest state.
    LaunchedEffect(graph) {
        graph.api.sessionEnded.collect { wasAccount ->
            // Starting a test already opened sign-in with its own banner (Nav.begin): one signal, not two.
            if (controller.currentBackStackEntry?.destination?.hasRoute(Auth::class) == true) return@collect
            if (!wasAccount) snack.showSnackbar("Your guest session ended. Your next test starts a new one.")
            else if (snack.showSnackbar(
                    "You were signed out. Sign in again to see your results.", actionLabel = "Sign in", duration = SnackbarDuration.Long,
                ) == SnackbarResult.ActionPerformed
            ) nav.go(Auth("signin", Nav.SESSION_ENDED))
        }
    }

    val d = if (reduced) 0 else 1
    // Push: the new screen slides in from the right edge (a third of the way) over the old one, which drifts left and
    // fades a little (parallax). Pop mirrors it; predictive back scrubs the same transition with the gesture.
    // Tab to tab: fade through with a slight 0.98 -> 1 scale, no sideways motion (tabs are peers, not a stack).
    val spec = { ms: Int -> tween<IntOffset>(ms * d, easing = NbMotion.EaseOut) }
    val fspec = { ms: Int, delay: Int -> tween<Float>(ms * d, delayMillis = delay * d, easing = NbMotion.EaseOut) }
    val push: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        if (isTab(targetState) && isTab(initialState)) fadeIn(fspec(200, 70)) + scaleIn(fspec(260, 70), initialScale = 0.98f)
        else slideInHorizontally(spec(320)) { it / 3 } + fadeIn(fspec(220, 0))
    }
    val pushOut: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        if (isTab(targetState) && isTab(initialState)) fadeOut(fspec(90, 0))
        else slideOutHorizontally(spec(320)) { -it / 10 } + fadeOut(tween(320 * d, easing = NbMotion.EaseOut), targetAlpha = 0.4f)
    }
    val pop: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        slideInHorizontally(spec(320)) { -it / 10 } + fadeIn(tween(320 * d, easing = NbMotion.EaseOut), initialAlpha = 0.4f)
    }
    val popOut: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        slideOutHorizontally(spec(320)) { it / 3 } + fadeOut(fspec(220, 60))
    }

    CompositionLocalProvider(LocalNav provides nav, LocalSnack provides snack) {
        Scaffold(
            // Landscape side nav bar / display cutout: keep every screen (lists, runner, bars) clear of them here, once.
            // windowInsetsPadding consumes the insets, so top bars below don't pad twice.
            Modifier.background(NB.colors.bg).windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
            containerColor = NB.colors.bg,
            contentWindowInsets = WindowInsets(0),
            bottomBar = { if (current != null) BottomBar(current, nav::openTab, todayBadge = due) },
            snackbarHost = { NbSnackbarHost(snack) },
        ) { pad ->
            SharedTransitionLayout { CompositionLocalProvider(LocalSharedScope provides this) {
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
            } }
        }
        GateHost(nav)
    }
}
