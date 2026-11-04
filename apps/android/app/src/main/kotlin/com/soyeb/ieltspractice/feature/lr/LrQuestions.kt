package com.soyeb.ieltspractice.feature.lr

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.ZoomIn
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.soyeb.ieltspractice.data.logic.Block
import com.soyeb.ieltspractice.data.logic.Inline
import com.soyeb.ieltspractice.data.logic.gapPart
import com.soyeb.ieltspractice.data.logic.multiPicks
import com.soyeb.ieltspractice.data.logic.parseContent
import com.soyeb.ieltspractice.data.logic.parseInline
import com.soyeb.ieltspractice.data.logic.numberGapParts
import com.soyeb.ieltspractice.data.logic.setGapPart
import com.soyeb.ieltspractice.data.logic.setMultiPicks
import com.soyeb.ieltspractice.data.logic.withAnswer
import com.soyeb.ieltspractice.data.model.LrGroup
import com.soyeb.ieltspractice.data.model.LrOption
import com.soyeb.ieltspractice.data.model.LrQuestion
import com.soyeb.ieltspractice.data.net.Api
import com.soyeb.ieltspractice.design.LightColors
import com.soyeb.ieltspractice.design.LocalNbColors
import com.soyeb.ieltspractice.design.NB
import com.soyeb.ieltspractice.design.components.BtnKind
import com.soyeb.ieltspractice.design.components.BtnSize
import com.soyeb.ieltspractice.design.components.NbButton
import com.soyeb.ieltspractice.design.components.NbChip
import com.soyeb.ieltspractice.design.components.NbIconButton
import com.soyeb.ieltspractice.design.components.NbSheet
import com.soyeb.ieltspractice.design.components.RowDivider
import com.soyeb.ieltspractice.design.components.Segmented

// Every question type of the data model, as one composable per group (web components/lr/QuestionGroup.tsx), shaped for a phone:
// compact inline gap fields that grow, large radio / checkbox cards, three-way segmented T/F/NG, a picker sheet for matching,
// word-box chips that fill the blanks, zoomable figures.

/** Where each question is on screen, so a jump can scroll to it and focus it. Remember one per screen. */
class QRegistry {
    private val bring = HashMap<Int, BringIntoViewRequester>()
    private val focus = HashMap<Int, FocusRequester>()
    fun requester(n: Int) = bring.getOrPut(n) { BringIntoViewRequester() }
    fun focuser(n: Int) = focus.getOrPut(n) { FocusRequester() }
}

/**
 * What every question composable of one screen shares. Remember one per screen: the answers, the ringed question and the flags are read
 * through lambdas over snapshot state, so a keystroke recomposes the questions that read it, not the runner around them.
 */
@Stable
class QCtx(
    private val responsesOf: () -> Map<String, String>,
    val onChange: (Map<String, String>) -> Unit,
    val assets: Map<String, String>,
    val api: Api,
    /** The question to ring (jumped to from the navigator). */
    private val activeOf: () -> Int? = { null },
    private val flaggedOf: () -> Set<Int> = { emptySet() },
    val onFocus: (Int) -> Unit = {},
    /** A text gap gained (true) or lost (false) the cursor: pacing counts a typed change once per visit. */
    val onText: (Int, Boolean) -> Unit = { _, _ -> },
    val reg: QRegistry = QRegistry(),
    /** Highlights and notes on the question text; null turns them off. */
    val hl: Marks? = null,
    /** A small haptic tick on choosing. */
    val tick: () -> Unit = {},
) {
    val responses: Map<String, String> get() = responsesOf()
    val active: Int? get() = activeOf()
    val flagged: Set<Int> get() = flaggedOf()
    fun value(n: Int) = responses[n.toString()].orEmpty()
    fun set(n: Int, v: String) = onChange(responses.withAnswer(n, v))
    fun requester(n: Int) = reg.requester(n)
    fun focuser(n: Int) = reg.focuser(n)

    /** Scroll question [n] into view and put the cursor in it when it is a text field. */
    suspend fun reveal(n: Int, focusField: Boolean) {
        runCatching { requester(n).bringIntoView() }
        if (focusField) runCatching { focuser(n).requestFocus() }
    }
}

