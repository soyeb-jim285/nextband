package com.soyeb.ieltspractice.feature.speaking

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.soyeb.ieltspractice.data.logic.sentenceCase
import com.soyeb.ieltspractice.data.model.Prompt
import com.soyeb.ieltspractice.design.LocalReducedMotion
import com.soyeb.ieltspractice.design.NB
import com.soyeb.ieltspractice.design.components.NbCard
import com.soyeb.ieltspractice.design.components.Overline

// Building blocks shared by the speaking hub, the session and the live examiner.

// MARK: Microphone permission

/** The microphone permission: ask once, learn the answer through a callback. */
class MicAccess(private val context: Context, private val request: ((Boolean) -> Unit) -> Unit) {
    fun granted() = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    fun ask(onResult: (Boolean) -> Unit) {
        if (granted()) onResult(true) else request(onResult)
    }
}

@Composable
fun rememberMicAccess(): MicAccess {
    val ctx = LocalContext.current
    val pending = remember { arrayOfNulls<(Boolean) -> Unit>(1) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        pending[0]?.invoke(ok)
        pending[0] = null
    }
    return remember(launcher) { MicAccess(ctx) { cb -> pending[0] = cb; launcher.launch(Manifest.permission.RECORD_AUDIO) } }
}

internal tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** The app's page in system settings (where a denied microphone can be switched back on). */
fun openAppSettings(context: Context) {
    val i = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(i) }
}

/** Keeps the screen awake while the composable is shown (a recording must not be cut by the display timing out). */
@Composable
fun KeepScreenOn(on: Boolean = true) {
    val ctx = LocalContext.current
    DisposableEffect(on) {
        val window = ctx.findActivity()?.window
        if (on) window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
}

// MARK: Rings and meters

/** A progress ring with [content] in the middle. [progress] is 0..1. */
@Composable
fun TimerRing(progress: Float, color: Color, ringSize: Dp, stroke: Dp, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val track = NB.colors.surface3
    Box(modifier.size(ringSize), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val sw = stroke.toPx()
            val arc = Size(this.size.width - sw, this.size.height - sw)
            val tl = Offset(sw / 2, sw / 2)
            drawArc(track, -90f, 360f, false, tl, arc, style = Stroke(sw))
            drawArc(color, -90f, 360f * progress.coerceIn(0f, 1f), false, tl, arc, style = Stroke(sw, cap = StrokeCap.Round))
        }
        content()
    }
}

/** A short live waveform: the last [count] input levels as rounded bars. Decorative. */
@Composable
fun LevelBars(levels: List<Double>, modifier: Modifier = Modifier, color: Color = NB.colors.brand, barHeight: Dp = 40.dp, count: Int = 24) {
    Canvas(modifier.fillMaxWidth().height(barHeight).clearAndSetSemantics {}) {
        val data = levels.takeLast(count)
        val gap = 3.dp.toPx()
        val w = (size.width - gap * (count - 1)) / count
        val min = 4.dp.toPx()
        data.forEachIndexed { i, l ->
            val h = min + (size.height - min) * l.toFloat().coerceIn(0f, 1f)
            val x = (count - data.size + i) * (w + gap)
            drawRoundRect(color.copy(alpha = 0.85f), Offset(x, (size.height - h) / 2), Size(w, h), CornerRadius(w / 2))
        }
    }
}

/** Segmented input meter for the mic check. */
@Composable
fun MicMeter(level: Float, heard: Boolean, quiet: Boolean, modifier: Modifier = Modifier) {
    val segments = 24
    val lit = (minOf(1f, level * 1.8f) * segments).toInt()
    val on = if (heard) NB.colors.good else if (quiet) NB.colors.warn else NB.colors.brand
    val off = NB.colors.surface3
    Row(
        modifier.fillMaxWidth().height(28.dp).semantics { contentDescription = "Microphone level ${(level * 100).toInt()} percent" },
        horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.Bottom,
    ) {
        repeat(segments) { i ->
            Box(Modifier.weight(1f).height((28 * (0.4f + 0.6f * i / segments)).dp).background(if (i < lit) on else off, NB.shape.chip))
        }
    }
}

/**
 * The big record button (72 dp) with a ring that swells with the input [level]. [recording] swaps the mic for a stop square.
 * The ring is decorative; the button carries [description].
 */
@Composable
fun RecordButton(
    recording: Boolean, level: Float, description: String, onClick: () -> Unit, modifier: Modifier = Modifier,
    enabled: Boolean = true, ringColor: Color = NB.colors.brand,
) {
    val reduced = LocalReducedMotion.current
    val l by animateFloatAsState(if (recording) level.coerceIn(0f, 1f) else 0f, if (reduced) tween(0) else tween(90), label = "recLevel")
    val base = NB.colors.brand
    Box(modifier.size(112.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().clearAndSetSemantics {}) {
            val c = Offset(size.width / 2, size.height / 2)
            if (recording) drawCircle(ringColor.copy(alpha = 0.18f), 36.dp.toPx() + 18.dp.toPx() * l, c)
            drawCircle(ringColor.copy(alpha = if (recording) 0.55f else 0.35f), 42.dp.toPx(), c, style = Stroke(2.dp.toPx()))
        }
        Surface(
            onClick = onClick, enabled = enabled, shape = CircleShape,
            color = if (enabled) base else NB.colors.surface3, contentColor = if (enabled) NB.colors.onBrand else NB.colors.faint,
            modifier = Modifier.size(72.dp).semantics { contentDescription = description },
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(if (recording) Icons.Rounded.Stop else Icons.Rounded.Mic, null, Modifier.size(32.dp))
            }
        }
    }
}

// MARK: Content cards

/** The examiner's avatar disc; [active] draws a soft ring while the examiner is talking. */
@Composable
fun ExaminerDisc(active: Boolean, modifier: Modifier = Modifier, discSize: Dp = 44.dp) {
    Box(
        modifier.size(discSize).background(NB.colors.speakingSoft, CircleShape)
            .let { if (active) it.background(NB.colors.speaking.copy(alpha = 0.18f), CircleShape) else it },
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.RecordVoiceOver, null, Modifier.size(discSize * 0.5f), tint = NB.colors.speaking)
    }
}

/** The Part 2 cue card, in the reading face because it is something the learner reads. */
@Composable
fun CueCard(p: Prompt, modifier: Modifier = Modifier) {
    val c = NB.colors
    NbCard(modifier, padding = PaddingValues(20.dp)) {
        Overline("Part 2 · Cue card")
        Spacer(Modifier.height(10.dp))
        Text(sentenceCase(p.title), style = NB.type.readingTitle, color = c.ink)
        val intro = cueIntro(p)
        if (intro.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(intro, style = NB.type.reading, color = c.ink)
        }
        val bullets = p.bullets.orEmpty()
        if (bullets.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            Text("You should say", style = NB.type.label, color = c.muted)
            bullets.forEach { b ->
                Row(Modifier.padding(top = 6.dp)) {
                    Text("•", style = NB.type.reading, color = c.muted, modifier = Modifier.width(18.dp))
                    Text(b, style = NB.type.reading, color = c.ink, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}
