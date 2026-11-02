package com.soyeb.ieltspractice.data.model

import com.soyeb.ieltspractice.data.net.AppJson
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable data class LrOption(val key: String, val text: String = "")
/** Review-only enrichment of a question (after submit): where the answer is, why, why each wrong pick is wrong, wording pairs. */
@Serializable data class LrReview(
    val evidence: String? = null,
    val at: Double? = null,
    val why: String? = null,
    val wrong: Map<String, String>? = null,
    /** `[question wording, passage wording]` pairs */
    val paraphrase: List<List<String>>? = null,
)
@Serializable data class LrQuestion(val n: Int, val text: String? = null, val options: List<LrOption>? = null, val answer: List<String>? = null, val review: LrReview? = null)
@Serializable data class LrVocab(val word: String, val meaning: String = "", val example: String? = null)
@Serializable data class LrGroup(
    val from: Int,
    val to: Int,
    /** gap | mcq | mcq-multi | tfng | ynng | match */
    val type: String,
    val instructions: String = "",
    val wordLimit: String? = null,
    val title: String? = null,
    /** gap groups: markdown (tables allowed) with `{{n}}` placeholders */
    val content: String? = null,
    val options: List<LrOption>? = null,
    val reusable: Boolean? = null,
    /** asset key of a map/plan/diagram image */
    val image: String? = null,
    val questions: List<LrQuestion> = emptyList(),
)
@Serializable data class LrParagraph(val label: String? = null, val text: String)
@Serializable data class LrPassage(val title: String, val subtitle: String? = null, val paragraphs: List<LrParagraph> = emptyList())
@Serializable data class LrSection(
    val part: Int,
    val title: String? = null,
    val audio: String? = null,
    val transcript: String? = null,
    /** After submit: key words of the part */
    val vocab: List<LrVocab>? = null,
    /** After submit, listening: `[word, start s, end s]` rows (see [timingRows]) */
    val timings: List<List<JsonElement>>? = null,
    val passage: LrPassage? = null,
    val groups: List<LrGroup> = emptyList(),
)
@Serializable data class LrTest(
    val slug: String = "",
    val skill: String,
    val variant: String = "academic",
    val source: String = "generated",
    val ref: String = "",
    val title: String,
    val sections: List<LrSection> = emptyList(),
    /** Listening, before submit: when the checking time the recording announces runs out, in seconds into the last part's audio. Null: 2 minutes. */
    val checkEndsAt: Double? = null,
) {
    val listening get() = skill == "listening"
}
@Serializable data class LrMark(val n: Int, val given: String = "", val correct: Boolean = false, val answer: List<String> = emptyList())

/** What the runner measured: seconds per part, answer changes per question, questions answered in the final 5 minutes (or after the recordings, in the exam). */
@Serializable data class LrStats(val partS: Map<String, Double> = emptyMap(), val changes: Map<String, Int> = emptyMap(), val late: List<Int> = emptyList(), val audio: LrAudioState? = null)

/** Practice listening resume state (stats.audio, mirrored on the device): playback position per part in seconds, and the speed. */
@Serializable data class LrAudioState(val pos: Map<String, Double> = emptyMap(), val rate: Double? = null) {
    /** Drops anything the server would reject (non-finite, out of range, unknown speed). */
    fun cleaned() = LrAudioState(
        pos.filter { (k, v) -> k.length == 1 && k[0].isDigit() && v.isFinite() }.mapValues { Math.round(it.value.coerceIn(0.0, 3600.0) * 10) / 10.0 },
        rate?.takeIf { it in RATES },
    )
    fun encode() = AppJson.encodeToString(serializer(), cleaned())

    companion object {
        val RATES = listOf(0.75, 1.0, 1.25)
        /** Where to put the playhead: within 3 s of the end (or unknown) means the part was finished, so start over. */
        fun resumePosition(pos: Double, duration: Double) = if (pos > 1 && duration.isFinite() && duration > 0 && pos < duration - 3) pos else 0.0
        /** This device's copy wins over the server's (it is never older than what this device last played). */
        fun pick(local: String?, server: LrAudioState?): LrAudioState? =
            local?.let { runCatching { AppJson.decodeFromString(serializer(), it).cleaned() }.getOrNull() } ?: server?.cleaned()
    }
}
/** A wrong gap answer with a deterministic reason (server `analysis.gaps`). */
@Serializable data class LrGapEntry(val n: Int, val kind: String, val label: String, val message: String, val word: String? = null, val typed: String? = null, val before: Int? = null,
    /** lost-place: the question this answer belongs to */
    val other: Int? = null,
)
/** Lost marks grouped by cause, largest first. family: slip (had the answer, lost the mark) | trap (picked a distractor) | missed (different detail) | blank */
@Serializable data class LrCause(val family: String, val label: String, val message: String = "", val questions: List<Int> = emptyList())
@Serializable data class LrTfngRow(val n: Int, val kind: String, val chose: String, val answer: String)
@Serializable data class LrTypeAcc(val label: String, val right: Int, val total: Int)
@Serializable data class LrAnalysis(val gaps: List<LrGapEntry> = emptyList(), val tfng: List<LrTfngRow> = emptyList(), val byType: List<LrTypeAcc> = emptyList(), val causes: List<LrCause> = emptyList())

