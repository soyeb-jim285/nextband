package com.soyeb.ieltspractice.media

import kotlin.test.Test
import com.soyeb.ieltspractice.feature.you.isWebUrl
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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

    // The player's upstream guard (MediaCache.sourceFactory): server audio URLs never reach local files.
    @Test fun playerFetchesOnlyHttps() {
        assertTrue(isWebUrl("https://r2.example/audio/p1.mp3?X-Amz-Signature=a"))
        listOf("file:///data/data/com.soyeb.ieltspractice/shared_prefs/x.xml", "content://media/1", "asset:///demo.mp3", "http://r2.example/a.mp3")
            .forEach { assertFalse(isWebUrl(it), it) }
    }
}
