package com.soyeb.ieltspractice.feature.today

import com.soyeb.ieltspractice.design.components.HeroInk
import com.soyeb.ieltspractice.design.components.BrandHero
import com.soyeb.ieltspractice.design.rise
import com.soyeb.ieltspractice.design.sharedBox
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.History as HistoryIcon
import androidx.compose.material.icons.rounded.Style
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.soyeb.ieltspractice.LocalDemo
import com.soyeb.ieltspractice.LocalGraph
import com.soyeb.ieltspractice.data.logic.MockFlow
import com.soyeb.ieltspractice.data.logic.fmt
import com.soyeb.ieltspractice.data.model.Coach
import com.soyeb.ieltspractice.data.model.LrProgress
import com.soyeb.ieltspractice.data.model.Me
import com.soyeb.ieltspractice.data.model.Mock
import com.soyeb.ieltspractice.data.model.ProgressData
import com.soyeb.ieltspractice.data.net.Api
import com.soyeb.ieltspractice.design.NB
import com.soyeb.ieltspractice.design.Skill
import com.soyeb.ieltspractice.design.bandTone
import com.soyeb.ieltspractice.design.components.Avatar
import com.soyeb.ieltspractice.design.components.BandPill
import com.soyeb.ieltspractice.design.components.BandRing
import com.soyeb.ieltspractice.design.components.BtnKind
import com.soyeb.ieltspractice.design.components.CardTone
import com.soyeb.ieltspractice.design.components.ErrorState
import com.soyeb.ieltspractice.design.components.IconTile
import com.soyeb.ieltspractice.design.components.Meter
import com.soyeb.ieltspractice.design.components.Sparkline
import com.soyeb.ieltspractice.design.components.NbButton
import com.soyeb.ieltspractice.design.components.NbCard
import com.soyeb.ieltspractice.design.components.NbListRow
import com.soyeb.ieltspractice.design.components.RowDivider
import com.soyeb.ieltspractice.design.components.RowGroup
import com.soyeb.ieltspractice.design.components.SectionHeader
import com.soyeb.ieltspractice.design.components.Skeleton
import com.soyeb.ieltspractice.design.components.SkillDots
import com.soyeb.ieltspractice.design.components.SkillTile
import com.soyeb.ieltspractice.design.components.TabScreen
import com.soyeb.ieltspractice.design.components.Tag
import com.soyeb.ieltspractice.design.components.rememberLoad
import com.soyeb.ieltspractice.design.fill
import com.soyeb.ieltspractice.design.fg
import com.soyeb.ieltspractice.design.fmtBand
import com.soyeb.ieltspractice.feature.history.HistoryItem
import com.soyeb.ieltspractice.feature.history.ResultRow
import com.soyeb.ieltspractice.feature.history.mergeItems
import com.soyeb.ieltspractice.feature.history.openItem
import com.soyeb.ieltspractice.feature.history.skillTab
import com.soyeb.ieltspractice.feature.history.toItem
import com.soyeb.ieltspractice.nav.AttemptResult
import com.soyeb.ieltspractice.nav.Auth
import com.soyeb.ieltspractice.nav.CoachRoute
import com.soyeb.ieltspractice.nav.History
import com.soyeb.ieltspractice.nav.LrTestRoute
import com.soyeb.ieltspractice.nav.Mistakes
import com.soyeb.ieltspractice.nav.MockHub
import com.soyeb.ieltspractice.nav.MockResult
import com.soyeb.ieltspractice.nav.MockStart
import com.soyeb.ieltspractice.nav.Nav
import com.soyeb.ieltspractice.nav.Onboarding
import com.soyeb.ieltspractice.nav.Review
import com.soyeb.ieltspractice.nav.SpeakingSession
import com.soyeb.ieltspractice.nav.WritingTask
import com.soyeb.ieltspractice.nav.You
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/** Everything Today shows for an account, loaded in parallel. Only progress is required; the rest degrades to "nothing yet". */
private class TodayData(
    val progress: ProgressData,
    val lr: LrProgress?,
    val due: Int,
    val mock: Mock?,
    val lastMock: Mock?,
    val coach: Coach?,
    val recent: List<HistoryItem>,
)

