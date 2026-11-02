package com.soyeb.ieltspractice

import android.content.Context
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
import com.soyeb.ieltspractice.media.Tts
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File

/**
 * The app's long-lived objects (manual DI). Screens reach it with `LocalGraph.current`.
 * In demo mode ([demo] != null) the API answers from bundled fixtures and prefs live in a throwaway file.
 */
class AppGraph(context: Context, val demo: DemoConfig? = null) {
    val app: Context = context.applicationContext

    /** Outlives every screen: uploads, sign-out cleanup, gate checks. */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

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
}

val LocalGraph = staticCompositionLocalOf<AppGraph> { error("LocalGraph not provided") }
val LocalDemo = staticCompositionLocalOf<DemoConfig?> { null }