/** The number in front of an item: brand once answered, with a flag when flagged. */
@Composable
fun QNum(n: Int, done: Boolean, flagged: Boolean, modifier: Modifier = Modifier) {
    val c = NB.colors
    val (bg, fg) = if (done) c.brandSoft to c.brandText else c.surface2 to c.muted
    Row(modifier.clearAndSetSemantics {}, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        Box(Modifier.heightIn(min = 28.dp).widthIn(min = 28.dp).background(bg, NB.shape.chip).padding(horizontal = 6.dp), contentAlignment = Alignment.Center) {
            Text("$n", style = NB.type.num, color = fg)
        }
        if (flagged) Icon(Icons.Rounded.Flag, null, Modifier.size(14.dp), tint = c.warnText)
    }
}

// ---------------------------------------------------------------- gap fill

@Composable
private fun GapField(n: Int, ctx: QCtx, wordLimit: String?, part: Int, of: Int) {
    val c = NB.colors
    val value = if (of > 1) gapPart(ctx.value(n), part) else ctx.value(n)
    var focused by remember { mutableStateOf(false) }
    val ring = if (focused || ctx.active == n) c.brand else c.faint
    val w = (72 + 10 * value.length).coerceIn(96, 240).dp
    BasicTextField(
        value = value,
        onValueChange = { ctx.set(n, if (of > 1) setGapPart(ctx.value(n), part, of, it) else it) },
        modifier = Modifier.width(w).heightIn(min = 48.dp)
            // Only the first blank of a multi-blank question is the jump target: a requester can sit on one node.
            .let { if (part == 0) it.focusRequester(ctx.focuser(n)).bringIntoViewRequester(ctx.requester(n)) else it }
            .onFocusChanged { focused = it.isFocused; if (it.isFocused) ctx.onFocus(n); ctx.onText(n, it.isFocused) }
            .semantics { contentDescription = "Question $n" + (if (of > 1) ", blank ${part + 1} of $of" else "") + (wordLimit?.let { ", ${it.lowercase()}" } ?: "") },
        singleLine = true,
        textStyle = NB.type.body.copy(color = c.ink, textAlign = TextAlign.Center, fontWeight = FontWeight.Medium),
        cursorBrush = SolidColor(c.brand),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false, imeAction = ImeAction.Next),
        decorationBox = { inner ->
            Box(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).background(c.surface, NB.shape.tile).border(if (focused || ctx.active == n) 2.dp else 1.dp, ring, NB.shape.tile).padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (value.isEmpty()) Text("$n", style = NB.type.num, color = c.muted)
                inner()
            }
        },
    )
}

