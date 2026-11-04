package com.soyeb.ieltspractice.feature.writing

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.soyeb.ieltspractice.data.PendingRecording
import com.soyeb.ieltspractice.data.Prefs
import com.soyeb.ieltspractice.data.belongsTo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Local essays and recordings stay with the account that made them. */
class DraftStoreTest {
    @Test fun draftsAreKeptPerOwner() = runBlocking {
        val scope = CoroutineScope(Dispatchers.IO)
        val file = File(Files.createTempDirectory("drafts").toFile(), "p.preferences_pb")
        try {
            val prefs = Prefs(PreferenceDataStoreFactory.create(scope = scope) { file })
            var who: String? = "a"
            val store = DraftStore(prefs) { who }
            store.save("w2", Draft("A's essay"), DraftMeta("w2", 2, "Remote work"))
            assertEquals("A's essay", store.load("w2").text)

            who = "b"
            assertEquals(Draft(), store.load("w2"))
            assertTrue(store.index().isEmpty())

            who = null // signed out: nothing shown, nothing kept
            assertTrue(store.index().isEmpty())
            store.save("w1", Draft("x"), DraftMeta("w1", 1, "Chart"))

            who = "a"
            assertEquals(listOf("w2"), store.index().map { it.promptId })
            store.clearAll()
            assertTrue(store.index().isEmpty())
            assertEquals(Draft(), store.load("w2"))
        } finally { scope.cancel() }
    }

    @Test fun recordingsBelongToTheirOwnerOrTheGuestItCameFrom() {
        val r = PendingRecording("r1", "p1", 1, "Part 1", 0L, 1000, emptyList(), emptyList(), ownerId = "a")
        assertTrue(r.belongsTo("a"))
        assertFalse(r.belongsTo("b"))
        assertFalse(r.belongsTo(null))
        assertTrue(r.belongsTo("b", setOf("a")))
        assertFalse(r.copy(ownerId = null).belongsTo(null))
    }
}
