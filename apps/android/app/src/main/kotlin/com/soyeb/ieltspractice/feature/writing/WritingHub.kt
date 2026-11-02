package com.soyeb.ieltspractice.feature.writing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Mail
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.soyeb.ieltspractice.LocalDemo
import com.soyeb.ieltspractice.LocalGraph
import com.soyeb.ieltspractice.data.logic.QuotaCopy
import com.soyeb.ieltspractice.data.logic.formatDate
import com.soyeb.ieltspractice.data.logic.sentenceCase
import com.soyeb.ieltspractice.data.model.AttemptListItem
import com.soyeb.ieltspractice.data.model.Mock
import com.soyeb.ieltspractice.design.NB
import com.soyeb.ieltspractice.design.Skill
import com.soyeb.ieltspractice.design.components.Avatar
import com.soyeb.ieltspractice.design.components.BandPill
import com.soyeb.ieltspractice.design.components.BtnKind
import com.soyeb.ieltspractice.design.components.BtnSize
import com.soyeb.ieltspractice.design.components.CardTone
import com.soyeb.ieltspractice.design.components.ConfirmDialog
import com.soyeb.ieltspractice.design.components.IconTile
import com.soyeb.ieltspractice.design.components.MockStrip
import com.soyeb.ieltspractice.design.components.NbButton
import com.soyeb.ieltspractice.design.components.NbCard
import com.soyeb.ieltspractice.design.components.NbIconButton
import com.soyeb.ieltspractice.design.components.NbListRow
import com.soyeb.ieltspractice.design.components.Overline
import com.soyeb.ieltspractice.design.components.RowDivider
import com.soyeb.ieltspractice.design.components.RowGroup
import com.soyeb.ieltspractice.design.components.SectionHeader
import com.soyeb.ieltspractice.design.components.Segmented
import com.soyeb.ieltspractice.design.components.SkillTile
import com.soyeb.ieltspractice.design.components.TabScreen
import com.soyeb.ieltspractice.design.components.Tag
import com.soyeb.ieltspractice.design.components.rememberLoad
import com.soyeb.ieltspractice.nav.AttemptResult
import com.soyeb.ieltspractice.nav.Bank
import com.soyeb.ieltspractice.nav.History
import com.soyeb.ieltspractice.nav.MockHub
import com.soyeb.ieltspractice.nav.MockStart
import com.soyeb.ieltspractice.nav.Nav
import com.soyeb.ieltspractice.nav.You
import com.soyeb.ieltspractice.nav.WritingTask
import kotlinx.coroutines.launch

private class HubData(val drafts: List<DraftMeta>, val recent: List<AttemptListItem>, val mock: Mock?)

private class TaskKind(
    val title: String, val meta: String, val blurb: String, val types: List<String>, val icon: ImageVector,
    val part: Int, val variant: String?,
)

private val academic = TaskKind(
    "Task 1 Academic", "20 min · 150+ words", "Describe a chart, table, process or map.",
    listOf("Line graph", "Bar chart", "Pie chart", "Table", "Process", "Map"), Icons.Rounded.BarChart, 1, "academic",
)
private val general = TaskKind(
    "Task 1 General", "20 min · 150+ words", "Write a letter that covers three points.",
    listOf("Formal letter", "Semi-formal", "Informal"), Icons.Rounded.Mail, 1, "general",
)
private val essay = TaskKind(
    "Task 2", "40 min · 250+ words", "Argue a position in an essay.",
    listOf("Opinion", "Discussion", "Problem and solution", "Advantages", "Two-part question"), Icons.Rounded.Description, 2, null,
)

