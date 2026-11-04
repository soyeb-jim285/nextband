package com.soyeb.ieltspractice.feature.you

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.soyeb.ieltspractice.LocalDemo
import com.soyeb.ieltspractice.LocalGraph
import com.soyeb.ieltspractice.feature.writing.DraftStore
import com.soyeb.ieltspractice.data.net.ApiError
import com.soyeb.ieltspractice.design.NB
import com.soyeb.ieltspractice.design.components.BtnKind
import com.soyeb.ieltspractice.design.components.BtnSize
import com.soyeb.ieltspractice.design.components.ConfirmDialog
import com.soyeb.ieltspractice.design.components.LocalSnack
import com.soyeb.ieltspractice.design.components.NbSheet
import com.soyeb.ieltspractice.design.components.NbButton
import com.soyeb.ieltspractice.design.components.NbCard
import com.soyeb.ieltspractice.design.components.NbIconButton
import com.soyeb.ieltspractice.design.components.NbListRow
import com.soyeb.ieltspractice.design.components.NbTextField
import com.soyeb.ieltspractice.design.components.PushedList
import com.soyeb.ieltspractice.design.components.PushedScreen
import com.soyeb.ieltspractice.design.components.RowDivider
import com.soyeb.ieltspractice.design.components.RowGroup
import com.soyeb.ieltspractice.design.components.SectionHeader
import com.soyeb.ieltspractice.design.components.Segmented
import com.soyeb.ieltspractice.nav.Auth
import com.soyeb.ieltspractice.nav.Nav
import com.soyeb.ieltspractice.nav.Welcome
import com.soyeb.ieltspractice.data.currentUserId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val appearances = listOf("system", "light", "dark")
private val sources = listOf("any", "cambridge", "generated")

