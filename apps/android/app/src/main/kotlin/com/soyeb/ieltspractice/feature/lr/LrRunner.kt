package com.soyeb.ieltspractice.feature.lr

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Notes
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.TimerOff
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.soyeb.ieltspractice.LocalDemo
import com.soyeb.ieltspractice.LocalGraph
import com.soyeb.ieltspractice.data.logic.FlatQ
import com.soyeb.ieltspractice.data.logic.answeredCount
import com.soyeb.ieltspractice.data.logic.flat
import com.soyeb.ieltspractice.data.logic.isAnswered
import com.soyeb.ieltspractice.data.logic.readingSeconds
import com.soyeb.ieltspractice.data.logic.typeLabel
import com.soyeb.ieltspractice.data.model.LrAttempt
import com.soyeb.ieltspractice.data.model.LrSection
import com.soyeb.ieltspractice.design.DarkColors
import com.soyeb.ieltspractice.design.LightColors
import com.soyeb.ieltspractice.design.LocalNbColors
import com.soyeb.ieltspractice.design.NB
import com.soyeb.ieltspractice.design.NbColors
import com.soyeb.ieltspractice.design.NbTypography
import com.soyeb.ieltspractice.design.Skill
import com.soyeb.ieltspractice.design.components.BtnKind
import com.soyeb.ieltspractice.design.components.BtnSize
import com.soyeb.ieltspractice.design.components.ConfirmDialog
import com.soyeb.ieltspractice.design.components.ErrorState
import com.soyeb.ieltspractice.design.components.Load
import com.soyeb.ieltspractice.design.components.LocalSnack
import com.soyeb.ieltspractice.design.components.NbButton
import com.soyeb.ieltspractice.design.components.NbChip
import com.soyeb.ieltspractice.design.components.NbIconButton
import com.soyeb.ieltspractice.design.components.NbSheet
import com.soyeb.ieltspractice.design.components.Overline
import com.soyeb.ieltspractice.design.components.PushedScreen
import com.soyeb.ieltspractice.design.components.Segmented
import com.soyeb.ieltspractice.design.components.SkeletonList
import com.soyeb.ieltspractice.design.components.Tag
import com.soyeb.ieltspractice.design.components.TimerPill
import com.soyeb.ieltspractice.design.components.clockText
import com.soyeb.ieltspractice.design.components.rememberLoad
import com.soyeb.ieltspractice.design.schemeFor
import com.soyeb.ieltspractice.nav.LrResult
import com.soyeb.ieltspractice.nav.LrRun
import com.soyeb.ieltspractice.nav.MockHub
import com.soyeb.ieltspractice.nav.Nav
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

// The immersive runner for Listening and Reading attempts (web components/lr/Runner.tsx, routes lr/run.$attemptId.tsx).
// No bottom bar, screen kept on. Reading on a phone: the passage full screen with the questions in a draggable sheet; two panes from 600 dp.
// Listening: a pinned player under the top bar, questions below. Autosave about 1 s after a change, on stop, and the clock every 15 s.

/** Opens an attempt: loads it and the device's notes about it, then runs it (a submitted one goes to its result). */
@Composable
fun LrRunScreen(route: LrRun, nav: Nav) {
    val graph = LocalGraph.current
    val load = rememberLoad(route.attemptId) {
        val a = graph.api.lrAttempt(route.attemptId)
        a to RunLocal.load(graph.prefs, a.id)
    }
    when (val s = load.state) {
        Load.Loading -> PushedScreen("Test", nav::back, close = true) { pad -> SkeletonList(Modifier.padding(pad)) }
        is Load.Failed -> PushedScreen("Test", nav::back, close = true) { pad -> ErrorState(s.message, load.reload, Modifier.padding(pad)) }
        is Load.Ready -> {
            val (attempt, local) = s.value
            if (attempt.submitted) {
                LaunchedEffect(attempt.id) { if (route.mockId != null) nav.replace(MockHub(route.mockId)) else nav.replace(LrResult(attempt.id)) }
                PushedScreen("Test", nav::back, close = true) { pad -> SkeletonList(Modifier.padding(pad)) }
            } else if (attempt.test.sections.isEmpty() || attempt.test.flat().isEmpty()) {
                PushedScreen("Test", nav::back, close = true) { pad -> ErrorState("This test has no questions.", load.reload, Modifier.padding(pad)) }
            } else LrRunner(attempt, local, route.mockId, nav)
        }
    }
}

/** The run's clock in seconds, kept out of the runner's own recomposition: only the timer reads it. */
@Stable
class RunClock(start: Double) {
    var seconds by mutableDoubleStateOf(start)
}

