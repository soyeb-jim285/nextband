package com.soyeb.ieltspractice.nav

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.VolunteerActivism
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.soyeb.ieltspractice.data.logic.FairUse
import com.soyeb.ieltspractice.data.logic.Gate
import com.soyeb.ieltspractice.data.logic.GateAction
import com.soyeb.ieltspractice.data.logic.GateText
import com.soyeb.ieltspractice.design.NB
import com.soyeb.ieltspractice.design.components.BtnKind
import com.soyeb.ieltspractice.design.components.BtnSize
import com.soyeb.ieltspractice.design.components.IconTile
import com.soyeb.ieltspractice.design.components.NbButton
import com.soyeb.ieltspractice.design.components.NbSheet

/** The sheet over the current screen when a test can't start, or the once-a-day fair-use note (docs/community.md). */
@Composable
fun GateHost(nav: Nav) {
    when (val g = nav.gate) {
        null -> Unit
        is Gate.FairUse -> NbSheet(
            onDismiss = nav::dismissGate,
            title = FairUse.TITLE,
            actions = {
                NbButton("Start the test", g.onStart, fill = true, size = BtnSize.Large)
                NbButton("Not now", nav::dismissGate, kind = BtnKind.Text, fill = true)
            },
        ) {
            IconTile(Icons.Rounded.VolunteerActivism, NB.colors.brandText, NB.colors.brandSoft, 56.dp)
            FairUse.paragraphs(g.quota, g.skill, g.guest).forEach { Text(it, style = NB.type.body, color = NB.colors.ink) }
        }
        is Gate.Blocked -> {
            val copy = GateText.copy(g.code, g.skill, g.tier, g.resetAt, g.keep)
            NbSheet(
                onDismiss = nav::dismissGate,
                title = copy.title,
                actions = {
                    copy.primary?.let { (label, action) ->
                        NbButton(label, { nav.dismissGate(); if (action == GateAction.Account) nav.go(Auth("signup", "Create a free account for more tests")) }, fill = true, size = BtnSize.Large)
                    }
                    NbButton(copy.secondary.first, nav::dismissGate, kind = if (copy.primary == null) BtnKind.Secondary else BtnKind.Text, fill = true, size = if (copy.primary == null) BtnSize.Large else BtnSize.Medium)
                },
            ) {
                IconTile(Icons.Rounded.HourglassTop, NB.colors.warnText, NB.colors.warnSoft, 56.dp)
                Text(copy.body, style = NB.type.body, color = NB.colors.ink)
            }
        }
        is Gate.Message -> NbSheet(
            onDismiss = nav::dismissGate,
            title = g.title,
            actions = { NbButton("OK", nav::dismissGate, kind = BtnKind.Secondary, fill = true, size = BtnSize.Large) },
        ) {
            IconTile(Icons.Rounded.Info, NB.colors.brandText, NB.colors.brandSoft, 56.dp)
            Text(g.body, style = NB.type.body, color = NB.colors.ink)
        }
    }
}
