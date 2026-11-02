package com.soyeb.ieltspractice.feature.speaking

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.soyeb.ieltspractice.LocalDemo
import com.soyeb.ieltspractice.LocalGraph
import com.soyeb.ieltspractice.data.logic.answeredRelevance
import com.soyeb.ieltspractice.data.logic.categoryLabel
import com.soyeb.ieltspractice.data.logic.errorTitle
import com.soyeb.ieltspractice.data.logic.mtld
import com.soyeb.ieltspractice.data.logic.offTopic
import com.soyeb.ieltspractice.data.logic.questionHead
import com.soyeb.ieltspractice.data.logic.repeatedWords
import com.soyeb.ieltspractice.data.logic.tokenize
import com.soyeb.ieltspractice.data.model.AnalysisError
import com.soyeb.ieltspractice.data.model.AnalysisResult
import com.soyeb.ieltspractice.data.model.PronAudioWord
import com.soyeb.ieltspractice.data.model.PronunciationAudio
import com.soyeb.ieltspractice.data.model.Repeated
import com.soyeb.ieltspractice.data.model.VocabUpgrade
import com.soyeb.ieltspractice.design.NB
import com.soyeb.ieltspractice.design.components.BtnKind
import com.soyeb.ieltspractice.design.components.BtnSize
import com.soyeb.ieltspractice.design.components.CardTone
import com.soyeb.ieltspractice.design.components.Meter
import com.soyeb.ieltspractice.design.components.NbButton
import com.soyeb.ieltspractice.design.components.NbCard
import com.soyeb.ieltspractice.design.components.NbChip
import com.soyeb.ieltspractice.design.components.NbListRow
import com.soyeb.ieltspractice.design.components.Overline
import com.soyeb.ieltspractice.design.components.RowDivider
import com.soyeb.ieltspractice.design.components.RowGroup
import com.soyeb.ieltspractice.design.components.SectionHeader
import com.soyeb.ieltspractice.design.components.Stat
import com.soyeb.ieltspractice.design.components.Tag
import com.soyeb.ieltspractice.media.AudioPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max
import kotlin.math.roundToInt

// The Language tab (mistakes by type, vocabulary upgrades, lexical range, "did you answer the question?", clarity hints) and the
// Pronunciation tab (phoneme findings with a dictionary recording to compare). Ports of the old LanguagePanel and PronunciationPanel.

private fun groups(r: AnalysisResult): List<Pair<String, List<AnalysisError>>> =
    r.errors.groupBy { it.category }.toList().sortedWith(compareByDescending<Pair<String, List<AnalysisError>>> { it.second.size }.thenBy { it.first })

/** Upgrades whose suggestions differ from the original. */
private fun upgrades(r: AnalysisResult): List<VocabUpgrade> = r.vocabUpgrades.orEmpty().mapNotNull { v ->
    val better = v.better.filter { it.trim().lowercase() != v.original.trim().lowercase() }
    if (better.isEmpty()) null else VocabUpgrade(v.original, better, v.note)
}

fun playAction(player: ResultPlayer, e: AnalysisError): (() -> Unit)? {
    val t = e.time ?: return null
    return if (player.isLoaded) ({ player.seek(max(0.0, t - 0.3)) }) else null
}

/** A mistake: what you said, the fix, why, and (with a recording) a button to hear it. Shared by the Language tab and the transcript sheet. */
@Composable
fun ErrorDetails(err: AnalysisError, onPlay: (() -> Unit)?, onAdd: (() -> Unit)? = null, hideCategory: Boolean = false) {
    val c = NB.colors
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!hideCategory) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Tag(errorTitle(err.category))
            Tag(if (err.severity == "major") "Major" else "Minor", fg = if (err.severity == "major") c.badText else c.muted, bg = if (err.severity == "major") c.badSoft else c.surface2)
        }
        if (err.original.isNotEmpty()) Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = c.badText, textDecoration = TextDecoration.LineThrough)) { append(err.original) }
                if (err.correction.isNotEmpty()) {
                    append("  →  ")
                    withStyle(SpanStyle(color = c.goodText, fontWeight = FontWeight.SemiBold)) { append(err.correction) }
                }
            },
            style = NB.type.readingSmall,
        )
        Text(err.explanation, style = NB.type.bodySmall, color = c.muted)
        if (onPlay != null || onAdd != null) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (onPlay != null) NbButton("Hear it", onPlay, kind = BtnKind.Secondary, size = BtnSize.Small, icon = Icons.Rounded.PlayArrow)
            if (onAdd != null) NbButton("Add to review", onAdd, kind = BtnKind.Secondary, size = BtnSize.Small)
        }
    }
}