/** A high-contrast palette over [base]'s semantic colours, or null for the standard look. */
private fun lrPalette(scheme: String): NbColors? {
    val (bg, well, fg, soft) = when (scheme) {
        "bw" -> listOf(0xFFFFFFFF, 0xFFEDEDED, 0xFF000000, 0xFFE0E0E0)
        "cream" -> listOf(0xFFF5EFDC, 0xFFE8E0C4, 0xFF000000, 0xFFDDD2A8)
        "yb" -> listOf(0xFF000000, 0xFF1F1F00, 0xFFFFE600, 0xFF3A3500)
        else -> return null
    }
    val b = if (scheme == "yb") DarkColors else LightColors
    return NbColors(
        bg = Color(bg), surface = Color(bg), surface2 = Color(well), surface3 = Color(well), ink = Color(fg), muted = Color(fg), faint = Color(fg), line = Color(fg),
        brand = Color(fg), onBrand = Color(bg), brandSoft = Color(soft), brandText = Color(fg),
        good = b.good, goodText = b.goodText, goodSoft = b.goodSoft, warn = b.warn, warnText = b.warnText, warnSoft = b.warnSoft,
        bad = b.bad, badText = b.badText, badSoft = b.badSoft,
        listening = Color(fg), listeningSoft = Color(soft), reading = Color(fg), readingSoft = Color(soft),
        writing = b.writing, writingSoft = b.writingSoft, speaking = b.speaking, speakingSoft = b.speakingSoft,
        scrim = b.scrim, isDark = scheme == "yb",
    )
}

@Composable
private fun LrRunner(attempt: LrAttempt, local: RunLocal, mockId: String?, nav: Nav) {
    val graph = LocalGraph.current
    val demo = LocalDemo.current
    val isDemo = demo != null
    val dv = demo?.tab.orEmpty()
    var settings by remember(attempt.id) { mutableStateOf(local.settings) }
    val pal = lrPalette(settings.scheme)
    val body: @Composable () -> Unit = {
        RunnerBody(attempt, local, mockId, nav, isDemo, dv, settings) { next ->
            settings = next
            if (!isDemo) graph.scope.launch { graph.prefs.setString("settings", settingsJson(next)) }
        }
    }
    CompositionLocalProvider(LocalLrScheme provides settings.scheme) {
        if (pal == null) body() else CompositionLocalProvider(LocalNbColors provides pal) {
            MaterialTheme(colorScheme = schemeFor(pal), typography = NbTypography, shapes = MaterialTheme.shapes) { body() }
        }
    }
}

