package com.soyeb.ieltspractice.feature.study

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Style
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.soyeb.ieltspractice.LocalDemo
import com.soyeb.ieltspractice.LocalGraph
import com.soyeb.ieltspractice.data.logic.categoryLabel
import com.soyeb.ieltspractice.data.logic.formatDate
import com.soyeb.ieltspractice.data.model.DueCard
import com.soyeb.ieltspractice.data.net.Api
import com.soyeb.ieltspractice.design.NB
import com.soyeb.ieltspractice.design.components.BtnKind
import com.soyeb.ieltspractice.design.components.BtnSize
import com.soyeb.ieltspractice.design.components.EmptyState
import com.soyeb.ieltspractice.design.components.ErrorState
import com.soyeb.ieltspractice.design.components.IconTile
import com.soyeb.ieltspractice.design.components.LocalSnack
import com.soyeb.ieltspractice.design.components.Meter
import com.soyeb.ieltspractice.design.components.NbButton
import com.soyeb.ieltspractice.design.components.NbCard
import com.soyeb.ieltspractice.design.components.NbTextField
import com.soyeb.ieltspractice.design.components.Overline
import com.soyeb.ieltspractice.design.components.PushedScreen
import com.soyeb.ieltspractice.design.components.RowDivider
import com.soyeb.ieltspractice.design.components.RowGroup
import com.soyeb.ieltspractice.design.components.SkeletonList
import com.soyeb.ieltspractice.design.components.StickyActionBar
import com.soyeb.ieltspractice.design.components.rememberLoad
import com.soyeb.ieltspractice.design.components.readable
import com.soyeb.ieltspractice.media.AudioPlayer
import com.soyeb.ieltspractice.nav.CoachRoute
import com.soyeb.ieltspractice.nav.Mistakes
import com.soyeb.ieltspractice.nav.Nav
import com.soyeb.ieltspractice.nav.Review
import com.soyeb.ieltspractice.nav.WritingTask
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * One drill session: up to [SESSION] due cards (one pattern, or ahead of schedule), each typed and checked, then Got it / Not yet.
 * Not yet re-queues the card at the end (a learning step); only "Keep going" fetches more.
 */
@Stable
internal class DrillSession(private val api: Api, private val scope: CoroutineScope, private val route: Review) {
    var loading by mutableStateOf(true)
    var error by mutableStateOf<String?>(null)
    /** The batch the server sent (up to 50). */
    var batch by mutableStateOf<List<DueCard>>(emptyList())
    var dueTotal by mutableIntStateOf(0)
    var deck by mutableIntStateOf(0)
    var queue by mutableStateOf<List<DueCard>>(emptyList())
    var index by mutableIntStateOf(0)
    /** Every verdict this session, in order (a re-queued card appears again). */
    var done by mutableStateOf<List<Pair<DueCard, Boolean>>>(emptyList())
    var ended by mutableStateOf(false)
    var typed by mutableStateOf("")
    var checked by mutableStateOf(false)
    var busy by mutableStateOf(false)

    val card: DueCard? get() = if (ended) null else queue.getOrNull(index)
    val finished: Boolean get() = card == null && done.isNotEmpty()
    /** Ahead of schedule every card counts, so "more" is what this visit has not drilled yet. */
    val more: Int get() = if (route.ahead) batch.count { c -> done.none { it.first.id == c.id } } else dueTotal

    suspend fun load(skip: Set<String> = emptySet()) {
        loading = queue.isEmpty()
        try {
            val r = api.dueCards(route.pattern, route.ahead)
            batch = r.list; dueTotal = r.dueTotal; deck = r.deck ?: 0
            queue = r.list.filter { it.id !in skip }.take(SESSION); index = 0; error = null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.message?.takeIf { it.isNotBlank() } ?: "Something went wrong. Check your connection and try again."
        }
        loading = false
    }

    /** After the last card: the due count again, so "Keep going" is honest. */
    suspend fun refreshCount() {
        try { dueTotal = api.dueCards(route.pattern, route.ahead).dueTotal } catch (e: CancellationException) { throw e } catch (e: Exception) { }
    }

    fun keepGoing() {
        val drilled = if (route.ahead) done.map { it.first.id }.toSet() else emptySet()
        done = emptyList(); ended = false; queue = emptyList()
        scope.launch { load(drilled) }
    }

    private fun next() { typed = ""; checked = false }