private suspend fun loadToday(api: Api): TodayData = coroutineScope {
    val progress = async { api.progress() }
    val lr = async { runCatching { api.lrProgress() }.getOrNull() }
    val due = async { runCatching { api.dueCards().dueTotal }.getOrDefault(0) }
    val mock = async { runCatching { api.mockCurrent() }.getOrNull()?.takeIf { it.open } }
    val last = async { runCatching { api.mockList().firstOrNull { it.status == "completed" && it.overall != null } }.getOrNull() }
    val coach = async { runCatching { api.coach() }.getOrNull() }
    val recent = async { loadRecent(api) }
    TodayData(progress.await(), lr.await(), due.await(), mock.await(), last.await(), coach.await(), recent.await())
}

/** The newest few results of any skill (a guest sees the same list, the server caps it). */
private suspend fun loadRecent(api: Api, count: Int = 3): List<HistoryItem> = coroutineScope {
    val sp = async { runCatching { api.attempts(null, 1).items.map { it.toItem() } }.getOrDefault(emptyList()) }
    val lr = async { runCatching { api.lrAttempts().map { it.toItem() } }.getOrDefault(emptyList()) }
    mergeItems(sp.await(), lr.await()).take(count)
}

@Composable
fun TodayRoute(nav: Nav) {
    val api = LocalGraph.current.api
    val hasAccount by api.hasAccount.collectAsState()
    if (hasAccount) TodayScreen(nav) else GuestToday(nav)
}

private fun Modifier.sectionGap() = padding(top = 12.dp)

@Composable
private fun Section(title: String, action: String? = null, onAction: (() -> Unit)? = null, content: @Composable () -> Unit) {
    Column(Modifier.sectionGap(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader(title, action = action, onAction = onAction)
        content()
    }
}

// MARK: Account

@Composable
private fun TodayScreen(nav: Nav) {
    val graph = LocalGraph.current
    val api = graph.api
    val me by api.me.collectAsState()
    val loader = rememberLoad { loadToday(api) }
    val onboarded by graph.prefs.onboardingDone.collectAsState(initial = true)
    val testType by graph.prefs.testType.collectAsState(initial = "academic")
    val demo = LocalDemo.current
    // Demo screenshots would all carry the nudge (fresh prefs); only the first-run row shows it.
    val nudge = !onboarded && (demo == null || demo.screen == "today-new")
    val target = me?.settings?.targetBand ?: 7.0
    val data = loader.value
    val listState = rememberLazyListState()

    LaunchedEffect(data != null) {
        if (demo?.screen == "today-lower" && data != null) {
            withFrameNanos { }
            listState.scrollBy(100_000f)
        }
    }

    TabScreen(
        title = greeting(hourNow(), me?.user?.name),
        subtitle = data?.let { subtitle(it.progress.streak, it.progress.minutesThisWeek) },
        actions = { com.soyeb.ieltspractice.feature.updates.UpdatesBell(nav); Avatar(me?.user?.name, onClick = { nav.go(You) }) },
        refreshing = loader.refreshing, onRefresh = loader.reload, listState = listState,
    ) {
        when (val s = loader.state) {
            is com.soyeb.ieltspractice.design.components.Load.Loading -> item { TodaySkeleton() }
            is com.soyeb.ieltspractice.design.components.Load.Failed -> item { ErrorState(s.message, loader.reload) }
            is com.soyeb.ieltspractice.design.components.Load.Ready -> readyItems(nav, s.value, me, target, testType, nudge)
        }
    }
}

@Composable
private fun TodaySkeleton() {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Skeleton(Modifier.fillMaxWidth(), height = 200.dp, corner = 20.dp)
        Skeleton(Modifier.fillMaxWidth(), height = 220.dp, corner = 20.dp)
        Skeleton(Modifier.fillMaxWidth(), height = 120.dp, corner = 20.dp)
        Skeleton(Modifier.fillMaxWidth(), height = 120.dp, corner = 20.dp)
    }
}

