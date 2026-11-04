package com.soyeb.ieltspractice.feature.streak

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.soyeb.ieltspractice.data.Prefs
import com.soyeb.ieltspractice.data.model.Streak
import com.soyeb.ieltspractice.data.net.Api
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The last streak the app loaded (AppGraph keeps one), shared by the Today card, the streak screen and the daily
 * celebration. Today refreshes it on entry; a finished result or drill refreshes it with `afterPractice = true`, which
 * opens the celebration sheet ([celebrating], drawn by StreakCelebrationHost) the first time the day turns done.
 */
class StreakStore(private val api: Api, private val prefs: Prefs) {
    private val _value = MutableStateFlow<Streak?>(null)
    /** Null before the first load and after a failed one (the card hides). */
    val value: StateFlow<Streak?> = _value.asStateFlow()

    /** The streak the celebration sheet shows; null = closed. */
    var celebrating by mutableStateOf<Streak?>(null)

    fun clear() { _value.value = null }

    /** Reloads; null when the call fails. Call from the main thread (it may open the celebration). */
    suspend fun refresh(afterPractice: Boolean = false): Streak? {
        val prevDone = _value.value?.done
        val now = try { api.streak() } catch (e: CancellationException) { throw e } catch (e: Exception) { _value.value = null; return null }
        _value.value = now
        val today = localToday(now)
        if (shouldCelebrate(prevDone, afterPractice, now, prefs.getString(SHOWN), today)) {
            prefs.setString(SHOWN, today)
            celebrating = now
        }
        return now
    }

    companion object {
        /** Prefs key: the local day the celebration last showed. */
        const val SHOWN = "streak:celebrated"
    }
}
