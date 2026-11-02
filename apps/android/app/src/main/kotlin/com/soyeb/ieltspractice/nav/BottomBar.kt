package com.soyeb.ieltspractice.nav

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
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
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
import com.soyeb.ieltspractice.design.soft
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
 * The bar. Selected item: filled icon, the skill's identity colour on a soft pill (brand for Today).
 * [todayBadge] shows the due-review count on Today.
 */
@Composable
fun BottomBar(current: Tab, onSelect: (Tab) -> Unit, todayBadge: Int = 0) {
    val c = NB.colors
    val hf = LocalHapticFeedback.current
    Column {
        HorizontalDivider(color = c.line)
        NavigationBar(containerColor = c.surface, contentColor = c.ink, tonalElevation = 0.dp) {
            Tab.entries.forEach { tab ->
                val selected = tab == current
                val accent = tab.skill?.color ?: c.brand
                val pill = tab.skill?.soft ?: c.brandSoft
                NavigationBarItem(
                    selected = selected,
                    onClick = { if (!selected) hf.performHapticFeedback(HapticFeedbackType.TextHandleMove); onSelect(tab) },
                    icon = {
                        val ic: @Composable () -> Unit = { Icon(if (selected) tab.selectedIcon else tab.icon, null, Modifier.size(24.dp)) }
                        if (tab == Tab.Today && todayBadge > 0) BadgedBox(badge = {
                            Badge(containerColor = c.bad, contentColor = androidx.compose.ui.graphics.Color.White) {
                                Text(if (todayBadge > 99) "99+" else "$todayBadge")
                            }
                        }) { ic() } else ic()
                    },
                    label = { Text(tab.label, style = NB.type.caption, maxLines = 1) },
                    modifier = Modifier.semantics {
                        if (tab == Tab.Today && todayBadge > 0) contentDescription = "Today, $todayBadge review cards due"
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = accent, selectedTextColor = c.ink, indicatorColor = pill,
                        unselectedIconColor = c.muted, unselectedTextColor = c.muted,
                    ),
                )
            }
        }
    }
}
