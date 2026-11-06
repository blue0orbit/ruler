package com.blueorbit.ruler.core.geometry

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The tick hierarchy (DESIGN.md §5.1) is what stops a centimetre tick looking like a 2 mm tick. It is
 * decided in `RulerScene`, not in the canvas, so the table is assertable without a UI.
 */
class RulerSceneRankTest {

    private val mm = PixelPerMm(15.3543)
    private fun scene(unit: MeasurementUnit, labelSizePx: Double = 38.0) =
        RulerScene(mm, unit, labelSizePx)

    @Test
    fun metricLadderRanksMatchTheTable() {
        val s = scene(MeasurementUnit.MM)
        assertEquals(0, s.rankOf(1.0))
        assertEquals(1, s.rankOf(2.0))
        assertEquals(2, s.rankOf(5.0))
        assertEquals(3, s.rankOf(10.0))
        assertEquals(3, s.rankOf(20.0))
        // A tick that is on the ladder but not on a larger step stays quiet.
        assertEquals(0, s.rankOf(3.0))
        assertEquals(0, s.rankOf(7.0))
    }

    @Test
    fun imperialLadderRanksMatchTheTable() {
        val s = scene(MeasurementUnit.INCH)
        assertEquals(0, s.rankOf(1.0 / 32))
        assertEquals(1, s.rankOf(1.0 / 16))
        assertEquals(2, s.rankOf(1.0 / 8))
        assertEquals(3, s.rankOf(1.0 / 4))
        assertEquals(3, s.rankOf(1.0 / 2))
        assertEquals(0, s.rankOf(3.0 / 32))
    }

    @Test
    fun rankIsCappedAtThreeSoALongStepIsNotTallerThanAPrintedStep() {
        val s = scene(MeasurementUnit.MM)
        assertEquals(RulerScene.MAX_RANK, s.rankOf(20.0))
        assertEquals(RulerScene.MAX_RANK, s.rankOf(50.0))
        assertEquals(RulerScene.MAX_RANK, s.rankOf(100.0))
        assertEquals(RulerScene.MAX_RANK, scene(MeasurementUnit.CM).rankOf(10.0))
    }

    @Test
    fun zeroIsThePrintedStepNotAQuietTick() {
        // The start of the ruler must read as the printed step, or the plate has no visible zero.
        assertEquals(RulerScene.MAX_RANK, scene(MeasurementUnit.MM).rankOf(0.0))
    }

    @Test
    fun rankDoesNotDependOnLabelSize() {
        // Rule 1: the label font is ink. A tick's rank is a property of its position, so zooming the
        // labels must not change which ticks look major.
        val positions = listOf(1.0, 2.0, 3.0, 5.0, 7.0, 10.0, 20.0)
        val reference = scene(MeasurementUnit.MM)
        for (labelSize in listOf(20.0, 90.0)) {
            val s = scene(MeasurementUnit.MM, labelSize)
            for (position in positions) {
                assertEquals(
                    "labelSize $labelSize at $position",
                    reference.rankOf(position),
                    s.rankOf(position)
                )
            }
        }
    }

    @Test
    fun theSameTickPositionGetsTheSameRankWhateverTheTickDensityIs() {
        // `minTickSpacingPx` decides which ticks exist; it must not decide how a tick that does exist
        // is weighted. A 10 mm tick is the printed step at every density.
        val sparse = RulerScene(mm, MeasurementUnit.MM, 38.0, minTickSpacingPx = 40.0)
        val dense = RulerScene(mm, MeasurementUnit.MM, 38.0, minTickSpacingPx = 2.0)
        for (position in listOf(1.0, 2.0, 5.0, 10.0, 20.0, 50.0)) {
            assertEquals("position $position", sparse.rankOf(position), dense.rankOf(position))
        }
    }

    @Test
    fun buildAssignsTheRankFromTheLadderNotFromTheLabel() {
        val s = scene(MeasurementUnit.MM)
        val ticks = s.build(0L, s.pixelsPerUnit() * 12)
        val rankByMillimetre = ticks.associate { it.micrometres / 1000L to it.rank }

        assertEquals(3, rankByMillimetre[0L])
        assertEquals(0, rankByMillimetre[1L])
        assertEquals(1, rankByMillimetre[2L])
        assertEquals(0, rankByMillimetre[3L])
        assertEquals(2, rankByMillimetre[5L])
        assertEquals(3, rankByMillimetre[10L])
    }

    @Test
    fun theHierarchySurvivesALabelStepThatSkipsTicks() {
        // A coarse label step drops most ticks, but the ones that remain keep their rank, so the
        // hierarchy is not an accident of which ticks happen to be labelled.
        val s = RulerScene(mm, MeasurementUnit.MM, 200.0)
        val ticks = s.build(0L, s.pixelsPerUnit() * 22)
        val ranks = ticks.map { it.micrometres / 1000L to it.rank }.toMap()

        assertEquals(3, ranks[0L])
        assertEquals(3, ranks[10L])
        assertEquals(3, ranks[20L])
    }
}