@Composable
fun LanguagePanel(
    r: AnalysisResult, player: ResultPlayer, lean: Repeated?, onLean: (Repeated?) -> Unit, onSelect: (AnalysisError) -> Unit,
) {
    val c = NB.colors
    val groups = groups(r)
    Column(verticalArrangement = Arrangement.spacedBy(NB.space.lg)) {
        // Most answers off-topic: that is the first thing to read, so it leads.
        if (offTopic(r) != null) Relevance(r)
        SectionHeader("Mistakes by type")
        if (groups.isEmpty()) {
            NbCard { Text("No grammar or vocabulary mistakes were flagged in this answer.", style = NB.type.bodySmall, color = c.muted) }
        } else {
            val maxCount = max(groups.first().second.size, 5)
            RowGroup {
                groups.forEachIndexed { i, g ->
                    if (i > 0) RowDivider()
                    ErrorGroup(g.first, g.second, maxCount, onSelect)
                }
            }
        }
        val ups = upgrades(r)
        if (ups.isNotEmpty()) {
            SectionHeader("Vocabulary upgrades")
            ups.forEach { VocabRow(it) }
        }
        Lexical(r, lean, onLean)
        if (offTopic(r) == null) Relevance(r)
        Clarity(r, player)
    }
}

/** One mistake category: name, count bar, and its mistakes behind a disclosure. */
@Composable
private fun ErrorGroup(category: String, errors: List<AnalysisError>, maxCount: Int, onSelect: (AnalysisError) -> Unit) {
    val c = NB.colors
    var open by remember { mutableStateOf(false) }
    Column {
        Column(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.Button) { open = !open }
                .semantics { stateDescription = if (open) "Expanded" else "Collapsed" }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(categoryLabel(category), Modifier.weight(1f), style = NB.type.title3, color = c.ink)
                Text("${errors.size}", style = NB.type.num, color = c.ink)
                Icon(if (open) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown, null, Modifier.size(20.dp), tint = c.muted)
            }
            Meter(errors.size.toDouble(), max = maxCount.toDouble(), color = c.warn, height = 6.dp)
        }
        if (open) Column(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
            errors.forEach { err ->
                RowDivider()
                Column(Modifier.fillMaxWidth().clickable(role = Role.Button) { onSelect(err) }.padding(16.dp)) {
                    ErrorDetails(err, null, hideCategory = true)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VocabRow(v: VocabUpgrade) {
    val c = NB.colors
    NbCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(v.original, style = NB.type.title3, color = c.ink)
            Text("→", style = NB.type.title3, color = c.muted)
        }
        FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            v.better.forEach { Tag(it, fg = c.goodText, bg = c.goodSoft) }
        }
        if (v.note.isNotEmpty()) Text(v.note, style = NB.type.bodySmall, color = c.muted, modifier = Modifier.padding(top = 8.dp))
    }
}

