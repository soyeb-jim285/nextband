package com.soyeb.ieltspractice.feature.study

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.soyeb.ieltspractice.LocalDemo
import com.soyeb.ieltspractice.LocalGraph
import com.soyeb.ieltspractice.data.logic.categoryLabel
import com.soyeb.ieltspractice.data.logic.clock
import com.soyeb.ieltspractice.data.logic.formatDate
import com.soyeb.ieltspractice.data.model.CategoryCount
import com.soyeb.ieltspractice.data.model.Mistake
import com.soyeb.ieltspractice.data.model.LrSpellingItem
import com.soyeb.ieltspractice.design.NB
import com.soyeb.ieltspractice.design.components.BtnKind
import com.soyeb.ieltspractice.design.components.BtnSize
import com.soyeb.ieltspractice.design.components.ChipRow
import com.soyeb.ieltspractice.design.components.EmptyState
import com.soyeb.ieltspractice.design.components.ErrorState
import com.soyeb.ieltspractice.design.components.LocalSnack
import com.soyeb.ieltspractice.design.components.NbButton
import com.soyeb.ieltspractice.design.components.NbListRow
import com.soyeb.ieltspractice.design.components.NbSheet
import com.soyeb.ieltspractice.design.components.Overline
import com.soyeb.ieltspractice.design.components.PushedScreen
import com.soyeb.ieltspractice.design.components.RowDivider
import com.soyeb.ieltspractice.design.components.RowGroup
import com.soyeb.ieltspractice.design.components.SectionHeader
import com.soyeb.ieltspractice.design.components.Skeleton
import com.soyeb.ieltspractice.design.components.Tag
import com.soyeb.ieltspractice.design.components.rememberLoad
import com.soyeb.ieltspractice.nav.AttemptResult
import com.soyeb.ieltspractice.nav.Mistakes
import com.soyeb.ieltspractice.nav.Nav
import com.soyeb.ieltspractice.nav.Spelling
import com.soyeb.ieltspractice.nav.WritingTask
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Every correction from the learner's results, grouped by attempt, filtered by category; tap one for the explanation and to add it to the review deck. */
@Composable
fun MistakesScreen(nav: Nav, route: Mistakes) {
    val g = LocalGraph.current
    val api = g.api
    val account by api.hasAccount.collectAsState()
    val scope = rememberCoroutineScope()
    val snack = LocalSnack.current
    val demoSheet = LocalDemo.current?.tab == "Sheet"

    var category by remember { mutableStateOf(route.category) }
    var groups by remember { mutableStateOf(emptyList<CategoryCount>()) }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var adding by remember { mutableStateOf(false) }
    val pager = remember { Pager<Mistake>(scope) }
    val spelling = rememberLoad(account) { if (account) api.lrSpelling().items else emptyList() }

    LaunchedEffect(account, category) {
        if (account) {
            val c = category
            pager.reset { page ->
                val log = api.mistakes(c, page)
                groups = log.groups
                log.items to log.total
            }
        }
    }
    // Screenshots: open the first correction's sheet.
    LaunchedEffect(pager.items.size) { if (demoSheet && selectedId == null) selectedId = pager.items.firstOrNull()?.id }

    fun add(m: Mistake) {
        scope.launch {
            adding = true
            val msg = try {
                api.mistakeToCard(m.id)
                pager.update { if (it.id == m.id) it.copy(inDeck = true) else it }
                "Added to your review deck"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                e.message?.takeIf { it.isNotBlank() } ?: "Couldn't add the card. Try again."
            }
            adding = false
            snack.showSnackbar(msg)
        }
    }

    PushedScreen("Mistakes", nav::back) { pad ->
        if (!account) {
            GuestGate(
                Icons.Rounded.CheckCircle, "Your mistake log",
                "Create an account to see the errors you repeat, grouped by type, and turn them into review cards.",
                nav, "Create an account to keep your mistake log", Modifier.padding(pad),
            )
            return@PushedScreen
        }
        val all = groups.sumOf { it.count }
        val spellItems = spelling.value.orEmpty()
        Column(Modifier.fillMaxSize().padding(top = pad.calculateTopPadding())) {
            if (groups.isNotEmpty()) {
                val options = listOf("All $all") + groups.map { "${categoryLabel(it.category)} ${it.count}" }
                val at = groups.indexOfFirst { it.category == category } + 1 // 0 = All
                ChipRow(
                    options, setOf(at), { i -> category = if (i == 0) null else groups[i - 1].category },
                    Modifier.padding(top = 4.dp, bottom = 8.dp).semantics { contentDescription = "Filter by category" },
                )
            }
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = NB.space.gutter, end = NB.space.gutter, top = 4.dp, bottom = pad.calculateBottomPadding() + 32.dp),
                verticalArrangement = Arrangement.spacedBy(NB.space.lg),
            ) {
                item {
                    Text(
                        if (all > 0) "$all ${plural(all, "correction")} from your results, grouped so patterns stand out."
                        else "Every correction from your results, grouped so patterns stand out.",
                        style = NB.type.bodySmall, color = NB.colors.muted,
                    )
                }
                if (category == null && spellItems.isNotEmpty()) item(key = "spelling") { SpellingGroup(spellItems, nav) }
                pager.error?.let { msg -> item(key = "error") { ErrorState(msg, { pager.more() }) } }
                if (!pager.loaded && pager.loading) {
                    items(3) { Skeleton(Modifier.fillMaxWidth(), height = 96.dp, corner = 20.dp) }
                }
                if (pager.loaded && pager.items.isEmpty() && spellItems.isEmpty()) item(key = "empty") {
                    EmptyState(
                        Icons.Rounded.CheckCircle, "Your error log is empty",
                        "Grammar slips, word choices and cohesion issues from your results collect here, so you can spot the ones that keep coming back.",
                        action = "Write an essay", onAction = { nav.startTest("writing") { nav.go(WritingTask("task2")) } },
                    )
                }
                items(runsBy(pager.items) { it.attemptId }, key = { it.first().id }) { run ->
                    val first = run.first()
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column(
                            Modifier.fillMaxWidth().padding(horizontal = 4.dp).clickable(role = Role.Button) { nav.go(AttemptResult.of(first.attemptId)) },
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(first.promptTitle, style = NB.type.readingSmall, color = NB.colors.ink, maxLines = 2)
                            Text(sourceLine(first) + formatDate(first.createdAt).let { if (it.isEmpty()) "" else ", $it" }, style = NB.type.caption, color = NB.colors.muted)
                        }
                        RowGroup {
                            run.forEachIndexed { i, m ->
                                if (i > 0) RowDivider()
                                MistakeRow(m, showCategory = category == null) { selectedId = m.id }
                            }
                        }
                    }
                }
                item(key = "more") {
                    if (pager.loaded && pager.loading) Box(Modifier.fillMaxWidth().padding(16.dp), Alignment.Center) { CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp, color = NB.colors.brand) }
                    LaunchedEffect(pager.items.size, pager.hasMore) { if (pager.hasMore) pager.more() }
                }
            }
        }
    }

    pager.items.firstOrNull { it.id == selectedId }?.let { m ->
        NbSheet(
            onDismiss = { selectedId = null },
            title = categoryLabel(m.category),
            subtitle = sourceLine(m) + (m.time?.let { ", at ${clock(it.toInt())}" } ?: ""),
            actions = {
                NbButton(
                    if (m.inDeck) "In your review deck" else "Add to review deck", { add(m) },
                    kind = if (m.inDeck) BtnKind.Secondary else BtnKind.Primary, size = BtnSize.Large, fill = true,
                    enabled = !m.inDeck, loading = adding, icon = if (m.inDeck) Icons.Rounded.Check else null, haptic = true,
                )
                NbButton("Open the attempt", { selectedId = null; nav.go(AttemptResult.of(m.attemptId)) }, kind = BtnKind.Text, fill = true)
            },
        ) {
            val same = m.original.trim() == m.correction.trim()
            if (!same) {
                Overline("You wrote")
                Text(
                    buildAnnotatedString { withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) { append(m.original) } },
                    style = NB.type.reading, color = NB.colors.badText,
                )
                Overline("Better")
                Text(m.correction, style = NB.type.reading, color = NB.colors.goodText)
            }
            Overline("Why")
            Text(m.explanation, style = NB.type.body, color = NB.colors.ink)
            Text(m.promptTitle, style = NB.type.readingSmall, color = NB.colors.muted, maxLines = 3)
        }
    }
}

