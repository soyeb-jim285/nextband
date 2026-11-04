package com.soyeb.ieltspractice.feature.writing

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.soyeb.ieltspractice.AppGraph
import com.soyeb.ieltspractice.LocalDemo
import com.soyeb.ieltspractice.LocalGraph
import com.soyeb.ieltspractice.data.currentUserId
import com.soyeb.ieltspractice.data.demo.DemoCommunity
import com.soyeb.ieltspractice.data.logic.clock
import com.soyeb.ieltspractice.data.logic.minWords
import com.soyeb.ieltspractice.data.logic.newSessionId
import com.soyeb.ieltspractice.data.logic.taskLabel
import com.soyeb.ieltspractice.data.model.Prompt
import com.soyeb.ieltspractice.data.net.ApiError
import com.soyeb.ieltspractice.data.net.AppJson
import com.soyeb.ieltspractice.design.NB
import com.soyeb.ieltspractice.design.Tone
import com.soyeb.ieltspractice.design.components.Banner
import com.soyeb.ieltspractice.design.components.BtnKind
import com.soyeb.ieltspractice.design.components.BtnSize
import com.soyeb.ieltspractice.design.components.ConfirmDialog
import com.soyeb.ieltspractice.design.components.ErrorState
import com.soyeb.ieltspractice.design.components.Meter
import com.soyeb.ieltspractice.design.components.NbButton
import com.soyeb.ieltspractice.design.components.NbCard
import com.soyeb.ieltspractice.design.components.NbIconButton
import com.soyeb.ieltspractice.design.components.NbListRow
import com.soyeb.ieltspractice.design.components.NbSnackbarHost
import com.soyeb.ieltspractice.design.components.Overline
import com.soyeb.ieltspractice.design.components.PushedScreen
import com.soyeb.ieltspractice.design.components.RowDivider
import com.soyeb.ieltspractice.design.components.RowGroup
import com.soyeb.ieltspractice.design.components.Segmented
import com.soyeb.ieltspractice.design.components.SkeletonList
import com.soyeb.ieltspractice.design.components.Tag
import com.soyeb.ieltspractice.design.components.TimerPill
import com.soyeb.ieltspractice.design.components.readable
import com.soyeb.ieltspractice.nav.AttemptResult
import com.soyeb.ieltspractice.nav.Nav
import com.soyeb.ieltspractice.nav.WritingTask
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

// The writing exam: a wall-clock countdown (warn at 5 min, bad at 1 min, with haptics and a notice), auto-submit at 0 when the setting
// is on (else overtime), essay and plan autosaved per prompt, paste blocked when the setting is on, no autocorrect. A full test is
// Task 1 + Task 2 on one 60-minute clock. Mobile layout: the task folds into one line once you start typing; a bar with the word
// count, Plan and Submit rides above the keyboard.

private const val PASTE_NOTICE = "Pasting is disabled in exam mode"
private const val PLAN_PLACEHOLDER = "Position: …\nBody 1: idea + example\nBody 2: idea + example\nConclusion: …"
private const val ANSWER_PLACEHOLDER = "Start writing here. Your draft saves on this device as you type."

private class Flags { var autoFired = false; var submitted = false }

@Composable
fun WritingTaskScreen(route: WritingTask, nav: Nav) {
    val graph = LocalGraph.current
    // The loaded prompts and the start time survive rotation and process death, so the task never reshuffles under a half-written essay.
    var saved by rememberSaveable { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    LaunchedEffect(attempt) {
        if (saved != null) return@LaunchedEffect
        error = null
        try {
            saved = AppJson.encodeToString(ExamSession.serializer(), loadSession(graph, route))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.message ?: "Something went wrong."
        }
    }
    val session = remember(saved) { saved?.let { AppJson.decodeFromString(ExamSession.serializer(), it) } }
    if (session != null) ExamScreen(session, nav) else LoadingEditor(nav, error) { attempt++ }
}

@Composable
private fun LoadingEditor(nav: Nav, error: String?, onRetry: () -> Unit) {
    PushedScreen("Writing", nav::back, close = true) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            if (error == null) SkeletonList() else ErrorState(error, onRetry)
        }
    }
}

