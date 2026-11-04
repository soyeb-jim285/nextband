package com.soyeb.ieltspractice.feature.writing

import com.soyeb.ieltspractice.data.Prefs
import com.soyeb.ieltspractice.data.net.Api
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Whose drafts: the signed-in user's id; null when signed out or while /api/me has not loaded (nothing is shown or kept, so two
 * accounts never share an unknown-owner bucket). Callers resolve the id first ([com.soyeb.ieltspractice.data.currentUserId]).
 */
fun draftOwner(api: Api): String? = if (!api.isSignedIn) null else api.me.value?.user?.id

/**
 * Essay + plan per prompt on this device (`draft:{owner}:{promptId}`), so leaving or being killed mid-test loses nothing.
 * A small index per owner (`draft-index:{owner}`) lists the drafts for the hub's "Drafts in progress" rows.
 * Keyed by [owner] (see [draftOwner]) so the next person on the device never sees someone else's essay.
 */
class DraftStore(private val prefs: Prefs, private val owner: () -> String?) {
    private val lock = Mutex()

    suspend fun load(promptId: String): Draft = owner()?.let { decodeDraft(prefs.getString(key(it, promptId))) } ?: Draft()

    /** An empty draft is removed rather than stored (a test that was opened and never written in leaves nothing behind). [meta] adds it to the hub list. */
    suspend fun save(promptId: String, draft: Draft, meta: DraftMeta? = null) = lock.withLock {
        val o = owner() ?: return@withLock
        if (draft.text.isEmpty() && draft.plan.isEmpty()) {
            prefs.setString(key(o, promptId), null)
            writeIndex(o, readIndex(o).filter { it.promptId != promptId })
        } else {
            prefs.setString(key(o, promptId), encodeDraft(draft))
            if (meta != null) {
                val m = meta.copy(words = wordCount(draft.text), updatedAt = System.currentTimeMillis())
                writeIndex(o, listOf(m) + readIndex(o).filter { it.promptId != promptId })
            }
        }
    }

    suspend fun clear(promptIds: List<String>) = lock.withLock {
        val o = owner() ?: return@withLock
        promptIds.forEach { prefs.setString(key(o, it), null) }
        writeIndex(o, readIndex(o).filter { it.promptId !in promptIds })
    }

    /** Every listed draft of the owner (account deletion). */
    suspend fun clearAll() = clear(index().map { it.promptId })

    /** Newest first. */
    suspend fun index(): List<DraftMeta> = lock.withLock { owner()?.let { readIndex(it) }.orEmpty() }

    private fun key(owner: String, promptId: String) = "draft:$owner:$promptId"
    private suspend fun readIndex(owner: String) = decodeIndex(prefs.getString("draft-index:$owner"))
    private suspend fun writeIndex(owner: String, items: List<DraftMeta>) =
        prefs.setString("draft-index:$owner", if (items.isEmpty()) null else encodeIndex(items))
}
