package com.soyeb.ieltspractice.data.model

import kotlinx.serialization.Serializable

@Serializable data class TrendPoint(
    val date: String, val overall: Double, val criteria: Map<String, Double> = emptyMap(),
    val attemptId: String = "", val skill: String? = null, val part: Int? = null,
)
@Serializable data class Weakest(val key: String, val avg: Double, /** speaking | writing: where it was judged (lr / gra exist in both) */ val skill: String? = null)
@Serializable data class CategoryCount(val category: String, val count: Int)
@Serializable data class Predicted(val speaking: Double? = null, val writing: Double? = null)

@Serializable data class ProgressData(
    val trend: List<TrendPoint>,
    val streak: Int,
    val minutesThisWeek: Double,
    val attempts: Int,
    val weakest: Weakest? = null,
    val topMistakes: List<CategoryCount> = emptyList(),
    val predicted: Predicted = Predicted(),
    /** The most recent submitted attempt, when its analysis failed. */
    val lastFailed: LastFailed? = null,
)
@Serializable data class LastFailed(val id: String, val skill: String)
