package com.soyeb.ieltspractice.feature.lr

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.soyeb.ieltspractice.data.Prefs
import com.soyeb.ieltspractice.data.logic.withAnswer
import com.soyeb.ieltspractice.data.model.LrAttempt
import com.soyeb.ieltspractice.data.model.LrAudioState
import com.soyeb.ieltspractice.data.model.LrStats
import com.soyeb.ieltspractice.data.net.Api
import com.soyeb.ieltspractice.data.net.ApiError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class SaveState(val label: String) { Saved("Saved"), Dirty("Saving soon"), Saving("Saving"), Error("Offline, retrying") }

/** What the device remembers about one attempt (the old app's keys, so an update keeps it). */
class RunLocal(
    val marks: List<TextMark> = emptyList(),
    val flags: Set<Int> = emptySet(),
    /** Part index and current question when the screen was left. */
    val position: Pair<Int, Int>? = null,
    val audio: String? = null,
    val settings: LrSettings = LrSettings(),
    /** Answers changed here that the server may not have yet (see [withPending]). */
    val pending: PendingAnswers? = null,
) {
    companion object {
        suspend fun load(prefs: Prefs, attemptId: String): RunLocal = RunLocal(
            marks = parseMarks(prefs.getString("marks:$attemptId")),
            flags = parseFlags(prefs.getString("flags:$attemptId")),
            position = prefs.getString("pos:$attemptId")?.split(",")?.mapNotNull { it.toIntOrNull() }?.takeIf { it.size >= 2 }?.let { it[0] to it[1] },
            audio = prefs.getString("audio:$attemptId"),
            settings = parseSettings(prefs.getString("settings")),
            pending = parsePending(prefs.getString("answers:$attemptId")),
        )
    }
}

/**
 * The answers of an in-progress attempt: local state, debounced autosave (about 1 s), a flush when the screen stops, a keep-alive save of
 * the clock every 15 s (the screen calls [flush] with force), and submit. Saves run on the app [scope] so a flush survives the screen leaving.
 * [elapsed] is the clock in seconds, written by the screen; it is not state, so the clock does not recompose this.
 * With [live] false (demo screenshots) nothing is sent.
 */
