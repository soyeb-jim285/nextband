package com.soyeb.ieltspractice.feature.updates

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.Style
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.soyeb.ieltspractice.design.components.ErrorState
import com.soyeb.ieltspractice.design.components.Load
import com.soyeb.ieltspractice.design.components.PollWhileVisible
import com.soyeb.ieltspractice.nav.MockStart
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.soyeb.ieltspractice.LocalGraph
import com.soyeb.ieltspractice.data.net.Api
import com.soyeb.ieltspractice.design.NB
import com.soyeb.ieltspractice.design.components.IconTile
import com.soyeb.ieltspractice.design.components.NbIconButton
import com.soyeb.ieltspractice.design.components.NbListRow
import com.soyeb.ieltspractice.design.components.NbSheet
import com.soyeb.ieltspractice.design.components.RowDivider
import com.soyeb.ieltspractice.design.components.RowGroup
import com.soyeb.ieltspractice.design.components.rememberLoad
import com.soyeb.ieltspractice.design.fmtBand
import com.soyeb.ieltspractice.design.color
import com.soyeb.ieltspractice.design.soft
import com.soyeb.ieltspractice.feature.history.mergeItems
import com.soyeb.ieltspractice.feature.history.openItem
import com.soyeb.ieltspractice.feature.history.toItem
import com.soyeb.ieltspractice.nav.MockHub
import com.soyeb.ieltspractice.nav.Nav
import com.soyeb.ieltspractice.nav.Review
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/** Attempts are required (a failure keeps the last list, or shows a retry); cards and the open mock are optional extras. */
private suspend fun loadUpdates(api: Api, examDate: String?): List<UpdateRow> = coroutineScope {
    val due = async { runCatching { api.dueCards().dueTotal }.getOrDefault(0) }
    val mock = async { runCatching { api.mockCurrent() }.getOrNull() }
    val sp = async { api.attempts(null, 1).items.map { it.toItem() } }
    val lr = async { api.lrAttempts().map { it.toItem() } }
    buildUpdates(due.await(), mock.await()?.id, mergeItems(sp.await(), lr.await()), examDate)
}

private fun UpdateRow.icon(): ImageVector = when (kind) {
    UpdateKind.Marking -> Icons.Rounded.HourglassTop
    UpdateKind.Failed -> Icons.Rounded.ErrorOutline
    UpdateKind.Result -> Icons.Rounded.TaskAlt
    UpdateKind.Cards -> Icons.Rounded.Style
    UpdateKind.Mock -> Icons.Rounded.WorkspacePremium
    UpdateKind.Exam -> Icons.Rounded.Event
}

private fun UpdateRow.title(): String = when (kind) {
    UpdateKind.Marking -> "Marking your ${item!!.skill.label} answer"
    UpdateKind.Failed -> "Marking didn't finish"
    UpdateKind.Result -> "${item!!.skill.label} result: band ${fmtBand(item.band)}"
    UpdateKind.Cards -> "$n drill ${if (n == 1) "card" else "cards"} due"
    UpdateKind.Mock -> "Your full mock is waiting"
    UpdateKind.Exam -> if (n == 0) "Your exam is today" else "$n ${if (n == 1) "day" else "days"} to your exam"
}

private fun UpdateRow.body(): String = when (kind) {
    UpdateKind.Marking -> "${item!!.title}. We'll show the band here when it's ready."
    UpdateKind.Failed -> "${item!!.title}. Open it to try again."
    UpdateKind.Result -> item!!.title
    UpdateKind.Cards -> "A few minutes keeps your mistakes from coming back."
    UpdateKind.Mock -> "Pick up where you left off."
    UpdateKind.Exam -> "A full mock this week shows where you stand."
}

private fun UpdateRow.open(nav: Nav) {
    when (kind) {
        UpdateKind.Cards -> nav.go(Review())
        UpdateKind.Mock -> nav.go(MockHub(mockId!!))
        UpdateKind.Exam -> nav.go(MockStart)
        else -> openItem(nav, item!!)
    }
}

/**
 * The bell on every tab's top bar. A dot while a result or a failed marking has not been shown in the sheet yet; a marking in
 * progress and standing reminders (cards due, exam) are listed but do not light it (Today already badges cards). Opens a sheet of updates; each row goes where it points.
 * In-app only for now: phone notifications come later and will reuse this list.
 */
@Composable
fun UpdatesBell(nav: Nav) {
    val graph = LocalGraph.current
    val me by graph.api.me.collectAsState()
    val uid = me?.user?.id
    val examDate = me?.settings?.examDate
    // Per account, so one account's reads don't hide another's results on a shared phone.
    val seenKey = "updates:seen:$uid"
    val seenRaw by remember(seenKey) { graph.prefs.string(seenKey) }.collectAsState(initial = null)
    val seen = parseSeen(seenRaw)
    val loader = rememberLoad(uid, examDate) { if (uid == null) emptyList() else loadUpdates(graph.api, examDate) }
    val rows = loader.value
    val items = rows.orEmpty()
    val fresh = freshCount(items, seen)
    var open by remember { mutableStateOf(false) }

    // Fresh on return to the app, and while something is being marked (a band may land while this tab stays open).
    var resumed by remember { mutableStateOf(false) }
    LifecycleResumeEffect(Unit) {
        if (resumed) loader.reload() else resumed = true
        onPauseOrDispose { }
    }
    PollWhileVisible(items.any { it.kind == UpdateKind.Marking }) { loader.reload() }
    // Only rows actually loaded and on screen count as read.
    LaunchedEffect(open, rows, seenKey) {
        if (open && rows != null && uid != null) {
            val next = markSeen(rows, seen)
            if (next != seen) graph.prefs.setString(seenKey, next.joinToString(","))
        }
    }

    Box {
        NbIconButton(Icons.Outlined.Notifications, if (fresh > 0) "Updates, $fresh new" else "Updates", {
            open = true
            loader.reload()
        })
        if (fresh > 0) Box(
            Modifier.align(Alignment.TopEnd).offset(x = (-11).dp, y = 11.dp).size(9.dp).background(NB.colors.bad, CircleShape).clearAndSetSemantics { },
        )
    }
    if (open) NbSheet({ open = false }, title = "Updates") {
        val state = loader.state
        if (state is Load.Failed) ErrorState(state.message, { loader.reload() })
        else if (rows == null) Text("Loading updates...", style = NB.type.body, color = NB.colors.muted)
        else if (items.isEmpty()) Text("You're all caught up. Results and reminders show up here.", style = NB.type.body, color = NB.colors.muted)
        else RowGroup {
            items.forEachIndexed { i, u ->
                if (i > 0) RowDivider()
                val skill = u.item?.skill
                NbListRow(
                    u.title(), subtitle = u.body(),
                    leading = {
                        when {
                            u.kind == UpdateKind.Failed -> IconTile(u.icon(), NB.colors.badText, NB.colors.badSoft, 40.dp)
                            skill != null -> IconTile(u.icon(), skill.color, skill.soft, 40.dp)
                            else -> IconTile(u.icon(), NB.colors.brandText, NB.colors.brandSoft, 40.dp)
                        }
                    },
                    onClick = { open = false; u.open(nav) },
                )
            }
        }
    }
}
