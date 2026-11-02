package com.soyeb.ieltspractice.design.components

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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
        val x by animateDpAsState(w * selected, if (reduced) tween(0) else tween(220), label = "seg")
        // matchParentSize: the pill is as tall as the options row. A bare fillMaxHeight took the parent's max height, so in a tall
        // layout (the full Writing test) the control stretched to the whole screen.
        Box(Modifier.matchParentSize()) {
            Box(Modifier.offset(x = x).width(w).fillMaxHeight().clip(NB.shape.pill).background(if (c.isDark) c.surface3 else c.surface))
        }
        Row(Modifier.fillMaxWidth().selectableGroup()) {
            options.forEachIndexed { i, label ->
                Box(
                    Modifier.weight(1f).heightIn(min = 36.dp).clip(NB.shape.pill)
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
    FilterChip(
        selected, onClick, { Text(label, style = NB.type.label) }, modifier.heightIn(min = 36.dp),
        leadingIcon = icon?.let { { Icon(it, null, Modifier.size(18.dp)) } },
        shape = NB.shape.pill,
        colors = FilterChipDefaults.filterChipColors(
            containerColor = c.surface, labelColor = c.ink, iconColor = c.muted,
            selectedContainerColor = c.brandSoft, selectedLabelColor = c.brandText, selectedLeadingIconColor = c.brandText,
        ),
        border = FilterChipDefaults.filterChipBorder(true, selected, borderColor = c.line, selectedBorderColor = c.brandSoft),
    )
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

/** Text field with our colours. */
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
) {
    val c = NB.colors
    OutlinedTextField(
        value, onValueChange, modifier.fillMaxWidth(), enabled = enabled, singleLine = singleLine,
        label = { Text(label) }, placeholder = placeholder?.let { { Text(it) } },
        isError = error != null, supportingText = error?.let { { Text(it) } },
        leadingIcon = leadingIcon?.let { { Icon(it, null) } }, trailingIcon = trailing,
        keyboardOptions = keyboardOptions, keyboardActions = keyboardActions, visualTransformation = visualTransformation,
        shape = NB.shape.tile, textStyle = NB.type.body,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = c.brand, unfocusedBorderColor = c.faint, focusedLabelColor = c.brandText, cursorColor = c.brand,
            focusedContainerColor = c.surface, unfocusedContainerColor = c.surface,
        ),
    )
}
