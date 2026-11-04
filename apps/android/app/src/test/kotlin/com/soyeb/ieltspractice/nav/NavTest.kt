package com.soyeb.ieltspractice.nav

import androidx.compose.runtime.remember
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.soyeb.ieltspractice.data.MemoryTokenStore
import com.soyeb.ieltspractice.data.Prefs
import com.soyeb.ieltspractice.data.net.Api
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.nio.file.Files
import kotlin.reflect.KClass
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The back stack after the moves screens make: first run, tab switches, "Back to Today", returning to a mock hub. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NavTest {
    @get:Rule val rule = createComposeRule()

    private lateinit var controller: NavHostController
    private lateinit var nav: Nav

    /** A graph with empty screens, starting at Welcome like a first launch. */
    private fun launch() {
        val scope = CoroutineScope(Dispatchers.IO)
        val prefs = Prefs(PreferenceDataStoreFactory.create(scope = scope) { File(Files.createTempDirectory("nav").toFile(), "p.preferences_pb") })
        val api = Api("https://t.test", MemoryTokenStore())
        rule.setContent {
            controller = rememberNavController()
            nav = remember { Nav(controller, api, prefs, scope) }
            NavHost(controller, startDestination = Welcome) {
                composable<Welcome> {}
                composable<Onboarding> {}
                composable<TodayTab> {}
                composable<ListeningTab> {}
                composable<ReadingTab> {}
                composable<WritingTab> {}
                composable<SpeakingTab> {}
                composable<Plan> {}
                composable<MockHub> {}
                composable<LrRun> {}
            }
        }
        rule.waitForIdle()
    }

    private fun at(route: KClass<out Any>) = controller.currentDestination?.hasRoute(route) == true

    @Test fun tabsDoNotPileUpAfterTheFirstRunReset() {
        launch()
        rule.runOnIdle { nav.reset(TodayTab) } // Welcome (the graph start) is gone from here on
        rule.runOnIdle { nav.openTab(Tab.Listening) }
        rule.runOnIdle { nav.openTab(Tab.Reading) }
        rule.runOnIdle {
            assertTrue(at(ReadingTab::class))
            assertTrue(controller.previousBackStackEntry?.destination?.hasRoute(TodayTab::class) == true) // [Today, Reading]
            nav.openTab(Tab.Today)
        }
        rule.runOnIdle { assertTrue(at(TodayTab::class)); assertFalse(nav.canGoBack) }
    }

    @Test fun homeDropsTheStackInsteadOfRestoringIt() {
        launch()
        rule.runOnIdle { nav.reset(TodayTab) }
        rule.runOnIdle { nav.go(Plan) }
        rule.runOnIdle { nav.home() }
        rule.runOnIdle { assertTrue(at(TodayTab::class)); assertFalse(nav.canGoBack); nav.openTab(Tab.Writing) }
        rule.runOnIdle { nav.openTab(Tab.Today) }
        rule.runOnIdle { assertTrue(at(TodayTab::class)) } // not Plan again
    }

    @Test fun finishingAMockSectionReturnsToTheSameHub() {
        launch()
        rule.runOnIdle { nav.reset(TodayTab) }
        rule.runOnIdle { nav.go(MockHub("m1")) }
        rule.runOnIdle { nav.go(LrRun("a1", "m1")) }
        rule.runOnIdle { nav.toMock("m1") }
        rule.runOnIdle {
            assertTrue(at(MockHub::class))
            assertTrue(controller.previousBackStackEntry?.destination?.hasRoute(TodayTab::class) == true) // one hub, not two
        }
    }

    @Test fun backOnALoneRootGoesToTodayNotAnEmptyStack() {
        launch()
        rule.runOnIdle { nav.reset(Onboarding) }
        rule.runOnIdle { assertFalse(nav.canGoBack); nav.back() }
        rule.runOnIdle { assertTrue(at(TodayTab::class)); assertEquals(null, controller.previousBackStackEntry) }
    }
}
