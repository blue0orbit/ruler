package com.blueorbit.ruler.core.calibration

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReferencesTest {

    @Test
    fun cardEdgesMatchTheID1Spec() {
        assertEquals(85.60, References.cardLongEdge.knownMm, 1e-9)
        assertEquals(53.98, References.cardShortEdge.knownMm, 1e-9)
    }

    @Test
    fun errorBudgetIsNormalisedPer100Mm() {
        // 0.10 mm over a 53.98 mm edge = 0.185 mm per 100 mm.
        assertEquals(0.185, References.cardShortEdge.errorBudgetMmPer100Mm(), 0.001)
        assertEquals(0.5, References.manual.errorBudgetMmPer100Mm(), 1e-9)
    }

    @Test
    fun measuredSpanDividesByTheKnownSize() {
        // A 53.98 mm edge spanning exactly twice its size in px -> 2 px/mm.
        assertEquals(2.0, References.cardShortEdge.pixelsPerMm(107.96), 1e-9)
    }

    @Test
    fun everyReferenceHasAPositiveSizeAndTolerance() {
        val all = listOf(References.cardLongEdge, References.cardShortEdge, References.manual) + References.coins
        all.forEach {
            assertTrue("${it.id} knownMm", it.knownMm > 0.0)
            assertTrue("${it.id} toleranceMm", it.toleranceMm > 0.0)
            assertTrue("${it.id} budget", it.errorBudgetMmPer100Mm() > 0.0)
        }
    }

    @Test
    fun coinIdsAreUnique() {
        assertEquals(References.coins.size, References.coins.map { it.id }.distinct().size)
    }

    /** M16: the drawing the user lines up has to be the shape of the real object, at the right scale. */
    @Test
    fun cardOutlinesKeepTheID1Proportions(): Unit {
        assertEquals(ReferenceShape.RECTANGLE, References.cardLongEdge.shape)
        assertEquals(ReferenceShape.RECTANGLE, References.cardShortEdge.shape)

        // A long edge drawn as 856 px is 85.6 mm, so the same scale makes the card 539.8 mm-deep px.
        assertEquals(539.8, References.cardLongEdge.outlineDepthPx(856.0), 0.1)
        // Matching the short edge instead: 539.8 px wide is the same card standing upright, 856 px deep.
        assertEquals(856.0, References.cardShortEdge.outlineDepthPx(539.8), 0.1)
    }

    @Test
    fun coinsAreDrawnAsCirclesAndCardsAsRectangles(): Unit {
        References.coins.forEach {
            assertEquals("${it.id} shape", ReferenceShape.CIRCLE, it.shape)
            assertEquals("${it.id} aspect", 1.0, it.aspectRatio, 1e-9)
        }
        assertEquals(ReferenceShape.BAR, References.manual.shape)
    }

    /**
     * M16 (rule 14): the guided path must not open by telling the user their card is too long. On the
     * test device a card's long edge is 1 314 px in a 1 041-px column, so the wizard has to start with
     * the short edge there and with the long edge on a screen wide enough for it.
     */
    @Test
    fun theGuidedPathStartsWithTheCardEdgeThatFitsTheScreen(): Unit {
        assertEquals(References.cardShortEdge.id, References.bestCardEdgeFor(15.35, 1041.0).id)
        assertEquals(References.cardLongEdge.id, References.bestCardEdgeFor(10.0, 1041.0).id)
        // A screen with no width yet is not a reason to pick the reference that cannot fit it.
        assertEquals(References.cardShortEdge.id, References.bestCardEdgeFor(0.0, 1041.0).id)
    }

    /**
     * The manual path asks the user to measure the drawing, so the drawing has to fit. With the span
     * fixed at 100 mm it was 1 535 px on a 1 080-px panel and the wizard offered nothing to measure —
     * the manual path could not be completed on any phone.
     */
    @Test
    fun theManualSpanIsAlwaysASizeTheScreenCanDraw(): Unit {
        assertEquals(60.0, References.manualSpanFor(15.35, 1041.0).knownMm, 1e-9)
        assertEquals(100.0, References.manualSpanFor(10.0, 1041.0).knownMm, 1e-9)
        // A span still has to be offered on a screen too small for any of them: the smallest one, which
        // is the case the "too long" sentence then explains.
        assertEquals(25.0, References.manualSpanFor(15.35, 300.0).knownMm, 1e-9)
    }

    @Test
    fun aFittedManualSpanCarriesItsOwnErrorBudget(): Unit {
        val fitted = References.manualSpanFor(15.35, 1041.0)
        // The steel-ruler tolerance is 0.5 mm over the span actually drawn, so a shorter span is a
        // proportionally worse reading and the budget has to say so.
        assertEquals(0.5 / 60.0 * 100.0, fitted.errorBudgetMmPer100Mm(), 1e-9)
        assertEquals(0.5, References.manual.errorBudgetMmPer100Mm(), 1e-9)
    }
}
