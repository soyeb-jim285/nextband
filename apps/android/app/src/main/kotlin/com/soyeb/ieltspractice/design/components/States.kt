package com.soyeb.ieltspractice.design.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.soyeb.ieltspractice.design.LocalReducedMotion
import com.soyeb.ieltspractice.design.NB
import kotlinx.coroutines.CancellationException

sealed interface Load<out T> {
    data object Loading : Load<Nothing>
    data class Failed(val message: String) : Load<Nothing>
    data class Ready<T>(val value: T) : Load<T>
}

/** [refreshing] is true while a reload runs over already-shown data (pull-to-refresh), so content isn't swapped for a skeleton. */
class Loader<T>(val state: Load<T>, val refreshing: Boolean, val reload: () -> Unit) {
    val value: T? get() = (state as? Load.Ready<T>)?.value
}

/**
 * Run [block] when the screen enters, when [keys] change, and on [Loader.reload]. A reload keeps the last good value visible.
 * Exceptions become a user-facing message (Api errors carry display-ready messages).
 */
@Composable
fun <T> rememberLoad(vararg keys: Any?, block: suspend () -> T): Loader<T> {
    var attempt by remember { mutableIntStateOf(0) }
    var state by remember(*keys) { mutableStateOf<Load<T>>(Load.Loading) }
    var refreshing by remember { mutableStateOf(false) }
    val current by rememberUpdatedState(block)
    LaunchedEffect(*keys, attempt) {
        if (state is Load.Ready) refreshing = true else state = Load.Loading
        state = try {
            Load.Ready(current())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (state is Load.Ready) state.also { /* keep showing data; caller may snack */ } else Load.Failed(e.message?.takeIf { it.isNotBlank() } ?: "Something went wrong. Check your connection and try again.")
        } finally {
            refreshing = false
        }
    }
    return Loader(state, refreshing) { attempt++ }
}

/** Skeleton while loading, [ErrorState] on failure, [content] when ready. */
@Composable
fun <T> LoadContent(
    loader: Loader<T>,
    modifier: Modifier = Modifier,
    skeleton: @Composable () -> Unit = { SkeletonList() },
    content: @Composable (T) -> Unit,
) {
    when (val s = loader.state) {
        Load.Loading -> Box(modifier) { skeleton() }
        is Load.Failed -> ErrorState(s.message, loader.reload, modifier)
        is Load.Ready -> content(s.value)
    }
}

/** Shimmering placeholder block. Static under reduced motion. */
@Composable
fun Skeleton(modifier: Modifier = Modifier, height: Dp = 16.dp, corner: Dp = 8.dp) {
    val reduced = LocalReducedMotion.current
    val a = if (reduced) 0.7f else {
        val t = rememberInfiniteTransition(label = "sk")
        t.animateFloat(0.45f, 0.9f, infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse), label = "a").value
    }
    Box(modifier.height(height).alpha(a).background(NB.colors.surface3, RoundedCornerShape(corner)))
}

/** Generic page skeleton: a hero block and a few rows. */
@Composable
fun SkeletonList(modifier: Modifier = Modifier, rows: Int = 4) {
    Column(
        modifier.fillMaxWidth().padding(NB.space.gutter).semantics { contentDescription = "Loading" },
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Skeleton(Modifier.fillMaxWidth(), height = 140.dp, corner = 20.dp)
        repeat(rows) { Skeleton(Modifier.fillMaxWidth(), height = 64.dp, corner = 16.dp) }
    }
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IconTile(icon, NB.colors.brand, NB.colors.brandSoft, 64.dp)
        Text(title, style = NB.type.title2, color = NB.colors.ink, textAlign = TextAlign.Center)
        Text(body, style = NB.type.bodySmall, color = NB.colors.muted, textAlign = TextAlign.Center)
        if (action != null && onAction != null) NbButton(action, onAction, kind = BtnKind.Secondary, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
fun ErrorState(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IconTile(Icons.Rounded.CloudOff, NB.colors.badText, NB.colors.badSoft, 64.dp)
        Text("Couldn't load this", style = NB.type.title2, color = NB.colors.ink, textAlign = TextAlign.Center)
        Text(message, style = NB.type.bodySmall, color = NB.colors.muted, textAlign = TextAlign.Center)
        NbButton("Try again", onRetry, kind = BtnKind.Secondary, icon = Icons.Rounded.Refresh, modifier = Modifier.padding(top = 4.dp))
    }
}

/** Inline alert banner (not a card): soft tone fill, icon, text, optional action. */
@Composable
fun Banner(
    text: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    tone: com.soyeb.ieltspractice.design.Tone = com.soyeb.ieltspractice.design.Tone.Neutral,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val bg = when (tone) {
        com.soyeb.ieltspractice.design.Tone.Good -> NB.colors.goodSoft
        com.soyeb.ieltspractice.design.Tone.Warn -> NB.colors.warnSoft
        com.soyeb.ieltspractice.design.Tone.Bad -> NB.colors.badSoft
        com.soyeb.ieltspractice.design.Tone.Neutral -> NB.colors.brandSoft
    }
    val fg = when (tone) {
        com.soyeb.ieltspractice.design.Tone.Good -> NB.colors.goodText
        com.soyeb.ieltspractice.design.Tone.Warn -> NB.colors.warnText
        com.soyeb.ieltspractice.design.Tone.Bad -> NB.colors.badText
        com.soyeb.ieltspractice.design.Tone.Neutral -> NB.colors.brandText
    }
    androidx.compose.foundation.layout.Row(
        modifier.fillMaxWidth().background(bg, NB.shape.tile).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        androidx.compose.material3.Icon(icon, null, Modifier.size(20.dp), tint = fg)
        Text(text, style = NB.type.bodySmall, color = NB.colors.ink, modifier = Modifier.weight(1f))
        if (action != null && onAction != null) NbButton(action, onAction, kind = BtnKind.Text, size = BtnSize.Small)
    }
}
