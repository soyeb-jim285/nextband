package com.soyeb.ieltspractice.feature.study

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Style
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.soyeb.ieltspractice.LocalDemo
import com.soyeb.ieltspractice.LocalGraph
import com.soyeb.ieltspractice.data.model.DueCard
import com.soyeb.ieltspractice.data.net.Api
import com.soyeb.ieltspractice.design.LocalReducedMotion
import com.soyeb.ieltspractice.design.NB
import com.soyeb.ieltspractice.design.Tone
import com.soyeb.ieltspractice.design.components.Banner
import com.soyeb.ieltspractice.design.components.BtnKind
import com.soyeb.ieltspractice.design.components.BtnSize
import com.soyeb.ieltspractice.design.components.EmptyState
import com.soyeb.ieltspractice.design.components.ErrorState
import com.soyeb.ieltspractice.design.components.IconTile
import com.soyeb.ieltspractice.design.components.Meter
import com.soyeb.ieltspractice.design.components.NbButton
import com.soyeb.ieltspractice.design.components.NbCard
import com.soyeb.ieltspractice.design.components.Overline
import com.soyeb.ieltspractice.design.components.PushedScreen
import com.soyeb.ieltspractice.design.components.SkeletonList
import com.soyeb.ieltspractice.design.components.StickyActionBar
import com.soyeb.ieltspractice.design.components.Stat
import com.soyeb.ieltspractice.design.components.Tag
import com.soyeb.ieltspractice.nav.Mistakes
import com.soyeb.ieltspractice.nav.Nav
import com.soyeb.ieltspractice.nav.Tab
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Everything the review session remembers: the due batch, where we are, and what was graded. */
@Stable
internal class ReviewSession(
    private val api: Api,
    private val scope: CoroutineScope,
    private val revealOnLoad: Boolean = false,
    reviewed0: Int = 0,
) {
    var loading by mutableStateOf(true)
    var error by mutableStateOf<String?>(null)
    var queue by mutableStateOf<List<DueCard>>(emptyList())
    var index by mutableIntStateOf(0)
    var dueTotal by mutableIntStateOf(0)
    var batch by mutableIntStateOf(0)
    var deck by mutableStateOf<Int?>(null)
    /** Graded this visit, so the end state can say so after the refetch comes back empty. */
    var reviewed by mutableIntStateOf(reviewed0)
    var revealed by mutableStateOf(false)
    var grading by mutableStateOf(false)
    var gradeError by mutableStateOf<String?>(null)

    val card: DueCard? get() = queue.getOrNull(index)
    /** Cards in this session: what is due, plus every Again that came back round. */
    val total: Int get() = maxOf(1, dueTotal + queue.size - batch)

    suspend fun load() {
        loading = queue.isEmpty()
        try {
            val r = api.dueCards()
            queue = r.list; batch = r.list.size; dueTotal = r.dueTotal; deck = r.deck
            index = 0; revealed = revealOnLoad; error = null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.message?.takeIf { it.isNotBlank() } ?: "Something went wrong. Check your connection and try again."
        }
        loading = false
    }

    fun grade(g: Int) {
        val c = card ?: return
        if (grading) return
        grading = true
        gradeError = null
        scope.launch {
            try {
                api.reviewCard(c.id, g)
                revealed = false; reviewed++; index++
                // Again goes to the back of this session (a learning step); otherwise ask again once the batch runs out.
                if (g == 1) queue = queue + c else if (index >= queue.size) load()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                gradeError = e.message?.takeIf { it.isNotBlank() } ?: "Couldn't save that grade. Try again."
            } finally {
                grading = false
            }
        }
    }
}

