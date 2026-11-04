package com.soyeb.ieltspractice.design.components

import org.junit.Assert.assertEquals
import org.junit.Test

class TimeLeftWordsTest {
    @Test fun lastMinuteIsNotZeroMinutes() {
        assertEquals("under a minute", timeLeftWords(59))
        assertEquals("under a minute", timeLeftWords(0))
        assertEquals("1 minute", timeLeftWords(60))
        assertEquals("1 minute", timeLeftWords(119))
        assertEquals("2 minutes", timeLeftWords(120))
        assertEquals("20 minutes", timeLeftWords(1234))
    }
}
