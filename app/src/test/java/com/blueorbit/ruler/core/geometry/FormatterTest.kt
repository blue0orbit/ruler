package com.blueorbit.ruler.core.geometry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Formatting happens only at the display boundary (rule 2), and the rounding rule is tested. */
class FormatterTest {

    @Test
    fun millimetresRoundHalfUpAtTheDisplayBoundary() {
        // Banker's rounding would give 12.3; the rule is round-half-up.
        assertEquals("12.4", Formatter.numeric(12_350L, MeasurementUnit.MM, FormatMode.DECIMAL))
    }

    @Test
    fun centimetresRoundHalfUp() {
        assertEquals("1.3", Formatter.numeric(12_500L, MeasurementUnit.CM, FormatMode.DECIMAL))
        assertEquals("1.2", Formatter.numeric(12_449L, MeasurementUnit.CM, FormatMode.DECIMAL))
    }

    @Test
    fun decimalModeIsSelectableForInch() {
        val formatted = Formatter.format(38_100L, MeasurementUnit.INCH, FormatMode.DECIMAL)
        assertEquals("1.5", formatted.text)
        assertFalse(formatted.approximate)
    }

    @Test
    fun fractionModeIsSelectableForInch() {
        val formatted = Formatter.format(7_162L, MeasurementUnit.INCH, FormatMode.FRACTION)
        assertEquals("9/32", formatted.text)
        assertTrue(formatted.approximate)
    }

    @Test
    fun anExactReadingIsNeverFlaggedAsApproximate() {
        assertFalse(Formatter.format(25_400L, MeasurementUnit.INCH, FormatMode.FRACTION).approximate)
        assertEquals("1", Formatter.format(25_400L, MeasurementUnit.INCH, FormatMode.FRACTION).text)
    }

    @Test
    fun theApproximateFlagIsWhatTheUiUsesForTheMarker() {
        // Rule 5: the marker comes from a resource string, so the formatter returns a flag, not text.
        val formatted = Formatter.format(7_162L, MeasurementUnit.INCH, FormatMode.FRACTION)
        assertTrue(formatted.approximate)
        assertEquals("9/32", formatted.text)
    }
}