/** Lexical range and the words leaned on, from the transcript. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Lexical(r: AnalysisResult, lean: Repeated?, onLean: (Repeated?) -> Unit) {
    val c = NB.colors
    val tokens = remember(r) { tokenize(r.words.orEmpty().joinToString(" ") { it.w }) }
    val m = mtld(tokens)
    val (tone, word) = if (m >= 70) c.goodText to "Wide range" else if (m >= 50) c.warnText to "Adequate range" else c.badText to "Limited range"
    val repeated = remember(tokens) { repeatedWords(tokens) }
    SectionHeader("Vocabulary range")
    NbCard {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("${m.roundToInt()}", style = NB.type.band(34), color = c.ink)
            Text("MTLD", style = NB.type.bodySmall, color = c.muted)
            Tag(word, fg = tone)
        }
        if (tokens.size < 50 || word != "Wide range") Text(
            if (tokens.size < 50) "Short answer, so treat this number as rough." else "Try synonyms and more precise words for repeated ideas.",
            style = NB.type.bodySmall, color = c.muted, modifier = Modifier.padding(top = 6.dp),
        )
        Text(
            "MTLD: how long you keep using new words before repeating yourself. Higher means a wider range. Around 70+ is typical of band 7 speech.",
            style = NB.type.caption, color = c.muted, modifier = Modifier.padding(top = 6.dp),
        )
        Spacer(Modifier.height(14.dp))
        Text("Words you leaned on", style = NB.type.title3, color = c.ink)
        if (repeated.isEmpty()) Text("No content word stood out as overused.", style = NB.type.bodySmall, color = c.muted, modifier = Modifier.padding(top = 4.dp))
        else {
            Text("Select a word to highlight every use in your transcript.", style = NB.type.bodySmall, color = c.muted, modifier = Modifier.padding(top = 4.dp, bottom = 8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                repeated.forEach { x -> NbChip("${x.word} ×${x.count}", lean?.word == x.word, { onLean(if (lean?.word == x.word) null else x) }) }
            }
        }
    }
}

/** How developed each answer was, and what a fuller answer adds (web LanguagePanel DEVELOPED). */
private val developed = mapOf(
    "none" to ("Not developed" to "Add a reason, then a detail or an example from your own life."),
    "some" to ("Partly developed" to "Good start. Extend it with an example, a comparison or a consequence."),
    "full" to ("Well developed" to ""),
)

@Composable
private fun Relevance(r: AnalysisResult) {
    val c = NB.colors
    val rel = answeredRelevance(r)
    if (rel.isEmpty()) return
    SectionHeader("Did you answer the question?")
    if (rel.any { it.development != null }) Text(
        "IELTS rewards answers you extend with a reason and an example. Each answer is marked for how far you developed it.",
        style = NB.type.bodySmall, color = c.muted, modifier = Modifier.padding(horizontal = 4.dp),
    )
    RowGroup {
        rel.forEachIndexed { i, x ->
            if (i > 0) RowDivider()
            val text = r.questions?.getOrNull(x.questionIdx)?.text ?: "Question ${x.questionIdx + 1}"
            val (head, rest) = questionHead(text)
            val dev = if (x.onTopic) x.development?.let { developed[it] } else null
            Row(Modifier.fillMaxWidth().padding(16.dp).semantics(mergeDescendants = true) {}, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(
                    if (x.onTopic) Icons.Rounded.CheckCircle else Icons.Rounded.Warning, if (x.onTopic) "On topic" else "Off topic",
                    Modifier.size(22.dp), tint = if (x.onTopic) c.goodText else c.warnText,
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(head, style = NB.type.title3, color = c.ink)
                    if (rest.isNotEmpty()) Text(rest, style = NB.type.bodySmall, color = c.muted)
                    if (dev != null) Text(
                        dev.first, style = NB.type.label,
                        color = when (x.development) { "none" -> c.warnText; "full" -> c.goodText; else -> c.ink },
                    )
                    if (x.note.isNotEmpty()) Text(x.note, style = NB.type.bodySmall, color = c.muted)
                    if (dev != null && dev.second.isNotEmpty()) Text(dev.second, style = NB.type.bodySmall, color = c.muted)
                }
            }
        }
    }
}

private val issues = mapOf("sound" to "Sound", "stress" to "Word stress", "intonation" to "Intonation", "unclear" to "Unclear")

/** The web's short labels, else the model's sentence in sentence case without its trailing period. */
private fun issueLabel(raw: String): String {
    issues[raw.lowercase()]?.let { return it }
    val t = raw.trim().trimEnd('.')
    return t.take(1).uppercase() + t.drop(1).lowercase()
}

