package com.blueorbit.ruler.core.geometry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Rule 7: the ± bound must grow with segment count, and it is an estimate, not a guarantee. */
class ErrorEstimateTest {

    private val alignment = 0.5
    private val budget = 0.2

    @Test
    fun boundIsMonotonicInSegmentCount() {
        var previous = -1.0
        for (n in 1..12) {
            val total = n * 100_000L
            val bound = ErrorEstimate.boundMm(n, total, budget, alignment)
            assertTrue("bound must widen as segments accumulate (n=$n)", bound > previous)
            previous = bound
        }
    }

    @Test
    fun eachSegmentAddsTheAlignmentTerm() {
        val total = 300_000L
        val difference = ErrorEstimate.boundMm(3, total, budget, alignment) -
            ErrorEstimate.boundMm(2, total, budget, alignment)
        assertEquals(alignment, difference, 1e-9)
    }

    @Test
    fun boundForThreeSegmentsIsAlignmentTimesThreePlusTheCalibrationTerm() {
        // 3 segments x 0.5 mm alignment + 300 mm x 0.2 mm per 100 mm.
        assertEquals(1.5 + 0.6, ErrorEstimate.boundMm(3, 300_000L, budget, alignment), 1e-9)
    }

    @Test
    fun withNoSegmentsOnlyTheCalibrationTermIsLeft() {
        assertEquals(0.0, ErrorEstimate.boundMm(0, 0L, budget, alignment), 1e-9)
        assertEquals(0.4, ErrorEstimate.boundMm(0, 200_000L, budget, alignment), 1e-9)
    }

    @Test
    fun theBoundIsNeverNegativeAndTheDefaultAssumptionIsStated() {
        assertTrue(ErrorEstimate.boundMm(1, 0L, 0.0, alignment) > 0.0)
        assertEquals(0.5, ErrorEstimate.DEFAULT_ALIGNMENT_MM_PER_SEGMENT, 1e-9)
    }
}
