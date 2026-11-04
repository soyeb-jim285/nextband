package com.soyeb.ieltspractice.feature.auth

import android.content.res.Configuration
import androidx.compose.foundation.Image
import com.soyeb.ieltspractice.design.rise
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.soyeb.ieltspractice.R
import com.soyeb.ieltspractice.design.NB
import com.soyeb.ieltspractice.design.Skill
import com.soyeb.ieltspractice.design.components.heroFill
import com.soyeb.ieltspractice.design.components.heroInk
import com.soyeb.ieltspractice.design.components.BandRing
import com.soyeb.ieltspractice.design.components.Logo
import com.soyeb.ieltspractice.design.components.BandScale
import com.soyeb.ieltspractice.design.components.BtnKind
import com.soyeb.ieltspractice.design.components.BtnSize
import com.soyeb.ieltspractice.design.components.NbButton
import com.soyeb.ieltspractice.design.components.SkillTile
import com.soyeb.ieltspractice.design.components.readable
import com.soyeb.ieltspractice.nav.Auth
import com.soyeb.ieltspractice.nav.Nav
import com.soyeb.ieltspractice.nav.Onboarding


/**
 * First launch. Deep teal header (the same one sign-in uses) with the promise and a sample band; a sheet rises over it
 * with what the app does and two ways in. "Start practising free" needs no account.
 */
@Composable
fun WelcomeScreen(nav: Nav) {
    val c = NB.colors
    val hero = heroFill()
    val onHero = heroInk().fg
    val onHeroMuted = heroInk().muted
    // At large text sizes the sample band would squeeze the headline; it is decorative, so drop it.
    val showBand = LocalDensity.current.fontScale < 1.3f
    Column(Modifier.fillMaxSize().background(c.surface)) {
        // Header and value lines scroll together; the two ways in stay pinned below, whatever the font scale.
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Box(Modifier.fillMaxWidth().background(hero)) {
                // Pushed right and faded so its ticks sit beside the copy as texture, never as a stroke through a word.
                BandScale(onHero, Modifier.align(Alignment.TopEnd).offset(x = 150.dp, y = (-30).dp).size(340.dp).alpha(0.4f))
                Column(
                    // 56 = 32 of air + the 24 the sheet below rises over.
                    Modifier.statusBarsPadding().padding(horizontal = NB.space.gutter).padding(top = 20.dp, bottom = 56.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    Logo(height = 28.dp, dark = true, modifier = Modifier.rise(0))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Column(Modifier.weight(1f).rise(1), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                "Know your band before exam day",
                                style = NB.type.heroTitle,
                                color = onHero, modifier = Modifier.semantics { heading() },
                            )
                            Text("Timed practice for all four IELTS tests, scored the way an examiner would.", style = NB.type.body, color = onHeroMuted)
                        }
                        if (showBand) {
                            Spacer(Modifier.width(12.dp))
                            // Decorative: a sample band, so the first screen shows what the app gives back.
                            Box(
                                Modifier.rise(2).clearAndSetSemantics { }.size(100.dp).clip(CircleShape).background(c.surface),
                                contentAlignment = Alignment.Center,
                            ) { BandRing(7.5, 7.0, size = 88.dp, stroke = 8.dp) }
                        }
                    }
                }
            }
            Column(
                Modifier.fillMaxWidth().offset(y = (-24).dp).clip(NB.shape.sheet).background(c.surface).readable(480.dp)
                    .padding(horizontal = NB.space.gutter).padding(top = 28.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                ValueLine(Skill.Speaking, "Speak out loud, get a real band", "Fluency, vocabulary, grammar and pronunciation scored one by one.", Modifier.rise(3))
                ValueLine(Skill.Writing, "Essays marked, mistakes shown", "Every error underlined where you made it, with the fix.", Modifier.rise(4))
                ValueLine(Skill.Reading, "Full Listening and Reading tests", "Timed like the exam, free, with every answer explained.", Modifier.rise(5))
            }
        }
        Column(Modifier.fillMaxWidth().navigationBarsPadding().readable(480.dp).padding(horizontal = NB.space.gutter).padding(top = 4.dp, bottom = 12.dp)) {
            NbButton("Start practising free", { nav.go(Onboarding) }, fill = true, size = BtnSize.Large, haptic = true)
            NbButton("I have an account", { nav.go(Auth("signin")) }, kind = BtnKind.Text, fill = true)
        }
    }
}

@Composable
private fun ValueLine(skill: Skill, title: String, body: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.Top) {
        SkillTile(skill, 44.dp)
        Spacer(Modifier.width(14.dp))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = NB.type.title3, color = NB.colors.ink)
            Text(body, style = NB.type.bodySmall, color = NB.colors.muted)
        }
    }
}
