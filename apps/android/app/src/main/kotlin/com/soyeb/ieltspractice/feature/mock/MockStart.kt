package com.soyeb.ieltspractice.feature.mock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import com.soyeb.ieltspractice.LocalGraph
import com.soyeb.ieltspractice.data.logic.MockFlow
import com.soyeb.ieltspractice.data.logic.MockPick
import com.soyeb.ieltspractice.data.model.Mock
import com.soyeb.ieltspractice.data.model.MockCambridge
import com.soyeb.ieltspractice.data.model.MockOptions
import com.soyeb.ieltspractice.data.net.ApiError
import com.soyeb.ieltspractice.design.NB
import com.soyeb.ieltspractice.design.Skill
import com.soyeb.ieltspractice.design.Tone
import com.soyeb.ieltspractice.design.color
import com.soyeb.ieltspractice.design.components.Banner
import com.soyeb.ieltspractice.design.components.BtnKind
import com.soyeb.ieltspractice.design.components.BtnSize
import com.soyeb.ieltspractice.design.components.CardTone
import com.soyeb.ieltspractice.design.components.ConfirmDialog
import com.soyeb.ieltspractice.design.components.IconTile
import com.soyeb.ieltspractice.design.components.LoadContent
import com.soyeb.ieltspractice.design.components.NbButton
import com.soyeb.ieltspractice.design.components.NbCard
import com.soyeb.ieltspractice.design.components.NbListRow
import com.soyeb.ieltspractice.design.components.NbSheet
import com.soyeb.ieltspractice.design.components.Overline
import com.soyeb.ieltspractice.design.components.PushedList
import com.soyeb.ieltspractice.design.components.PushedScreen
import com.soyeb.ieltspractice.design.components.RowDivider
import com.soyeb.ieltspractice.design.components.RowGroup
import com.soyeb.ieltspractice.design.components.SectionHeader
import com.soyeb.ieltspractice.design.components.Segmented
import com.soyeb.ieltspractice.design.components.Skeleton
import com.soyeb.ieltspractice.design.components.SkeletonList
import com.soyeb.ieltspractice.design.components.SkillDots
import com.soyeb.ieltspractice.design.components.SkillTile
import com.soyeb.ieltspractice.design.components.StepRow
import com.soyeb.ieltspractice.design.components.StepState
import com.soyeb.ieltspractice.design.components.StickyActionBar
import com.soyeb.ieltspractice.design.components.Tag
import com.soyeb.ieltspractice.design.components.rememberLoad
import com.soyeb.ieltspractice.feature.study.GuestGate
import com.soyeb.ieltspractice.nav.MockHub
import com.soyeb.ieltspractice.nav.Nav
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** What the four tests are, in order, for the timeline preview. */
private class Leg(val skill: String, val length: String, val note: String)

private val legs = listOf(
    Leg("listening", "30 min + 2", "The recording plays once, then 2 minutes to check"),
    Leg("reading", "60 min", "Three passages, 40 questions"),
    Leg("writing", "60 min", "Task 1 and Task 2"),
    Leg("speaking", "11–14 min", "A recorded test or the live examiner"),
)

