package com.soyeb.ieltspractice.design.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.soyeb.ieltspractice.design.NbMotion
import com.soyeb.ieltspractice.design.PressWash
import com.soyeb.ieltspractice.design.pressScale
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.soyeb.ieltspractice.design.NB

/** Inverse = the action on a BrandHero: white on teal (light), bright teal (dark). */
enum class BtnKind { Primary, Secondary, Outline, Text, Destructive, Inverse }
enum class BtnSize(val height: Dp, val pad: Dp, val icon: Dp, val radius: Dp) {
    Large(56.dp, 24.dp, 22.dp, 18.dp), Medium(48.dp, 20.dp, 20.dp, 14.dp), Small(36.dp, 14.dp, 18.dp, 11.dp);
    val shape get() = RoundedCornerShape(radius)
}

/**
 * The one button. Exactly one Primary per view. `loading` keeps the width and shows a spinner; `fill` = full width.
 * Heights are minimums so 200 % font scale grows the button instead of clipping it.
 */
@Composable
fun NbButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: BtnKind = BtnKind.Primary,
    size: BtnSize = BtnSize.Medium,
    icon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
    enabled: Boolean = true,
    loading: Boolean = false,
    fill: Boolean = false,
    haptic: Boolean = false,
) {
    val c = NB.colors
    val hf = LocalHapticFeedback.current
    val click = {
        if (haptic) hf.performHapticFeedback(HapticFeedbackType.LongPress)
        onClick()
    }
    // Small is 36 dp to look at but still reserves a 48 dp touch target (DESIGN.md: touch targets >= 48 dp).
    val m = modifier.then(if (size == BtnSize.Small) Modifier.minimumInteractiveComponentSize() else Modifier)
        .heightIn(min = size.height).let { if (fill) it.fillMaxWidth() else it }
    val pad = PaddingValues(horizontal = size.pad, vertical = 8.dp)
    val content: @Composable () -> Unit = {
        Box(contentAlignment = Alignment.Center) {
            val la by animateFloatAsState(if (loading) 1f else 0f, tween(NbMotion.FAST), label = "btnLoad")
            Row(Modifier.graphicsLayer { alpha = 1f - la; scaleX = 1f - 0.04f * la; scaleY = 1f - 0.04f * la }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (icon != null) Icon(icon, null, Modifier.size(size.icon))
                Text(text, style = if (size == BtnSize.Small) NB.type.label.copy(fontSize = NB.type.caption.fontSize) else NB.type.label, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (trailingIcon != null) Icon(trailingIcon, null, Modifier.size(size.icon))
            }
            if (la > 0f) CircularProgressIndicator(Modifier.size(20.dp).graphicsLayer { alpha = la }, strokeWidth = 2.dp, color = LocalContentColor.current)
        }
    }
    val on = enabled && !loading
    val (bg, fg) = when {
        !enabled && !loading -> (if (kind == BtnKind.Text || kind == BtnKind.Outline) Color.Transparent else c.surface2) to c.faint
        kind == BtnKind.Primary -> c.brand to c.onBrand
        kind == BtnKind.Destructive -> c.bad to c.onBad
        kind == BtnKind.Secondary -> c.surface2 to c.ink
        kind == BtnKind.Inverse -> (if (c.isDark) c.brand to c.onBrand else Color.White to c.brandText)
        kind == BtnKind.Outline -> Color.Transparent to c.ink
        else -> Color.Transparent to c.brandText
    }
    val bgA by animateColorAsState(bg, tween(NbMotion.BASE, easing = NbMotion.EaseOut), label = "btnBg")
    val fgA by animateColorAsState(fg, tween(NbMotion.BASE, easing = NbMotion.EaseOut), label = "btnFg")
    val shape = size.shape
    val src = remember { MutableInteractionSource() }
    // Primary sits on a soft brand-tinted shadow in light mode; everything else is flat. Kept at 4 dp so the halo stays
    // inside StickyActionBar's 12 dp padding instead of reaching (and being cut by) the bar's edge.
    val lift = kind == BtnKind.Primary && on && !c.isDark
    Box(
        m.pressScale(src)
            .then(if (lift) Modifier.shadow(4.dp, shape, ambientColor = c.brand, spotColor = c.brand) else Modifier)
            .clip(shape)
            .background(bgA)
            .then(if (kind == BtnKind.Outline) Modifier.border(1.dp, if (on) c.faint.copy(alpha = 0.6f) else c.line, shape) else Modifier)
            .clickable(src, PressWash, enabled = on, role = Role.Button, onClick = click)
            .semantics { if (loading) stateDescription = "Loading" }
            .padding(pad),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides fgA) { content() }
    }
}

/** 48 dp icon button. `tonal` gives it a surface2 disc (toolbars inside runners). Always pass a content description. */
@Composable
fun NbIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tonal: Boolean = false,
    enabled: Boolean = true,
    tint: Color? = null,
) {
    val c = NB.colors
    val src = remember { MutableInteractionSource() }
    Box(
        modifier.size(48.dp).pressScale(src, 0.92f).clip(CircleShape)
            .then(if (tonal) Modifier.background(c.surface2) else Modifier)
            .clickable(src, PressWash, enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, Modifier.size(if (tonal) 22.dp else 24.dp), tint = if (enabled) tint ?: c.ink else c.faint)
    }
}