private suspend fun loadSession(g: AppGraph, r: WritingTask): ExamSession {
    val api = g.api
    r.mockId?.let { id ->
        // A full mock test: both tasks, the shared session id and the time already used come from the mock; the clock continues from there.
        val m = api.mockWritingStart(id)
        return ExamSession(m.prompts.sortedBy { it.part }, System.currentTimeMillis() - m.elapsedS.toLong() * 1000L, m.writingSessionId, null, id)
    }
    val variant = r.variant ?: g.prefs.testType.first()
    val promptId = r.promptId
    val prompts = when {
        promptId != null && r.task != "full" -> listOf(api.prompt(promptId))
        r.task == "full" && promptId != null && r.promptId2 != null -> listOf(api.prompt(promptId), api.prompt(r.promptId2))
        r.task == "full" -> listOf(api.randomPrompt("writing", part = 1, variant = variant), api.randomPrompt("writing", part = 2))
        r.task == "task1" -> listOf(api.randomPrompt("writing", part = 1, variant = variant))
        else -> listOf(api.randomPrompt("writing", part = 2))
    }
    return ExamSession(prompts, System.currentTimeMillis(), newSessionId(), r.parentId)
}

@Composable
private fun ExamScreen(session: ExamSession, nav: Nav) {
    val graph = LocalGraph.current
    val demo = LocalDemo.current
    val store = remember { DraftStore(graph.prefs) { if (demo != null) "demo" else draftOwner(graph.api) } }
    val drafts by produceState<Map<String, Draft>?>(null, session) {
        if (demo == null && graph.api.isSignedIn) currentUserId(graph.api) // the drafts are keyed by the account id: know it before loading
        value = session.prompts.mapIndexed { i, p -> p.id to (if (demo != null) demoDraft(demo.screen, demo.tab, i) else store.load(p.id)) }.toMap()
    }
    val d = drafts
    if (d == null) LoadingEditor(nav, null) {} else ExamBody(session, d, store, nav)
}

private val TextMapSaver = Saver<SnapshotStateMap<String, String>, HashMap<String, String>>(
    save = { HashMap(it) },
    restore = { mutableStateMapOf<String, String>().apply { putAll(it) } },
)

