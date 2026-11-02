package com.soyeb.ieltspractice

import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.soyeb.ieltspractice.data.demo.DemoConfig
import com.soyeb.ieltspractice.nav.screenCatalog
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** One screenshot of a screen (optionally one variant) in one theme: `light-today.png`, `dark-result-speaking-Transcript.png`. */
data class ScreenCase(val screen: String, val variant: String?, val dark: Boolean) {
    val fileName get() = "${if (dark) "dark" else "light"}-$screen${variant?.let { "-$it" }.orEmpty()}"
    override fun toString() = fileName
}

/**
 * Renders every row of nav/ScreenCatalog.kt, light and dark, at phone size from the demo fixtures, into
 * apps/android/screenshots/<name>.png. JVM only (Robolectric native graphics). CI runs `recordRoborazziDebug`.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w412dp-h915dp-xxhdpi")
class ScreenshotTest(private val case: ScreenCase) {
    @get:Rule val rule = createComposeRule()

    @Test fun capture() {
        val demo = DemoConfig(screen = case.screen, tab = case.variant, theme = if (case.dark) "dark" else "light")
        val graph = AppGraph(RuntimeEnvironment.getApplication(), demo)
        rule.setContent { NbApp(graph, demo) }
        // Demo requests answer from fixtures on an IO thread: wait until none are in flight, let the UI settle, repeat
        // (a loaded screen can start follow-up requests).
        repeat(4) {
            rule.waitForIdle()
            Thread.sleep(80)
            try { rule.waitUntil(30_000) { graph.api.inflight.get() == 0 } } catch (e: ComposeTimeoutException) { println("capture ${case.fileName}: inflight=${graph.api.inflight.get()} after 30s") }
        }
        rule.waitForIdle()
        rule.onRoot().captureRoboImage("../screenshots/${case.fileName}.png")
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun cases(): List<Array<Any>> = screenCatalog.flatMap { s ->
            (s.variants.ifEmpty { listOf(null) }).flatMap { v -> listOf(false, true).map { d -> arrayOf<Any>(ScreenCase(s.name, v, d)) } }
        }
    }
}
