package com.soyeb.ieltspractice.feature.push

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PushLogicTest {
    @Test fun routesFromTheServerContract() {
        assertEquals(PushTarget.Attempts(listOf("a1")), parsePushRoute("/writing/result/a1"))
        assertEquals(PushTarget.Attempts(listOf("t1", "t2")), parsePushRoute("/writing/result/t1?pair=t2"))
        assertEquals(PushTarget.Attempts(listOf("s1")), parsePushRoute("/speaking/result/s1"))
        assertEquals(PushTarget.Session("s1", "9b1d-sess"), parsePushRoute("/speaking/result/s1?session=9b1d-sess"))
        assertEquals(PushTarget.Mock("m1"), parsePushRoute("/mock/m1"))
        assertEquals(PushTarget.Settings, parsePushRoute("/settings"))
    }

    @Test fun unknownOrMalformedRoutesJustOpenTheApp() {
        assertNull(parsePushRoute(null))
        assertNull(parsePushRoute(""))
        assertNull(parsePushRoute("/pricing"))
        assertNull(parsePushRoute("/writing/result/"))
        assertNull(parsePushRoute("/writing/result/a b"))
        assertNull(parsePushRoute("https://evil.example/x"))
        // A bad pair id is dropped, the first attempt still opens.
        assertEquals(PushTarget.Attempts(listOf("t1")), parsePushRoute("/writing/result/t1?pair=../x"))
    }

    @Test fun channelsMatchThePlan() {
        assertEquals(listOf("results", "streak", "practice", "exam", "weekly"), PUSH_CHANNELS.map { it.id })
        assertEquals(listOf(3, 4, 3, 3, 2), PUSH_CHANNELS.map { it.importance }) // default, high, default, default, low
        assertEquals("streak", channelFor("streak"))
        assertEquals("results", channelFor(null))
        assertEquals("results", channelFor("promo"))
    }

    @Test fun openingAResultPushMarksItsUpdateRead() {
        assertEquals(listOf("d:t1", "f:t1", "d:t2", "f:t2"), seenKeysFor(PushTarget.Attempts(listOf("t1", "t2"))))
        assertEquals(emptyList(), seenKeysFor(PushTarget.Settings))
    }

    @Test fun explainerNeverNags() {
        val day = 24L * 60 * 60 * 1000
        assertTrue(shouldAskForPush(33, enabled = false, refusals = 0, lastRefusedAt = null, now = 0))
        assertFalse(shouldAskForPush(32, false, 0, null, 0), "no runtime permission before Android 13")
        assertFalse(shouldAskForPush(34, true, 0, null, 0), "already allowed")
        assertFalse(shouldAskForPush(34, false, 1, lastRefusedAt = 0, now = 6 * day), "7 days after Not now")
        assertTrue(shouldAskForPush(34, false, 1, lastRefusedAt = 0, now = 7 * day))
        assertFalse(shouldAskForPush(34, false, 2, lastRefusedAt = 0, now = 100 * day), "two refusals: Settings row only")
    }

    @Test fun aPushShowsOnlyForTheAccountSignedInHere() {
        assertTrue(pushIsForThisPhone("u1", "u1", signedIn = true))
        assertFalse(pushIsForThisPhone("u1", "u2", signedIn = true), "another account signed in since")
        assertFalse(pushIsForThisPhone("u1", null, signedIn = true), "signed-in user not known yet")
        assertFalse(pushIsForThisPhone("u1", "u1", signedIn = false), "signed out")
        assertTrue(pushIsForThisPhone(null, null, signedIn = true), "an older server sends no uid")
        assertFalse(pushIsForThisPhone("", "u1", signedIn = false))
    }

    @Test fun clockTimes() {
        assertEquals(22 to 30, parseClock("22:30"))
        assertNull(parseClock("24:00"))
        assertNull(parseClock("7:30"))
        assertEquals("08:05", clockString(8, 5))
        assertEquals("22:30", displayClock("22:30", is24 = true))
        assertEquals("10:30 pm", displayClock("22:30", is24 = false))
        assertEquals("12:00 am", displayClock("00:00", is24 = false))
        assertEquals("12:15 pm", displayClock("12:15", is24 = false))
    }
}
