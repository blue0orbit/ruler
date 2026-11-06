package com.blueorbit.ruler.core.geometry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * The card case (ISO/IEC 7810 ID-1, 85.60 x 53.98 mm) — the reference used by the calibration
 * wizard in M2. These tests pin the geometry contract before any UI exists.
 */
class PixelPerMmTest {

    private val cardLongEdgeMm = 85.60
    private val cardShortEdgeMm = 53.98

    @Test
    fun cardCalibrationDerivesTrueDensityFromMeasuredPixels() {
        // A card whose long edge spans 1419 px on the test device.
        val pxPerMm = PixelPerMm(1419.0 / cardLongEdgeMm)

        // The short edge must then land inside the ±0.5 mm acceptance window (PLAN M2).
        val shortEdgePx = pxPerMm.mmToPixels(cardShortEdgeMm)
        val measuredShortEdgeMm = pxPerMm.pixelsToMm(shortEdgePx)
        assertEquals(cardShortEdgeMm, measuredShortEdgeMm, 0.5)
    }

    @Test
    fun densityGuessIsNotTrustedForTheCardCase() {
        val guess = PixelPerMm(390.0 / 25.4) // densityDpi on the test device
        val calibrated = PixelPerMm(411.0 / 25.4) // what a card measurement actually yields

        val errorMm = abs(calibrated.pixelsToMm(guess.mmToPixels(cardLongEdgeMm)) - cardLongEdgeMm)
        // The guess misses the card edge by far more than the ±0.5 mm window, which is exactly
        // why it is labelled a guess and never silently trusted (rule 4).
        assertTrue("guess error should exceed the acceptance window, was ${errorMm} mm", errorMm > 0.5)
    }

    @Test
    fun micrometreConversionHasNoFloatDrift() {
        val pxPerMm = PixelPerMm(16.5)
        assertEquals(16.5, pxPerMm.micrometresToPixels(1_000L), 1e-9)   // 1 mm
        assertEquals(1_650.0, pxPerMm.micrometresToPixels(100_000L), 1e-9) // 100 mm
    }
}
