package com.blueorbit.ruler.feature.ruler

import com.blueorbit.ruler.core.geometry.PixelPerMm
import org.junit.Assert.assertEquals
import org.junit.Test

/** M4 maths: the zero is the start line, and the bezel inset moves it. */
class StartEdgeTest {

    private val pxPerMm = PixelPerMm(390.0 / 25.4)

    @Test
    fun noInsetMeansZeroAtThePhysicalEdge() {
        assertEquals(0.0, StartEdge(0.0, pxPerMm).zeroPixels(), 1e-9)
    }

    @Test
    fun insetMovesTheStartLineByItsPhysicalLength() {
        assertEquals(38.385826771653543, StartEdge(2.5, pxPerMm).zeroPixels(), 1e-9)
    }

    @Test
    fun aReadingIsTakenFromTheStartLine() {
        val edge = StartEdge(2.5, pxPerMm)
        val endPx = edge.zeroPixels() + pxPerMm.mmToPixels(100.0)
        assertEquals(100_000L, edge.measureAt(endPx))
    }

    @Test
    fun aligningAgainstThePhysicalEdgeInsteadIsOffByTheInset() {
        val edge = StartEdge(2.5, pxPerMm)
        assertEquals(97_500L, edge.measureAt(pxPerMm.mmToPixels(100.0)))
    }

    @Test
    fun measurableSpanExcludesTheInset() {
        val edge = StartEdge(2.5, pxPerMm)
        assertEquals(97_500L, edge.measurableMicrometres(pxPerMm.mmToPixels(100.0)))
    }

    @Test
    fun aViewportSmallerThanTheInsetMeasuresNothing() {
        assertEquals(0L, StartEdge(2.5, pxPerMm).measurableMicrometres(10.0))
    }
}
