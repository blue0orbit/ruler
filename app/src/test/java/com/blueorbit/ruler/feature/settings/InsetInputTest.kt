package com.blueorbit.ruler.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.text.NumberFormat
import java.util.Locale

/**
 * The bezel-inset field used to fall back to 0 mm for anything `toDoubleOrNull` could not read — which
 * included the field's own German prefill ("1,5"). Saving without an edit wiped the inset.
 */
class InsetInputTest {

    @Test
    fun aCommaIsADecimalPoint(): Unit {
        assertEquals(1.5, parseInsetMm("1,5")!!, 1e-9)
        assertEquals(1.5, parseInsetMm("1.5")!!, 1e-9)
    }

    @Test
    fun theFieldsOwnGermanPrefillReadsBackAsTheSameValue(): Unit {
        // Exactly what the screen writes into the field on a German phone.
        val format = NumberFormat.getInstance(Locale.GERMANY).apply { maximumFractionDigits = 2 }
        val prefill = format.format(1.25)
        assertEquals("1,25", prefill)
        assertEquals(1.25, parseInsetMm(prefill)!!, 1e-9)
    }

    @Test
    fun wholeNumbersAndLooseSeparatorsRead(): Unit {
        assertEquals(0.0, parseInsetMm("0")!!, 1e-9)
        assertEquals(3.0, parseInsetMm(" 3 ")!!, 1e-9)
        assertEquals(3.0, parseInsetMm("3.")!!, 1e-9)
        assertEquals(0.5, parseInsetMm(",5")!!, 1e-9)
        assertEquals(20.0, parseInsetMm("20")!!, 1e-9)
    }

    @Test
    fun aTypoIsRefusedAndNeverBecomesZero(): Unit {
        listOf("", " ", "abc", "1,5mm", "1..5", "1,2,3", "-1", "+2", ".", ",").forEach { text ->
            assertNull("\"$text\" was read as an inset", parseInsetMm(text))
        }
    }

    @Test
    fun thingsDoubleWouldAcceptButNobodyMeansAreRefused(): Unit {
        listOf("1e1", "NaN", "Infinity", "1d", "1f", "0x1p3").forEach { text ->
            assertNull("\"$text\" was read as an inset", parseInsetMm(text))
        }
    }

    @Test
    fun aValueOutsideTheRangeIsRefusedRatherThanClamped(): Unit {
        assertNull(parseInsetMm("20,01"))
        assertNull(parseInsetMm("150"))
    }
}
