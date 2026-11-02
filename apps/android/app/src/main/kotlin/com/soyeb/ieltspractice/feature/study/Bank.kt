package com.soyeb.ieltspractice.feature.study

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.soyeb.ieltspractice.LocalDemo
import com.soyeb.ieltspractice.data.logic.sentenceCase
import com.soyeb.ieltspractice.data.model.Prompt
import com.soyeb.ieltspractice.design.NB
import com.soyeb.ieltspractice.design.components.BtnSize
import com.soyeb.ieltspractice.design.components.ChipRow
import com.soyeb.ieltspractice.design.components.EmptyState
import com.soyeb.ieltspractice.design.components.ErrorState
import com.soyeb.ieltspractice.design.components.NbButton
import com.soyeb.ieltspractice.design.components.NbCard
import com.soyeb.ieltspractice.design.components.NbIconButton
import com.soyeb.ieltspractice.design.components.NbSheet
import com.soyeb.ieltspractice.design.components.NbTextField
import com.soyeb.ieltspractice.design.components.Overline
import com.soyeb.ieltspractice.design.components.PushedScreen
import com.soyeb.ieltspractice.design.components.Segmented
import com.soyeb.ieltspractice.design.components.Skeleton
import com.soyeb.ieltspractice.design.components.Tag
import com.soyeb.ieltspractice.design.components.rememberLoad
import com.soyeb.ieltspractice.nav.Bank
import com.soyeb.ieltspractice.nav.Nav
import com.soyeb.ieltspractice.nav.SpeakingSession
import com.soyeb.ieltspractice.nav.WritingTask
import kotlinx.coroutines.delay

private val skills = listOf("speaking", "writing")

/** The prompt bank: search, filter by skill, part and topic, preview a prompt and practise it. */
@Composable
fun BankScreen(nav: Nav, route: Bank) {
    val api = com.soyeb.ieltspractice.LocalGraph.current.api
    val scope = rememberCoroutineScope()
    val demoSheet = LocalDemo.current?.screen == "bank-sheet"
    val fixedSkill = route.skill.isNotBlank()

    var skill by remember { mutableStateOf(route.skill.ifBlank { "speaking" }) }
    var part by remember { mutableStateOf(route.part) }
    var topic by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var q by remember { mutableStateOf("") } // the debounced query the list uses
    var pickedId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(query) {
        if (query.trim() != q) { delay(300); q = query.trim() }
    }
    val meta = rememberLoad { api.promptMeta() }
    val pager = remember { Pager<Prompt>(scope) }
    LaunchedEffect(skill, part, topic, q) {
        val s = skill; val p = part; val t = topic; val text = q
        pager.reset { page ->
            val r = api.prompts(skill = s, part = p, topic = t, q = text.ifEmpty { null }, page = page)
            r.items to r.total
        }
    }
    // Screenshots: open the first prompt's preview.
    LaunchedEffect(pager.items.size) { if (demoSheet && pickedId == null) pickedId = pager.items.firstOrNull()?.id }

    val topics = meta.value?.groups.orEmpty()
        .filter { it.skill == skill && (part == null || it.part == part) }
        .flatMap { it.topics }.filterNotNull().filter { it.isNotBlank() }.distinct().sorted()
    val partOptions = if (skill == "writing") listOf("All tasks", "Task 1", "Task 2") else listOf("All parts", "Part 1", "Part 2", "Part 3")

    fun practise(p: Prompt) {
        pickedId = null
        if (p.skill == "speaking") nav.startTest("speaking") { nav.go(SpeakingSession("part${p.part}", p.id)) }
        else nav.startTest("writing") { nav.go(WritingTask(if (p.part == 2) "task2" else "task1", p.variant, p.id)) }
    }

    PushedScreen("Prompt bank", nav::back) { pad ->
        val gutter = Modifier.padding(horizontal = NB.space.gutter)
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = pad.calculateTopPadding() + 4.dp, bottom = pad.calculateBottomPadding() + 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                NbTextField(
                    query, { query = it }, "Search prompts", gutter, placeholder = "A word from the prompt", leadingIcon = Icons.Rounded.Search,
                    trailing = if (query.isNotEmpty()) ({ NbIconButton(Icons.Rounded.Close, "Clear search", { query = ""; q = "" }) }) else null,
                )
            }
            if (!fixedSkill) item {
                Segmented(skills.map { it.replaceFirstChar { c -> c.uppercase() } }, skills.indexOf(skill), { i ->
                    skill = skills[i]; part = null; topic = null
                }, gutter)
            }
            item {
                ChipRow(partOptions, setOf(part ?: 0), { i -> part = if (i == 0) null else i; topic = null })
            }
            if (topics.isNotEmpty()) item {
                val at = topics.indexOf(topic) + 1
                ChipRow(listOf("All topics") + topics, setOf(at), { i -> topic = if (i == 0) null else topics[i - 1] })
            }
            if (pager.loaded) item {
                Text("${pager.total} ${plural(pager.total, "prompt")}", style = NB.type.caption, color = NB.colors.muted, modifier = gutter)
            }
            pager.error?.let { msg -> item(key = "error") { ErrorState(msg, { pager.more() }) } }
            if (!pager.loaded && pager.loading) {
                items(4) { Skeleton(gutter.fillMaxWidth(), height = 84.dp, corner = 20.dp) }
            }
            if (pager.loaded && pager.items.isEmpty()) item(key = "empty") {
                EmptyState(
                    Icons.Rounded.Search, "No prompts match", "Try another word, or clear the filters.",
                    action = "Clear filters", onAction = { query = ""; q = ""; part = null; topic = null },
                )
            }
            items(pager.items, key = { it.id }) { p ->
                NbCard(gutter, onClick = { pickedId = p.id }) {
                    Text(
                        sentenceCase(p.title), style = NB.type.readingSmall.copy(fontWeight = FontWeight.SemiBold), color = NB.colors.ink, maxLines = 3,
                    )
                    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Tag(partLabel(p))
                        p.topic?.takeIf { it.isNotBlank() }?.let { Tag(it) }
                        if (p.done == true) Tag("Done", fg = NB.colors.goodText, bg = NB.colors.goodSoft, icon = Icons.Rounded.Check)
                    }
                }
            }
            item(key = "more") {
                if (pager.loaded && pager.loading) Box(Modifier.fillMaxWidth().padding(16.dp), Alignment.Center) { CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp, color = NB.colors.brand) }
                LaunchedEffect(pager.items.size, pager.hasMore) { if (pager.hasMore) pager.more() }
            }
        }
    }

    pager.items.firstOrNull { it.id == pickedId }?.let { p ->
        NbSheet(
            onDismiss = { pickedId = null },
            actions = { NbButton("Practise this", { practise(p) }, fill = true, size = BtnSize.Large, haptic = true) },
        ) { PromptPreview(p) }
    }
}

