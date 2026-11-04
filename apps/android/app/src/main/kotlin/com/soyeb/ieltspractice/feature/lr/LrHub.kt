package com.soyeb.ieltspractice.feature.lr

import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.layout.layout
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Spellcheck
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.soyeb.ieltspractice.LocalDemo
import com.soyeb.ieltspractice.LocalGraph
import com.soyeb.ieltspractice.data.logic.Band
import com.soyeb.ieltspractice.data.logic.LrHubGroup
import com.soyeb.ieltspractice.data.logic.MockFlow
import com.soyeb.ieltspractice.data.logic.formatDate
import com.soyeb.ieltspractice.data.logic.lrHubGroups
import com.soyeb.ieltspractice.data.logic.lrParts
import com.soyeb.ieltspractice.data.logic.parseRef
import com.soyeb.ieltspractice.data.logic.partsLabel
import com.soyeb.ieltspractice.data.logic.readingSeconds
import com.soyeb.ieltspractice.data.model.LrAttemptItem
import com.soyeb.ieltspractice.data.model.LrTestDetail
import com.soyeb.ieltspractice.data.model.LrTestItem
import com.soyeb.ieltspractice.data.model.Mock
import com.soyeb.ieltspractice.data.net.Api
import com.soyeb.ieltspractice.data.model.LrAttempt
import com.soyeb.ieltspractice.design.NB
import com.soyeb.ieltspractice.design.Skill
import com.soyeb.ieltspractice.design.components.Meter
import com.soyeb.ieltspractice.design.components.Avatar
import com.soyeb.ieltspractice.design.components.BandPill
import com.soyeb.ieltspractice.design.components.Banner
import com.soyeb.ieltspractice.design.components.BtnKind
import com.soyeb.ieltspractice.design.components.BtnSize
import com.soyeb.ieltspractice.design.components.CardTone
import com.soyeb.ieltspractice.design.components.ConfirmDialog
import com.soyeb.ieltspractice.design.components.EmptyState
import com.soyeb.ieltspractice.design.components.ErrorState
import com.soyeb.ieltspractice.design.components.IconTile
import com.soyeb.ieltspractice.design.components.Load
import com.soyeb.ieltspractice.design.components.LocalSnack
import com.soyeb.ieltspractice.design.components.MockStrip
import com.soyeb.ieltspractice.design.components.NbButton
import com.soyeb.ieltspractice.design.components.NbCard
import com.soyeb.ieltspractice.design.components.NbListRow
import com.soyeb.ieltspractice.design.components.NbSheet
import com.soyeb.ieltspractice.design.components.Overline
import com.soyeb.ieltspractice.design.components.PushedList
import com.soyeb.ieltspractice.design.components.PushedScreen
import com.soyeb.ieltspractice.design.components.RowDivider
import com.soyeb.ieltspractice.design.components.RowGroup
import com.soyeb.ieltspractice.design.components.SectionHeader
import com.soyeb.ieltspractice.design.components.Segmented
import com.soyeb.ieltspractice.design.components.Skeleton
import com.soyeb.ieltspractice.design.components.SkillTile
import com.soyeb.ieltspractice.design.components.StickyActionBar
import com.soyeb.ieltspractice.design.components.Stat
import com.soyeb.ieltspractice.design.components.TabScreen
import com.soyeb.ieltspractice.design.components.Tag
import com.soyeb.ieltspractice.design.components.VDivider
import com.soyeb.ieltspractice.design.components.clockText
import com.soyeb.ieltspractice.design.components.rememberLoad
import com.soyeb.ieltspractice.design.fmtBand
import com.soyeb.ieltspractice.design.soft
import com.soyeb.ieltspractice.design.color
import com.soyeb.ieltspractice.nav.LrRun
import com.soyeb.ieltspractice.nav.MockHub
import com.soyeb.ieltspractice.nav.MockStart
import com.soyeb.ieltspractice.nav.Nav
import com.soyeb.ieltspractice.nav.Spelling
import com.soyeb.ieltspractice.nav.You
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

// The Listening and Reading tab roots, the test sheet (exam or practice, which parts) and the pushed version of it for deep links.

private class HubData(val tests: List<LrTestItem>, val attempts: List<LrAttemptItem>, val mock: Mock?)

/** Lets a row of cards run to the screen edges inside a gutter-padded list. */
private fun Modifier.bleed(h: Dp): Modifier = layout { measurable, constraints ->
    val extra = h.roundToPx() * 2
    val w = constraints.maxWidth + extra
    val p = measurable.measure(constraints.copy(minWidth = w, maxWidth = w))
    layout(constraints.maxWidth, p.height) { p.place(-h.roundToPx(), 0) }
}

