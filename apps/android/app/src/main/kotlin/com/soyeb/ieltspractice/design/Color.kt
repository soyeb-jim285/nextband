package com.soyeb.ieltspractice.design

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * NextBand colour tokens (DESIGN.md section 2). Read them with `NB.colors`.
 * Teal = act here / you are here. good/warn/bad = band vs target and real errors only. Skill hues = identity only.
 */
@Immutable
class NbColors(
    val bg: Color,
    val surface: Color,
    val surface2: Color,
    val surface3: Color,
    val ink: Color,
    val muted: Color,
    val faint: Color,
    val line: Color,
    val brand: Color,
    val onBrand: Color,
    val brandSoft: Color,
    val brandText: Color,
    val good: Color, val goodText: Color, val goodSoft: Color,
    val warn: Color, val warnText: Color, val warnSoft: Color,
    val bad: Color, val badText: Color, val badSoft: Color,
    val listening: Color, val listeningSoft: Color,
    val reading: Color, val readingSoft: Color,
    val writing: Color, val writingSoft: Color,
    val speaking: Color, val speakingSoft: Color,
    val scrim: Color,
    val isDark: Boolean,
)

val LightColors = NbColors(
    bg = Color(0xFFF6F8FA), surface = Color(0xFFFFFFFF), surface2 = Color(0xFFEEF2F6), surface3 = Color(0xFFE3E9F0),
    ink = Color(0xFF0F172A), muted = Color(0xFF536175), faint = Color(0xFF7A8AA0), line = Color(0xFFE3E8EF),
    brand = Color(0xFF0F766E), onBrand = Color(0xFFFFFFFF), brandSoft = Color(0xFFDDF3EF), brandText = Color(0xFF0B5F58),
    good = Color(0xFF15803D), goodText = Color(0xFF166534), goodSoft = Color(0xFFDCFCE7),
    warn = Color(0xFFB45309), warnText = Color(0xFF92400E), warnSoft = Color(0xFFFEF3C7),
    bad = Color(0xFFBE123C), badText = Color(0xFFBE123C), badSoft = Color(0xFFFFE4EA),
    listening = Color(0xFF0369A1), listeningSoft = Color(0xFFE0F2FE),
    reading = Color(0xFF4338CA), readingSoft = Color(0xFFE0E7FF),
    writing = Color(0xFFA21CAF), writingSoft = Color(0xFFFAE8FF),
    speaking = Color(0xFFC2410C), speakingSoft = Color(0xFFFFEDD5),
    scrim = Color(0x99000000),
    isDark = false,
)

val DarkColors = NbColors(
    bg = Color(0xFF0A111C), surface = Color(0xFF111B2B), surface2 = Color(0xFF18243A), surface3 = Color(0xFF22304A),
    ink = Color(0xFFE6EDF5), muted = Color(0xFF93A1B5), faint = Color(0xFF64748B), line = Color(0xFF1D293B),
    brand = Color(0xFF2DD4BF), onBrand = Color(0xFF042F2C), brandSoft = Color(0xFF0E2F33), brandText = Color(0xFF5EEAD4),
    good = Color(0xFF4ADE80), goodText = Color(0xFF4ADE80), goodSoft = Color(0xFF0B2E1A),
    warn = Color(0xFFFBBF24), warnText = Color(0xFFFBBF24), warnSoft = Color(0xFF33240A),
    bad = Color(0xFFFB7185), badText = Color(0xFFFB7185), badSoft = Color(0xFF3B0A18),
    listening = Color(0xFF38BDF8), listeningSoft = Color(0xFF0B2536),
    reading = Color(0xFFA5B4FC), readingSoft = Color(0xFF1E1B4B),
    writing = Color(0xFFE879F9), writingSoft = Color(0xFF3A0D3F),
    speaking = Color(0xFFFB923C), speakingSoft = Color(0xFF3A1A0B),
    scrim = Color(0xB3000000),
    isDark = true,
)

/** Material 3 slots mapped onto our tokens so stock M3 components (dialogs, menus, text fields) look native to the app. */
fun schemeFor(c: NbColors): ColorScheme = if (c.isDark) darkColorScheme(
    primary = c.brand, onPrimary = c.onBrand, primaryContainer = c.brandSoft, onPrimaryContainer = c.brandText,
    secondary = c.muted, onSecondary = c.bg, secondaryContainer = c.surface2, onSecondaryContainer = c.ink,
    tertiary = c.listening, onTertiary = c.bg, tertiaryContainer = c.listeningSoft, onTertiaryContainer = c.ink,
    error = c.bad, onError = Color(0xFF3B0613), errorContainer = c.badSoft, onErrorContainer = c.badText,
    background = c.bg, onBackground = c.ink, surface = c.surface, onSurface = c.ink,
    surfaceVariant = c.surface2, onSurfaceVariant = c.muted, surfaceTint = Color.Transparent,
    inverseSurface = c.ink, inverseOnSurface = c.bg, inversePrimary = LightColors.brand,
    outline = c.faint, outlineVariant = c.line, scrim = Color.Black,
    surfaceBright = c.surface2, surfaceDim = c.bg,
    surfaceContainerLowest = c.bg, surfaceContainerLow = c.surface, surfaceContainer = c.surface,
    surfaceContainerHigh = c.surface2, surfaceContainerHighest = c.surface3,
) else lightColorScheme(
    primary = c.brand, onPrimary = c.onBrand, primaryContainer = c.brandSoft, onPrimaryContainer = c.brandText,
    secondary = c.muted, onSecondary = Color.White, secondaryContainer = c.surface2, onSecondaryContainer = c.ink,
    tertiary = c.listening, onTertiary = Color.White, tertiaryContainer = c.listeningSoft, onTertiaryContainer = c.ink,
    error = c.bad, onError = Color.White, errorContainer = c.badSoft, onErrorContainer = c.badText,
    background = c.bg, onBackground = c.ink, surface = c.surface, onSurface = c.ink,
    surfaceVariant = c.surface2, onSurfaceVariant = c.muted, surfaceTint = Color.Transparent,
    inverseSurface = c.ink, inverseOnSurface = c.bg, inversePrimary = DarkColors.brand,
    outline = c.faint, outlineVariant = c.line, scrim = Color.Black,
    surfaceBright = c.surface, surfaceDim = c.surface2,
    surfaceContainerLowest = c.surface, surfaceContainerLow = c.surface, surfaceContainer = c.surface,
    surfaceContainerHigh = c.surface2, surfaceContainerHighest = c.surface3,
)
