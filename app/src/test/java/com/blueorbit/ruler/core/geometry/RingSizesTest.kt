package com.blueorbit.ruler.core.geometry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The ring-size ladders are data transcribed from published charts (see the citations on `RingSizes`),
 * so what a test can prove is that the transcription is right and that the matching rule behaves the
 * way the roadmap states it does. A wrong rung here is a wrong ring ordered from a jeweller.
 */
class RingSizesTest {

    /** The inner diameter one system publishes for one label, read straight off the transcribed ladder. */
    private fun rung(system: RingSizeSystem, label: String): RingSize =
        RingSizes.ladder(system).firstOrNull { it.label == label }
            ?: throw AssertionError("no rung labelled \"$label\" in $system")

    // ---- The chart's own anchor rows -------------------------------------------------------

    @Test
    fun theUsLadderSitsOnTheChartRows() {
        assertEquals(11.63, rung(RingSizeSystem.US, "0").innerDiameterMm, 0.02)
        assertEquals(12.04, rung(RingSizeSystem.US, "0½").innerDiameterMm, 0.02)
        assertEquals(12.45, rung(RingSizeSystem.US, "1").innerDiameterMm, 0.02)
        assertEquals(20.17, rung(RingSizeSystem.US, "10½").innerDiameterMm, 0.05)
        assertEquals(21.39, rung(RingSizeSystem.US, "12").innerDiameterMm, 0.05)
        assertEquals(24.63, rung(RingSizeSystem.US, "16").innerDiameterMm, 0.02)
    }

    @Test
    fun theUkLadderSitsOnTheChartRows() {
        assertEquals(11.9, rung(RingSizeSystem.UK, "A").innerDiameterMm, 0.02)
        assertEquals(12.1, rung(RingSizeSystem.UK, "A½").innerDiameterMm, 0.02)
        assertEquals(20.3, rung(RingSizeSystem.UK, "V").innerDiameterMm, 0.02)
        assertEquals(21.9, rung(RingSizeSystem.UK, "Z").innerDiameterMm, 0.02)
        assertEquals(22.1, rung(RingSizeSystem.UK, "Z½").innerDiameterMm, 0.02)
        assertEquals(22.3, rung(RingSizeSystem.UK, "Z+1").innerDiameterMm, 0.02)
        assertEquals(23.9, rung(RingSizeSystem.UK, "Z+5").innerDiameterMm, 0.02)
        assertEquals(25.5, rung(RingSizeSystem.UK, "Z+9").innerDiameterMm, 0.02)
    }

    @Test
    fun theJapaneseLadderIsThePublishedJisLadder() {
        assertEquals(13.00, rung(RingSizeSystem.JP, "1").innerDiameterMm, 0.02)
        assertEquals(13.33, rung(RingSizeSystem.JP, "2").innerDiameterMm, 0.02)
        assertEquals(13.67, rung(RingSizeSystem.JP, "3").innerDiameterMm, 0.02)
        assertEquals(21.00, rung(RingSizeSystem.JP, "25").innerDiameterMm, 0.02)
        assertEquals(23.00, rung(RingSizeSystem.JP, "31").innerDiameterMm, 0.02)
    }

    @Test
    fun theEuSizeIsTheInnerCircumferenceInWholeMillimetres() {
        // ISO 8653:2016: size 54 means a 54 mm inner circumference, i.e. 54/π mm of inner diameter.
        assertEquals(54.0 / Math.PI, rung(RingSizeSystem.EU, "54").innerDiameterMm, 0.02)
        assertEquals("54", RingSizes.match(RingSizeSystem.EU, 54.0 / Math.PI).nearest.label)
    }

    // ---- The cross-checks the roadmap names as the acceptance tests -------------------------

    @Test
    fun eu54AndUsSevenAreTheSameRingAtAbout17Point3Millimetres() {
        val us7 = rung(RingSizeSystem.US, "7").innerDiameterMm
        assertEquals(17.3, us7, 0.1)
        assertEquals("7", RingSizes.match(RingSizeSystem.US, us7).nearest.label)
        assertEquals("54", RingSizes.match(RingSizeSystem.EU, us7).nearest.label)
        assertEquals("N½", RingSizes.match(RingSizeSystem.UK, us7).nearest.label)
        assertEquals("14", RingSizes.match(RingSizeSystem.JP, us7).nearest.label)
        assertEquals(54.4, RingSizes.circumferenceFromDiameter(us7), 0.3)
    }