class LrSession(
    val attempt: LrAttempt,
    private val api: Api,
    private val scope: CoroutineScope,
    private val live: Boolean = true,
    localAudio: String? = null,
    private val saveLocalAudio: (String) -> Unit = {},
    /** Keeps the unsynced answers on the device (JSON), or clears them (null) once the server has them. */
    private val saveLocalAnswers: (String?) -> Unit = {},
) {
    var responses by mutableStateOf(attempt.responses)
        private set
    var state by mutableStateOf(SaveState.Saved)
        private set
    var submitting by mutableStateOf(false)
        private set
    @Volatile var elapsed: Double = attempt.elapsedS.toDouble()

    // Pacing: seconds per part, answer changes per question, questions answered late.
    /** Elapsed seconds after which an answer counts as late: reading 3300, exam listening the end of the recordings, otherwise never. */
    @Volatile var lateFrom: () -> Double = { Double.POSITIVE_INFINITY }
    // Written on the main thread, copied by stats() inside saves on the background scope: concurrent maps so a copy never throws.
    private val partS = java.util.concurrent.ConcurrentHashMap<String, Double>(attempt.stats?.partS.orEmpty())
    private val changes = java.util.concurrent.ConcurrentHashMap<String, Int>(attempt.stats?.changes.orEmpty())
    private val late = java.util.concurrent.ConcurrentSkipListSet<Int>(attempt.stats?.late.orEmpty())

    // Practice listening position: noteAudio is hot (every player tick, memory only); saveAudio stores it on the device and queues a server save.
    @Volatile private var audio: LrAudioState? = LrAudioState.pick(localAudio, attempt.stats?.audio)
    fun audioStart(part: Int): Double = audio?.pos?.get(part.toString()) ?: 0.0
    val audioRate: Float get() = (audio?.rate ?: 1.0).toFloat()
    fun noteAudio(part: Int, pos: Double) { val a = audio ?: LrAudioState(); audio = a.copy(pos = a.pos + (part.toString() to pos)) }
    fun noteRate(rate: Float) { audio = (audio ?: LrAudioState()).copy(rate = rate.toDouble()); saveAudio() }
    fun saveAudio() {
        val a = audio ?: return
        if (done || !live) return
        saveLocalAudio(a.encode())
        dirty = true
        flushLater()
    }

    private val focusVal = HashMap<Int, String>()
    private var textField: Int? = null

    fun tickPart(part: Int) { partS[part.toString()] = (partS[part.toString()] ?: 0.0) + 1 }

    /** A text gap got the cursor: remember its value so a typed change counts once per visit (on [noteBlur]). */
    fun noteFocus(n: Int) { focusVal[n] = responses[n.toString()].orEmpty(); textField = n }
    fun noteBlur(n: Int) {
        val before = focusVal.remove(n)
        if (!before.isNullOrEmpty() && responses[n.toString()].orEmpty() != before) changes[n.toString()] = (changes[n.toString()] ?: 0) + 1
        if (textField == n) textField = null
    }

    private var dirty = false
    private var done = false
    /** The server already has this attempt as submitted (seen as a 409). */
    private var submitted = false
    private var debounce: Job? = null
    private var failures = 0
    private val lock = Mutex()

    fun answer(n: Int, v: String) = change(responses.withAnswer(n, v))

    fun change(next: Map<String, String>) {
        val prev = responses
        for (k in prev.keys + next.keys) {
            if (prev[k].orEmpty() == next[k].orEmpty()) continue
            // typing in a text gap counts once per visit (noteBlur); choosing or switching an option counts each time
            if (!prev[k].isNullOrEmpty() && textField?.toString() != k) changes[k] = (changes[k] ?: 0) + 1
            val n = k.toIntOrNull()
            if (!next[k].isNullOrEmpty() && n != null && elapsed >= lateFrom()) late.add(n)
        }
        responses = next
        if (!live) return
        saveLocalAnswers(pendingJson(PendingAnswers(next, elapsed.toInt())))
        dirty = true
        state = SaveState.Dirty
        debounce?.cancel()
        debounce = scope.launch { delay(1000); flush() }
    }

    private fun stats() = LrStats(partS.toMap(), changes.filterValues { it > 0 }, late.toList(), audio?.cleaned())

    /** Persist now when something changed (or [force], for the clock). Transient failures retry on a backoff ([saveRetryMs]). */
    suspend fun flush(force: Boolean = false) {
        if (!live || done || (!dirty && !force)) return
        lock.withLock {
            if (done) return
            dirty = false
            state = SaveState.Saving
            try {
                api.lrSave(attempt.id, responses, elapsed.toInt(), stats())
                state = if (dirty) SaveState.Dirty else SaveState.Saved
                // ponytail: the prefs writes are async launches, so a clear can in theory land after a newer snapshot; a later save clears again.
                if (!dirty) saveLocalAnswers(null)
                failures = 0
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // 409: already submitted (a submit whose response was lost); there is nothing left to save.
                if ((e as? ApiError)?.status == 409) { done = true; submitted = true; state = SaveState.Saved; return }
                dirty = true
                state = SaveState.Error
                // Keep the device copy's clock current too (the 15 s clock save lands here offline), so a kill never gives exam time back.
                saveLocalAnswers(pendingJson(PendingAnswers(responses, elapsed.toInt())))
                debounce?.cancel()
                // The next change, the 15 s clock save or leaving the screen tries again even after the retries stop.
                saveRetryMs(++failures, e)?.let { ms -> debounce = scope.launch { delay(ms); flush() } }
            }
        }
    }

    /** Fire-and-forget [flush] for lifecycle callbacks (stop, leaving the screen). */
    fun flushLater(force: Boolean = false) { scope.launch { flush(force) } }

    /**
     * Scores the attempt. Throws the ApiError on failure (the answers stay saved and the caller may try again). A 409 (already
     * submitted: an earlier submit went in and only its response was lost) counts as success, so a retry lands on the result.
     */
    suspend fun submit() {
        if (submitted) return
        check(!done) { "already submitting" }
        done = true
        debounce?.cancel()
        submitting = true
        try {
            lock.withLock { api.lrSubmit(attempt.id, responses, elapsed.toInt(), stats()) }
            saveLocalAnswers(null)
        } catch (e: ApiError) {
            if (e.status == 409) { submitted = true; saveLocalAnswers(null); return }
            done = false
            throw e
        } catch (e: Throwable) {
            done = false
            throw e
        } finally {
            submitting = false
        }
    }
}

/**
 * Autosave retry delay after [failures] consecutive failures (5 s doubling to 60 s), or null to stop: after 8 tries, or on a 4xx such as a
 * deleted (404) or already-submitted (409) attempt, which retrying never fixes.
 */
fun saveRetryMs(failures: Int, e: Exception): Long? {
    val status = (e as? ApiError)?.status ?: 0
    if ((status in 400..499 && status != 408 && status != 429) || failures > 8) return null
    return minOf(5_000L shl (failures - 1).coerceIn(0, 4), 60_000L)
}
