package com.soyeb.ieltspractice.data.net

import com.soyeb.ieltspractice.data.AnswerWindow
import com.soyeb.ieltspractice.data.TokenStore
import com.soyeb.ieltspractice.data.model.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.serializer
import okhttp3.Call
import okhttp3.Callback
import okhttp3.CookieJar
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** A failed request. [status] is 0 for client-side failures (offline, bad response). [message] is safe to show the user. [code] is Better Auth's error code (INVALID_OTP, ...). */
class ApiError(
    val status: Int, override val message: String, val code: String? = null,
    /** quota_exceeded extras (docs/community.md): when the window resets (ISO), which skill, and the caller's tier. */
    val resetAt: String? = null, val skill: String? = null, val tier: String? = null,
) : Exception(message) {
    /** One of the "you can't start or finish a test now" codes (quota, scoring paused, busy, too fast, live examiner unavailable). */
    val isLimit: Boolean get() = code in Codes.limits
}

class ApiResponse(val status: Int, val body: String, val headers: Headers)

private val JSON_MEDIA = "application/json".toMediaType()

/**
 * Typed client for the NextBand API (port of iOS APIClient.swift). Auth is Better Auth's bearer plugin: the token comes
 * back in the `set-auth-token` header on sign-in, lives in [TokenStore] and is sent as `Authorization: Bearer`.
 *
 * Reads: `api.get<Progress>("/api/progress")`, `api.getList<Prompt>("/api/prompts")` (accepts a bare list or `{items|cards|models|data}`).
 * Writes: `api.send<Created>("POST", "/api/attempts", buildJsonObject { put("promptId", id) })`.
 * Observe [token] / [me] / [signedIn] / [isGuest] / [hasAccount] with `collectAsState()`. The typed endpoint functions are below; [get], [send] and [raw] are the escape hatch.
 *
 * [interceptor] is how demo mode answers from fixtures (see Demo.kt); [initialToken] pre-signs-in the demo.
 */
