package com.soyeb.ieltspractice.data.model

import kotlinx.serialization.Serializable

/** POST /api/live/start. */
@Serializable data class LiveStart(val sessionId: String, val test: SpeakingTest)
@Serializable data class UploadTarget(val key: String, val uploadUrl: String)
@Serializable data class FinishResult(val attemptIds: List<String>)

/** POST /api/live/livekit/start: where and how to join the examiner's room. */
@Serializable data class LiveKitJoin(val url: String, val token: String)
/** POST /api/live/part. */
@Serializable data class LivePartCreated(val attemptId: String)
