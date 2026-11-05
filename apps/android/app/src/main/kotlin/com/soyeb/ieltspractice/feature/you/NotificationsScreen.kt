package com.soyeb.ieltspractice.feature.you

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.soyeb.ieltspractice.LocalDemo
import com.soyeb.ieltspractice.LocalGraph
import com.soyeb.ieltspractice.data.model.AppSettings
import com.soyeb.ieltspractice.data.net.ApiError
import com.soyeb.ieltspractice.design.NB
import com.soyeb.ieltspractice.design.components.BtnKind
import com.soyeb.ieltspractice.design.components.BtnSize
import com.soyeb.ieltspractice.design.components.CardTone
import com.soyeb.ieltspractice.design.components.LocalSnack
import com.soyeb.ieltspractice.design.components.NbButton
import com.soyeb.ieltspractice.design.components.NbCard
import com.soyeb.ieltspractice.design.components.NbListRow
import com.soyeb.ieltspractice.design.components.NbSheet
import com.soyeb.ieltspractice.design.components.PushedList
import com.soyeb.ieltspractice.design.components.PushedScreen
import com.soyeb.ieltspractice.design.components.RowDivider
import com.soyeb.ieltspractice.design.components.RowGroup
import com.soyeb.ieltspractice.design.components.Segmented
import com.soyeb.ieltspractice.feature.push.appNotificationSettings
import com.soyeb.ieltspractice.feature.push.clockString
import com.soyeb.ieltspractice.feature.push.displayClock
import com.soyeb.ieltspractice.feature.push.parseClock
import com.soyeb.ieltspractice.feature.push.rememberPushPermission
import com.soyeb.ieltspractice.nav.Nav
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

private val languages = listOf(null, "en", "banglish")

/** Which time the sheet edits: the settings field and the sheet's title. */
private enum class TimeField(val key: String, val title: String) {
    QuietStart("quietStart", "Quiet hours start"), QuietEnd("quietEnd", "Quiet hours end"), RemindAt("remindAt", "Best time to remind me"),
}

/**
 * Settings > Notifications (plan §6): the phone's permission, a switch per type, quiet hours, the reminder time (Auto by default),
 * the language and a test push. Stored on the server (PUT /api/settings; guests too), except Android's own per-channel switches.
 */
@Composable
fun NotificationsScreen(nav: Nav) {
    val graph = LocalGraph.current
    val api = graph.api
    val push = graph.push
    val demo = LocalDemo.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snack = LocalSnack.current
    val me by api.me.collectAsState()
    val signedIn by api.signedIn.collectAsState()
    val s = me?.settings
    val is24 = DateFormat.is24HourFormat(context)

    // Re-read on resume: the user may have switched notifications on or off in Android's settings.
    var enabled by remember { mutableStateOf(if (demo != null) demo.tab != "Off" else push.enabled()) }
    LifecycleResumeEffect(Unit) {
        if (demo == null) enabled = push.enabled()
        onPauseOrDispose { }
    }
    LaunchedEffect(signedIn) { if (signedIn && me == null) runCatching { api.loadMe() } }
    val request = rememberPushPermission { enabled = push.enabled() } // it syncs the device itself
    var editing by remember { mutableStateOf(TimeField.entries.firstOrNull { it.name == demo?.tab }) }
    var testing by remember { mutableStateOf(false) }

    /** Save one field; false (and a snackbar) when the server refused. */
    suspend fun save(key: String, value: Any?): Boolean = try {
        api.saveSettings(JsonObject(mapOf(key to when (value) { null -> JsonNull; is Boolean -> JsonPrimitive(value); else -> JsonPrimitive(value.toString()) })))
        true
    } catch (e: CancellationException) {
        throw e
    } catch (e: ApiError) {
        scope.launch { snack.showSnackbar(e.message) }; false
    } catch (e: Exception) {
        scope.launch { snack.showSnackbar("Couldn't save. Try again.") }; false
    }

    fun sendTest() = scope.launch {
        testing = true
        val msg = try {
            push.sync() // a device row must exist first
            if (api.pushTest()) "Sent. It should arrive in a few seconds." else "Couldn't reach this phone. Try again in a minute."
        } catch (e: CancellationException) {
            throw e
        } catch (e: ApiError) {
            e.message
        } catch (e: Exception) {
            "Couldn't reach this phone. Try again in a minute."
        } finally {
            testing = false
        }
        snack.showSnackbar(msg)
    }

    PushedScreen("Notifications", nav::back) { pad ->
        PushedList(pad) {
            if (!enabled) item {
                NbCard(Modifier.padding(top = 12.dp), tone = CardTone.Hero) {
                    Text("Notifications are off on this phone", style = NB.type.title2, color = NB.colors.ink)
                    Text("Turn them on to hear when your band is ready.", style = NB.type.bodySmall, color = NB.colors.muted, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))
                    NbButton("Turn on", { if (demo == null) request() })
                }
            }
            if (!signedIn) item {
                Text(
                    "Start a test first: your notification choices are saved with your results.",
                    style = NB.type.body, color = NB.colors.muted, modifier = Modifier.padding(top = 12.dp),
                )
            } else if (s != null) {
                item { Types(s) { k, v -> save(k, v) } }
                item {
                    Block("Quiet hours") {
                        RowGroup {
                            TimeRow("From", displayClock(s.quietStart, is24)) { editing = TimeField.QuietStart }
                            RowDivider()
                            TimeRow("Until", displayClock(s.quietEnd, is24)) { editing = TimeField.QuietEnd }
                        }
                        Caption("Reminders wait until quiet hours end. A result you're waiting for still arrives.")
                    }
                }
                item {
                    Block("Reminders") {
                        RowGroup {
                            TimeRow("Best time to remind me", s.remindAt?.let { displayClock(it, is24) } ?: "Auto") { editing = TimeField.RemindAt }
                        }
                        Caption("Auto picks the time you usually practise.")
                    }
                }
                item {
                    Block("Language") {
                        Segmented(
                            listOf("Phone", "English", "Banglish"), languages.indexOf(s.notifyLanguage).coerceAtLeast(0),
                            { i -> scope.launch { save("notifyLanguage", languages[i]) } }, role = Role.RadioButton,
                        )
                        Caption("Phone: Banglish when your phone is set to Bangla, otherwise English.")
                    }
                }
                if (enabled) item {
                    Column(Modifier.padding(top = 20.dp)) {
                        NbButton("Send me a test notification", { sendTest() }, kind = BtnKind.Secondary, fill = true, loading = testing, enabled = push.available || demo != null)
                    }
                }
            }
            item {
                Block("On this phone") {
                    RowGroup {
                        NbListRow(
                            "Sound, vibration and each type", subtitle = "Android's own settings for NextBand notifications",
                            onClick = { context.startActivity(appNotificationSettings(context.packageName)) },
                        )
                    }
                }
            }
        }
    }

    editing?.let { f ->
        val current = when (f) { TimeField.QuietStart -> s?.quietStart; TimeField.QuietEnd -> s?.quietEnd; TimeField.RemindAt -> s?.remindAt }
        TimeSheet(
            f.title, current ?: if (f == TimeField.RemindAt) "19:30" else null, is24, auto = f == TimeField.RemindAt,
            onDismiss = { editing = null },
            onSave = { v -> if (save(f.key, v)) editing = null },
        )
    }
}