private fun LazyListScope.readyItems(nav: Nav, d: TodayData, me: Me?, target: Double, testType: String, nudge: Boolean) {
    val days = daysToExam(me?.settings?.examDate)
    val bands = skillBands(d.progress, d.lr)
    val next = pickUpNext(d.coach, d.progress, d.lr, target)

    if (days != null) item { Box(Modifier.rise(0)) { ExamChip(days) } }
    if (nudge) item { Box(Modifier.rise(0)) { GoalNudge(nav) } }
    item { Box(Modifier.rise(1)) { MockHero(nav, d.mock, d.lastMock, target) } }
    item { Box(Modifier.rise(2)) { Section("Your bands") { BandsCard(nav, bands, target) } } }
    item { Box(Modifier.rise(3)) { Section("Up next") { UpNextCard(nav, next, testType) } } }
    item { Box(Modifier.rise(4)) { Section("Study tools") { ToolsGrid(nav, d.due) } } }
    item {
        Section("Recent results", action = if (d.recent.isNotEmpty()) "See all" else null, onAction = { nav.go(History()) }) {
            if (d.recent.isEmpty()) NbCard(tone = CardTone.Inset) {
                Text("No results yet", style = NB.type.title3, color = NB.colors.ink)
                Text("Take a test and its result shows up here.", style = NB.type.bodySmall, color = NB.colors.muted)
            } else RowGroup {
                d.recent.forEachIndexed { i, r ->
                    if (i > 0) RowDivider()
                    ResultRow(r, target) { openItem(nav, r) }
                }
            }
        }
    }
}

@Composable
private fun ExamChip(days: Int) {
    Row(
        Modifier.clip(NB.shape.pill).background(NB.colors.brandSoft).padding(horizontal = 14.dp, vertical = 8.dp).semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icons.Rounded.Event, null, Modifier.size(18.dp), tint = NB.colors.brandText)
        Text(examChipText(days), style = NB.type.label, color = NB.colors.brandText)
    }
}

@Composable
private fun GoalNudge(nav: Nav) {
    RowGroup {
        NbListRow(
            "Set your goal", subtitle = "Pick a target band and an exam date so your bands show how close you are.",
            leading = { IconTile(Icons.Rounded.Flag, NB.colors.brandText, NB.colors.brandSoft, 40.dp) },
            onClick = { nav.go(Onboarding) },
        )
    }
}

// MARK: Full mock hero

@Composable
private fun MockHero(nav: Nav, mock: Mock?, last: Mock?, target: Double) {
    BrandHero(Modifier.sharedBox(com.soyeb.ieltspractice.feature.mock.MOCK_HERO, NB.shape.card)) { ink ->
        Text(if (mock != null) "Full mock in progress" else "Full mock test", style = NB.type.label, color = ink.muted)
        Spacer(Modifier.height(6.dp))
        Text(
            if (mock != null) "Pick up where you left off" else "See your real band in one sitting",
            style = NB.type.headline.copy(fontSize = 26.sp, lineHeight = 32.sp), color = ink.fg, modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(6.dp))
        Text(
            if (mock != null) MockFlow.continueLine(mock) else "Real timing, all four tests and an overall band.",
            style = NB.type.bodySmall, color = ink.muted,
        )
        Spacer(Modifier.height(18.dp))
        if (mock != null) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                MockFlow.skills.forEach { key -> MiniStep(mock, key, target, ink) }
            }
            Spacer(Modifier.height(18.dp))
            NbButton("Resume", { nav.go(MockHub(mock.id)) }, kind = BtnKind.Inverse, fill = true, size = com.soyeb.ieltspractice.design.components.BtnSize.Large, haptic = true)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Rounded.WorkspacePremium, null, Modifier.size(18.dp), tint = ink.fg)
                Text("4 tests · 2 h 45 m", style = NB.type.label.copy(fontFeatureSettings = "tnum"), color = ink.fg)
            }
            Spacer(Modifier.height(18.dp))
            NbButton("Start full mock", { nav.go(MockStart) }, kind = BtnKind.Inverse, fill = true, size = com.soyeb.ieltspractice.design.components.BtnSize.Large, haptic = true)
            if (last?.overall != null) Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(top = 6.dp).clip(NB.shape.tile)
                    .clickable(onClickLabel = "Open your last mock result") { nav.go(MockResult(last.id)) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Last mock", style = NB.type.bodySmall, color = ink.muted, modifier = Modifier.weight(1f))
                BandPill(last.overall, target)
                Spacer(Modifier.width(4.dp))
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = ink.muted)
            }
        }
    }
}

