package com.soyeb.ieltspractice.feature.push

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.soyeb.ieltspractice.App
import com.soyeb.ieltspractice.MainActivity

/**
 * A tapped push lands here (Push.show's PendingIntent). Not exported, so only our own notifications can hand the app a push id and
 * route: MainActivity is exported (launcher) and never reads them from its intent. Hands the tap to Push.opened, brings MainActivity
 * forward (CLEAR_TOP | SINGLE_TOP, the open task's activity is reused) and finishes without drawing anything.
 */
class PushOpenActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        (application as App).graph.push.opened.value = PushOpen(intent.getStringExtra(Push.EXTRA_ID), intent.getStringExtra(Push.EXTRA_ROUTE))
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
        finish()
    }
}