@Composable
private fun RunnerBody(
    attempt: LrAttempt,
    local: RunLocal,
    mockId: String?,
    nav: Nav,
    isDemo: Boolean,
    dv: String,
    settings: LrSettings,
    onSettings: (LrSettings) -> Unit,
) {
    val c = NB.colors
    val graph = LocalGraph.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snack = LocalSnack.current
    val hf = LocalHapticFeedback.current
    val test = attempt.test
    val sections = test.sections
    val listening = test.listening
    val exam = attempt.exam
    val examListening = listening && exam
    val flat = remember(test) { test.flat() }
    val total = flat.size
    val skill = if (listening) Skill.Listening else Skill.Reading
    val noun = if (listening) "Part" else "Passage"

    // Keep the screen on while a test is open.
    val view = LocalView.current
    DisposableEffect(view) { view.keepScreenOn = true; onDispose { view.keepScreenOn = false } }

    val session = remember(attempt.id) {
        LrSession(
            attempt, graph.api, graph.scope, live = !isDemo, localAudio = local.audio,
            saveLocalAudio = { v -> graph.scope.launch { graph.prefs.setString("audio:${attempt.id}", v) } },
        )
    }
    val reg = remember { QRegistry() }
    val hl = remember(attempt.id) {
        Marks(if (isDemo) demoMarks(dv) else local.marks) { list -> if (!isDemo) graph.scope.launch { graph.prefs.setString("marks:${attempt.id}", marksJson(list)) } }
            .also { if (isDemo && dv == "Highlight") { it.draft = Sel("passage:1:2", 8, 44); it.pending = true } }
    }

    // ---- where we are ----
    val demoStart: Pair<Int, Int>? = when (dv) {
        "Matching" -> if (listening) 2 to 21 else 1 to 14
        "WordBox" -> 1 to 23
        "Map" -> 1 to 13
        else -> null
    }
    var partIdx by rememberSaveable { mutableIntStateOf(demoStart?.first ?: local.position?.first?.coerceIn(0, sections.lastIndex) ?: 0) }
    var current by rememberSaveable { mutableIntStateOf(demoStart?.second ?: local.position?.second?.takeIf { n -> flat.any { it.n == n } } ?: flat.first().n) }
    var active by remember { mutableStateOf<Int?>(null) }
    var flagged by remember(attempt.id) { mutableStateOf(if (isDemo) (if (dv == "Navigator") setOf(4, 9) else emptySet()) else local.flags) }
    val section = sections[partIdx.coerceIn(0, sections.lastIndex)]
    val responses = session.responses

    var navOpen by remember { mutableStateOf(dv == "Navigator") }
    var confirm by remember { mutableStateOf(dv == "Submit") }
    var leave by remember { mutableStateOf(false) }
    var notesOpen by remember { mutableStateOf(false) }
    var helpOpen by remember { mutableStateOf(false) }
    var settingsOpen by remember { mutableStateOf(dv == "Settings") }
    var menuOpen by remember { mutableStateOf(false) }
    var hideTimer by remember { mutableStateOf(false) }
    var leaving by remember { mutableStateOf(false) }
    var submitError by remember { mutableStateOf<String?>(null) }

    // ---- clocks ----
    val clock = remember(attempt.id) { RunClock(attempt.elapsedS.toDouble()) }
    val playlist = remember(attempt.id) {
        val urls = if (examListening) sections.map { attempt.assets[it.audio.orEmpty()].orEmpty() } else emptyList()
        val demoPhase = if (isDemo && examListening) when (dv) { "Gate" -> ExamPhase.Idle; "Review" -> ExamPhase.Review; else -> ExamPhase.Audio } else null
        ExamPlaylist(urls, attempt.elapsedS.toDouble(), demoPhase, test.checkEndsAt)
    }
    DisposableEffect(playlist) { if (examListening) playlist.attach(context); onDispose { playlist.release() } }
    LaunchedEffect(playlist) { if (examListening && !isDemo) playlist.tick { session.elapsed = it } }
    LaunchedEffect(examListening) {
        if (examListening || isDemo) return@LaunchedEffect
        val t0 = System.nanoTime()
        val base = clock.seconds
        while (true) {
            delay(500)
            clock.seconds = base + (System.nanoTime() - t0) / 1e9
            session.elapsed = clock.seconds
        }
    }
    val started = !examListening || playlist.phase != ExamPhase.Idle
    val limit = readingSeconds(attempt.parts)
    remember(session) {
        session.lateFrom = { if (listening) (if (exam && playlist.total > 0) playlist.total else Double.POSITIVE_INFINITY) else limit - 300.0 }
        0
    }

    var foreground by remember { mutableStateOf(true) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { foreground = true }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { foreground = false }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { if (!isDemo) session.flushLater(true) }
    DisposableEffect(session) { onDispose { if (!isDemo) session.flushLater(true) } }
    LaunchedEffect(session) { if (!isDemo) while (true) { delay(15_000); session.flush(true) } }
    val partNo = section.part
    LaunchedEffect(started, partNo) { if (started && !isDemo) while (true) { delay(1000); if (foreground) session.tickPart(partNo) } }
    LaunchedEffect(partIdx, current, flagged) {
        if (!isDemo) {
            graph.scope.launch {
                graph.prefs.setString("pos:${attempt.id}", "$partIdx,$current")
                graph.prefs.setString("flags:${attempt.id}", flagsText(flagged))
            }
        }
    }

    fun submit() {
        if (leaving || session.submitting) return
        scope.launch {
            try {
                session.submit()
                leaving = true
                graph.scope.launch { graph.prefs.setString("marks:${attempt.id}", null) } // notes are not kept past the attempt
                if (mockId != null) nav.replace(MockHub(mockId)) else nav.replace(LrResult(attempt.id))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                confirm = false
                submitError = e.message?.takeIf { it.isNotBlank() } ?: "Could not submit. Your answers are saved; try again."
            }
        }
    }
    fun exit() {
        leaving = true
        session.flushLater(true)
        nav.back()
    }

    // Reading exam: warnings at 10 and 5 minutes, auto-submit at 0. Listening exam: auto-submit when the checking time ends.
    LaunchedEffect(session) {
        if (isDemo || !exam) return@LaunchedEffect
        snapshotFlow { if (listening) playlist.phase == ExamPhase.Review && playlist.reviewLeft == 0 else limit - clock.seconds.toInt() <= 0 }.first { it }
        submit()
    }
    LaunchedEffect(session) {
        if (isDemo || !exam || listening) return@LaunchedEffect
        snapshotFlow { readingTone(limit - clock.seconds.toInt(), limit) }.distinctUntilChanged().collect { t ->
            if (t == 1) snack.showSnackbar("10 minutes left") else if (t == 2) snack.showSnackbar("5 minutes left")
        }
    }

    BackHandler(enabled = !leaving) { leave = true }

    // ---- layout mode ----
    val wide = LocalConfiguration.current.screenWidthDp >= 600
    val phoneReading = !listening && !wide
    val sheetState = rememberStandardBottomSheetState(
        initialValue = if (dv == "Questions" || dv == "Matching" || dv == "WordBox" || dv == "Submit" || dv == "Navigator") SheetValue.Expanded else SheetValue.PartiallyExpanded,
        skipHiddenState = true,
    )
    val sheetScaffold = rememberBottomSheetScaffoldState(sheetState)
    val qScrolls = remember { sections.associate { it.part to ScrollState(0) } }
    val pScrolls = remember { sections.associate { it.part to ScrollState(0) } }
    val qScroll = qScrolls.getValue(section.part)
    val pScroll = pScrolls.getValue(section.part)

    fun tick() { hf.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
    val ctx = QCtx(
        responses, session::change, attempt.assets, graph.api, active = active, flagged = flagged,
        onFocus = { current = it },
        onText = { n, on -> if (on) session.noteFocus(n) else session.noteBlur(n) },
        reg = reg, hl = hl, tick = ::tick,
    )

    fun canGo(n: Int): Boolean = !examListening || flat.firstOrNull { it.n == n }?.part == section.part
    fun goPart(i: Int) {
        partIdx = i
        current = sections[i].groups.firstOrNull()?.from ?: current
    }
    LaunchedEffect(examListening, playlist.phase, playlist.idx) {
        if (examListening && playlist.phase == ExamPhase.Audio) goPart(playlist.idx.coerceIn(0, sections.lastIndex))
    }
    fun jump(n: Int) {
        val f = flat.firstOrNull { it.n == n } ?: return
        if (!canGo(n)) return
        partIdx = sections.indexOfFirst { it.part == f.part }.coerceAtLeast(0)
        current = n
        active = n
        navOpen = false
        scope.launch {
            if (phoneReading) sheetState.expand()
            delay(220)
            ctx.reveal(n, true)
            delay(2500)
            if (active == n) active = null
        }
    }
    fun jumpMark(m: TextMark) {
        notesOpen = false
        val q = markQuestion(m)
        if (q != null) jump(q) else markPart(m)?.let { p -> sections.indexOfFirst { it.part == p }.takeIf { it >= 0 }?.let(::goPart) }
        hl.flash = m.id
        scope.launch { delay(2500); if (hl.flash == m.id) hl.flash = null }
    }
    fun canJumpMark(m: TextMark): Boolean = !examListening || (markQuestion(m)?.let { canGo(it) } ?: (markPart(m) == section.part))
    LaunchedEffect(started) {
        if (started && local.position != null && !isDemo) { delay(300); ctx.reveal(current, false) }
        if (isDemo && (dv == "WordBox")) { delay(300); ctx.reveal(current, false) }
    }

    val navParts = remember(test) { sections.map { s -> NavPart(s.part, "$noun ${s.part}", s.groups.flatMap { g -> g.questions.map { it.n } }) } }
    val answered = { n: Int -> responses.isAnswered(n) }
    val answeredTotal = responses.answeredCount(test)
    val unanswered = total - answeredTotal
    val idx = flat.indexOfFirst { it.n == current }.coerceAtLeast(0)
    val imeUp = WindowInsets.isImeVisible

    Scaffold(
        modifier = Modifier.imePadding(),
        containerColor = c.bg,
        topBar = {
            RunTopBar(
                title = if (!listening) (section.passage?.title ?: "$noun ${section.part}") else section.title ?: "$noun ${section.part}",
                subtitle = { "${section.groups.firstOrNull()?.from?.let { "Questions $it–${section.groups.last().to}" } ?: noun} · ${if (exam) "Exam" else "Practice"} · ${session.state.label}" },
                onClose = { leave = true },
                timer = {
                    if (started) {
                        if (hideTimer) NbIconButton(Icons.Rounded.TimerOff, "Show timer", { hideTimer = false })
                        else RunTimer(clock, playlist, exam, listening, limit)
                    }
                },
                menu = {
                    Box {
                        NbIconButton(Icons.Rounded.MoreVert, "More: notes, text size, timer, help", { menuOpen = true })
                        DropdownMenu(menuOpen, { menuOpen = false }, containerColor = c.surface) {
                            DropdownMenuItem(
                                { Text("Notes${if (hl.list.isNotEmpty()) " (${hl.list.size})" else ""}", style = NB.type.body, color = c.ink) },
                                { menuOpen = false; notesOpen = true }, leadingIcon = { Icon(Icons.Rounded.Notes, null, tint = c.muted) },
                            )
                            DropdownMenuItem(
                                { Text("Text size and colours", style = NB.type.body, color = c.ink) },
                                { menuOpen = false; settingsOpen = true }, leadingIcon = { Icon(Icons.Rounded.TextFields, null, tint = c.muted) },
                            )
                            if (started) DropdownMenuItem(
                                { Text(if (hideTimer) "Show timer" else "Hide timer", style = NB.type.body, color = c.ink) },
                                { menuOpen = false; hideTimer = !hideTimer }, leadingIcon = { Icon(if (hideTimer) Icons.Rounded.Timer else Icons.Rounded.TimerOff, null, tint = c.muted) },
                            )
                            DropdownMenuItem(
                                { Text("Help", style = NB.type.body, color = c.ink) },
                                { menuOpen = false; helpOpen = true }, leadingIcon = { Icon(Icons.AutoMirrored.Rounded.HelpOutline, null, tint = c.muted) },
                            )
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (started && !imeUp) RunBottomBar(
                current = current, total = total, answered = answeredTotal, flagged = current in flagged,
                canPrev = idx > 0 && canGo(flat[idx - 1].n), canNext = idx < total - 1 && canGo(flat[idx + 1].n),
                onNavigator = { navOpen = true },
                onFlag = {
                    flagged = if (current in flagged) flagged - current else flagged + current
                    hf.performHapticFeedback(HapticFeedbackType.LongPress)
                },
                onPrev = { jump(flat[idx - 1].n) }, onNext = { jump(flat[idx + 1].n) },
            )
        },
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            val d = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(d.density, d.fontScale * settings.scale())) {
                Column(Modifier.fillMaxSize()) {
                    if (started && sections.size > 1) PartTabs(
                        labels = navParts.map { it.label },
                        counts = navParts.map { p -> p.questions.count(answered) to p.questions.size },
                        selected = partIdx.coerceIn(0, sections.lastIndex), enabled = !examListening, onSelect = ::goPart,
                    )
                    when {
                        !started -> Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                            ExamGate(playlist, attempt.elapsedS > 0, ::exit, sections.map { it.part })
                        }
                        listening -> Column(Modifier.weight(1f).fillMaxWidth()) {
                            Surface(color = c.surface, modifier = Modifier.fillMaxWidth()) {
                                Column {
                                    Box(Modifier.padding(horizontal = NB.space.gutter, vertical = 8.dp)) {
                                        if (exam) ExamAudioBar(playlist, parts = attempt.parts?.let { sections.map { it.part } })
                                        else key(section.part) {
                                            val url = attempt.assets[section.audio.orEmpty()].orEmpty()
                                            val resume = remember { LrResume(session.audioStart(section.part), session.audioRate, { session.noteAudio(section.part, it) }, session::saveAudio, session::noteRate) }
                                            PracticePlayer(rememberLrPlayer(url, resume), "Part ${section.part}")
                                        }
                                    }
                                    HorizontalDivider(color = c.line)
                                }
                            }
                            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                                key(section.part) { QuestionsPane(section, ctx, qScroll, Modifier.widthIn(max = 720.dp)) }
                            }
                        }
                        wide -> Row(Modifier.weight(1f).fillMaxWidth()) {
                            key(section.part) {
                                Box(Modifier.weight(1f).fillMaxSize().verticalScroll(pScroll).padding(horizontal = NB.space.gutter, vertical = 16.dp)) {
                                    PassageView(section, hl)
                                }
                                Box(Modifier.fillMaxHeight().width(1.dp).background(c.line))
                                QuestionsPane(section, ctx, qScroll, Modifier.weight(1f).fillMaxSize())
                            }
                        }
                        else -> PhoneReading(
                            section = section, ctx = ctx, hl = hl, flat = flat, current = current, flagged = flagged,
                            scaffoldState = sheetScaffold, qScroll = qScroll, pScroll = pScroll,
                            onToggleSheet = { scope.launch { if (sheetState.currentValue == SheetValue.Expanded) sheetState.partialExpand() else sheetState.expand() } },
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                        )
                    }
                }
            }
            MarkSelectionBar(hl, Modifier.align(Alignment.BottomCenter))
        }
    }

    // ---- sheets and dialogs ----
    MarkSheets(hl)
    if (notesOpen) NotesSheet(hl, ::canJumpMark, ::jumpMark) { notesOpen = false }
    if (helpOpen) HelpSheet(listening, exam) { helpOpen = false }
    if (settingsOpen) SettingsSheet(settings, onSettings) { settingsOpen = false }
    if (navOpen) NavigatorSheet(
        parts = navParts, answered = answered, flagged = flagged, current = current, answeredTotal = answeredTotal, total = total,
        canGo = ::canGo, onJump = ::jump, onSubmit = { navOpen = false; confirm = true }, onDismiss = { navOpen = false },
    )
    if (confirm) NbSheet(
        onDismiss = { if (!session.submitting) confirm = false },
        title = "Submit your answers?",
        subtitle = if (unanswered > 0) "$unanswered ${if (unanswered == 1) "question is" else "questions are"} unanswered." else "Every question has an answer.",
        actions = {
            NbButton("Submit answers", ::submit, kind = BtnKind.Primary, size = BtnSize.Large, fill = true, loading = session.submitting, haptic = true)
            NbButton("Keep working", { confirm = false }, kind = BtnKind.Text, fill = true, enabled = !session.submitting)
        },
    ) {
        Text(
            "You cannot change answers after submitting." + if (examListening) " The recording will stop." else "",
            style = NB.type.bodySmall, color = c.muted,
        )
        if (flagged.isNotEmpty()) {
            Text("${flagged.size} flagged for review", style = NB.type.title3, color = c.ink, modifier = Modifier.semantics { heading() })
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                flagged.sorted().forEach { n -> NbChip("Q $n", false, { confirm = false; jump(n) }, icon = Icons.Rounded.Flag) }
            }
        }
        submitError?.let { Text(it, style = NB.type.bodySmall, color = c.badText) }
    }
    if (leave) ConfirmDialog(
        title = "Leave the test?",
        body = "Your answers are saved. You can pick this attempt up again from the ${skill.label} tab." + if (exam) " The exam clock stops while you are away." else "",
        confirm = "Leave", onConfirm = { leave = false; exit() },
        onDismiss = { leave = false }, dismiss = "Keep going",
    )
    if (submitError != null && !confirm) ConfirmDialog(
        title = "Not submitted", body = submitError.orEmpty(), confirm = "OK", onConfirm = { submitError = null }, onDismiss = { submitError = null }, dismiss = "Close",
    )
}

// ---------------------------------------------------------------- pieces

@Composable
private fun RunTopBar(title: String, subtitle: () -> String, onClose: () -> Unit, timer: @Composable () -> Unit, menu: @Composable () -> Unit) {
    val c = NB.colors
    Surface(color = c.bg, contentColor = c.ink) {
        Column {
            Row(Modifier.statusBarsPadding().heightIn(min = 56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                NbIconButton(Icons.Rounded.Close, "Close test", onClose)
                Column(Modifier.weight(1f).padding(horizontal = 4.dp)) {
                    Text(title, style = NB.type.title3, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(subtitle(), style = NB.type.caption, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                timer()
                menu()
            }
            HorizontalDivider(color = c.line)
        }
    }
}

/** The clock: reading exam counts down (warns at 10 and 5 minutes), listening exam shows the time left in the sitting, practice shows time spent. */
@Composable
private fun RunTimer(clock: RunClock, playlist: ExamPlaylist, exam: Boolean, listening: Boolean, limit: Int) {
    when {
        exam && listening && playlist.phase == ExamPhase.Review -> TimerPill(playlist.reviewLeft.toLong(), warnAt = 60, badAt = 15, label = "Checking time left")
        exam && listening -> TimerPill(playlist.timeLeft.toLong(), warnAt = 0, badAt = 0, label = "Time left")
        exam -> TimerPill((limit - clock.seconds.toInt()).toLong().coerceAtLeast(0), warnAt = 600, badAt = 300, label = "Time left")
        else -> Tag(clockText(clock.seconds.toLong()), icon = Icons.Rounded.Timer)
    }
}

@Composable
private fun RunBottomBar(
    current: Int, total: Int, answered: Int, flagged: Boolean, canPrev: Boolean, canNext: Boolean,
    onNavigator: () -> Unit, onFlag: () -> Unit, onPrev: () -> Unit, onNext: () -> Unit,
) {
    val c = NB.colors
    Surface(color = c.surface, contentColor = c.ink) {
        Column {
            HorizontalDivider(color = c.line)
            Row(
                Modifier.navigationBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    onClick = onNavigator, shape = NB.shape.tile, color = c.surface2, contentColor = c.ink,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                        .semantics { contentDescription = "Open the question navigator. Question $current of $total, $answered answered" },
                ) {
                    Row(Modifier.heightIn(min = 48.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Rounded.GridView, null, Modifier.size(22.dp), tint = c.ink)
                        Column {
                            Text("Q $current of $total", style = NB.type.label.copy(fontFeatureSettings = "tnum"), color = c.ink, maxLines = 1)
                            Text("$answered answered", style = NB.type.caption, color = c.muted, maxLines = 1)
                        }
                    }
                }
                Surface(
                    onClick = onFlag, shape = CircleShape, color = if (flagged) c.warnSoft else c.surface2, contentColor = if (flagged) c.warnText else c.ink,
                    modifier = Modifier.size(48.dp).semantics { contentDescription = if (flagged) "Remove flag from question $current" else "Flag question $current for review" },
                ) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Flag, null, Modifier.size(22.dp)) } }
                Surface(
                    onClick = onPrev, enabled = canPrev, shape = CircleShape, color = c.surface2, contentColor = if (canPrev) c.ink else c.faint,
                    modifier = Modifier.size(48.dp).semantics { contentDescription = "Previous question" },
                ) { Box(contentAlignment = Alignment.Center) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, Modifier.size(22.dp)) } }
                Surface(
                    onClick = onNext, enabled = canNext, shape = CircleShape, color = if (canNext) c.brand else c.surface2, contentColor = if (canNext) c.onBrand else c.faint,
                    modifier = Modifier.size(48.dp).semantics { contentDescription = "Next question" },
                ) { Box(contentAlignment = Alignment.Center) { Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, Modifier.size(22.dp)) } }
            }
        }
    }
}

