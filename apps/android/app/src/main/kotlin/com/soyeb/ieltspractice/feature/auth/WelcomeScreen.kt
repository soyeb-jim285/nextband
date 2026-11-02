package com.soyeb.ieltspractice.feature.auth

import android.content.res.Configuration
import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.soyeb.ieltspractice.R
import com.soyeb.ieltspractice.design.NB
import com.soyeb.ieltspractice.design.Skill
import com.soyeb.ieltspractice.design.components.BandRing
import com.soyeb.ieltspractice.design.components.BtnKind
import com.soyeb.ieltspractice.design.components.BtnSize
import com.soyeb.ieltspractice.design.components.NbButton
import com.soyeb.ieltspractice.design.components.SkillTile
import com.soyeb.ieltspractice.design.components.StickyActionBar
import com.soyeb.ieltspractice.nav.Auth
import com.soyeb.ieltspractice.nav.Nav
import com.soyeb.ieltspractice.nav.Onboarding

/** The NextBand wordmark. Its colours come from values(-night)/colors.xml, resolved for the app's theme (Settings can differ from the device). */
@Composable
fun Logo(modifier: Modifier = Modifier, height: Dp = 26.dp, dark: Boolean = NB.colors.isDark) {
    val context = LocalContext.current
    val vector = remember(context, dark) {
        val c = Configuration(context.resources.configuration)
        c.uiMode = (c.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or (if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO)
        ImageVector.vectorResource(null, context.createConfigurationContext(c).resources, R.drawable.logo_wordmark)
    }
    Image(vector, "NextBand", modifier.height(height))
}

/** First launch: the brand, three lines on what the app does, and two ways in. "Start practising free" needs no account. */
@Composable
fun WelcomeScreen(nav: Nav) {
    val c = NB.colors
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(c.brandSoft, c.bg)))) {
        Column(Modifier.fillMaxSize()) {
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).statusBarsPadding().padding(horizontal = NB.space.gutter, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(28.dp),
            ) {
                Logo(height = 30.dp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Know your band before exam day", style = NB.type.headline, color = c.ink, modifier = Modifier.semantics { heading() })
                        Text("Timed practice for all four IELTS tests, scored the way an examiner would.", style = NB.type.body, color = c.muted)
                    }
                    Spacer(Modifier.width(12.dp))
                    // Decorative: a sample band, so the first screen shows what the app gives back.
                    Box(Modifier.clearAndSetSemantics { }) { BandRing(7.5, 7.0, size = 104.dp, stroke = 9.dp) }
                }
                Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    ValueLine(Skill.Speaking, "Speak out loud, get a real band", "Fluency, vocabulary, grammar and pronunciation scored one by one.")
                    ValueLine(Skill.Writing, "Essays marked, mistakes shown", "Every error underlined where you made it, with the fix.")
                    ValueLine(Skill.Reading, "Full Listening and Reading tests", "Timed like the exam, free, with every answer explained.")
                }
            }
            StickyActionBar {
                NbButton("Start practising free", { nav.go(Onboarding) }, fill = true, size = BtnSize.Large, haptic = true)
                NbButton("I have an account", { nav.go(Auth("signin")) }, kind = BtnKind.Text, fill = true)
            }
        }
    }
}

@Composable
private fun ValueLine(skill: Skill, title: String, body: String) {
    Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.Top) {
        SkillTile(skill, 44.dp)
        Spacer(Modifier.width(14.dp))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = NB.type.title3, color = NB.colors.ink)
            Text(body, style = NB.type.bodySmall, color = NB.colors.muted)
        }
    }
}
