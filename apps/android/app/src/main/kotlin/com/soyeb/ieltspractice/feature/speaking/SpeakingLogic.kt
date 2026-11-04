package com.soyeb.ieltspractice.feature.speaking

import com.soyeb.ieltspractice.data.logic.Band
import com.soyeb.ieltspractice.data.model.AttemptListItem
import com.soyeb.ieltspractice.data.model.Prompt
import com.soyeb.ieltspractice.data.net.AppJson
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

// Pure helpers for the speaking screens (no Compose, unit-tested in SpeakingLogicTest). Port of the old
// ui/screens/speaking/SpeakingLogic.kt plus the result-side helpers that were not ported to data/logic (pronTip, parseDictionary).

const val PREP_SECONDS = 60
const val P2_MAX_SECONDS = 120.0
/** 15 min caps the energy timeline (20k x 50 ms) for any part. */
const val MAX_RECORDING_SECONDS = 900.0

enum class Zone { Brand, Warn, Good }

/** Ring colour by the part's target zone (web zoneTone; P2 is only green from 1:30). Teal = keep going, green = in zone, amber = long. */
fun zoneTone(part: Int, s: Double): Zone = when (part) {
    2 -> if (s < 60) Zone.Brand else if (s < 90) Zone.Warn else if (s <= 120) Zone.Good else Zone.Warn
    3 -> if (s < 30) Zone.Brand else if (s <= 60) Zone.Good else Zone.Warn
    else -> if (s < 15) Zone.Brand else if (s <= 40) Zone.Good else Zone.Warn
}

fun zoneHint(part: Int, s: Int): String {
    val lo = if (part == 2) 60 else if (part == 3) 30 else 15
    val hi = if (part == 2) 120 else if (part == 3) 60 else 40
    if (s < lo) return "Aim for $lo-$hi s"
    if (part == 2 && s < 90) return "Good. Keep going to 1:30 or more"
    return if (s <= hi) "In the target zone" else "Time to wrap up"
}

/** The ring's full circle: P2 2:00, P3 1:00, P1 0:40. */
fun ringMax(part: Int): Double = if (part == 2) P2_MAX_SECONDS else if (part == 3) 60.0 else 40.0

/** A resumed mock Speaking test starts at the first question not answered yet, so answered parts are never retaken (and charged) again. -1 = all answered. */
fun resumeIndex(items: List<Prompt>, answered: Set<String>): Int = items.indexOfFirst { it.id !in answered }

/** Prompts this session answered on the server. A "recording" attempt has no audio there yet (its upload failed somewhere), so it is still open. */
fun answeredOnServer(attempts: List<AttemptListItem>, sessionId: String): Set<String> =
    attempts.filter { it.sessionId == sessionId && it.status != "recording" }.map { it.promptId }.toSet()

/** "Part 1, 2 of 3" for a Part 1 topic in a multi-topic test, else "Part N". */
fun partLabel(items: List<Prompt>, i: Int): String {
    val p = items.getOrNull(i) ?: return "Speaking"
    val p1Count = items.count { it.part == 1 }
    if (p.part == 1 && p1Count > 1) return "Part 1, ${items.take(i + 1).count { it.part == 1 }} of $p1Count"
    return "Part ${p.part}"
}

/** The interstitial between parts: "Part 2" over "Long turn" and what happens next. */
fun partName(part: Int): String = when (part) { 1 -> "Interview"; 2 -> "Long turn"; else -> "Discussion" }

fun partBlurb(part: Int): String = when (part) {
    1 -> "Short answers about you, your home, work or studies."
    2 -> "A cue card, one minute to prepare, then up to two minutes of talking."
    else -> "Broader questions linked to Part 2. Give reasons and examples."
}

fun exitMessage(recording: Boolean, notDone: Boolean): String = listOfNotNull(
    if (recording) "The answer you are recording now will be discarded." else null,
    if (notDone) "Recordings that have not uploaded stay on this device. Upload them from the Speaking page." else null,
    "Answers already uploaded are still analysed.",
).joinToString(" ")

