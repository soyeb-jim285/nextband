package com.soyeb.ieltspractice.data.model

import kotlinx.serialization.Serializable

@Serializable data class ReviewCard(val id: String, val front: String, val back: String)

@Serializable data class Mistake(
    val id: String,
    val attemptId: String,
    val skill: String,
    val part: Int,
    val promptTitle: String,
    val category: String,
    val original: String,
    val correction: String,
    val explanation: String,
    val time: Double? = null,
    val inDeck: Boolean = false,
    val createdAt: String,
)

@Serializable data class MistakeLog(val groups: List<CategoryCount>, val items: List<Mistake>, val total: Int, val page: Int = 1, val pageSize: Int = 0)

/** A card as GET /api/cards/due returns it. The scheduling fields are optional so older payloads and fixtures still decode. */
@Serializable data class DueCard(
    val id: String,
    val front: String,
    val back: String,
    val source: String? = null, // mistake | vocab | fix
    val ease: Double? = null,
    val interval: Double? = null, // days
    val reps: Int? = null,
)

/** `{ cards, total, deck }` (also accepts `{ items }`): the due batch, how many are due in all, and the whole deck size. */
@Serializable data class DueResponse(
    val cards: List<DueCard>? = null,
    val items: List<DueCard>? = null,
    val total: Int? = null,
    val deck: Int? = null,
) {
    val list: List<DueCard> get() = cards ?: items ?: emptyList()
    val dueTotal: Int get() = total ?: list.size
}

/** Bodies of POST /api/cards and /api/cards/bulk. [source] is mistake | vocab | fix. */
@Serializable data class CardInput(val front: String, val back: String, val source: String)
