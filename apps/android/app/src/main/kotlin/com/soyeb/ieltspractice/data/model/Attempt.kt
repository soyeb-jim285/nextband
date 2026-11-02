package com.soyeb.ieltspractice.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

// MARK: - Attempts

@Serializable data class Created(val id: String, val uploadUrl: String? = null)

@Serializable data class Attempt(
    val id: String,
    val promptId: String,
    val skill: String,
    val part: Int,
    val mode: String,
    val sessionId: String? = null,
    val parentAttemptId: String? = null,
    val audioUrl: String? = null,
    /** Live examiner: the whole conversation (candidate mixed with the examiner), for playback. */
    val conversationUrl: String? = null,
    val text: String? = null,
    val plan: String? = null,
    val durationMs: Int? = null,
    val overtime: Boolean? = null,
    val status: String, // recording | analyzing | done | failed
    val error: String? = null,
    /** status failed: false when retrying now cannot help (AI credit/key problem): show "try later" instead of Retry. */
    val retryable: Boolean = true,
    /** While analyzing: transcribing | analyzing | feedback | scoring | finalizing; null once done/failed. */
    val stage: String? = null,
    /** Writing, while [stage] is scoring: the feedback that is ready before the scores (AnalysisResult fields without criteria/overall). */
    val partial: JsonElement? = null,
    /** Every top fix is already in the review deck. */
    val topFixesInDeck: Boolean = false,
    val createdAt: String,
    val analysis: AnalysisResult? = null,
    val prompt: Prompt,
) {
    val finished: Boolean get() = status == "done" || status == "failed"

    /** The essay, or the transcript for speaking. */
    val answerText: String? get() = analysis?.text ?: text ?: analysis?.words?.joinToString(" ") { it.w }
}

@Serializable data class AttemptListItem(
    val id: String,
    val promptTitle: String,
    val skill: String,
    val part: Int,
    val mode: String,
    val status: String,
    val overall: Double? = null,
    val createdAt: String,
    val promptId: String = "",
    val durationMs: Int? = null,
    /** offTopic | tooShort (older servers only). */
    val flag: String? = null,
    /** Attempts of one test share it (a mock's Writing and Speaking results open together). */
    val sessionId: String? = null,
)

@Serializable data class AttemptPage(val items: List<AttemptListItem>, val total: Int, val page: Int = 1, val pageSize: Int = 0)

/** GET /api/attempts/{id}/status: poll while analysing. stage: transcribing | analyzing | feedback | scoring | finalizing | null. */
@Serializable data class AttemptStatus(val status: String, val stage: String? = null, val error: String? = null, val retryable: Boolean = true)

// MARK: - Analysis (ai/types.ts)

@Serializable data class Criterion(
    val band: Double, val range: List<Double>, val descriptor: String, val evidence: List<String>, val summary: String,
)
@Serializable data class Fix(val title: String, val why: String, val before: String, val after: String)
@Serializable data class AnalysisError(
    val id: String,
    val category: String,
    val severity: String, // minor | major
    val start: Int,
    val end: Int,
    val original: String,
    val correction: String,
    val explanation: String,
    val time: Double? = null,
)
@Serializable data class VocabUpgrade(val original: String, val better: List<String>, val note: String)
@Serializable data class Word(val w: String, val start: Double, val end: Double, val conf: Double? = null)
@Serializable data class Pause(
    val start: Double, val end: Double, val dur: Double, val kind: String, val midClause: Boolean, val voiced: Boolean,
)
@Serializable data class Filler(val word: String, val time: Double, val kind: String)
@Serializable data class Repetition(val phrase: String, val time: Double, val wordIdx: Int)
@Serializable data class SelfCorrection(val time: Double, val wordIdx: Int)
@Serializable data class Unclear(val wordIdx: Int, val w: String, val conf: Double, val tier: Int)
@Serializable data class WpmPoint(val t: Double, val wpm: Double)

/** Fused disfluency event (core `Disfluency`): [kind] is one of [DisfluencyKinds.all]; [sources] say which detectors saw it. */
@Serializable data class Disfluency(val kind: String, val start: Double, val end: Double, val sources: List<String>)

/** Server `disfluencyProfile`: per-type count and rates. Absent on older analyses. */
@Serializable data class DisfluencyProfile(val byKind: Map<String, DisfluencyRate>)
@Serializable data class DisfluencyRate(val n: Int, val perMin: Double, val per100w: Double)
@Serializable data class FluencyDetail(val events: List<Disfluency>, val profile: DisfluencyProfile? = null)

