package com.soyeb.ieltspractice.feature.writing

import com.soyeb.ieltspractice.data.Prefs
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Essay + plan per prompt on this device (`draft:{promptId}`, the old key names), so leaving or being killed mid-test loses nothing.
 * A small index (`draft-index`) lists the drafts for the hub's "Drafts in progress" rows.
 */
class DraftStore(private val prefs: Prefs) {
    private val lock = Mutex()

    suspend fun load(promptId: String): Draft = decodeDraft(prefs.getString("draft:$promptId"))

    /** An empty draft is removed rather than stored (a test that was opened and never written in leaves nothing behind). [meta] adds it to the hub list. */
    suspend fun save(promptId: String, draft: Draft, meta: DraftMeta? = null) = lock.withLock {
        if (draft == Draft()) {
            prefs.setString("draft:$promptId", null)
            writeIndex(readIndex().filter { it.promptId != promptId })
        } else {
            prefs.setString("draft:$promptId", encodeDraft(draft))
            if (meta != null) {
                val m = meta.copy(words = wordCount(draft.text), updatedAt = System.currentTimeMillis())
                writeIndex(listOf(m) + readIndex().filter { it.promptId != promptId })
            }
        }
    }

    suspend fun clear(promptIds: List<String>) = lock.withLock {
        promptIds.forEach { prefs.setString("draft:$it", null) }
        writeIndex(readIndex().filter { it.promptId !in promptIds })
    }

    /** Newest first. */
    suspend fun index(): List<DraftMeta> = lock.withLock { readIndex() }

    private suspend fun readIndex() = decodeIndex(prefs.getString(INDEX))
    private suspend fun writeIndex(items: List<DraftMeta>) = prefs.setString(INDEX, if (items.isEmpty()) null else encodeIndex(items))

    private companion object { const val INDEX = "draft-index" }
}
