package com.soyeb.ieltspractice.feature.lr

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Spellcheck
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.soyeb.ieltspractice.LocalDemo
import com.soyeb.ieltspractice.LocalGraph
import com.soyeb.ieltspractice.data.logic.timesText
import com.soyeb.ieltspractice.data.model.LrSpellingItem
import com.soyeb.ieltspractice.design.NB
import com.soyeb.ieltspractice.design.Tone
import com.soyeb.ieltspractice.design.components.Meter
import com.soyeb.ieltspractice.design.components.Banner
import com.soyeb.ieltspractice.design.components.BtnKind
import com.soyeb.ieltspractice.design.components.BtnSize
import com.soyeb.ieltspractice.design.components.CardTone
import com.soyeb.ieltspractice.design.components.EmptyState
import com.soyeb.ieltspractice.design.components.ErrorState
import com.soyeb.ieltspractice.design.components.Load
import com.soyeb.ieltspractice.design.components.NbButton
import com.soyeb.ieltspractice.design.components.NbCard
import com.soyeb.ieltspractice.design.components.NbTextField
import com.soyeb.ieltspractice.design.components.Overline
import com.soyeb.ieltspractice.design.components.PushedList
import com.soyeb.ieltspractice.design.components.PushedScreen
import com.soyeb.ieltspractice.design.components.SkeletonList
import com.soyeb.ieltspractice.design.components.StickyActionBar
import com.soyeb.ieltspractice.design.components.rememberLoad
import com.soyeb.ieltspractice.nav.Nav

// Spelling drill: the words misspelt in gap answers (GET /api/lr/spelling), one card at a time. Hear the word (device voice), type it, check.

@Composable
fun SpellingScreen(nav: Nav) {
    val api = LocalGraph.current.api
    val load = rememberLoad { api.lrSpelling().items }
    when (val s = load.state) {
        Load.Loading -> PushedScreen("Spelling drill", nav::back) { pad -> SkeletonList(Modifier.padding(pad)) }
        is Load.Failed -> PushedScreen("Spelling drill", nav::back) { pad -> ErrorState(s.message, load.reload, Modifier.padding(pad).verticalScroll(rememberScrollState())) }
        is Load.Ready ->
            if (s.value.isEmpty()) PushedScreen("Spelling drill", nav::back) { pad ->
                EmptyState(
                    Icons.Rounded.Spellcheck, "No spelling slips yet",
                    "Words you misspell in Listening gap answers will appear here to practise.", Modifier.padding(pad).verticalScroll(rememberScrollState()),
                )
            } else SpellingDrill(s.value, nav)
    }
}

/** Right when the letters match, ignoring case and stray spaces. */
fun spellingRight(typed: String, word: String): Boolean = typed.trim().equals(word.trim(), ignoreCase = true)

@Composable
private fun SpellingDrill(items: List<LrSpellingItem>, nav: Nav) {
    val graph = LocalGraph.current
    val demo = LocalDemo.current
    val hf = LocalHapticFeedback.current
    val dv = demo?.tab.orEmpty()
    var i by remember { mutableIntStateOf(if (dv == "Done") items.size else 0) }
    var typed by remember { mutableStateOf(when (dv) { "Wrong" -> items.first().typed.firstOrNull().orEmpty(); "Right" -> items.first().word; else -> "" }) }
    var checked by remember { mutableStateOf(dv == "Wrong" || dv == "Right") }
    var right by remember { mutableIntStateOf(if (dv == "Done") items.size - 1 else 0) }
    val focus = remember { FocusRequester() }
    DisposableEffect(Unit) { onDispose { if (demo == null) graph.tts.stop() } }

    val done = i >= items.size
    val cur = items.getOrNull(i)
    fun checkWord() {
        val w = cur ?: return
        if (checked || typed.isBlank()) return
        checked = true
        val ok = spellingRight(typed, w.word)
        if (ok) right++
        hf.performHapticFeedback(if (ok) HapticFeedbackType.TextHandleMove else HapticFeedbackType.LongPress)
    }
    fun next() {
        i++
        typed = ""
        checked = false
    }

    PushedScreen(
        "Spelling drill", nav::back,
        bottomBar = {
            StickyActionBar {
                when {
                    done -> {
                        NbButton("Go again", { i = 0; right = 0; typed = ""; checked = false }, size = BtnSize.Large, fill = true)
                        NbButton("Done", nav::back, kind = BtnKind.Text, fill = true)
                    }
                    !checked -> NbButton("Check", ::checkWord, size = BtnSize.Large, fill = true, enabled = typed.isNotBlank())
                    else -> NbButton(if (i == items.lastIndex) "See result" else "Next word", ::next, size = BtnSize.Large, fill = true, haptic = true)
                }
            }
        },
    ) { pad ->
        PushedList(pad) {
            if (done || cur == null) item {
                NbCard(tone = CardTone.Hero) {
                    Overline("Finished", color = NB.colors.brandText)
                    Text("$right of ${items.size} right", style = NB.type.headline, color = NB.colors.ink, modifier = Modifier.padding(top = 4.dp))
                    Text(
                        if (right == items.size) "Every word spelt correctly. Come back after your next Listening test." else "Go again to practise the ones you missed.",
                        style = NB.type.bodySmall, color = NB.colors.muted, modifier = Modifier.padding(top = 4.dp),
                    )
                }
            } else {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Word ${i + 1} of ${items.size}", style = NB.type.caption, color = NB.colors.muted)
                        Meter((i + if (checked) 1 else 0).toDouble(), max = items.size.toDouble(), height = 6.dp)
                    }
                }
                item {
                    NbCard(tone = CardTone.Hero) {
                        Overline(if (cur.kind == "plural") "Singular or plural" else "Spelling", color = NB.colors.brandText)
                        Text(
                            "Listen, then type the word",
                            style = NB.type.title1, color = NB.colors.ink, modifier = Modifier.padding(top = 4.dp).semantics { heading() },
                        )
                        Text(
                            "You missed this ${timesText(cur.count)} in Listening gap answers.",
                            style = NB.type.bodySmall, color = NB.colors.muted, modifier = Modifier.padding(top = 4.dp),
                        )
                        NbButton(
                            "Hear the word", { if (demo == null) graph.tts.speak(cur.word) }, kind = BtnKind.Primary, size = BtnSize.Large,
                            icon = Icons.AutoMirrored.Rounded.VolumeUp, modifier = Modifier.padding(top = 16.dp), fill = true,
                        )
                    }
                }
                item {
                    NbTextField(
                        typed, { if (!checked) typed = it }, "Type the word", fieldModifier = Modifier.focusRequester(focus),
                        enabled = !checked,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { checkWord() }),
                    )
                }
                if (checked) item {
                    val ok = spellingRight(typed, cur.word)
                    if (ok) Banner("Correct. ${cur.word}", Icons.Rounded.CheckCircle, tone = Tone.Good)
                    else Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Banner("Not quite. It is spelt ${cur.word}.", Icons.Rounded.Close, tone = Tone.Bad)
                        if (cur.typed.isNotEmpty()) Text(
                            "Before, you wrote: ${cur.typed.joinToString(", ")}",
                            style = NB.type.bodySmall, color = NB.colors.muted,
                        )
                    }
                }
            }
        }
    }
}