@Composable
fun LrHubScreen(skill: Skill, nav: Nav) {
    val graph = LocalGraph.current
    val api = graph.api
    val demo = LocalDemo.current
    val scope = rememberCoroutineScope()
    val snack = LocalSnack.current
    val listening = skill == Skill.Listening
    val testType by graph.prefs.testType.collectAsState(initial = "academic")
    val me by api.me.collectAsState()
    val load = rememberLoad(skill) {
        val tests = api.lrTests(skill = skill.key)
        val attempts = runCatching { api.lrAttempts() }.getOrDefault(emptyList())
        val mock = runCatching { api.mockCurrent() }.getOrNull()
        HubData(tests, attempts, mock)
    }
    ReloadOnPlanChange(me?.tier, me?.subscribedUntil, load.reload)
    val data = load.value
    var sheet by remember { mutableStateOf<LrTestItem?>(null) }
    var restart by remember { mutableStateOf<LrTestItem?>(null) }
    var starting by remember { mutableStateOf(false) }

    val visible = remember(data, testType) { data?.tests.orEmpty().filter { listening || it.variant == testType } }
    val resumeItem = visible.firstOrNull { it.status == "in_progress" && it.attemptId != null }
    val resumed = rememberLoad(resumeItem?.attemptId) { resumeItem?.attemptId?.let { runCatching { api.lrAttempt(it) }.getOrNull() } }

    // Screenshots: open the test sheet over the hub.
    LaunchedEffect(data, demo?.screen) {
        if (demo?.screen?.endsWith("-sheet") == true && sheet == null) sheet = visible.firstOrNull { it.status == "new" } ?: visible.firstOrNull()
    }

    TabScreen(
        title = skill.label,
        actions = { com.soyeb.ieltspractice.feature.updates.UpdatesBell(nav); Avatar(me?.user?.takeIf { !it.isAnonymous }?.name, onClick = { nav.go(You) }) },
        refreshing = load.refreshing, onRefresh = load.reload,
    ) {
        item {
            val mock = data?.mock?.takeIf { it.open }
            MockStrip(
                onClick = { if (mock != null) nav.go(MockHub(mock.id)) else nav.go(MockStart) },
                resumeText = mock?.let { m -> m.next?.let { "${MockFlow.name(it)} next" } ?: "Open" },
            )
        }
        if (!listening) item {
            Segmented(
                listOf("Academic", "General Training"), if (testType == "general") 1 else 0,
                { i -> scope.launch { graph.prefs.setTestType(if (i == 1) "general" else "academic") } }, role = androidx.compose.ui.semantics.Role.RadioButton,
            )
        }
        when (val st = load.state) {
            Load.Loading -> {
                item { Skeleton(Modifier.fillMaxWidth(), height = 150.dp, corner = 20.dp) }
                item { Skeleton(Modifier.fillMaxWidth(), height = 72.dp, corner = 20.dp) }
                item { Skeleton(Modifier.fillMaxWidth(), height = 200.dp, corner = 20.dp) }
            }
            is Load.Failed -> item { ErrorState(st.message, load.reload) }
            is Load.Ready -> {
                val d = st.value
                if (resumeItem != null) item {
                    ContinueHero(resumeItem, resumed.value, skill, onContinue = { nav.go(LrRun(resumeItem.attemptId.orEmpty())) }, onRestart = { restart = resumeItem })
                }
                item { StatsStrip(d.attempts.filter { it.skill == skill.key }) }
                if (listening) item {
                    RowGroup {
                        NbListRow(
                            "Spelling drill", subtitle = "Hear and spell the words you got wrong",
                            leading = { IconTile(Icons.Rounded.Spellcheck, NB.colors.listening, NB.colors.listeningSoft, 40.dp) },
                            onClick = { nav.go(Spelling) },
                        )
                    }
                }
                if (visible.isEmpty()) item {
                    EmptyState(Icons.AutoMirrored.Rounded.MenuBook, "No tests here yet", "Tests for this kind of ${skill.label.lowercase()} are on the way. Pull down to refresh.")
                } else {
                    val groups = lrHubGroups(visible)
                    val books = groups.filter { it.key.startsWith("c") }
                    val own = groups.firstOrNull { it.key == "own" }
                    if (books.isNotEmpty()) {
                        item { SectionHeader("Cambridge books", Modifier.padding(top = NB.space.xs)) }
                        item { BookRow(books, skill) { sheet = it } }
                    }
                    if (own != null) {
                        item { SectionHeader("NextBand originals", Modifier.padding(top = NB.space.xs)) }
                        item { OriginalRows(own.tests, d.attempts, skill) { sheet = it } }
                    }
                }
            }
        }
    }

    sheet?.let { TestSheet(it, nav) { sheet = null } }
    restart?.let { item ->
        ConfirmDialog(
            title = "Start over?", body = "Your ${item.answered} answers in this attempt will be discarded and a new one starts.",
            confirm = "Start over", destructive = true,
            onConfirm = {
                restart = null
                if (!starting) {
                    starting = true
                    scope.launch {
                        try {
                            val a = api.lrStart(item.id, item.mode ?: "practice", item.parts, fresh = true)
                            nav.go(LrRun(a.id))
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            snack.showSnackbar(e.message ?: "Couldn't start. Try again.")
                        } finally { starting = false }
                    }
                }
            },
            onDismiss = { restart = null },
        )
    }
}

