package com.soyeb.ieltspractice.feature.speaking

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.soyeb.ieltspractice.LocalDemo
import com.soyeb.ieltspractice.LocalGraph
import com.soyeb.ieltspractice.data.PendingRecording
import com.soyeb.ieltspractice.data.UploadState
import com.soyeb.ieltspractice.data.belongsTo
import com.soyeb.ieltspractice.data.logic.QuotaCopy
import com.soyeb.ieltspractice.data.logic.formatDate
import com.soyeb.ieltspractice.design.NB
import com.soyeb.ieltspractice.design.Skill
import com.soyeb.ieltspractice.design.components.Avatar
import com.soyeb.ieltspractice.design.components.BandPill
import com.soyeb.ieltspractice.design.components.BrandHero
import com.soyeb.ieltspractice.design.components.BtnKind
import com.soyeb.ieltspractice.design.components.BtnSize
import com.soyeb.ieltspractice.design.components.ChipRow
import com.soyeb.ieltspractice.design.components.ConfirmDialog
import com.soyeb.ieltspractice.design.components.EmptyState
import com.soyeb.ieltspractice.design.components.ErrorState
import com.soyeb.ieltspractice.design.components.Load
import com.soyeb.ieltspractice.design.components.MockStrip
import com.soyeb.ieltspractice.design.components.NbButton
import com.soyeb.ieltspractice.design.components.NbCard
import com.soyeb.ieltspractice.design.components.NbIconButton
import com.soyeb.ieltspractice.design.components.NbListRow
import com.soyeb.ieltspractice.design.components.Overline
import com.soyeb.ieltspractice.design.components.RowDivider
import com.soyeb.ieltspractice.design.components.RowGroup
import com.soyeb.ieltspractice.design.components.SectionHeader
import com.soyeb.ieltspractice.design.components.Skeleton
import com.soyeb.ieltspractice.design.components.SkillTile
import com.soyeb.ieltspractice.design.components.TabScreen
import com.soyeb.ieltspractice.design.components.rememberLoad
import com.soyeb.ieltspractice.nav.Auth
import com.soyeb.ieltspractice.nav.AttemptResult
import com.soyeb.ieltspractice.nav.Bank
import com.soyeb.ieltspractice.nav.History
import com.soyeb.ieltspractice.nav.LiveExam
import com.soyeb.ieltspractice.nav.MockHub
import com.soyeb.ieltspractice.nav.MockStart
import com.soyeb.ieltspractice.nav.Nav
import com.soyeb.ieltspractice.nav.SpeakingSession
import com.soyeb.ieltspractice.nav.You
import kotlinx.coroutines.launch

private class PartInfo(val n: Int, val name: String, val time: String)

private val parts = listOf(PartInfo(1, "Interview", "4–5 min"), PartInfo(2, "Cue card", "3–4 min"), PartInfo(3, "Discussion", "4–5 min"))

