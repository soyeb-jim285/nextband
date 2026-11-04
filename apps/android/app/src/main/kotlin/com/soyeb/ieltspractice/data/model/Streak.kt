package com.soyeb.ieltspractice.data.model

import kotlinx.serialization.Serializable

// GET /api/streak (docs/engagement/notifications-and-streaks.md §3). Days are the student's local days ("YYYY-MM-DD").
// Every field is defaulted so an older or newer server still decodes; the old `month` heatmap is ignored (`days` replaces it).

/** One day of the Monday-to-Sunday week strip. [state]: done | frozen | missed | today (not practised yet) | future. */
@Serializable data class StreakWeekDay(val day: String, val state: String = "future")

/** One heatmap day. [level]: 0 none, 1 drills only, 2 a test, 3 a test plus drills. [frozen]: a freeze or repair covered it. */
@Serializable data class StreakDay(val day: String, val level: Int = 0, val frozen: Boolean = false)

/** The repair offer after a break with no freeze: practise on 2 days before [until] (ISO instant) to bring back [target]. */
@Serializable data class StreakRepair(val target: Int, val until: String = "", val daysDone: Int = 0)

@Serializable data class Streak(
    val current: Int = 0,
    val longest: Int = 0,
    val freezes: Int = 0,
    /** done | pending */
    val today: String = "pending",
    val week: List<StreakWeekDay> = emptyList(),
    /** The last 182 local days ending today, oldest first. */
    val days: List<StreakDay> = emptyList(),
    val practicedLast35: Int = 0,
    val nextMilestone: Int? = null,
    val repair: StreakRepair? = null,
    /** A milestone reached today. */
    val justReached: Int? = null,
) {
    val done: Boolean get() = today == "done"
}