    fun grade(got: Boolean, onError: (String) -> Unit) {
        val c = card ?: return
        if (busy) return
        busy = true
        scope.launch {
            try {
                val updated = api.reviewCard(c.id, if (got) GOT_IT else NOT_YET)
                done = done + (c to got)
                if (!got) queue = queue + c.copy(ease = updated.ease, interval = updated.interval, reps = updated.reps)
                index++; next()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onError("Couldn't save that: ${e.message ?: "try again."}")
            } finally {
                busy = false
            }
        }
    }

    fun skip(onDone: (String) -> Unit) {
        val c = card ?: return
        if (busy) return
        busy = true
        scope.launch {
            try {
                api.skipCard(c.id)
                queue = queue.filterIndexed { k, _ -> k != index }; next()
                onDone("Skipped. That card won't come back.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onDone("Couldn't skip it: ${e.message ?: "try again."}")
            } finally {
                busy = false
            }
        }
    }
}

class DrillHolder : ViewModel() { internal var session: DrillSession? = null }

@Composable
fun ReviewScreen(nav: Nav, route: Review) {
    val g = LocalGraph.current
    val account by g.api.hasAccount.collectAsState()
    val tab = LocalDemo.current?.tab
    val scope = rememberCoroutineScope()
    val snack = LocalSnack.current
    // Held by the back-stack entry, so rotating keeps the queue, the typed answer and the checked state.
    val holder = viewModel<DrillHolder>()
    val s = holder.session ?: DrillSession(g.api, holder.viewModelScope, route).also { holder.session = it }
    val say: (String) -> Unit = { m -> scope.launch { snack.currentSnackbarData?.dismiss(); snack.showSnackbar(m) } }
    LaunchedEffect(account, route) {
        if (!account || !s.loading) return@LaunchedEffect // already loaded (a rotation): keep the session as it is
        s.load()
        // Screenshots: a checked answer with a wrong attempt.
        if (tab == "Checked" && s.card != null) { s.typed = "I living there since 2015"; s.checked = true }
    }
    LaunchedEffect(s.finished) { if (s.finished) s.refreshCount() }

    PushedScreen(
        route.pattern?.let(::categoryLabel) ?: "Drill", nav::back,
        actions = {
            if (s.card != null && s.done.isNotEmpty()) NbButton("End", { s.ended = true }, kind = BtnKind.Text, size = BtnSize.Small)
        },
        bottomBar = {
            val card = s.card
            if (account && card != null) VerdictBar(s, card, say)
            else if (account && s.finished) StickyActionBar {
                if (s.more > 0) NbButton("Keep going · ${if (route.ahead) "${s.more} more" else "${s.more} ${plural(s.more, "card")} due"}", s::keepGoing, fill = true, size = BtnSize.Large)
                NbButton("Back to your plan", { nav.go(CoachRoute) }, kind = if (s.more > 0) BtnKind.Text else BtnKind.Primary, fill = true, size = if (s.more > 0) BtnSize.Medium else BtnSize.Large)
            }
        },
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            val card = s.card
            when {
                !account -> GuestGate(Icons.Rounded.Style, "Your drills", "Create an account and the mistakes worth practising from each test become short drills that come back just before you would forget them.", nav, "Create an account to drill your mistakes")
                s.loading && card == null -> SkeletonList()
                s.error != null && card == null -> ErrorState(s.error.orEmpty(), { scope.launch { s.load() } })
                card != null -> key(card.id, s.index) { CardStage(s, card, say) }
                s.finished -> Summary(s.done)
                else -> NothingDue(nav, route, s.deck)
            }
        }
    }
}

private val ASK = mapOf(
    "spelling" to "Type the word, spelt correctly.",
    "mistake" to "Rewrite it correctly.",
    "fix" to "How would you improve this sentence?",
    "vocab" to "Give a better word or phrase.",
)