@Composable
private fun ContinueHero(item: LrTestItem, attempt: LrAttempt?, skill: Skill, onContinue: () -> Unit, onRestart: () -> Unit) {
    val c = NB.colors
    val total = item.total.coerceAtLeast(1)
    val used = attempt?.elapsedS?.takeIf { it > 0 }?.let { " · ${clockText(it.toLong())} used" }.orEmpty()
    NbCard(tone = CardTone.Hero) {
        Overline("Continue", color = c.brandText)
        Text(item.title, style = NB.type.title1, color = c.ink, modifier = Modifier.padding(top = 4.dp))
        Text(
            "${if (item.mode == "exam") "Exam" else "Practice"} · ${partsLabel(item.skill, item.parts)}$used",
            style = NB.type.bodySmall, color = c.muted, modifier = Modifier.padding(top = 2.dp),
        )
        Meter(item.answered.toDouble(), Modifier.padding(top = 10.dp), max = total.toDouble(), height = 6.dp)
        Text("${item.answered} of $total answered", style = NB.type.caption, color = c.muted, modifier = Modifier.padding(top = 6.dp))
        Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NbButton("Continue", onContinue, modifier = Modifier.weight(1f), size = BtnSize.Large, haptic = true)
            NbButton("Restart", onRestart, kind = BtnKind.Secondary, size = BtnSize.Large)
        }
    }
}

/** Tests taken, average band and best (full tests only: a single part has no band). */
@Composable
private fun StatsStrip(attempts: List<LrAttemptItem>) {
    val done = attempts.filter { it.status == "submitted" }
    val bands = done.filter { it.parts == null }.mapNotNull { it.band }
    NbCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            Stat("${done.size}", "Taken")
            VDivider()
            Stat(if (bands.isEmpty()) "–" else fmtBand(Band.round(bands.average())), "Average band")
            VDivider()
            Stat(fmtBand(bands.maxOrNull()), "Best band")
        }
    }
}

