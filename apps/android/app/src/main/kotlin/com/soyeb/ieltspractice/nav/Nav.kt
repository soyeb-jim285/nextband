package com.soyeb.ieltspractice.nav

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
    private val controller: NavHostController,
    private val api: Api,
    private val prefs: Prefs,
    private val scope: CoroutineScope,
) {
    fun go(route: Any) = controller.navigate(route) { launchSingleTop = true }

    fun back() {
        if (!controller.popBackStack()) openTab(Tab.Today)
    }

    /** Open [route] in place of the current screen (a submitted test: the runner is replaced by its result). */
    fun replace(route: Any) = controller.navigate(route) {
        controller.currentBackStackEntry?.destination?.id?.let { popUpTo(it) { inclusive = true } }
    }

    /** Clear everything and land on [route] (after sign-in / sign-out / onboarding). */
    fun reset(route: Any = TodayTab) = controller.navigate(route) {
        popUpTo(controller.graph.id) { inclusive = true }
        launchSingleTop = true
    }

    /** Switch tab like the bottom bar does: each tab keeps its own stack. */
    fun openTab(tab: Tab) = controller.navigate(tab.route) {
        popUpTo(controller.graph.findStartDestination().id) { saveState = true }
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
        scope.launch {
            try {
                check(skill, live, go)
            } catch (e: CancellationException) {
                throw e
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
                scope.launch {
                    prefs.setLastSeenFairUseDay(key)
                    proceed(skill, go)
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

/** The app's [Nav], for deep components that would otherwise thread it through many layers. Screens still take `nav` as a parameter. */
val LocalNav = androidx.compose.runtime.staticCompositionLocalOf<Nav> { error("LocalNav not provided") }