/** A blank of a word-box group: shows the chosen word; tapping selects it as the blank the next word goes into. */
@Composable
private fun WordSlot(n: Int, opts: List<LrOption>, ctx: QCtx, selected: Boolean, onSelect: () -> Unit) {
    val c = NB.colors
    val value = ctx.value(n)
    val label = opts.firstOrNull { it.key.equals(value, true) }?.let { it.text.ifBlank { it.key } } ?: value
    Surface(
        onClick = onSelect, shape = NB.shape.tile, color = if (selected) c.brandSoft else c.surface, contentColor = c.ink,
        border = androidx.compose.foundation.BorderStroke(if (selected) 2.dp else 1.dp, if (selected) c.brand else c.faint),
        modifier = Modifier.widthIn(min = 96.dp).heightIn(min = 48.dp)
            .bringIntoViewRequester(ctx.requester(n))
            .semantics { contentDescription = "Question $n, ${if (value.isEmpty()) "empty blank" else "word $label"}"; stateDescription = if (selected) "Selected" else "" },
    ) {
        Row(Modifier.heightIn(min = 48.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            if (value.isEmpty()) Text("$n", style = NB.type.num, color = c.muted) else Text(label, style = NB.type.body.copy(fontWeight = FontWeight.Medium), color = c.ink)
        }
    }
}

/** Which blank of a word-box group the next chosen word fills. Holds no [QCtx]: that is rebuilt on every change, so callers pass the current one. */
private class WordBoxState {
    var slot by mutableStateOf<Int?>(null)

    fun assign(g: LrGroup, ctx: QCtx, key: String) {
        val target = slot ?: g.questions.firstOrNull { ctx.value(it.n).isBlank() }?.n ?: return
        var next = ctx.responses
        if (g.reusable != true) g.questions.forEach { if (ctx.value(it.n).equals(key, true)) next = next.withAnswer(it.n, "") }
        next = next.withAnswer(target, key)
        ctx.onChange(next)
        ctx.onFocus(target)
        ctx.tick()
        slot = g.questions.map { it.n }.firstOrNull { it != target && next[it.toString()].isNullOrBlank() }
    }

    fun tapSlot(ctx: QCtx, n: Int) {
        ctx.onFocus(n)
        if (slot == n) { if (ctx.value(n).isNotEmpty()) ctx.set(n, "") else slot = null } else slot = n
    }
}

private sealed interface Tok {
    data class W(val s: String, val bold: Boolean) : Tok
    data class G(val gap: Inline.Gap, val trail: String) : Tok
}

/** Words and gaps; punctuation right after a gap sticks to it so it never wraps alone. */
private fun tokens(inline: List<Inline>): List<Tok> {
    val out = ArrayList<Tok>()
    var glue = false
    for (p in inline) {
        when (p) {
            is Inline.Gap -> { out += Tok.G(p, ""); glue = true }
            is Inline.Text, is Inline.Bold -> {
                val bold = p is Inline.Bold
                var rest = if (p is Inline.Text) p.text else (p as Inline.Bold).text
                if (glue && rest.isNotEmpty() && !rest[0].isWhitespace()) {
                    val cut = rest.indexOfFirst { it.isWhitespace() }.let { if (it < 0) rest.length else it }
                    val last = out.last() as Tok.G
                    out[out.lastIndex] = last.copy(trail = last.trail + rest.substring(0, cut))
                    rest = rest.substring(cut)
                }
                glue = false
                rest.split(Regex("""\s+""")).filter { it.isNotEmpty() }.forEach { out += Tok.W(it, bold) }
            }
        }
    }
    return out
}

/** Text with `{{n}}` gaps as inline fields (or word-box slots). Words wrap; a field grows with what is typed. */
@Composable
private fun RichInline(inline: List<Inline>, ctx: QCtx, g: LrGroup, wb: WordBoxState?, modifier: Modifier = Modifier) {
    val c = NB.colors
    if (inline.none { it is Inline.Gap }) {
        Text(
            buildAnnotatedString { inline.forEach { if (it is Inline.Bold) withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(it.text) } else if (it is Inline.Text) append(it.text) } },
            modifier, style = NB.type.body, color = c.ink,
        )
        return
    }
    // English sentence split into word tokens: FlowRow follows layout direction, so pin LTR or RTL would reverse the words.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) { FlowRow(
        modifier, horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        tokens(inline).forEach { t ->
            when (t) {
                is Tok.W -> Text(t.s, style = NB.type.body.copy(fontWeight = if (t.bold) FontWeight.Bold else FontWeight.Normal), color = c.ink)
                is Tok.G -> Row(verticalAlignment = Alignment.CenterVertically) {
                    if (wb != null) WordSlot(t.gap.n, g.options.orEmpty(), ctx, wb.slot == t.gap.n) { wb.tapSlot(ctx, t.gap.n) }
                    else GapField(t.gap.n, ctx, g.wordLimit, t.gap.part, t.gap.of)
                    if (t.trail.isNotEmpty()) Text(t.trail, style = NB.type.body, color = c.ink)
                }
            }
        }
    } }
}

