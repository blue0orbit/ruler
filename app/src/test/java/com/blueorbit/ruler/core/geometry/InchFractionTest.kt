package com.blueorbit.ruler.core.geometry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** M3 maths: nearest 1/32, reduced, round-half-up, with the approximate flag (rule 3). */
class InchFractionTest {

    @Test
    fun oneInchIsExactAndNotApproximate() {
        val fraction = InchFractions.fromMicrometres(Physical.MICROMETRES_PER_INCH)
        assertEquals(1L, fraction.wholeInches)
        assertEquals(0L, fraction.numerator)
        assertEquals(1L, fraction.denominator)
        assertFalse(fraction.approximate)
        assertEquals("1", fraction.format())
    }

    @Test
    fun fractionsStayReduced() {
        // 9.02 thirty-seconds: the display must be 9/32, never 18/64.
        val fraction = InchFractions.fromMicrometres(7162)
        assertEquals(9L, fraction.numerator)
        assertEquals(32L, fraction.denominator)
        assertTrue(fraction.approximate)
        assertEquals("9/32", fraction.format())
    }

    @Test
    fun sixteenThirtySecondsReducesToOneHalf() {
        val fraction = InchFractions.fromMicrometres(12700) // exactly 16/32
        assertEquals(1L, fraction.numerator)
        assertEquals(2L, fraction.denominator)
        assertFalse(fraction.approximate)
        assertEquals("1/2", fraction.format())
    }

    @Test
    fun wholeInchesAndFractionsAreCombined() {
        val fraction = InchFractions.fromMicrometres(32562) // 1 in + 9.02 thirty-seconds
        assertEquals(1L, fraction.wholeInches)
        assertEquals(9L, fraction.numerator)
        assertEquals("1 9/32", fraction.format())
    }

    @Test
    fun roundingIsHalfUpAtTheFractionBoundary() {
        assertEquals("9/32", InchFractions.fromMicrometres(7466).format()) // 9.41 -> 9
        assertEquals("5/16", InchFractions.fromMicrometres(7563).format()) // 9.53 -> 10 -> 5/16
    }

    @Test
    fun zeroMeasuresAsZero() {
        val fraction = InchFractions.fromMicrometres(0)
        assertEquals("0", fraction.format())
        assertFalse(fraction.approximate)
    }

    @Test
    fun anInchIsExactly25Point4MillimetresInternally() {
        assertEquals(25_400L, Physical.MICROMETRES_PER_INCH)
        assertEquals(1.0, MeasurementUnit.INCH.value(25_400L), 1e-9)
        assertEquals(25_400L, MeasurementUnit.INCH.micrometres(1.0))
    }
}
