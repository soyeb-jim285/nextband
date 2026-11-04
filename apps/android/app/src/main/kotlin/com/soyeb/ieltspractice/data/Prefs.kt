package com.soyeb.ieltspractice.data

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

// The old app kept these in SharedPreferences "ielts" (appearance, questionSource:speaking, fairUseAck, mockVariant); the migration copies them over once.
// A corrupted file (power loss mid-write) resets to defaults instead of throwing on every read at launch.
private val Context.prefsStore by preferencesDataStore("prefs", corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() }, produceMigrations = { ctx -> listOf(SharedPreferencesMigration(ctx, "ielts")) })

/**
 * Per-device settings (never sent to the server). Every value is a Flow with a suspend setter; read once with `.first()`.
 * Per-attempt things a feature owns (LR marks and flags, writing drafts) use [string] / [setString] with their own key prefix.
 */
class Prefs(private val store: DataStore<Preferences>) {
    constructor(context: Context) : this(context.applicationContext.prefsStore)

    // A read I/O failure gives the defaults instead of crashing the collecting screen (DataStore's documented pattern).
    private fun <T> read(f: (Preferences) -> T): Flow<T> =
        store.data.catch { if (it is IOException) emit(emptyPreferences()) else throw it }.map(f)

    // Settings writes are fire-and-forget from screens: a failed write (disk full) is logged and the old value stays.
    private suspend fun edit(f: suspend (MutablePreferences) -> Unit) {
        try { store.edit(f) } catch (e: IOException) { Log.w("Prefs", "Couldn't save a setting", e) }
    }

    /** "system" (default) | "light" | "dark". */
    val appearance: Flow<String> = read { it[APPEARANCE]?.takeIf { v -> v in APPEARANCES } ?: "system" }
    suspend fun setAppearance(v: String) { edit { it[APPEARANCE] = if (v in APPEARANCES) v else "system" } }

    /** "academic" (default) | "general". */
    val testType: Flow<String> = read { if (it[TEST_TYPE] == "general") "general" else "academic" }
    suspend fun setTestType(v: String) { edit { it[TEST_TYPE] = if (v == "general") "general" else "academic" } }

    /** Reading passage text scale, 1.0 = default. */
    val readingTextScale: Flow<Float> = read { it[READING_SCALE] ?: 1f }
    suspend fun setReadingTextScale(v: Float) { edit { it[READING_SCALE] = v } }

    val onboardingDone: Flow<Boolean> = read { it[ONBOARDING] == true }
    suspend fun setOnboardingDone(v: Boolean = true) { edit { it[ONBOARDING] = v } }

    /** The fair-use note was read for this key (`FairUse.ackKey(userKey, now)`: user and UTC day), or null. */
    val lastSeenFairUseDay: Flow<String?> = read { it[FAIR_USE] }
    suspend fun setLastSeenFairUseDay(v: String?) { setString(FAIR_USE.name, v) }

    /** Question bank for random speaking tests: "any" (default) | "cambridge" | "generated". Pass it through `Api.sourceParam`. */
    val speakingSource: Flow<String> = read { it[SPEAKING_SOURCE] ?: "any" }
    suspend fun setSpeakingSource(v: String) { edit { it[SPEAKING_SOURCE] = v } }

    /** Full-mock variant last chosen: "academic" (default) | "general". */
    val mockVariant: Flow<String> = read { if (it[MOCK_VARIANT] == "general") "general" else "academic" }
    suspend fun setMockVariant(v: String) { edit { it[MOCK_VARIANT] = if (v == "general") "general" else "academic" } }

    // Free-form string keys for feature-local state (`marks:<attemptId>`, `draft:<promptId>`, ...). null removes the key.
    fun string(key: String): Flow<String?> = read { it[stringPreferencesKey(key)] }
    suspend fun getString(key: String): String? = string(key).first()
    suspend fun setString(key: String, value: String?) {
        val k = stringPreferencesKey(key)
        edit { p -> if (value == null) { p.remove(k) } else { p[k] = value } }
    }

    /** Drops every Listening/Reading attempt's local state ([isAttemptKey]): notes, flags, position, answers, audio. Used when the account is deleted. */
    // ponytail: attempt keys carry no owner, so another account's unfinished notes on this phone go too; track owners if shared phones matter.
    suspend fun clearAttemptState() {
        edit { p -> p.asMap().keys.map { it.name }.filter(::isAttemptKey).forEach { p.remove(stringPreferencesKey(it)) } }
    }

    private companion object {
        val APPEARANCES = setOf("system", "light", "dark")
        val APPEARANCE = stringPreferencesKey("appearance")
        val TEST_TYPE = stringPreferencesKey("testType")
        val READING_SCALE = floatPreferencesKey("readingTextScale")
        val ONBOARDING = booleanPreferencesKey("onboardingDone")
        val FAIR_USE = stringPreferencesKey("fairUseAck")
        val SPEAKING_SOURCE = stringPreferencesKey("questionSource:speaking")
        val MOCK_VARIANT = stringPreferencesKey("mockVariant")
    }
}

private val ATTEMPT_PREFIXES = listOf("marks:", "flags:", "pos:", "answers:", "audio:")

/** A per-attempt Listening/Reading key (`marks:<attemptId>` and its siblings), as opposed to a setting. */
fun isAttemptKey(name: String) = ATTEMPT_PREFIXES.any { name.startsWith(it) }