/** One card per Cambridge book: its tests as tiles, each with its best band. */
@Composable
private fun BookRow(books: List<LrHubGroup>, skill: Skill, onOpen: (LrTestItem) -> Unit) {
    Row(
        Modifier.bleed(NB.space.gutter).horizontalScroll(rememberScrollState()).padding(horizontal = NB.space.gutter),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        books.forEach { b ->
            val best = b.tests.mapNotNull { it.bestBand }.maxOrNull()
            NbCard(Modifier.width(236.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(b.heading.replace("Cambridge IELTS", "Cambridge"), style = NB.type.title2, color = NB.colors.ink, modifier = Modifier.semantics { heading() })
                        Text("${b.tests.size} tests", style = NB.type.caption, color = NB.colors.muted)
                    }
                    if (best != null) BandPill(best)
                }
                Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    b.tests.chunked(2).forEach { pair ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            pair.forEach { t -> TestTile(t, skill, Modifier.weight(1f)) { onOpen(t) } }
                            if (pair.size == 1) Box(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TestTile(t: LrTestItem, skill: Skill, modifier: Modifier, onClick: () -> Unit) {
    val c = NB.colors
    val label = "Test ${parseRef(t.ref)?.second ?: t.ref}"
    val state = when { t.status == "in_progress" -> "in progress"; t.bestBand != null -> "best band ${fmtBand(t.bestBand)}"; else -> "not taken" }
    Surface(
        onClick = onClick, shape = NB.shape.tile, color = c.surface2, contentColor = c.ink,
        modifier = modifier.heightIn(min = 48.dp).semantics { contentDescription = "$label, $state" },
    ) {
        Row(Modifier.heightIn(min = 48.dp).padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = NB.type.label, color = c.ink, modifier = Modifier.weight(1f), maxLines = 1)
            when {
                t.status == "in_progress" -> Box(Modifier.size(10.dp).background(skill.color, CircleShape))
                t.bestBand != null -> BandPill(t.bestBand)
            }
        }
    }
}

@Composable
private fun OriginalRows(tests: List<LrTestItem>, attempts: List<LrAttemptItem>, skill: Skill, onOpen: (LrTestItem) -> Unit) {
    RowGroup {
        tests.forEachIndexed { i, t ->
            if (i > 0) RowDivider()
            val last = attempts.filter { it.testId == t.id }.mapNotNull { it.submittedAt }.maxOrNull()?.let { formatDate(it) }?.takeIf { it.isNotEmpty() }
            val parts = if (skill == Skill.Listening) "4 parts" else "3 passages"
            NbListRow(
                t.title.removePrefix("Original practice: "),
                subtitle = listOfNotNull(parts, if (t.status == "in_progress") "${t.answered} of ${t.total} answered" else null, last?.let { "Taken $it" }).joinToString(" · "),
                leading = { SkillTile(skill, 40.dp) },
                trailing = {
                    when {
                        t.bestBand != null -> BandPill(t.bestBand)
                        t.status == "in_progress" -> Tag("In progress", fg = NB.colors.brandText, bg = NB.colors.brandSoft)
                        else -> Tag("New")
                    }
                },
                onClick = { onOpen(t) },
            )
        }
    }
}

// ---------------------------------------------------------------- the test sheet

/** What the learner chose for a test: exam or practice, and which parts. */
@Stable
private class StartPlan(val item: LrTestItem) {
    val allParts = lrParts(item.skill)
    val resumable = item.status == "in_progress" && item.attemptId != null
    var exam by mutableStateOf(resumable && item.mode == "exam")
    var picked by mutableStateOf(allParts.toSet())
    var busy by mutableStateOf(false)

    /** Null = the whole test. */
    val parts: List<Int>? get() = picked.sorted().takeIf { it.size < allParts.size }
}

@Composable
private fun TestSheet(item: LrTestItem, nav: Nav, onDismiss: () -> Unit) {
    val plan = remember(item.id) { StartPlan(item) }
    NbSheet(
        onDismiss, title = item.title.removePrefix("Original practice: "),
        subtitle = if (item.skill == "listening") "Listening · 40 questions · about 30 minutes" else "Reading · 40 questions · 60 minutes",
        actions = { StartButtons(plan, nav, onDismiss) },
    ) { TestPlanBody(plan) }
}

/** The same content as a pushed screen (deep links from Today and History). */
@Composable
fun LrTestScreen(testId: String, skill: String, nav: Nav) {
    val api = LocalGraph.current.api
    val load = rememberLoad(testId) { api.lrTests(skill = skill).firstOrNull { it.id == testId } }
    val me by api.me.collectAsState()
    ReloadOnPlanChange(me?.tier, me?.subscribedUntil, load.reload)
    val found = load.value
    val plan = remember(found?.id) { found?.let { StartPlan(it) } }
    PushedScreen(
        title = found?.title?.removePrefix("Original practice: ") ?: if (skill == "listening") "Listening test" else "Reading test",
        onBack = nav::back,
        bottomBar = { if (plan != null) StickyActionBar { StartButtons(plan, nav) {} } },
    ) { pad ->
        when (val s = load.state) {
            Load.Loading -> PushedList(pad) { item { Skeleton(Modifier.fillMaxWidth(), height = 240.dp, corner = 20.dp) } }
            is Load.Failed -> ErrorState(s.message, load.reload, Modifier.padding(pad).verticalScroll(rememberScrollState()))
            is Load.Ready -> {
                if (plan == null) EmptyState(Icons.AutoMirrored.Rounded.MenuBook, "Test not found", "It may have been removed. Go back and pick another.", Modifier.padding(pad).verticalScroll(rememberScrollState()))
                else PushedList(pad) { item { TestPlanBody(plan) } }
            }
        }
    }
}

@Composable
private fun TestPlanBody(plan: StartPlan) {
    val c = NB.colors
    val item = plan.item
    val api = LocalGraph.current.api
    val listening = item.skill == "listening"
    val detail by produceState<LrTestDetail?>(null, item.id) { value = runCatching { api.lrTest(item.id) }.getOrNull() }
    val noun = if (listening) "Part" else "Passage"
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Best band", style = NB.type.bodySmall, color = c.muted)
            if (item.bestBand != null) BandPill(item.bestBand) else Text("Not taken yet", style = NB.type.bodySmall, color = c.muted)
            if (item.attempts > 0) Text("· taken ${item.attempts} ${if (item.attempts == 1) "time" else "times"}", style = NB.type.bodySmall, color = c.muted)
        }
        if (plan.resumable) Banner("Unfinished attempt: ${item.answered} of ${item.total} answered.", Icons.AutoMirrored.Rounded.MenuBook)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("${noun}s", style = NB.type.title3, color = c.ink, modifier = Modifier.semantics { heading() })
            Text("Pick one or more to practise on their own. A single ${noun.lowercase()} has no band.", style = NB.type.caption, color = c.muted)
        }
        Column {
            plan.allParts.forEach { p ->
                val on = p in plan.picked
                val section = detail?.test?.sections?.firstOrNull { it.part == p }
                val sub = if (listening) section?.title?.takeIf { it != "$noun $p" } else section?.passage?.title
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 52.dp).toggleable(on, role = Role.Checkbox) { v ->
                        plan.picked = if (v) plan.picked + p else if (plan.picked.size > 1) plan.picked - p else plan.picked
                    },
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Checkbox(on, null, colors = CheckboxDefaults.colors(checkedColor = c.brand, uncheckedColor = c.faint, checkmarkColor = c.onBrand))
                    Column(Modifier.weight(1f)) {
                        Text("$noun $p", style = NB.type.title3, color = c.ink)
                        if (sub != null) Text(sub, style = NB.type.caption, color = c.muted, maxLines = 1)
                    }
                    section?.groups?.sumOf { it.questions.size }?.let { Text("$it questions", style = NB.type.caption, color = c.muted) }
                }
            }
        }
        Text("Mode", style = NB.type.title3, color = c.ink, modifier = Modifier.semantics { heading() })
        Segmented(listOf("Exam", "Practice"), if (plan.exam) 0 else 1, { plan.exam = it == 0 }, role = androidx.compose.ui.semantics.Role.RadioButton)
        Text(modeNote(listening, plan.exam, plan.parts), style = NB.type.bodySmall, color = c.muted)
    }
}

