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
) {
    companion object {
        suspend fun load(prefs: Prefs, attemptId: String): RunLocal = RunLocal(
            marks = parseMarks(prefs.getString("marks:$attemptId")),
            flags = parseFlags(prefs.getString("flags:$attemptId")),
            position = prefs.getString("pos:$attemptId")?.split(",")?.mapNotNull { it.toIntOrNull() }?.takeIf { it.size >= 2 }?.let { it[0] to it[1] },
            audio = prefs.getString("audio:$attemptId"),
            settings = parseSettings(prefs.getString("settings")),
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
    private val partS = HashMap<String, Double>(attempt.stats?.partS.orEmpty())
    private val changes = HashMap<String, Int>(attempt.stats?.changes.orEmpty())
    private val late = LinkedHashSet<Int>(attempt.stats?.late.orEmpty())

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
    private var debounce: Job? = null
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
        dirty = true
        state = SaveState.Dirty
        debounce?.cancel()
        debounce = scope.launch { delay(1000); flush() }
    }

    private fun stats() = LrStats(partS.toMap(), changes.filterValues { it > 0 }, late.toList(), audio?.cleaned())

    /** Persist now when something changed (or [force], for the clock). Failures retry every 5 s. */
    suspend fun flush(force: Boolean = false) {
        if (!live || done || (!dirty && !force)) return
        lock.withLock {
            if (done) return
            dirty = false
            state = SaveState.Saving
            try {
                api.lrSave(attempt.id, responses, elapsed.toInt(), stats())
                state = if (dirty) SaveState.Dirty else SaveState.Saved
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                dirty = true
                state = SaveState.Error
                debounce?.cancel()
                debounce = scope.launch { delay(5000); flush() }
            }
        }
    }

    /** Fire-and-forget [flush] for lifecycle callbacks (stop, leaving the screen). */
    fun flushLater(force: Boolean = false) { scope.launch { flush(force) } }

    /** Scores the attempt. Throws the ApiError on failure (the answers stay saved and the caller may try again). */
    suspend fun submit(): LrAttempt {
        check(!done) { "already submitting" }
        done = true
        debounce?.cancel()
        submitting = true
        try {
            return lock.withLock { api.lrSubmit(attempt.id, responses, elapsed.toInt(), stats()) }
        } catch (e: Throwable) {
            done = false
            throw e
        } finally {
            submitting = false
        }
    }
}