fun finishProgress(total: Int, done: Int, failed: Int): String = when {
    failed > 0 -> "$done of $total uploaded, $failed failed"
    done == total -> "$total of $total uploaded"
    else -> "Uploading ${minOf(done + 1, total)} of $total"
}

/**
 * The server's cue-card body repeats the title and ends with a "You should say:" lead; the bullets get their own heading, so drop both.
 * With bullets, the closing "and explain…" line goes after them (see [cueClosing]), as on a real cue card.
 */
fun cueIntro(p: Prompt): String = cueLines(p).filterNot { !p.bullets.isNullOrEmpty() && isClosing(it) }.joinToString("\n")

/** The "and explain…" line that ends a cue card, shown after the bullets; empty without bullets (it stays in [cueIntro]). */
fun cueClosing(p: Prompt): String = if (p.bullets.isNullOrEmpty()) "" else cueLines(p).filter(::isClosing).joinToString("\n")

private fun isClosing(line: String) = line.startsWith("and ", ignoreCase = true)

private fun cueLines(p: Prompt): List<String> {
    val bullets = p.bullets.orEmpty()
    var text = p.body
    if (text.startsWith(p.title)) text = text.removePrefix(p.title)
    return text.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
        .filter { line -> bullets.isEmpty() || line.lowercase().trim(':', ' ') != "you should say" }
}

/** Part 3 rows carry two sub-topic headings in bullets; the first half of the questions sits under the first. */
fun subTopic(p: Prompt, question: Int): String? {
    val b = p.bullets
    return if (p.part == 3 && b != null && b.size == 2 && p.questions.isNotEmpty()) b[minOf(1, question * 2 / p.questions.size)] else null
}

/** "just now", "5 minutes ago", "3 hours ago", "2 days ago" (web formatRelative). */
fun relativeTime(millis: Long, now: Long = System.currentTimeMillis()): String {
    val min = (now - millis) / 60_000
    return when {
        min < 1 -> "just now"
        min < 60 -> "$min ${if (min == 1L) "minute" else "minutes"} ago"
        min < 24 * 60 -> (min / 60).let { "$it ${if (it == 1L) "hour" else "hours"} ago" }
        else -> (min / (24 * 60)).let { "$it ${if (it == 1L) "day" else "days"} ago" }
    }
}

/** What the mic check says: 0 = say something, 1 = too quiet, 2 = heard (needs ~1 s of speech-level input). */
fun micCheckLevel(loud: Int, soft: Int): Int = if (loud >= 20) 2 else if (soft >= 20) 1 else 0

/** One row of "recent results": a single attempt, or the parts of one session folded together. */
data class RecentRow(val ids: List<String>, val title: String, val overall: Double?, val createdAt: String, val parts: Int)

/** Newest first. Attempts that share a session id fold into one "Full test" row (band = mean of the scored parts, rounded like a band). */
fun groupRecent(items: List<AttemptListItem>): List<RecentRow> {
    val out = mutableListOf<RecentRow>()
    val seen = mutableSetOf<String>()
    for (a in items) {
        val sid = a.sessionId
        if (sid == null) {
            out += RecentRow(listOf(a.id), if (a.part == 1) "Part 1: ${a.promptTitle}" else a.promptTitle, a.overall, a.createdAt, 1)
            continue
        }
        if (!seen.add(sid)) continue
        val parts = items.filter { it.sessionId == sid }.sortedBy { it.part }
        if (parts.size == 1) {
            out += RecentRow(listOf(a.id), a.promptTitle, a.overall, a.createdAt, 1)
            continue
        }
        val scored = parts.mapNotNull { it.overall }
        out += RecentRow(
            parts.map { it.id }, "Full test", scored.takeIf { it.isNotEmpty() }?.let { Band.round(it.average()) },
            parts.maxOf { it.createdAt }, parts.size,
        )
    }
    return out
}

// MARK: Pronunciation (moved from the old PronunciationPanel)

/** Human recordings and IPA from Wiktionary, through the Free Dictionary API (free, no key). Names and rare forms are often missing. */
data class DictEntry(val uk: String? = null, val us: String? = null, val ipa: String? = null)