@Composable
private fun ExamBody(session: ExamSession, initial: Map<String, Draft>, store: DraftStore, nav: Nav) {
    val graph = LocalGraph.current
    val api = graph.api
    val demo = LocalDemo.current
    val variant = demo?.tab // demo only: which state the screenshot shows (see WritingScreens.kt)
    val hf = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    val snack = remember { androidx.compose.material3.SnackbarHostState() } // own host: the root one sits under the keyboard
    val me by api.me.collectAsState()
    val blockPaste = me?.settings?.blockPaste ?: true
    val autoSubmit = me?.settings?.writingAutoSubmit ?: false
    val prompts = session.prompts
    val multi = prompts.size > 1
    val total = examSeconds(prompts)
    val flags = remember { Flags() }
    val created = remember { mutableMapOf<String, String>() } // promptId to attemptId, so a retried submit never duplicates attempts
    // Saveable: rotation or fold/unfold keeps what was typed, even the last moments before the debounced autosave.
    val texts = rememberSaveable(saver = TextMapSaver) { mutableStateMapOf<String, String>().apply { prompts.forEach { put(it.id, initial[it.id]?.text.orEmpty()) } } }
    val plans = rememberSaveable(saver = TextMapSaver) { mutableStateMapOf<String, String>().apply { prompts.forEach { put(it.id, initial[it.id]?.plan.orEmpty()) } } }

    var task by rememberSaveable { mutableIntStateOf(0) }
    val p = prompts[task.coerceIn(prompts.indices)]
    var promptOpen by remember { mutableStateOf(texts[p.id].orEmpty().isEmpty()) } // an answer already under way starts with the task folded
    var showSubmit by remember { mutableStateOf(variant == "Submit") }
    var showPlan by remember { mutableStateOf(variant == "Plan") }
    var showTask by remember { mutableStateOf(false) }
    var showExit by remember { mutableStateOf(false) }
    var submitting by remember { mutableStateOf(false) }
    var submitError by remember { mutableStateOf<String?>(null) }

    fun toast(text: String) {
        snack.currentSnackbarData?.dismiss()
        scope.launch {
            snack.showSnackbar(text, duration = if (demo != null) androidx.compose.material3.SnackbarDuration.Indefinite else androidx.compose.material3.SnackbarDuration.Short)
        }
    }
    val pasteBlocked = { hf.performHapticFeedback(HapticFeedbackType.LongPress); toast(PASTE_NOTICE) }

    // Wall clock, not ticks: the deadline is startedAt + seconds, so backgrounding or a throttled loop can't drift it.
    val startedAt = session.startedAt
    var now by remember { mutableLongStateOf(if (demo != null) startedAt + demoElapsed(variant, total) * 1000L else System.currentTimeMillis()) }
    LaunchedEffect(session) { if (demo == null) while (true) { now = System.currentTimeMillis(); delay(250) } }
    val left = remember { derivedStateOf { secondsLeft(total, startedAt, now) } }

    // Autosave: a moment after typing stops, whenever the app goes to the background, and when the screen closes.
    fun persist() {
        if (flags.submitted || demo != null) return
        val items = prompts.map { q ->
            Triple(q.id, Draft(texts[q.id].orEmpty(), plans[q.id].orEmpty()), DraftMeta(q.id, q.part, q.title, q.variant, pairId = prompts.firstOrNull { it.id != q.id }?.id).takeIf { session.mockId == null })
        }
        graph.scope.launch { items.forEach { (id, d, meta) -> store.save(id, d, meta) } }
    }
    LaunchedEffect(session) { snapshotFlow { prompts.map { texts[it.id] to plans[it.id] } }.collectLatest { delay(800); persist() } }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { persist() }
    DisposableEffect(Unit) { onDispose { persist() } }

    // A mock test keeps the Writing clock on the server (every 15 s, when the app goes to the background and when the screen closes).
    val mockId = session.mockId
    fun saveClock() {
        if (mockId == null || flags.submitted || demo != null) return
        val used = ((System.currentTimeMillis() - startedAt) / 1000).toInt()
        graph.scope.launch { runCatching { api.mockWritingClock(mockId, used) } }
    }
    LaunchedEffect(session) { if (mockId != null && demo == null) while (true) { delay(15_000); saveClock() } }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { saveClock() }
    DisposableEffect(Unit) { onDispose { saveClock() } }

    val counts = prompts.map { TaskCount(it.part, wordCount(texts[it.id].orEmpty())) }

    /** Create the attempt(s) and submit them; lands on the result. The text stays in the editor and the saved draft on failure. */
    fun submit() {
        if (submitting) return
        submitting = true
        submitError = null
        val overtime = left.value < 0
        // Editor time, split across the tasks of a full test so weekly minutes don't count it twice.
        val durationMs = (maxOf(0, total - left.value) * 1000L / prompts.size).toInt()
        val items = prompts.map { q -> Triple(q, texts[q.id].orEmpty(), plans[q.id].orEmpty()) }
        // The attempt ids also live in prefs, so a retry after rotation, process death or reopening the mock never creates a second attempt.
        val createdKey = "wcreated:${session.sessionId}"
        scope.launch {
            try {
                if (created.isEmpty()) graph.prefs.getString(createdKey)?.split(",")?.forEach { kv ->
                    kv.split("=").takeIf { it.size == 2 }?.let { created[it[0]] = it[1] }
                }
                val ids = mutableListOf<String>()
                for ((q, text, plan) in items) {
                    val id = created[q.id] ?: api.createAttempt(
                        q.id, "writing", q.part, mode = if (multi) "exam" else "practice", sessionId = if (multi) session.sessionId else null,
                        mockId = session.mockId, parentAttemptId = session.parentId, text = text,
                    ).id.also { id ->
                        created[q.id] = id
                        graph.prefs.setString(createdKey, created.entries.joinToString(",") { (k, v) -> "$k=$v" })
                    }
                    try {
                        api.submitWriting(id, text, plan.takeIf { q.part == 2 && it.isNotBlank() }, durationMs, overtime)
                    } catch (e: ApiError) {
                        if (e.status != 409) throw e // 409: already submitted on an earlier try
                    }
                    ids += id
                }
                flags.submitted = true
                store.clear(prompts.map { it.id })
                graph.scope.launch { graph.prefs.setString(createdKey, null) }
                hf.performHapticFeedback(HapticFeedbackType.LongPress)
                if (session.mockId != null) nav.back() else nav.replace(AttemptResult(ids)) // a mock test goes back to its hub
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Out of tests (another device used it, the window moved, scoring is paused): the draft stays and a sheet says so.
                val blocked = e is ApiError && nav.reportBlocked(e, "writing", "Your essay is still here.")
                if (!blocked) submitError = e.message ?: "Couldn't submit. Your answer is still here; try again."
                submitting = false
                showSubmit = false
            }
        }
    }

    // Time notices: haptics come from TimerPill; this adds the words.
    LaunchedEffect(session) {
        // Start just above zero so a session reopened after its time ran out (a mock resumed late) still gets its time-up.
        var prev = maxOf(left.value, 1)
        snapshotFlow { left.value }.collect { cur ->
            when (timeEvent(prev, cur)) {
                TimeEvent.Warn5 -> toast("5 minutes left")
                TimeEvent.Warn1 -> toast("1 minute left")
                TimeEvent.TimeUp -> if (autoSubmit) {
                    if (!flags.autoFired) {
                        flags.autoFired = true
                        showSubmit = false
                        toast("Time is up. Submitting your answer…")
                        submit()
                    }
                } else toast("Time is up. You can keep writing; it will be marked overtime.")
                null -> {}
            }
            prev = cur
        }
    }

    LaunchedEffect(showSubmit, showExit) { if (showSubmit || showExit) focus.clearFocus() }
    LaunchedEffect(Unit) { if (demo?.screen == "editor-blocked") nav.gate = DemoCommunity.gate("editor-blocked") }
    // Nothing to lose on an untouched single test: let Back pop with the system predictive-back animation.
    val typed = prompts.any { texts[it.id].orEmpty().isNotBlank() || plans[it.id].orEmpty().isNotBlank() }
    BackHandler(enabled = submitting || typed || session.mockId != null) { if (submitting) toast("Submitting your answer…") else showExit = true }

    val idx = prompts.indexOf(p)
    val need = minWords(p.part)
    val scroll = rememberScrollState()
    val density = LocalDensity.current
    // Landscape phone or very large text with the keyboard up: drop the fixed rows so the essay field keeps some height.
    val cramped = WindowInsets.isImeVisible && (LocalConfiguration.current.screenHeightDp < 480 || density.fontScale > 1.3f)
    LaunchedEffect(variant) {
        if (variant == "Chart" || demo?.screen?.startsWith("chart-") == true) {
            snapshotFlow { scroll.maxValue }.first { it > 0 }
            scroll.scrollTo(with(density) { 300.dp.roundToPx() })
        }
    }

    Box(Modifier.fillMaxSize()) {
        PushedScreen(
            title = if (multi) "Full test" else taskLabel(p),
            onBack = { if (!submitting) showExit = true },
            modifier = Modifier.imePadding(),
            close = true,
            scrollBehavior = null,
            actions = {
                TimerPill(left.value.coerceAtLeast(0).toLong())
                Spacer(Modifier.width(4.dp))
                var menu by remember { mutableStateOf(false) }
                Box {
                    NbIconButton(Icons.Rounded.MoreVert, "More options", { menu = true })
                    DropdownMenu(menu, { menu = false }, containerColor = NB.colors.surface) {
                        DropdownMenuItem(text = { Text("Show task", style = NB.type.body) }, onClick = { menu = false; showTask = true })
                        if (p.part == 2) DropdownMenuItem(text = { Text("Plan notes", style = NB.type.body) }, onClick = { menu = false; showPlan = true })
                        DropdownMenuItem(
                            text = { Text("Word goal", style = NB.type.body) },
                            onClick = { menu = false; toast("${if (p.part == 1) "Task 1" else "Task 2"} needs at least $need words. Fewer costs ${if (p.part == 1) "Task Achievement" else "Task Response"} marks.") },
                        )
                    }
                }
            },
            bottomBar = {
                Column {
                    AccessoryBar(counts[idx].words, need, left, p.part == 2 && !cramped, { showPlan = true }, { showSubmit = true }, submitting, cramped) // Plan stays in the ⋮ menu
                    if (variant == "Keyboard") FakeKeyboard()
                }
            },
        ) { pad ->
            Box(Modifier.padding(pad).fillMaxSize()) {
                Column(Modifier.fillMaxSize().readable()) {
                    if (multi && !cramped) Segmented(
                        prompts.mapIndexed { i, q -> "${if (q.part == 1) "Task 1" else "Task 2"} · ${plural(counts[i].words)}" }, task,
                        { i -> task = i; promptOpen = texts[prompts[i].id].orEmpty().isEmpty() },
                        Modifier.padding(horizontal = NB.space.gutter, vertical = 8.dp),
                    )
                    AnimatedVisibility(!promptOpen && !cramped) { CollapsedTask(p) { promptOpen = true } }
                    submitError?.let { msg ->
                        Banner(msg, Icons.Rounded.ErrorOutline, Modifier.padding(horizontal = NB.space.gutter, vertical = 8.dp), Tone.Bad, "Try again") { submit() }
                    }
                    Column(Modifier.weight(1f).verticalScroll(scroll)) {
                        if (promptOpen) TaskCard(p, Modifier.padding(horizontal = NB.space.gutter, vertical = 8.dp)) { promptOpen = false }
                        key(p.id) {
                            Box(Modifier.padding(horizontal = NB.space.gutter, vertical = 12.dp)) {
                                ExamTextField(
                                    texts[p.id].orEmpty(),
                                    { v -> if (texts[p.id].orEmpty().isEmpty() && v.isNotEmpty() && promptOpen) promptOpen = false; texts[p.id] = v }, // first keystroke: fold the task
                                    blockPaste, pasteBlocked,
                                    label = "Your answer to ${if (p.part == 1) "Task 1" else "Task 2"}", placeholder = ANSWER_PLACEHOLDER, minLines = 12,
                                )
                            }
                        }
                        Spacer(Modifier.height(24.dp))
                    }
                }
                NbSnackbarHost(snack, Modifier.align(Alignment.BottomCenter))
            }
        }

        if (showSubmit) {
            val note = submitNote(counts, left.value)
            val short = tooShort(counts)
            val under = underMinimum(counts) != null
            SheetOrInline(
                onDismiss = { if (!submitting) showSubmit = false },
                title = if (multi) "Submit both tasks?" else "Submit your answer?",
                subtitle = "You can't edit after submitting. Marking takes about a minute.",
                actions = {
                    if (under) {
                        NbButton("Keep writing", { showSubmit = false }, fill = true, size = BtnSize.Large)
                        NbButton("Submit anyway", { submit() }, kind = BtnKind.Text, fill = true, enabled = !short, loading = submitting)
                    } else {
                        NbButton("Submit", { submit() }, fill = true, size = BtnSize.Large, loading = submitting, haptic = true)
                        NbButton("Keep writing", { showSubmit = false }, kind = BtnKind.Text, fill = true, enabled = !submitting)
                    }
                },
            ) {
                RowGroup {
                    prompts.forEachIndexed { i, q ->
                        if (i > 0) RowDivider()
                        val low = counts[i].words < minWords(q.part)
                        NbListRow(
                            taskLabel(q),
                            subtitle = q.title.takeIf { multi },
                            trailing = {
                                Text(
                                    plural(counts[i].words) + if (low) ", under ${minWords(q.part)}" else "",
                                    style = NB.type.num, color = if (low) NB.colors.warnText else NB.colors.goodText,
                                )
                            },
                        )
                    }
                }
                note?.let { Text(it, style = NB.type.bodySmall, color = if (short) NB.colors.muted else NB.colors.warnText) }
            }
        }
        if (showPlan) SheetOrInline(
            onDismiss = { showPlan = false },
            title = "Plan",
            subtitle = "Your notes are saved with this draft and checked against your essay.",
            actions = { NbButton("Done", { showPlan = false }, fill = true, size = BtnSize.Large) },
        ) {
            val planned = prompts.firstOrNull { it.part == 2 } ?: p
            key("plan-${planned.id}") {
                ExamTextField(
                    plans[planned.id].orEmpty(), { plans[planned.id] = it }, blockPaste, pasteBlocked,
                    label = "Essay plan", placeholder = PLAN_PLACEHOLDER, minLines = 6, modifier = Modifier.imePadding(),
                )
            }
        }
        if (showTask) SheetOrInline(onDismiss = { showTask = false }, title = taskLabel(p)) { PromptBody(p) }
        if (showExit) ConfirmDialog(
            title = "Leave this test?",
            body = if (session.mockId != null) "Your draft stays saved on this device and the time you have used is kept. Come back to the mock test to continue."
            else "Your draft stays saved on this device. The timer restarts when you come back.",
            confirm = "Leave", onConfirm = { showExit = false; nav.back() }, onDismiss = { showExit = false }, dismiss = "Keep writing",
        )
    }
}

