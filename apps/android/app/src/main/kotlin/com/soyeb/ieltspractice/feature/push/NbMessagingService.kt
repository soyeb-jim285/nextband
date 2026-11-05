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
    // out (its DELETE never reached the server) or for another account is dropped.
    override fun onMessageReceived(message: RemoteMessage) {
        val graph = (application as App).graph
        val signedIn = graph.api.isSignedIn || runBlocking { KeystoreTokenStore(applicationContext).load() } != null
        val uid = graph.api.me.value?.user?.id ?: runBlocking { graph.prefs.getString(Push.UID) }
        if (pushIsForThisPhone(message.data["uid"], uid, signedIn)) Push.show(this, message.data)
    }
}
