package com.soyeb.ieltspractice.feature.writing

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.soyeb.ieltspractice.LocalDemo
import com.soyeb.ieltspractice.design.NB
import com.soyeb.ieltspractice.design.components.NbSheet

/**
 * A bottom sheet. A real sheet opens in its own window, which a screenshot cannot see, so demo mode draws the same content inline
 * over the screen. Call it from a Box that fills the screen.
 */
@Composable
internal fun SheetOrInline(
    onDismiss: () -> Unit,
    title: String?,
    subtitle: String? = null,
    actions: (@Composable ColumnScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (LocalDemo.current == null) {
        NbSheet(onDismiss, title = title, subtitle = subtitle, actions = actions, content = content)
        return
    }
    Box(Modifier.fillMaxSize().background(NB.colors.scrim), contentAlignment = Alignment.BottomCenter) {
        Surface(shape = NB.shape.sheet, color = NB.colors.surface, contentColor = NB.colors.ink) {
            Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
                Box(Modifier.align(Alignment.CenterHorizontally).padding(vertical = 12.dp).size(width = 32.dp, height = 4.dp).background(NB.colors.faint, CircleShape))
                Column(
                    Modifier.verticalScroll(rememberScrollState()).padding(horizontal = NB.space.gutter).padding(bottom = 16.dp),
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
}