@Composable
fun SettingsScreen(nav: Nav) {
    val graph = LocalGraph.current
    val api = graph.api
    val prefs = graph.prefs
    val scope = rememberCoroutineScope()
    val snack = LocalSnack.current
    val me by api.me.collectAsState()
    val hasAccount by api.hasAccount.collectAsState()
    val appearance by prefs.appearance.collectAsState(initial = "system")
    val source by prefs.speakingSource.collectAsState(initial = "any")
    val scale by prefs.readingTextScale.collectAsState(initial = 1f)
    val s = me?.settings
    val demoTab = LocalDemo.current?.tab
    var deleting by remember { mutableStateOf(demoTab == "Delete") }
    var deletingGuest by remember { mutableStateOf(false) }

    /** Delete the account (a guest needs no password) and this device's copies of its essays, test notes and unsent recordings. Null on success, else the error. */
    suspend fun deleteEverything(password: String?): String? = try {
        val uid = currentUserId(api) // loads /api/me when it never answered, so the local cleanup below knows whose data to clear
        api.deleteAccount(password)
        if (uid != null) {
            runCatching { DraftStore(graph.prefs) { uid }.clearAll() }
            withContext(Dispatchers.IO) { graph.pending.reload() }
            graph.pending.items.value.filter { it.ownerId == uid || it.ownerId == null }.forEach { graph.pending.remove(it.id) }
        }
        // Listening/Reading notes, flags and answers of attempts never submitted (submitting clears only the notes and answers).
        graph.prefs.clearAttemptState()
        // Live exam parts still on this device (a test that was never scored): the account they belonged to is gone.
        withContext(Dispatchers.IO) {
            graph.app.cacheDir.listFiles { f -> f.name.startsWith("live-") && f.name.endsWith(".m4a") }?.forEach { it.delete() }
        }
        null
    } catch (e: CancellationException) {
        throw e
    } catch (e: ApiError) {
        e.message
    } catch (e: Exception) {
        "Couldn't delete. Try again."
    }

    /** Save one setting; false (and a snackbar) when the server refused. */
    suspend fun save(block: suspend () -> Unit): Boolean = try {
        block(); true
    } catch (e: CancellationException) {
        throw e
    } catch (e: ApiError) {
        scope.launch { snack.showSnackbar(e.message) }; false
    } catch (e: Exception) {
        scope.launch { snack.showSnackbar("Couldn't save. Try again.") }; false
    }

    PushedScreen("Settings", nav::back) { pad ->
        PushedList(pad) {
            item {
                Block("Appearance") {
                    Segmented(listOf("System", "Light", "Dark"), appearances.indexOf(appearance).coerceAtLeast(0), { i -> scope.launch { prefs.setAppearance(appearances[i]) } }, role = androidx.compose.ui.semantics.Role.RadioButton)
                }
            }
            if (hasAccount && s != null) {
                item {
                    Block("Writing") {
                        RowGroup {
                            ToggleRow(
                                "Submit when time runs out", "Off: the timer keeps counting as overtime and the result is flagged.", s.writingAutoSubmit,
                            ) { v -> save { api.saveSettings(writingAutoSubmit = v) } }
                            RowDivider()
                            ToggleRow("Block pasting", "Matches the real test, where you type every word.", s.blockPaste) { v -> save { api.saveSettings(blockPaste = v) } }
                        }
                    }
                }
            }
            if (me?.cambridgeAccess == true) item {
                Block("Speaking questions") {
                    Segmented(listOf("Mixed", "Cambridge", "Ours"), sources.indexOf(source).coerceAtLeast(0), { i -> scope.launch { prefs.setSpeakingSource(sources[i]) } }, role = androidx.compose.ui.semantics.Role.RadioButton)
                    Text("Where random Speaking tests draw their questions from.", style = NB.type.caption, color = NB.colors.muted, modifier = Modifier.padding(horizontal = 4.dp))
                }
            }
            if (hasAccount && s != null) item {
                Block("Emails") {
                    RowGroup {
                        ToggleRow("Product news and tips", "New tests, features and practice advice. A few emails a month at most. Account emails, like sign-in codes, always arrive.", s.productEmails) { v ->
                            save { api.saveSettings(productEmails = v) }
                        }
                    }
                }
            }
            item {
                Block("Reading text size") {
                    ReadingSize(scale) { v -> scope.launch { prefs.setReadingTextScale(v) } }
                }
            }
            if (hasAccount) item {
                Block("Account") {
                    RowGroup {
                        NbListRow("Delete account", subtitle = "Removes your recordings, essays, results and review cards. This can't be undone.", titleColor = NB.colors.badText, onClick = { deleting = true }, chevron = false)
                    }
                }
            }
            if (!hasAccount && me?.user?.isAnonymous == true) item {
                Block("Guest data") {
                    RowGroup {
                        NbListRow(
                            "Delete my guest data",
                            subtitle = "Removes this guest's recordings, essays and results now. Guest data is also deleted automatically after 30 days.",
                            titleColor = NB.colors.badText, onClick = { deletingGuest = true }, chevron = false,
                        )
                    }
                }
            }
        }
    }

    if (deleting) DeleteSheet(
        onDismiss = { deleting = false },
        // Forgot it: reset by email code, then come back here (Close on the sign-in step returns to Settings).
        onForgot = { deleting = false; nav.go(Auth("forgot", reason = "Sign in with your new password, then delete your account from Settings.", email = me?.user?.email?.ifEmpty { null })) },
        onDelete = { password ->
            deleteEverything(password).also { if (it == null) { deleting = false; nav.reset(Welcome) } }
        },
    )
    if (deletingGuest) ConfirmDialog(
        "Delete guest data?", "Your recordings, essays and results from this guest session are deleted now. This can't be undone.", "Delete",
        onConfirm = {
            deletingGuest = false
            scope.launch { val err = deleteEverything(null); if (err == null) nav.reset(Welcome) else snack.showSnackbar(err) }
        },
        onDismiss = { deletingGuest = false }, destructive = true,
    )
}