/** Speech clarity hints: words the recogniser was unsure of, and the model's notes on sound, stress and intonation. */
@Composable
private fun Clarity(r: AnalysisResult, player: ResultPlayer) {
    val c = NB.colors
    val words = r.words.orEmpty()
    val unclear = r.pronunciation?.unclear.orEmpty()
    val llm = r.pronunciation?.llm
    if (unclear.isEmpty() && llm == null) return
    Column {
        SectionHeader(if (llm == null) "Speech clarity hints" else "Pronunciation notes")
        Text(
            "Low recognition confidence does not prove a pronunciation mistake. Listen again to check.",
            style = NB.type.bodySmall, color = c.muted, modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
    if (llm != null) NbCard {
        Text(llm.prosody, style = NB.type.bodySmall, color = c.ink)
        llm.words.take(8).forEach { w ->
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(w.word, style = NB.type.title3, color = c.ink)
                        Tag(issueLabel(w.issue))
                    }
                    if (w.tip.isNotEmpty()) Text(w.tip, style = NB.type.bodySmall, color = c.muted)
                }
                if (player.isLoaded) PlayDot(w.word) { player.seek(max(0.0, w.time - 0.3)) }
            }
        }
    }
    if (llm == null && unclear.isNotEmpty()) RowGroup {
        unclear.take(8).forEachIndexed { i, u ->
            if (i > 0) RowDivider()
            val t = words.getOrNull(u.wordIdx)?.start
            NbListRow(
                title = u.w, subtitle = "Recognised with ${(u.conf * 100).roundToInt()}% confidence",
                trailing = if (t != null && player.isLoaded) ({ PlayDot(u.w) { player.seek(max(0.0, t - 0.3)) } }) else null,
            )
        }
    }
}

@Composable
private fun PlayDot(word: String, onClick: () -> Unit) {
    Box(
        Modifier.size(48.dp).clickable(role = Role.Button, onClick = onClick).semantics { contentDescription = "Play $word" },
        contentAlignment = Alignment.Center,
    ) { Icon(Icons.Rounded.PlayArrow, null, Modifier.size(24.dp), tint = NB.colors.brandText) }
}

// MARK: Pronunciation tab

private val dictHttp by lazy { OkHttpClient() }
private val dictCache = ConcurrentHashMap<String, DictEntry>()

private suspend fun lookupWord(word: String): DictEntry {
    val key = word.lowercase()
    dictCache[key]?.let { return it }
    val d = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder().url("https://api.dictionaryapi.dev/api/v2/entries/en/" + URLEncoder.encode(key, "UTF-8").replace("+", "%20")).build()
            dictHttp.newCall(req).execute().use { r -> if (r.isSuccessful) parseDictionary(r.body?.string().orEmpty()) else DictEntry() }
        }.getOrDefault(DictEntry())
    }
    dictCache[key] = d
    return d
}

