package com.soyeb.ieltspractice

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.platform.app.InstrumentationRegistry
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.soyeb.ieltspractice.data.demo.DemoConfig
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A few key screens off the default phone portrait: landscape phone, tablet, 200 % font and RTL, so clipping and
 * edge-to-edge layout regressions show up in CI. Files: apps/android/screenshots/device-<case>.png.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w412dp-h915dp-xxhdpi")
class DeviceScreenshotTest {
    @get:Rule val rule = createComposeRule()

    @Test @Config(qualifiers = "w800dp-h360dp-land-xxhdpi") fun welcomeLandscape() = shot("welcome", "welcome-land")
    @Test @Config(qualifiers = "w800dp-h360dp-land-xxhdpi") fun signinLandscape() = shot("auth", "auth-land", variant = "SignIn")
    @Test fun welcomeFont200() = shot("welcome", "welcome-font200", fontScale = 2f)
    @Test fun todayFont200() = shot("today", "today-font200", fontScale = 2f)
    @Test fun streakFont200() = shot("streak", "streak-font200", fontScale = 2f)
    @Test fun writingFont200() = shot("editor", "editor-font200", variant = "Typing", fontScale = 2f)
    @Test @Config(qualifiers = "w1280dp-h800dp-mdpi") fun todayTablet() = shot("today", "today-tablet")
    @Test @Config(qualifiers = "w1280dp-h800dp-mdpi") fun readingTablet() = shot("lr-reading", "lr-reading-tablet", variant = "Passage")
    @Test fun readingRtl() = shot("lr-reading", "lr-reading-rtl", variant = "Questions", rtl = true)

    @OptIn(ExperimentalRoborazziApi::class)
    private fun shot(screen: String, name: String, variant: String? = null, fontScale: Float = 1f, rtl: Boolean = false) {
        InstrumentationRegistry.getInstrumentation().setInTouchMode(true)
        val demo = DemoConfig(screen = screen, tab = variant, theme = "light")
        val graph = AppGraph(RuntimeEnvironment.getApplication(), demo)
        rule.setContent {
            val d = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(d.density, fontScale),
                LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
            ) { NbApp(graph, demo) }
        }
        repeat(4) {
            rule.waitForIdle()
            Thread.sleep(80)
            try { rule.waitUntil(30_000) { graph.api.inflight.get() == 0 } } catch (e: ComposeTimeoutException) { println("device-$name: inflight after 30s") }
        }
        rule.waitForIdle()
        captureScreenRoboImage("../screenshots/device-$name.png")
    }
}