@Serializable data class SpeechMetrics(
    val durationS: Double,
    val wordCount: Int,
    val speechRate: Double,
    val articulationRate: Double,
    val phonationRatio: Double,
    val pauseRatio: Double,
    val mlr: Double,
    val pauses: List<Pause>,
    val longPauses: Int,
    val midClausePauses: Int,
    val fillers: List<Filler>,
    val fillersPerMin: Double,
    val repetitions: List<Repetition>,
    val selfCorrections: List<SelfCorrection>,
    val unclear: List<Unclear>,
    val wpmSeries: List<WpmPoint>,
    val wpmStdDev: Double,
    val fluency: FluencyDetail? = null, // absent on analyses stored before disfluency fusion
)

@Serializable data class Linker(val word: String, val count: Int, val overused: Boolean)
/** `forms`: every surface form grouped under `word`; absent on analyses stored before grouping existed. */
@Serializable data class Repeated(val word: String, val count: Int, val forms: List<String>? = null)
@Serializable data class TextMetrics(
    val words: Int,
    val sentences: Int,
    val paragraphs: Int,
    val avgSentenceLen: Double,
    val mtld: Double,
    val ttr: Double,
    val linkers: List<Linker>,
    val repeated: List<Repeated>,
)

@Serializable data class PronWord(val word: String, val time: Double, val issue: String, val tip: String)
@Serializable data class PronunciationLlm(val words: List<PronWord>, val prosody: String, val band: Double)

@Serializable data class StructParagraph(val role: String, val topicSentence: String, val ok: Boolean, val note: String)
@Serializable data class StructOverview(val present: Boolean, val mainTrends: Boolean, val noData: Boolean, val note: String)
@Serializable data class StructPosition(val clear: Boolean, val consistent: Boolean, val note: String)
@Serializable data class StructPlan(val followed: Boolean, val note: String)
@Serializable data class WritingStructure(
    val paragraphs: List<StructParagraph>,
    val overview: StructOverview? = null,
    val position: StructPosition? = null,
    val planFollowed: StructPlan? = null,
)

@Serializable data class Rewrite(val text: String, val note: String)
@Serializable data class AnalysisQuestion(val text: String, val startWord: Int)
@Serializable data class Pronunciation(val unclear: List<Unclear>, val llm: PronunciationLlm? = null, val audio: PronunciationAudio? = null)
/** Phoneme-model findings (server PronunciationAudio): accuracy 0-100, the words to practise worst first, sound swaps made more than once. */
@Serializable data class PronunciationAudio(
    val accuracy: Double? = null,
    val words: List<PronAudioWord> = emptyList(),
    val confusions: List<PronConfusion> = emptyList(),
    val wordsPerMinute: Double? = null,
)
/** accent: us | gb | null (the one the model found closer). */
@Serializable data class PronAudioWord(val word: String, val score: Double = 0.0, val accent: String? = null, val start: Double, val end: Double, val phones: List<PronPhone> = emptyList())
@Serializable data class PronPhone(val expected: String, val heard: String, val ok: Boolean)
@Serializable data class PronConfusion(val expected: String, val heard: String, val count: Int)
@Serializable data class Relevance(val questionIdx: Int, val onTopic: Boolean, val note: String, /** none | some | full; absent on older analyses */ val development: String? = null)
@Serializable data class Comparison(val parentAttemptId: String, val parentOverall: Double, val deltas: Map<String, Double>)

@Serializable data class AnalysisResult(
    val skill: String,
    val part: Int? = null,
    val overall: Double,
    val overallRaw: Double,
    val range: List<Double>,
    val calibrated: Boolean? = null, // false: unvalidated model, range ±1 band; absent on older analyses
    val criteria: Map<String, Criterion>,
    val topFixes: List<Fix>,
    val errors: List<AnalysisError>,
    val vocabUpgrades: List<VocabUpgrade>? = null,
    val rewrite: Rewrite,
    // speaking
    val words: List<Word>? = null,
    val metrics: SpeechMetrics? = null,
    val questions: List<AnalysisQuestion>? = null,
    val pronunciation: Pronunciation? = null,
    val relevance: List<Relevance>? = null,
    /** Speaking: bands taken off because some answers did not address the question. */
    val offTopicPenalty: Double? = null,
    val noSpeech: Boolean? = null,
    // writing
    val text: String? = null,
    val structure: WritingStructure? = null,
    val textMetrics: TextMetrics? = null,
    val tooShort: Boolean? = null,
    val comparison: Comparison? = null,
)