/** Part / passage tabs with the answered count under each. */
@Composable
private fun PartTabs(labels: List<String>, counts: List<Pair<Int, Int>>, selected: Int, enabled: Boolean, onSelect: (Int) -> Unit) {
    val c = NB.colors
    Column(Modifier.fillMaxWidth().background(c.bg)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
            labels.forEachIndexed { i, label ->
                val on = i == selected
                Column(
                    Modifier.weight(1f).heightIn(min = 52.dp).selectable(on, enabled, Role.Tab) { onSelect(i) }.padding(top = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(label, style = NB.type.label, color = if (on) c.brandText else c.muted, maxLines = 1)
                    Text("${counts[i].first}/${counts[i].second}", style = NB.type.caption.copy(fontFeatureSettings = "tnum"), color = c.muted)
                    // A fixed gap, not weight(1f): with no height cap the weighted spacer made the tabs fill the screen,
                    // leaving the passage, questions and audio bar zero height.
                    Spacer(Modifier.height(6.dp))
                    Box(Modifier.height(3.dp).widthIn(min = 32.dp).fillMaxWidth(0.5f).background(if (on) c.brand else Color.Transparent, NB.shape.pill))
                }
            }
        }
        HorizontalDivider(color = c.line)
    }
}

/** The groups of one part, scrolling. */
@Composable
private fun QuestionsPane(section: LrSection, ctx: QCtx, scroll: ScrollState, modifier: Modifier = Modifier) {
    Column(
        modifier.verticalScroll(scroll).padding(horizontal = NB.space.gutter).padding(top = 16.dp),
        verticalArrangement = Arrangement.spacedBy(32.dp),
    ) {
        section.groups.forEach { QuestionGroup(it, ctx) }
        Spacer(Modifier.height(96.dp))
    }
}

