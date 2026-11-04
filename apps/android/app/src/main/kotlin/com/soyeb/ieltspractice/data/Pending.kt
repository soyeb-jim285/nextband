package com.soyeb.ieltspractice.data

import com.soyeb.ieltspractice.data.logic.GateText
import com.soyeb.ieltspractice.data.model.Created
import com.soyeb.ieltspractice.data.net.Api
import com.soyeb.ieltspractice.data.net.ApiError
import com.soyeb.ieltspractice.data.net.AppJson
import com.soyeb.ieltspractice.data.net.submitBody
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import java.io.IOException

/**
 * A finished speaking recording that has not reached the server yet. The m4a and this metadata live in app storage from the
 * moment recording stops until the upload is confirmed, so a failed upload, a relaunch or a killed app never loses it
 * (port of iOS PendingUploads.swift; web: hooks/pendingRecordings.ts).
 */
/** When one question's answer window ran, in ms on the recording clock (which stands still while the examiner talks). */
@Serializable data class AnswerWindow(val q: Int, val startMs: Int, val endMs: Int)

@Serializable data class PendingRecording(
    val id: String,
    val promptId: String,
    val part: Int,
    val label: String,
    val createdAt: Long, // epoch millis
    val durationMs: Int,
    val energy: List<Int>,
    val marks: List<Int>,
    val segments: List<AnswerWindow> = emptyList(),
    val sessionId: String? = null,
    val parentAttemptId: String? = null,
    /** Full mock test: sent with the attempt so the server links it to the mock (its Speaking session id is [sessionId]). */
    val mockId: String? = null,
    // Upload progress, so a retry resumes at the step that failed.
    val attemptId: String? = null,
    val uploadUrl: String? = null,
    val uploaded: Boolean = false,
    /** The user id that recorded it: another account on this device never sees or uploads it (it would land in their results). */
    val ownerId: String? = null,
)

/**
 * Listed and uploaded only for the account that made it (a guest's recordings are re-owned by the account it signs into: [PendingStore.reassign]).
 * No owner (recorded before /api/me answered, e.g. offline): the signed-in user, and the upload stamps that user as the owner.
 * Unowned recordings are deleted on sign-out ([PendingStore.dropUnowned]), so they never reach a different account.
 */
fun PendingRecording.belongsTo(userId: String?): Boolean =
    if (ownerId == null) userId != null else ownerId == userId

/** The signed-in user's id, loading /api/me when it isn't known yet; null when signed out or offline. */
suspend fun currentUserId(api: Api): String? = api.me.value?.user?.id ?: runCatching { api.loadMe().user.id }.getOrNull()

sealed interface UploadState {
    data object Uploading : UploadState
    data class Failed(val message: String) : UploadState
    data class Done(val attemptId: String) : UploadState
}

/**
 * Owns the pending recordings and their uploads. Uploads run on [scope] (the app scope), so leaving a screen never cancels them.
 * Wiring: `PendingStore(File(context.filesDir, "PendingRecordings"), appScope)` (see AppGraph).
 */
class PendingStore(private val dir: File, private val scope: CoroutineScope) {
    companion object { const val ORPHAN_GRACE_MS = 6 * 60 * 60 * 1000L }

    private val _items = MutableStateFlow<List<PendingRecording>>(emptyList())
    val items: StateFlow<List<PendingRecording>> = _items.asStateFlow()

    /** Upload state per recording id for this launch (a recording with no entry has not been tried yet). */
    private val _states = MutableStateFlow<Map<String, UploadState>>(emptyMap())
    val states: StateFlow<Map<String, UploadState>> = _states.asStateFlow()

    // Off the main thread: AppGraph is built before the first frame, and a backlog of recordings means a disk scan plus JSON parsing.
    init { scope.launch(Dispatchers.IO) { reload() } }

    fun audioFile(id: String) = File(dir, "$id.m4a")
    private fun metaFile(id: String) = File(dir, "$id.json")

    /** Call before recording into [audioFile]. */
    fun prepare() { dir.mkdirs() }

    /**
     * Re-read the disk (the hub calls this when it appears). An m4a with no metadata (app killed mid-recording) can never be listed
     * or uploaded, so it is deleted once it is older than [ORPHAN_GRACE_MS] (a recording in progress is written to until it stops).
     */
    fun reload() {
        val cutoff = System.currentTimeMillis() - ORPHAN_GRACE_MS
        dir.listFiles().orEmpty()
            .filter { it.extension == "m4a" && !metaFile(it.nameWithoutExtension).exists() && it.lastModified() < cutoff }
            .forEach { it.delete() }
        val disk = (dir.listFiles().orEmpty())
            .filter { it.extension == "json" }
            .mapNotNull { runCatching { AppJson.decodeFromString(PendingRecording.serializer(), it.readText()) }.getOrNull() }
            .filter { audioFile(it.id).exists() }
        // A recording added while the scan ran keeps its newer in-memory copy.
        _items.update { cur -> (cur + disk.filter { d -> cur.none { it.id == d.id } }).sortedByDescending { it.createdAt } }
    }

    /** Keep a finished recording (its m4a is already at [audioFile]). A failed metadata write still keeps it for this launch. */
    fun add(p: PendingRecording) { runCatching { persist(p) }; keep(p) }