private fun sourceLine(m: Mistake) = "${if (m.skill == "speaking") "Speaking Part" else "Writing Task"} ${m.part}"

/** original, then the correction under it; the tap target is the whole row. */
@Composable
private fun MistakeRow(m: Mistake, showCategory: Boolean, onClick: () -> Unit) {
    val same = m.original.trim() == m.correction.trim()
    Column(
        Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).padding(NB.space.lg),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (showCategory || m.inDeck) Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (showCategory) Tag(categoryLabel(m.category))
            if (m.inDeck) Tag("In deck", fg = NB.colors.goodText, bg = NB.colors.goodSoft, icon = Icons.Rounded.Check)
        }
        if (same) {
            Text(m.explanation, style = NB.type.bodySmall, color = NB.colors.ink, maxLines = 3)
        } else {
            Text(
                buildAnnotatedString { withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) { append(m.original) } },
                style = NB.type.readingSmall, color = NB.colors.badText, maxLines = 2,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                Icon(Icons.AutoMirrored.Rounded.ArrowForward, "corrected to", Modifier.size(20.dp).padding(top = 3.dp), tint = NB.colors.muted)
                Text(m.correction, Modifier.weight(1f), style = NB.type.readingSmall, color = NB.colors.goodText, maxLines = 2)
            }
        }
    }
}

/** Words misspelt or wrongly pluralised in Listening and Reading gap answers, most frequent first. */
@Composable
private fun SpellingGroup(items: List<LrSpellingItem>, nav: Nav) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SectionHeader("Spelling and plurals", action = "Practise", onAction = { nav.go(Spelling) })
        RowGroup {
            items.take(4).forEachIndexed { i, w ->
                if (i > 0) RowDivider()
                NbListRow(
                    title = w.word,
                    subtitle = w.typed.take(2).joinToString(", ").takeIf { it.isNotEmpty() }?.let { "You typed $it" },
                    trailing = { Tag(if (w.kind == "plural") "Plural ×${w.count}" else "×${w.count}") },
                )
            }
        }
    }
}
