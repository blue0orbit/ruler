package com.blueorbit.ruler.core.calibration

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * M2 maths. The acceptance window is the one in PLAN.md: after card calibration, the 53.98 mm edge
 * and a 100 mm segment must both read within ±0.5 mm.
 *
 * The `aligned` flag is the M11R finding 3 guard: only a reading the user actually aligned is
 * allowed to stamp a calibration.
 */
class CalibrationDerivationTest {

    /** Physical density of the test device (Pixel 9 Pro Fold, 390 dpi). */
    private val truePxPerMm = 390.0 / 25.4

    private val guess = Calibration(truePxPerMm, CalibrationSource.DEVICE_DEFAULT, 5.0)

    /** What a steel ruler would read for a targetMm segment drawn with `calibration`. */
    private fun readingMm(calibration: Calibration, targetMm: Double): Double =
        targetMm * truePxPerMm / calibration.pixelsPerMm

    @Test
    fun cardPathDerivesDensityFromTheMeasuredPixelSpan() {
        val measuredPx = truePxPerMm * References.cardShortEdge.knownMm
        val derived = CalibrationDerivation.derive(
            guess, References.cardShortEdge, measuredPx, 0.0, aligned = true
        )

        assertEquals(truePxPerMm, derived.pixelsPerMm, 1e-9)
        assertEquals(CalibrationSource.CARD, derived.source)
        assertTrue(derived.verified)
    }

    @Test
    fun cardPathMeetsThePlusMinusHalfMillimetreAcceptanceWindow() {
        // A 1 px alignment error on a 53.98 mm edge is well inside the window.
        val measuredPx = truePxPerMm * References.cardShortEdge.knownMm + 1.0
        val derived = CalibrationDerivation.derive(
            guess, References.cardShortEdge, measuredPx, 0.0, aligned = true
        )

        assertEquals(53.98, readingMm(derived, 53.98), 0.5)
        assertEquals(100.0, readingMm(derived, 100.0), 0.5)
    }

    @Test
    fun coinPathUsesTheCoinDiameterAndLabelsTheSource() {
        val coin = References.coins.first { it.id == "eur_2" }
        val measuredPx = truePxPerMm * coin.knownMm
        val derived = CalibrationDerivation.derive(guess, coin, measuredPx, 0.0, aligned = true)

        assertEquals(truePxPerMm, derived.pixelsPerMm, 1e-9)
        assertEquals(CalibrationSource.COIN, derived.source)
        // 0.10 mm tolerance over a 25.75 mm coin = 0.3883 mm per 100 mm.
        assertEquals(0.3883495145631068, derived.errorBudgetMmPer100Mm, 1e-9)
    }

    @Test
    fun manualPathLetstheSteelRulerDecideTheMillimetres() {
        // The drawn 100 mm segment is fixed in pixels; the ruler reads 100.5 mm.
        val derived = CalibrationDerivation.derive(
            guess, References.manual, 0.0, 100.5, aligned = true
        )

        assertEquals(truePxPerMm * 100.0 / 100.5, derived.pixelsPerMm, 1e-9)
        assertEquals(CalibrationSource.MANUAL, derived.source)
        assertEquals(0.5, derived.errorBudgetMmPer100Mm, 1e-9)
    }

    @Test
    fun anEmptyReadingNeverInventsACalibration() {
        assertEquals(
            guess, CalibrationDerivation.derive(guess, References.cardShortEdge, 0.0, 0.0, aligned = true)
        )
        assertEquals(
            guess, CalibrationDerivation.derive(guess, References.manual, 0.0, 0.0, aligned = true)
        )
    }

    @Test
    fun aGuessIsNeverReportedAsVerified() {
        assertFalse(guess.verified)
        assertTrue(
            CalibrationDerivation.derive(guess, References.cardLongEdge, 1314.2, 0.0, aligned = true).verified
        )
    }

    @Test
    fun anUntouchedWizardNeverStampsACalibration() {
        // M11R finding 3: the prefilled prediction must not be saved as a verified card reading.
        val measuredPx = truePxPerMm * References.cardShortEdge.knownMm
        val untouched = CalibrationDerivation.derive(
            guess, References.cardShortEdge, measuredPx, 0.0, aligned = false
        )

        assertEquals(guess, untouched)
        assertFalse(untouched.verified)
    }

    @Test
    fun aDerivedCalibrationKeepsTheStoredBezelInset() {
        // M11R finding 9: re-calibrating the density must not move the start line.
        val withInset = guess.copy(bezelInsetMm = 3.5)
        val measuredPx = truePxPerMm * References.cardShortEdge.knownMm
        val derived = CalibrationDerivation.derive(
            withInset, References.cardShortEdge, measuredPx, 0.0, aligned = true
        )

        assertEquals(3.5, derived.bezelInsetMm, 1e-9)
    }
}