@Composable
private fun GapContent(blocks: List<Block>, ctx: QCtx, g: LrGroup, wb: WordBoxState?) {
    val c = NB.colors
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        blocks.forEach { b ->
            when (b) {
                is Block.P -> RichInline(b.inline, ctx, g, wb, Modifier.fillMaxWidth())
                is Block.Items -> Column(Modifier.padding(start = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    b.items.forEachIndexed { i, item ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(if (b.ordered) "${i + 1}." else "•", Modifier.padding(top = 2.dp), style = NB.type.num, color = c.muted)
                            RichInline(item, ctx, g, wb, Modifier.weight(1f))
                        }
                    }
                }
                is Block.Table -> Column(Modifier.fillMaxWidth().clip(NB.shape.tile).border(1.dp, c.line, NB.shape.tile)) {
                    val hasHead = b.head.any { it.isNotEmpty() }
                    val rows = (if (hasHead) listOf(b.head) else emptyList()) + b.rows
                    rows.forEachIndexed { r, cells ->
                        val head = hasHead && r == 0
                        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).background(if (head) c.surface2 else Color.Transparent)) {
                            cells.forEach { cell ->
                                Box(Modifier.weight(1f).fillMaxSize().padding(horizontal = 10.dp, vertical = 8.dp), contentAlignment = Alignment.CenterStart) {
                                    RichInline(if (head) cell.map { if (it is Inline.Text) Inline.Bold(it.text) else it } else cell, ctx, g, wb)
                                }
                            }
                        }
                        if (r < rows.lastIndex) Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- choices

@Composable
private fun OptionCard(key: String, text: String, selected: Boolean, multi: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val c = NB.colors
    val shape = NB.shape.tile
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(shape)
            .background(if (selected) c.brandSoft else c.surface)
            .border(if (selected) 2.dp else 1.dp, if (selected) c.brand else c.faint, shape)
            .selectable(selected, enabled, if (multi) Role.Checkbox else Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = "Option $key, $text" }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(32.dp).background(if (selected) c.brand else c.surface2, NB.shape.chip), contentAlignment = Alignment.Center) {
            if (selected && multi) Icon(Icons.Rounded.Check, null, Modifier.size(18.dp), tint = c.onBrand)
            else Text(key, style = NB.type.label, color = if (selected) c.onBrand else c.muted)
        }
        Text(text, style = NB.type.body, color = c.ink, modifier = Modifier.weight(1f))
    }
}

/** One question with its number and flag, bringable into view. */
@Composable
private fun QItem(q: LrQuestion, ctx: QCtx, content: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().bringIntoViewRequester(ctx.requester(q.n)), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        QNum(q.n, ctx.value(q.n).isNotBlank(), q.n in ctx.flagged, Modifier.padding(top = 2.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) { content() }
    }
}

/** The option list of a match or word-box group, shown once. Keys that carry no text (map letters) are not listed. */
@Composable
private fun OptionList(title: String, options: List<LrOption>) {
    if (options.none { it.text.isNotBlank() }) return
    val c = NB.colors
    Column(Modifier.fillMaxWidth().background(c.surface2, NB.shape.tile).padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = NB.type.title3, color = c.ink, modifier = Modifier.semantics { heading() })
        options.forEach { o ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(o.key, Modifier.widthIn(min = 24.dp), style = NB.type.num, color = c.brandText)
                Text(o.text, style = NB.type.bodySmall, color = c.ink, modifier = Modifier.weight(1f))
            }
        }
    }
}

/** The matching picker: every option on its own large row. */
@Composable
private fun ChoiceSheet(n: Int, options: List<LrOption>, current: String, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val c = NB.colors
    NbSheet(
        onDismiss, title = "Question $n",
        actions = { if (current.isNotEmpty()) NbButton("Clear answer", { onPick("") }, kind = BtnKind.Text, fill = true) },
    ) {
        options.forEach { o ->
            val on = o.key.equals(current, true)
            Row(
                Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(NB.shape.tile).background(if (on) c.brandSoft else c.surface2)
                    .selectable(on, role = Role.RadioButton) { onPick(o.key) }.padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(o.key, style = NB.type.title3, color = if (on) c.brandText else c.muted, modifier = Modifier.widthIn(min = 24.dp))
                Text(o.text.ifBlank { o.key }, style = NB.type.body, color = c.ink, modifier = Modifier.weight(1f))
                if (on) Icon(Icons.Rounded.Check, "Selected", Modifier.size(20.dp), tint = c.brandText)
            }
        }
    }
}

