package com.soyeb.ieltspractice.design.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import com.soyeb.ieltspractice.design.NbMotion
import com.soyeb.ieltspractice.design.PressWash
import com.soyeb.ieltspractice.design.pressScale
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.SecondaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.soyeb.ieltspractice.design.LocalReducedMotion
import com.soyeb.ieltspractice.design.NB

/** iOS-style segmented control: surface2 track, sliding surface thumb. 2–4 options. */
@Composable
fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val c = NB.colors
    val hf = LocalHapticFeedback.current
    val reduced = LocalReducedMotion.current
    BoxWithConstraints(
        modifier.fillMaxWidth().heightIn(min = 44.dp).clip(NB.shape.pill).background(c.surface2).padding(4.dp),
    ) {
        val w = maxWidth / options.size
        val x by animateDpAsState(w * selected, if (reduced) tween(0) else tween(NbMotion.SLOW, easing = NbMotion.EaseOut), label = "seg")
        // matchParentSize: the pill is as tall as the options row. A bare fillMaxHeight took the parent's max height, so in a tall
        // layout (the full Writing test) the control stretched to the whole screen.
        Box(Modifier.matchParentSize()) {
            Box(
                Modifier.offset(x = x).width(w).fillMaxHeight()
                    .then(if (c.isDark) Modifier else Modifier.shadow(3.dp, NB.shape.pill, ambientColor = c.ink.copy(alpha = 0.25f), spotColor = c.ink.copy(alpha = 0.25f)))
                    .clip(NB.shape.pill).background(if (c.isDark) c.surface3 else c.surface),
            )
        }
        Row(Modifier.fillMaxWidth().selectableGroup()) {
            options.forEachIndexed { i, label ->
                Box(
                    Modifier.weight(1f).minimumInteractiveComponentSize().heightIn(min = 36.dp).clip(NB.shape.pill)
                        .selectable(i == selected, role = Role.Tab) { if (i != selected) { hf.performHapticFeedback(HapticFeedbackType.TextHandleMove); onSelect(i) } },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label, style = NB.type.label, color = if (i == selected) c.ink else c.muted,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 8.dp),
                    )
                }
            }
        }
    }
}

/** A horizontally scrolling row of filter chips with the screen gutter as content padding. */
@Composable
fun ChipRow(
    options: List<String>,
    selected: Set<Int>,
    onToggle: (Int) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = NB.space.gutter),
) {
    Row(
        modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(contentPadding),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEachIndexed { i, label -> NbChip(label, i in selected, { onToggle(i) }) }
    }
}

@Composable
fun NbChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    val c = NB.colors
    val spec = tween<Color>(NbMotion.BASE, easing = NbMotion.EaseOut)
    val bg by animateColorAsState(if (selected) c.brandSoft else c.surface, spec, label = "chipBg")
    val fg by animateColorAsState(if (selected) c.brandText else c.ink, spec, label = "chipFg")
    val edge by animateColorAsState(if (selected) c.brand.copy(alpha = 0.45f) else c.line, spec, label = "chipEdge")
    val src = remember { MutableInteractionSource() }
    Row(
        modifier.minimumInteractiveComponentSize().heightIn(min = 36.dp).pressScale(src).clip(NB.shape.pill).background(bg).border(1.dp, edge, NB.shape.pill)
            .selectable(selected, interactionSource = src, indication = PressWash, role = Role.Checkbox, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (icon != null) Icon(icon, null, Modifier.size(18.dp), tint = if (selected) fg else c.muted)
        Text(label, style = NB.type.label, color = fg, maxLines = 1)
    }
}

/** Fixed tabs (2–4) or scrollable tabs (5+), brand indicator. */
@Composable
fun NbTabs(tabs: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier, scrollable: Boolean = tabs.size > 4) {
    val c = NB.colors
    val tab: @Composable (Int, String) -> Unit = { i, t ->
        Tab(
            i == selected, { onSelect(i) },
            text = { Text(t, style = NB.type.label, maxLines = 1) },
            selectedContentColor = c.brandText, unselectedContentColor = c.muted,
        )
    }
    if (scrollable) SecondaryScrollableTabRow(
        selected, modifier, containerColor = c.bg, contentColor = c.brandText, edgePadding = NB.space.gutter,
        indicator = { TabRowDefaults.SecondaryIndicator(Modifier.tabIndicatorOffset(selected), color = c.brand, height = 3.dp) },
        divider = { androidx.compose.material3.HorizontalDivider(color = c.line) },
    ) { tabs.forEachIndexed { i, t -> tab(i, t) } }
    else PrimaryTabRow(
        selected, modifier, containerColor = c.bg, contentColor = c.brandText,
        indicator = { TabRowDefaults.PrimaryIndicator(Modifier.tabIndicatorOffset(selected), color = c.brand, width = 32.dp, height = 3.dp) },
        divider = { androidx.compose.material3.HorizontalDivider(color = c.line) },
    ) { tabs.forEachIndexed { i, t -> tab(i, t) } }
}

