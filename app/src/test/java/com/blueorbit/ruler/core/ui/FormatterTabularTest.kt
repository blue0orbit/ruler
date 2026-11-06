package com.blueorbit.ruler.core.ui

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.blueorbit.ruler.core.geometry.FormatMode
import com.blueorbit.ruler.core.geometry.Formatter
import com.blueorbit.ruler.core.geometry.MeasurementUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

/**
 * Tabular figures (DESIGN.md §3) are what stop a readout reflowing while the device moves: proportional
 * digits make "11 mm" narrower than "18 mm", so the number the user is reading jumps around. The
 * feature is a string, so the failure mode is a typo that does nothing at all — hence the tag itself
 * is asserted, plus the rule that applying it is presentation-only.
 */
class FormatterTabularTest {

    @Test
    fun theFeatureTagIsTheOpenTypeOne() {
        // "tnum" is the OpenType feature tag for tabular figures. Anything else is silently ignored by
        // the text stack, which is the failure mode this guards.
        assertEquals("tnum", TABULAR_FONT_FEATURES)
    }

    @Test
    fun tabularSetsTheFeatureAndNothingElse() {
        val base = TextStyle(
            fontSize = 44.sp,
            lineHeight = 52.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = (-0.2).em
        )
        assertNull(base.fontFeatureSettings)

        val tabular = base.tabular()
        assertEquals(TABULAR_FONT_FEATURES, tabular.fontFeatureSettings)
        assertEquals(base.fontSize, tabular.fontSize)
        assertEquals(base.lineHeight, tabular.lineHeight)
        assertEquals(base.fontWeight, tabular.fontWeight)
        assertEquals(base.letterSpacing, tabular.letterSpacing)
        assertEquals(base.fontFamily, tabular.fontFamily)
    }

    @Test
    fun applyingItTwiceChangesNothingTheSecondTime() {
        val once = TextStyle(fontSize = 16.sp).tabular()
        assertEquals(once, once.tabular())
    }

    @Test
    fun theFeatureSurvivesACopyThatChangesSize() {
        // The scale is built by copying roles around; a tabular role must stay tabular after a copy.
        assertEquals(TABULAR_FONT_FEATURES, TextStyle(fontSize = 16.sp).tabular().copy(fontSize = 22.sp).fontFeatureSettings)
    }

    @Test
    fun tabularFiguresArePresentationOnlyTheNumberIsUntouched() {
        for (unit in MeasurementUnit.entries) {
            for (mode in FormatMode.entries) {
                val plain = Formatter.format(12_350L, unit, mode)
                val again = Formatter.format(12_350L, unit, mode)
                assertEquals("$unit/$mode", plain, again)

                // The helper takes a style and returns a style; it cannot reach the value at all.
                val style = TextStyle(fontSize = 44.sp).tabular()
                assertNotEquals(TextStyle(fontSize = 44.sp), style)
                assertEquals(plain.text, Formatter.numeric(12_350L, unit, mode))
            }
        }
    }

    @Test
    fun theDigitsStayAsciiSoTheFontFeatureCanApply() {
        // A font feature applies to glyphs the string actually contains, so the digits must stay ASCII
        // 0-9 in every locale. The decimal separator is the reader's own ("12,4" in German) — "tnum"
        // styles digits, not separators, so a comma does not stop the figures being tabular.
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMAN)
            assertEquals("12,4", Formatter.numeric(12_350L, MeasurementUnit.MM, FormatMode.DECIMAL))
            assertEquals("1,2", Formatter.numeric(12_350L, MeasurementUnit.CM, FormatMode.DECIMAL))
            assertEquals("0,49", Formatter.numeric(12_350L, MeasurementUnit.INCH, FormatMode.DECIMAL))
            Locale.setDefault(Locale.forLanguageTag("ar-EG"))
            val arabic = Formatter.numeric(12_350L, MeasurementUnit.MM, FormatMode.DECIMAL)
            assertEquals("digits stay ASCII", true, arabic.all { it in '0'..'9' || !it.isDigit() })
        } finally {
            Locale.setDefault(previous)
        }
    }
}
