package com.blueorbit.ruler.core.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The colour layer (DESIGN.md §2) is the part of the redesign that a user cannot un-see, so it is
 * asserted rather than eyeballed: every derived role has to clear its own WCAG bar on every palette
 * the app can actually render, and the derived values have to be the ones §2.2 lists.
 */
class RulerColorsTest {

    private val allPalettes = listOf(
        ColorSafePalette.LIGHT,
        ColorSafePalette.DARK
    ) + ColorSafePalette.CUSTOM_THEMES

    @Test
    fun everyDerivedRoleClearsItsOwnContrastBarInEveryPalette() {
        for (palette in allPalettes) {
            val colors = palette.toRulerColors()
            for (check in rulerContrastChecks(colors)) {
                assertTrue(
                    "${palette.name}: ${check.role} is ${check.ratio.fmt()} against a minimum of " +
                        "${check.minRatio}",
                    check.passed
                )
            }
        }
    }

    @Test
    fun theDerivedValuesMatchTheDocumentedTable() {
        // DESIGN.md §2.2. These are the values the screens paint, so a changed blend ratio is a
        // visible design change and must fail a test rather than arrive quietly.
        val expected = mapOf(
            "light" to Values(
                surface = Rgb(0xEE, 0xEF, 0xF1),
                surfaceElevated = Rgb(0xE5, 0xE6, 0xE9),
                surfaceVariant = Rgb(0xD8, 0xD9, 0xDC),
                onSurfaceSecondary = Rgb(0x51, 0x54, 0x57),
                outline = Rgb(0x72, 0x75, 0x78),
                tickMinor = Rgb(0x46, 0x49, 0x4C),
                outlineVariant = Rgb(0x9F, 0xA0, 0xA3)
            ),
            // M19: the dark substrate and ink are the concept's Z-0 floor and on-surface values
            // (design-concept/design-system.md), so every derived rung moved with them.
            "dark" to Values(
                surface = Rgb(0x19, 0x1C, 0x1F),
                surfaceElevated = Rgb(0x22, 0x24, 0x28),
                surfaceVariant = Rgb(0x2E, 0x31, 0x34),
                onSurfaceSecondary = Rgb(0xAD, 0xAF, 0xB3),
                outline = Rgb(0x8E, 0x90, 0x94),
                tickMinor = Rgb(0xB7, 0xB9, 0xBD),
                outlineVariant = Rgb(0x64, 0x66, 0x6A)
            ),
            "high-contrast" to Values(
                surface = Rgb(0x0A, 0x0A, 0x0A),
                surfaceElevated = Rgb(0x14, 0x14, 0x14),
                surfaceVariant = Rgb(0x24, 0x24, 0x24),
                onSurfaceSecondary = Rgb(0xBF, 0xBF, 0xBF),
                outline = Rgb(0x99, 0x99, 0x99),
                tickMinor = Rgb(0xCC, 0xCC, 0xCC),
                outlineVariant = Rgb(0x66, 0x66, 0x66)
            ),
            "sepia" to Values(
                surface = Rgb(0xED, 0xE5, 0xD7),
                surfaceElevated = Rgb(0xE5, 0xDD, 0xD0),
                surfaceVariant = Rgb(0xDA, 0xD2, 0xC5),
                onSurfaceSecondary = Rgb(0x6A, 0x62, 0x59),
                outline = Rgb(0x86, 0x7E, 0x73),
                tickMinor = Rgb(0x61, 0x59, 0x50),
                outlineVariant = Rgb(0xAA, 0xA2, 0x97)
            )
        )

        for (palette in allPalettes) {
            val colors = palette.toRulerColors()
            val want = expected.getValue(palette.name)
            assertEquals("${palette.name} surface", want.surface, colors.surface)
            assertEquals("${palette.name} surfaceElevated", want.surfaceElevated, colors.surfaceElevated)
            assertEquals("${palette.name} surfaceVariant", want.surfaceVariant, colors.surfaceVariant)
            assertEquals("${palette.name} onSurfaceSecondary", want.onSurfaceSecondary, colors.onSurfaceSecondary)
            assertEquals("${palette.name} outline", want.outline, colors.outline)
            assertEquals("${palette.name} tickMinor", want.tickMinor, colors.tickMinor)
            assertEquals("${palette.name} outlineVariant", want.outlineVariant, colors.outlineVariant)
        }
    }

    @Test
    fun theAuthoredAccentsAreTheBrassFamilyOfTheConcept(): Unit {
        // M19 (design-concept/design-system.md): the accent is brass on every palette. This pins the
        // authored values so the accent cannot drift back to the old blue without a test failure.
        assertEquals(Rgb(122, 92, 18), ColorSafePalette.LIGHT.accent)
        assertEquals(Rgb(242, 202, 80), ColorSafePalette.DARK.accent)
        assertEquals(Rgb(255, 214, 10), ColorSafePalette.CUSTOM_THEMES[0].accent)
        assertEquals(Rgb(120, 86, 46), ColorSafePalette.CUSTOM_THEMES[1].accent)
    }