/** The Writing tab: the full test hero, the three tasks, drafts to resume and recent results. */
@Composable
fun WritingHub(nav: Nav) {
    val graph = LocalGraph.current
    val api = graph.api
    val demo = LocalDemo.current
    val me by api.me.collectAsState()
    val account by api.hasAccount.collectAsState()
    val guest by api.isGuest.collectAsState()
    val quota by api.quota.collectAsState()
    val testType by graph.prefs.testType.collectAsState(initial = "academic")
    val target = me?.settings?.targetBand ?: 7.0
    val store = remember { DraftStore(graph.prefs) }
    val scope = rememberCoroutineScope()
    var picked by rememberSaveable { mutableStateOf<String?>(null) }
    val variant = picked ?: testType
    var discard by remember { mutableStateOf<DraftMeta?>(null) }
    LaunchedEffect(Unit) { api.refreshQuotaIfStale() }

    val load = rememberLoad(account) {
        if (demo?.screen == "writing-drafts") {
            store.save("w2", Draft("Many people argue that working from home brings real benefits to both employees and employers. Workers save the time and money they would spend commuting."), DraftMeta("w2", 2, "Remote work"))
            store.save("w1a", Draft("The graph shows how much electricity three countries generate from renewable sources between 2000 and 2020."), DraftMeta("w1a", 1, "Renewable energy share", "academic"))
        }
        HubData(
            drafts = store.index(),
            recent = if (!account) emptyList() else runCatching {
                api.attempts("writing", 1).items.filter { it.status == "done" || it.status == "analyzing" }.take(3)
            }.getOrDefault(emptyList()),
            mock = runCatching { api.mockCurrent() }.getOrNull()?.takeIf { it.open },
        )
    }
    val data = load.value
    val tasks = if (variant == "general") listOf(general, academic, essay) else listOf(academic, general, essay)

    TabScreen(
        "Writing",
        actions = { com.soyeb.ieltspractice.feature.updates.UpdatesBell(nav); Avatar(me?.user?.name?.takeIf { !guest && it.isNotBlank() }, Modifier.padding(end = 8.dp), onClick = { nav.go(You) }) },
        refreshing = load.refreshing, onRefresh = load.reload,
    ) {
        item {
            val m = data?.mock
            MockStrip(
                onClick = { if (m != null) nav.go(MockHub(m.id)) else nav.go(MockStart) },
                resumeText = m?.let { x -> x.next?.let { Skill.of(it)?.label }?.let { "$it next" } ?: "In progress" },
            )
        }
        item {
            NbCard(tone = CardTone.Hero) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SkillTile(Skill.Writing, 40.dp)
                    Spacer(Modifier.size(12.dp))
                    Column(Modifier.weight(1f)) {
                        Overline("Full writing test", color = NB.colors.brandText)
                        Text("60 min · Task 1 + Task 2", style = NB.type.title2, color = NB.colors.ink)
                    }
                }
                Spacer(Modifier.height(16.dp))
                Segmented(listOf("Academic", "General"), if (variant == "general") 1 else 0, { picked = if (it == 1) "general" else "academic" })
                Spacer(Modifier.height(16.dp))
                NbButton(
                    "Start full test", { nav.startTest("writing") { nav.go(WritingTask("full", variant)) } },
                    fill = true, size = BtnSize.Large, loading = nav.checking, haptic = true,
                )
            }
        }
        tasks.forEach { k ->
            item(key = k.title) {
                NbCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconTile(k.icon, NB.colors.writing, NB.colors.writingSoft, 44.dp)
                        Spacer(Modifier.size(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(k.title, style = NB.type.title2, color = NB.colors.ink)
                            Text(k.meta, style = NB.type.caption.copy(fontFeatureSettings = "tnum"), color = NB.colors.muted)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(k.blurb, style = NB.type.bodySmall, color = NB.colors.muted)
                    Spacer(Modifier.height(10.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        k.types.forEach { Tag(it) }
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NbButton(
                            "Random prompt",
                            { nav.startTest("writing") { nav.go(WritingTask(if (k.part == 1) "task1" else "task2", k.variant)) } },
                            Modifier.weight(1f), kind = BtnKind.Secondary,
                        )
                        NbButton("Choose from bank", { nav.go(Bank("writing", k.part)) }, Modifier.weight(1f), kind = BtnKind.Outline)
                    }
                }
            }
        }
        val drafts = data?.drafts.orEmpty()
        if (drafts.isNotEmpty()) {
            item { SectionHeader("Drafts in progress", Modifier.padding(top = 12.dp)) }
            item {
                val now = System.currentTimeMillis()
                RowGroup {
                    drafts.forEachIndexed { i, d ->
                        if (i > 0) RowDivider()
                        NbListRow(
                            sentenceCase(d.title),
                            subtitle = "Task ${d.part} · ${plural(d.words)} · ${ago(d.updatedAt, now)}",
                            leading = { IconTile(Icons.Rounded.EditNote, NB.colors.writing, NB.colors.writingSoft, 40.dp) },
                            trailing = { NbIconButton(Icons.Rounded.Close, "Discard draft: ${d.title}", { discard = d }) },
                            onClick = { nav.go(WritingTask(if (d.part == 1) "task1" else "task2", d.variant, d.promptId)) },
                        )
                    }
                }
            }
        }
        val recent = data?.recent.orEmpty()
        if (recent.isNotEmpty()) {
            item { SectionHeader("Recent results", Modifier.padding(top = 12.dp), action = "See all", onAction = { nav.go(History("writing")) }) }
            item {
                RowGroup {
                    recent.forEachIndexed { i, a ->
                        if (i > 0) RowDivider()
                        NbListRow(
                            sentenceCase(a.promptTitle),
                            subtitle = listOf("Task ${a.part}", formatDate(a.createdAt)).filter { it.isNotEmpty() }.joinToString(" · "),
                            trailing = { if (a.status == "done") BandPill(a.overall, target) else Tag("Marking") },
                            chevron = true,
                            onClick = { nav.go(AttemptResult.of(a.id)) },
                        )
                    }
                }
            }
        }
        item {
            val q = quota
            val parts = listOfNotNull(
                q?.let { "Writing tests: ${QuotaCopy.leftShort(it, "writing")}" },
                if (me?.settings?.blockPaste == false) "Autocorrect is off; pasting is allowed." else "Autocorrect is off and pasting is blocked, like the exam.",
            )
            Text(parts.joinToString(" "), Modifier.padding(horizontal = 4.dp, vertical = 8.dp), style = NB.type.caption, color = NB.colors.muted)
        }
    }

    discard?.let { d ->
        ConfirmDialog(
            title = "Discard this draft?", body = "\"${sentenceCase(d.title)}\" and what you wrote will be deleted from this device.",
            confirm = "Discard", destructive = true, dismiss = "Keep it",
            onConfirm = { discard = null; scope.launch { store.clear(listOf(d.promptId)); load.reload() } },
            onDismiss = { discard = null },
        )
    }
}
