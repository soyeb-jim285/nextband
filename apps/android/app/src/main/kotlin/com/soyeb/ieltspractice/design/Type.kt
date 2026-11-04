package com.soyeb.ieltspractice.design

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.soyeb.ieltspractice.R

// Hanken Grotesk = interface. Newsreader = only what the learner reads or writes. Both are bundled variable fonts (OFL).
private fun variable(res: Int, vararg weights: Int) =
    FontFamily(weights.map { w -> Font(res, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w))) })

val Hanken = variable(R.font.hanken_grotesk, 400, 500, 600, 700)
val Newsreader = variable(R.font.newsreader, 400, 500, 600)

private fun sans(size: Int, line: Int, weight: Int = 400, tracking: Double = 0.0, tnum: Boolean = false) = TextStyle(
    fontFamily = Hanken, fontWeight = FontWeight(weight), fontSize = size.sp, lineHeight = line.sp, letterSpacing = tracking.em,
    fontFeatureSettings = if (tnum) "tnum, lnum" else null,
    // All copy is English: in an RTL locale each paragraph still runs LTR, so "fur." never becomes ".fur". The layout still mirrors.
    textDirection = TextDirection.Content,
)

private fun serif(size: Int, line: Int, weight: Int = 400, tracking: Double = -0.003) = TextStyle(
    fontFamily = Newsreader, fontWeight = FontWeight(weight), fontSize = size.sp, lineHeight = line.sp, letterSpacing = tracking.em,
    textDirection = TextDirection.Content,
)

/** The NextBand type scale (DESIGN.md). Read with `NB.type`. Colour comes from the caller or LocalContentColor. */
@Immutable
object NbType {
    val display = sans(48, 52, 700, -0.02, tnum = true)
    /** The one big title on a BrandHero / welcome / auth header. */
    val heroTitle = sans(30, 36, 600, -0.015)
    val headline = sans(28, 34, 600, -0.012)
    val title1 = sans(22, 28, 600, -0.008)
    val title2 = sans(18, 24, 600, -0.004)
    val title3 = sans(16, 22, 600)
    val body = sans(16, 24)
    val bodySmall = sans(14, 20)
    val label = sans(14, 20, 600, 0.004)
    val caption = sans(12, 16, 500, 0.01)
    val overline = sans(11, 16, 700, 0.08)
    /** Numbers that change (timers, counts, scores): tabular so they don't jiggle. */
    val num = sans(16, 22, 600, tnum = true)
    fun band(size: Int) = sans(size, size + 4, 700, -0.02, tnum = true)

    val reading = serif(18, 29)
    val readingSmall = serif(16, 25)
    val readingTitle = serif(24, 30, 600, -0.01)
}

/** Material slots so stock M3 components pick up our faces. */
val NbTypography = Typography(
    displayLarge = NbType.display, displayMedium = sans(40, 46, 700, -0.02), displaySmall = sans(34, 40, 700, -0.02),
    headlineLarge = sans(32, 38, 600, -0.015), headlineMedium = NbType.headline, headlineSmall = sans(24, 30, 600, -0.01),
    titleLarge = NbType.title1, titleMedium = NbType.title3, titleSmall = sans(14, 20, 600),
    bodyLarge = NbType.body, bodyMedium = NbType.bodySmall, bodySmall = sans(12, 16),
    labelLarge = NbType.label, labelMedium = sans(12, 16, 600), labelSmall = sans(11, 16, 600, 0.02),
)
