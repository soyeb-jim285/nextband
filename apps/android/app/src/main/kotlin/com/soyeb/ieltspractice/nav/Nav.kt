package com.soyeb.ieltspractice.nav

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import com.soyeb.ieltspractice.data.Prefs
import com.soyeb.ieltspractice.data.logic.FairUse
import com.soyeb.ieltspractice.data.logic.Gate
import com.soyeb.ieltspractice.data.model.Codes
import com.soyeb.ieltspractice.data.net.Api
import com.soyeb.ieltspractice.data.net.ApiError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * What screens use to move around. Screens get it as a parameter; they never see the NavHostController.
 *
 * - `nav.go(LrRun(id))`, `nav.back()`, `nav.replace(LrResult(id))` (runner → result), `nav.openTab(Tab.Reading)`
 * - `nav.startTest("writing") { nav.go(WritingTask("task2")) }`: quota check, fair-use note, guest session, then go.
 * - `nav.requireAccount("Create an account to keep your history") { nav.go(History()) }`
 */
class Nav(
    /** Rebound by AppShell after the activity is recreated (rotation): Nav itself outlives it in [NavHolder]. */
    var controller: NavHostController,
    private val api: Api,
    private val prefs: Prefs,
    private val scope: CoroutineScope,
) {
    fun go(route: Any) = controller.navigate(route) { launchSingleTop = true }

    /** Push [route] even when the top screen is the same kind (result → another result): single-top would swap it in place. */
    fun push(route: Any) = controller.navigate(route)

    /** The signed-in account's email ("" for a guest or before /me loads). */
    val email: String get() = api.me.value?.user?.email.orEmpty()

    /** False on a lone root (nothing beneath to go back to). */
    val canGoBack: Boolean get() = controller.previousBackStackEntry != null

    /** Pop one screen; on a lone root (e.g. Onboarding after `reset`) go to Today instead of emptying the stack. */
    fun back() {
        if (controller.previousBackStackEntry == null || !controller.popBackStack()) home()
    }

    /** Today's root, fresh: drops everything above it without saving it, so tapping Today later doesn't restore it. */
    fun home() {
        val today = todayId() ?: return reset(TodayTab) // e.g. a lone Onboarding root: Today becomes the only entry
        controller.navigate(TodayTab) {
            popUpTo(today)
            launchSingleTop = true
        }
    }

    /**
     * The tab anchor: the TodayTab entry, when on the stack. Not `findStartDestination()`: on first run the graph starts at
     * Welcome, which `reset(TodayTab)` removes, and popping to an absent destination pops nothing (tabs then pile up).
     */
    private fun todayId(): Int? = runCatching { controller.getBackStackEntry<TodayTab>().destination.id }.getOrNull()

    /** Open [route] in place of the current screen (a submitted test: the runner is replaced by its result). */
    fun replace(route: Any) = controller.navigate(route) {
        controller.currentBackStackEntry?.destination?.id?.let { popUpTo(it) { inclusive = true } }
    }

    /** Back to mock [mockId]'s hub: pop to it when it's underneath (it reloads on return), else open it in place of this screen. */
    fun toMock(mockId: String) {
        if (!controller.popBackStack(MockHub(mockId), inclusive = false)) replace(MockHub(mockId))
    }

    /** Clear everything and land on [route] (after sign-in / sign-out / onboarding). */
    fun reset(route: Any = TodayTab) = controller.navigate(route) {
        popUpTo(controller.graph.id) { inclusive = true }
        launchSingleTop = true
    }

    /**
     * Switch tab like the bottom bar does: each tab keeps its own stack. When [tab] is the one we're already in (a link
     * deep inside its stack), pop back to its root: saving and restoring its own stack would leave us where we are.
     */
    fun openTab(tab: Tab) {
        // Other tabs' stacks are saved off the back stack, so a non-Today tab root on it is the current tab; Today (the anchor,
        // always underneath) is current only when no other tab root is above it.
        val current = if (tab == Tab.Today) Tab.entries.none { it != Tab.Today && onStack(it.route) } else onStack(tab.route)
        if (current && controller.popBackStack(tab.route, inclusive = false)) return
        navigateTab(tab)
    }

    private fun onStack(route: Any): Boolean = runCatching { controller.getBackStackEntry(route) }.isSuccess

    private fun navigateTab(tab: Tab) = controller.navigate(tab.route) {
        popUpTo(todayId() ?: controller.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }

    // Sign-in that continues where the user was.

    private var pendingAfterAuth: (() -> Unit)? = null

    /** Guests can browse and take tests; personal data (history, mistakes, review, coach) needs an account. */
    fun requireAccount(reason: String, action: () -> Unit) {
        if (api.hasAccount.value) action() else {
            pendingAfterAuth = action
            go(Auth("signup", reason))
        }
    }

    companion object {
        const val SESSION_ENDED = "You were signed out. Sign in again to keep your results in your account."
    }

    /** Called by the auth flow when it finishes. Leaves it, then continues the pending action if the user signed in. */
    fun authFinished(signedIn: Boolean) {
        val action = pendingAfterAuth
        pendingAfterAuth = null
        back()
        if (signedIn) action?.invoke()
    }

    // Starting a test (docs/community.md): check the quota before anything is recorded or typed.

    /** The limit sheet / fair-use sheet over the current screen (drawn by GateHost). Null = none. */
    var gate by mutableStateOf<Gate?>(null)
    /** True while the quota is being checked before a test starts (buttons show a spinner). */
    var checking by mutableStateOf(false)
        private set

    fun dismissGate() { gate = null }

    /** Start a speaking or writing test. Listening & Reading are free and need no check: just `go`. */
    fun startTest(skill: String, go: () -> Unit) = begin(skill, live = false, go)

    /** The live examiner (needs `quota.live`). */
    fun startLive(go: () -> Unit) = begin("examiner", live = true, go)

    private fun begin(skill: String, live: Boolean, go: () -> Unit) {
        if (checking) return
        checking = true
        // Main: the app scope runs on Dispatchers.Default, and go() navigates (NavController is main-thread only).
        scope.launch(Dispatchers.Main.immediate) {
            try {
                check(skill, live, go)
            } catch (e: CancellationException) {
                throw e
            } catch (e: ApiError) {
                // An account session expired (Api.refreshQuota): sign in again and carry on, rather than start the test as a new guest.
                // Closing sign-in drops the test; tapping it again then continues as a guest by choice.
                if (e.status == 401) {
                    pendingAfterAuth = { begin(skill, live, go) }
                    this@Nav.go(Auth("signin", SESSION_ENDED))
                } else gate = Gate.Message("Couldn't check your tests", e.message)
            } catch (e: Exception) {
                gate = Gate.Message("Couldn't check your tests", e.message ?: "Check your connection and try again.")
            } finally {
                checking = false
            }
        }
    }

    private suspend fun check(skill: String, live: Boolean, go: () -> Unit) {
        val q = api.refreshQuota()
        val guest = !api.hasAccount.value
        if (live && !q.live) {
            gate = Gate.Blocked(Codes.LIVE, skill, q.tier, null)
            return
        }
        val s = q.of(skill)
        s.blocked?.let { gate = Gate.Blocked(it, skill, q.tier, s.resetAt); return }
        val user = if (guest) "guest" else api.me.value?.user?.id ?: "account"
        val key = FairUse.ackKey(user)
        if (q.free && prefs.lastSeenFairUseDay.first() != key) {
            gate = Gate.FairUse(skill, q, guest) {
                gate = null
                scope.launch(Dispatchers.Main.immediate) {
                    prefs.setLastSeenFairUseDay(key)
                    try {
                        proceed(skill, go)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        gate = Gate.Message("Couldn't start your test", e.message ?: "Check your connection and try again.")
                    }
                }
            }
            return
        }
        proceed(skill, go)
    }

    private suspend fun proceed(skill: String, go: () -> Unit) {
        try {
            api.ensureSession()
        } catch (e: CancellationException) {
            throw e
        } catch (e: ApiError) {
            if (!reportBlocked(e, skill)) gate = Gate.Message("Couldn't start your test", e.message)
            return
        }
        go()
    }

    /**
     * A request refused with a limit code (quota ran out elsewhere, scoring paused, too fast): show the same sheet as at start.
     * [keep] says what was kept ("Your essay is still here."). False when [e] is not a limit error.
     */
    fun reportBlocked(e: ApiError, skill: String, keep: String? = null): Boolean {
        if (!e.isLimit) return false
        val q = api.quota.value
        val tier = e.tier ?: if (!api.hasAccount.value) "guest" else q?.tier ?: "community"
        gate = Gate.Blocked(e.code.orEmpty(), e.skill ?: skill, tier, e.resetAt, keep)
        return true
    }
}

/**
 * One [Nav] per activity, kept across rotation so a pending sign-in continuation and an open limit sheet survive it.
 * ponytail: lost on process death (lambdas aren't saveable); carry the continuation as a route arg if that matters.
 */
class NavHolder : ViewModel() { var nav: Nav? = null }

/** The app's [Nav], for deep components that would otherwise thread it through many layers. Screens still take `nav` as a parameter. */
val LocalNav = androidx.compose.runtime.staticCompositionLocalOf<Nav> { error("LocalNav not provided") }
