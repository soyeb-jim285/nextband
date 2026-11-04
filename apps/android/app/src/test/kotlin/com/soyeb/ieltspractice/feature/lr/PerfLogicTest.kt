package com.soyeb.ieltspractice.feature.lr

import com.soyeb.ieltspractice.data.net.ApiError
import com.soyeb.ieltspractice.design.components.nextPollMs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Resource limits: figure downsampling, autosave retry backoff, status-poll backoff. */
class PerfLogicTest {
    @Test fun figuresAreDownsampledToTheCap() {
        assertEquals(1, figureSampleSize(1200, 800, 2048))
        assertEquals(2, figureSampleSize(4000, 3000, 2048))
        assertEquals(4, figureSampleSize(3000, 8000, 2048))
        assertEquals(1, figureSampleSize(-1, -1, 2048)) // undecodable bounds
    }

    @Test fun saveRetriesBackOffAndStop() {
        val net = ApiError(0, "offline")
        assertEquals(5_000L, saveRetryMs(1, net))
        assertEquals(10_000L, saveRetryMs(2, net))
        assertEquals(60_000L, saveRetryMs(5, net))
        assertEquals(60_000L, saveRetryMs(8, net))
        assertNull(saveRetryMs(9, net))
        assertEquals(5_000L, saveRetryMs(1, ApiError(503, "busy")))
        assertEquals(5_000L, saveRetryMs(1, ApiError(429, "slow down")))
        assertNull(saveRetryMs(1, ApiError(404, "gone")))
        assertNull(saveRetryMs(1, ApiError(409, "submitted")))
    }

    @Test fun pollsBackOffToThirtySeconds() {
        assertEquals(7_500L, nextPollMs(5_000L))
        assertEquals(30_000L, nextPollMs(25_000L))
        assertEquals(30_000L, nextPollMs(30_000L))
    }
}
