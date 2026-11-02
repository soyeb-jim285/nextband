package com.soyeb.ieltspractice.data.model

import kotlinx.serialization.Serializable

// Full mock exam (docs/mock-exam.md, section 3). The server sends every band, including the overall one, so nothing here computes a band.

@Serializable data class MockSection(
    val skill: String, // listening | reading | writing | speaking
    val state: String = "todo", // todo | in_progress | submitted | marking | done | failed | skipped
    val band: Double? = null,
    /** LR attempt, first writing attempt, first speaking attempt (the link target). */
    val attemptId: String? = null,
    /** Writing / speaking session, to open all of its attempts together. */
    val sessionId: String? = null,
    val elapsedS: Double? = null,
    val limitS: Double? = null,
    val mode: String? = null, // speaking: recorded | live
)

@Serializable data class Mock(
    val id: String,
    val variant: String = "academic",
    val source: String = "generated",
    val ref: String? = null,
    val status: String = "in_progress", // in_progress | completed | closed
    val startedAt: String = "",
    val expiresAt: String = "",
    val completedAt: String? = null,
    /** First section not yet submitted; null once the mock is completed or closed. */
    val next: String? = null,
    val overall: Double? = null,
    val sections: List<MockSection> = emptyList(),
) {
    val open: Boolean get() = status == "in_progress"
    fun section(skill: String): MockSection? = sections.firstOrNull { it.skill == skill }
    val marking: Boolean get() = sections.any { it.state == "marking" }
}

@Serializable data class CurrentMock(val mock: Mock? = null)
@Serializable data class MockList(val items: List<Mock> = emptyList())
@Serializable data class MockCambridge(val ref: String, val bookTest: String = ref, val started: Boolean = false)
@Serializable data class MockOptions(val cambridge: List<MockCambridge> = emptyList(), val own: Boolean = false, val quota: MockOptionsQuota? = null)
@Serializable data class MockOptionsQuota(val writing: SkillQuota = SkillQuota(), val speaking: SkillQuota = SkillQuota())
@Serializable data class MockSectionStarted(val attemptId: String)
@Serializable data class MockWritingStarted(val prompts: List<Prompt>, val writingSessionId: String, val elapsedS: Double = 0.0)
@Serializable data class MockSpeakingChosen(val mode: String, val sessionId: String? = null, val test: SpeakingTest? = null)