/** The bar that rides above the keyboard: words against the goal with a thin meter, the clock, Plan (Task 2) and Submit. */
@Composable
private fun AccessoryBar(words: Int, need: Int, left: State<Int>, plan: Boolean, onPlan: () -> Unit, onSubmit: () -> Unit, submitting: Boolean, compact: Boolean = false) {
    val c = NB.colors
    val reached = words >= need
    val l = left.value
    Surface(color = c.surface, contentColor = c.ink, modifier = Modifier.fillMaxWidth()) {
        Column {
            HorizontalDivider(color = c.line)
            // Large text: the buttons would squeeze the count and clock to nothing, so they move to a row of their own.
            val stack = LocalDensity.current.fontScale > 1.3f && !compact
            val buttons: @Composable () -> Unit = {
                if (plan) NbButton("Plan", onPlan, kind = BtnKind.Secondary)
                NbButton("Submit", onSubmit, enabled = !submitting)
            }
            // The scaffold's imePadding already eats the nav-bar inset while the keyboard is up, so this only pads when it is down.
            Column(Modifier.fillMaxWidth().readable().navigationBarsPadding().padding(horizontal = NB.space.gutter, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(
                    Modifier.weight(1f).semantics(mergeDescendants = true) { contentDescription = "$words of $need words" + if (reached) ", minimum reached" else "" },
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "$words / $need words", Modifier.weight(1f, fill = false),
                            style = NB.type.label.copy(fontFeatureSettings = "tnum"), color = if (reached) c.goodText else c.ink, maxLines = 2,
                        )
                        Spacer(Modifier.width(8.dp))
                        Icon(Icons.Rounded.Timer, null, Modifier.width(14.dp).height(14.dp), tint = c.muted)
                        Spacer(Modifier.width(2.dp))
                        Text(
                            if (l < 0) "+${clock(-l)}" else clock(l),
                            style = NB.type.caption.copy(fontFeatureSettings = "tnum"), color = if (l <= 60) c.badText else if (l <= 300) c.warnText else c.muted, maxLines = 1,
                        )
                    }
                    Meter(words.toDouble(), max = need.toDouble(), color = if (reached) c.good else c.brand, height = 4.dp)
                }
                if (!stack) buttons()
            }
            if (stack) Row(Modifier.align(Alignment.End), horizontalArrangement = Arrangement.spacedBy(10.dp)) { buttons() }
            }
        }
    }
}

