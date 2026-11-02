package com.soyeb.ieltspractice.feature.writing

import com.soyeb.ieltspractice.nav.ScreenSpec
import com.soyeb.ieltspractice.nav.WritingTab
import com.soyeb.ieltspractice.nav.WritingTask

/** Screenshot/demo rows for this feature (merged into nav/ScreenCatalog.kt). Fixture prompts: w2 essay, w1a line, w1g letter, w1b bar, w1p pie, w1t table, w1pr process, w1m map. */
val writingScreens: List<ScreenSpec> = listOf(
    ScreenSpec("writing-drafts", WritingTab),
    ScreenSpec("editor", WritingTask("task2"), listOf("Empty", "Typing", "Keyboard", "Warning", "Submit", "Plan")),
    ScreenSpec("editor-t1", WritingTask("task1", "academic", "w1a"), listOf("Top", "Chart")),
    ScreenSpec("editor-full", WritingTask("full", "academic")),
    ScreenSpec("chart-line", WritingTask("task1", "academic", "w1a")),
    ScreenSpec("chart-bar", WritingTask("task1", "academic", "w1b")),
    ScreenSpec("chart-pie", WritingTask("task1", "academic", "w1p")),
    ScreenSpec("chart-table", WritingTask("task1", "academic", "w1t")),
    ScreenSpec("chart-process", WritingTask("task1", "academic", "w1pr")),
    ScreenSpec("chart-map", WritingTask("task1", "academic", "w1m")),
    ScreenSpec("editor-letter", WritingTask("task1", "general", "w1g")),
    ScreenSpec("editor-blocked", WritingTask("task2")),
)

private const val PARA = "Many people argue that working from home brings real benefits to both employees and employers. Workers save the time and money they would spend commuting, and they can arrange their day around their own energy. Companies, in turn, spend less on office space and often find that staff are more satisfied. "

private const val PLAN = "Position: both views, but I lean towards flexible work\nBody 1: benefits for staff (commute, wellbeing)\nBody 2: risks (isolation, teamwork)\nConclusion: hybrid is best"

/** The draft a demo screenshot starts with, by screen name and variant ([index] = which task of a full test). */
internal fun demoDraft(screen: String?, variant: String?, index: Int): Draft = when {
    screen == "editor-blocked" -> if (index == 0) Draft((PARA.repeat(3)).trim()) else Draft()
    screen == "editor-full" -> if (index == 0) Draft("The line graph compares the share of electricity that Denmark, Spain and Japan generated from renewable sources between 2000 and 2020. Denmark rose fastest, from 15% to 68%.") else Draft()
    variant == "Typing" || variant == "Warning" -> Draft(PARA.repeat(3).trim())
    variant == "Keyboard" -> Draft(PARA.repeat(2).trim())
    variant == "Submit" -> Draft(PARA.repeat(2).trim())
    variant == "Plan" -> Draft(PARA.trim(), PLAN)
    else -> Draft()
}

/** Seconds already used when a demo screenshot is taken (the clock is pinned, so screenshots don't change). */
internal fun demoElapsed(variant: String?, total: Int): Int = when (variant) {
    "Warning" -> total - 290
    "Typing", "Keyboard", "Submit", "Plan" -> 420
    else -> 0
}
