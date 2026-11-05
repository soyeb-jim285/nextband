package com.soyeb.ieltspractice.feature.push

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.soyeb.ieltspractice.App
import com.soyeb.ieltspractice.data.KeystoreTokenStore
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/** FCM entry point. The server sends data-only messages, so every push lands here, foreground or not, and we draw it. */
class NbMessagingService : FirebaseMessagingService() {
    override fun onRegistered(installationId: String) {
        val graph = (application as App).graph
        graph.scope.launch { graph.push.onFid(installationId) }
    }

    // Runs on FCM's background thread. Drawn only for the account signed in here (pushIsForThisPhone): a push for one that signed
    // out (its DELETE never reached the server) or for another account is dropped, and the server is told to forget this phone for it.
    override fun onMessageReceived(message: RemoteMessage) {
        val graph = (application as App).graph
        val signedIn = graph.api.isSignedIn || runBlocking { KeystoreTokenStore(applicationContext).load() } != null
        val uid = graph.api.me.value?.user?.id ?: runBlocking { graph.prefs.getString(Push.UID) }
        val forUid = message.data["uid"]
        if (pushIsForThisPhone(forUid, uid, signedIn)) Push.show(this, message.data)
        else if (shouldForgetPush(forUid, uid, signedIn)) graph.scope.launch {
            // Its sign-out never reached the server (offline, expired session): ask it to stop. Quiet on failure; the next push asks again.
            val fid = graph.prefs.getString(Push.FID) ?: return@launch
            runCatching { graph.api.pushForget(fid, forUid!!) }
        }
    }
}