/** A matching item: the statement and a button that opens the picker (or, for map letters, the letters right there). */
@Composable
private fun MatchItem(q: LrQuestion, g: LrGroup, ctx: QCtx) {
    val c = NB.colors
    val opts = g.options.orEmpty()
    val value = ctx.value(q.n)
    var open by remember { mutableStateOf(false) }
    val lettersOnly = opts.none { it.text.isNotBlank() }
    QItem(q, ctx) {
        MarkedText(ctx.hl, "q:${q.n}:text", q.text.orEmpty(), NB.type.body, c.ink)
        if (lettersOnly) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            opts.forEach { o ->
                val on = o.key.equals(value, true)
                Box(
                    Modifier.size(48.dp).clip(CircleShape).background(if (on) c.brand else c.surface).border(if (on) 2.dp else 1.dp, if (on) c.brand else c.faint, CircleShape)
                        .selectable(on, role = Role.RadioButton) { ctx.set(q.n, if (on) "" else o.key); ctx.onFocus(q.n); ctx.tick() }
                        .semantics { contentDescription = "Question ${q.n}, letter ${o.key}" },
                    contentAlignment = Alignment.Center,
                ) { Text(o.key, style = NB.type.title3, color = if (on) c.onBrand else c.ink) }
            }
        } else {
            val chosen = opts.firstOrNull { it.key.equals(value, true) }
            Surface(
                onClick = { open = true; ctx.onFocus(q.n) }, shape = NB.shape.tile, color = if (value.isEmpty()) c.surface else c.brandSoft, contentColor = c.ink,
                border = androidx.compose.foundation.BorderStroke(if (value.isEmpty()) 1.dp else 2.dp, if (value.isEmpty()) c.faint else c.brand),
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics { contentDescription = "Question ${q.n}, ${if (value.isEmpty()) "choose an option" else "answer $value"}" },
            ) {
                Row(Modifier.heightIn(min = 48.dp).padding(start = 14.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (chosen == null) Text("Choose an option", style = NB.type.body, color = c.muted, modifier = Modifier.weight(1f))
                    else Text("${chosen.key}  ${chosen.text}", style = NB.type.body, color = c.ink, maxLines = 2, modifier = Modifier.weight(1f))
                    Icon(Icons.Rounded.ArrowDropDown, null, tint = c.muted)
                }
            }
        }
    }
    if (open) ChoiceSheet(q.n, opts, value, { ctx.set(q.n, it); ctx.tick(); open = false }) { open = false }
}

// ---------------------------------------------------------------- figure

/** Decoded figures by URL. ponytail: ~24 MB of bitmaps, enough for a test's few maps; a size-aware image loader if figures multiply. */
private val figures by lazy {
    object : LruCache<String, ImageBitmap>(24 shl 20) {
        override fun sizeOf(key: String, value: ImageBitmap) = value.asAndroidBitmap().byteCount
    }
}

/** Largest figure side kept in memory: sharp enough for the 6x zoom on a phone, far smaller than a print-resolution scan. */
private const val FIGURE_MAX_PX = 2048

/** Power-of-two BitmapFactory sample size that brings a [w] x [h] image within [max] px on its longer side. */
fun figureSampleSize(w: Int, h: Int, max: Int): Int {
    var n = 1
    while (maxOf(w, h) / n > max) n *= 2
    return n
}

private fun decodeFigure(bytes: ByteArray): ImageBitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    val opts = BitmapFactory.Options().apply { inSampleSize = figureSampleSize(bounds.outWidth, bounds.outHeight, FIGURE_MAX_PX) }
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)?.asImageBitmap()
}