@Composable
fun ReviewScreen(nav: Nav) {
    val g = LocalGraph.current
    val account by g.api.hasAccount.collectAsState()
    val tab = LocalDemo.current?.tab
    val scope = rememberCoroutineScope()
    val s = remember { ReviewSession(g.api, scope, revealOnLoad = tab == "Revealed", reviewed0 = if (tab == "Done") 24 else 0) }
    LaunchedEffect(account) { if (account) s.load() }

    PushedScreen(
        "Review", nav::back,
        bottomBar = {
            if (account && !s.loading && s.error == null) {
                if (s.card != null) GradeBar(s) else if (s.reviewed > 0 || (s.deck ?: 0) > 0) DoneBar(nav)
            }
        },
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            val card = s.card
            when {
                !account -> GuestGate(Icons.Rounded.Style, "Your review deck", "Create an account to turn your corrections into flashcards that come back when you are about to forget them.", nav, "Create an account to build your review deck")
                s.loading && card == null -> SkeletonList()
                s.error != null && card == null -> ErrorState(s.error.orEmpty(), { scope.launch { s.load() } })
                card != null -> CardStage(s, card)
                s.reviewed == 0 && (s.deck ?: 0) == 0 -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
                    EmptyState(
                        Icons.Rounded.Style, "No cards yet",
                        "Cards come from your results. Open Mistakes and tap Add to review deck on the corrections you want to remember. They come back here on a spaced schedule.",
                        action = "Open mistakes", onAction = { nav.go(Mistakes()) },
                    )
                }
                else -> DoneStage(s)
            }
        }
    }
}

@Composable
private fun CardStage(s: ReviewSession, card: DueCard) {
    val g = LocalGraph.current
    val left = maxOf(0, s.total - s.index)
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = NB.space.gutter, vertical = NB.space.md),
        verticalArrangement = Arrangement.spacedBy(NB.space.lg),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("$left ${plural(left, "card")} left", style = NB.type.label, color = NB.colors.ink, modifier = Modifier.weight(1f))
                Text("${s.index} of ${s.total}", style = NB.type.num, color = NB.colors.muted)
            }
            Meter(s.index.toDouble(), Modifier.fillMaxWidth().semantics { contentDescription = "Session progress" }, max = s.total.toDouble(), height = 6.dp)
        }
        Flashcard(card, s.revealed, onFlip = { s.revealed = !s.revealed }, onHear = { g.tts.speak(it) })
        s.gradeError?.let { Banner("Couldn't save that grade. $it", Icons.Rounded.Info, tone = Tone.Bad) }
        Text(
            if (s.revealed) "Be honest: cards you find hard come back sooner. Again shows the card once more before you finish."
            else "Say or write your answer first, then turn the card.",
            style = NB.type.bodySmall, color = NB.colors.muted,
        )
    }
}

/** The card. Turns around on tap (rotationY); with "remove animations" on it crossfades instead. */
@Composable
private fun Flashcard(card: DueCard, revealed: Boolean, onFlip: () -> Unit, onHear: (String) -> Unit) {
    val reduced = LocalReducedMotion.current
    val density = LocalDensity.current.density
    val click = Modifier.clickable(role = Role.Button, onClickLabel = if (revealed) "Show the question" else "Show the answer", onClick = onFlip)
    if (reduced) {
        Crossfade(revealed, Modifier.fillMaxWidth().then(click), animationSpec = tween(150), label = "card") { back ->
            NbCard { Face(card, back, onHear) }
        }
    } else {
        val rot by animateFloatAsState(if (revealed) 180f else 0f, tween(380), label = "flip")
        Box(Modifier.fillMaxWidth().graphicsLayer { rotationY = rot; cameraDistance = 14f * density }.then(click)) {
            // Past 90 degrees the back is showing: turn it once more so its text is not mirrored.
            NbCard(Modifier.graphicsLayer { rotationY = if (rot > 90f) 180f else 0f }) { Face(card, rot > 90f, onHear) }
        }
    }
}