/**
 * Text field: label above, filled well, focus ring that fades in. Not the Material outlined field with its floating label.
 * Error replaces the hint below and turns the ring red; TalkBack hears the label and the error.
 */
@Composable
fun NbTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    error: String? = null,
    singleLine: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: androidx.compose.foundation.text.KeyboardActions = androidx.compose.foundation.text.KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    leadingIcon: ImageVector? = null,
    trailing: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    /** Goes on the editable itself: focusRequester, autofill contentType. [modifier] goes on the whole block. */
    fieldModifier: Modifier = Modifier,
) {
    val c = NB.colors
    val src = remember { MutableInteractionSource() }
    val focused by src.collectIsFocusedAsState()
    val spec = tween<Color>(NbMotion.BASE, easing = NbMotion.EaseOut)
    val ring by animateColorAsState(
        when { error != null -> c.bad; focused -> c.brand; else -> if (c.isDark) c.line else Color.Transparent }, spec, label = "fieldRing",
    )
    val well by animateColorAsState(if (focused) c.surface else c.surface2, spec, label = "fieldWell")
    val labelColor by animateColorAsState(if (error != null) c.badText else if (focused) c.brandText else c.muted, spec, label = "fieldLabel")
    val shape = RoundedCornerShape(14.dp)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = NB.type.label, color = labelColor, modifier = Modifier.padding(start = 2.dp).clearAndSetSemantics {})
        BasicTextField(
            value, onValueChange,
            fieldModifier.fillMaxWidth().semantics {
                contentDescription = label
                if (error != null) this.error(error)
            },
            enabled = enabled, singleLine = singleLine, textStyle = NB.type.body.copy(color = if (enabled) c.ink else c.faint),
            keyboardOptions = keyboardOptions, keyboardActions = keyboardActions, visualTransformation = visualTransformation,
            interactionSource = src, cursorBrush = SolidColor(c.brand),
            decorationBox = { inner ->
                Row(
                    Modifier.fillMaxWidth().heightIn(min = if (singleLine) 54.dp else 120.dp).clip(shape).background(well)
                        .border(if (focused || error != null) 1.5.dp else 1.dp, ring, shape)
                        .padding(start = if (leadingIcon != null) 14.dp else 16.dp, end = if (trailing != null) 4.dp else 16.dp),
                    verticalAlignment = if (singleLine) Alignment.CenterVertically else Alignment.Top,
                ) {
                    if (leadingIcon != null) {
                        Icon(leadingIcon, null, Modifier.padding(top = if (singleLine) 0.dp else 15.dp).size(20.dp), tint = if (focused) c.brandText else c.muted)
                        Spacer(Modifier.width(10.dp))
                    }
                    Box(Modifier.weight(1f).padding(vertical = 15.dp)) {
                        if (value.isEmpty() && placeholder != null) Text(placeholder, style = NB.type.body, color = c.muted, maxLines = if (singleLine) 1 else Int.MAX_VALUE)
                        inner()
                    }
                    if (trailing != null) trailing()
                }
            },
        )
        AnimatedVisibility(
            error != null,
            enter = fadeIn(tween(NbMotion.BASE)) + expandVertically(tween(NbMotion.BASE, easing = NbMotion.EaseOut)),
            exit = fadeOut(tween(NbMotion.FAST)) + shrinkVertically(tween(NbMotion.BASE, easing = NbMotion.EaseOut)),
        ) {
            // Keep the last message on screen while it animates out.
            var last by remember { mutableStateOf(error.orEmpty()) }
            if (error != null) last = error
            Text(last, style = NB.type.bodySmall, color = c.badText, modifier = Modifier.padding(start = 2.dp).clearAndSetSemantics {})
        }
    }
}
