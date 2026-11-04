package com.soyeb.ieltspractice.feature.speaking

import android.Manifest
import android.content.pm.PackageManager
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import com.soyeb.ieltspractice.AppGraph
import com.soyeb.ieltspractice.data.AnswerWindow
import com.soyeb.ieltspractice.data.PendingRecording
import com.soyeb.ieltspractice.data.currentUserId
import com.soyeb.ieltspractice.data.UploadState
import com.soyeb.ieltspractice.data.demo.DemoConfig
import com.soyeb.ieltspractice.data.logic.newSessionId
import com.soyeb.ieltspractice.data.model.AudioLine
import com.soyeb.ieltspractice.data.model.Prompt
import com.soyeb.ieltspractice.data.net.ApiError
import com.soyeb.ieltspractice.media.AudioPlayer
import com.soyeb.ieltspractice.media.MicRecorder
import com.soyeb.ieltspractice.nav.SpeakingSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.concurrent.thread
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.sin

enum class Phase { Loading, Empty, Failed, Permission, MicCheck, Ready, Prep, Recording, Transition, Finishing }

/** The examiner reading a question (microphone held), then the "Speak now" cue. */
enum class Examiner { Idle, Asking, Cue }

class UploadItem(val id: String, val label: String)

/**
 * The practice flow (web SessionFlow.tsx, iOS SpeakingSessionView): one recording per prompt (P1 topic / P2 card / P3 set) with "Next question"
 * marks, P2 one minute of prep then a 2 min talk with a hard stop. Each recording is kept on disk (PendingStore), uploaded in the background
 * while you continue, and becomes an attempt sharing a session id. A ViewModel, so rotating the phone does not lose a recording.
 *
 * The screen reads the plain `elapsed` / `level` / `levels` copies (refreshed ten times a second) rather than the recorder, so demo
 * mode can show a recording without a microphone.
 */