/** Demo only: a stand-in for the keyboard, since a screenshot has none. */
@Composable
private fun FakeKeyboard() {
    Box(Modifier.fillMaxWidth().height(260.dp).background(NB.colors.surface3), contentAlignment = Alignment.Center) {
        Text("Keyboard", style = NB.type.label, color = NB.colors.muted)
    }
}

/** One line of the task, pinned above the answer once you start typing. */
@Composable
private fun CollapsedTask(p: Prompt, onShow: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = NB.space.gutter, vertical = 4.dp).heightIn(min = 48.dp)
            .clip(NB.shape.tile).background(NB.colors.surface2)
            .clickable(onClickLabel = "Show the task", role = Role.Button, onClick = onShow).padding(start = 12.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(p.body, Modifier.weight(1f), style = NB.type.readingSmall, color = NB.colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.width(8.dp))
        Tag("Show task", fg = NB.colors.brandText, bg = NB.colors.brandSoft, icon = Icons.Rounded.ExpandMore)
    }
}

/** The full task above the answer: prompt, bullets and the figure. */
@Composable
private fun TaskCard(p: Prompt, modifier: Modifier, onHide: () -> Unit) {
    NbCard(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Overline(taskLabel(p)) }
            NbButton("Hide task", onHide, kind = BtnKind.Text, size = BtnSize.Small, icon = Icons.Rounded.ExpandLess)
        }
        Spacer(Modifier.height(8.dp))
        PromptBody(p)
    }
}

/** The task text in the reading face, with its figure (Task 1 Academic) below. Shared by the card and the task sheet. */
@Composable
internal fun PromptBody(p: Prompt) {
    val spec = p.chartSpec
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (showPromptTitle(p, spec?.title)) Text(p.title, style = NB.type.readingTitle, color = NB.colors.ink)
        Text(p.body, style = NB.type.reading, color = NB.colors.ink)
        p.bullets.orEmpty().forEach { b ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("•", style = NB.type.readingSmall, color = NB.colors.muted)
                Text(b, Modifier.weight(1f), style = NB.type.readingSmall, color = NB.colors.ink)
            }
        }
        letterOpening(p)?.let { Text("Start with: $it", style = NB.type.readingSmall, color = NB.colors.muted) }
        if (spec != null) {
            Spacer(Modifier.height(4.dp))
            ChartView(spec, zoomable = true)
            if (spec.kind in listOf("line", "bar", "pie")) Text("Pinch to zoom, double-tap to reset.", style = NB.type.caption, color = NB.colors.muted)
        }
    }
}
