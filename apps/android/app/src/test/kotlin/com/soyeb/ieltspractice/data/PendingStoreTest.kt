package com.soyeb.ieltspractice.data

import com.soyeb.ieltspractice.data.net.Api
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** A kept recording's metadata is written whole (temp file + rename) and a relaunch reads it back. */
class PendingStoreTest {
    @Test fun metadataSurvivesARelaunchWithNoTempFileLeft() {
        val dir = Files.createTempDirectory("pending").toFile()
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val a = PendingStore(dir, scope)
        a.prepare()
        a.audioFile("r1").writeBytes(byteArrayOf(1))
        a.add(PendingRecording("r1", "p1", 1, "Part 1", 1L, 1000, emptyList(), emptyList()))
        assertFalse(File(dir, "r1.json.tmp").exists())
        val b = PendingStore(dir, scope)
        b.reload()
        assertEquals(listOf("r1"), b.items.value.map { it.id })
    }

    @Test fun oldAudioWithNoMetadataIsSweptButAFreshOneIsKept() {
        val dir = Files.createTempDirectory("pending").toFile()
        val old = File(dir, "old.m4a").apply { writeBytes(byteArrayOf(1)); setLastModified(System.currentTimeMillis() - PendingStore.ORPHAN_GRACE_MS - 60_000) }
        val fresh = File(dir, "fresh.m4a").apply { writeBytes(byteArrayOf(1)) }
        PendingStore(dir, CoroutineScope(Dispatchers.Unconfined)).reload()
        assertFalse(old.exists())
        assertTrue(fresh.exists())
    }

    @Test fun aLinkedGuestsRecordingsMoveToTheAccountAndStayThereAfterARelaunch() {
        val dir = Files.createTempDirectory("pending").toFile()
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val a = PendingStore(dir, scope)
        a.prepare()
        listOf("g1", "x1").forEach { a.audioFile(it).writeBytes(byteArrayOf(1)) }
        a.add(PendingRecording("g1", "p1", 1, "Part 1", 1L, 1000, emptyList(), emptyList(), ownerId = "guest"))
        a.add(PendingRecording("x1", "p1", 1, "Part 1", 2L, 1000, emptyList(), emptyList(), ownerId = "other"))
        PendingStore(dir, scope).reassign("guest", "acctA") // a fresh launch: reassign reads the disk itself
        val b = PendingStore(dir, scope)
        b.reload()
        val g = b.items.value.first { it.id == "g1" }
        assertTrue(g.belongsTo("acctA"))
        assertFalse(g.belongsTo("acctB")) // a later account on this device never sees it
        assertEquals("other", b.items.value.first { it.id == "x1" }.ownerId)
    }

    @Test fun anUnknownUserIsOfflineNotAnotherAccount() {
        val mine = PendingRecording("r1", "p1", 1, "Part 1", 1L, 1000, emptyList(), emptyList(), ownerId = "acctA")
        val unowned = mine.copy(ownerId = null)
        assertTrue(blockedReason(mine, null)!!.startsWith("Can't reach"))
        assertTrue(blockedReason(unowned, null)!!.startsWith("Can't reach"))
        assertTrue(blockedReason(mine, "acctB")!!.contains("another account"))
        assertEquals(null, blockedReason(mine, "acctA"))
        assertEquals(null, blockedReason(unowned, "acctA"))
    }

    @Test fun aSignOutWhileAnExpiredUploadIsPendingNeverCreatesAnAttemptInTheNextSession() = runBlocking {
        val dir = Files.createTempDirectory("pending").toFile()
        val store = PendingStore(dir, CoroutineScope(Dispatchers.Unconfined))
        store.prepare()
        store.audioFile("r1").writeBytes(byteArrayOf(1))
        store.add(PendingRecording("r1", "p1", 1, "Part 1", 1L, 1000, emptyList(), emptyList(),
            attemptId = "att1", uploadUrl = "https://storage.example/put1", ownerId = "acctA"))
        var creates = 0
        lateinit var api: Api
        api = Api("https://demo.ielts.local", MemoryTokenStore(), Interceptor { chain ->
            val r = chain.request()
            fun answer(code: Int, body: String) = Response.Builder().request(r).protocol(Protocol.HTTP_1_1).code(code).message("x").body(body.toResponseBody()).build()
            when {
                r.url.encodedPath == "/api/me" -> answer(200, """{"user":{"id":"acctA"}}""")
                r.url.host == "storage.example" -> { api.signOutLocal(); answer(403, "") } // A signs out while the expired PUT is in flight
                r.method == "POST" && r.url.encodedPath == "/api/attempts" -> { creates++; answer(200, """{"id":"att2","uploadUrl":"https://storage.example/put2"}""") }
                else -> answer(200, "{}")
            }
        }, initialToken = "tokenA")
        assertEquals(null, store.upload(store.items.value.first(), api))
        assertEquals(0, creates)
        assertEquals("acctA", store.items.value.single().ownerId) // kept for A to retry after signing back in
        assertEquals(UploadState.Failed(Api.SESSION_CHANGED), store.states.value["r1"])
    }
}
