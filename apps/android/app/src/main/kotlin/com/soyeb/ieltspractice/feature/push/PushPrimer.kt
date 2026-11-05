package com.soyeb.ieltspractice.feature.push

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.soyeb.ieltspractice.LocalDemo
import com.soyeb.ieltspractice.LocalGraph
import com.soyeb.ieltspractice.design.NB
import com.soyeb.ieltspractice.design.components.BtnKind
import com.soyeb.ieltspractice.design.components.BtnSize
import com.soyeb.ieltspractice.design.components.NbButton
import com.soyeb.ieltspractice.design.components.NbSheet
import com.soyeb.ieltspractice.feature.speaking.findActivity
import kotlinx.coroutines.launch

/** On a writing, speaking or mock result opened right after submitting it (the route's `fresh`), and after an exam date is set: opens the
 *  explainer when the rules allow (Push.askable). Not on Listening / Reading (marked instantly) or results opened from History. */
@Composable
fun PushAskAfterResult() {
    val push = LocalGraph.current.push
    LaunchedEffect(Unit) { push.maybeAsk() }
}

/**
 * Asks Android 13+ for POST_NOTIFICATIONS; a decline counts as a "Not now" ([Push.refused]). After the second one Android
 * stops showing its dialog, so [request] opens the app's notification settings instead. Below Android 13 it always opens them.
 * A decline with no rationale before or after the request means Android answered without a dialog (denied for good outside our
 * count) or the dialog was swiped away: the tap still has to do something visible, so the settings page opens and the explainer stops.
 */
@Composable
fun rememberPushPermission(onGranted: () -> Unit = {}): () -> Unit {
    val push = LocalGraph.current.push
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val rationaleBefore = remember { BooleanArray(1) }
    fun rationale() = context.findActivity()?.let { ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.POST_NOTIFICATIONS) } ?: false
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        when {
            ok -> { scope.launch { push.sync() }; onGranted() } // the device row goes back on (Push.sync drops it while notifications are off)
            !rationaleBefore[0] && !rationale() -> {
                scope.launch { push.refused(forGood = true) }
                context.startActivity(appNotificationSettings(context.packageName))
            }
            else -> scope.launch { push.refused() }
        }
    }
    return {
        scope.launch {
            if (Build.VERSION.SDK_INT >= 33 && push.refusals() < PRIMER_MAX_REFUSALS) {
                rationaleBefore[0] = rationale()
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else context.startActivity(appNotificationSettings(context.packageName))
        }
    }
}

fun appNotificationSettings(pkg: String): Intent =
    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, pkg).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

/** The explainer sheet (AppShell, next to the streak celebration, which goes first). Demo: only the `push-primer` screen. */
@Composable
fun PushPrimerHost() {
    val g = LocalGraph.current
    val demo = LocalDemo.current
    if (demo?.screen == "push-primer") LaunchedEffect(Unit) { g.push.primer = true }
    if (!g.push.primer || g.streak.celebrating != null) return
    val scope = rememberCoroutineScope()
    val request = rememberPushPermission()
    PushPrimer(
        onTurnOn = { g.push.primer = false; if (demo == null) request() },
        onNotNow = { g.push.primer = false; if (demo == null) scope.launch { g.push.refused() } },
    )
}

@Composable
private fun PushPrimer(onTurnOn: () -> Unit, onNotNow: () -> Unit) {
    val c = NB.colors
    NbSheet(
        onDismiss = onNotNow,
        actions = {
            NbButton("Turn on", onTurnOn, size = BtnSize.Large, fill = true)
            NbButton("Not now", onNotNow, kind = BtnKind.Text, fill = true)
        },
    ) {
        Box(Modifier.size(56.dp).background(c.brandSoft, CircleShape), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.NotificationsActive, null, Modifier.size(28.dp), tint = c.brandText)
        }
        Text("Want a ping when your band is ready and before your streak runs out?", style = NB.type.title1, color = c.ink, modifier = Modifier.semantics { heading() })
        Text(
            "Marking takes a minute or two. We'll tell you the moment it's done. Reminders come at most once a day, never in your quiet hours, and you can switch each kind off in Settings.",
            style = NB.type.body, color = c.muted,
        )
    }
}