class SessionModel(
    private val graph: AppGraph,
    private val route: SpeakingSession,
    private val demo: DemoConfig?,
) : ViewModel() {
    var phase by mutableStateOf(Phase.Loading); private set
    var failMessage by mutableStateOf(""); private set
    var items by mutableStateOf<List<Prompt>>(emptyList()); private set
    var index by mutableIntStateOf(0); private set
    var question by mutableIntStateOf(0); private set
    var questionStart by mutableDoubleStateOf(0.0); private set
    var examiner by mutableStateOf(Examiner.Idle); private set
    var prepLeft by mutableIntStateOf(PREP_SECONDS); private set
    var notes by mutableStateOf("")
    var uploads by mutableStateOf<List<UploadItem>>(emptyList()); private set
    var startError by mutableStateOf<String?>(null)
    var micDenied by mutableStateOf(false); private set
    var demoStates by mutableStateOf<Map<String, UploadState>>(emptyMap()); private set

    // What the screen draws (copied from the recorders by the ticker).
    var elapsed by mutableDoubleStateOf(0.0); private set
    var level by mutableDoubleStateOf(0.0); private set
    var levels by mutableStateOf(List(48) { 0.0 }); private set
    var liveWpm by mutableIntStateOf(0); private set
    var silence by mutableDoubleStateOf(0.0); private set
    var checkLevel by mutableDoubleStateOf(0.0); private set
    var checkLoud by mutableIntStateOf(0); private set
    var checkSoft by mutableIntStateOf(0); private set

    private val api get() = graph.api
    private val store get() = graph.pending
    private val recorder = MicRecorder()
    private val micCheck = MicRecorder()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val player by lazy { AudioPlayer(graph.app) }

    /** Answer window of each question on the recording clock. */
    private var windows = mutableListOf<AnswerWindow>()
    private var askJob: Job? = null
    private var prepJob: Job? = null
    private var transitionJob: Job? = null
    private var introduced = -1
    private var recId = newSessionId()
    private var sessionId = newSessionId() // a mock test replaces it with the mock's Speaking session
    private var parent: String? = null
    private var prepEnd = 0L
    private var starting = false
    private var finishing = false // the recording is being closed off the main thread

    val current: Prompt? get() = items.getOrNull(index)
    val mockId: String? get() = route.mockId
    val isFull get() = route.mockId != null || route.mode == "full"
    val recording get() = phase == Phase.Recording

    init {
        reload()
        if (demo == null) scope.launch { tick() }
    }

    fun reload() {
        scope.launch { load() }
    }

    fun hasMic() = ContextCompat.checkSelfPermission(graph.app, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private suspend fun load() {
        phase = Phase.Loading
        try {
            val mock = route.mockId
            if (mock != null) {
                // Full mock test: the questions and the session id are the mock's (choosing again before anything is submitted returns the same set).
                val c = api.mockChoose(mock, "recorded")
                val t = c.test ?: throw ApiError(0, "The mock test has no Speaking questions.")
                sessionId = c.sessionId ?: sessionId
                items = t.part1 + t.part2 + t.part3
            } else {
                val source = api.sourceParam(graph.prefs.speakingSource.first())
                val promptId = route.promptId
                if (promptId != null) {
                    items = listOf(api.prompt(promptId))
                    parent = route.parentId
                } else if (route.mode == "full") {
                    val t = api.speakingTest(source)
                    items = t.part1 + t.part2 + t.part3
                } else {
                    val n = route.mode.removePrefix("part").toIntOrNull()?.coerceIn(1, 3) ?: 1
                    items = listOf(api.randomPrompt("speaking", n, source = source))
                }
            }
            if (demo != null) applyDemo(demo.tab) else if (hasMic()) goMicCheck() else phase = Phase.Permission
        } catch (e: CancellationException) {
            throw e
        } catch (e: ApiError) {
            if (e.status == 404) phase = Phase.Empty else { failMessage = e.message; phase = Phase.Failed }
        } catch (e: Exception) {
            failMessage = e.message ?: "Something went wrong."
            phase = Phase.Failed
        }
    }

    // MARK: Permission and mic check

    fun onPermission(granted: Boolean) {
        if (granted) { micDenied = false; goMicCheck() } else micDenied = true
    }

    /** Back from system settings: continue if the microphone is now allowed. */
    fun recheckPermission() {
        if (phase == Phase.Permission && hasMic()) { micDenied = false; goMicCheck() }
    }

    private fun goMicCheck() {
        phase = Phase.MicCheck
        startError = null
        if (!micCheck.start()) startError = "The microphone couldn't start. Close other apps that use it and try again."
    }

    fun retryMicCheck() {
        if (phase == Phase.MicCheck && !micCheck.isRecording) goMicCheck()
    }

    /** "Continue" (or skip) on the mic check. */
    fun finishMicCheck() {
        micCheck.stop()
        checkLevel = 0.0
        phase = Phase.Ready
        introduce()
    }

    // MARK: Examiner

    /** A question with no rendered line is still asked: [ask] reads its text with the device voice. */
    fun lineFor(p: Prompt, text: String) = p.audio?.questions?.firstOrNull { it.text == text } ?: AudioLine(text)

    /** True when the question is heard rather than shown: the examiner has a recording of it (the text is one tap away). */
    fun spoken(p: Prompt, q: Int): Boolean = lineFor(p, p.questions.getOrNull(q) ?: "").url != null

    /**
     * The examiner reads [lines] (microphone held meanwhile, so the recording holds only the candidate), then the answer window opens
     * with a "Speak now" cue. No pause, seek or speed. A line without audio, a bad URL or a stalled load never blocks the test.
     */
    private fun ask(lines: List<AudioLine?>, resume: Boolean = true) {
        askJob?.cancel()
        askJob = scope.launch {
            val spoken = lines.filterNotNull().filter { it.url != null || it.text.isNotBlank() }
            if (spoken.isNotEmpty()) {
                examiner = Examiner.Asking
                recorder.paused = true
                for (l in spoken) {
                    val u = l.url
                    if (u == null) { graph.tts.speakAndAwait(l.text); continue }
                    player.load(u)
                    player.play()
                    withTimeoutOrNull(60_000) { player.ended.first { it } }
                }
            }
            if (!resume) { examiner = Examiner.Idle; return@launch }
            recorder.paused = false
            questionStart = recorder.elapsed
            examiner = Examiner.Cue
            delay(2500)
            if (examiner == Examiner.Cue) examiner = Examiner.Idle
        }
    }

    /** P2: the examiner introduces the card while the screen waits for the preparation tap. Part 1 and 3 after the first start by themselves (the mic permission is already granted). */
    private fun introduce() {
        val p = current ?: return
        if (introduced == index || (index == 0 && p.part != 2)) return
        introduced = index
        if (p.part == 2) ask(listOf(p.audio?.lead, lineFor(p, p.questions[0])), resume = false) else startRecording()
    }

    // MARK: Actions

    /** Part 2: one minute of preparation, then recording starts by itself. Wall clock, so a backgrounded app still ends prep on time. */
    fun beginPrep() {
        if (phase != Phase.Ready) return
        startError = null
        askJob?.cancel()
        examiner = Examiner.Idle
        prepLeft = PREP_SECONDS
        prepEnd = SystemClock.elapsedRealtime() + PREP_SECONDS * 1000L
        phase = Phase.Prep
        prepJob = scope.launch {
            while (phase == Phase.Prep) {
                prepLeft = max(0, ceil((prepEnd - SystemClock.elapsedRealtime()) / 1000.0).toInt())
                if (prepLeft == 0) { startRecording(); return@launch }
                delay(250)
            }
        }
    }

    /** Needs the microphone permission already granted (the screen asks first). */
    fun startRecording() {
        if ((phase != Phase.Ready && phase != Phase.Prep) || starting) return
        starting = true
        startError = null
        prepJob?.cancel()
        micCheck.stop()
        store.prepare()
        val ok = recorder.start(store.audioFile(recId), startPaused = current?.part != 2)
        starting = false
        if (!ok) {
            startError = "Couldn't start recording. Is another app using the microphone?"
            phase = Phase.Ready
            return
        }
        windows = mutableListOf()
        question = 0
        questionStart = 0.0
        elapsed = 0.0
        phase = Phase.Recording
        val p = current ?: return
        if (p.part == 2) {
            examiner = Examiner.Cue // the card was introduced before the preparation minute
            askJob?.cancel()
            askJob = scope.launch { delay(2500); if (examiner == Examiner.Cue) examiner = Examiner.Idle }
        } else {
            ask(listOf(if (index == 0 && p.part == 1 && items.size > 1) p.audio?.intro else null, p.audio?.lead, lineFor(p, p.questions[0])))
        }
    }

    /** The screen left the foreground (a call, another app): hold the recording so it doesn't fill with silence; it continues on return. */
    fun setAway(away: Boolean) {
        recorder.away = away
        // The mic check holds the microphone for nothing while the screen is hidden: close it, and open it again on return.
        if (phase == Phase.MicCheck) { if (away) micCheck.stop() else retryMicCheck() }
    }

    /** True when the recording must stop now: P2 hard stop at 2:00, the 15 min cap for any part. */
    fun hitLimit(elapsed: Double): Boolean {
        val p = current ?: return false
        return (p.part == 2 && elapsed >= P2_MAX_SECONDS) || elapsed >= MAX_RECORDING_SECONDS
    }

    fun nextQuestion() {
        val p = current ?: return
        if (examiner == Examiner.Asking || question + 1 >= p.questions.size) return
        windows.add(AnswerWindow(question, (questionStart * 1000).toInt(), (recorder.elapsed * 1000).toInt()))
        question += 1
        ask(listOf(lineFor(p, p.questions[question])))
    }

    /** Stops, keeps the recording on disk, uploads it in the background and moves on. */
    fun finishPart(force: Boolean = false) {
        if (phase != Phase.Recording || finishing) return
        val p = current ?: return
        if (examiner == Examiner.Asking && !force) return
        finishing = true
        windows.add(AnswerWindow(question, (questionStart * 1000).toInt(), (recorder.elapsed * 1000).toInt()))
        askJob?.cancel()
        runCatching { player.pause() }
        graph.tts.stop()
        examiner = Examiner.Idle
        // Joining the capture thread and flushing the encoder can take a second or two: keep it off the main thread. NonCancellable so a
        // screen closing meanwhile still keeps and queues the recording.
        scope.launch(start = CoroutineStart.UNDISPATCHED) { withContext(NonCancellable) { try { savePart(p) } finally { finishing = false } } }
    }

    private suspend fun savePart(p: Prompt) {
        val r = withContext(Dispatchers.IO) { recorder.stop() }
        level = 0.0
        if (!store.audioFile(recId).exists()) {
            startError = "Nothing was recorded. Check your microphone and try again."
            phase = Phase.Ready
            return
        }
        val rec = PendingRecording(
            id = recId, promptId = p.id, part = p.part, label = "${partLabel(items, index)}: ${p.topic ?: p.title}",
            createdAt = System.currentTimeMillis(), durationMs = r.durationMs, energy = r.energy.take(20000),
            marks = windows.map { it.startMs }.take(200), segments = windows.take(200),
            sessionId = if (isFull) sessionId else null, parentAttemptId = parent, mockId = route.mockId,
            ownerId = currentUserId(api),
        )
        withContext(Dispatchers.IO) { store.add(rec) }
        uploads = uploads + UploadItem(rec.id, rec.label)
        store.start(rec, api)
        if (index + 1 < items.size) {
            val partChanges = items[index + 1].part != p.part
            index += 1
            question = 0
            notes = ""
            recId = newSessionId()
            if (partChanges) {
                phase = Phase.Transition
                transitionJob = scope.launch { delay(2600); continueFromTransition() }
            } else {
                phase = Phase.Ready
                introduce()
            }
        } else {
            phase = Phase.Finishing
        }
    }

    /** The part interstitial is over (the timer ran out or the learner tapped Continue). */
    fun continueFromTransition() {
        if (phase != Phase.Transition) return
        transitionJob?.cancel()
        phase = Phase.Ready
        introduce()
    }

    fun retry(u: UploadItem) {
        store.items.value.firstOrNull { it.id == u.id }?.let { store.start(it, api) }
    }

    /** Drop a recording in progress (it was never kept) and the mic test. */
    fun discardLive() {
        prepJob?.cancel()
        askJob?.cancel()
        transitionJob?.cancel()
        runCatching { player.pause() }
        graph.tts.stop()
        examiner = Examiner.Idle
        if (recorder.isRecording && !finishing) {
            val f = store.audioFile(recId)
            thread(name = "mic-discard") { recorder.stop(); f.delete() } // the encoder flush blocks; never on the main thread
        }
        micCheck.stop()
    }

    override fun onCleared() {
        discardLive()
        runCatching { player.release() }
        scope.cancel()
    }

    /** Ten times a second: copy the meters for the screen, and stop the recording at its hard limit. */
    private suspend fun tick() {
        while (scope.isActive) {
            if (phase == Phase.MicCheck && micCheck.isRecording) {
                checkLevel = micCheck.level; checkLoud = micCheck.loud; checkSoft = micCheck.soft
            } else if (phase == Phase.Recording) {
                elapsed = recorder.elapsed; level = recorder.level; levels = recorder.levels
                liveWpm = recorder.liveWpm; silence = recorder.silence
                if ((examiner != Examiner.Asking && hitLimit(elapsed)) || recorder.failed) finishPart(force = true)
            }
            delay(100)
        }
    }

    // MARK: Screenshots

    /** Demo mode: show a state without a microphone. [tab] is Permission | MicCheck | Question | Recording | CueCard | Prep | Talk | Transition | Saving. */
    private fun applyDemo(tab: String?) {
        val wave = List(48) { i -> 0.15 + 0.55 * abs(sin(i * 0.55)) }
        val part2 = items.indexOfFirst { it.part == 2 }.coerceAtLeast(0)
        when (tab) {
            "Permission" -> phase = Phase.Permission
            "PermissionDenied" -> { micDenied = true; phase = Phase.Permission }
            "MicCheck" -> { checkLevel = 0.55; checkLoud = 25; phase = Phase.MicCheck }
            "Recording" -> {
                elapsed = 24.0; level = 0.5; levels = wave; liveWpm = 146; question = 1; questionStart = 12.0
                phase = Phase.Recording
            }
            "CueCard" -> { index = part2; phase = Phase.Ready }
            "Prep" -> { index = part2; prepLeft = 38; notes = "books, change my mind, teacher gave it, 2019, empathy"; phase = Phase.Prep }
            "Talk" -> {
                index = part2; elapsed = 78.0; level = 0.4; levels = wave; liveWpm = 138
                notes = "books, change my mind, teacher gave it, 2019, empathy"
                phase = Phase.Recording
            }
            "Transition" -> { index = part2; phase = Phase.Transition }
            "Saving" -> {
                uploads = items.mapIndexed { i, p -> UploadItem("demo-$i", "${partLabel(items, i)}: ${p.topic ?: p.title}") }
                demoStates = mapOf(
                    "demo-0" to UploadState.Done("as2"),
                    "demo-2" to UploadState.Failed("Audio upload failed. Check your connection and retry."),
                )
                phase = Phase.Finishing
            }
            else -> phase = Phase.Ready // "Question"
        }
    }
}
