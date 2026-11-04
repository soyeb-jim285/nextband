package com.soyeb.ieltspractice.media

import kotlin.test.Test
import kotlin.test.assertEquals

class MediaCacheTest {
    @Test fun presignedUrlsShareOneKeyAcrossSignatures() {
        val a = "https://r2.example/audio/p1.mp3?X-Amz-Algorithm=AWS4&X-Amz-Signature=aaa"
        val b = "https://r2.example/audio/p1.mp3?X-Amz-Algorithm=AWS4&X-Amz-Signature=bbb"
        assertEquals(cacheKey(a), cacheKey(b))
        assertEquals("https://r2.example/audio/p1.mp3", cacheKey(a))
    }

    @Test fun otherUrlsKeepTheirQuery() {
        assertEquals("https://x.example/tts?id=7", cacheKey("https://x.example/tts?id=7"))
        assertEquals("file:///data/rec.m4a", cacheKey("file:///data/rec.m4a"))
    }
}
