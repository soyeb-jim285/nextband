package com.soyeb.ieltspractice.design.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.soyeb.ieltspractice.design.NB

/**
 * Modal bottom sheet with our shape and colours: drag handle, optional title + subtitle, scrollable body, sticky [actions].
 * Opens fully when [skipPartial] (default) so short forms don't stop half-way.
 */
@Composable
fun NbSheet(
    onDismiss: () -> Unit,
    title: String? = null,
    subtitle: String? = null,
    skipPartial: Boolean = true,
    scroll: Boolean = true,
    actions: (@Composable ColumnScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = skipPartial)
    ModalBottomSheet(
        onDismiss, sheetState = state, shape = NB.shape.sheet, containerColor = NB.colors.surface, contentColor = NB.colors.ink,
        scrimColor = NB.colors.scrim, dragHandle = { BottomSheetDefaults.DragHandle(color = NB.colors.faint) },
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
            Column(
                Modifier.weight(1f, fill = false).let { if (scroll) it.verticalScroll(rememberScrollState()) else it }
                    .padding(horizontal = NB.space.gutter).padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (title != null) Text(title, style = NB.type.title1, color = NB.colors.ink, modifier = Modifier.semantics { heading() })
                if (subtitle != null) Text(subtitle, style = NB.type.bodySmall, color = NB.colors.muted)
                content()
            }
            if (actions != null) Column(
                Modifier.fillMaxWidth().padding(horizontal = NB.space.gutter).padding(bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp), content = actions,
            )
        }
    }
}

/** Confirm dialog. [destructive] colours the confirm button red. */
@Composable
fun ConfirmDialog(
    title: String,
    body: String,
    confirm: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    dismiss: String = "Cancel",
    destructive: Boolean = false,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = NB.type.title1) },
        text = { Text(body, style = NB.type.body, color = NB.colors.muted) },
        confirmButton = { NbButton(confirm, onConfirm, kind = if (destructive) BtnKind.Destructive else BtnKind.Primary, size = BtnSize.Small) },
        dismissButton = { NbButton(dismiss, onDismiss, kind = BtnKind.Text, size = BtnSize.Small) },
        containerColor = NB.colors.surface, titleContentColor = NB.colors.ink, textContentColor = NB.colors.muted,
        shape = NB.shape.card,
    )
}