    @Test
    fun theInkRolesAreTheAuthoredInk() {
        // §2.1: onBackground, onSurface and tickMajor are `text`, not a blend. A tick label is the
        // measurement, so it is never quieted by a derived ratio.
        for (palette in allPalettes) {
            val colors = palette.toRulerColors()
            assertEquals(palette.name, palette.text, colors.onBackground)
            assertEquals(palette.name, palette.text, colors.onSurface)
            assertEquals(palette.name, palette.text, colors.tickMajor)
        }
    }

    @Test
    fun theAuthoredStateAndAccentColoursAreNeverRewritten() {
        // The colour-blind rule is defined on the palette's own state pair. If the derived layer
        // touched them, `stateIsColourBlindSafe` would be proving a different pair from the drawn one.
        for (palette in allPalettes) {
            val colors = palette.toRulerColors()
            assertEquals(palette.name, palette.accent, colors.accent)
            assertEquals(palette.name, palette.stateA, colors.stateA)
            assertEquals(palette.name, palette.stateB, colors.stateB)
            assertEquals(palette.name, palette.danger, colors.danger)
            assertEquals(palette.name, palette.isDark, colors.isDark)
            assertEquals(palette.name, colors.name)
        }
    }

    @Test
    fun theColourBlindRuleStillHoldsForEveryPaletteTheColourLayerCanBuild() {
        for (palette in allPalettes) {
            assertTrue(palette.name, ColorSafePalette.stateIsColourBlindSafe(palette))
        }
    }

    @Test
    fun surfacesGetProgressivelyCloserToTheInkAndQuietInksGetProgressivelyCloserToTheBackground() {
        for (palette in allPalettes) {
            val colors = palette.toRulerColors()
            val surface = ColorSafePalette.contrastRatio(colors.surface, palette.background)
            val elevated = ColorSafePalette.contrastRatio(colors.surfaceElevated, palette.background)
            val variant = ColorSafePalette.contrastRatio(colors.surfaceVariant, palette.background)
            assertTrue("${palette.name} surface order", surface < elevated && elevated < variant)

            val tickMinor = ColorSafePalette.contrastRatio(palette.background, colors.tickMinor)
            val secondary = ColorSafePalette.contrastRatio(palette.background, colors.onSurfaceSecondary)
            val outline = ColorSafePalette.contrastRatio(palette.background, colors.outline)
            assertTrue("${palette.name} ink order", tickMinor > secondary && secondary > outline)
        }
    }

    @Test
    fun everyDerivedColourStaysBetweenTheTwoColoursItWasDerivedFrom() {
        // A derived role can never be brighter or darker than both of its sources, which is the
        // structural reason the derived roles need no separate contrast proof.
        for (palette in allPalettes) {
            val colors = palette.toRulerColors()
            val backgroundL = palette.background.luminance()
            val textL = palette.text.luminance()
            val lower = minOf(backgroundL, textL)
            val upper = maxOf(backgroundL, textL)

            val derived = listOf(
                colors.surface, colors.surfaceElevated, colors.surfaceVariant,
                colors.onSurfaceSecondary, colors.outline, colors.outlineVariant, colors.tickMinor
            )
            for (colour in derived) {
                val luminance = colour.luminance()
                assertTrue(
                    "${palette.name}: ${colour.hex()} is outside the ${lower.fmt()}..${upper.fmt()} band",
                    luminance >= lower - 1e-9 && luminance <= upper + 1e-9
                )
            }
        }
    }

    @Test
    fun theNormativeRatiosAreTheFormulaNotATranscribedTable() {
        // DESIGN.md §2.1 is the decision; §2.2 is a review aid. The first draft of §2.2 listed
        // `surfaceVariant` at 0.10 and mis-rounded two channels of the dark row. The code follows the
        // formula, §2.2 was corrected to match it at M11V, and this test is what stops the corrected
        // table being re-derived wrongly a third time.
        for (palette in allPalettes) {
            val colors = palette.toRulerColors()
            assertEquals("${palette.name} surface", blend(palette.background, palette.text, 0.04), colors.surface)
            assertEquals("${palette.name} surfaceElevated", blend(palette.background, palette.text, 0.08), colors.surfaceElevated)
            assertEquals("${palette.name} surfaceVariant", blend(palette.background, palette.text, 0.14), colors.surfaceVariant)
            assertEquals("${palette.name} onSurfaceSecondary", blend(palette.text, palette.background, 0.25), colors.onSurfaceSecondary)
            assertEquals("${palette.name} outline", blend(palette.text, palette.background, 0.40), colors.outline)
            assertEquals("${palette.name} outlineVariant", blend(palette.text, palette.background, 0.60), colors.outlineVariant)
            assertEquals("${palette.name} tickMinor", blend(palette.text, palette.background, 0.20), colors.tickMinor)
        }
    }

    private data class Values(
        val surface: Rgb,
        val surfaceElevated: Rgb,
        val surfaceVariant: Rgb,
        val onSurfaceSecondary: Rgb,
        val outline: Rgb,
        val tickMinor: Rgb,
        val outlineVariant: Rgb
    )

    private fun Double.fmt(): String = String.format(java.util.Locale.US, "%.2f", this)

    private fun Rgb.hex(): String = String.format(java.util.Locale.US, "#%02X%02X%02X", r, g, b)
}
