package com.soyeb.ieltspractice.data.model

import com.soyeb.ieltspractice.data.logic.fmtNum
import com.soyeb.ieltspractice.data.net.AppJson
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull

/** One rendered examiner line; [url] is null where the line has no audio (the test then runs silently for that line). */
@Serializable data class AudioLine(val text: String, val url: String? = null)
/** Examiner audio of a generated speaking prompt: [intro] = greeting before the first Part 1 topic of a full test, [lead] = topic or part transition, [questions] align with followUps. */
@Serializable data class PromptAudio(val intro: AudioLine? = null, val lead: AudioLine? = null, val questions: List<AudioLine> = emptyList())

@Serializable data class Prompt(
    val id: String,
    val skill: String,
    val part: Int,
    val variant: String? = null,
    val type: String? = null,
    val topic: String? = null,
    val title: String,
    val body: String,
    val bullets: List<String>? = null,
    val followUps: List<String>? = null,
    /** Raw chart JSON. Use [chartSpec]: a malformed chart decodes to null instead of failing the whole prompt. */
    val chart: JsonElement? = null,
    val imageUrl: String? = null,
    val groupId: String? = null,
    val done: Boolean? = null,
    val source: String? = null,
    /** Cambridge ref, e.g. "C19 T2"; null for generated prompts. */
    val sourceRef: String? = null,
    val audio: PromptAudio? = null,
) {
    /** Questions asked in this prompt's recording (P2: the cue card title). */
    val questions: List<String>
        get() = when {
            skill == "speaking" && part == 2 -> listOf(title)
            !followUps.isNullOrEmpty() -> followUps
            else -> listOf(body)
        }
    val chartSpec: ChartSpec? get() = chart?.let { runCatching { AppJson.decodeFromJsonElement(ChartSpec.serializer(), it) }.getOrNull() }
}

@Serializable data class SpeakingTest(val part1: List<Prompt>, val part2: Prompt, val part3: Prompt)

/**
 * A chart (writing task 1). iOS models this as an enum; here it is one flat class: switch on [kind]
 * (`line` | `bar` | `pie` | `table` | `process` | `map`) and read only that kind's fields.
 */
@Serializable data class ChartSpec(
    val kind: String,
    val title: String = "",
    val xLabel: String = "",
    val yLabel: String = "",
    val unit: String = "",
    val categories: List<String> = emptyList(), // line, bar
    val series: List<Series> = emptyList(), // line, bar
    val pies: List<Pie> = emptyList(), // pie
    val columns: List<String> = emptyList(), // table
    val rows: List<List<JsonPrimitive>> = emptyList(), // table; cells are strings or numbers, read via [rowsText]
    val steps: List<String> = emptyList(), // process
    val before: MapSide? = null, // map
    val after: MapSide? = null, // map
) {
    val isBar: Boolean get() = kind == "bar"

    /** Table cells as display text (whole numbers without a decimal point). */
    val rowsText: List<List<String>>
        get() = rows.map { row ->
            row.map { c -> if (c.isString) c.content else c.doubleOrNull?.let { fmtNum(it) } ?: c.content }
        }
}

@Serializable data class Series(val name: String, val values: List<Double>)
@Serializable data class Slice(val label: String, val value: Double)
@Serializable data class Pie(val name: String, val slices: List<Slice>)
@Serializable data class MapSide(val label: String, val features: List<String>)

/** GET /api/prompts (the question bank). */
@Serializable data class PromptPage(val items: List<Prompt> = emptyList(), val total: Int = 0, val page: Int = 1, val pageSize: Int = 0)

/** GET /api/prompts/meta: the distinct topics and types per skill and part (null entries tolerated). */
@Serializable data class PromptMeta(val groups: List<PromptMetaGroup> = emptyList())
@Serializable data class PromptMetaGroup(val skill: String, val part: Int, val topics: List<String?> = emptyList(), val types: List<String?> = emptyList())