@Composable
private fun CardStage(s: DrillSession, card: DueCard, say: (String) -> Unit) {
    val g = LocalGraph.current
    val demo = LocalDemo.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val (label, text) = promptOf(card)
    val (answer, note) = answerOf(card.back)
    val spelling = card.category == "spelling"
    val heard = isHeard(card)
    val tried = s.checked && s.typed.isNotBlank()
    val right = tried && sameAnswer(s.typed, answer)
    // The word in the test speaker's voice, cut from the recording; the device voice when it can't be located.
    val clip = rememberLoad(card.id) { if (heard && card.attemptId != null && demo == null) g.api.cardClip(card.id) else null }
    val player = remember { if (heard && demo == null) AudioPlayer(context) else null }
    DisposableEffect(player) { onDispose { player?.release() } }

    fun hear() {
        scope.launch {
            val c = clip.value
            if (player != null && c != null) {
                player.load(c.url); player.seekTo((c.from * 1000).toLong()); player.play()
                delay(((c.to - c.from) * 1000).toLong()); player.pause()
                return@launch
            }
            g.tts.speak(answer)
            delay(1500)
            if (g.tts.unavailable) say("This phone has no English voice to read the word. Use the letters shown, or replay the recording from the test's review.")
        }
    }

    val left = s.queue.size - s.index
    Column(
        Modifier.fillMaxSize().readable().verticalScroll(rememberScrollState()).padding(horizontal = NB.space.gutter, vertical = NB.space.md),
        verticalArrangement = Arrangement.spacedBy(NB.space.lg),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("$left ${plural(left, "card")} left", style = NB.type.label, color = NB.colors.ink, modifier = Modifier.weight(1f))
                Text("${s.index} of ${s.queue.size}", style = NB.type.num, color = NB.colors.muted)
            }
            Meter(s.index.toDouble(), Modifier.fillMaxWidth().semantics { contentDescription = "Session progress" }, max = maxOf(1, s.queue.size).toDouble(), height = 6.dp)
        }
        NbCard {
            Column(Modifier.fillMaxWidth().heightIn(min = 240.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val origin = card.origin?.let { o -> "${if (o.skill == "writing") "Writing Task" else "Speaking Part"} ${o.part}, ${formatDate(o.at)}" }
                    Text(
                        buildAnnotatedString {
                            withStyle(SpanStyle(color = NB.colors.ink, fontWeight = FontWeight.Medium)) {
                                append(card.category?.let(::categoryLabel) ?: if (card.source == "fix") "Fix from your results" else "Vocabulary")
                            }
                            origin?.let { append(" · $it") }
                        },
                        style = NB.type.caption, color = NB.colors.muted, maxLines = 2, modifier = Modifier.weight(1f),
                    )
                    NbButton("Skip", { s.skip(say) }, kind = BtnKind.Text, size = BtnSize.Small, enabled = !s.busy)
                }
                Text(ASK[if (spelling) "spelling" else card.source ?: "mistake"] ?: ASK.getValue("mistake"), style = NB.type.bodySmall, color = NB.colors.muted)
                label?.let { Text(it, style = NB.type.label, color = NB.colors.brandText) }
                Text(text, style = if (text.length > 90) NB.type.reading else NB.type.readingTitle, color = NB.colors.ink)
                if (heard) NbButton("Hear the word", ::hear, kind = BtnKind.Secondary, icon = Icons.AutoMirrored.Rounded.VolumeUp)
                NbTextField(
                    s.typed, { s.typed = it }, if (spelling) "Your spelling" else "Your correction (optional)",
                    singleLine = spelling, enabled = !s.checked,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false, // the keyboard must not fix it for them
                        imeAction = if (spelling) ImeAction.Done else ImeAction.Default,
                    ),
                    keyboardActions = KeyboardActions(onDone = { s.checked = true }),
                )
                AnimatedVisibility(s.checked, enter = fadeIn()) {
                    Column(Modifier.semantics { liveRegion = LiveRegionMode.Polite }, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        RowDivider()
                        if (right) Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Check, null, Modifier.size(18.dp), tint = NB.colors.goodText)
                            Text("That's it.", style = NB.type.label, color = NB.colors.goodText)
                        } else if (tried) Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Overline("Your version, with the changes it needs")
                            Text(diffText(s.typed, answer), style = NB.type.reading, color = NB.colors.ink)
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Overline("Correction")
                            Text(answer, style = NB.type.reading.copy(fontWeight = FontWeight.Medium), color = NB.colors.ink)
                        }
                        if (note.isNotEmpty()) Text(note, style = NB.type.body, color = NB.colors.muted)
                    }
                }
            }
        }
        Text(
            "Type your fix, or think it through and show the answer. Be honest: Not yet brings the card back before you finish, and tomorrow.",
            style = NB.type.caption, color = NB.colors.muted,
        )
    }
}

/** Deleted words struck through in the bad tone, added words underlined in the good tone. */
@Composable
private fun diffText(typed: String, answer: String) = buildAnnotatedString {
    wordDiff(typed, answer).forEachIndexed { i, (kind, w) ->
        if (i > 0) append(' ')
        when (kind) {
            Change.Same -> append(w)
            Change.Gone -> withStyle(SpanStyle(color = NB.colors.badText, textDecoration = TextDecoration.LineThrough)) { append(w) }
            Change.Added -> withStyle(SpanStyle(color = NB.colors.goodText, textDecoration = TextDecoration.Underline, fontWeight = FontWeight.Medium)) { append(w) }
        }
    }
}