@Composable
private fun Block(title: String, content: @Composable () -> Unit) {
    Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader(title)
        content()
    }
}

/** A whole row that toggles; the switch only shows state. The value follows the tap at once and goes back if the save fails. */
@Composable
private fun ToggleRow(title: String, sub: String, value: Boolean, save: suspend (Boolean) -> Boolean) {
    var on by remember(value) { mutableStateOf(value) }
    val scope = rememberCoroutineScope()
    val c = NB.colors
    NbListRow(
        title, subtitle = sub,
        modifier = Modifier.toggleable(on, role = Role.Switch) { v ->
            on = v
            scope.launch { if (!save(v)) on = !v }
        },
        trailing = {
            Switch(
                on, null,
                colors = SwitchDefaults.colors(
                    checkedTrackColor = c.brand, checkedThumbColor = c.onBrand, checkedBorderColor = c.brand,
                    uncheckedTrackColor = c.surface2, uncheckedThumbColor = c.faint, uncheckedBorderColor = c.faint,
                ),
            )
        },
    )
}

/** Slider 0.85 to 1.4 with a live line in the reading face, so the size you pick is the size you get. */
@Composable
private fun ReadingSize(saved: Float, onSave: (Float) -> Unit) {
    var v by remember { mutableFloatStateOf(saved) }
    LaunchedEffect(saved) { v = saved }
    val c = NB.colors
    NbCard {
        Text(
            "The examiner asked how the town had changed since she was a child.",
            style = NB.type.reading.copy(fontSize = (18 * v).sp, lineHeight = (29 * v).sp), color = c.ink,
        )
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text("A", style = NB.type.bodySmall, color = c.muted)
            Slider(
                v, { v = it }, Modifier.weight(1f).padding(horizontal = 8.dp).semantics { contentDescription = "Reading text size" },
                valueRange = 0.85f..1.4f, onValueChangeFinished = { onSave(v) },
                colors = SliderDefaults.colors(thumbColor = c.brand, activeTrackColor = c.brand, inactiveTrackColor = c.surface3),
            )
            Text("A", style = NB.type.title1, color = c.muted)
        }
        Text("${Math.round(v * 100)}%", style = NB.type.caption.copy(fontFeatureSettings = "tnum"), color = c.muted)
    }
}

/** Confirms with the account password (Better Auth delete-user). [onDelete] returns an error message, or null when it worked. */
@Composable
private fun DeleteSheet(onDismiss: () -> Unit, onForgot: () -> Unit, onDelete: suspend (String) -> String?) {
    var password by remember { mutableStateOf("") }
    var show by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    fun go() {
        if (password.isEmpty() || busy) return
        scope.launch {
            busy = true
            error = onDelete(password)
            busy = false
        }
    }
    // A sheet, not an AlertDialog: thumb-reach on a phone, and the actions sit where the keyboard leaves room.
    NbSheet(
        onDismiss = { if (!busy) onDismiss() },
        title = "Delete your account?",
        subtitle = "Everything you've recorded and written will be permanently deleted. Confirm with your password.",
        actions = {
            NbButton("Delete forever", ::go, kind = BtnKind.Destructive, size = BtnSize.Large, fill = true, enabled = password.isNotEmpty(), loading = busy)
            NbButton("Keep account", onDismiss, kind = BtnKind.Text, fill = true, enabled = !busy)
        },
    ) {
        NbTextField(
            password, { password = it; error = null }, "Password", error = error,
            visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { go() }),
            fieldModifier = Modifier.semantics { contentType = ContentType.Password },
            trailing = {
                NbIconButton(if (show) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, if (show) "Hide password" else "Show password", { show = !show })
            },
        )
        NbButton("Forgot password?", onForgot, kind = BtnKind.Text, size = BtnSize.Small, enabled = !busy)
    }
}
