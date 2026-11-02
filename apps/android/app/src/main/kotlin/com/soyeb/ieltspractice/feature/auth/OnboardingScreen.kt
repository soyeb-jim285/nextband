package com.soyeb.ieltspractice.feature.auth

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.soyeb.ieltspractice.LocalDemo
import com.soyeb.ieltspractice.AppGraph
import com.soyeb.ieltspractice.LocalGraph
import com.soyeb.ieltspractice.design.LocalReducedMotion
import com.soyeb.ieltspractice.design.NB
import com.soyeb.ieltspractice.design.components.BandRing
import com.soyeb.ieltspractice.design.components.BtnKind
import com.soyeb.ieltspractice.design.components.BtnSize
import com.soyeb.ieltspractice.design.components.CardTone
import com.soyeb.ieltspractice.design.components.IconTile
import com.soyeb.ieltspractice.design.components.LocalSnack
import com.soyeb.ieltspractice.design.components.NbButton
import com.soyeb.ieltspractice.design.components.NbCard
import com.soyeb.ieltspractice.design.components.NbChip
import com.soyeb.ieltspractice.design.components.NbIconButton
import com.soyeb.ieltspractice.design.components.StickyActionBar
import com.soyeb.ieltspractice.design.fmtBand
import com.soyeb.ieltspractice.feature.today.daysToExam
import com.soyeb.ieltspractice.feature.today.examChipText
import com.soyeb.ieltspractice.feature.you.ExamDateDialog
import com.soyeb.ieltspractice.feature.you.examDateText
import com.soyeb.ieltspractice.feature.you.targetBands
import com.soyeb.ieltspractice.nav.Nav
import com.soyeb.ieltspractice.nav.TodayTab
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.time.LocalDate

private const val PAGES = 3

/**
 * Three steps after the first sign-in (or from Welcome as a guest): test type, target band, exam date. Nothing here needs an account:
 * the choices are kept on the device, and also saved to the account when there is a session.
 */
@Composable
fun OnboardingScreen(nav: Nav) {
    val graph = LocalGraph.current
    val api = graph.api
    val prefs = graph.prefs
    val scope = rememberCoroutineScope()
    val snack = LocalSnack.current
    val reduced = LocalReducedMotion.current
    val hf = LocalHapticFeedback.current
    val me by api.me.collectAsState()
    val signedIn by api.signedIn.collectAsState()

    val startPage = when (LocalDemo.current?.tab) { "Band" -> 1; "Date" -> 2; else -> 0 }
    val pager = rememberPagerState(initialPage = startPage, pageCount = { PAGES })
    var type by remember { mutableStateOf("academic") }
    var band by remember(me?.user?.id) { mutableStateOf(me?.settings?.targetBand ?: 7.0) }
    var date by remember(me?.user?.id) { mutableStateOf(me?.settings?.examDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }) }
    var picker by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val page = pager.currentPage

    fun scrollTo(i: Int) { scope.launch { if (reduced) pager.scrollToPage(i) else pager.animateScrollToPage(i) } }

    /** Keep the choices (device, and the account when there is a session), then open Today. */
    fun finish(save: Boolean) {
        if (busy) return
        scope.launch {
            busy = true
            if (save) {
                prefs.setTestType(type)
                // No session yet: kept on the device and sent when one exists (AppGraph).
                suspend fun queue() {
                    prefs.setString(AppGraph.GOAL_BAND, band.toString())
                    prefs.setString(AppGraph.GOAL_DATE, date?.toString())
                }
                if (!signedIn) queue()
                else try {
                    api.saveSettings(targetBand = band, examDate = date?.toString(), clearExamDate = date == null && me?.settings?.examDate != null)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    queue()
                    graph.scope.launch { snack.showSnackbar("Couldn't save your goal yet. It will sync next time you're online.") }
                }
            }
            prefs.setOnboardingDone()
            nav.reset(TodayTab)
        }
    }

    fun back() { if (page > 0) scrollTo(page - 1) else nav.back() }
    BackHandler { back() }

    Column(Modifier.fillMaxSize().background(NB.colors.bg)) {
        Row(Modifier.statusBarsPadding().padding(start = 4.dp, end = 8.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            NbIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Back", ::back)
            Spacer(Modifier.weight(1f))
            Dots(page)
            Spacer(Modifier.weight(1f))
            NbButton("Skip", { finish(save = false) }, kind = BtnKind.Text, size = BtnSize.Small, enabled = !busy)
        }
        HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth(), verticalAlignment = Alignment.Top) { p ->
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = NB.space.gutter, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                when (p) {
                    0 -> TypePage(type) { type = it }
                    1 -> BandPage(band, onChange = { band = it; hf.performHapticFeedback(HapticFeedbackType.TextHandleMove) })
                    else -> DatePage(date, { picker = true }, { date = null })
                }
            }
        }
        StickyActionBar {
            NbButton(
                if (page < PAGES - 1) "Continue" else "Start practising",
                { if (page < PAGES - 1) scrollTo(page + 1) else finish(save = true) },
                fill = true, size = BtnSize.Large, loading = busy, haptic = true,
            )
        }
    }

    if (picker) ExamDateDialog(date?.toString(), onDismiss = { picker = false }, onSave = { date = it; picker = false })
}

