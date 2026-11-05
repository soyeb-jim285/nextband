package com.soyeb.ieltspractice.feature.speaking

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VoiceBarsTest {
    private fun bars(mode: OrbMode, t: Float = 0.3f, voice: Float = 0f, level: Float = 0f, reduced: Boolean = false) =
        FloatArray(5).also { barTargets(mode, t, voice, level, reduced, it) }

    @Test fun aLouderVoiceMeansTallerBarsWithTheMiddleTallest() {
        val quiet = bars(OrbMode.Listening, level = 0.1f)
        val loud = bars(OrbMode.Listening, level = 0.5f)
        assertTrue(loud.sum() > quiet.sum())
        assertTrue(loud[2] > loud[0] && loud[2] > loud[4])
        assertTrue(bars(OrbMode.Speaking, voice = 0.5f).sum() > bars(OrbMode.Speaking, voice = 0.05f).sum())
    }

    @Test fun everyHeightStaysInRange() {
        for (m in OrbMode.entries) for (t in 0..40) for (v in listOf(0f, 0.3f, 1f)) {
            bars(m, t / 7f, v, v).forEach { assertTrue(it in 0f..1f, "$m $t $v $it") }
        }
    }

    @Test fun connectingSweepsThereAndBack() {
        val lit = (0 until 8).map { barTargets(OrbMode.Connecting, it / 6f + 0.01f, 0f, 0f, false, FloatArray(5)) }
        assertEquals(listOf(0, 1, 2, 3, 4, 3, 2, 1), lit)
    }

    @Test fun reducedMotionIsStill() {
        assertTrue(bars(OrbMode.Speaking, t = 0.1f, reduced = true).contentEquals(bars(OrbMode.Speaking, t = 9f, reduced = true)))
    }
}
