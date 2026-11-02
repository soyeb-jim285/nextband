package com.soyeb.ieltspractice.feature.you

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Style
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.soyeb.ieltspractice.LocalDemo
import com.soyeb.ieltspractice.LocalGraph
import com.soyeb.ieltspractice.data.logic.Clocks
import com.soyeb.ieltspractice.data.logic.QuotaCopy
import com.soyeb.ieltspractice.data.model.Quota
import com.soyeb.ieltspractice.data.net.ApiError
import com.soyeb.ieltspractice.design.NB
import com.soyeb.ieltspractice.design.components.Avatar
import com.soyeb.ieltspractice.design.components.BtnKind
import com.soyeb.ieltspractice.design.components.BtnSize
import com.soyeb.ieltspractice.design.components.CardTone
import com.soyeb.ieltspractice.design.components.ConfirmDialog
import com.soyeb.ieltspractice.design.components.IconTile
import com.soyeb.ieltspractice.design.components.LocalSnack
import com.soyeb.ieltspractice.design.components.Meter
import com.soyeb.ieltspractice.design.components.NbButton
import com.soyeb.ieltspractice.design.components.NbCard
import com.soyeb.ieltspractice.design.components.NbChip
import com.soyeb.ieltspractice.design.components.NbListRow
import com.soyeb.ieltspractice.design.components.NbSheet
import com.soyeb.ieltspractice.design.components.NbTextField
import com.soyeb.ieltspractice.design.components.PushedList
import com.soyeb.ieltspractice.design.components.PushedScreen
import com.soyeb.ieltspractice.design.components.RowDivider
import com.soyeb.ieltspractice.design.components.RowGroup
import com.soyeb.ieltspractice.design.components.SectionHeader
import com.soyeb.ieltspractice.design.components.Segmented
import com.soyeb.ieltspractice.design.components.Skeleton
import com.soyeb.ieltspractice.design.components.Tag
import com.soyeb.ieltspractice.design.fmtBand
import com.soyeb.ieltspractice.nav.Auth
import com.soyeb.ieltspractice.nav.Bank
import com.soyeb.ieltspractice.nav.CoachRoute
import com.soyeb.ieltspractice.nav.History
import com.soyeb.ieltspractice.nav.Mistakes
import com.soyeb.ieltspractice.nav.Nav
import com.soyeb.ieltspractice.nav.Plan
import com.soyeb.ieltspractice.nav.Review
import com.soyeb.ieltspractice.nav.SettingsRoute
import com.soyeb.ieltspractice.nav.Welcome
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

private enum class YouSheet { Band, Type, Feedback, Legal }