@Composable
fun SpeakingTabScreen(nav: Nav) {
    val graph = LocalGraph.current
    val api = graph.api
    val demo = LocalDemo.current
    val store = graph.pending
    val scope = rememberCoroutineScope()
    val me by api.me.collectAsState()
    val quotaNow by api.quota.collectAsState()
    val account by api.hasAccount.collectAsState()
    val allPending by store.items.collectAsState()
    // Only this account's recordings: another user of the device must not hear or retry them into their own results.
    val pending = if (demo != null) allPending else allPending.filter { it.belongsTo(me?.user?.id) }
    val states by store.states.collectAsState()
    val source by graph.prefs.speakingSource.collectAsState("any")
    val quota = quotaNow ?: me?.quota()
    val target = me?.settings?.targetBand ?: 7.0
    val liveOn = me?.live == true || quota?.live == true

    val recent = rememberLoad(account) { if (!account) emptyList<RecentRow>() else groupRecent(api.attempts("speaking", 1).items) }
    val mock = rememberLoad(account) { runCatching { api.mockCurrent() }.getOrNull() }
    var deleting by remember { mutableStateOf<PendingRecording?>(null) }
    val started = remember { mutableStateListOf<String>() } // uploads begun from this screen: open the result when one finishes

    LaunchedEffect(Unit) {
        runCatching { api.refreshQuotaIfStale() }
        if (demo == null) store.reload()
        else if (demo.tab == "Pending") {
            val now = System.currentTimeMillis()
            store.add(PendingRecording("demo-p1", "sp1", 1, "Part 1: Hometown", now - 25 * 60_000, 62_000, emptyList(), listOf(0)))
            store.add(PendingRecording("demo-p2", "sp2", 2, "Part 2: Describe a book that changed the way you think", now - 3 * 3_600_000, 118_000, emptyList(), listOf(0)))
        }
    }
    LaunchedEffect(states) {
        val id = started.firstNotNullOfOrNull { (states[it] as? UploadState.Done)?.attemptId }
        if (id != null) { started.clear(); nav.go(AttemptResult.of(id)) }
    }

    TabScreen(
        "Speaking",
        actions = { com.soyeb.ieltspractice.feature.updates.UpdatesBell(nav); Avatar(me?.user?.takeIf { !it.isAnonymous }?.name, onClick = { nav.go(You) }) },
        refreshing = recent.refreshing,
        onRefresh = {
            recent.reload(); mock.reload()
            scope.launch { runCatching { api.refreshQuota() } }
            if (demo == null) store.reload()
        },
    ) {
        item {
            val m = mock.value?.takeIf { it.open }
            MockStrip(
                onClick = { if (m != null) nav.go(MockHub(m.id)) else nav.go(MockStart) },
                resumeText = m?.let { x -> x.next?.let { n -> Skill.of(n)?.label?.let { "$it next" } } ?: "In progress" },
            )
        }
        if (liveOn) item {
            LiveHero(quota?.let { QuotaCopy.leftShort(it, "examiner") }, nav.checking) { nav.startLive { nav.go(LiveExam()) } }
        }
        item { FullTestCard(quota?.let { QuotaCopy.leftShort(it, "speaking") }) { nav.startTest("speaking") { nav.go(SpeakingSession("full")) } } }

        item { Column { Spacer(Modifier.height(12.dp)); SectionHeader("Practise one part") } }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                parts.forEach { p ->
                    PartCard(
                        p, Modifier.weight(1f),
                        onStart = { nav.startTest("speaking") { nav.go(SpeakingSession("part${p.n}")) } },
                        onTopic = { nav.go(Bank("speaking", p.n)) },
                    )
                }
            }
        }
        if (me?.cambridgeAccess == true) item {
            val opts = listOf("any" to "Mixed", "cambridge" to "Cambridge books", "generated" to "Our own")
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Overline("Question source")
                ChipRow(
                    opts.map { it.second }, setOf(opts.indexOfFirst { it.first == source }.coerceAtLeast(0)),
                    { i -> scope.launch { graph.prefs.setSpeakingSource(opts[i].first) } }, contentPadding = PaddingValues(0.dp),
                )
                if (source == "cambridge") Text(
                    "Cambridge questions have no recorded examiner voice, so the practice test shows them on screen. The live examiner still asks them aloud.",
                    style = NB.type.caption, color = NB.colors.muted,
                )
            }
        }

        if (pending.isNotEmpty()) {
            item { Column { Spacer(Modifier.height(12.dp)); SectionHeader("Not uploaded yet") } }
            item {
                RowGroup {
                    pending.forEachIndexed { i, p ->
                        if (i > 0) RowDivider()
                        PendingRow(p, states[p.id], onRetry = { started.add(p.id); store.start(p, api) }, onDiscard = { deleting = p })
                    }
                }
            }
        }

        item {
            Column {
                Spacer(Modifier.height(12.dp))
                SectionHeader("Recent results", action = if (account) "See all" else null, onAction = { nav.go(History("speaking")) })
            }
        }
        when {
            !account -> item {
                NbCard {
                    Text("Keep your results", style = NB.type.title3, color = NB.colors.ink)
                    Spacer(Modifier.height(4.dp))
                    Text("Create an account to keep every speaking result and see your progress.", style = NB.type.bodySmall, color = NB.colors.muted)
                    Spacer(Modifier.height(12.dp))
                    NbButton("Create account", { nav.go(Auth("signup", "Create an account to keep your results")) }, kind = BtnKind.Secondary, size = BtnSize.Small)
                }
            }
            recent.state is Load.Loading -> item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Skeleton(Modifier.fillMaxWidth(), height = 64.dp, corner = 16.dp)
                    Skeleton(Modifier.fillMaxWidth(), height = 64.dp, corner = 16.dp)
                }
            }
            recent.state is Load.Failed -> item { ErrorState((recent.state as Load.Failed).message, recent.reload) }
            else -> {
                val rows = recent.value.orEmpty()
                if (rows.isEmpty()) item { EmptyState(Icons.Rounded.Mic, "No speaking results yet", "Take a practice test and your band will show up here.") }
                else item {
                    RowGroup {
                        rows.take(5).forEachIndexed { i, r ->
                            if (i > 0) RowDivider()
                            NbListRow(
                                title = r.title,
                                subtitle = listOfNotNull(formatDate(r.createdAt).ifEmpty { null }, if (r.parts > 1) "${r.parts} parts" else null).joinToString(" · "),
                                leading = { SkillTile(Skill.Speaking, 40.dp) },
                                trailing = { BandPill(r.overall, target) },
                                chevron = true,
                                onClick = { nav.go(AttemptResult(r.ids)) },
                            )
                        }
                    }
                }
            }
        }
    }

    deleting?.let { p ->
        ConfirmDialog(
            "Delete this recording?", "The recording is removed from this device and can't be uploaded afterwards.", "Delete",
            onConfirm = { deleting = null; scope.launch { store.discard(p, api) } }, onDismiss = { deleting = null }, destructive = true,
        )
    }
}

