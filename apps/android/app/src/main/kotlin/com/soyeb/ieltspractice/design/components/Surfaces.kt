package com.soyeb.ieltspractice.design.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import com.soyeb.ieltspractice.design.PressWash
import com.soyeb.ieltspractice.design.pressScale
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.soyeb.ieltspractice.design.NB
import com.soyeb.ieltspractice.design.Skill
import com.soyeb.ieltspractice.design.color
import com.soyeb.ieltspractice.design.soft

enum class CardTone { Default, Hero, Inset }

/**
 * The card. Flat surface with a hairline in light mode; lifted by colour in dark mode. Hero = brandSoft tint (one per screen).
 * Clickable when [onClick] is set (ripple, merged semantics).
 */
@Composable
fun NbCard(
    modifier: Modifier = Modifier,
    tone: CardTone = CardTone.Default,
    tint: Color? = null,
    onClick: (() -> Unit)? = null,
    padding: PaddingValues = PaddingValues(NB.space.cardPad),
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = NB.colors
    val bg = tint ?: when (tone) {
        CardTone.Default -> c.surface
        CardTone.Hero -> c.brandSoft
        CardTone.Inset -> c.surface2
    }
    val border = if (tone == CardTone.Default && tint == null && !c.isDark) BorderStroke(1.dp, c.line) else null
    val inner: @Composable () -> Unit = { Column(Modifier.padding(padding), content = content) }
    if (onClick != null) {
        // Tappable cards give a little under the finger (scale + wash) instead of a Material ripple.
        val src = remember { MutableInteractionSource() }
        Surface(
            modifier = modifier.fillMaxWidth().pressScale(src, 0.985f).clip(NB.shape.card)
                .clickable(src, PressWash, role = Role.Button, onClick = onClick),
            shape = NB.shape.card, color = bg, contentColor = c.ink, border = border, content = inner,
        )
    }
    else Surface(modifier = modifier.fillMaxWidth(), shape = NB.shape.card, color = bg, contentColor = c.ink, border = border, content = inner)
}

/** A rounded square with the skill's soft fill and its icon. */
@Composable
fun SkillTile(skill: Skill, size: Dp = 44.dp, modifier: Modifier = Modifier) {
    IconTile(skill.icon, skill.color, skill.soft, size, modifier)
}

@Composable
fun IconTile(icon: ImageVector, fg: Color, bg: Color, size: Dp = 44.dp, modifier: Modifier = Modifier) {
    Box(modifier.size(size).background(bg, NB.shape.tile), contentAlignment = Alignment.Center) {
        Icon(icon, null, Modifier.size(size * 0.5f), tint = fg)
    }
}

/** Overline + optional trailing action. Marked as a heading for TalkBack. */
@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(
        modifier.fillMaxWidth().heightIn(min = 40.dp).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = NB.type.title2, color = NB.colors.ink, modifier = Modifier.weight(1f).semantics { heading() })
        if (action != null && onAction != null) NbButton(action, onAction, kind = BtnKind.Text, size = BtnSize.Small)
    }
}

/** Uppercase overline label. */
@Composable
fun Overline(text: String, modifier: Modifier = Modifier, color: Color = NB.colors.muted) {
    Text(text.uppercase(), style = NB.type.overline, color = color, modifier = modifier)
}

/**
 * A list row: leading slot, title, subtitle, trailing slot (defaults to a chevron when clickable).
 * Put several inside one NbCard(padding = PaddingValues(0.dp)) separated by [RowDivider].
 */
@Composable
fun NbListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    titleColor: Color = NB.colors.ink,
    chevron: Boolean = onClick != null && trailing == null,
) {
    val row: @Composable () -> Unit = {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = NB.space.lg, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leading != null) {
                leading()
                Spacer(Modifier.width(14.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = NB.type.title3, color = titleColor, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (subtitle != null) Text(subtitle, style = NB.type.bodySmall, color = NB.colors.muted, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
            if (trailing != null) {
                Spacer(Modifier.width(12.dp))
                CompositionLocalProvider(LocalContentColor provides NB.colors.muted) { trailing() }
            }
            if (chevron) {
                Spacer(Modifier.width(8.dp))
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = NB.colors.faint)
            }
        }
    }
    if (onClick != null) Box(modifier.clickable(role = Role.Button, onClick = onClick)) { row() }
    else Box(modifier) { row() }
}

@Composable
fun RowDivider(inset: Dp = NB.space.lg) {
    HorizontalDivider(Modifier.padding(start = inset), thickness = 1.dp, color = NB.colors.line)
}

/** Small pill label ("New", "Exam", "3 due"). */
@Composable
fun Tag(text: String, modifier: Modifier = Modifier, fg: Color = NB.colors.muted, bg: Color = NB.colors.surface2, icon: ImageVector? = null) {
    Row(
        modifier.background(bg, NB.shape.chip).padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (icon != null) Icon(icon, null, Modifier.size(14.dp), tint = fg)
        Text(text, style = NB.type.caption, color = fg, maxLines = 1)
    }
}

/** A titled group of rows inside one card. */
@Composable
fun RowGroup(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    NbCard(modifier, padding = PaddingValues(vertical = 4.dp), content = content)
}
