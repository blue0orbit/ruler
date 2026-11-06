package com.blueorbit.ruler.core.geometry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RulerSceneTest {

    private val pxPerMm = PixelPerMm(16.0)

    @Test
    fun labelsReSpaceWhenTheLabelSizeChanges() {
        val small = RulerScene(pxPerMm, MeasurementUnit.MM, labelSizePx = 20.0)
        val large = RulerScene(pxPerMm, MeasurementUnit.MM, labelSizePx = 60.0)
        assertEquals(10.0, small.labelStepUnits(), 1e-9)
        assertEquals(50.0, large.labelStepUnits(), 1e-9)
        assertTrue(large.labelStepUnits() > small.labelStepUnits())
    }

    @Test
    fun labelSizeNeverChangesThePhysicalGeometry() {
        // Rule 1: labels scale separately from geometry. Tick positions must be identical.
        val small = RulerScene(pxPerMm, MeasurementUnit.MM, labelSizePx = 20.0)
        val large = RulerScene(pxPerMm, MeasurementUnit.MM, labelSizePx = 60.0)
        val a = small.build(0L, 1000.0)
        val b = large.build(0L, 1000.0)
        assertEquals(a.size, b.size)
        for (i in a.indices) {
            assertEquals(a[i].pixels, b[i].pixels, 1e-9)
        }
    }

    @Test
    fun centimetresAreTenMillimetresWide() {
        val cm = RulerScene(pxPerMm, MeasurementUnit.CM, labelSizePx = 20.0)
        assertEquals(160.0, cm.pixelsPerUnit(), 1e-9)
        assertEquals(1.0, cm.labelStepUnits(), 1e-9)
    }

    @Test
    fun aCentimetreScalePrintsMillimetresBetweenItsNumbers() {
        val cm = RulerScene(pxPerMm, MeasurementUnit.CM, labelSizePx = 20.0)
        val ticks = cm.build(0L, 160.0 * 2)
        // 0.1 cm apart: twenty millimetre ticks in two centimetres, plus the zero.
        assertEquals(21, ticks.size)
        assertEquals(1_000L, ticks[1].micrometres)
        // Millimetre quietest, half-centimetre louder, centimetre louder still.
        assertTrue(ticks[1].rank < ticks[5].rank)
        assertTrue(ticks[5].rank < ticks[10].rank)
        assertEquals(cm.rankOf(0.1), ticks[3].rank)
    }

    @Test
    fun oneInchIsPhysically25Point4Millimetres() {
        // Rule 2: 1 inch = 25.4 mm exactly, so on the 390 dpi test device an inch is 390 px.
        val truePxPerMm = 390.0 / 25.4
        val inch = RulerScene(PixelPerMm(truePxPerMm), MeasurementUnit.INCH, labelSizePx = 20.0)
        assertEquals(25.4, inch.unitSizeMm, 1e-9)
        assertEquals(390.0, inch.pixelsPerUnit(), 1e-9)
    }

    @Test
    fun inchLabelsShowReducedFractionsWhenThereIsRoom() {
        val inch = RulerScene(PixelPerMm(390.0 / 25.4), MeasurementUnit.INCH, labelSizePx = 20.0)
        assertEquals(0.5, inch.labelStepUnits(), 1e-9)
        val labels = inch.build(0L, 2000.0).mapNotNull { it.label }
        assertEquals(listOf("0", "1/2", "1", "1 1/2", "2"), labels.take(5))
    }

    @Test
    fun tickStepGrowsWhenPixelsPerMmIsTooSmallToDraw() {
        val coarse = RulerScene(PixelPerMm(3.0), MeasurementUnit.MM, labelSizePx = 20.0)
        assertEquals(2_000L, coarse.tickStepMicrometres()) // 1 mm would be 3 px, below the 6 px floor
    }

    @Test
    fun ticksStayInsideTheViewport() {
        val ticks = RulerScene(pxPerMm, MeasurementUnit.MM, 20.0).build(0L, 1000.0)
        assertTrue(ticks.isNotEmpty())
        for (tick in ticks) {
            assertTrue(tick.pixels <= 1000.0)
        }
        assertEquals(0.0, ticks.first().pixels, 1e-9)
        assertEquals("0", ticks.first().label)
    }

    @Test
    fun roundingIsHalfUp() {
        assertEquals(2.3, RulerScene.roundHalfUp(2.25, 1), 1e-9)
        assertEquals(2.4, RulerScene.roundHalfUp(2.4499, 1), 1e-9)
        assertEquals(0.1, RulerScene.roundHalfUp(0.05, 1), 1e-9)
    }
}