/** One section of the open mock: a dot (done / next / later), its name, and its band or status. */
@Composable
private fun MiniStep(mock: Mock, key: String, target: Double, ink: HeroInk) {
    val c = NB.colors
    val sec = mock.section(key)
    val done = sec != null && MockFlow.isFinished(sec.state)
    val current = !done && mock.next == key
    Row(Modifier.fillMaxWidth().heightIn(min = 36.dp).semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(22.dp).background(if (done) ink.fg else Color.Transparent, CircleShape)
                .border(2.dp, if (done || current) ink.fg else ink.muted.copy(alpha = 0.5f), CircleShape),
            contentAlignment = Alignment.Center,
        ) { if (done) Icon(Icons.Rounded.Check, null, Modifier.size(14.dp), tint = if (c.isDark) c.brandSoft else c.brandText) }
        Spacer(Modifier.width(12.dp))
        Text(MockFlow.name(key), style = NB.type.title3, color = if (done || current) ink.fg else ink.muted, modifier = Modifier.weight(1f))
        when {
            done && sec?.band != null -> BandPill(sec.band, target)
            done -> Text(MockFlow.stateLabel(key, sec?.state.orEmpty()).first, style = NB.type.caption, color = ink.muted)
            current -> Tag("Next", fg = if (c.isDark) c.brandSoft else c.brandText, bg = ink.fg)
        }
    }
}

// MARK: Bands

@Composable
private fun BandsCard(nav: Nav, bands: SkillBands, target: Double) {
    val c = NB.colors
    val overall = bands.overall
    NbCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BandRing(overall, target, size = 96.dp, stroke = 9.dp, label = "Overall")
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Overall estimate", style = NB.type.title3, color = c.ink)
                if (overall == null) Text("Take two skills to see an overall band.", style = NB.type.bodySmall, color = c.muted)
                else {
                    Text(
                        if (overall >= target) "At or above your target" else "${fmt(target - overall)} below your target",
                        style = NB.type.bodySmall, color = bandTone(overall, target).fg(),
                    )
                    Text("From ${bands.count} of 4 skills", style = NB.type.caption, color = c.muted)
                }
                Text("Target ${fmtBand(target)}", style = NB.type.caption.copy(fontFeatureSettings = "tnum"), color = c.muted)
            }
        }
        Spacer(Modifier.height(16.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Skill.entries.chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    pair.forEach { s -> BandCell(s, bands.of(s), bands.series[s].orEmpty(), target, Modifier.weight(1f)) { nav.openTab(skillTab(s)) } }
                }
            }
        }
    }
}