/** A map / plan / diagram on white, as printed (fixed paper colours on purpose: figures are drawn for white). Tap to zoom (pinch, drag). */
@Composable
private fun Figure(url: String, title: String?, ctx: QCtx) {
    val c = NB.colors
    var attempt by remember { mutableIntStateOf(0) }
    // null = loading; a failed download or decode is a failure, not an endless "Loading".
    val image by produceState<Result<ImageBitmap>?>(figures.get(url)?.let { Result.success(it) }, url, attempt) {
        if (value?.isSuccess == true) return@produceState // cached: revisiting a part doesn't download and decode again
        value = null
        value = runCatching {
            val bytes = ctx.api.download(url)
            withContext(Dispatchers.Default) { decodeFigure(bytes) }?.also { figures.put(url, it) } ?: error("undecodable figure")
        }.onFailure { if (it is kotlin.coroutines.cancellation.CancellationException) throw it }
    }
    var zoom by remember { mutableStateOf(false) }
    val desc = title ?: "Figure for the questions below"
    val img = image?.getOrNull()
    // ponytail: figures are drawn for white paper, so the frame always uses the light tokens.
    val paperMuted = LightColors.muted
    Box(
        Modifier.fillMaxWidth().clip(NB.shape.tile).background(LightColors.surface).border(1.dp, c.line, NB.shape.tile)
            .then(if (img != null) Modifier.clickable(onClickLabel = "Zoom in") { zoom = true } else Modifier).padding(8.dp),
        contentAlignment = Alignment.Center,
    ) {
        when {
            img != null -> {
                Image(img, desc, Modifier.fillMaxWidth().heightIn(max = 360.dp), contentScale = ContentScale.Fit)
                Icon(Icons.Rounded.ZoomIn, null, Modifier.align(Alignment.BottomEnd).padding(4.dp).size(24.dp), tint = paperMuted)
            }
            image == null -> Text("Loading the figure…", Modifier.padding(24.dp), style = NB.type.bodySmall, color = paperMuted)
            else -> Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("The figure didn't load.", style = NB.type.bodySmall, color = LightColors.ink)
                CompositionLocalProvider(LocalNbColors provides LightColors) {
                    NbButton("Try again", { attempt++ }, kind = BtnKind.Secondary, size = BtnSize.Small)
                }
            }
        }
    }
    if (zoom && img != null) Dialog({ zoom = false }, DialogProperties(usePlatformDefaultWidth = false)) {
        var scale by remember { mutableFloatStateOf(1f) }
        var offset by remember { mutableStateOf(Offset.Zero) }
        val state = rememberTransformableState { z, pan, _ -> scale = (scale * z).coerceIn(1f, 6f); offset += pan }
        Box(Modifier.fillMaxSize().background(Color.White)) {
            Image(
                img, desc,
                Modifier.fillMaxSize().transformable(state).graphicsLayer { scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y },
                contentScale = ContentScale.Fit,
            )
            NbIconButton(Icons.Rounded.Close, "Close figure", { zoom = false }, Modifier.align(Alignment.TopEnd).padding(8.dp), tonal = true)
        }
    }
}

// ---------------------------------------------------------------- the group

