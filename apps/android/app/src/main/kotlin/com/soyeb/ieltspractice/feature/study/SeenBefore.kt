package com.soyeb.ieltspractice.feature.study

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Style
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.soyeb.ieltspractice.LocalGraph
import com.soyeb.ieltspractice.data.logic.categoryLabel
import com.soyeb.ieltspractice.design.components.Banner
import com.soyeb.ieltspractice.design.components.NbListRow
import com.soyeb.ieltspractice.design.components.RowDivider
import com.soyeb.ieltspractice.design.components.RowGroup
import com.soyeb.ieltspractice.design.components.SectionHeader
import com.soyeb.ieltspractice.design.components.rememberLoad
import com.soyeb.ieltspractice.nav.Mistakes
import com.soyeb.ieltspractice.nav.Nav
import com.soyeb.ieltspractice.nav.Review

/**
 * On a Speaking or Writing result: which of this test's mistakes you have made before (in how many of your last tests of this skill),
 * and how many went into your drills. Accounts only; nothing when there is nothing to say or the call fails.
 */
@Composable
fun SeenBefore(attemptId: String, nav: Nav, modifier: Modifier = Modifier) {
    val api = LocalGraph.current.api
    val account by api.hasAccount.collectAsState()
    val data = rememberLoad(attemptId, account) { if (account) api.recurring(attemptId) else null }.value ?: return
    val repeats = data.items.filter { it.before > 0 }.take(4)
    if (repeats.isEmpty() && data.added == 0) return
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (repeats.isNotEmpty()) {
            SectionHeader("Mistakes you have made before", action = "Patterns", onAction = { nav.go(Mistakes()) })
            RowGroup {
                repeats.forEachIndexed { i, r ->
                    if (i > 0) RowDivider()
                    NbListRow(
                        categoryLabel(r.category),
                        subtitle = "${r.here} ${plural(r.here, "time")} this time, ${seenLine(r.before, r.outOf)}",
                        onClick = { nav.go(Mistakes(r.category)) },
                    )
                }
            }
        }
        if (data.added > 0) Banner(
            "${data.added} ${plural(data.added, "mistake")} from this test went into your drills.", Icons.Rounded.Style,
            action = "Drill now", onAction = { nav.go(Review()) },
        )
    }
}