@Composable
private fun BandCell(skill: Skill, band: Double?, series: List<Double>, target: Double, modifier: Modifier, onClick: () -> Unit) {
    val tone = bandTone(band, target)
    NbCard(modifier, tone = CardTone.Inset, onClick = onClick, padding = PaddingValues(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SkillTile(skill, 32.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(skill.label, style = NB.type.caption, color = NB.colors.muted, maxLines = 1)
                if (band != null) Text(fmtBand(band), style = NB.type.title2.copy(fontFeatureSettings = "tnum"), color = tone.fg())
                else Text("Take a test", style = NB.type.label, color = NB.colors.brandText)
            }
            Sparkline(
                series, target, tone.fill(), "${skill.label}: last ${series.size} bands, latest ${series.lastOrNull()?.let(::fmtBand).orEmpty()}",
                Modifier.size(width = 44.dp, height = 28.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        Meter(band, target = target, color = tone.fill(), height = 4.dp)
    }
}

// MARK: Up next

private fun runAct(nav: Nav, act: NextAct, testType: String) {
    when (act) {
        is NextAct.WriteTask -> nav.startTest("writing") {
            nav.go(if (act.task == 1) WritingTask("task1", variant = testType) else WritingTask("task2"))
        }
        is NextAct.SpeakPart -> nav.startTest("speaking") { nav.go(SpeakingSession("part${act.part}")) }
        NextAct.SpeakFull -> nav.startTest("speaking") { nav.go(SpeakingSession("full")) }
        is NextAct.OpenTab -> nav.openTab(skillTab(act.skill))
        is NextAct.LrTest -> nav.go(LrTestRoute(act.id, act.skill))
        is NextAct.Result -> nav.go(AttemptResult.of(act.id))
        NextAct.ReviewDeck -> nav.requireAccount("Create an account to build and review your deck.") { nav.go(Review) }
        NextAct.OpenCoach -> nav.requireAccount("Create an account to get your coach report.") { nav.go(CoachRoute) }
    }
}

@Composable
private fun UpNextCard(nav: Nav, next: UpNext, testType: String) {
    NbCard {
        Row(verticalAlignment = Alignment.Top) {
            if (next.skill != null) {
                SkillTile(next.skill, 40.dp)
                Spacer(Modifier.width(14.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (next.fromCoach) Tag("From your coach", fg = NB.colors.brandText, bg = NB.colors.brandSoft)
                Text(next.title, style = NB.type.title2, color = NB.colors.ink)
                Text(next.body, style = NB.type.bodySmall, color = NB.colors.muted)
            }
        }
        Spacer(Modifier.height(16.dp))
        NbButton(next.cta, { runAct(nav, next.act, testType) }, fill = true, loading = nav.checking, haptic = true)
    }
}

// MARK: Study tools

private class Tool(val title: String, val sub: String, val icon: ImageVector, val badge: String?, val onClick: () -> Unit)

@Composable
private fun ToolsGrid(nav: Nav, due: Int) {
    val tools = listOf(
        Tool("Review", if (due > 0) "$due cards due" else "All caught up", Icons.Rounded.Style, if (due > 0) "$due" else null) {
            nav.requireAccount("Create an account to build and review your deck.") { nav.go(Review) }
        },
        Tool("Mistakes", "From your results", Icons.Rounded.ErrorOutline, null) {
            nav.requireAccount("Create an account to see your mistakes.") { nav.go(Mistakes()) }
        },
        Tool("Coach", "Your plan and focus", Icons.Rounded.AutoAwesome, null) {
            nav.requireAccount("Create an account to get your coach report.") { nav.go(CoachRoute) }
        },
        Tool("History", "All your results", Icons.Rounded.HistoryIcon, null) {
            nav.requireAccount("Create an account to keep your history.") { nav.go(History()) }
        },
    )
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        tools.chunked(2).forEach { pair ->
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                pair.forEach { t -> ToolTile(t, Modifier.weight(1f).fillMaxHeight()) }
            }
        }
    }
}

@Composable
private fun ToolTile(t: Tool, modifier: Modifier) {
    NbCard(modifier, onClick = t.onClick) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            IconTile(t.icon, NB.colors.brandText, NB.colors.brandSoft, 40.dp)
            Spacer(Modifier.weight(1f))
            if (t.badge != null) Tag(t.badge, fg = NB.colors.badText, bg = NB.colors.badSoft)
        }
        Spacer(Modifier.height(12.dp))
        Text(t.title, style = NB.type.title3, color = NB.colors.ink)
        Text(t.sub, style = NB.type.bodySmall, color = NB.colors.muted)
    }
}

// MARK: Guest

@Composable
private fun GuestToday(nav: Nav) {
    val api = LocalGraph.current.api
    val session by api.signedIn.collectAsState()
    val loader = rememberLoad(session) { if (session) loadRecent(api, 5) else emptyList() }
    val recent = loader.value.orEmpty()

    TabScreen(
        title = greeting(hourNow(), null),
        subtitle = "Free practice, no account needed",
        actions = { com.soyeb.ieltspractice.feature.updates.UpdatesBell(nav); Avatar(null, onClick = { nav.go(You) }) },
        refreshing = loader.refreshing, onRefresh = if (session) loader.reload else null,
    ) {
        item {
            BrandHero { ink ->
                Text("Free, no account needed", style = NB.type.label, color = ink.muted)
                Spacer(Modifier.height(6.dp))
                Text("Practise all four IELTS tests", style = NB.type.headline.copy(fontSize = 26.sp, lineHeight = 32.sp), color = ink.fg, modifier = Modifier.semantics { heading() })
                Spacer(Modifier.height(6.dp))
                Text("Timed like the real exam, with an honest band score on each test.", style = NB.type.bodySmall, color = ink.muted)
                Spacer(Modifier.height(18.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Rounded.WorkspacePremium, null, Modifier.size(18.dp), tint = ink.fg)
                    Text("Full mock · 2 h 45 m", style = NB.type.label.copy(fontFeatureSettings = "tnum"), color = ink.fg)
                }
                Spacer(Modifier.height(18.dp))
                NbButton("Start full mock", { nav.go(MockStart) }, kind = BtnKind.Inverse, fill = true, size = com.soyeb.ieltspractice.design.components.BtnSize.Large, haptic = true)
            }
        }
        item {
            Section("Or practise one test") {
                RowGroup {
                    Skill.entries.forEachIndexed { i, s ->
                        if (i > 0) RowDivider()
                        NbListRow(s.label, subtitle = guestLine(s), leading = { SkillTile(s, 40.dp) }, onClick = { nav.openTab(skillTab(s)) })
                    }
                }
            }
        }
        if (recent.isNotEmpty()) item {
            Section("Your recent tests") {
                RowGroup {
                    recent.forEachIndexed { i, r ->
                        if (i > 0) RowDivider()
                        ResultRow(r, null) { openItem(nav, r) }
                    }
                }
                Text("These live on this device only. Create an account to keep them.", style = NB.type.bodySmall, color = NB.colors.muted, modifier = Modifier.padding(horizontal = 4.dp))
            }
        }
        item {
            NbCard {
                Text("Create a free account to keep your results", style = NB.type.title2, color = NB.colors.ink)
                Spacer(Modifier.height(4.dp))
                Text("Save your bands, watch your progress and unlock History, Mistakes, Review and your coach.", style = NB.type.bodySmall, color = NB.colors.muted)
                Spacer(Modifier.height(16.dp))
                NbButton("Create free account", { nav.go(Auth("signup")) }, kind = BtnKind.Secondary, fill = true)
                NbButton("I have an account", { nav.go(Auth("signin")) }, kind = BtnKind.Text, fill = true)
            }
        }
    }
}

private fun guestLine(s: Skill) = when (s) {
    Skill.Listening -> "4 parts, 40 questions"
    Skill.Reading -> "3 passages, 40 questions"
    Skill.Writing -> "Task 1 and Task 2 with essay feedback"
    Skill.Speaking -> "Parts 1 to 3 with examiner feedback"
}
