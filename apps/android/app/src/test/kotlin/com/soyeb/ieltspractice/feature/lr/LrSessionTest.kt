package com.soyeb.ieltspractice.feature.lr

import com.soyeb.ieltspractice.data.MemoryTokenStore
import com.soyeb.ieltspractice.data.model.LrAttempt
import com.soyeb.ieltspractice.data.model.LrTest
import com.soyeb.ieltspractice.data.net.Api
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import kotlin.test.Test
import kotlin.test.assertEquals

/** A submit whose response was lost: the retry's 409 ("already submitted") counts as success, and autosave stops on it. */
class LrSessionTest {
    private var calls = 0
    private val api = Api("https://t.test", MemoryTokenStore(), Interceptor { chain ->
        calls++
        Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(409).message("x")
            .body("""{"error":"Attempt already submitted"}""".toResponseBody("application/json".toMediaType())).build()
    }, initialToken = "t")

    @Test fun alreadySubmittedIsSuccess() = runBlocking {
        val s = LrSession(LrAttempt("a1", test = LrTest(skill = "reading", title = "T")), api, CoroutineScope(Dispatchers.Unconfined))
        s.submit() // no throw
        s.submit() // and again: nothing sent
        assertEquals(1, calls)
    }

    @Test fun autosaveStopsOnAlreadySubmitted() = runBlocking {
        val s = LrSession(LrAttempt("a1", test = LrTest(skill = "reading", title = "T")), api, CoroutineScope(Dispatchers.Unconfined))
        s.flush(force = true)
        assertEquals(SaveState.Saved, s.state)
        s.flush(force = true)
        s.submit()
        assertEquals(1, calls)
    }
}
