package com.soyeb.ieltspractice.nav

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import com.soyeb.ieltspractice.design.LocalReducedMotion
import com.soyeb.ieltspractice.design.NbMotion
import com.soyeb.ieltspractice.design.pressScale
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.soyeb.ieltspractice.design.NB
import com.soyeb.ieltspractice.design.Skill
import com.soyeb.ieltspractice.design.color
import kotlinx.serialization.Serializable

@Serializable data object TodayTab
@Serializable data object ListeningTab
@Serializable data object ReadingTab
@Serializable data object WritingTab
@Serializable data object SpeakingTab

/** Bottom bar: Today + the four tests (DESIGN.md section 1). */
enum class Tab(val route: Any, val label: String, val icon: ImageVector, val selectedIcon: ImageVector, val skill: Skill?) {
    Today(TodayTab, "Today", Icons.Outlined.WbSunny, Icons.Rounded.WbSunny, null),
    Listening(ListeningTab, "Listening", Icons.Outlined.Headphones, Icons.Rounded.Headphones, Skill.Listening),
    Reading(ReadingTab, "Reading", Icons.AutoMirrored.Outlined.MenuBook, Icons.AutoMirrored.Rounded.MenuBook, Skill.Reading),
    Writing(WritingTab, "Writing", Icons.Outlined.EditNote, Icons.Rounded.EditNote, Skill.Writing),
    Speaking(SpeakingTab, "Speaking", Icons.Outlined.Mic, Icons.Rounded.Mic, Skill.Speaking),
}

/**
 * The bar. A short accent rule slides along the top edge to the selected tab; the selected icon fills in the skill's
 * identity colour (brand for Today) and its label goes ink and semibold. No Material pill, no ripple.
 * [todayBadge] shows the due-review count on Today.
 */
@Composable
fun BottomBar(current: Tab, onSelect: (Tab) -> Unit, todayBadge: Int = 0) {
    val c = NB.colors
    val hf = LocalHapticFeedback.current
    val reduced = LocalReducedMotion.current
    val index = Tab.entries.indexOf(current)
    val accent by animateColorAsState(current.skill?.color ?: c.brand, tween(NbMotion.SLOW, easing = NbMotion.EaseOut), label = "barAccent")
    Column(Modifier.fillMaxWidth().background(c.surface).windowInsetsPadding(WindowInsets.navigationBars)) {
        HorizontalDivider(color = c.line)
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val w = maxWidth / Tab.entries.size
            val x by animateDpAsState(w * index, if (reduced) tween(0) else tween(NbMotion.SLOW, easing = NbMotion.EaseOut), label = "barRule")
            // Grows with large text instead of clipping (DESIGN.md: no fixed heights on text containers).
            Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).height(IntrinsicSize.Min).selectableGroup()) {
                Tab.entries.forEach { tab ->
                    val selected = tab == current
                    val src = remember { MutableInteractionSource() }
                    val tint by animateColorAsState(if (selected) tab.skill?.color ?: c.brand else c.muted, tween(NbMotion.BASE, easing = NbMotion.EaseOut), label = "barIcon")
                    Column(
                        Modifier.weight(1f).fillMaxHeight()
                            .selectable(selected, interactionSource = src, indication = null, role = Role.Tab) {
                                if (!selected) hf.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSelect(tab)
                            }
                            .semantics { if (tab == Tab.Today && todayBadge > 0) contentDescription = "Today, $todayBadge review cards due" },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Box(Modifier.pressScale(src, 0.88f)) {
                            val ic: @Composable () -> Unit = { Icon(if (selected) tab.selectedIcon else tab.icon, null, Modifier.size(24.dp), tint = tint) }
                            if (tab == Tab.Today && todayBadge > 0) BadgedBox(badge = {
                                Badge(containerColor = c.bad, contentColor = c.onBad) { Text(if (todayBadge > 99) "99+" else "$todayBadge") }
                            }) { ic() } else ic()
                        }
                        Spacer(Modifier.height(4.dp))
                        // One line that shrinks to fit a narrow tab at large font scales, never cut mid-word.
                        BasicText(
                            tab.label, maxLines = 1,
                            style = NB.type.caption.copy(fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium, color = if (selected) c.ink else c.muted),
                            autoSize = TextAutoSize.StepBased(minFontSize = 8.sp, maxFontSize = NB.type.caption.fontSize),
                        )
                    }
                }
            }
            Box(
                Modifier.offset(x = x + (w - 28.dp) / 2).width(28.dp).height(3.dp)
                    .clip(RoundedCornerShape(bottomStart = 3.dp, bottomEnd = 3.dp)).background(accent),
            )
        }
    }
}
