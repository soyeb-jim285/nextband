package com.soyeb.ieltspractice.design.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.soyeb.ieltspractice.design.NB

enum class BtnKind { Primary, Secondary, Outline, Text, Destructive }
enum class BtnSize(val height: Dp, val pad: Dp, val icon: Dp) { Large(56.dp, 24.dp, 22.dp), Medium(48.dp, 20.dp, 20.dp), Small(36.dp, 14.dp, 18.dp) }

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
    val m = modifier.heightIn(min = size.height).let { if (fill) it.fillMaxWidth() else it }
    val pad = PaddingValues(horizontal = size.pad, vertical = 8.dp)
    val content: @Composable () -> Unit = {
        Box(contentAlignment = Alignment.Center) {
            Row(Modifier.alpha(if (loading) 0f else 1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (icon != null) Icon(icon, null, Modifier.size(size.icon))
                Text(text, style = if (size == BtnSize.Small) NB.type.label.copy(fontSize = NB.type.caption.fontSize) else NB.type.label, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (trailingIcon != null) Icon(trailingIcon, null, Modifier.size(size.icon))
            }
            if (loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = androidx.compose.material3.LocalContentColor.current)
        }
    }
    val on = enabled && !loading
    when (kind) {
        BtnKind.Primary, BtnKind.Destructive, BtnKind.Secondary -> {
            val (bg, fg) = when (kind) {
                BtnKind.Primary -> c.brand to c.onBrand
                BtnKind.Destructive -> c.bad to (if (c.isDark) Color(0xFF3B0613) else Color.White)
                else -> c.surface2 to c.ink
            }
            Button(
                click, m, enabled = on, shape = NB.shape.pill, contentPadding = pad,
                colors = ButtonDefaults.buttonColors(
                    containerColor = bg, contentColor = fg,
                    disabledContainerColor = if (loading) bg else c.surface2, disabledContentColor = if (loading) fg else c.faint,
                ),
                elevation = null,
            ) { content() }
        }
        BtnKind.Outline -> OutlinedButton(
            click, m, enabled = on, shape = NB.shape.pill, contentPadding = pad,
            border = BorderStroke(1.dp, if (on) c.faint else c.line),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = c.ink, disabledContentColor = c.faint),
        ) { content() }
        BtnKind.Text -> TextButton(
            click, m, enabled = on, shape = NB.shape.pill, contentPadding = pad,
            colors = ButtonDefaults.textButtonColors(contentColor = c.brandText, disabledContentColor = c.faint),
        ) { content() }
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
    if (tonal) FilledTonalIconButton(
        onClick, modifier.size(48.dp), enabled = enabled,
        colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = c.surface2, contentColor = tint ?: c.ink),
    ) { Icon(icon, contentDescription, Modifier.size(22.dp)) }
    else IconButton(onClick, modifier.size(48.dp), enabled = enabled) {
        Icon(icon, contentDescription, Modifier.size(24.dp), tint = if (enabled) tint ?: c.ink else c.faint)
    }
}
