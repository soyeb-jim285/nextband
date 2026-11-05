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

    // Runs on FCM's background thread. A push for an account that signed out here (its DELETE never reached the server)
    // is dropped: no session on this phone, nothing to show.
    override fun onMessageReceived(message: RemoteMessage) {
        val signedIn = (application as App).graph.api.isSignedIn || runBlocking { KeystoreTokenStore(applicationContext).load() } != null
        if (signedIn) Push.show(this, message.data)
    }
}