/** Pronunciation tab: the phoneme model's findings, each word with your clip and a correct recording. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PronunciationPanel(pron: PronunciationAudio, player: ResultPlayer) {
    val c = NB.colors
    val ctx = LocalContext.current
    val graph = LocalGraph.current
    // one dictionary-clip player for the whole tab, released when it closes
    val clip = remember { AudioPlayer(ctx) }
    DisposableEffect(Unit) { onDispose { clip.release() } }
    fun playUrl(url: String) { clip.load(url); clip.play() }
    fun speak(word: String, accent: String) { graph.tts.speak(word, if (accent == "us") "us" else "uk") }

    Column(verticalArrangement = Arrangement.spacedBy(NB.space.lg)) {
        Column {
            SectionHeader("Summary")
            Text(
                "A phoneme model listened to your recording and compared each sound with US and UK English, counting whichever is closer. It does not change your band yet.",
                style = NB.type.bodySmall, color = c.muted, modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            NbCard(Modifier.weight(1f)) { Stat(pron.accuracy?.let { "${it.roundToInt()} / 100" } ?: "n/a", "Sounds said as expected") }
            NbCard(Modifier.weight(1f)) { Stat("${pron.words.size}", "Words to practise") }
        }
        if (pron.confusions.isNotEmpty()) {
            Column {
                SectionHeader("Sounds you mix up most")
                Text(
                    "Only sounds you swapped more than once. A single slip is not a pattern, and an accent is not an error.",
                    style = NB.type.bodySmall, color = c.muted, modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                pron.confusions.forEach { x ->
                    Text(
                        buildAnnotatedString {
                            append("/${x.expected}/ → ")
                            withStyle(SpanStyle(color = c.badText)) { append("/${x.heard}/") }
                            withStyle(SpanStyle(color = c.muted)) { append("  ${x.count} times") }
                        },
                        Modifier.border(1.dp, c.line, CircleShape).padding(horizontal = 12.dp, vertical = 6.dp)
                            .semantics { contentDescription = "${x.expected} said as ${x.heard}, ${x.count} times" },
                        style = NB.type.bodySmall, color = c.ink,
                    )
                }
            }
        }
        SectionHeader("Words to practise")
        if (pron.words.isEmpty()) Text("No word stood out. Every content word was close to US or UK English.", style = NB.type.body, color = c.ink)
        else {
            Text("Worst first. Play your version, then the correct one, and say it again.", style = NB.type.bodySmall, color = c.muted, modifier = Modifier.padding(horizontal = 4.dp))
            pron.words.forEach { w -> WordCard(w, player, ::playUrl, ::speak) }
        }
    }
}

/** The word's phones, with the ones that went wrong marked: green for the expected sound, red for what was heard. */
@Composable
private fun phones(w: PronAudioWord, expected: Boolean): AnnotatedString {
    val c = NB.colors
    return buildAnnotatedString {
        append("/")
        w.phones.forEach { p ->
            val s = if (expected) p.expected else p.heard
            if (p.ok) append(s)
            else withStyle(SpanStyle(color = if (expected) c.goodText else c.badText, background = (if (expected) c.good else c.bad).copy(alpha = 0.14f), fontWeight = FontWeight.SemiBold)) { append(s) }
        }
        append("/")
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WordCard(w: PronAudioWord, player: ResultPlayer, playUrl: (String) -> Unit, speak: (String, String) -> Unit) {
    val c = NB.colors
    val scope = rememberCoroutineScope()
    val demo = LocalDemo.current != null
    // null while looking up; demo mode has no network
    val dict by produceState<DictEntry?>(if (demo) DictEntry() else null, w.word) { if (!demo) value = lookupWord(w.word) }
    val first = if (w.accent == "us") "us" else "gb" // the accent the model found closer plays first
    val recordings = (if (first == "gb") listOf("gb", "us") else listOf("us", "gb")).mapNotNull { a -> dict?.let { d -> if (a == "gb") d.uk else d.us }?.let { a to it } }
    NbCard {
        Text(w.word, style = NB.type.title2, color = c.ink)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Correct", Modifier.width(72.dp), style = NB.type.bodySmall, color = c.muted)
            Text(phones(w, true), style = NB.type.body, color = c.ink)
        }
        dict?.ipa?.let { Text("Dictionary $it", Modifier.padding(start = 84.dp), style = NB.type.caption, color = c.muted) }
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("You said", Modifier.width(72.dp), style = NB.type.bodySmall, color = c.muted)
            Text(phones(w, false), style = NB.type.body, color = c.ink)
        }
        pronTip(w)?.let { Text(it, style = NB.type.bodySmall, color = c.muted, modifier = Modifier.padding(top = 8.dp)) }
        FlowRow(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (player.isLoaded) NbButton("Your version", { scope.launch { player.playSpan(w.start, w.end + 0.3) } }, kind = BtnKind.Secondary, size = BtnSize.Small)
            recordings.forEach { (a, url) ->
                NbButton("Correct · ${if (a == "gb") "UK" else "US"}", { player.pause(); playUrl(url) }, kind = BtnKind.Secondary, size = BtnSize.Small)
            }
            if (dict != null && recordings.isEmpty()) NbButton("Device voice", { player.pause(); speak(w.word, first) }, kind = BtnKind.Secondary, size = BtnSize.Small)
        }
        Text(
            when { dict == null -> "Looking up…"; recordings.isNotEmpty() -> "Recording: Wiktionary"; else -> "No dictionary recording" },
            style = NB.type.caption, color = c.muted, modifier = Modifier.padding(top = 6.dp),
        )
    }
}