@Composable
fun YouScreen(nav: Nav) {
    val graph = LocalGraph.current
    val api = graph.api
    val prefs = graph.prefs
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snack = LocalSnack.current
    val me by api.me.collectAsState()
    val quota by api.quota.collectAsState()
    val hasAccount by api.hasAccount.collectAsState()
    val testType by prefs.testType.collectAsState(initial = "academic")
    val demoTab = LocalDemo.current?.tab
    var sheet by remember {
        mutableStateOf(
            when (demoTab) { "Band" -> YouSheet.Band; "Type" -> YouSheet.Type; "Feedback" -> YouSheet.Feedback; "Legal" -> YouSheet.Legal; else -> null },
        )
    }
    var datePicker by remember { mutableStateOf(false) }
    var confirmSignOut by remember { mutableStateOf(false) }
    LaunchedEffect(hasAccount) { if (hasAccount) runCatching { api.refreshQuotaIfStale() } }

    val q = quota ?: me?.quota()
    val tier = q?.tier ?: me?.tier
    val until = q?.subscribedUntil ?: me?.subscribedUntil
    val settings = me?.settings

    fun toast(msg: String) { scope.launch { snack.showSnackbar(msg) } }
    /** Run a settings write; on failure say so and report false. */
    suspend fun save(block: suspend () -> Unit): Boolean = try {
        block(); true
    } catch (e: CancellationException) {
        throw e
    } catch (e: ApiError) {
        toast(e.message); false
    } catch (e: Exception) {
        toast("Couldn't save. Try again."); false
    }

    PushedScreen("You", nav::back) { pad ->
        PushedList(pad) {
            if (hasAccount) item {
                Row(Modifier.semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically) {
                    Avatar(me?.user?.name, size = 64.dp)
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(me?.user?.name?.ifBlank { null } ?: "Your account", style = NB.type.title1, color = NB.colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(me?.user?.email.orEmpty(), style = NB.type.bodySmall, color = NB.colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Tag(tierLabel(tier, until), fg = NB.colors.brandText, bg = NB.colors.brandSoft)
                    }
                }
            } else item { GuestHero(nav) }

            if (hasAccount) item {
                NbCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Plan & limits", style = NB.type.title2, color = NB.colors.ink, modifier = Modifier.weight(1f))
                        if (q?.unlimited != true) NbButton(if (paidUntil(tier, until) != null) "Extend" else "Upgrade", { nav.go(Plan) }, kind = BtnKind.Secondary, size = BtnSize.Small)
                    }
                    Spacer(Modifier.height(12.dp))
                    if (q == null) Skeleton(Modifier.fillMaxWidth(), height = 88.dp, corner = 12.dp) else LimitRows(q)
                }
            }

            if (hasAccount) item {
                Group("Your goal") {
                    RowGroup {
                        NbListRow(
                            "Target band", leading = { IconTile(Icons.Rounded.Flag, NB.colors.brandText, NB.colors.brandSoft, 40.dp) },
                            trailing = { Text(fmtBand(settings?.targetBand), style = NB.type.num, color = NB.colors.ink) }, chevron = true,
                            onClick = { sheet = YouSheet.Band },
                        )
                        RowDivider()
                        NbListRow(
                            "Exam date", leading = { IconTile(Icons.Rounded.Event, NB.colors.brandText, NB.colors.brandSoft, 40.dp) },
                            trailing = { Text(examDateText(settings?.examDate) ?: "Not set", style = NB.type.bodySmall, color = NB.colors.muted) }, chevron = true,
                            onClick = { datePicker = true },
                        )
                        RowDivider()
                        NbListRow(
                            "Test type", leading = { IconTile(Icons.Rounded.School, NB.colors.brandText, NB.colors.brandSoft, 40.dp) },
                            trailing = { Text(if (testType == "general") "General" else "Academic", style = NB.type.bodySmall, color = NB.colors.muted) }, chevron = true,
                            onClick = { sheet = YouSheet.Type },
                        )
                    }
                }
            }

            item {
                Group("Study") {
                    RowGroup {
                        StudyRow("History", Icons.Rounded.History) { nav.requireAccount("Create an account to keep your history.") { nav.go(History()) } }
                        RowDivider()
                        StudyRow("Mistakes", Icons.Rounded.ErrorOutline) { nav.requireAccount("Create an account to see your mistakes.") { nav.go(Mistakes()) } }
                        RowDivider()
                        StudyRow("Review deck", Icons.Rounded.Style) { nav.requireAccount("Create an account to build and review your deck.") { nav.go(Review) } }
                        RowDivider()
                        StudyRow("Coach", Icons.Rounded.AutoAwesome) { nav.requireAccount("Create an account to get your coach report.") { nav.go(CoachRoute) } }
                        RowDivider()
                        StudyRow("Prompt bank", Icons.Rounded.Search) { nav.go(Bank()) }
                    }
                }
            }

            item {
                Group("App") {
                    RowGroup {
                        StudyRow("Settings", Icons.Rounded.Settings) { nav.go(SettingsRoute) }
                        RowDivider()
                        StudyRow("Send feedback", Icons.Rounded.ChatBubbleOutline) { sheet = YouSheet.Feedback }
                        RowDivider()
                        StudyRow("Help & legal", Icons.Rounded.Info) { sheet = YouSheet.Legal }
                        if (hasAccount) {
                            RowDivider()
                            NbListRow(
                                "Sign out", titleColor = NB.colors.badText, chevron = false,
                                leading = { IconTile(Icons.AutoMirrored.Rounded.Logout, NB.colors.badText, NB.colors.badSoft, 40.dp) },
                                onClick = { confirmSignOut = true },
                            )
                        }
                    }
                }
            }
        }
    }

    when (sheet) {
        YouSheet.Band -> BandSheet(settings?.targetBand ?: 7.0, onDismiss = { sheet = null }) { band ->
            if (save { api.saveSettings(targetBand = band) }) sheet = null
        }
        YouSheet.Type -> NbSheet(onDismiss = { sheet = null }, title = "Test type", subtitle = "Sets the Writing Task 1 and Reading you see first.", actions = {
            NbButton("Done", { sheet = null }, fill = true, size = BtnSize.Large)
        }) {
            Segmented(listOf("Academic", "General"), if (testType == "general") 1 else 0, { i -> scope.launch { prefs.setTestType(if (i == 1) "general" else "academic") } })
            Spacer(Modifier.height(8.dp))
        }
        YouSheet.Feedback -> FeedbackSheet(onDismiss = { sheet = null }) { msg ->
            if (save { api.feedback(msg, "android:you") }) { sheet = null; toast("Thanks. We read every message.") }
        }
        YouSheet.Legal -> NbSheet(onDismiss = { sheet = null }, title = "Help & legal") {
            RowGroup {
                legalPages.forEachIndexed { i, (title, path) ->
                    if (i > 0) RowDivider()
                    NbListRow(title, onClick = { openCustomTab(context, SITE + path) }, trailing = { Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, Modifier.size(20.dp)) })
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        null -> Unit
    }

    if (datePicker) ExamDateDialog(
        current = settings?.examDate,
        onDismiss = { datePicker = false },
        onSave = { date ->
            scope.launch {
                val ok = save { if (date == null) api.saveSettings(clearExamDate = true) else api.saveSettings(examDate = date.toString()) }
                if (ok) datePicker = false
            }
        },
    )

    if (confirmSignOut) ConfirmDialog(
        "Sign out?", "You can sign back in any time. Results stay on your account.", "Sign out",
        onConfirm = {
            confirmSignOut = false
            scope.launch { api.signOut(); nav.reset(Welcome) }
        },
        onDismiss = { confirmSignOut = false },
    )
}

@Composable
private fun Group(title: String, content: @Composable () -> Unit) {
    Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader(title)
        content()
    }
}