/** One group of questions: heading, instructions, optional figure and option list, then the items in the shape of its type. */
@Composable
fun QuestionGroup(g: LrGroup, ctx: QCtx, modifier: Modifier = Modifier) {
    val c = NB.colors
    val opts = g.options.orEmpty()
    val wordBox = g.type == "gap" && opts.isNotEmpty()
    val wb = if (wordBox) remember(g.from) { WordBoxState() } else null
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                if (g.from == g.to) "Question ${g.from}" else "Questions ${g.from}–${g.to}",
                Modifier.semantics { heading() }, style = NB.type.title2, color = c.ink,
            )
            MarkedText(ctx.hl, "grp:${g.from}:instr", cleanInstructions(g.instructions), NB.type.bodySmall, c.muted)
            if (g.wordLimit != null && !g.instructions.uppercase().contains(g.wordLimit.uppercase())) {
                Text("Write ${g.wordLimit}.", style = NB.type.label, color = c.ink)
            }
        }
        g.title?.let { Text(it, style = NB.type.readingSmall.copy(fontWeight = FontWeight.Medium), color = c.ink) }
        g.image?.let { key -> ctx.assets[key]?.let { Figure(it, g.title, ctx) } }
        if (g.type == "match" || wordBox) {
            val roman = opts.any { Regex("^[ivx]+$", RegexOption.IGNORE_CASE).matches(it.key) }
            if (!wordBox) OptionList(if (roman) "List of headings" else "Options", opts)
        }
        if (wb != null) WordBox(g, ctx, wb)
        when {
            g.type == "gap" && g.content != null -> GapContent(parseContent(g.content), ctx, g, wb)
            g.type == "gap" -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                g.questions.forEach { q ->
                    QItem(q, ctx) {
                        val t = q.text.orEmpty()
                        RichInline(if (t.contains("{{${q.n}}}")) parseInline(numberGapParts(t)) else parseInline(t) + Inline.Text(" ") + Inline.Gap(q.n), ctx, g, wb, Modifier.fillMaxWidth())
                    }
                }
            }
            g.type == "mcq" -> Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                g.questions.forEach { q ->
                    QItem(q, ctx) {
                        MarkedText(ctx.hl, "q:${q.n}:text", q.text.orEmpty(), NB.type.body.copy(fontWeight = FontWeight.Medium), c.ink)
                        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            q.options.orEmpty().forEach { o ->
                                val on = ctx.value(q.n).equals(o.key, true)
                                OptionCard(o.key, o.text, on, false, true) { ctx.set(q.n, if (on) "" else o.key); ctx.onFocus(q.n); ctx.tick() }
                            }
                        }
                    }
                }
            }
            g.type == "mcq-multi" -> {
                val picks = multiPicks(g, ctx.responses)
                val max = g.questions.size
                Column(Modifier.bringIntoViewRequester(ctx.requester(g.from)), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { g.questions.forEach { QNum(it.n, ctx.value(it.n).isNotBlank(), it.n in ctx.flagged) } }
                    MarkedText(ctx.hl, "grp:${g.from}:text", g.questions.firstOrNull()?.text ?: "Choose $max answers", NB.type.body.copy(fontWeight = FontWeight.Medium), c.ink)
                    Text(
                        "Choose $max · ${picks.size} of $max selected${if (picks.size >= max) ". Tap one to change." else ""}",
                        Modifier.semantics { stateDescription = "${picks.size} of $max selected" }, style = NB.type.caption,
                        color = if (picks.size >= max) c.brandText else c.muted,
                    )
                    opts.forEach { o ->
                        val on = picks.contains(o.key)
                        OptionCard(o.key, o.text, on, true, on || picks.size < max) {
                            ctx.onChange(setMultiPicks(g, ctx.responses, if (on) picks - o.key else picks + o.key)); ctx.onFocus(g.from); ctx.tick()
                        }
                    }
                }
            }
            g.type == "tfng" || g.type == "ynng" -> Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                val keys = trioKeys(g.type)
                g.questions.forEachIndexed { i, q ->
                    if (i > 0) RowDivider(inset = 0.dp)
                    Column(Modifier.padding(vertical = 14.dp)) {
                        QItem(q, ctx) {
                            MarkedText(ctx.hl, "q:${q.n}:text", q.text.orEmpty(), NB.type.body, c.ink)
                            Segmented(
                                keys.map(::trioLabel), keys.indexOfFirst { it.equals(ctx.value(q.n), true) },
                                { idx -> ctx.set(q.n, keys[idx]); ctx.onFocus(q.n) },
                                strong = true, describe = { "$it, question ${q.n}" },
                            )
                        }
                    }
                }
            }
            else -> Column(verticalArrangement = Arrangement.spacedBy(20.dp)) { g.questions.forEach { MatchItem(it, g, ctx) } }
        }
    }
}

/** The word box of a gap group: tap a blank, then a word (or just a word: it goes into the first empty blank). */
@Composable
private fun WordBox(g: LrGroup, ctx: QCtx, wb: WordBoxState) {
    val c = NB.colors
    val opts = g.options.orEmpty()
    val used = g.questions.map { ctx.value(it.n).uppercase() }.toSet()
    Column(Modifier.fillMaxWidth().background(c.surface2, NB.shape.tile).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Word box", style = NB.type.title3, color = c.ink, modifier = Modifier.semantics { heading() })
        Text("Tap a blank, then a word.${if (g.reusable == true) " A word can be used more than once." else ""}", style = NB.type.caption, color = c.muted)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            opts.forEach { o -> NbChip(if (o.text.isBlank()) o.key else "${o.key}  ${o.text}", o.key.uppercase() in used, { wb.assign(g, ctx, o.key) }) }
        }
    }
}