class Api(
    val baseUrl: String = SERVER,
    private val store: TokenStore,
    interceptor: Interceptor? = null,
    initialToken: String? = null,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    companion object {
        /** The production server. Fixed in the app; there is no user-facing server setting (matches iOS). */
        const val SERVER = "https://ielts.soyebjim.me"
        const val SESSION_CHANGED = "You signed out or switched account. Your recording is saved on this device; sign back in to upload it."
    }

    private val http = OkHttpClient.Builder()
        .cookieJar(CookieJar.NO_COOKIES) // bearer only: cookies would trigger Better Auth's browser origin checks
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .apply { interceptor?.let { addInterceptor(it) } }
        .build()

    private val _token = MutableStateFlow(initialToken)
    /** The bearer token: an account's, or a guest's (see [hasAccount]). */
    val token: StateFlow<String?> = _token.asStateFlow()
    /** A session exists (guest or account). Tests need one; it is created on the first test start ([ensureSession]). */
    val isSignedIn: Boolean get() = _token.value != null
    private val _signedIn = MutableStateFlow(initialToken != null)
    /** Observable [isSignedIn]: a session exists (guest or account). */
    val signedIn: StateFlow<Boolean> = _signedIn.asStateFlow()
    private val _isGuest = MutableStateFlow(false)
    /** A guest session exists (no account yet). False when signed out or signed in with an account. */
    val isGuest: StateFlow<Boolean> = _isGuest.asStateFlow()

    private val _account = MutableStateFlow(initialToken != null)
    /** True only for a real account. False for no session and for a guest session: History, Mistakes and Review are account-only. */
    val hasAccount: StateFlow<Boolean> = _account.asStateFlow()
    private var guest = false

    private fun setSession(token: String?, isGuest: Boolean) {
        guest = isGuest
        _token.value = token
        _signedIn.value = token != null
        _isGuest.value = token != null && isGuest
        _account.value = token != null && !isGuest
    }

    // A channel, not a SharedFlow: a 401 at launch (restoreSession) comes before AppShell collects, and must still be told once.
    private val _sessionEnded = Channel<Boolean>(Channel.CONFLATED)
    /** The server dropped the session (401: expired or purged). True when it was an account, false for a guest. AppShell tells the user. */
    val sessionEnded: Flow<Boolean> = _sessionEnded.receiveAsFlow()

    private val _quota = MutableStateFlow<Quota?>(null)
    /** Tests left per skill and whether the live examiner is set up. Null until first loaded. */
    val quota: StateFlow<Quota?> = _quota.asStateFlow()
    private var quotaAt = 0L
    private val meLoads = AtomicInteger(0) // /api/me requests in flight
    private val sessionLock = Mutex()

    /** False until the stored token has been read at launch, so the UI doesn't flash the guest state for a signed-in user. */
    private val _ready = MutableStateFlow(initialToken != null)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    private val _me = MutableStateFlow<Me?>(null)
    val me: StateFlow<Me?> = _me.asStateFlow()

    /** Requests in flight. Screenshot tests wait for 0 before capturing. */
    val inflight = AtomicInteger(0)

    /** Load the stored token and the account (call once at launch). A guest simply stays signed out. */
    suspend fun restoreSession() {
        if (_token.value == null) store.load()?.let { setSession(it, store.loadGuest()) }
        _ready.value = true
        if (_token.value != null && _me.value == null) runCatching { loadMe() }
    }

    // MARK: Requests

    /** [asToken]: send only with this session's token; another session (signed out or switched account since) throws [ApiError] 0 unsent. */
    suspend fun raw(method: String, path: String, body: JsonElement? = null, asToken: String? = null, extraHeaders: Map<String, String> = emptyMap()): ApiResponse {
        val payload = when {
            body != null -> AppJson.encodeToString(JsonElement.serializer(), body).toRequestBody(JSON_MEDIA)
            method == "GET" || method == "HEAD" || method == "DELETE" -> null
            else -> "{}".toRequestBody(JSON_MEDIA)
        }
        // The token this request carries: a late 401/403 for an older session must not end the one adopted since.
        val sent = _token.value
        if (asToken != null && sent != asToken) throw ApiError(0, SESSION_CHANGED)
        val req = Request.Builder().url(baseUrl + path).method(method, payload)
            .apply { sent?.let { header("Authorization", "Bearer $it") }; extraHeaders.forEach { (k, v) -> header(k, v) } }
            .build()
        val (status, text, headers) = execute(req, "Can't reach NextBand. Check your internet connection and try again.")
        if (status !in 200..299) {
            val err = runCatching { AppJson.parseToJsonElement(text) as? JsonObject }.getOrNull()
            fun field(k: String) = (err?.get(k) as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull
            if (status == 401 && sent != null && _token.value == sent && !path.startsWith("/api/auth/")) {
                val wasAccount = _account.value
                signOutLocal(accountExpired = wasAccount)
                _sessionEnded.trySend(wasAccount)
            }
            // A guest on an account-only endpoint is not an expired session: flip the UI to its "Create an account" gates.
            if (status == 403 && field("code") == Codes.ACCOUNT && sent != null && _token.value == sent && _account.value) { _account.value = false; _isGuest.value = true; guest = true }
            throw ApiError(status, field("error") ?: field("message") ?: httpReason(status), field("code"), field("resetAt"), field("skill"), field("tier"))
        }
        // A submitted test is now used: the "tests left" labels must not keep the old numbers for up to a minute.
        if (method == "POST" && (path.endsWith("/submit") || path == "/api/live/finish")) quotaAt = 0L
        return ApiResponse(status, text, headers)
    }

    suspend fun <T> request(method: String, path: String, body: JsonElement?, serializer: KSerializer<T>): T {
        val text = raw(method, path, body).body
        // Off the main thread: callers are often composables, and analyses / transcripts / L&R payloads are large.
        return try {
            withContext(Dispatchers.Default) { AppJson.decodeFromString(serializer, text) }
        } catch (e: SerializationException) {
            throw ApiError(0, "Unexpected response from the server ($path).")
        } catch (e: IllegalArgumentException) {
            throw ApiError(0, "Unexpected response from the server ($path).")
        }
    }

    suspend inline fun <reified T> send(method: String, path: String, body: JsonElement? = null): T =
        request(method, path, body, serializer<T>())

    suspend inline fun <reified T> get(path: String, query: Map<String, String?> = emptyMap()): T =
        request("GET", path + queryString(query), null, serializer<T>())

    /** A list endpoint: accepts a bare array or `{items|cards|models|data: [...]}`. */
    suspend inline fun <reified T> getList(path: String, query: Map<String, String?> = emptyMap()): List<T> {
        val text = raw("GET", path + queryString(query)).body
        return withContext(Dispatchers.Default) { decodeListOrThrow(serializer<T>(), text, path) }
    }

    @PublishedApi internal fun <T> decodeListOrThrow(s: KSerializer<T>, text: String, path: String): List<T> = try {
        AppJson.decodeList(s, text)
    } catch (e: SerializationException) {
        throw ApiError(0, "Unexpected response from the server ($path).")
    } catch (e: IllegalArgumentException) {
        throw ApiError(0, "Unexpected response from the server ($path).")
    }

    /** `?a=1&b=2` with keys sorted (so demo fixtures can match them) and null values dropped. */
    fun queryString(query: Map<String, String?>): String {
        val b = okhttp3.HttpUrl.Builder().scheme("https").host("x")
        query.entries.sortedBy { it.key }.forEach { (k, v) -> if (v != null) b.addQueryParameter(k, v) }
        return b.build().encodedQuery?.let { "?$it" }.orEmpty()
    }

    /** Storage URLs come from server answers (and a saved recording): only https ones are fetched. */
    private fun storageUrl(url: String): HttpUrl =
        url.toHttpUrlOrNull()?.takeIf { it.isHttps } ?: throw ApiError(0, "Unexpected file address from the server.")

    /** PUT a file to a presigned URL (no bearer token: it is a signed storage URL). */
    suspend fun upload(url: String, file: File, contentType: String) {
        val req = Request.Builder().url(storageUrl(url)).put(file.asRequestBody(contentType.toMediaType())).build()
        val (status, _, _) = execute(req, "Audio upload failed. Check your connection and retry.")
        if (status !in 200..299) throw ApiError(status, "Audio upload failed. Check your connection and retry.")
    }

    suspend fun download(url: String): ByteArray {
        val target = storageUrl(url)
        inflight.incrementAndGet()
        try {
            return withContext(Dispatchers.IO) {
                try {
                    http.newCall(Request.Builder().url(target).build()).await().use {
                        // An expired or missing presigned URL answers with an XML error body: not the file.
                        if (!it.isSuccessful) throw ApiError(it.code, "Couldn't download the file.")
                        it.body?.bytes() ?: ByteArray(0)
                    }
                } catch (e: IOException) {
                    throw ApiError(0, "Couldn't download the audio.")
                }
            }
        } finally {
            inflight.decrementAndGet()
        }
    }

    // The counter moves on the caller's thread (not inside withContext) so a test that just ran its composition sees it at once.
    private suspend fun execute(req: Request, offline: String): Triple<Int, String, Headers> {
        inflight.incrementAndGet()
        try {
            return withContext(Dispatchers.IO) {
                try {
                    http.newCall(req).await().use { Triple(it.code, it.body?.string().orEmpty(), it.headers) }
                } catch (e: IOException) {
                    throw ApiError(0, offline)
                }
            }
        } finally {
            inflight.decrementAndGet()
        }
    }

    private fun httpReason(status: Int) = when (status) {
        400 -> "Bad request"
        401 -> "Please sign in"
        403 -> "Not allowed"
        404 -> "Not found"
        409 -> "Conflict"
        429 -> "Too many requests. Try again in a moment."
        in 500..599 -> "The server had a problem. Try again."
        else -> "Request failed ($status)"
    }

    // MARK: Auth

    suspend fun signIn(email: String, password: String) {
        val r = raw("POST", "/api/auth/sign-in/email", buildJsonObject { put("email", email); put("password", password) })
        adopt(r)
    }

    /** Returns false when the server requires email verification before signing in. */
    suspend fun signUp(name: String, email: String, password: String): Boolean {
        val r = raw("POST", "/api/auth/sign-up/email", buildJsonObject { put("name", name); put("email", email); put("password", password) })
        statusToken = r.headers["x-email-status-token"]
        if (r.headers["set-auth-token"].isNullOrEmpty()) return false
        adopt(r)
        return true
    }
    // A guest token, when there is one, is sent on sign-up, sign-in and verify-email (see [raw]): the server moves the guest's tests to the
    // new account and answers with the account's token, which replaces the guest's here. Until then the guest session keeps working.

    /** Emails a 6-digit code (Better Auth emailOTP): [type] is "email-verification" or "forget-password". The answer is the same whether or not the address has an account. */
    suspend fun sendOtp(email: String, type: String): String? {
        val r = raw("POST", "/api/auth/email-otp/send-verification-otp", buildJsonObject { put("email", email); put("type", type) })
        return r.headers["x-email-status-token"].also { statusToken = it }
    }

    /** Proof of the latest code-sending request (sign-up, send code): the delivery status is only answered to whoever holds it. */
    var statusToken: String? = null

    /** Whether the code email was sent, behind a [statusToken]. */
    suspend fun emailStatus(token: String): EmailStatus =
        AppJson.decodeFromString(EmailStatus.serializer(), raw("GET", "/api/auth-email/status", extraHeaders = mapOf("x-email-status-token" to token)).body) // a header: query strings land in access logs

    /** Verifies the address with the emailed code and signs the user in. */
    suspend fun verifyEmail(email: String, otp: String) {
        adopt(raw("POST", "/api/auth/email-otp/verify-email", buildJsonObject { put("email", email); put("otp", otp) }))
    }

    suspend fun resetPassword(email: String, otp: String, password: String) {
        raw("POST", "/api/auth/email-otp/reset-password", buildJsonObject { put("email", email); put("otp", otp); put("password", password) })
    }

    private suspend fun adopt(r: ApiResponse) {
        val t = r.headers["set-auth-token"]
        if (t.isNullOrEmpty()) throw ApiError(0, "Sign-in didn't complete. Please try again.")
        val fromGuest = _me.value?.user?.takeIf { guest && it.isAnonymous }?.id
        setSession(t, false)
        _me.value = null // the previous session's profile must not stand in for the new account's (local drafts and recordings key on it)
        store.save(t, false)
        // Signed in from here on: a failed /api/me must not read as a wrong password or used code. `me` loads again where it's needed.
        loadMeQuietly()
        if (fromGuest != null) _me.value?.user?.id?.let { onGuestLinked?.invoke(fromGuest, it) }
    }

    /**
     * Called with (guest id, account id) when a guest signs in or up: the server links the guest's data to that account, so the
     * device's unsent guest recordings are re-owned by it (AppGraph -> PendingStore.reassign) and follow no later account.
     * ponytail: if /api/me failed at sign-in the account id is unknown and the guest's recordings stay the guest's.
     */
    @Volatile var onGuestLinked: ((guestId: String, accountId: String) -> Unit)? = null

    /** Start an anonymous guest session (the first test start, never a page view). The bearer token is the `set-auth-token` header, not the JSON `token`. */
    suspend fun signInAnonymously() {
        val r = raw("POST", "/api/auth/sign-in/anonymous", buildJsonObject {})
        val t = r.headers["set-auth-token"]
        if (t.isNullOrEmpty()) throw ApiError(0, "Couldn't start a guest session. Please try again.")
        setSession(t, true)
        store.save(t, true)
        loadMeQuietly()
    }

    private suspend fun loadMeQuietly() {
        try { loadMe() } catch (e: ApiError) { /* offline or a server hiccup: the session itself is fine */ }
    }

    /** A session for the test about to start: the guest session is created here when there is none. */
    suspend fun ensureSession() = sessionLock.withLock { if (_token.value == null) signInAnonymously() }

    suspend fun loadMe(): Me {
        meLoads.incrementAndGet()
        val sent = _token.value
        val m = try { get<Me>("/api/me") } finally { meLoads.decrementAndGet() }
        // Signed out or switched account while this was in flight: the answer is the old session's, so it must not overwrite the new state.
        if (_token.value != sent) return m
        _me.value = m
        m.quota()?.let { _quota.value = it; quotaAt = System.currentTimeMillis() }
        if (_token.value != null && m.user.isAnonymous != guest) {
            val t = _token.value
            setSession(t, m.user.isAnonymous)
            if (t != null) store.save(t, m.user.isAnonymous)
        }
        return m
    }

    /**
     * GET /api/quota: works without a session (answers for a guest by IP). A stale guest token is dropped and asked again as a guest.
     * An expired account session throws the 401 instead, so a test start doesn't quietly continue as a new guest (Nav.check).
     */
    suspend fun refreshQuota(): Quota {
        val wasAccount = _account.value
        val q = try {
            get<Quota>("/api/quota")
        } catch (e: ApiError) {
            if (e.status == 401 && !wasAccount) get<Quota>("/api/quota") else throw e
        }
        _quota.value = q
        quotaAt = System.currentTimeMillis()
        return q
    }

    /**
     * Foreground refresh: skipped while /api/me is already loading (cold start: restoreSession), and when it or /api/quota answered in the
     * last [maxAgeMs] (a permission dialog, a Custom Tab, a screen's own recheck). A submitted test clears the age, so its count still updates.
     */
    suspend fun loadMeIfStale(maxAgeMs: Long = 30_000) {
        if (meLoads.get() > 0) return
        if (_quota.value == null || System.currentTimeMillis() - quotaAt >= maxAgeMs) loadMe()
    }

    /** For screens that show the labels: refreshes at most once a minute and stays quiet on failure. */
    suspend fun refreshQuotaIfStale() {
        if (quotaBusy || (_quota.value != null && System.currentTimeMillis() - quotaAt < 60_000)) return
        quotaBusy = true
        try { runCatching { refreshQuota() } } finally { quotaBusy = false }
    }
    @Volatile private var quotaBusy = false

    /** The live examiner is set up on the server (docs/community.md). */
    val liveAvailable: Boolean get() = _quota.value?.live ?: _me.value?.live ?: false

    /** Signs out here at once, then revokes the old token on the server in the background, so a slow network never stalls the tap. */
    fun signOut() {
        val t = _token.value
        signOutLocal()
        if (t != null) scope.launch {
            runCatching {
                execute(
                    Request.Builder().url(baseUrl + "/api/auth/sign-out").post("{}".toRequestBody(JSON_MEDIA)).header("Authorization", "Bearer $t").build(),
                    "offline",
                )
            }
        }
    }

    /** Deleting needs the account password (Better Auth delete-user), like the web dialog. */
    suspend fun deleteAccount(password: String? = null) {
        val sent = _token.value
        try {
            raw("POST", "/api/auth/delete-user", password?.let { buildJsonObject { put("password", it) } })
        } catch (e: ApiError) {
            // 401 is the session, not the password (a wrong one is 400), e.g. revoked by a password reset. raw() leaves /api/auth/ 401s alone.
            if (e.status != 401 || sent == null || _token.value != sent) throw e
            val wasAccount = _account.value
            signOutLocal(accountExpired = wasAccount)
            _sessionEnded.trySend(wasAccount)
            throw ApiError(401, "Your session ended. Sign in again, then delete.")
        }
        signOutLocal()
    }

    fun signOutLocal(accountExpired: Boolean = false) {
        val lastUser = _me.value?.user?.id
        expiredUser = if (accountExpired) lastUser else null
        setSession(null, false)
        _me.value = null
        _quota.value = null
        scope.launch { store.clear() }
        onSignedOut?.invoke(accountExpired, lastUser)
    }

    /**
     * After every sign-out: [accountExpired] is true when the server ended an account's session (401), false for one the user chose or a
     * guest's; [lastUser] is the account that was signed in, when known. AppGraph gives unowned recordings to that account on expiry
     * (so only it can upload them after signing back in) and drops them otherwise.
     */
    @Volatile var onSignedOut: ((accountExpired: Boolean, lastUser: String?) -> Unit)? = null

    /**
     * The account whose session the server ended (401), until the next sign-out. Sign-in after it continues in place only for that
     * same account (AuthScreen): anyone else starts from a clean stack, so the expired account's screens and cached data never show.
     */
    @Volatile var expiredUser: String? = null

    // MARK: Endpoints (one suspend function per route in apps/web/src/openapi.json; admin and replay are left out)

    private fun enc(id: String) = java.net.URLEncoder.encode(id, "UTF-8").replace("+", "%20")

    /** The `source` to send for random speaking tests: only Cambridge-allowlisted accounts choose; null = the server default (any visible prompt). [pref] is Prefs.speakingSource. */
    fun sourceParam(pref: String): String? = pref.takeIf { _me.value?.cambridgeAccess == true && (it == "cambridge" || it == "generated") }

    // Health, account, settings

    /** GET /api/health */
    suspend fun health(): Boolean = try { raw("GET", "/api/health"); true } catch (e: ApiError) { false }

    /** GET /api/settings */
    suspend fun settings(): AppSettings = get("/api/settings")

    /** PUT /api/settings: only the arguments you pass are sent. [clearExamDate] sends `examDate: null`. Updates [me]. */
    suspend fun saveSettings(
        targetBand: Double? = null, writingAutoSubmit: Boolean? = null, blockPaste: Boolean? = null, productEmails: Boolean? = null,
        examDate: String? = null, clearExamDate: Boolean = false, timeZone: String? = null,
    ): AppSettings {
        val saved: AppSettings = send("PUT", "/api/settings", buildJsonObject {
            targetBand?.let { put("targetBand", it) }
            writingAutoSubmit?.let { put("writingAutoSubmit", it) }
            blockPaste?.let { put("blockPaste", it) }
            productEmails?.let { put("productEmails", it) }
            if (clearExamDate) put("examDate", JsonNull) else examDate?.let { put("examDate", it) }
            timeZone?.let { put("timeZone", it) }
        })
        _me.value = _me.value?.copy(settings = saved)
        return saved
    }

    // Prompts

    /** GET /api/prompts (the question bank). [page] is 1-based. */
    suspend fun prompts(
        skill: String? = null, part: Int? = null, variant: String? = null, type: String? = null, source: String? = null,
        topic: String? = null, q: String? = null, page: Int? = null,
    ): PromptPage = get("/api/prompts", mapOf(
        "skill" to skill, "part" to part?.toString(), "variant" to variant, "type" to type, "source" to source,
        "topic" to topic, "q" to q, "page" to page?.toString(),
    ))

    /** GET /api/prompts/meta */
    suspend fun promptMeta(): PromptMeta = get("/api/prompts/meta")

    /** GET /api/prompts/random */
    suspend fun randomPrompt(skill: String, part: Int? = null, variant: String? = null, type: String? = null, source: String? = null): Prompt =
        get("/api/prompts/random", mapOf("skill" to skill, "part" to part?.toString(), "variant" to variant, "type" to type, "source" to source))

    /** GET /api/prompts/{id} */
    suspend fun prompt(id: String): Prompt = get("/api/prompts/${enc(id)}")

    /** GET /api/speaking/test: a full test (Part 1 topics, Part 2 cue card, Part 3). */
    suspend fun speakingTest(source: String? = null): SpeakingTest = get("/api/speaking/test", mapOf("source" to source))

    // Attempts (speaking and writing)

    /** POST /api/attempts. A speaking attempt answers with an [Created.uploadUrl] for the audio. */
    suspend fun createAttempt(
        promptId: String, skill: String, part: Int, mode: String = "practice", sessionId: String? = null, mockId: String? = null,
        parentAttemptId: String? = null, text: String? = null, audioContentType: String? = null,
    ): Created = send("POST", "/api/attempts", buildJsonObject {
        put("promptId", promptId); put("skill", skill); put("part", part); put("mode", mode)
        sessionId?.let { put("sessionId", it) }
        mockId?.let { put("mockId", it) }
        parentAttemptId?.let { put("parentAttemptId", it) }
        text?.let { put("text", it) }
        audioContentType?.let { put("audioContentType", it) }
    })

    /** GET /api/attempts. [page] is 1-based. */
    suspend fun attempts(skill: String? = null, page: Int? = null): AttemptPage =
        get("/api/attempts", mapOf("skill" to skill, "page" to page?.toString()))

    /** POST /api/attempts/{id}/submit with a ready body (see [submitBody] for speaking). Starts the analysis. */
    suspend fun submitAttempt(id: String, body: JsonObject = buildJsonObject {}) { raw("POST", "/api/attempts/${enc(id)}/submit", body) }

    /** Submit a written essay: [durationMs] is the time in the editor, [overtime] whether the clock ran out. */
    suspend fun submitWriting(id: String, text: String, plan: String?, durationMs: Int, overtime: Boolean) = submitAttempt(id, buildJsonObject {
        put("text", text); plan?.let { put("plan", it) }; put("durationMs", durationMs); put("overtime", overtime)
    })

    /** GET /api/attempts/{id}/status: poll while analysing. */
    suspend fun attemptStatus(id: String): AttemptStatus = get("/api/attempts/${enc(id)}/status")

    /** GET /api/attempts/{id} */
    suspend fun attempt(id: String): Attempt = get("/api/attempts/${enc(id)}")

    /** DELETE /api/attempts/{id}: removes the row, analysis and recording; the test allowance is not given back. */
    suspend fun deleteAttempt(id: String) { raw("DELETE", "/api/attempts/${enc(id)}") }

    /** Create a speaking attempt, upload the m4a and start analysis. Returns the attempt id. (Pending uploads use the same steps with resume.) */
    suspend fun submitSpeaking(
        prompt: Prompt, mode: String = "practice", sessionId: String, parentAttemptId: String?, file: File,
        durationMs: Int, energy: List<Int>, marks: List<Int>,
    ): String {
        val created = createAttempt(prompt.id, "speaking", prompt.part, mode, sessionId, parentAttemptId = parentAttemptId, audioContentType = "audio/mp4")
        val url = created.uploadUrl ?: throw ApiError(0, "The server didn't return an upload URL.")
        upload(url, file, "audio/mp4")
        submitAttempt(created.id, submitBody(durationMs, energy, marks))
        return created.id
    }

    // Live examiner (LiveKit)

    /** POST /api/live/start: creates the session and picks the test. [localHour] is 0-23 (the greeting). */
    suspend fun liveStart(source: String?, mockId: String?, localHour: Int): LiveStart = send("POST", "/api/live/start", buildJsonObject {
        source?.let { put("source", it) }; mockId?.let { put("mockId", it) }; put("localHour", localHour)
    })

    /** POST /api/live/livekit/start: the room URL and token to join. */
    suspend fun liveKitStart(sessionId: String): LiveKitJoin = send("POST", "/api/live/livekit/start", buildJsonObject { put("sessionId", sessionId) })
    // ponytail: POST /api/live/livekit/usage is the examiner worker's call (it reports provider cost with the worker's own key), so the app has no function for it.

    /** POST /api/live/upload-url: where to PUT one part's recording. */
    suspend fun liveUploadUrl(sessionId: String, contentType: String = "audio/mp4"): UploadTarget =
        send("POST", "/api/live/upload-url", buildJsonObject { put("sessionId", sessionId); put("audioContentType", contentType) })

    /** POST /api/live/part: sends a finished part while the test goes on. [part] is a `LivePart` body ({part, audioKey, durationMs, energy, ...}). */
    suspend fun livePart(sessionId: String, part: JsonObject): LivePartCreated =
        send("POST", "/api/live/part", buildJsonObject { put("sessionId", sessionId); put("part", part) })

    /** POST /api/live/finish: the parts not already sent with [livePart]. */
    suspend fun liveFinish(sessionId: String, parts: List<JsonObject>): FinishResult = send("POST", "/api/live/finish", buildJsonObject {
        put("sessionId", sessionId); put("parts", JsonArray(parts))
    })

    /** POST /api/live/latency: per-turn timings (each turn is a LiveLatency turn object: at, wait, stt, llm, tts, net, total, label?). */
    suspend fun liveLatency(sessionId: String, turns: List<JsonObject>) {
        raw("POST", "/api/live/latency", buildJsonObject { put("sessionId", sessionId); put("turns", JsonArray(turns)) })
    }

    // Progress, mistakes, cards

    /** GET /api/progress; [skill] filters the trend to speaking or writing. */
    suspend fun progress(skill: String? = null): ProgressData = get("/api/progress", mapOf("skill" to skill))

    /** GET /api/mistakes. [page] is 1-based. */
    suspend fun mistakes(category: String? = null, page: Int? = null): MistakeLog =
        get("/api/mistakes", mapOf("category" to category, "page" to page?.toString()))

    /** POST /api/mistakes/{id}/card */
    suspend fun mistakeToCard(id: String) { raw("POST", "/api/mistakes/${enc(id)}/card") }

    /** POST /api/cards. [source] is mistake | vocab | fix; [category] lets drills group it. */
    suspend fun addCard(front: String, back: String, source: String, category: String? = null) {
        raw("POST", "/api/cards", buildJsonObject { put("front", front); put("back", back); put("source", source); category?.let { put("category", it) } })
    }

    /** POST /api/cards/bulk (1 to 50 cards). */
    suspend fun addCards(cards: List<CardInput>) {
        raw("POST", "/api/cards/bulk", buildJsonObject { put("cards", AppJson.encodeToJsonElement(ListSerializer(CardInput.serializer()), cards)) })
    }

    /** GET /api/cards/due. [category] narrows to one pattern; [ahead] includes cards not yet due (soonest first). */
    suspend fun dueCards(category: String? = null, ahead: Boolean = false): DueResponse =
        get("/api/cards/due", mapOf("category" to category, "practice" to if (ahead) "1" else null))

    /** POST /api/cards/{id}/skip: the card never comes back. */
    suspend fun skipCard(id: String) { raw("POST", "/api/cards/${enc(id)}/skip") }

    /** GET /api/mistakes/patterns */
    suspend fun mistakePatterns(): MistakePatterns = get("/api/mistakes/patterns")

    /** GET /api/mistakes/recurring/{attemptId} */
    suspend fun recurring(attemptId: String): Recurring = get("/api/mistakes/recurring/${enc(attemptId)}")

    /** GET /api/lr/cards/{id}/clip (404 when the word can't be located in the recording). */
    suspend fun cardClip(id: String): CardClip = get("/api/lr/cards/${enc(id)}/clip")

    /** POST /api/cards/{id}/review: [grade] 0-5 (the card comes back rescheduled). */
    suspend fun reviewCard(id: String, grade: Int): DueCard = send("POST", "/api/cards/${enc(id)}/review", buildJsonObject { put("grade", grade) })

    // Listening and Reading

    /** GET /api/lr/tests */
    suspend fun lrTests(skill: String? = null, variant: String? = null, source: String? = null): List<LrTestItem> =
        getList("/api/lr/tests", mapOf("skill" to skill, "variant" to variant, "source" to source))

    /** GET /api/lr/tests/{id} */
    suspend fun lrTest(id: String): LrTestDetail = get("/api/lr/tests/${enc(id)}")

    /** POST /api/lr/tests/{id}/attempts. [parts] null = whole test; [fresh] discards an in-progress attempt of this test. */
    suspend fun lrStart(testId: String, mode: String = "practice", parts: List<Int>? = null, fresh: Boolean = false): LrAttempt =
        send("POST", "/api/lr/tests/${enc(testId)}/attempts", buildJsonObject {
            put("mode", mode)
            if (!parts.isNullOrEmpty()) putJsonArray("parts") { parts.forEach { add(JsonPrimitive(it)) } }
            if (fresh) put("fresh", true)
        })

    /** GET /api/lr/attempts */
    suspend fun lrAttempts(): List<LrAttemptItem> = getList("/api/lr/attempts")

    /** GET /api/lr/attempts/{id} */
    suspend fun lrAttempt(id: String): LrAttempt = get("/api/lr/attempts/${enc(id)}")

    private fun lrBody(responses: Map<String, String>, elapsedS: Int, stats: LrStats?) = buildJsonObject {
        put("responses", buildJsonObject { responses.forEach { (k, v) -> put(k, v) } })
        put("elapsedS", elapsedS.coerceIn(0, 86400))
        stats?.let { put("stats", AppJson.encodeToJsonElement(LrStats.serializer(), it)) }
    }

    /** PUT /api/lr/attempts/{id}: autosave. */
    suspend fun lrSave(id: String, responses: Map<String, String>, elapsedS: Int, stats: LrStats? = null): LrSaved =
        send("PUT", "/api/lr/attempts/${enc(id)}", lrBody(responses, elapsedS, stats))

    /** DELETE /api/lr/attempts/{id} */
    suspend fun lrDelete(id: String) { raw("DELETE", "/api/lr/attempts/${enc(id)}") }

    /** POST /api/lr/attempts/{id}/submit: marks the attempt and returns it with marks, analysis and transcripts. */
    suspend fun lrSubmit(id: String, responses: Map<String, String>, elapsedS: Int, stats: LrStats? = null): LrAttempt =
        send("POST", "/api/lr/attempts/${enc(id)}/submit", lrBody(responses, elapsedS, stats))

    /** GET /api/lr/progress */
    suspend fun lrProgress(): LrProgress = get("/api/lr/progress")

    /** GET /api/lr/spelling */
    suspend fun lrSpelling(): LrSpelling = get("/api/lr/spelling")

    // Full mock exam (docs/mock-exam.md)

    /** GET /api/mock/current: the open mock, or null. */
    suspend fun mockCurrent(): Mock? = get<CurrentMock>("/api/mock/current").mock
    suspend fun mockGet(id: String): Mock = get("/api/mock/${enc(id)}")
    suspend fun mockList(): List<Mock> = get<MockList>("/api/mock").items
    suspend fun mockOptions(variant: String): MockOptions = get("/api/mock/options", mapOf("variant" to variant))

    /** Creates the mock. A 409 `mock_open` (ApiError.code) means one is already open: retry with [replace] after the person confirmed. */
    suspend fun mockCreate(variant: String, source: String, ref: String?, replace: Boolean = false): Mock =
        send("POST", "/api/mock" + if (replace) "?replace=true" else "", buildJsonObject {
            put("variant", variant); put("source", source)
            ref?.let { put("ref", it) }
        })

    /** Listening or Reading: creates (or resumes) the exam-mode attempt; returns its id. */
    suspend fun mockStartSection(id: String, skill: String): String = send<MockSectionStarted>("POST", "/api/mock/${enc(id)}/sections/${enc(skill)}/start").attemptId
    suspend fun mockWritingStart(id: String): MockWritingStarted = send("POST", "/api/mock/${enc(id)}/writing/start")

    /** PATCH writing/clock: autosave of the Writing clock; the server only ever raises it. */
    suspend fun mockWritingClock(id: String, elapsedS: Int) {
        raw("PATCH", "/api/mock/${enc(id)}/writing/clock", buildJsonObject { put("elapsedS", elapsedS.coerceIn(0, 7200)) })
    }

    /** [mode] is recorded | live. */
    suspend fun mockChoose(id: String, mode: String): MockSpeakingChosen =
        send("POST", "/api/mock/${enc(id)}/speaking/choose", buildJsonObject { put("mode", mode) })

    /** Live examiner: link the finished live session to the mock. */
    suspend fun mockAttach(id: String, sessionId: String): Mock =
        send("POST", "/api/mock/${enc(id)}/speaking/attach", buildJsonObject { put("sessionId", sessionId) })

    suspend fun mockClose(id: String): Mock = send("POST", "/api/mock/${enc(id)}/close")
    suspend fun mockAbandon(id: String) { raw("DELETE", "/api/mock/${enc(id)}") }

    // Coach, plans, feedback

    /** GET /api/coach */
    suspend fun coach(): Coach = get("/api/coach")

    /** POST /api/coach/feedback: "this is wrong" on one item ([itemKey] = `focus:<id>` | `pattern:<id>` | `cheat:<id>`, max 100 chars). */
    suspend fun coachFeedback(reportId: String, itemKey: String, note: String? = null) {
        raw("POST", "/api/coach/feedback", buildJsonObject {
            put("reportId", reportId); put("itemKey", itemKey.take(100)); note?.let { put("note", it.take(1000)) }
        })
    }

    /** POST /api/pay/checkout: returns the hosted payment page URL; open it in a browser. [plan] is month | quarter, [phone] a Bangladeshi mobile number. */
    suspend fun checkout(plan: String, phone: String, acceptTerms: Boolean): String =
        send<CheckoutStarted>("POST", "/api/pay/checkout", buildJsonObject { put("plan", plan); put("phone", phone); put("acceptTerms", acceptTerms) }).url

    /** POST /api/feedback: in-app feedback. [page] is where the person was (a route name, max 300 chars). */
    suspend fun feedback(message: String, page: String) {
        raw("POST", "/api/feedback", buildJsonObject { put("message", message.take(2000)); put("page", page.take(300)) })
    }
}

/** The body of `POST /api/attempts/{id}/submit` (energy and marks are capped like the server expects). */
fun submitBody(durationMs: Int, energy: List<Int>, marks: List<Int>, segments: List<AnswerWindow> = emptyList()): JsonObject = buildJsonObject {
    put("durationMs", durationMs)
    putJsonArray("energy") { energy.take(20000).forEach { add(JsonPrimitive(it)) } }
    putJsonArray("marks") { marks.take(200).forEach { add(JsonPrimitive(it)) } }
    if (segments.isNotEmpty()) putJsonArray("segments") { segments.take(200).forEach { add(buildJsonObject { put("q", it.q); put("startMs", it.startMs); put("endMs", it.endMs) }) } }
}

private suspend fun Call.await(): Response = suspendCancellableCoroutine { c ->
    c.invokeOnCancellation { cancel() }
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) { if (c.isActive) c.resumeWithException(e) }
        override fun onResponse(call: Call, response: Response) { c.resume(response) { response.close() } }
    })
}