/** Set up a full mock: Academic or General, our tests or a Cambridge one, the rules, then start (or resume the open one). */
@Composable
fun MockStartScreen(nav: Nav) {
    val g = LocalGraph.current
    val api = g.api
    val scope = rememberCoroutineScope()
    val account by api.hasAccount.collectAsState()
    val variant by g.prefs.mockVariant.collectAsState(initial = "academic")

    val current = rememberLoad(account) { if (account) api.mockCurrent() else null }
    val options = rememberLoad(account, variant) { if (account) api.mockOptions(variant) else MockOptions() }

    var source by remember { mutableStateOf<String?>(null) } // null = whatever is available
    var ref by remember { mutableStateOf<String?>(null) }
    var picker by remember { mutableStateOf(false) }
    var confirmNew by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(variant) { ref = null }

    val openMock = current.value?.takeIf { it.open }
    val opts = options.value
    val cams = opts?.cambridge.orEmpty()
    val canOwn = opts?.own ?: true
    val canCambridge = cams.isNotEmpty()
    val src = when {
        source == "cambridge" && canCambridge -> "cambridge"
        canOwn -> "generated"
        canCambridge -> "cambridge"
        else -> "generated"
    }
    val chosenRef = if (src == "cambridge") ref ?: MockPick.defaultRef(cams) else null
    val blocked = MockPick.blocked(opts?.quota)
    val left = MockPick.left(opts?.quota)
    val canStart = opts != null && (canOwn || canCambridge) && !blocked && (left == null || left > 0)

    fun create(replace: Boolean) {
        busy = true
        error = null
        scope.launch {
            try {
                val m = api.mockCreate(variant, src, chosenRef, replace)
                nav.replace(MockHub(m.id))
            } catch (e: CancellationException) {
                throw e
            } catch (e: ApiError) {
                when {
                    e.code == "mock_open" -> confirmNew = true
                    nav.reportBlocked(e, "writing") -> Unit
                    else -> error = e.message
                }
            } catch (e: Exception) {
                error = e.message?.takeIf { it.isNotBlank() } ?: "Something went wrong. Check your connection and try again."
            } finally {
                busy = false
            }
        }
    }

    PushedScreen(
        "Full mock", nav::back,
        bottomBar = {
            if (account) StickyActionBar {
                error?.let { Text(it, style = NB.type.bodySmall, color = NB.colors.badText) }
                if (openMock != null) {
                    NbButton("Resume mock", { nav.replace(MockHub(openMock.id)) }, fill = true, size = BtnSize.Large)
                    NbButton("Start a new one", { confirmNew = true }, kind = BtnKind.Text, fill = true, enabled = !busy)
                } else {
                    NbButton(
                        "Start full mock", { nav.startTest("writing") { create(false) } },
                        fill = true, size = BtnSize.Large, enabled = canStart, loading = busy || nav.checking, haptic = true,
                    )
                }
            }
        },
    ) { pad ->
        if (!account) {
            GuestGate(
                Icons.Rounded.WorkspacePremium, "Take a full mock",
                "A mock spans several tests and days, so it needs an account to keep your place and your overall band.",
                nav, "Create an account to take a full mock", Modifier.padding(pad),
            )
        } else LoadContent(current, Modifier.padding(pad), skeleton = { SkeletonList() }) { cur ->
            PushedList(pad) {
                if (cur != null && cur.open) item { ResumeCard(cur) { nav.replace(MockHub(cur.id)) } }
                item { Hero() }

                item { SectionHeader("The four tests") }
                item {
                    NbCard {
                        legs.forEachIndexed { i, leg ->
                            val sk = Skill.of(leg.skill)
                            StepRow(
                                i + 1, MockFlow.name(leg.skill), leg.note, StepState.Current, last = i == legs.lastIndex,
                                accent = sk?.color ?: NB.colors.brand,
                                trailing = { Tag(leg.length, Modifier.padding(top = 6.dp)) },
                            )
                        }
                    }
                }

                item { SectionHeader("Test type") }
                item {
                    Segmented(listOf("Academic", "General Training"), if (variant == "general") 1 else 0, { i ->
                        scope.launch { g.prefs.setMockVariant(if (i == 1) "general" else "academic") }
                    }, role = androidx.compose.ui.semantics.Role.RadioButton)
                }

                item { SectionHeader("Questions from") }
                item {
                    SourceBlock(
                        loaded = opts != null, canOwn = canOwn, cams = cams, source = src, ref = chosenRef,
                        onSource = { source = it }, onPick = { picker = true },
                    )
                }

                MockPick.leftText(opts?.quota)?.let { text ->
                    item {
                        Banner(
                            if (blocked) "$text. Your allowance resets soon, so come back then." else text,
                            if (blocked || (left ?: 1) <= 0) Icons.Rounded.Info else Icons.Rounded.Timer,
                            tone = if (blocked || (left ?: 1) <= 0) Tone.Warn else Tone.Neutral,
                        )
                    }
                }
                if (opts != null && !canOwn && !canCambridge) item {
                    Banner("No complete test is available for this type yet. Try the other one.", Icons.Rounded.Info, tone = Tone.Warn)
                }

                item { SectionHeader("Good to know") }
                item { Rules() }
            }
        }
    }

    if (picker) {
        NbSheet(
            onDismiss = { picker = false },
            title = "Choose a Cambridge test",
            subtitle = "Each one has Listening, Reading, Writing and Speaking.",
            actions = {
                NbButton("Surprise me", { ref = MockPick.surprise(cams); picker = false }, kind = BtnKind.Secondary, fill = true, size = BtnSize.Large)
            },
        ) {
            RowGroup {
                cams.forEachIndexed { i, c ->
                    if (i > 0) RowDivider()
                    NbListRow(
                        title = c.bookTest,
                        subtitle = if (c.started) "You have started a mock on this one" else null,
                        trailing = { if (c.ref == chosenRef) Icon(Icons.Rounded.Check, "Selected", Modifier.size(24.dp), tint = NB.colors.brandText) },
                        onClick = { ref = c.ref; picker = false },
                    )
                }
            }
        }
    }

    if (confirmNew) {
        ConfirmDialog(
            title = "Start a new mock?",
            body = if (openMock != null) "Your open mock is closed. Sections you already took stay in your history." else "You already have a mock open. It is closed and a new one starts. Sections you already took stay in your history.",
            confirm = "Start new", dismiss = "Keep it",
            onConfirm = { confirmNew = false; nav.startTest("writing") { create(true) } },
            onDismiss = { confirmNew = false },
        )
    }
}