private fun modeNote(listening: Boolean, exam: Boolean, parts: List<Int>?): String = when {
    listening && exam -> "Timed like the real test. The recording plays once with no pause, then you get checking time."
    listening -> "No clock. Pause, rewind and replay each part as often as you like."
    exam -> "${readingSeconds(parts) / 60} minutes, one go. Answers are marked when you submit."
    else -> "No clock. Take as long as you need."
}

@Composable
private fun ColumnScope.StartButtons(plan: StartPlan, nav: Nav, after: () -> Unit) {
    val api = LocalGraph.current.api
    val scope = rememberCoroutineScope()
    val snack = LocalSnack.current
    fun go(fresh: Boolean) {
        if (plan.busy) return
        plan.busy = true
        scope.launch {
            try {
                api.ensureSession()
                val a = api.lrStart(plan.item.id, if (plan.exam) "exam" else "practice", plan.parts, fresh)
                after()
                nav.go(LrRun(a.id))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                snack.showSnackbar(e.message?.takeIf { it.isNotBlank() } ?: "Couldn't start the test. Try again.")
            } finally {
                plan.busy = false
            }
        }
    }
    if (plan.resumable) {
        NbButton("Continue", { after(); nav.go(LrRun(plan.item.attemptId.orEmpty())) }, size = BtnSize.Large, fill = true, haptic = true)
        NbButton("Start a new attempt", { go(true) }, kind = BtnKind.Secondary, fill = true, loading = plan.busy)
    } else NbButton("Start", { go(false) }, size = BtnSize.Large, fill = true, loading = plan.busy, haptic = true)
}

/**
 * Pro bought on nextband.app while a list is open: the foreground /api/me refresh changes the plan, and the Pro-only tests
 * appear without leaving the screen. Refreshes quietly; the first /api/me after launch (no plan seen yet) doesn't count.
 */
@Composable
private fun ReloadOnPlanChange(tier: String?, until: String?, reload: () -> Unit) {
    val plan = tier?.let { it to until }
    var seen by remember { mutableStateOf(plan) }
    LaunchedEffect(plan) {
        if (plan != seen) {
            if (seen != null) reload()
            seen = plan
        }
    }
}
