package com.soyeb.ieltspractice.design

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test

/** WCAG AA for every token pair the UI draws: 4.5 for text, 3.0 for icons, fills and control edges. */
class ContrastTest {
    private fun ratio(a: Color, b: Color): Double {
        val la = a.luminance() + 0.05
        val lb = b.luminance() + 0.05
        return (maxOf(la, lb) / minOf(la, lb)).toDouble()
    }

    private fun check(name: String, c: NbColors) {
        val fails = mutableListOf<String>()
        fun need(label: String, fg: Color, bg: Color, min: Double) {
            val r = ratio(fg, bg)
            if (r < min) fails += "$name $label ${"%.2f".format(r)} < $min"
        }
        for ((bgName, bg) in listOf("bg" to c.bg, "surface" to c.surface)) {
            need("ink/$bgName", c.ink, bg, 4.5)
            need("muted/$bgName", c.muted, bg, 4.5)
            need("brandText/$bgName", c.brandText, bg, 4.5)
            need("goodText/$bgName", c.goodText, bg, 4.5)
            need("warnText/$bgName", c.warnText, bg, 4.5)
            need("badText/$bgName", c.badText, bg, 4.5)
            need("faint/$bgName (control edge)", c.faint, bg, 3.0)
            need("brand/$bgName (fill)", c.brand, bg, 3.0)
            for ((s, col) in listOf("listening" to c.listening, "reading" to c.reading, "writing" to c.writing, "speaking" to c.speaking))
                need("$s/$bgName (icon)", col, bg, 3.0)
        }
        need("ink/surface2", c.ink, c.surface2, 4.5)
        need("muted/surface2", c.muted, c.surface2, 4.5)
        need("onBrand/brand", c.onBrand, c.brand, 4.5)
        need("onBad/bad", c.onBad, c.bad, 4.5)
        need("brandText/brandSoft", c.brandText, c.brandSoft, 4.5)
        need("ink/brandSoft", c.ink, c.brandSoft, 4.5)
        need("goodText/goodSoft", c.goodText, c.goodSoft, 4.5)
        need("warnText/warnSoft", c.warnText, c.warnSoft, 4.5)
        need("badText/badSoft", c.badText, c.badSoft, 4.5)
        need("listening/soft", c.listening, c.listeningSoft, 3.0)
        need("reading/soft", c.reading, c.readingSoft, 3.0)
        need("writing/soft", c.writing, c.writingSoft, 3.0)
        need("speaking/soft", c.speaking, c.speakingSoft, 3.0)
        need("bg/ink (snackbar)", c.bg, c.ink, 4.5)
        assertTrue(fails.joinToString("\n"), fails.isEmpty())
    }

    @Test fun light() = check("light", LightColors)
    @Test fun dark() = check("dark", DarkColors)
}
