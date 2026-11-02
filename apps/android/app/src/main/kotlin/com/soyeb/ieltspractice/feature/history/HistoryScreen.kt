package com.soyeb.ieltspractice.feature.history

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.soyeb.ieltspractice.LocalGraph
import com.soyeb.ieltspractice.data.net.Api
import com.soyeb.ieltspractice.design.NB
import com.soyeb.ieltspractice.design.Skill
import com.soyeb.ieltspractice.design.components.BandPill
import com.soyeb.ieltspractice.design.components.CardTone
import com.soyeb.ieltspractice.design.components.ChipRow
import com.soyeb.ieltspractice.design.components.EmptyState
import com.soyeb.ieltspractice.design.components.ErrorState
import com.soyeb.ieltspractice.design.components.LocalSnack
import com.soyeb.ieltspractice.design.components.NbCard
import com.soyeb.ieltspractice.design.components.NbListRow
import com.soyeb.ieltspractice.design.components.Overline
import com.soyeb.ieltspractice.design.components.PushedScreen
import com.soyeb.ieltspractice.design.components.SkeletonList
import com.soyeb.ieltspractice.design.components.SkillTile
import com.soyeb.ieltspractice.design.components.Tag
import com.soyeb.ieltspractice.nav.AttemptResult
import com.soyeb.ieltspractice.nav.LrResult
import com.soyeb.ieltspractice.nav.LrRun
import com.soyeb.ieltspractice.nav.Nav
import com.soyeb.ieltspractice.nav.Tab
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private enum class Phase { Loading, Ready, Failed }

/** The merged, paged list behind History. Speaking and Writing come page by page; Listening and Reading in one request. */
@Stable
private class Feed(private val api: Api, private val skill: Skill?) {
    var items by mutableStateOf<List<HistoryItem>>(emptyList()); private set
    var phase by mutableStateOf(Phase.Loading); private set
    var error by mutableStateOf(""); private set
    var refreshing by mutableStateOf(false); private set
    var loadingMore by mutableStateOf(false); private set
    var hasMore by mutableStateOf(false); private set
    private var page = 1
    private var sp = emptyList<HistoryItem>()
    private var lr = emptyList<HistoryItem>()
    private val wantsSp = skill == null || skill == Skill.Writing || skill == Skill.Speaking
    private val wantsLr = skill == null || skill == Skill.Listening || skill == Skill.Reading

    suspend fun refresh() {
        if (phase == Phase.Ready) refreshing = true else phase = Phase.Loading
        try {
            val l = if (wantsLr) api.lrAttempts().map { it.toItem() }.filter { skill == null || it.skill == skill } else emptyList()
            val first = if (wantsSp) api.attempts(skill?.key, 1) else null
            lr = l
            sp = first?.items?.map { it.toItem() }.orEmpty()
            page = 1
            hasMore = first != null && sp.isNotEmpty() && sp.size < first.total
            items = mergeItems(lr, sp)
            phase = Phase.Ready
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (phase != Phase.Ready) {
                error = e.message?.takeIf { it.isNotBlank() } ?: "Check your connection and try again."
                phase = Phase.Failed
            }
        } finally {
            refreshing = false
        }
    }

    suspend fun next() {
        if (loadingMore || !hasMore) return
        loadingMore = true
        try {
            val p = api.attempts(skill?.key, page + 1)
            val fresh = p.items.map { it.toItem() }.filter { n -> sp.none { it.id == n.id } }
            page += 1
            sp = sp + fresh
            hasMore = fresh.isNotEmpty() && sp.size < p.total
            items = mergeItems(lr, sp)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            hasMore = false // a failed page stops the scroll; pull to refresh tries again
        } finally {
            loadingMore = false
        }
    }

    fun remove(id: String) {
        sp = sp.filter { it.id != id }
        lr = lr.filter { it.id != id }
        items = mergeItems(lr, sp)
    }
}

/** Open the result (or, for something unfinished, where it was left). */
fun openItem(nav: Nav, item: HistoryItem) {
    if (item.lr) nav.go(if (item.status == HStatus.Open) LrRun(item.id) else LrResult(item.id)) else nav.go(AttemptResult.of(item.id))
}

/** One finished or unfinished result: skill tile, title, "Part 2 · 30 Sep · 12 min", and the band or what is going on. Used by History and Today. */
@Composable
fun ResultRow(item: HistoryItem, target: Double?, onClick: () -> Unit) {
    NbListRow(
        title = item.title,
        subtitle = metaLine(item),
        leading = { SkillTile(item.skill, 40.dp) },
        trailing = {
            when (item.status) {
                HStatus.Done -> if (item.band != null) BandPill(item.band, target)
                HStatus.Analysing -> Tag("Analysing", fg = NB.colors.brandText, bg = NB.colors.brandSoft)
                HStatus.Failed -> Tag("Failed", fg = NB.colors.badText, bg = NB.colors.badSoft)
                HStatus.Open -> Tag(if (item.lr) "In progress" else "Not submitted", fg = NB.colors.warnText, bg = NB.colors.warnSoft)
            }
        },
        onClick = onClick,
    )
}

