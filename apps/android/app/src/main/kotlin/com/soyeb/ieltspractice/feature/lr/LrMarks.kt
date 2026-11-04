package com.soyeb.ieltspractice.feature.lr

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.soyeb.ieltspractice.design.NB
import com.soyeb.ieltspractice.design.components.BtnKind
import com.soyeb.ieltspractice.design.components.BtnSize
import com.soyeb.ieltspractice.design.components.NbButton
import com.soyeb.ieltspractice.design.components.NbCard
import com.soyeb.ieltspractice.design.components.NbIconButton
import com.soyeb.ieltspractice.design.components.NbSheet
import com.soyeb.ieltspractice.design.components.NbTextField
import com.soyeb.ieltspractice.design.components.CardTone

// Highlights and notes on the test text (docs/exam-fidelity.md section 1). Long-press a word and drag to extend, then choose Highlight or Note.
// Tap a marked stretch to open it. State and storage follow the old app: `marks:<attemptId>`, regions `passage:<part>:<paragraph>` and `q:<n>:<field>`.

/** The colour scheme in force ("std" | "bw" | "cream" | "yb"), so highlights stay visible on every scheme. */
val LocalLrScheme = staticCompositionLocalOf { "std" }

class NoteDraft(val sel: Sel?, val id: String?, val text: String)

/** The marks of one attempt plus the transient UI state around them. [persist] saves every change. */
@Stable
class Marks(initial: List<TextMark>, private val persist: (List<TextMark>) -> Unit) {
    var list by mutableStateOf(initial)
        private set
    /** Plain text of every region on screen now, for excerpts. */
    val regions = mutableStateMapOf<String, String>()
    /** A tapped mark (its id) whose menu is open. */
    var menu by mutableStateOf<String?>(null)
    var editor by mutableStateOf<NoteDraft?>(null)
    /** A mark to flash after "Go to". */
    var flash by mutableStateOf<String?>(null)
    /** The selection being dragged, and whether the finger has lifted (the action bar shows). */
    var draft by mutableStateOf<Sel?>(null)
    var pending by mutableStateOf(false)

    fun update(next: List<TextMark>) {
        val withText = next.map { m -> regions[m.region]?.let { t -> runCatching { m.copy(x = t.substring(m.s, m.e).take(80)) }.getOrNull() } ?: m }
        list = withText
        persist(withText)
    }

    private fun newId() = java.util.UUID.randomUUID().toString().take(8)

    fun clearDraft() { draft = null; pending = false }

    fun highlightDraft() {
        val d = draft ?: return
        update(withHighlight(list, d.region, d.s, d.e, newId()))
        clearDraft()
    }

    fun beginNote() {
        val sel = draft ?: return
        val own = list.firstOrNull { it.region == sel.region && it.note != null && it.s < sel.e && sel.s < it.e }
        editor = if (own != null) NoteDraft(null, own.id, own.note.orEmpty()) else NoteDraft(sel, null, "")
        clearDraft()
    }

    fun save(d: NoteDraft, text: String) {
        if (d.id != null) update(withNoteText(list, d.id, text))
        else if (d.sel != null && text.isNotBlank()) update(withNote(list, d.sel.region, d.sel.s, d.sel.e, text, newId()))
    }

    fun excerpt(m: TextMark): String = regions[m.region]?.let { runCatching { it.substring(m.s, m.e) }.getOrNull() } ?: m.x.orEmpty()

    fun draftText(): String = draft?.let { d -> regions[d.region]?.let { runCatching { it.substring(d.s, d.e) }.getOrNull() } }.orEmpty()

    /** Registers [text] as the content of [region] while it is on screen. */
    @Composable
    fun Track(region: String, text: String) {
        remember(region, text) { regions[region] = text; 0 }
        DisposableEffect(region) { onDispose { regions.remove(region) } }
    }
}

class MarkLook(val bg: Color, val fg: Color, val flashBg: Color, val underline: Boolean)

@Composable
fun markLook(): MarkLook {
    val scheme = LocalLrScheme.current
    val dark = NB.colors.isDark
    return remember(scheme, dark) {
        when (scheme) {
            "bw" -> MarkLook(Color(0xFFFFE14D), Color.Black, Color(0xFFFFA000), false)
            "cream" -> MarkLook(Color(0xFFF2C200), Color.Black, Color(0xFFE08A00), false)
            "yb" -> MarkLook(Color(0xFF00B3FF), Color.Black, Color(0xFF9BE0FF), true)
            else -> if (dark) MarkLook(Color(0xFF854D0E), Color.Unspecified, Color(0xFFB45309), false) else MarkLook(Color(0xFFFEF08A), Color.Unspecified, Color(0xFFFDBA74), false)
        }
    }
}

