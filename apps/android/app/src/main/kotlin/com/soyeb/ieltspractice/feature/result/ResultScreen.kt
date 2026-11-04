package com.soyeb.ieltspractice.feature.result

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.soyeb.ieltspractice.LocalDemo
import com.soyeb.ieltspractice.LocalGraph
import com.soyeb.ieltspractice.data.logic.notAssessed
import com.soyeb.ieltspractice.data.model.Attempt
import com.soyeb.ieltspractice.data.net.ApiError
import com.soyeb.ieltspractice.design.Skill
import com.soyeb.ieltspractice.design.components.EmptyState
import com.soyeb.ieltspractice.design.components.ErrorState
import com.soyeb.ieltspractice.design.components.LocalSnack
import com.soyeb.ieltspractice.design.components.PushedScreen
import com.soyeb.ieltspractice.design.components.SkeletonList
import com.soyeb.ieltspractice.design.components.awaitStarted
import com.soyeb.ieltspractice.design.components.nextPollMs
import com.soyeb.ieltspractice.design.components.readable
import com.soyeb.ieltspractice.feature.speaking.SpeakingResult
import com.soyeb.ieltspractice.feature.writing.WritingResult
import com.soyeb.ieltspractice.nav.Nav
import com.soyeb.ieltspractice.nav.SpeakingSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The route for a speaking or writing result. Loads every attempt, polls while any is being marked (every 2.5 s, stopping when it
 * is done, failed or the screen closes), shows the in-between states, then hands the finished attempts to [SpeakingResult] or
 * [WritingResult]. A guest gets a bar to create an account under whichever of those is showing.
 */
@Composable
fun AttemptResultScreen(ids: List<String>, nav: Nav) {
    val api = LocalGraph.current.api
    val demo = LocalDemo.current
    val snack = LocalSnack.current
    val scope = rememberCoroutineScope()
    val guest by api.isGuest.collectAsState()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val attempts = remember(ids) { mutableStateMapOf<String, Attempt>() }
    var error by remember(ids) { mutableStateOf<String?>(null) }
    var round by remember(ids) { mutableIntStateOf(0) }

    LaunchedEffect(ids, round) {
        error = null
        for (id in ids) if (attempts[id]?.finished != true) {
            try {
                attempts[id] = api.attempt(id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (attempts[id] == null) error = e.message ?: "Something went wrong. Check your connection and try again."
            }
        }
        // A screenshot test must go idle, so demo mode fetches once instead of polling. A hidden app polls nothing (the next poll runs on
        // return), and the interval backs off so a stuck analysis doesn't keep the radio busy.
        var wait = 2500L
        // "recording" (a speaking upload that never finished) never moves on by itself, so it stops the polling too.
        while (demo == null && error == null && ids.any { attempts[it]?.let { a -> !a.finished && a.status != "recording" } != false }) {
            delay(wait)
            lifecycle.awaitStarted()
            wait = nextPollMs(wait)
            for (id in ids) {
                val cur = attempts[id] ?: continue
                if (cur.finished || cur.status == "recording") continue
                try {
                    val st = api.attemptStatus(id)
                    if (st.status == "done" || st.status == "failed") attempts[id] = api.attempt(id)
                    else attempts[id] = cur.copy(status = st.status, stage = st.stage)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // a missed poll: try again on the next tick
                }
            }
        }
    }

    fun retry(failed: List<Attempt>) {
        scope.launch {
            // Each part on its own: one that went through is analysing even if another failed, so it polls and isn't resent.
            var err: Exception? = null
            var sent = false
            for (a in failed) {
                try {
                    api.submitAttempt(a.id)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (!(e is ApiError && e.status == 409)) { err = e; continue } // 409: already analysing from an earlier try
                }
                attempts[a.id] = a.copy(status = "analyzing", stage = null, error = null)
                sent = true
            }
            if (sent) round++
            // Retrying costs nothing again, unless the allowance was used elsewhere meanwhile: say so, the answer stays.
            err?.let { e -> if (!(e is ApiError && nav.reportBlocked(e, failed.first().skill))) snack.showSnackbar(e.message ?: "Couldn't try again.") }
        }
    }

    val list = ids.mapNotNull { attempts[it] }
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).let { if (guest) it.consumeWindowInsets(WindowInsets.navigationBars) else it }) {
            val failed = list.filter { it.status == "failed" }
            val unsent = list.firstOrNull { it.skill == "speaking" && it.status == "recording" }
            val busy = list.firstOrNull { !it.finished }
            val first = list.firstOrNull()
            when {
                ids.isEmpty() -> Shell(nav) { EmptyState(Icons.Rounded.Warning, "Nothing to show", "There's no answer to mark here.") }
                // Any part missing after a failed load: retry fetches only what's missing (loaded parts are kept).
                error != null && list.size < ids.size -> Shell(nav) { ErrorState(error.orEmpty(), onRetry = { round++ }) }
                list.size < ids.size || first == null -> Shell(nav) { SkeletonList() }
                unsent != null -> Shell(nav) {
                    FailedState("This recording never finished uploading. Record it again from the Speaking tab.", retryable = false, onRetry = {})
                }
                failed.isNotEmpty() -> Shell(nav) { FailedState(failed.first().error, failed.all { it.retryable }, { retry(failed) }) }
                busy != null -> Shell(nav) { AnalysingState(busy.stage, Skill.of(busy.skill) ?: Skill.Writing) }
                first.skill == "speaking" && list.size == 1 && first.analysis?.let { notAssessed(it) } == true -> Shell(nav) {
                    NoSpeechState({ nav.startTest("speaking") { nav.replace(SpeakingSession("part${first.part}", first.promptId)) } })
                }
                first.skill == "speaking" -> SpeakingResult(list, nav)
                else -> WritingResult(list, nav)
            }
        }
        if (guest) GuestResultBar(nav)
    }
}

/** A pushed screen around one of the in-between states (marking, failed, no speech, loading). */
@Composable
private fun Shell(nav: Nav, content: @Composable () -> Unit) {
    PushedScreen("Result", nav::back) { pad: PaddingValues ->
        Box(Modifier.padding(pad).fillMaxSize().readable().verticalScroll(rememberScrollState())) { content() }
    }
}
