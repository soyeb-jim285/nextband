package com.soyeb.ieltspractice

import android.content.Context
import android.util.Log
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.soyeb.ieltspractice.data.KeystoreTokenStore
import com.soyeb.ieltspractice.data.MemoryTokenStore
import com.soyeb.ieltspractice.data.PendingStore
import com.soyeb.ieltspractice.data.Prefs
import com.soyeb.ieltspractice.data.demo.DemoCommunity
import com.soyeb.ieltspractice.data.demo.DemoConfig
import com.soyeb.ieltspractice.data.demo.DemoFixtures
import com.soyeb.ieltspractice.data.demo.DemoInterceptor
import com.soyeb.ieltspractice.data.net.Api
import com.soyeb.ieltspractice.media.MediaCache
import com.soyeb.ieltspractice.media.Tts
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import java.io.File

/**
 * The app's long-lived objects (manual DI). Screens reach it with `LocalGraph.current`.
 * In demo mode ([demo] != null) the API answers from bundled fixtures and prefs live in a throwaway file.
 */
class AppGraph(context: Context, val demo: DemoConfig? = null) {
    val app: Context = context.applicationContext

    /** Outlives every screen: uploads, sign-out cleanup, gate checks. */
    // A failed fire-and-forget write (disk full, DataStore IOException) is logged, never a process crash.
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, e -> Log.w("NextBand", "background task failed", e) })

    val api: Api = if (demo == null) Api(store = KeystoreTokenStore(app), scope = scope) else {
        DemoCommunity.pinClock()
        Api(
            baseUrl = "https://demo.nextband.local", store = MemoryTokenStore(),
            interceptor = DemoInterceptor(DemoCommunity.overlay(DemoFixtures.load(app), demo.screen, demo.tab)) { name ->
                runCatching { app.assets.open(name).use { it.readBytes() } }.getOrNull()
            },
            // Signed-out demo screens: welcome/auth and "guest-*" (except a guest who already has a session).
            initialToken = if (demo.screen in setOf("welcome", "auth", "onboarding") || (DemoCommunity.guest(demo.screen) && !DemoCommunity.anonymousSession(demo.screen))) null else "demo",
            scope = scope,
        )
    }

    val prefs: Prefs = if (demo == null) Prefs(app) else Prefs(
        PreferenceDataStoreFactory.create(scope = scope) { File(app.cacheDir, "demo-${System.nanoTime()}.preferences_pb") },
    )

    val pending = PendingStore(File(app.filesDir, if (demo == null) "PendingRecordings" else "DemoPendingRecordings"), scope)

    val tts: Tts by lazy { Tts(app) }

    init {
        api.onGuestLinked = { guest, account -> scope.launch { pending.reassign(guest, account) } }
        // ponytail: after a 401 the next account to sign in here claims unowned recordings; persist the last account id if that matters.
        if (demo == null) api.onSignedOut = { expired ->
            // Cached audio includes this account's own recordings: gone on every sign-out (and account delete, which signs out).
            scope.launch(Dispatchers.IO) { MediaCache.clear(app) }
            if (!expired) scope.launch { pending.dropUnowned() }
        }
        // A goal picked in onboarding before there was a session (prefs "goal:*") goes to the account once one exists,
        // then the device copy is dropped. A failed save stays queued for the next sign-in or launch.
        if (demo == null) scope.launch {
            api.signedIn.filter { it }.collect {
                val band = prefs.getString(GOAL_BAND)?.toDoubleOrNull() ?: return@collect
                val date = prefs.getString(GOAL_DATE)
                runCatching { api.saveSettings(targetBand = band, examDate = date) }.onSuccess {
                    prefs.setString(GOAL_BAND, null)
                    prefs.setString(GOAL_DATE, null)
                }
            }
        }
    }

    companion object {
        const val GOAL_BAND = "goal:targetBand"
        const val GOAL_DATE = "goal:examDate"
    }
}

val LocalGraph = staticCompositionLocalOf<AppGraph> { error("LocalGraph not provided") }
val LocalDemo = staticCompositionLocalOf<DemoConfig?> { null }