@Composable
private fun VerdictBar(s: DrillSession, card: DueCard, say: (String) -> Unit) {
    val hf = LocalHapticFeedback.current
    StickyActionBar {
        if (!s.checked) {
            NbButton(if (s.typed.isBlank()) "Show answer" else "Check", { s.checked = true }, fill = true, size = BtnSize.Large)
        } else {
            Row(Modifier.fillMaxWidth().semantics { contentDescription = "Did you get it?" }, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(Triple(false, "Not yet", "again this session"), Triple(true, "Got it", gotItHint(card))).forEach { (got, label, sub) ->
                    VerdictButton(label, sub, if (got) NB.colors.goodText else NB.colors.badText, !s.busy, Modifier.weight(1f)) {
                        hf.performHapticFeedback(HapticFeedbackType.LongPress)
                        s.grade(got, say)
                    }
                }
            }
        }
    }
}

/** One verdict: the word in its tone, when the card comes back under it. Neutral fill so the bar has no second primary. */
@Composable
private fun VerdictButton(label: String, next: String, tone: Color, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick, enabled = enabled, shape = NB.shape.tile, color = NB.colors.surface2, contentColor = NB.colors.ink,
        modifier = modifier.heightIn(min = 56.dp).semantics { contentDescription = "$label, $next" },
    ) {
        Column(Modifier.padding(vertical = 8.dp, horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(label, style = NB.type.label, color = tone, maxLines = 1)
            Text(next, style = NB.type.caption, color = NB.colors.muted, textAlign = TextAlign.Center)
        }
    }
}

/** End of a session: first-try score overall and per pattern (a card's re-runs are practice). */
@Composable
private fun Summary(done: List<Pair<DueCard, Boolean>>) {
    val first = done.distinctBy { it.first.id }
    val got = first.count { it.second }
    val byPattern = first.groupBy { it.first.category?.let(::categoryLabel) ?: "Other" }
    Column(
        Modifier.fillMaxSize().readable().verticalScroll(rememberScrollState()).padding(horizontal = NB.space.gutter, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IconTile(Icons.Rounded.CheckCircle, NB.colors.goodText, NB.colors.goodSoft, 72.dp)
        Text("Session done", style = NB.type.headline, color = NB.colors.ink, textAlign = TextAlign.Center)
        Text(
            "You knew $got of ${first.size} ${plural(first.size, "card")} first time. Each one comes back just before you would forget it.",
            style = NB.type.bodySmall, color = NB.colors.muted, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.size(8.dp))
        RowGroup(Modifier.fillMaxWidth()) {
            byPattern.entries.forEachIndexed { i, (k, v) ->
                if (i > 0) RowDivider()
                Row(Modifier.fillMaxWidth().padding(horizontal = NB.space.lg, vertical = 14.dp)) {
                    Text(k, style = NB.type.body, color = NB.colors.ink, modifier = Modifier.weight(1f), maxLines = 1)
                    Text("${v.count { it.second }} / ${v.size} first time", style = NB.type.num, color = NB.colors.muted)
                }
            }
        }
    }
}

/** Nothing to drill: an empty deck says what fills it; nothing due offers the same cards ahead of schedule. */
@Composable
private fun NothingDue(nav: Nav, route: Review, deck: Int) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
        if (deck == 0) EmptyState(
            Icons.Rounded.Style, "No drill cards yet",
            "After each Speaking or Writing test, the mistakes worth practising become drill cards here. A few minutes a day is enough.",
            action = "Write an essay", onAction = { nav.startTest("writing") { nav.go(WritingTask("task2")) } },
        )
        else {
            val name = route.pattern?.let { categoryLabel(it).lowercase() }
            EmptyState(
                Icons.Rounded.CheckCircle,
                if (route.ahead) "No ${name?.let { "$it " } ?: ""}cards to drill" else "Nothing ${name?.let { "in $it " } ?: ""}due right now",
                if (route.ahead) "Every card in this pattern was skipped." else "Each card comes back just before you would forget it. You can drill ahead of schedule; it counts as a normal review.",
                action = if (route.ahead) "See your patterns" else "Drill anyway",
                onAction = { if (route.ahead) nav.go(Mistakes()) else nav.replace(route.copy(ahead = true)) },
            )
        }
    }
}
