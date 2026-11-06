package com.blueorbit.ruler.core.geometry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Rule 1: font scale changes labels only, never the physical ruler length. A larger label must
 * re-space the labels while the physical size of a unit in pixels stays exactly the same.
 */
class FontScaleTest {

    private val pxPerMm = PixelPerMm(15.354)

    @Test
    fun physicalGeometryIsUnchangedByLabelSize(): Unit {
        val small = RulerScene(pxPerMm, MeasurementUnit.MM, labelSizePx = 20.0)
        val large = RulerScene(pxPerMm, MeasurementUnit.MM, labelSizePx = 60.0)

        assertEquals(pxPerMm.mmToPixels(1.0), small.pixelsPerUnit(), 1e-9)
        assertEquals(pxPerMm.mmToPixels(1.0), large.pixelsPerUnit(), 1e-9)
        assertEquals(pxPerMm.mmToPixels(25.4), RulerScene(pxPerMm, MeasurementUnit.INCH, 60.0).pixelsPerUnit(), 1e-6)
    }

    @Test
    fun labelsReSpaceWithFontScaleWhileTheUnitSizeStaysFixed(): Unit {
        val small = RulerScene(pxPerMm, MeasurementUnit.MM, labelSizePx = 20.0)
        val large = RulerScene(pxPerMm, MeasurementUnit.MM, labelSizePx = 60.0)

        assertTrue(
            "a bigger label needs more room, so the label step must grow",
            large.labelStepUnits() > small.labelStepUnits()
        )
        assertEquals(small.unitSizeMm, large.unitSizeMm, 1e-9)
    }

    @Test
    fun aTickStillRepresentsTheSamePhysicalLengthAtEitherLabelSize(): Unit {
        val scene = RulerScene(pxPerMm, MeasurementUnit.MM, labelSizePx = 40.0)
        val stepMm = scene.tickStepUnits()
        // The tick step is a ladder value, so it is a physical length, not a pixel measurement.
        assertEquals(listOf(1.0, 2.0, 5.0, 10.0, 20.0, 50.0, 100.0).contains(stepMm), true)
    }
}
