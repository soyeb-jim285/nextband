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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.soyeb.ieltspractice.design.Skill
import com.soyeb.ieltspractice.design.color
import com.soyeb.ieltspractice.design.soft
import com.soyeb.ieltspractice.feature.history.HStatus
import com.soyeb.ieltspractice.feature.history.HistoryItem
import com.soyeb.ieltspractice.feature.history.mergeItems
import com.soyeb.ieltspractice.feature.history.openItem
import com.soyeb.ieltspractice.feature.history.toItem
import com.soyeb.ieltspractice.feature.today.daysToExam
import com.soyeb.ieltspractice.nav.MockHub
import com.soyeb.ieltspractice.nav.Nav
import com.soyeb.ieltspractice.nav.Review
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant

/** One line in the Updates sheet. [at] orders "new since you last looked"; null = standing reminder (cards due, exam). */
private data class Update(val key: String, val icon: ImageVector, val skill: Skill?, val title: String, val body: String, val at: Instant?, val open: (Nav) -> Unit)

private const val SEEN = "updates:seenAt"

private suspend fun loadUpdates(api: Api, examDate: String?): List<Update> = coroutineScope {
    val due = async { runCatching { api.dueCards().dueTotal }.getOrDefault(0) }
    val mock = async { runCatching { api.mockCurrent() }.getOrNull() }
    val sp = async { runCatching { api.attempts(null, 1).items.map { it.toItem() } }.getOrDefault(emptyList()) }
    val lr = async { runCatching { api.lrAttempts().map { it.toItem() } }.getOrDefault(emptyList()) }
    val since = Instant.now().minus(Duration.ofDays(3))
    val recent = mergeItems(sp.await(), lr.await()).filter { (it.instant ?: Instant.EPOCH) > since }
    buildList {
        recent.filter { it.status == HStatus.Analysing }.forEach {
            add(Update("a:${it.id}", Icons.Rounded.HourglassTop, it.skill, "Marking your ${it.skill.label} answer", "${it.title}. We'll show the band here when it's ready.", it.instant) { n -> openItem(n, it) })
        }
        recent.filter { it.status == HStatus.Done && it.band != null }.take(4).forEach { item: HistoryItem ->
            add(Update("d:${item.id}", Icons.Rounded.TaskAlt, item.skill, "${item.skill.label} result: band ${fmtBand(item.band)}", item.title, item.instant) { n -> openItem(n, item) })
        }
        due.await().takeIf { it > 0 }?.let { add(Update("cards", Icons.Rounded.Style, null, "$it drill ${if (it == 1) "card" else "cards"} due", "A few minutes keeps your mistakes from coming back.", null) { n -> n.go(Review()) }) }
        mock.await()?.let { m -> add(Update("mock", Icons.Rounded.WorkspacePremium, null, "Your full mock is waiting", "Pick up where you left off.", null) { n -> n.go(MockHub(m.id)) }) }
        daysToExam(examDate)?.takeIf { it in 0..30 }?.let { d ->
            add(Update("exam", Icons.Rounded.Event, null, if (d == 0) "Your exam is today" else "$d ${if (d == 1) "day" else "days"} to your exam", "A full mock this week shows where you stand.", null) { n -> n.go(com.soyeb.ieltspractice.nav.MockStart) })
        }
    }
}

/**
 * The bell on every tab's top bar. A dot when something arrived since the sheet was last opened (a result, a marking in
 * progress); standing reminders (cards due, exam) are listed but do not light it (Today already badges cards). Opens a sheet of updates; each row goes where it points.
 * In-app only for now: phone notifications come later and will reuse this list.
 */
@Composable
fun UpdatesBell(nav: Nav) {
    val graph = LocalGraph.current
    val me by graph.api.me.collectAsState()
    val seenAt by graph.prefs.string(SEEN).collectAsState(initial = null)
    val loader = rememberLoad(me?.user?.id) { if (me == null) emptyList() else loadUpdates(graph.api, me?.settings?.examDate) }
    val items = loader.value.orEmpty()
    val seen = seenAt?.let { runCatching { Instant.parse(it) }.getOrNull() }
    val fresh = items.count { u -> u.at != null && (seen == null || u.at > seen) }
    var open by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Box {
        NbIconButton(Icons.Outlined.Notifications, if (fresh > 0) "Updates, $fresh new" else "Updates", {
            open = true
            scope.launch { graph.prefs.setString(SEEN, Instant.now().toString()) }
        })
        if (fresh > 0) Box(
            Modifier.align(Alignment.TopEnd).offset(x = (-11).dp, y = 11.dp).size(9.dp).background(NB.colors.bad, CircleShape).clearAndSetSemantics { },
        )
    }
    if (open) NbSheet({ open = false }, title = "Updates") {
        if (items.isEmpty()) Text("You're all caught up. Results and reminders show up here.", style = NB.type.body, color = NB.colors.muted)
        else RowGroup {
            items.forEachIndexed { i, u ->
                if (i > 0) RowDivider()
                NbListRow(
                    u.title, subtitle = u.body,
                    leading = { if (u.skill != null) IconTile(u.icon, u.skill.color, u.skill.soft, 40.dp) else IconTile(u.icon, NB.colors.brandText, NB.colors.brandSoft, 40.dp) },
                    onClick = { open = false; u.open(nav) },
                )
            }
        }
    }
}
