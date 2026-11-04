package com.soyeb.ieltspractice.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Prefs on a real DataStore in a temp directory (no Android needed). */
class PrefsTest {
    private fun withPrefs(block: suspend (Prefs) -> Unit) = runBlocking {
        val scope = CoroutineScope(Dispatchers.IO)
        val file = File(Files.createTempDirectory("prefs").toFile(), "p.preferences_pb")
        try { block(Prefs(PreferenceDataStoreFactory.create(scope = scope) { file })) } finally { scope.cancel() }
    }

    @Test fun defaults() = withPrefs { p ->
        assertEquals("system", p.appearance.first())
        assertEquals("academic", p.testType.first())
        assertEquals(1f, p.readingTextScale.first())
        assertEquals(false, p.onboardingDone.first())
        assertNull(p.lastSeenFairUseDay.first())
        assertEquals("any", p.speakingSource.first())
    }

    @Test fun settersRoundTripAndRejectBadValues() = withPrefs { p ->
        p.setAppearance("dark"); assertEquals("dark", p.appearance.first())
        p.setAppearance("neon"); assertEquals("system", p.appearance.first())
        p.setTestType("general"); assertEquals("general", p.testType.first())
        p.setReadingTextScale(1.25f); assertEquals(1.25f, p.readingTextScale.first())
        p.setOnboardingDone(); assertEquals(true, p.onboardingDone.first())
        p.setLastSeenFairUseDay("u1@2026-10-03"); assertEquals("u1@2026-10-03", p.lastSeenFairUseDay.first())
        p.setLastSeenFairUseDay(null); assertNull(p.lastSeenFairUseDay.first())
        p.setString("marks:a1", "[]"); assertEquals("[]", p.getString("marks:a1"))
        p.setString("marks:a1", null); assertNull(p.getString("marks:a1"))
    }

    @Test fun clearAttemptStateKeepsSettingsAndDrafts() = withPrefs { p ->
        listOf("marks:a1", "flags:a1", "pos:a1", "answers:a1", "audio:a1", "draft-index:u1", "settings").forEach { p.setString(it, "x") }
        p.setAppearance("dark")
        p.clearAttemptState()
        listOf("marks:a1", "flags:a1", "pos:a1", "answers:a1", "audio:a1").forEach { assertNull(p.getString(it), it) }
        assertEquals("x", p.getString("draft-index:u1"))
        assertEquals("x", p.getString("settings"))
        assertEquals("dark", p.appearance.first())
    }

    @Test fun readFailureGivesDefaults() = runBlocking {
        val broken = object : DataStore<Preferences> {
            override val data: Flow<Preferences> = flow { throw IOException("disk") }
            override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences = throw IOException("disk")
        }
        val p = Prefs(broken)
        assertEquals("system", p.appearance.first())
        assertEquals(false, p.onboardingDone.first())
        assertNull(p.getString("draft:x"))
    }
}