@Composable
private fun Types(s: AppSettings, save: suspend (String, Any?) -> Boolean) {
    Block("What to send") {
        RowGroup {
            ToggleRow("Results", "Your band is ready, or a marking needs a retry", s.notifyResults) { save("notifyResults", it) }
            RowDivider()
            ToggleRow("Streak", "A nudge before your streak ends for the day", s.notifyStreak) { save("notifyStreak", it) }
            RowDivider()
            ToggleRow("Practice reminders", "Drills that are due, and skills you haven't practised lately", s.notifyPractice) { save("notifyPractice", it) }
            RowDivider()
            ToggleRow("Exam", "A countdown as your exam date gets close", s.notifyExam) { save("notifyExam", it) }
            RowDivider()
            ToggleRow("Weekly recap", "A short summary of your week", s.notifyWeekly) { save("notifyWeekly", it) }
        }
    }
}

@Composable
private fun TimeRow(title: String, value: String, onClick: () -> Unit) {
    NbListRow(title, onClick = onClick, trailing = { Text(value, style = NB.type.label.copy(fontFeatureSettings = "tnum"), color = NB.colors.brandText) })
}

@Composable
private fun Caption(text: String) {
    Text(text, style = NB.type.caption, color = NB.colors.muted, modifier = Modifier.padding(horizontal = 4.dp))
}

/** A clock-face picker in a sheet. [auto]: the reminder time also offers "Use Auto" (null). */
@Composable
private fun TimeSheet(title: String, current: String?, is24: Boolean, auto: Boolean, onDismiss: () -> Unit, onSave: suspend (String?) -> Unit) {
    val (h, m) = parseClock(current) ?: (22 to 30)
    val state = rememberTimePickerState(h, m, is24Hour = is24)
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    fun go(v: String?) = scope.launch { busy = true; onSave(v); busy = false }
    val c = NB.colors
    NbSheet(
        onDismiss = { if (!busy) onDismiss() }, title = title,
        actions = {
            NbButton("Save", { go(clockString(state.hour, state.minute)) }, size = BtnSize.Large, fill = true, loading = busy)
            if (auto) NbButton("Use Auto", { go(null) }, kind = BtnKind.Text, fill = true, enabled = !busy)
        },
    ) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TimePicker(
                state,
                colors = TimePickerDefaults.colors(
                    clockDialColor = c.surface2, clockDialSelectedContentColor = c.onBrand, clockDialUnselectedContentColor = c.ink,
                    selectorColor = c.brand, containerColor = c.surface,
                    periodSelectorBorderColor = c.faint, periodSelectorSelectedContainerColor = c.brandSoft, periodSelectorUnselectedContainerColor = c.surface,
                    periodSelectorSelectedContentColor = c.brandText, periodSelectorUnselectedContentColor = c.muted,
                    timeSelectorSelectedContainerColor = c.brandSoft, timeSelectorUnselectedContainerColor = c.surface2,
                    timeSelectorSelectedContentColor = c.brandText, timeSelectorUnselectedContentColor = c.ink,
                ),
            )
        }
    }
}
