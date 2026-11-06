package com.blueorbit.ruler.feature.ruler

import androidx.compose.ui.unit.Density
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * M11R finding 8: Compose has no pixel `TextUnitType`, so a pixel-sized tick label has to be
 * converted with `with(density) { px.toSp() }`. The measurer multiplies the value back by density
 * *and* font scale, so the conversion cancels out and the glyph keeps its exact pixel size — which is
 * what keeps rule 1 true: the font-scale setting must change the label's size, not the ruler's length.
 * This pins that invariant without needing Robolectric.
 *
 * Named for `RulerPlate` (M11V) — the plate applies the same conversion to every tick label.
 */
class RulerPlateTextUnitTest {

    @Test
    fun aPixelSizedLabelKeepsItsPixelSizeAtEveryFontScale() {
        val labelSizePx = 24f
        for (fontScale in listOf(0.85f, 1.0f, 1.3f, 2.0f)) {
            val density = Density(density = 2.75f, fontScale = fontScale)
            val asSp = with(density) { labelSizePx.toSp() }
            val backToPx = with(density) { asSp.toPx() }
            assertEquals("fontScale $fontScale", labelSizePx, backToPx, 1e-3f)
        }
    }

    @Test
    fun theConversionActuallyScalesWithFontScale() {
        // The sp value must shrink as the font scale grows, otherwise it is not a real conversion.
        val small = with(Density(2.0f, 1.0f)) { 20f.toSp() }
        val large = with(Density(2.0f, 2.0f)) { 20f.toSp() }
        assertEquals(10f, small.value, 1e-3f)
        assertEquals(5f, large.value, 1e-3f)
    }
}
