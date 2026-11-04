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
    /** Mistake category (grammar.article, spelling, ...); null for vocab and older cards. */
    val category: String? = null,
    /** The test the card came from (spelling cards use it to find the word in the recording). */
    val attemptId: String? = null,
    val origin: CardOrigin? = null,
)

/** The Speaking/Writing test a drill card came from. */
@Serializable data class CardOrigin(val skill: String, val part: Int, val title: String, val at: String)

/** GET /api/mistakes/patterns: each category as a pattern, plus drill cards per category. */
@Serializable data class MistakePatterns(val patterns: List<MistakePattern> = emptyList(), val deck: List<DeckCategory> = emptyList())

/** [recent]/[before]: mistakes per test over the newest 3 tests and the 3 before them (null under 4 tests). */
@Serializable data class MistakePattern(
    val category: String,
    val count: Int,
    val tests: Int,
    val outOf: Int,
    val lastAt: String,
    val recent: Double? = null,
    val before: Double? = null,
    val cards: Int = 0,
    val due: Int = 0,
)

@Serializable data class DeckCategory(val category: String, val cards: Int, val due: Int)

/** GET /api/mistakes/recurring/{attemptId}: this test's categories, how many earlier tests ([outOf]) had them, and how many cards it added. */
@Serializable data class Recurring(val items: List<RecurringItem> = emptyList(), val added: Int = 0)
@Serializable data class RecurringItem(val category: String, val here: Int, val before: Int, val outOf: Int)

/** GET /api/lr/cards/{id}/clip: the word's moment in the test recording, in seconds. */
@Serializable data class CardClip(val url: String, val from: Double, val to: Double)

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
@Serializable data class CardInput(val front: String, val back: String, val source: String, val category: String? = null)