    @Test
    fun eu52AndUsSixAreTheSameRingAtAbout16Point5Millimetres() {
        val us6 = rung(RingSizeSystem.US, "6").innerDiameterMm
        assertEquals(16.5, us6, 0.1)
        assertEquals("6", RingSizes.match(RingSizeSystem.US, us6).nearest.label)
        assertEquals("52", RingSizes.match(RingSizeSystem.EU, us6).nearest.label)
        assertEquals("L½", RingSizes.match(RingSizeSystem.UK, us6).nearest.label)
        assertEquals("12", RingSizes.match(RingSizeSystem.JP, us6).nearest.label)
    }

    @Test
    fun aRingMeasuredByItsCircumferenceGivesTheSameSizesAsOneMeasuredByItsDiameter() {
        val diameter = RingSizes.diameterFromCircumference(54.4)
        assertEquals(17.32, diameter, 0.05)
        assertEquals(
            RingSizes.match(RingSizeSystem.US, 17.32).nearest.label,
            RingSizes.match(RingSizeSystem.US, diameter).nearest.label
        )
    }

    // ---- Rule 5: the reading carries how many sizes it can be off by -------------------------

    @Test
    fun oneSizeOfEachSystemConvertsToItsOwnPublishedStepInMillimetres() {
        assertEquals(1.0, RingSizes.sizesOfUncertainty(RingSizeSystem.EU, 1.0 / Math.PI), 0.001)
        assertEquals(1.0, RingSizes.sizesOfUncertainty(RingSizeSystem.US, 0.8128), 0.001)
        assertEquals(1.0, RingSizes.sizesOfUncertainty(RingSizeSystem.UK, 0.4), 0.001)
        assertEquals(1.0, RingSizes.sizesOfUncertainty(RingSizeSystem.JP, 1.0 / 3.0), 0.001)
    }

    /**
     * The number M21's accuracy gate exists for. The uncalibrated ruler states "usually within 5 mm
     * over 10 cm"; on a 17.3 mm ring that is 0.87 mm of diameter, and one EU size is 0.318 mm — so an
     * uncalibrated ring reading is about three EU sizes wide. Sizes are therefore shown as a range
     * until the calibration is verified.
     */
    @Test
    fun anUncalibratedReadingOfA17mmRingIsWorthAboutThreeEuSizes() {
        val toleranceMm = 17.32 * 5.0 / 100.0
        assertEquals(0.87, toleranceMm, 0.01)
        val sizes = RingSizes.sizesOfUncertainty(RingSizeSystem.EU, toleranceMm)
        assertTrue("expected about 3 EU sizes, was $sizes", sizes in 2.5..3.5)
    }

    @Test
    fun aCardCalibrationIsWorthUnderOneFifthOfAnEuSizeOnTheSameRing() {
        // The card reference carries 0.10 mm of tolerance over the 53.98 mm edge it is measured on:
        // 0.19 mm per 100 mm, which on a 17.3 mm ring is 0.03 mm of diameter.
        val per100mm = 0.10 / 53.98 * 100.0
        val sizes = RingSizes.sizesOfUncertainty(RingSizeSystem.EU, 17.32 * per100mm / 100.0)
        assertTrue("expected well under 0.2 EU sizes, was $sizes", sizes < 0.2)
    }

    @Test
    fun theRangeShownBeforeCalibrationNamesTwoSizesRatherThanOne() {
        val toleranceMm = 17.32 * 5.0 / 100.0
        val (low, high) = RingSizes.sizeRange(RingSizeSystem.EU, 17.32, toleranceMm)
        assertNotEquals(low, high)
        assertTrue("low must be the smaller size", low.toInt() < high.toInt())
        // 54.4 mm of circumference ± 2.7 mm.
        assertEquals("52", low)
        assertEquals("57", high)
    }

    @Test
    fun aReadingExactlyOnARungNamesOneSizeRatherThanTwo() {
        val (low, high) = RingSizes.sizeRange(RingSizeSystem.EU, 54.0 / Math.PI, 0.0)
        assertEquals(low, high)
        assertEquals("54", low)
    }

    // ---- Nearest size, and "between X and Y" -------------------------------------------------

    @Test
    fun aReadingCloseToOneRungIsNamedByThatRung() {
        val match = RingSizes.match(RingSizeSystem.US, 17.32)
        assertEquals("7", match.nearest.label)
        assertFalse("a US 7 ring must not be reported as between two sizes", match.isBetween)
        assertTrue("expected under 0.05 rungs away, was ${match.rungsAway}", match.rungsAway < 0.05)
    }

