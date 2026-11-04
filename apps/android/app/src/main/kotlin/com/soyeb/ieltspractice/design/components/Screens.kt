package com.soyeb.ieltspractice.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.soyeb.ieltspractice.design.NB
import com.soyeb.ieltspractice.design.pressScale

/** One snackbar host for the whole app (root scaffold). `LocalSnack.current.showSnackbar("Saved")`. */
val LocalSnack = staticCompositionLocalOf { SnackbarHostState() }

@Composable
fun NbSnackbarHost(state: SnackbarHostState, modifier: Modifier = Modifier) {
    SnackbarHost(state, modifier) { data ->
        androidx.compose.material3.Snackbar(
            data, shape = NB.shape.tile, containerColor = NB.colors.ink, contentColor = NB.colors.bg,
            actionColor = if (NB.colors.isDark) NB.colors.onBrand else NB.colors.brandSoft,
        )
    }
}

private val topBarColors @Composable get() = TopAppBarDefaults.topAppBarColors(
    containerColor = NB.colors.bg, scrolledContainerColor = NB.colors.surface,
    titleContentColor = NB.colors.ink, navigationIconContentColor = NB.colors.ink, actionIconContentColor = NB.colors.ink,
)

/**
 * A tab root: large title that collapses on scroll, actions on the right (avatar), pull-to-refresh, lazy content with the
 * gutter. Put each block in `item {}`; spacing between items is [NbSpace.lg].
 */
@Composable
fun TabScreen(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
    refreshing: Boolean = false,
    onRefresh: (() -> Unit)? = null,
    listState: LazyListState = rememberLazyListState(),
    content: LazyListScope.() -> Unit,
) {
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier.nestedScroll(scroll.nestedScrollConnection),
        containerColor = NB.colors.bg,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            LargeTopAppBar(
                title = {
                    Column {
                        Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (subtitle != null && scroll.state.collapsedFraction < 0.5f) Text(subtitle, style = NB.type.bodySmall, color = NB.colors.muted, maxLines = 1)
                    }
                },
                // The brand rides in the bar on every tab (left); actions (bell, avatar) sit right.
                navigationIcon = { Logo(Modifier.padding(start = NB.space.gutter, end = 8.dp), height = 20.dp) },
                actions = actions, colors = topBarColors, scrollBehavior = scroll,
            )
        },
    ) { pad ->
        val body: @Composable () -> Unit = {
            LazyColumn(
                Modifier.fillMaxSize().readable(), state = listState,
                contentPadding = PaddingValues(start = NB.space.gutter, end = NB.space.gutter, top = pad.calculateTopPadding() + 4.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(NB.space.lg),
                content = content,
            )
        }
        if (onRefresh != null) PullToRefreshBox(refreshing, onRefresh, Modifier.fillMaxSize()) { body() } else body()
    }
}

/**
 * A pushed screen: small top bar with back (or close for flows), optional actions and a sticky [bottomBar].
 * [content] receives the inner padding; use [PushedList] for the common scrolling case.
 */
@Composable
fun PushedScreen(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    close: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    scrollBehavior: TopAppBarScrollBehavior? = TopAppBarDefaults.pinnedScrollBehavior(),
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        modifier.let { m -> scrollBehavior?.let { m.nestedScroll(it.nestedScrollConnection) } ?: m },
        containerColor = NB.colors.bg,
        topBar = {
            TopAppBar(
                title = { Text(title, style = NB.type.title2, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    NbIconButton(if (close) Icons.Rounded.Close else Icons.AutoMirrored.Rounded.ArrowBack, if (close) "Close" else "Back", onBack)
                },
                actions = actions, colors = topBarColors, scrollBehavior = scrollBehavior,
            )
        },
        bottomBar = bottomBar,
        content = content,
    )
}

/** Tablets and unfolded foldables: keep lists to a readable column, centred, instead of running edge to edge. */
private fun Modifier.readable() = wrapContentWidth(Alignment.CenterHorizontally).widthIn(max = 720.dp).fillMaxWidth()

/** Standard scrolling body for a [PushedScreen]. */
@Composable
fun PushedList(pad: PaddingValues, modifier: Modifier = Modifier, state: LazyListState = rememberLazyListState(), content: LazyListScope.() -> Unit) {
    LazyColumn(
        modifier.fillMaxSize().readable(), state = state,
        contentPadding = PaddingValues(
            start = NB.space.gutter, end = NB.space.gutter,
            top = pad.calculateTopPadding() + 4.dp, bottom = pad.calculateBottomPadding() + 32.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(NB.space.lg),
        content = content,
    )
}

/**
 * Bottom-pinned action area with a hairline on top. Sits above the navigation bar and the keyboard.
 * Put 1–2 NbButton(fill = true, size = Large) inside; a Row for side-by-side.
 */
@Composable
fun StickyActionBar(modifier: Modifier = Modifier, ime: Boolean = true, content: @Composable ColumnScope.() -> Unit) {
    Surface(color = NB.colors.surface, contentColor = NB.colors.ink, modifier = modifier.fillMaxWidth()) {
        Column {
            HorizontalDivider(color = NB.colors.line)
            Column(
                Modifier.fillMaxWidth().navigationBarsPadding().let { if (ime) it.imePadding() else it }
                    .padding(horizontal = NB.space.gutter, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp), content = content,
            )
        }
    }
}

/** Initials avatar (brandSoft) or a person icon for guests. 40 dp, inside a 48 dp touch target when clickable. */
@Composable
fun Avatar(name: String?, modifier: Modifier = Modifier, size: Dp = 36.dp, onClick: (() -> Unit)? = null) {
    val initials = name?.trim()?.split(Regex("\\s+"))?.filter { it.isNotEmpty() }?.take(2)?.joinToString("") { it.first().uppercase() }.orEmpty()
    val face: @Composable () -> Unit = {
        Box(Modifier.size(size).clip(CircleShape).background(NB.colors.brandSoft), contentAlignment = Alignment.Center) {
            if (initials.isEmpty()) Icon(Icons.Rounded.Person, null, Modifier.size(size * 0.6f), tint = NB.colors.brandText)
            else Text(initials, style = if (size >= 56.dp) NB.type.title1 else NB.type.label, color = NB.colors.brandText)
        }
    }
    if (onClick != null) {
        val src = androidx.compose.runtime.remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
        Box(
            modifier.size(48.dp).pressScale(src, 0.92f).clip(CircleShape)
                .clickable(src, com.soyeb.ieltspractice.design.PressWash, role = androidx.compose.ui.semantics.Role.Button, onClick = onClick)
                .semantics { contentDescription = "You: profile and settings" },
            contentAlignment = Alignment.Center,
        ) { face() }
    }
    else Box(modifier) { face() }
}

/** Gutter-wide column helper for non-lazy pages. */
@Composable
fun Gutter(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().padding(horizontal = NB.space.gutter), verticalArrangement = Arrangement.spacedBy(NB.space.lg), content = content)
}

@Composable
fun SpacerRow(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically, content = content)
}