/** Paragraph letters in the margin, Newsreader text. [evidence] highlights a stretch (the result's passage tab); [hl] adds highlights and notes. */
@Composable
fun PassageView(section: LrSection, hl: Marks?, modifier: Modifier = Modifier, evidence: com.soyeb.ieltspractice.data.logic.TextSpan? = null) {
    val c = NB.colors
    val p = section.passage ?: return
    val pref by LocalGraph.current.prefs.readingTextScale.collectAsState(initial = 1f)
    val body = NB.type.reading.let { it.copy(fontSize = it.fontSize * pref, lineHeight = it.lineHeight * pref) }
    val title = NB.type.readingTitle.let { it.copy(fontSize = it.fontSize * pref, lineHeight = it.lineHeight * pref) }
    val hasLabels = p.paragraphs.any { !it.label.isNullOrBlank() }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(p.title, style = title, color = c.ink, modifier = Modifier.semantics { heading() })
        p.subtitle?.takeIf { it.isNotBlank() }?.let { Text(it, style = NB.type.readingSmall.copy(fontStyle = FontStyle.Italic), color = c.muted) }
        p.paragraphs.forEachIndexed { i, para ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (hasLabels) Box(Modifier.widthIn(min = 22.dp).padding(top = 2.dp)) {
                    Text(para.label.orEmpty(), style = NB.type.num, color = c.brandText, modifier = Modifier.semantics { contentDescription = "Paragraph ${para.label.orEmpty()}" })
                }
                if (evidence != null && evidence.p == i) EvidenceText(para.text, evidence.s, evidence.e, body, Modifier.weight(1f))
                else MarkedText(hl, "passage:${section.part}:$i", para.text, body, c.ink, Modifier.weight(1f))
            }
        }
    }
}

