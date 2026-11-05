package com.soyeb.ieltspractice.feature.push

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.soyeb.ieltspractice.BuildConfig
import com.soyeb.ieltspractice.R
import com.soyeb.ieltspractice.data.Prefs
import com.soyeb.ieltspractice.data.currentUserId
import com.soyeb.ieltspractice.data.logic.sessionAttemptIds
import com.soyeb.ieltspractice.data.net.Api
import com.soyeb.ieltspractice.feature.updates.parseSeen
import com.soyeb.ieltspractice.nav.AttemptResult
import com.soyeb.ieltspractice.nav.MockResult
import com.soyeb.ieltspractice.nav.Nav
import com.soyeb.ieltspractice.nav.NotificationsRoute
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.Locale

/** A tap on a push: its notification_log id and deep-link route (the PendingIntent extras). */
data class PushOpen(val id: String?, val route: String?)

/**
 * Push on this phone (docs/engagement/notifications-and-streaks.md §6): Firebase Cloud Messaging by Installation ID, the
 * device registration with the server, the five channels, the permission explainer and opening a tapped push.
 * [live] is false in demo / screenshot runs: nothing registers and Firebase is never touched.
 */
class Push(private val app: Context, private val api: Api, private val prefs: Prefs, private val scope: CoroutineScope, private val live: Boolean) {
    /** Firebase is configured in this build (google-services.json was there at build time). Without it push stays off. */
    val available: Boolean by lazy { live && runCatching { FirebaseApp.getApps(app).isNotEmpty() }.getOrDefault(false) }

    /** The permission explainer is open (PushPrimerHost draws it). */
    var primer by mutableStateOf(false)

    /** A push the user tapped, waiting for AppShell to open it. */
    val opened = MutableStateFlow<PushOpen?>(null)

    /** Every launch: channels, FCM registration, and the device sent to the server for each session (sign-in, guest start, launch). */
    fun start() {
        ensureChannels(app)
        if (!available) return
        scope.launch {
            api.pushDevice = prefs.getString(FID)
            api.token.collect { if (it != null) sync() }
        }
        // The signed-in user, kept for NbMessagingService (a cold start from FCM never loads /api/me). Cleared on sign-out ([forgetUser]).
        scope.launch { uidWrites(api.me.map { it?.user?.id }).collect { prefs.setString(UID, it) } }
        // Auto-init is off in the manifest, so the docs' pattern: register() on every start; onRegistered() hands us the FID.
        FirebaseMessaging.getInstance().register().addOnFailureListener { Log.w(TAG, "FCM registration failed", it) }
    }

    /** NbMessagingService.onRegistered: a new or confirmed Installation ID. */
    suspend fun onFid(fid: String) {
        prefs.setString(FID, fid)
        api.pushDevice = fid
        sync()
    }

    /** Every sign-out (AppGraph): pushes still addressed to that account are dropped from now on. */
    suspend fun forgetUser() = prefs.setString(UID, null)

    /** The app came on screen and the server hasn't been told yet (the first [sync] with a session tells it). */
    @Volatile private var foregroundPending = false

    /**
     * PUT /api/push/token for the current session while Android lets us notify, else DELETE it: FCM deprioritises apps whose
     * high-priority messages show nothing. Quiet on failure (the next launch, sign-in or return to the app sends it again).
     * `foreground` is sent only after [foreground]: a start in the background (FCM waking the app for a push) must not reset the back-off.
     */
    suspend fun sync() {
        val fid = prefs.getString(FID) ?: return
        val session = api.token.value ?: return
        val on = enabled()
        val fg = foregroundPending
        try {
            if (on) api.pushRegister(fid, BuildConfig.VERSION_NAME, Locale.getDefault().toLanguageTag(), fg, session)
            else api.pushUnregister(fid, session)
            if (fg) foregroundPending = false
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't register this device for push", e)
        }
    }

    /** MainActivity.onStart: the app is on screen. Sends it with the next [sync] (now, or once the session and FID are loaded). */
    fun foreground() {
        if (!available) return
        foregroundPending = true
        scope.launch { sync() }
    }

    fun enabled(): Boolean = NotificationManagerCompat.from(app).areNotificationsEnabled()