/** The Free Dictionary API answer to a [DictEntry]: the -uk / -us recordings (any recording counts as US when there is no UK one), the first IPA. */
fun parseDictionary(json: String): DictEntry {
    val entries = runCatching { AppJson.parseToJsonElement(json).jsonArray.map { it.jsonObject } }.getOrNull() ?: return DictEntry()
    val ph = entries.flatMap { e -> e["phonetics"]?.let { runCatching { it.jsonArray.map { p -> p.jsonObject } }.getOrNull() }.orEmpty() }
    fun str(o: Map<String, JsonElement>, k: String) = runCatching { o[k]?.jsonPrimitive?.contentOrNull }.getOrNull()?.takeIf { it.isNotEmpty() }
    fun audio(accent: String) = ph.firstNotNullOfOrNull { p -> str(p, "audio")?.takeIf { it.endsWith("-$accent.mp3") } }
    val uk = audio("uk")
    val us = audio("us") ?: if (uk != null) null else ph.firstNotNullOfOrNull { str(it, "audio") }
    val ipa = entries.firstOrNull()?.let { str(it, "phonetic") } ?: ph.firstNotNullOfOrNull { str(it, "text") }
    return DictEntry(uk, us, ipa)
}

/** How to make the sound that was expected, for the swaps learners make most. Keyed by espeak IPA. */
private val TIPS = mapOf(
    "θ" to "Put your tongue lightly between your teeth and blow air out. No stop, no burst.",
    "ð" to "Tongue lightly between your teeth, then add your voice so it buzzes.",
    "v" to "Touch your top teeth to your bottom lip and let it buzz. Rounded lips make a /w/ instead.",
    "w" to "Round your lips into a small circle, then open them. Your teeth do not touch your lip.",
    "f" to "Top teeth on your bottom lip, then blow. Your lips do not close.",
    "p" to "Close both lips, then release a puff of air.",
    "r" to "Curl your tongue back without touching the roof of your mouth. Do not roll or tap it.",
    "ɹ" to "Curl your tongue back without touching the roof of your mouth. Do not roll or tap it.",
    "l" to "Press the tip of your tongue behind your top teeth and let the air pass at the sides.",
    "ɪ" to "A short, relaxed sound, as in \"sit\". Do not stretch it into \"seat\".",
    "iː" to "A long sound with spread lips, as in \"seat\". Hold it a little longer than \"sit\".",
    "æ" to "Open your mouth wide and spread your lips, as in \"cat\". Not \"ket\".",
    "e" to "Mouth half open, as in \"bed\". Less open than \"bad\".",
    "ɛ" to "Mouth half open, as in \"bed\". Less open than \"bad\".",
    "ʌ" to "Short and central, as in \"cup\". Your lips stay relaxed, not rounded.",
    "ɒ" to "Short with rounded lips and an open mouth, as in \"hot\" (UK).",
    "ʃ" to "Round your lips a little and push them forward, as in \"she\". Flat lips make an /s/.",
    "s" to "Tongue close behind your top teeth, lips spread, a thin hiss.",
    "z" to "The /s/ position with your voice on, so it buzzes.",
    "ʒ" to "The /ʃ/ position with your voice on, as in \"measure\".",
    "dʒ" to "Start with the tongue touching behind your teeth, then release into a buzzing \"zh\", as in \"job\".",
    "tʃ" to "Start with the tongue touching behind your teeth, then release into \"sh\", as in \"chip\".",
    "ŋ" to "Lift the back of your tongue to the soft palate and let the sound go through your nose. Do not add a /g/ at the end.",
    "h" to "A light breath from the throat, as in \"hat\". Do not drop it.",
)

/** The tip for the first wrong sound that has one. */
fun pronTip(w: com.soyeb.ieltspractice.data.model.PronAudioWord): String? =
    w.phones.firstOrNull { !it.ok && TIPS[it.expected] != null }?.let { TIPS[it.expected] }

/** The session mode for a prompt's part, for "Practise this again". */
fun modeForPart(part: Int): String = "part${part.coerceIn(1, 3)}"