/** Phone Reading: the passage full screen, the questions in a sheet whose peek shows the current group and question. */
@Composable
private fun PhoneReading(
    section: LrSection, ctx: QCtx, hl: Marks, flat: List<FlatQ>, current: Int, flagged: Set<Int>,
    scaffoldState: androidx.compose.material3.BottomSheetScaffoldState, qScroll: ScrollState, pScroll: ScrollState,
    onToggleSheet: () -> Unit, modifier: Modifier = Modifier,
) {
    val c = NB.colors
    val cur = flat.firstOrNull { it.n == current } ?: flat.first()
    val expanded = scaffoldState.bottomSheetState.currentValue == SheetValue.Expanded
    BottomSheetScaffold(
        scaffoldState = scaffoldState,
        sheetPeekHeight = 136.dp,
        modifier = modifier,
        sheetContainerColor = c.surface, sheetContentColor = c.ink, sheetShape = NB.shape.sheet,
        sheetTonalElevation = 0.dp, sheetShadowElevation = 10.dp,
        sheetDragHandle = { BottomSheetDefaults.DragHandle(color = c.faint) },
        containerColor = c.bg,
        sheetContent = {
            Column(Modifier.fillMaxWidth().fillMaxHeight(0.94f)) {
                Column(
                    Modifier.fillMaxWidth().clickable(onClickLabel = if (expanded) "Show the passage" else "Show the questions", onClick = onToggleSheet)
                        .padding(horizontal = NB.space.gutter, vertical = 2.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Overline(
                            "Questions ${cur.group.from}–${cur.group.to} · ${typeLabel(cur.group)}", color = c.brandText,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(if (expanded) Icons.Rounded.KeyboardArrowDown else Icons.Rounded.KeyboardArrowUp, null, tint = c.muted)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        QNum(cur.n, ctx.value(cur.n).isNotBlank(), cur.n in flagged)
                        Text(
                            questionPrompt(cur.group, cur.q), style = NB.type.bodySmall, color = c.ink, maxLines = 2, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Text(if (expanded) "Drag down to read the passage" else "Drag up to answer", style = NB.type.caption, color = c.muted)
                }
                HorizontalDivider(Modifier.padding(top = 8.dp), color = c.line)
                key(section.part) { QuestionsPane(section, ctx, qScroll, Modifier.weight(1f).fillMaxWidth()) }
            }
        },
        content = { inner ->
            key(section.part) {
                Box(Modifier.fillMaxSize().padding(inner).verticalScroll(pScroll).padding(horizontal = NB.space.gutter, vertical = 16.dp)) {
                    PassageView(section, hl)
                }
            }
        },
    )
}

private fun demoMarks(dv: String): List<TextMark> = if (dv != "Highlight") emptyList() else listOf(
    TextMark("d1", "passage:1:0", 0, 4, 40, null),
    TextMark("d2", "passage:1:1", 1, 12, 58, "Contrast with the earlier claim"),
)

// ---------------------------------------------------------------- sheets

@Composable
private fun NavigatorSheet(
    parts: List<NavPart>, answered: (Int) -> Boolean, flagged: Set<Int>, current: Int, answeredTotal: Int, total: Int,
    canGo: (Int) -> Boolean, onJump: (Int) -> Unit, onSubmit: () -> Unit, onDismiss: () -> Unit,
) {
    val c = NB.colors
    NbSheet(
        onDismiss, title = "Questions",
        subtitle = "$answeredTotal of $total answered" + if (flagged.isNotEmpty()) " · ${flagged.size} flagged" else "",
        actions = { NbButton("Review and submit", onSubmit, kind = BtnKind.Primary, size = BtnSize.Large, fill = true) },
    ) {
        parts.forEach { p ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(p.label, style = NB.type.title3, color = c.ink, modifier = Modifier.semantics { heading() })
                    Text("${p.questions.count(answered)} of ${p.questions.size} answered", style = NB.type.caption, color = c.muted)
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    p.questions.forEach { n -> QButton(n, answered(n), n in flagged, n == current, canGo(n)) { onJump(n) } }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Legend("Answered") { Box(Modifier.size(14.dp).background(c.brand, CircleShape)) }
            Legend("Current") { Box(Modifier.size(14.dp).border(2.dp, c.ink, CircleShape)) }
            Legend("Flagged") { Box(Modifier.size(10.dp).background(c.warn, CircleShape)) }
        }
    }
}

@Composable
private fun Legend(label: String, mark: @Composable () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        mark()
        Text(label, style = NB.type.caption, color = NB.colors.muted)
    }
}

/** A numbered 48 dp circle: filled when answered, ringed when current, a dot when flagged. */
@Composable
private fun QButton(n: Int, answered: Boolean, flagged: Boolean, current: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val c = NB.colors
    val desc = "Question $n, ${if (answered) "answered" else "not answered"}${if (flagged) ", flagged" else ""}${if (current) ", current" else ""}"
    Box(
        Modifier.size(48.dp).semantics { contentDescription = desc }
            .background(if (answered) c.brand else c.surface2, CircleShape)
            .border(if (current) 3.dp else if (answered) 0.dp else 1.dp, if (current) c.ink else c.faint, CircleShape)
            .clip(CircleShape)
            .selectable(current, enabled, Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text("$n", style = NB.type.num, color = if (!enabled) c.faint else if (answered) c.onBrand else c.ink)
        if (flagged) Box(Modifier.align(Alignment.TopEnd).size(14.dp).background(c.warn, CircleShape).border(2.dp, c.surface, CircleShape))
    }
}

@Composable
private fun SettingsSheet(s: LrSettings, onChange: (LrSettings) -> Unit, onDismiss: () -> Unit) {
    val sizes = listOf("std", "lg", "xl")
    val schemes = listOf("std" to "Standard", "bw" to "Black on white", "cream" to "Black on cream", "yb" to "Yellow on black")
    NbSheet(onDismiss, title = "Text size and colours", subtitle = "Applies to the questions and passage, not the timer.") {
        Text("Text size", style = NB.type.title3, color = NB.colors.ink, modifier = Modifier.semantics { heading() })
        Segmented(listOf("Standard", "Large", "Extra large"), sizes.indexOf(s.size).coerceAtLeast(0), { onChange(s.copy(size = sizes[it])) })
        Text("Colours", style = NB.type.title3, color = NB.colors.ink, modifier = Modifier.semantics { heading() })
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            schemes.forEach { (k, label) -> NbChip(label, s.scheme == k, { onChange(s.copy(scheme = k)) }) }
        }
        NbButton("Reset to default", { onChange(LrSettings()) }, kind = BtnKind.Text, enabled = s != LrSettings())
    }
}

@Composable
private fun HelpSheet(listening: Boolean, exam: Boolean, onDismiss: () -> Unit) {
    val lines = buildList {
        if (listening) {
            add(if (exam) "The recording plays once, from the first part to the last, with no pause. Answer as you listen." else "Play, pause and replay each part as often as you like. Use the 5 second buttons to go back a little.")
            add("Questions for the part that is playing are below the player.")
        } else {
            add("Drag the questions sheet up to answer, and down to read the passage. On a tablet both sit side by side.")
            add("Press and hold a word in the passage, then drag to highlight it or add a note. Tap a highlight to open it.")
        }
        add("Tap the flag to mark a question to come back to. Tap the question count to see every question.")
        add("Your answers are saved as you go. Closing the test keeps them.")
        if (exam) add("The clock is for the whole test. There is a warning at 10 and 5 minutes, and the test submits itself at zero.")
    }
    NbSheet(onDismiss, title = "How this works") {
        lines.forEach { Text(it, style = NB.type.body, color = NB.colors.ink) }
    }
}