@Composable
private fun Face(card: DueCard, back: Boolean, onHear: (String) -> Unit) {
    val (label, front) = cleanFront(card)
    Column(Modifier.fillMaxWidth().heightIn(min = 280.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        sourceLabel[card.source]?.let { Tag(it) }
        if (!back) {
            sourcePrompt[card.source]?.let { Text(it, style = NB.type.bodySmall, color = NB.colors.muted) }
            label?.let { Text(it, style = NB.type.label, color = NB.colors.brandText) }
            Text(front, style = NB.type.readingTitle, color = NB.colors.ink)
            // The spelling stays hidden until the card is turned.
            heardWord(card)?.let { w -> NbButton("Hear the word", { onHear(w) }, kind = BtnKind.Secondary, icon = Icons.AutoMirrored.Rounded.VolumeUp) }
            Spacer(Modifier.weight(1f))
            Text("Tap the card to show the answer", style = NB.type.caption, color = NB.colors.muted)
        } else {
            Overline("Answer")
            Text(card.back, style = NB.type.reading, color = NB.colors.ink)
            Spacer(Modifier.weight(1f))
            Overline("Question")
            Text(front, style = NB.type.readingSmall, color = NB.colors.muted)
        }
    }
}

@Composable
private fun GradeBar(s: ReviewSession) {
    val hf = LocalHapticFeedback.current
    val card = s.card ?: return
    StickyActionBar {
        if (!s.revealed) {
            NbButton("Show answer", { s.revealed = true }, fill = true, size = BtnSize.Large)
        } else {
            Row(Modifier.fillMaxWidth().semantics { contentDescription = "How well did you remember?" }, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                grades.forEach { gr ->
                    val tone = when (gr.value) { 1 -> NB.colors.badText; 3 -> NB.colors.warnText; 4 -> NB.colors.goodText; else -> NB.colors.brandText }
                    GradeButton(gr.label, hint(card, gr.value), tone, !s.grading, Modifier.weight(1f)) {
                        hf.performHapticFeedback(HapticFeedbackType.LongPress)
                        s.grade(gr.value)
                    }
                }
            }
        }
    }
}

/** One grade: the word in its tone, the next interval under it. Neutral fill so the bar has no second primary. */
@Composable
private fun GradeButton(label: String, next: String, tone: Color, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick, enabled = enabled, shape = NB.shape.tile, color = NB.colors.surface2, contentColor = NB.colors.ink,
        modifier = modifier.heightIn(min = 56.dp).semantics { contentDescription = "$label, next review ${next.lowercase()}" },
    ) {
        Column(Modifier.padding(vertical = 8.dp, horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(label, style = NB.type.label, color = tone, maxLines = 1)
            Text(next, style = NB.type.caption, color = NB.colors.muted, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun DoneStage(s: ReviewSession) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        IconTile(Icons.Rounded.Check, NB.colors.goodText, NB.colors.goodSoft, 80.dp)
        Spacer(Modifier.padding(top = 20.dp))
        Text("All caught up", style = NB.type.headline, color = NB.colors.ink, textAlign = TextAlign.Center)
        Text(
            if (s.reviewed > 0) "${s.reviewed} reviewed" else "Nothing is due right now",
            style = NB.type.title3, color = NB.colors.goodText, textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp),
        )
        Text(
            "Your cards come back when they are due, so a short session tomorrow keeps them fresh. Add more from Mistakes any time.",
            style = NB.type.bodySmall, color = NB.colors.muted, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 12.dp),
        )
        s.deck?.takeIf { it > 0 }?.let {
            Stat("$it", "${plural(it, "card")} in your deck", Modifier.padding(top = 20.dp))
        }
    }
}

@Composable
private fun DoneBar(nav: Nav) {
    StickyActionBar {
        NbButton("Back to Today", { nav.openTab(Tab.Today) }, fill = true, size = BtnSize.Large)
        NbButton("Open mistakes", { nav.go(Mistakes()) }, kind = BtnKind.Text, fill = true)
    }
}
