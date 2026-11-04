package com.soyeb.ieltspractice.feature.mock

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.soyeb.ieltspractice.LocalDemo
import com.soyeb.ieltspractice.LocalGraph
import com.soyeb.ieltspractice.data.logic.MockFlow
import com.soyeb.ieltspractice.data.logic.fmt
import com.soyeb.ieltspractice.data.logic.weakest
import com.soyeb.ieltspractice.data.logic.workOn
import com.soyeb.ieltspractice.data.model.Mock
import com.soyeb.ieltspractice.data.model.MockSection
import com.soyeb.ieltspractice.design.NB
import com.soyeb.ieltspractice.design.Skill
import com.soyeb.ieltspractice.design.Tone
import com.soyeb.ieltspractice.design.bandTone
import com.soyeb.ieltspractice.design.fg
import com.soyeb.ieltspractice.design.fmtBand
import com.soyeb.ieltspractice.design.components.BandRing
import com.soyeb.ieltspractice.design.components.Banner
import com.soyeb.ieltspractice.design.components.BtnKind
import com.soyeb.ieltspractice.design.components.BtnSize
import com.soyeb.ieltspractice.design.components.LoadContent
import com.soyeb.ieltspractice.design.components.NbButton
import com.soyeb.ieltspractice.design.components.NbCard
import com.soyeb.ieltspractice.design.components.Overline
import com.soyeb.ieltspractice.design.components.PollWhileVisible
import com.soyeb.ieltspractice.design.components.PushedList
import com.soyeb.ieltspractice.design.components.PushedScreen
import com.soyeb.ieltspractice.design.components.SectionHeader
import com.soyeb.ieltspractice.design.components.SkillTile
import com.soyeb.ieltspractice.design.components.StickyActionBar
import com.soyeb.ieltspractice.design.components.Tag
import com.soyeb.ieltspractice.design.components.rememberLoad
import com.soyeb.ieltspractice.nav.MockResult
import com.soyeb.ieltspractice.nav.Nav
import com.soyeb.ieltspractice.nav.Tab

/** The finished mock: the overall band, the four section bands (tap one for its full result) and where to work next. */
@Composable
fun MockResultScreen(nav: Nav, route: MockResult) {
    val g = LocalGraph.current
    val api = g.api
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val demo = LocalDemo.current
    val me by api.me.collectAsState()
    val target = me?.settings?.targetBand ?: 7.0

    val loader = rememberLoad(route.mockId) { api.mockGet(route.mockId) }
    val m = loader.value
    // Sections may still be marking: look again every 5 s. (Not in screenshots.)
    val marking = m?.marking == true
    PollWhileVisible(marking && demo == null) { loader.reload() }

    fun share(overall: Double) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "I scored an overall band ${fmtBand(overall)} on a full mock test on NextBand. https://nextband.app")
        }
        context.startActivity(Intent.createChooser(send, "Share your result"))
    }

    PushedScreen(
        "Mock result", nav::back,
        bottomBar = {
            if (m != null) StickyActionBar {
                if (m.open && m.overall == null) {
                    NbButton("Back to your mock", { nav.toMock(m.id) }, fill = true, size = BtnSize.Large)
                } else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    m.overall?.let { o ->
                        NbButton("Share", { share(o) }, Modifier.weight(1f), kind = BtnKind.Outline, size = BtnSize.Large, icon = Icons.Rounded.Share)
                    }
                    NbButton("Back to Today", { nav.home() }, Modifier.weight(1f), size = BtnSize.Large)
                }
            }
        },
    ) { pad ->
        LoadContent(loader, Modifier.padding(pad)) { mock ->
            PushedList(pad) {
                if (mock.open && mock.overall == null) item {
                    Banner("This mock is still open. Finish the rest to get your overall band.", Icons.Rounded.Info)
                }
                item { OverallHero(mock, target) }
                item { SectionHeader("By section") }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        mock.sections.chunked(2).forEach { pair ->
                            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                pair.forEach { s -> SectionCell(s, target, Modifier.weight(1f).fillMaxHeight()) { openSection(nav, scope, api, s) } }
                            }
                        }
                    }
                }
                val weak = MockFlow.weakest(mock)
                if (weak != null && mock.sections.count { it.band != null } >= 2) {
                    item { SectionHeader("What to work on") }
                    item {
                        val sk = Skill.of(weak.skill)
                        NbCard {
                            Row(verticalAlignment = Alignment.Top) {
                                if (sk != null) { SkillTile(sk, 40.dp); Spacer(Modifier.width(12.dp)) }
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("Start with ${MockFlow.name(weak.skill)}", style = NB.type.title3, color = NB.colors.ink)
                                    Text("It was your lowest section at ${fmtBand(weak.band)}. ${MockFlow.workOn(weak.skill)}", style = NB.type.bodySmall, color = NB.colors.muted)
                                }
                            }
                            val tab = Tab.entries.firstOrNull { it.skill?.key == weak.skill }
                            if (tab != null) NbButton("Practise ${MockFlow.name(weak.skill)}", { nav.openTab(tab) }, kind = BtnKind.Secondary, modifier = Modifier.padding(top = 12.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OverallHero(m: Mock, target: Double) {
    val o = m.overall
    NbCard {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Overline("Overall band")
            BandRing(o, target, size = 140.dp, stroke = 12.dp, label = "Overall")
            if (o != null) {
                val diff = o - target
                Text(
                    if (diff >= 0) "At or above your ${fmtBand(target)} target" else "${fmt(-diff)} below your ${fmtBand(target)} target",
                    style = NB.type.title3, color = bandTone(o, target).fg(), textAlign = TextAlign.Center,
                )
            }
            Text(MockFlow.overallNote(m, target), style = NB.type.bodySmall, color = NB.colors.muted, textAlign = TextAlign.Center)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Tag(if (m.variant == "general") "General Training" else "Academic")
                m.ref?.takeIf { it.isNotBlank() }?.let { Tag(it) }
            }
        }
    }
}

@Composable
private fun SectionCell(s: MockSection, target: Double, modifier: Modifier, onOpen: () -> Unit) {
    val sk = Skill.of(s.skill)
    val linked = s.attemptId != null && MockFlow.isFinished(s.state)
    val status = MockFlow.stateLabel(s.skill, s.state).first
    NbCard(modifier, onClick = if (linked) onOpen else null) {
        Column(
            Modifier.fillMaxWidth().semantics(mergeDescendants = true) {
                contentDescription = "${MockFlow.name(s.skill)}, " + (s.band?.let { "band ${fmtBand(it)}" } ?: status)
            },
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (sk != null) SkillTile(sk, 36.dp)
            Text(MockFlow.name(s.skill), style = NB.type.label, color = NB.colors.muted)
            val band = s.band
            if (band != null) Text(fmtBand(band), style = NB.type.band(32), color = bandTone(band, target).fg())
            else Text(status, style = NB.type.bodySmall, color = NB.colors.muted)
            if (linked) Text("View result", style = NB.type.caption, color = NB.colors.brandText)
        }
    }
}