@Composable
private fun Dots(page: Int) {
    val reduced = LocalReducedMotion.current
    Row(
        Modifier.semantics { contentDescription = "Step ${page + 1} of $PAGES" },
        horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(PAGES) { i ->
            val w by animateDpAsState(if (i == page) 24.dp else 8.dp, if (reduced) tween(0) else tween(220), label = "dot")
            Box(Modifier.width(w).height(8.dp).clip(CircleShape).background(if (i == page) NB.colors.brand else NB.colors.surface3))
        }
    }
}

@Composable
private fun Heading(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = NB.type.headline, color = NB.colors.ink, modifier = Modifier.semantics { heading() })
        Text(body, style = NB.type.body, color = NB.colors.muted)
    }
}

@Composable
private fun TypePage(type: String, onSelect: (String) -> Unit) {
    Heading("Which test are you taking?", "It decides which Reading and Writing Task 1 you see first. You can change it any time.")
    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TypeCard("Academic", "For university study and professional registration.", type == "academic") { onSelect("academic") }
        TypeCard("General Training", "For work, training and migration.", type == "general") { onSelect("general") }
    }
}

@Composable
private fun TypeCard(title: String, body: String, selected: Boolean, onClick: () -> Unit) {
    val c = NB.colors
    NbCard(
        Modifier.selectable(selected, role = Role.RadioButton, onClick = onClick).let { if (selected) it.border(2.dp, c.brand, NB.shape.card) else it },
        tone = if (selected) CardTone.Hero else CardTone.Default,
        padding = androidx.compose.foundation.layout.PaddingValues(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = NB.type.title1, color = c.ink)
                Text(body, style = NB.type.bodySmall, color = c.muted)
            }
            if (selected) Icon(Icons.Rounded.CheckCircle, null, tint = c.brand, modifier = Modifier.size(28.dp))
        }
    }
}

@Composable
private fun BandPage(band: Double, onChange: (Double) -> Unit) {
    Heading("What band are you aiming for?", "Your results show green at or above this band. Most universities ask for 6.5 to 7.0 overall.")
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
        BandRing(band, null, size = 220.dp, stroke = 14.dp, label = "Target band")
        Row(horizontalArrangement = Arrangement.spacedBy(32.dp), verticalAlignment = Alignment.CenterVertically) {
            NbIconButton(Icons.Rounded.Remove, "Lower target band", { onChange((band - 0.5).coerceAtLeast(targetBands.first())) }, tonal = true, enabled = band > targetBands.first())
            NbIconButton(Icons.Rounded.Add, "Raise target band", { onChange((band + 0.5).coerceAtMost(targetBands.last())) }, tonal = true, enabled = band < targetBands.last())
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(6.0, 6.5, 7.0, 7.5, 8.0).forEach { b -> NbChip(fmtBand(b), selected = b == band, onClick = { onChange(b) }) }
        }
    }
}

@Composable
private fun DatePage(date: LocalDate?, onPick: () -> Unit, onNone: () -> Unit) {
    val c = NB.colors
    Heading("When is your exam?", "Optional. We count the days down on Today so you can pace your practice.")
    NbChip("Not booked yet", selected = date == null, onClick = onNone)
    NbCard(onClick = onPick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(Icons.Rounded.Event, c.brandText, c.brandSoft, 44.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(date?.let { examDateText(it.toString()) } ?: "Choose a date", style = NB.type.title3, color = c.ink)
                val days = date?.let { daysToExam(it.toString()) }
                Text(if (days != null) examChipText(days) else "Tap to open the calendar", style = NB.type.bodySmall, color = if (days != null) c.brandText else c.muted)
            }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = c.faint)
        }
    }
}
