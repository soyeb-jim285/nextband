package com.soyeb.ieltspractice.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
}