private fun buildMarked(text: String, marks: List<TextMark>, draft: Sel?, look: MarkLook, draftBg: Color, flash: String?): AnnotatedString = buildAnnotatedString {
    append(text)
    for (m in marks) {
        val s = m.s.coerceIn(0, text.length)
        val e = m.e.coerceIn(s, text.length)
        if (e > s) addStyle(
            SpanStyle(
                background = if (m.id == flash) look.flashBg else look.bg, color = look.fg,
                textDecoration = if (m.note != null || look.underline) TextDecoration.Underline else null,
            ),
            s, e,
        )
    }
    if (draft != null) {
        val s = draft.s.coerceIn(0, text.length)
        val e = draft.e.coerceIn(s, text.length)
        if (e > s) addStyle(SpanStyle(background = draftBg), s, e)
    }
}

/**
 * Text that can carry marks. With no [hl] (anywhere that is not the live runner) it is a plain Text.
 * Gestures: long-press a word (it is selected), drag to extend, lift for Highlight / Note; tap a marked stretch to open it.
 */
@Composable
fun MarkedText(hl: Marks?, region: String, text: String, style: TextStyle, color: Color, modifier: Modifier = Modifier) {
    if (hl == null) {
        Text(text, modifier, color = color, style = style)
        return
    }
    hl.Track(region, text)
    val look = markLook()
    val draftBg = NB.colors.brand.copy(alpha = 0.30f)
    val marks = hl.list.filter { it.region == region }
    val draft = hl.draft?.takeIf { it.region == region }
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val str = remember(text, marks, draft, look, hl.flash, draftBg) { buildMarked(text, marks, draft, look, draftBg, hl.flash) }
    Text(
        str,
        modifier
            // TalkBack / switch access cannot long-press-drag: select the whole stretch (then the bar offers Highlight / Add note),
            // and open each existing mark without a precise tap. ponytail: whole-paragraph only; per-sentence picker if asked for.
            .semantics {
                customActions = buildList {
                    if (text.isNotEmpty()) add(CustomAccessibilityAction("Select this text to highlight or add a note") {
                        hl.draft = Sel(region, 0, text.length); hl.pending = true; true
                    })
                    marks.forEach { m ->
                        val snippet = text.substring(m.s.coerceIn(0, text.length), m.e.coerceIn(m.s.coerceIn(0, text.length), text.length)).take(40)
                        add(CustomAccessibilityAction((if (m.note != null) "Open note: " else "Open highlight: ") + snippet) { hl.menu = m.id; true })
                    }
                }
            }
            .pointerInput(text, marks) {
                detectTapGestures { pos ->
                    val l = layout
                    if (l != null && text.isNotEmpty()) {
                        val off = l.getOffsetForPosition(pos)
                        val hit = marks.firstOrNull { off >= it.s && off < it.e }
                        if (hit != null) hl.menu = hit.id else hl.clearDraft()
                    }
                }
            }
            .pointerInput(region, text) {
                var anchorS = 0
                var anchorE = 0
                detectDragGesturesAfterLongPress(
                    onDragStart = { pos ->
                        val l = layout
                        if (l != null && text.isNotEmpty()) {
                            val wb = l.getWordBoundary(l.getOffsetForPosition(pos).coerceIn(0, text.length - 1))
                            anchorS = wb.start
                            anchorE = wb.end
                            hl.pending = false
                            hl.draft = Sel(region, wb.start, wb.end)
                        }
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        val l = layout
                        if (l != null && text.isNotEmpty() && hl.draft != null) {
                            val wb = l.getWordBoundary(l.getOffsetForPosition(change.position).coerceIn(0, text.length - 1))
                            hl.draft = Sel(region, minOf(anchorS, wb.start), maxOf(anchorE, wb.end))
                        }
                    },
                    onDragEnd = { if (hl.draft != null) hl.pending = true },
                    onDragCancel = { hl.clearDraft() },
                )
            },
        color = color, style = style, onTextLayout = { layout = it },
    )
}