/** The live examiner: the screen's brand hero, with the voice orb breathing beside the pitch. */
@Composable
private fun LiveHero(left: String?, checking: Boolean, onStart: () -> Unit) {
    BrandHero { ink ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("LIVE EXAMINER", style = NB.type.overline, color = ink.muted)
                Text("Talk to a live AI examiner", style = NB.type.title1, color = ink.fg)
                Text("11–14 min · real-time", style = NB.type.bodySmall, color = ink.muted)
                Spacer(Modifier.height(6.dp))
                NbButton("Start live test", onStart, kind = BtnKind.Inverse, icon = Icons.Rounded.Mic, loading = checking, haptic = true)
                if (left != null) Text(left, style = NB.type.caption, color = ink.muted)
            }
            VoiceOrb(OrbMode.Speaking, { 0.3f }, orbSize = 96.dp)
        }
    }
}

@Composable
private fun FullTestCard(left: String?, onStart: () -> Unit) {
    val c = NB.colors
    NbCard(onClick = onStart) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SkillTile(Skill.Speaking, 48.dp)
            Column(Modifier.weight(1f).padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Full recorded test", style = NB.type.title2, color = c.ink)
                Text("Parts 1–3 · about 12 min", style = NB.type.bodySmall, color = c.muted)
                if (left != null) Text(left, style = NB.type.caption, color = c.brandText)
            }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = c.faint)
        }
    }
}

@Composable
private fun PartCard(p: PartInfo, modifier: Modifier, onStart: () -> Unit, onTopic: () -> Unit) {
    val c = NB.colors
    NbCard(modifier, onClick = onStart, padding = PaddingValues(start = 12.dp, end = 12.dp, top = 14.dp, bottom = 4.dp)) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Part ${p.n}", style = NB.type.title3, color = c.ink)
            Text(p.name, style = NB.type.bodySmall, color = c.muted)
            Text(p.time, style = NB.type.caption, color = c.muted)
            NbButton("Topics", onTopic, kind = BtnKind.Text, size = BtnSize.Small, modifier = Modifier.semantics { contentDescription = "Choose a topic for Part ${p.n}" })
        }
    }
}

@Composable
private fun PendingRow(p: PendingRecording, state: UploadState?, onRetry: () -> Unit, onDiscard: () -> Unit) {
    val uploading = state == UploadState.Uploading
    Column {
        NbListRow(
            title = p.label,
            subtitle = when (state) {
                is UploadState.Failed -> state.message
                UploadState.Uploading -> "Uploading…"
                else -> "Recorded ${relativeTime(p.createdAt)}"
            },
            leading = { Icon(Icons.Rounded.CloudUpload, null, tint = NB.colors.muted) },
            trailing = {
                Row {
                    NbIconButton(Icons.Rounded.Refresh, "Upload ${p.label} again", onRetry, enabled = !uploading)
                    NbIconButton(Icons.Rounded.Delete, "Delete ${p.label}", onDiscard, enabled = !uploading)
                }
            },
        )
        if (uploading) LinearProgressIndicator(
            modifier = Modifier.fillMaxWidth().padding(horizontal = NB.space.lg).padding(bottom = 12.dp).height(6.dp).clip(NB.shape.pill).semantics { contentDescription = "Uploading" },
            color = NB.colors.brand, trackColor = NB.colors.surface2,
        )
    }
}