@Composable
private fun StudyRow(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    NbListRow(title, leading = { IconTile(icon, NB.colors.brandText, NB.colors.brandSoft, 40.dp) }, onClick = onClick)
}

@Composable
private fun GuestHero(nav: Nav) {
    NbCard(tone = CardTone.Hero) {
        Text("Create your free account", style = NB.type.title1, color = NB.colors.ink)
        Spacer(Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Keep every result and watch your bands climb", "History, Mistakes, Review and your coach", "More tests every week").forEach { b ->
                Row(Modifier.semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.Top) {
                    Icon(Icons.Rounded.Check, null, Modifier.size(20.dp).padding(top = 2.dp), tint = NB.colors.brandText)
                    Spacer(Modifier.width(10.dp))
                    Text(b, style = NB.type.body, color = NB.colors.ink)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        NbButton("Create free account", { nav.go(Auth("signup")) }, fill = true, size = BtnSize.Large)
        NbButton("Sign in", { nav.go(Auth("signin")) }, kind = BtnKind.Text, fill = true)
    }
}

@Composable
private fun LimitRows(q: Quota) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        buildList {
            add("Writing tests" to "writing")
            add("Speaking tests" to "speaking")
            if (q.live) add("Live examiner" to "examiner")
        }.forEach { (label, key) ->
            val s = q.of(key)
            Column(Modifier.semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row {
                    Text(label, style = NB.type.bodySmall, color = NB.colors.ink, modifier = Modifier.weight(1f))
                    Text(QuotaCopy.leftShort(q, key), style = NB.type.bodySmall, color = if (s.blocked != null || (s.remaining ?: 1) <= 0) NB.colors.warnText else NB.colors.muted)
                }
                val limit = s.limit
                if (limit != null && !q.unlimited) Meter((s.remaining ?: (limit - s.used)).coerceAtLeast(0).toDouble(), max = limit.toDouble(), height = 6.dp)
            }
        }
        if (q.lr.listening.total > 0 || q.lr.reading.total > 0) Row(Modifier.semantics(mergeDescendants = true) {}) {
            Text("Listening and Reading", style = NB.type.bodySmall, color = NB.colors.ink, modifier = Modifier.weight(1f))
            Text(
                "${q.lr.listening.open + q.lr.reading.open} of ${q.lr.listening.total + q.lr.reading.total} tests open",
                style = NB.type.bodySmall, color = NB.colors.muted,
            )
        }
    }
}

@Composable
private fun BandSheet(current: Double, onDismiss: () -> Unit, onSave: suspend (Double) -> Unit) {
    var pick by remember { mutableStateOf(current) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    NbSheet(
        onDismiss, title = "Target band", subtitle = "Scores at or above it show green; up to one band below, amber; further below, red.",
        actions = { NbButton("Save", { scope.launch { busy = true; onSave(pick); busy = false } }, fill = true, size = BtnSize.Large, loading = busy) },
    ) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            targetBands.forEach { b -> NbChip(fmtBand(b), selected = b == pick, onClick = { pick = b }, modifier = Modifier.defaultMinSize(minWidth = 64.dp, minHeight = 48.dp)) }
        }
        Text("Most universities ask for 6.5 to 7.0 overall.", style = NB.type.caption, color = NB.colors.muted)
    }
}

@Composable
private fun FeedbackSheet(onDismiss: () -> Unit, onSend: suspend (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    NbSheet(
        onDismiss, title = "Send feedback", subtitle = "Tell us what is broken or what you would like. We read every message.",
        actions = { NbButton("Send", { scope.launch { busy = true; onSend(text.trim()); busy = false } }, fill = true, size = BtnSize.Large, enabled = text.isNotBlank(), loading = busy) },
    ) {
        NbTextField(text, { text = it.take(2000) }, "Your message", singleLine = false)
    }
}

/** Material 3 date picker for the exam date. Only today or later can be picked; "Clear" removes the date. */
@Composable
internal fun ExamDateDialog(current: String?, onDismiss: () -> Unit, onSave: (LocalDate?) -> Unit) {
    val today = Clocks.now().atZone(Clocks.zone()).toLocalDate()
    val todayMs = today.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    val initial = current?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli(),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis >= todayMs
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            NbButton("Save", {
                val ms = state.selectedDateMillis
                if (ms != null) onSave(Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()) else onDismiss()
            }, size = BtnSize.Small)
        },
        dismissButton = {
            Row {
                if (initial != null) NbButton("Clear", { onSave(null) }, kind = BtnKind.Text, size = BtnSize.Small)
                NbButton("Cancel", onDismiss, kind = BtnKind.Text, size = BtnSize.Small)
            }
        },
    ) { DatePicker(state = state) }
}
