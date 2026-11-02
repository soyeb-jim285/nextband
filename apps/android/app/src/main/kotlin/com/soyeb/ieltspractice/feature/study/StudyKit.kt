package com.soyeb.ieltspractice.feature.study

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Person
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.soyeb.ieltspractice.design.components.EmptyState
import com.soyeb.ieltspractice.nav.Nav
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Shown to guests on screens that keep personal data (review, mistakes, coach, and the full mock). */
@Composable
fun GuestGate(icon: ImageVector = Icons.Rounded.Person, title: String, body: String, nav: Nav, reason: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
        // The account flow returns here; hasAccount flips and the real screen replaces this one.
        EmptyState(icon, title, body, action = "Create an account", onAction = { nav.requireAccount(reason) {} })
    }
}

/**
 * Page-by-page loader for a list the server pages (mistakes, prompts). [reset] with the new filters starts over;
 * [more] loads the next page and is safe to call from a "reached the end" effect.
 */
@Stable
internal class Pager<T>(private val scope: CoroutineScope) {
    var items by mutableStateOf<List<T>>(emptyList())
        private set
    var total by mutableIntStateOf(0)
        private set
    var loading by mutableStateOf(true)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    /** The first page has arrived (an empty list is then a real empty state). */
    var loaded by mutableStateOf(false)
        private set

    private var pages = 0
    private var generation = 0
    private var job: Job? = null
    private var fetch: suspend (page: Int) -> Pair<List<T>, Int> = { emptyList<T>() to 0 }

    val hasMore: Boolean get() = items.size < total

    fun reset(fetch: suspend (page: Int) -> Pair<List<T>, Int>) {
        job?.cancel()
        generation++
        this.fetch = fetch
        items = emptyList(); total = 0; pages = 0; error = null; loaded = false
        more()
    }

    fun more() {
        if (job?.isActive == true || (loaded && !hasMore)) return
        val g = generation
        val run = fetch
        loading = true
        error = null
        job = scope.launch {
            try {
                val (list, t) = run(pages + 1)
                if (g != generation) return@launch
                items = items + list; total = t; pages++; loaded = true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (g == generation) error = e.message?.takeIf { it.isNotBlank() } ?: "Something went wrong. Check your connection and try again."
            } finally {
                if (g == generation) loading = false
            }
        }
    }

    fun update(f: (T) -> T) { items = items.map(f) }
}