private fun partLabel(p: Prompt): String {
    if (p.skill != "writing") return "Part ${p.part}"
    val v = p.variant
    return if (p.part == 1 && !v.isNullOrBlank()) "Task 1 · ${v.replaceFirstChar { it.uppercase() }}" else "Task ${p.part}"
}

/** The whole prompt as the learner will see it: cue card, questions or task. Content text is Newsreader. */
@Composable
private fun PromptPreview(p: Prompt) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Tag(partLabel(p))
        p.topic?.takeIf { it.isNotBlank() }?.let { Tag(it) }
        p.sourceRef?.takeIf { it.isNotBlank() }?.let { Tag(it) }
    }
    val title = sentenceCase(p.title)
    Text(title, style = NB.type.readingTitle, color = NB.colors.ink)
    if (p.body.isNotBlank() && p.body.trim() != p.title.trim()) Text(p.body, style = NB.type.reading, color = NB.colors.ink)
    p.bullets?.takeIf { it.isNotEmpty() }?.let { bullets ->
        Overline("You should say")
        bullets.forEach { Text("•  $it", style = NB.type.readingSmall, color = NB.colors.ink) }
    }
    p.followUps?.takeIf { it.isNotEmpty() }?.let { qs ->
        Overline(if (p.part == 3) "Discussion questions" else "Questions")
        qs.forEachIndexed { i, text -> Text("${i + 1}.  $text", style = NB.type.readingSmall, color = NB.colors.ink) }
    }
    p.chartSpec?.let { c ->
        Text("Includes a ${c.kind}${c.title.takeIf { t -> t.isNotBlank() }?.let { t -> ": $t" }.orEmpty()}", style = NB.type.bodySmall, color = NB.colors.muted)
    }
}