@Composable
fun HistoryScreen(nav: Nav, skillArg: String?) {
    val api = LocalGraph.current.api
    val hasAccount by api.hasAccount.collectAsState()
    PushedScreen("History", nav::back) { pad ->
        if (!hasAccount) EmptyState(
            Icons.Rounded.History, "History keeps your results",
            "Create a free account and every test you take is saved here, with its band.",
            Modifier.padding(pad), action = "Create free account",
            onAction = { nav.requireAccount("Create an account to keep your results.") { } },
        ) else HistoryBody(nav, skillArg, pad)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HistoryBody(nav: Nav, skillArg: String?, pad: PaddingValues) {
    val graph = LocalGraph.current
    val api = graph.api
    val snack = LocalSnack.current
    val hf = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val me by api.me.collectAsState()
    val target = me?.settings?.targetBand

    var filter by remember { mutableIntStateOf(historyFilters.indexOf(Skill.of(skillArg)).coerceAtLeast(0)) }
    val skill = historyFilters[filter]
    val feed = remember(skill) { Feed(api, skill) }
    LaunchedEffect(feed) { feed.refresh() }
    var hidden by remember { mutableStateOf(emptySet<String>()) }
    val listState = rememberLazyListState()

    val nearEnd by remember { derivedStateOf { listState.layoutInfo.let { (it.visibleItemsInfo.lastOrNull()?.index ?: 0) >= it.totalItemsCount - 4 } } }
    LaunchedEffect(nearEnd, feed.hasMore, feed.items.size, feed.phase) {
        if (nearEnd && feed.hasMore && feed.phase == Phase.Ready) feed.next()
    }

    // The row disappears now; the delete runs only if the snackbar times out without Undo. The app scope outlives this screen.
    fun delete(item: HistoryItem) {
        hf.performHapticFeedback(HapticFeedbackType.LongPress)
        hidden = hidden + item.id
        graph.scope.launch {
            val r = snack.showSnackbar("Result removed", actionLabel = "Undo", duration = SnackbarDuration.Short)
            if (r == SnackbarResult.ActionPerformed) {
                hidden = hidden - item.id
                return@launch
            }
            try {
                if (item.lr) api.lrDelete(item.id) else api.deleteAttempt(item.id)
                feed.remove(item.id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                hidden = hidden - item.id
                snack.showSnackbar("Couldn't remove that result. Try again.")
            }
        }
    }

    Column(Modifier.fillMaxSize().padding(top = pad.calculateTopPadding())) {
        ChipRow(
            historyFilters.map { it?.label ?: "All" }, setOf(filter), { filter = it },
            Modifier.padding(vertical = 4.dp),
        )
        Box(Modifier.weight(1f)) {
            when (feed.phase) {
                Phase.Loading -> SkeletonList()
                Phase.Failed -> ErrorState(feed.error, { scope.launch { feed.refresh() } })
                Phase.Ready -> {
                    val visible = feed.items.filter { it.id !in hidden }
                    PullToRefreshBox(feed.refreshing, { scope.launch { feed.refresh() } }, Modifier.fillMaxSize()) {
                        if (visible.isEmpty()) {
                            EmptyState(
                                Icons.Rounded.History,
                                if (skill == null) "No results yet" else "No ${skill.label} results yet",
                                "Finish a test and its result shows up here.",
                                action = if (skill == null) "Start practising" else "Take a ${skill.label.lowercase()} test",
                                onAction = { nav.openTab(if (skill == null) Tab.Today else skillTab(skill)) },
                            )
                        } else {
                            val groups = visible.groupBy { bucketOf(it.instant) }
                            LazyColumn(
                                Modifier.fillMaxSize(), state = listState,
                                contentPadding = PaddingValues(start = NB.space.gutter, end = NB.space.gutter, top = 4.dp, bottom = pad.calculateBottomPadding() + 32.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                groups.forEach { (label, rows) ->
                                    stickyHeader(key = "h:$label") {
                                        Box(Modifier.fillMaxWidth().background(NB.colors.bg).padding(top = 12.dp, bottom = 6.dp, start = 4.dp).semantics { heading() }) {
                                            Overline(label)
                                        }
                                    }
                                    items(rows, key = { it.id }) { item ->
                                        SwipeRow(item, target, onOpen = { openItem(nav, item) }, onDelete = { delete(item) })
                                    }
                                }
                                if (feed.loadingMore) item(key = "more") {
                                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                        CircularProgressIndicator(Modifier.padding(4.dp), color = NB.colors.brand, strokeWidth = 2.dp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SwipeRow(item: HistoryItem, target: Double?, onOpen: () -> Unit, onDelete: () -> Unit) {
    val state = rememberSwipeToDismissBoxState(confirmValueChange = { v ->
        if (v == SwipeToDismissBoxValue.EndToStart) { onDelete(); true } else false
    })
    SwipeToDismissBox(
        state,
        modifier = Modifier.semantics { customActions = listOf(CustomAccessibilityAction("Delete result") { onDelete(); true }) },
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            if (state.dismissDirection == SwipeToDismissBoxValue.EndToStart) Box(
                Modifier.fillMaxSize().clip(NB.shape.card).background(NB.colors.badSoft).padding(horizontal = 24.dp),
                contentAlignment = Alignment.CenterEnd,
            ) { Icon(Icons.Rounded.DeleteOutline, "Delete", tint = NB.colors.badText) }
        },
    ) {
        NbCard(tone = CardTone.Default, padding = PaddingValues(0.dp)) { ResultRow(item, target, onOpen) }
    }
}
