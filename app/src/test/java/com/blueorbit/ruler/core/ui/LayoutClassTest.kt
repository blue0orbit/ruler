package com.blueorbit.ruler.core.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The breakpoints (DESIGN.md §6) are plain integers on purpose: the bar/rail switch and the pane count
 * are the two decisions that change the whole layout, so the boundaries are asserted rather than left
 * to a density-dependent library. The unfolded Pixel 9 Pro Fold is 851 dp wide, which is why the rail
 * boundary is 840 and not 600.
 */
class LayoutClassTest {

    @Test
    fun theMaterial3BoundariesAreTheDocumentedOnes() {
        assertEquals(LayoutClass.COMPACT, layoutClassFor(320))
        assertEquals(LayoutClass.COMPACT, layoutClassFor(599))
        assertEquals(LayoutClass.MEDIUM, layoutClassFor(600))
        assertEquals(LayoutClass.MEDIUM, layoutClassFor(839))
        assertEquals(LayoutClass.EXPANDED, layoutClassFor(840))
        assertEquals(LayoutClass.EXPANDED, layoutClassFor(1280))
    }

    @Test
    fun theBarBecomesARailOnlyAtTheExpandedWidth() {
        assertFalse(useNavigationRail(599))
        assertFalse(useNavigationRail(839))
        assertTrue(useNavigationRail(840))
    }

    @Test
    fun onePaneBelow840AndTwoAt840() {
        assertEquals(1, paneCount(411))
        assertEquals(1, paneCount(600))
        assertEquals(1, paneCount(839))
        assertEquals(2, paneCount(840))
        assertEquals(2, paneCount(1024))
    }

    @Test
    fun theRailWidthAndThePaneCountAgree() {
        for (width in listOf(320, 599, 600, 839, 840, 1280)) {
            assertEquals("width $width", useNavigationRail(width), paneCount(width) == 2)
        }
    }

    @Test
    fun theScreenMarginGrowsOutsideTheCompactClass() {
        assertEquals(16, screenMarginDp(320))
        assertEquals(16, screenMarginDp(599))
        assertEquals(24, screenMarginDp(600))
        assertEquals(24, screenMarginDp(840))
    }

    @Test
    fun theFoldedAndUnfoldedFoldLandOnOppositeSidesOfTheBoundary() {
        // Recorded from the test device: folded outer panel 1080 px at 390 dpi = 443 dp; unfolded
        // 2076 px at 390 dpi = 851 dp. The bar must serve the first and the rail the second.
        val foldedDp = (1080 * 160 / 390)
        val unfoldedDp = (2076 * 160 / 390)

        assertEquals(LayoutClass.COMPACT, layoutClassFor(foldedDp))
        assertFalse(useNavigationRail(foldedDp))
        assertEquals(LayoutClass.EXPANDED, layoutClassFor(unfoldedDp))
        assertTrue(useNavigationRail(unfoldedDp))
    }

    @Test
    fun theBreakpointsAreNotOffByOne() {
        assertEquals(MEDIUM_WIDTH_DP, 600)
        assertEquals(EXPANDED_WIDTH_DP, 840)
        assertEquals(LayoutClass.COMPACT, layoutClassFor(MEDIUM_WIDTH_DP - 1))
        assertEquals(LayoutClass.MEDIUM, layoutClassFor(MEDIUM_WIDTH_DP))
        assertEquals(LayoutClass.MEDIUM, layoutClassFor(EXPANDED_WIDTH_DP - 1))
        assertEquals(LayoutClass.EXPANDED, layoutClassFor(EXPANDED_WIDTH_DP))
    }
}
