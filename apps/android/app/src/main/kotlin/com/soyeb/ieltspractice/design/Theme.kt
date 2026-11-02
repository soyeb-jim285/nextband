package com.soyeb.ieltspractice.design

import android.provider.Settings
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

val LocalNbColors = staticCompositionLocalOf { LightColors }

/** True when the system "Remove animations" setting is on (animator duration scale 0). Skip decorative motion. */
val LocalReducedMotion = staticCompositionLocalOf { false }

/** Token accessor: `NB.colors.brand`, `NB.type.title2`, `NB.space.gutter`. */
object NB {
    val colors: NbColors @Composable @ReadOnlyComposable get() = LocalNbColors.current
    val type = NbType
    val space = NbSpace
    val shape = NbShape
}

object NbSpace {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 20.dp
    val xxl = 28.dp
    val gutter = 20.dp
    val section = 28.dp
    val cardPad = 16.dp
}

object NbShape {
    val chip = RoundedCornerShape(8.dp)
    val tile = RoundedCornerShape(12.dp)
    val card = RoundedCornerShape(20.dp)
    val sheet = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    val pill = RoundedCornerShape(percent = 50)
}

private val M3Shapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp), small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp), extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun NbTheme(dark: Boolean, content: @Composable () -> Unit) {
    val colors = if (dark) DarkColors else LightColors
    val context = LocalContext.current
    val reduced = remember(context) {
        runCatching { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }.getOrDefault(false)
    }
    CompositionLocalProvider(LocalNbColors provides colors, LocalReducedMotion provides reduced) {
        MaterialTheme(colorScheme = schemeFor(colors), typography = NbTypography, shapes = M3Shapes, content = content)
    }
}

/** The four tests. Identity hue + icon; never carries band meaning. */
enum class Skill(val key: String, val label: String, val icon: ImageVector) {
    Listening("listening", "Listening", Icons.Rounded.Headphones),
    Reading("reading", "Reading", Icons.AutoMirrored.Rounded.MenuBook),
    Writing("writing", "Writing", Icons.Rounded.EditNote),
    Speaking("speaking", "Speaking", Icons.Rounded.Mic);

    companion object {
        fun of(key: String?): Skill? = entries.firstOrNull { it.key.equals(key, ignoreCase = true) }
    }
}

val Skill.color: Color @Composable @ReadOnlyComposable get() = when (this) {
    Skill.Listening -> NB.colors.listening
    Skill.Reading -> NB.colors.reading
    Skill.Writing -> NB.colors.writing
    Skill.Speaking -> NB.colors.speaking
}

val Skill.soft: Color @Composable @ReadOnlyComposable get() = when (this) {
    Skill.Listening -> NB.colors.listeningSoft
    Skill.Reading -> NB.colors.readingSoft
    Skill.Writing -> NB.colors.writingSoft
    Skill.Speaking -> NB.colors.speakingSoft
}

/** Band vs target: good at/above, warn 0.5–1.0 below, bad 1.5+ below. */
enum class Tone { Good, Warn, Bad, Neutral }

fun bandTone(band: Double?, target: Double?): Tone = when {
    band == null || target == null -> Tone.Neutral
    band >= target -> Tone.Good
    target - band <= 1.0 -> Tone.Warn
    else -> Tone.Bad
}

@Composable @ReadOnlyComposable
fun Tone.fg(): Color = when (this) {
    Tone.Good -> NB.colors.goodText
    Tone.Warn -> NB.colors.warnText
    Tone.Bad -> NB.colors.badText
    Tone.Neutral -> NB.colors.ink
}

@Composable @ReadOnlyComposable
fun Tone.fill(): Color = when (this) {
    Tone.Good -> NB.colors.good
    Tone.Warn -> NB.colors.warn
    Tone.Bad -> NB.colors.bad
    Tone.Neutral -> NB.colors.brand
}

@Composable @ReadOnlyComposable
fun Tone.soft(): Color = when (this) {
    Tone.Good -> NB.colors.goodSoft
    Tone.Warn -> NB.colors.warnSoft
    Tone.Bad -> NB.colors.badSoft
    Tone.Neutral -> NB.colors.surface2
}

/** "6.5", "7", "–" */
fun fmtBand(b: Double?): String = if (b == null) "–" else String.format(java.util.Locale.US, "%.1f", b)