/** Shown once a selection is made: the text and what to do with it. Sits above the bottom bar. */
@Composable
fun MarkSelectionBar(hl: Marks, modifier: Modifier = Modifier) {
    if (hl.draft == null || !hl.pending) return
    Surface(modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), shape = NB.shape.card, color = NB.colors.surface, shadowElevation = 8.dp, tonalElevation = 0.dp) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("“${hl.draftText().take(80)}”", style = NB.type.bodySmall, color = NB.colors.muted, maxLines = 2, modifier = Modifier.weight(1f))
                NbIconButton(Icons.Rounded.Close, "Cancel selection", hl::clearDraft)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NbButton("Highlight", hl::highlightDraft, size = BtnSize.Small, modifier = Modifier.weight(1f), haptic = true)
                NbButton("Add note", hl::beginNote, kind = BtnKind.Secondary, size = BtnSize.Small, modifier = Modifier.weight(1f))
            }
        }
    }
}

/** The sheet of one tapped mark (its text, its note, Add / Edit note, Remove) and the note editor. */
@Composable
fun MarkSheets(hl: Marks) {
    val open = hl.menu?.let { id -> hl.list.firstOrNull { it.id == id } }
    if (open != null) NbSheet(
        onDismiss = { hl.menu = null },
        title = if (open.note != null) "Note" else "Highlight",
        subtitle = markWhere(open),
        actions = {
            NbButton(if (open.note == null) "Add note" else "Edit note", { hl.menu = null; hl.editor = NoteDraft(null, open.id, open.note.orEmpty()) }, fill = true, kind = BtnKind.Secondary)
            NbButton(if (open.note == null) "Remove highlight" else "Delete", { hl.update(withoutMark(hl.list, open.id)); hl.menu = null }, fill = true, kind = BtnKind.Text)
        },
    ) {
        Text("“${hl.excerpt(open)}”", style = NB.type.readingSmall, color = NB.colors.ink)
        open.note?.let { Text(it, style = NB.type.body, color = NB.colors.brandText) }
    }
    hl.editor?.let { d ->
        var text by remember(d) { mutableStateOf(d.text) }
        NbSheet(
            onDismiss = { hl.editor = null },
            title = "Note",
            subtitle = "Kept on this device only.",
            actions = {
                NbButton("Save", { hl.save(d, text); hl.editor = null }, fill = true)
                NbButton("Cancel", { hl.editor = null }, kind = BtnKind.Text, fill = true)
            },
        ) {
            NbTextField(text, { text = it.take(NOTE_MAX) }, "Your note", singleLine = false)
            Text("${text.length} of $NOTE_MAX", style = NB.type.caption, color = NB.colors.muted)
        }
    }
}

/** The Notes sheet: every mark of the attempt with its place, the marked text and the note. [canJump] false hides Go to (Listening exam, another part). */
@Composable
fun NotesSheet(hl: Marks, canJump: (TextMark) -> Boolean, onJump: (TextMark) -> Unit, onDismiss: () -> Unit) {
    NbSheet(onDismiss, title = "Notes", subtitle = "Highlights and notes stay on this device.") {
        if (hl.list.isEmpty()) Text("Nothing marked yet. Press and hold a word in the passage or a question, then drag.", style = NB.type.bodySmall, color = NB.colors.muted)
        hl.list.forEach { m ->
            NbCard(tone = CardTone.Inset) {
                Text(markWhere(m), style = NB.type.caption, color = NB.colors.muted)
                Text("“${hl.excerpt(m)}”", style = NB.type.readingSmall, color = NB.colors.ink, maxLines = 3)
                m.note?.let { Text(it, style = NB.type.bodySmall, color = NB.colors.brandText, modifier = Modifier.padding(top = 4.dp)) }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (canJump(m)) NbButton("Go to", { onJump(m) }, kind = BtnKind.Text, size = BtnSize.Small)
                    NbButton(if (m.note == null) "Add note" else "Edit note", { onDismiss(); hl.editor = NoteDraft(null, m.id, m.note.orEmpty()) }, kind = BtnKind.Text, size = BtnSize.Small)
                    NbButton("Delete", { hl.update(withoutMark(hl.list, m.id)) }, kind = BtnKind.Text, size = BtnSize.Small)
                }
            }
        }
    }
}

/** A plain stretch of text with one highlighted range (the evidence in the result's passage and transcript). */
@Composable
fun EvidenceText(text: String, s: Int, e: Int, style: TextStyle, modifier: Modifier = Modifier, color: Color = NB.colors.ink) {
    val look = markLook()
    val str = remember(text, s, e, look) {
        buildAnnotatedString {
            append(text)
            val a = s.coerceIn(0, text.length)
            val b = e.coerceIn(a, text.length)
            if (b > a) addStyle(SpanStyle(background = look.bg, color = look.fg, textDecoration = if (look.underline) TextDecoration.Underline else null), a, b)
        }
    }
    Text(str, modifier, color = color, style = style)
}