/** `GET /api/lr/progress` */
@Serializable data class LrTrendPoint(val attemptId: String = "", val skill: String, val date: String, val band: Double)
@Serializable data class LrTypeStat(val skill: String, val label: String, val right: Int, val total: Int)
@Serializable data class LrSuggested(val id: String, val title: String, val skill: String, val label: String, val count: Int)
@Serializable data class LrTfngPattern(val kind: String = "tfng", val answer: String = "", val chose: String = "", val count: Int = 0, val of: Int = 0, val pct: Int = 0, val text: String = "")
@Serializable data class LrTfngSummary(val pattern: LrTfngPattern? = null, val rows: Int = 0)
/** Lost marks by cause across past attempts, per skill, largest first. */
@Serializable data class LrCauseStat(val skill: String, val family: String, val label: String, val count: Int)
@Serializable data class LrProgress(
    val trend: List<LrTrendPoint> = emptyList(),
    val byType: List<LrTypeStat> = emptyList(),
    val weakest: List<LrTypeStat> = emptyList(),
    val suggested: LrSuggested? = null,
    val tfng: LrTfngSummary = LrTfngSummary(),
    val causes: List<LrCauseStat> = emptyList(),
)
/** `GET /api/lr/spelling`: words misspelt (or wrongly pluralised) in gap answers, most frequent first. */
@Serializable data class LrSpellingItem(val word: String, val kind: String = "spelling", val count: Int = 1, val typed: List<String> = emptyList(), val lastAt: String = "")
@Serializable data class LrSpelling(val items: List<LrSpellingItem> = emptyList())

/** `GET/POST /api/lr/attempts...`: the stripped test while in progress, the full test, marks and transcripts once submitted. */
@Serializable data class LrAttempt(
    val id: String,
    val testId: String = "",
    val mode: String = "practice",
    /** Chosen parts; null = the whole test. Partial attempts have no band. */
    val parts: List<Int>? = null,
    val status: String = "in_progress",
    val responses: Map<String, String> = emptyMap(),
    val elapsedS: Int = 0,
    val startedAt: String = "",
    val submittedAt: String? = null,
    val raw: Int? = null,
    val total: Int? = null,
    val band: Double? = null,
    val marks: List<LrMark>? = null,
    val test: LrTest,
    /** Asset key to presigned GET URL (audio supports Range). */
    val assets: Map<String, String> = emptyMap(),
    val stats: LrStats? = null,
    /** Null for attempts submitted before the analysis existed. */
    val analysis: LrAnalysis? = null,
) {
    val submitted get() = status == "submitted"
    val exam get() = mode == "exam"
}

@Serializable data class LrTestItem(
    val id: String,
    val slug: String = "",
    val skill: String,
    val variant: String = "academic",
    val source: String = "generated",
    val ref: String = "",
    val title: String,
    val total: Int = 40,
    /** new | in_progress | submitted (from the latest attempt) */
    val status: String = "new",
    val attemptId: String? = null,
    val mode: String? = null,
    /** Parts of the in-progress attempt (null = whole test or none). */
    val parts: List<Int>? = null,
    val answered: Int = 0,
    val bestBand: Double? = null,
    val attempts: Int = 0,
)

@Serializable data class LrAttemptItem(
    val id: String,
    val testId: String = "",
    val skill: String,
    val variant: String = "academic",
    val ref: String = "",
    val title: String,
    val mode: String = "practice",
    val parts: List<Int>? = null,
    val status: String = "in_progress",
    val raw: Int? = null,
    val total: Int? = null,
    val band: Double? = null,
    val answered: Int = 0,
    val startedAt: String = "",
    val submittedAt: String? = null,
)

@Serializable data class LrSaved(val savedAt: String = "")

/** `GET /api/lr/tests/{id}`: the stripped test and its asset URLs (no answers). */
@Serializable data class LrTestDetail(val id: String, val test: LrTest, val assets: Map<String, String> = emptyMap())