    /** The explainer may show now ([shouldAskForPush]). */
    suspend fun askable(now: Long = System.currentTimeMillis()): Boolean =
        available && shouldAskForPush(Build.VERSION.SDK_INT, enabled(), refusals(), prefs.getString(REFUSED_AT)?.toLongOrNull(), now)

    /** After a result opens or an exam date is set: open the explainer when it may show. Main thread. */
    suspend fun maybeAsk() { if (askable()) primer = true }

    suspend fun refusals(): Int = prefs.getString(REFUSALS)?.toIntOrNull() ?: 0

    /** "Not now", or the system dialog declined: wait 7 days; after the second, only the Settings row asks. [forGood]: Android won't
     *  show its dialog again, so the explainer stops now and every "Turn on" goes to the app's notification settings. */
    suspend fun refused(forGood: Boolean = false, now: Long = System.currentTimeMillis()) {
        prefs.setString(REFUSALS, (if (forGood) PRIMER_MAX_REFUSALS else refusals() + 1).toString())
        prefs.setString(REFUSED_AT, now.toString())
    }

    /** Opens the push's screen over the current stack, reports the open, and marks its Updates rows read. Signed-out: just the app. */
    suspend fun open(o: PushOpen, nav: Nav) {
        if (!api.isSignedIn) return
        o.id?.let { id -> scope.launch { runCatching { api.pushOpened(id) } } }
        val target = parsePushRoute(o.route)
        val keys = seenKeysFor(target)
        if (keys.isNotEmpty()) currentUserId(api)?.let { uid ->
            val k = "updates:seen:$uid"
            prefs.setString(k, (keys + parseSeen(prefs.getString(k))).distinct().take(50).joinToString(","))
        }
        val route: Any = when (target) {
            is PushTarget.Attempts -> AttemptResult(target.ids)
            // The history list is the only place to find a session's other parts; if it fails, the first part still opens.
            is PushTarget.Session -> AttemptResult(
                sessionAttemptIds(runCatching { api.attempts("speaking", 1).items }.getOrDefault(emptyList()), target.sessionId, target.firstId),
            )
            is PushTarget.Mock -> MockResult(target.mockId)
            PushTarget.Settings -> NotificationsRoute
            null -> return
        }
        nav.go(route)
    }

    companion object {
        private const val TAG = "Push"
        const val FID = "push:fid"
        const val REFUSALS = "push:refusals"
        const val REFUSED_AT = "push:refusedAt"
        const val UID = "push:uid"
        const val EXTRA_ID = "push_id"
        const val EXTRA_ROUTE = "push_route"

        fun ensureChannels(c: Context) {
            NotificationManagerCompat.from(c).createNotificationChannelsCompat(
                PUSH_CHANNELS.map { NotificationChannelCompat.Builder(it.id, it.importance).setName(it.name).setDescription(it.description).build() },
            )
        }

        /** Draws a data-only message (`id uid type channel title body route`) on its channel; a tap goes through PushOpenActivity. */
        @SuppressLint("MissingPermission") // areNotificationsEnabled() is false without POST_NOTIFICATIONS on Android 13+
        fun show(c: Context, data: Map<String, String>) {
            val nm = NotificationManagerCompat.from(c)
            val title = data["title"] ?: return
            if (!nm.areNotificationsEnabled()) return
            ensureChannels(c)
            val id = data["id"]
            val n = (id ?: title).hashCode()
            val intent = Intent(c, PushOpenActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(EXTRA_ID, id).putExtra(EXTRA_ROUTE, data["route"])
            val tap = PendingIntent.getActivity(c, n, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val body = data["body"].orEmpty()
            val notification = NotificationCompat.Builder(c, channelFor(data["channel"]))
                .setSmallIcon(R.drawable.ic_notification)
                .setColor(ContextCompat.getColor(c, R.color.logo_symbol))
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setContentIntent(tap)
                .setAutoCancel(true)
                .build()
            try { nm.notify(n, notification) } catch (e: SecurityException) { Log.w(TAG, "Notification blocked", e) }
        }
    }
}

/** The signed-in user ids to store as push:uid. The sign-out null stays in until after the distinct check, so A, sign-out, A
 *  writes A again ([Push.forgetUser] cleared it in between). */
internal fun uidWrites(ids: Flow<String?>): Flow<String> = ids.distinctUntilChanged().filterNotNull()