@Composable
private fun ResumeCard(m: Mock, onClick: () -> Unit) {
    val done = m.sections.filter { MockFlow.isFinished(it.state) }.mapNotNull { Skill.of(it.skill) }.toSet()
    NbCard(tone = CardTone.Hero, onClick = onClick) {
        Overline("Mock in progress", color = NB.colors.brandText)
        Text("Continue your mock test", style = NB.type.title1, color = NB.colors.ink, modifier = Modifier.padding(top = 4.dp))
        Text(MockFlow.continueLine(m), style = NB.type.bodySmall, color = NB.colors.ink, modifier = Modifier.padding(top = 4.dp))
        if (done.isNotEmpty()) SkillDots(Modifier.padding(top = 12.dp), done)
    }
}

@Composable
private fun Hero() {
    NbCard(tone = CardTone.Hero) {
        Overline("Full mock test", color = NB.colors.brandText)
        Text("Take the real thing", style = NB.type.headline, color = NB.colors.ink, modifier = Modifier.padding(top = 4.dp))
        HeroLines(
            "All four tests back to back, like test day.",
            "About 2 h 45 m in all, and Speaking can wait up to a week.",
            "An overall band at the end, from your four section bands.",
        )
    }
}

@Composable
private fun HeroLines(vararg lines: String) {
    Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        lines.forEach { line ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                Icon(Icons.Rounded.Check, null, Modifier.size(20.dp).padding(top = 2.dp), tint = NB.colors.brandText)
                Text(line, Modifier.weight(1f), style = NB.type.body, color = NB.colors.ink)
            }
        }
    }
}

@Composable
private fun SourceBlock(
    loaded: Boolean, canOwn: Boolean, cams: List<MockCambridge>, source: String, ref: String?,
    onSource: (String) -> Unit, onPick: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (!loaded) {
            Skeleton(Modifier.fillMaxWidth(), height = 44.dp, corner = 22.dp)
            return@Column
        }
        if (canOwn && cams.isNotEmpty()) {
            Segmented(listOf("NextBand originals", "Cambridge test"), if (source == "cambridge") 1 else 0, { onSource(if (it == 1) "cambridge" else "generated") }, role = androidx.compose.ui.semantics.Role.RadioButton)
        }
        if (source == "cambridge" && ref != null) {
            NbCard(padding = PaddingValues(0.dp)) {
                NbListRow(title = ref, subtitle = "Tap to choose another Cambridge test", onClick = onPick)
            }
        } else if (canOwn) {
            Text("Tests written for NextBand, picked for you. Ones you have not taken come first.", style = NB.type.bodySmall, color = NB.colors.muted)
        }
    }
}

@Composable
private fun Rules() {
    RowGroup {
        NbListRow(
            "No pausing in Listening", subtitle = "The recording plays once. The clock runs only while you are inside a section.",
            leading = { SkillTile(Skill.Listening, 40.dp) },
        )
        RowDivider()
        NbListRow(
            "Writing auto-submits", subtitle = "When the 60 minutes end, both tasks are sent for marking.",
            leading = { SkillTile(Skill.Writing, 40.dp) },
        )
        RowDivider()
        NbListRow(
            "You can take Speaking later", subtitle = "The mock stays open for 7 days. The overall band appears once Speaking is marked.",
            leading = { SkillTile(Skill.Speaking, 40.dp) },
        )
        RowDivider()
        NbListRow(
            "Leaving keeps your place", subtitle = "Come back and you resume with the time already used.",
            leading = { IconTile(Icons.Rounded.Timer, NB.colors.brandText, NB.colors.brandSoft, 40.dp) },
        )
    }
}
