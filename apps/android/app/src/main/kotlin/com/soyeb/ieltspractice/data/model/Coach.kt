package com.soyeb.ieltspractice.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive

// GET /api/coach. openapi types facts/words loosely (additionalProperties); the contract is apps/server/src/coach/types.ts, mirrored here.
// Everything is defaulted so a report written by an older or newer server still decodes.

/** `state` is "empty" (no tests yet, [report] null) or "ready". [updating]: a fresh report is being written. */
@Serializable data class Coach(val state: String = "empty", val updating: Boolean = false, val report: CoachReport? = null) {
    val ready get() = state == "ready" && report != null
}

@Serializable data class CoachReport(
    val id: String,
    val day: String = "",
    val createdAt: String = "",
    /** Item keys the student marked wrong (`focus:<id>`, `pattern:<id>`, `cheat:<id>`); hide them. */
    val hidden: List<String> = emptyList(),
    val facts: CoachFacts = CoachFacts(),
    val words: CoachWords = CoachWords(),
)

/** skill: listening | reading | writing | speaking. */
@Serializable data class CoachEvidence(
    val attemptIds: List<String> = emptyList(),
    val mistakeIds: List<String> = emptyList(),
    val lr: List<CoachLrRef> = emptyList(),
)
@Serializable data class CoachLrRef(val attemptId: String, val n: Int = 0)

/** trend: improving | steady | slipping | calibrating. */
@Serializable data class CoachSkillFacts(
    val skill: String,
    val tests: Int = 0,
    val band: Double? = null,
    val range: List<Double>? = null,
    val trend: String = "calibrating",
    val trendDelta: Double? = null,
    val needMore: Int = 0,
    val gap: Double? = null,
    val series: List<CoachPoint> = emptyList(),
)
@Serializable data class CoachPoint(val at: String = "", val band: Double = 0.0)

/** A repeated weakness. [kind] is e.g. `grammar.articles`, `spelling`, `question-type:Matching headings`. [actions] are ActionType strings (write-task2, write-task1, speak-part, speak-full, listening-test, reading-test, see-evidence, review-deck). */
@Serializable data class CoachCandidate(
    val id: String,
    val skill: String,
    val kind: String,
    val count: Int = 0,
    val tests: Int = 0,
    val share: Double = 0.0,
    val score: Double = 0.0,
    /** Numbers and short strings the copy quotes. */
    val facts: Map<String, JsonPrimitive> = emptyMap(),
    val evidence: CoachEvidence = CoachEvidence(),
    val actions: List<String> = emptyList(),
)

/** source: mistake | upgrade | overused. */
@Serializable data class CoachSwap(
    val id: String, val skill: String, val from: String, val to: List<String> = emptyList(), val count: Int = 0,
    val source: String = "mistake", val evidence: CoachEvidence = CoachEvidence(),
)

/** rule: double | ending | silent | ie-ei | plural | other. */
@Serializable data class CoachSpellGroup(
    val id: String, val rule: String = "other", val count: Int = 0,
    val examples: List<CoachSpellExample> = emptyList(), val evidence: CoachEvidence = CoachEvidence(),
)
@Serializable data class CoachSpellExample(val typed: String = "", val correct: String = "")

@Serializable data class CoachWeekCount(val tests: Int = 0, val minutes: Double = 0.0, val days: Int = 0)
@Serializable data class CoachWeekStats(val tests: Int = 0, val minutes: Double = 0.0, val days: Int = 0, val prev: CoachWeekCount = CoachWeekCount())
@Serializable data class CoachOverall(val band: Double, val range: List<Double> = emptyList())

@Serializable data class CoachFacts(
    val version: Int = 1,
    val generatedFor: String = "",
    val target: Double? = null,
    val examDate: String? = null,
    val daysToExam: Int? = null,
    val totalTests: Int = 0,
    val skills: List<CoachSkillFacts> = emptyList(),
    val overall: CoachOverall? = null,
    val candidates: List<CoachCandidate> = emptyList(),
    val win: CoachCandidate? = null,
    val swaps: List<CoachSwap> = emptyList(),
    val spelling: List<CoachSpellGroup> = emptyList(),
    /** Keyed by skill. */
    val patterns: Map<String, List<CoachCandidate>> = emptyMap(),
    val timing: Map<String, Double> = emptyMap(),
    val reviewDue: Int = 0,
    val weekStats: CoachWeekStats = CoachWeekStats(),
)

/** The student-facing text. [CoachFocus.action] and [CoachPlanDay.action] are ActionType strings. */
@Serializable data class CoachWords(
    val summary: String = "",
    val focus: List<CoachFocus> = emptyList(),
    val win: String? = null,
    val cheat: List<CoachCheat> = emptyList(),
    val plan: List<CoachPlanDay> = emptyList(),
)
@Serializable data class CoachFocus(val candidateId: String, val title: String = "", val why: String = "", val action: String = "", val label: String = "")
@Serializable data class CoachCheat(val candidateId: String, val text: String = "")
@Serializable data class CoachPlanDay(val day: String = "", val text: String = "", val action: String = "")