    @Test
    fun aReadingInTheMiddleOfAGapNamesTheTwoSizesItFallsBetween() {
        // US 7 is 17.32 and US 7½ is 17.73; 17.60 is a third of a size above the nearer of the two,
        // so it is not fairly called either one.
        val match = RingSizes.match(RingSizeSystem.US, 17.60)
        assertTrue(
            "expected a between reading, was ${match.rungsAway} rungs from ${match.nearest.label}",
            match.isBetween
        )
        assertEquals(setOf("7", "7½"), setOf(match.nearest.label, match.neighbour!!.label))
    }

    @Test
    fun theNeighbourIsAlwaysOnTheSideTheMeasurementLeans() {
        // 17.20 and 17.45 both sit nearest US 7 (17.32), one from below and one from above.
        val small = RingSizes.match(RingSizeSystem.US, 17.20)
        val large = RingSizes.match(RingSizeSystem.US, 17.45)
        assertEquals("7", small.nearest.label)
        assertEquals("6½", small.neighbour?.label)
        assertTrue("a reading under the rung is signed negative", small.rungsAway < 0)
        assertEquals("7", large.nearest.label)
        assertEquals("7½", large.neighbour?.label)
        assertTrue("a reading over the rung is signed positive", large.rungsAway > 0)
    }

    @Test
    fun everyMatchIsTheNearestRungNotTheFirstOneAbove() {
        assertEquals("7", RingSizes.match(RingSizeSystem.US, 17.27).nearest.label)
        assertEquals("7", RingSizes.match(RingSizeSystem.US, 17.37).nearest.label)
    }

    // ---- Off the end of a ladder -------------------------------------------------------------

    @Test
    fun aCircleSmallerThanTheSmallestSizeSaysSoInsteadOfNamingARung() {
        val match = RingSizes.match(RingSizeSystem.US, 9.0)
        assertTrue(match.belowScale)
        assertTrue(match.offScale)
        assertEquals("0", match.nearest.label)
    }

    @Test
    fun aCircleLargerThanTheLargestSizeSaysSoAndHasNoNeighbourToName() {
        val match = RingSizes.match(RingSizeSystem.UK, 30.0)
        assertTrue(match.aboveScale)
        assertEquals("Z+9", match.nearest.label)
        assertNull(match.neighbour)
    }

    @Test
    fun aZeroDiameterNeverCrashesAndNeverClaimsASize() {
        val match = RingSizes.match(RingSizeSystem.EU, 0.0)
        assertTrue(match.offScale)
        assertEquals(0.0, RingSizes.sizesOfUncertainty(RingSizeSystem.EU, 0.0), 1e-9)
    }

    // ---- The ladders themselves --------------------------------------------------------------

    @Test
    fun circumferenceAndDiameterAreTwoFormsOfTheSameCircle() {
        val diameter = 17.32
        val back = RingSizes.diameterFromCircumference(RingSizes.circumferenceFromDiameter(diameter))
        assertEquals(diameter, back, 1e-9)
    }

    @Test
    fun everyLadderStepsByItsOwnPublishedRung() {
        for (system in RingSizeSystem.entries) {
            val ladder = RingSizes.ladder(system)
            assertEquals(
                "$system: a rung must be its published step",
                system.rungStepMm,
                ladder[1].innerDiameterMm - ladder[0].innerDiameterMm,
                0.001
            )
            assertEquals(
                "$system: rungs must be whole sizes",
                0.0,
                system.stepMmPerSize % system.rungStepMm,
                0.001
            )
        }
    }

    @Test
    fun everyLadderAscendsAndNamesEachSizeOnce() {
        for (system in RingSizeSystem.entries) {
            val ladder = RingSizes.ladder(system)
            val labels = HashSet<String>()
            for (index in ladder.indices) {
                assertTrue("$system labels ${ladder[index].label} twice", labels.add(ladder[index].label))
                if (index > 0) {
                    assertTrue(
                        "$system is not ascending at ${ladder[index].label}",
                        ladder[index].innerDiameterMm > ladder[index - 1].innerDiameterMm
                    )
                }
            }
        }
    }

    @Test
    fun aSizeRangeAtTheEndOfALadderNeverRunsOffIt() {
        val (low, high) = RingSizes.sizeRange(RingSizeSystem.US, 11.63, 5.0)
        assertEquals("0", low)
        assertTrue("high must still be a US size", RingSizes.ladder(RingSizeSystem.US).any { it.label == high })
    }
}