    /** Upload progress: throws when the disk refuses, so the queue never runs ahead of what a relaunch would read back. */
    private fun write(p: PendingRecording) { persist(p); keep(p) }

    private fun persist(p: PendingRecording) {
        dir.mkdirs()
        // Temp file, then rename over the old one: a full disk or a kill mid-write leaves the last good metadata, never a truncated file.
        val tmp = File(dir, "${p.id}.json.tmp")
        tmp.writeText(AppJson.encodeToString(PendingRecording.serializer(), p))
        if (!tmp.renameTo(metaFile(p.id))) { tmp.delete(); throw IOException("Couldn't save the recording on this device.") }
    }

    private fun keep(p: PendingRecording) =
        _items.update { list -> if (list.any { it.id == p.id }) list.map { if (it.id == p.id) p else it } else listOf(p) + list }

    /** A guest signed into [to]: the server linked its data there, so its recordings on this device now belong to that account. */
    fun reassign(from: String, to: String) {
        reload() // the hub may not have read the disk yet this launch
        _items.value.filter { it.ownerId == from }.forEach { add(it.copy(ownerId = to)) }
    }

    /** Signed out: a recording whose owner was never learned must not be claimable by the next account on this device. */
    fun dropUnowned() {
        reload()
        _items.value.filter { it.ownerId == null }.forEach { remove(it.id) }
    }

    fun remove(id: String) {
        audioFile(id).delete()
        metaFile(id).delete()
        _items.update { l -> l.filterNot { it.id == id } }
    }

    /** Delete the local copy and the half-created attempt on the server. */
    suspend fun discard(p: PendingRecording, api: Api) {
        p.attemptId?.let { runCatching { api.raw("DELETE", "/api/attempts/$it") } }
        remove(p.id)
        _states.update { it - p.id }
    }

    /** Fire and forget; watch [states]. */
    fun start(p: PendingRecording, api: Api) { scope.launch { upload(p, api) } }

    /**
     * Create the attempt, PUT the audio, submit. Progress is saved after each step so a retry resumes where it failed.
     * Returns the attempt id on success (the local copy is then removed), null on failure or when already uploading.
     */
    suspend fun upload(rec: PendingRecording, api: Api): String? {
        var busy = false
        _states.update { s -> if (s[rec.id] is UploadState.Uploading) { busy = true; s } else { busy = false; s + (rec.id to UploadState.Uploading) } }
        if (busy) return null
        var p = _items.value.firstOrNull { it.id == rec.id } ?: rec
        val uid = currentUserId(api)
        if (!p.belongsTo(uid)) {
            _states.update { it + (p.id to UploadState.Failed("This recording belongs to another account. Sign in to it to upload, or delete it.")) }
            return null
        }
        if (p.ownerId == null) p = p.copy(ownerId = uid).also(::add)
        // Creating an attempt reserves nothing (the quota is taken at submit), so a fresh one can replace an expired upload URL.
        suspend fun create(from: PendingRecording): PendingRecording {
            val created: Created = api.send("POST", "/api/attempts", buildJsonObject {
                put("promptId", from.promptId); put("skill", "speaking"); put("part", from.part); put("mode", "practice")
                put("audioContentType", "audio/mp4")
                from.sessionId?.let { put("sessionId", it) }
                from.mockId?.let { put("mockId", it) }
                from.parentAttemptId?.let { put("parentAttemptId", it) }
            })
            val url = created.uploadUrl ?: throw ApiError(0, "The server didn't return an upload URL.")
            return from.copy(attemptId = created.id, uploadUrl = url).also(::write)
        }
        return try {
            if (p.attemptId == null) p = create(p)
            if (!p.uploaded) {
                val url = p.uploadUrl ?: throw ApiError(0, "The server didn't return an upload URL.")
                try {
                    api.upload(url, audioFile(p.id), "audio/mp4")
                } catch (e: ApiError) {
                    // The presigned PUT lives 5-15 minutes; a later retry gets 400/401/403. Swap the half-made attempt for a fresh one.
                    if (e.status !in 400..403) throw e
                    p.attemptId?.let { runCatching { api.deleteAttempt(it) } }
                    p = create(p.copy(attemptId = null, uploadUrl = null))
                    api.upload(p.uploadUrl!!, audioFile(p.id), "audio/mp4")
                }
                p = p.copy(uploaded = true).also(::write)
            }
            val attemptId = p.attemptId ?: throw ApiError(0, "Couldn't start the upload.")
            try {
                api.raw("POST", "/api/attempts/$attemptId/submit", submitBody(p.durationMs, p.energy, p.marks, p.segments))
            } catch (e: ApiError) {
                // 409: an earlier submit already went through and only its response was lost.
                if (e.status != 409) throw e
            }
            remove(p.id)
            _states.update { it + (p.id to UploadState.Done(attemptId)) }
            attemptId
        } catch (e: CancellationException) {
            _states.update { it - p.id }
            throw e
        } catch (e: Exception) {
            // A refused test (no tests left, scoring paused) says so and that the recording is safe; the retry button works once there is room.
            val msg = if (e is ApiError && e.isLimit) GateText.sentence(e, "Your recording is saved on this device.") else e.message ?: "Upload failed."
            _states.update { it + (p.id to UploadState.Failed(msg)) }
            null
        }
    }
}
