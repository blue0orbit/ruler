package com.blueorbit.ruler.core.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * `blend` (DESIGN.md §2.1) is the only way a derived colour is produced, so its endpoints, its
 * midpoint and its clamping are the whole guarantee that a derived role sits between two colours that
 * were already checked against the contrast rules.
 */
class BlendTest {

    private val black = Rgb(0, 0, 0)
    private val white = Rgb(255, 255, 255)

    @Test
    fun ratioZeroReturnsTheStartColourUnchanged() {
        assertEquals(black, blend(black, white, 0.0))
        assertEquals(white, blend(white, black, 0.0))
    }

    @Test
    fun ratioOneReturnsTheTargetColourUnchanged() {
        assertEquals(white, blend(black, white, 1.0))
        assertEquals(black, blend(white, black, 1.0))
    }

    @Test
    fun ratioOneHalfReturnsTheMidpoint() {
        assertEquals(Rgb(128, 128, 128), blend(black, white, 0.5))
    }

    @Test
    fun everyChannelMovesByItsOwnDistance() {
        // A per-channel lerp, not a luminance lerp: each channel stays between the two colours it was
        // derived from, which is what keeps a derived surface the same hue as its palette.
        assertEquals(Rgb(33, 24, 27), blend(Rgb(20, 24, 29), Rgb(71, 24, 22), 0.25))
    }

    @Test
    fun outOfRangeRatiosAreClampedNotExtrapolated() {
        // Extrapolating would produce a colour that was never checked against the contrast rules.
        // Below zero the blend stops at `from`; above one it stops at `toward`.
        assertEquals(black, blend(black, white, -0.5))
        assertEquals(white, blend(black, white, 1.5))
        assertEquals(Rgb(26, 29, 33), blend(Rgb(247, 248, 250), Rgb(26, 29, 33), 999.0))
    }

    @Test
    fun blendingAColourWithItselfIsIdentity() {
        val ink = Rgb(26, 29, 33)
        assertEquals(ink, blend(ink, ink, 0.37))
    }

    @Test
    fun roundingIsRoundHalfUpSoTheDocumentedTableIsReproducible() {
        // 44 + (222 - 44) * 0.25 = 88.5 -> 89, matching Java's Math.round (half up).
        assertEquals(89, blend(Rgb(44, 44, 44), Rgb(222, 222, 222), 0.25).r)
    }
}
