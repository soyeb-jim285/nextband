package com.soyeb.ieltspractice.feature.speaking

import com.soyeb.ieltspractice.nav.AttemptResult
import com.soyeb.ieltspractice.nav.LiveExam
import com.soyeb.ieltspractice.nav.ScreenSpec
import com.soyeb.ieltspractice.nav.SpeakingSession
import com.soyeb.ieltspractice.nav.SpeakingTab

/**
 * Screenshot/demo rows for this feature (merged into nav/ScreenCatalog.kt).
 *
 * The live screens use the names media/LiveExam.applyDemo understands (`live`, `live-heard`, `live-intro`, `live-prep`, `live-talk`,
 * `live-failed`); Connecting and Ended are variants of `live`. Session variants are read by SessionModel.applyDemo.
 */
val speakingScreens: List<ScreenSpec> = listOf(
    // Hub: the demo quota has the live examiner on, so the hero shows. "Pending" adds two unsent recordings.
    ScreenSpec("speaking-live", SpeakingTab, listOf("Pending")),
    // Recorded test
    ScreenSpec(
        "session", SpeakingSession("full"),
        listOf("Permission", "PermissionDenied", "MicCheck", "Question", "Recording", "CueCard", "Prep", "Talk", "Transition", "Saving"),
    ),
    // Live examiner
    ScreenSpec("live", LiveExam(), listOf("Connecting", "Ended")),
    ScreenSpec("live-heard", LiveExam()),
    ScreenSpec("live-intro", LiveExam()),
    ScreenSpec("live-talk", LiveExam()),
    ScreenSpec("live-prep", LiveExam()),
    ScreenSpec("live-failed", LiveExam()),
    // Results: one part, and a whole session
    ScreenSpec(
        "result-speaking", AttemptResult.of("as1"),
        listOf("Overview", "Transcript", "Transcript-Sheet", "Fluency", "Language", "Pronunciation", "Improve"),
    ),
    ScreenSpec("result-session", AttemptResult.of("as1", "as2"), listOf("Overview")),
)
