package com.blueorbit.ruler.core.ui

import com.blueorbit.ruler.core.entitlement.EntitlementRepository
import com.blueorbit.ruler.core.entitlement.EntitlementRule
import com.blueorbit.ruler.core.entitlement.EntitlementState
import com.blueorbit.ruler.core.entitlement.EntitlementStatus
import com.blueorbit.ruler.core.entitlement.ProFeature
import com.blueorbit.ruler.core.settings.AppSettings
import com.blueorbit.ruler.core.settings.ThemeChoice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertTrue
import org.junit.Test

/** PLAN M9 acceptance: light and dark both pass contrast checks, and the free palette stays
 * colour-blind safe. The rule is checked against a known red/green pair so the test is not
 * satisfied by the palette alone. */
class ColorSafePaletteTest {

    private class FakeEntitlement(private val pro: Boolean) : EntitlementRepository {
        override val state: StateFlow<EntitlementState> = MutableStateFlow(
            EntitlementState(if (pro) EntitlementStatus.UNLOCKED else EntitlementStatus.LOCKED, EntitlementRule.PRODUCT_ID)
        )

        override fun has(feature: ProFeature): Boolean = pro
    }

    @Test
    fun lightPalettePassesTextAndUiContrast(): Unit {
        assertTrue(
            "light text on background: ${ColorSafePalette.contrastRatio(ColorSafePalette.LIGHT.text, ColorSafePalette.LIGHT.background)}",
            ColorSafePalette.contrastRatio(ColorSafePalette.LIGHT.text, ColorSafePalette.LIGHT.background) >= ColorSafePalette.MIN_TEXT_CONTRAST
        )
        assertTrue(
            "light accent on background: ${ColorSafePalette.contrastRatio(ColorSafePalette.LIGHT.accent, ColorSafePalette.LIGHT.background)}",
            ColorSafePalette.contrastRatio(ColorSafePalette.LIGHT.accent, ColorSafePalette.LIGHT.background) >= ColorSafePalette.MIN_UI_CONTRAST
        )
    }

    @Test
    fun darkPalettePassesTextAndUiContrast(): Unit {
        assertTrue(
            "dark text on background: ${ColorSafePalette.contrastRatio(ColorSafePalette.DARK.text, ColorSafePalette.DARK.background)}",
            ColorSafePalette.contrastRatio(ColorSafePalette.DARK.text, ColorSafePalette.DARK.background) >= ColorSafePalette.MIN_TEXT_CONTRAST
        )
        assertTrue(
            "dark accent on background: ${ColorSafePalette.contrastRatio(ColorSafePalette.DARK.accent, ColorSafePalette.DARK.background)}",
            ColorSafePalette.contrastRatio(ColorSafePalette.DARK.accent, ColorSafePalette.DARK.background) >= ColorSafePalette.MIN_UI_CONTRAST
        )
    }

    @Test
    fun everyProThemePassesTheSameChecksAsTheFreePalette(): Unit {
        ColorSafePalette.CUSTOM_THEMES.forEach { theme ->
            assertTrue("${theme.name} fails the checks", ColorSafePalette.passesChecks(theme))
        }
    }

    @Test
    fun everyPaletteKeepsItsDangerContrast(): Unit {
        // M11R finding 15: `error` is text, so it needs 4.5:1, not the 3:1 a graphical state token
        // needs. Wiring it to stateB put 2.87:1 on the light background.
        (listOf(ColorSafePalette.LIGHT, ColorSafePalette.DARK) + ColorSafePalette.CUSTOM_THEMES).forEach { palette ->
            val contrast = ColorSafePalette.contrastRatio(palette.danger, palette.background)
            assertTrue("${palette.name} danger text is only ${contrast}:1", contrast >= ColorSafePalette.MIN_TEXT_CONTRAST)
        }
    }

    @Test
    fun freePalettesAreColourBlindSafe(): Unit {
        assertTrue("light state pair", ColorSafePalette.stateIsColourBlindSafe(ColorSafePalette.LIGHT))
        assertTrue("dark state pair", ColorSafePalette.stateIsColourBlindSafe(ColorSafePalette.DARK))
    }

    @Test
    fun aRedGreenPairIsRejectedEvenWhenItsContrastIsHigh(): Unit {
        // The rule has to be real, not satisfied by whatever the palette happens to contain.
        val redGreen = Palette(
            name = "red-green",
            background = Rgb(247, 248, 250),
            text = Rgb(26, 29, 33),
            accent = Rgb(47, 111, 237),
            stateA = Rgb(200, 40, 40),
            stateB = Rgb(40, 160, 60),
            danger = Rgb(176, 24, 36),
            isDark = false
        )
        val contrast = ColorSafePalette.contrastRatio(redGreen.stateA, redGreen.stateB)
        assertTrue("contrast alone is not enough: $contrast", ColorSafePalette.contrastRatio(redGreen.text, redGreen.background) >= ColorSafePalette.MIN_TEXT_CONTRAST)
        assertTrue("a red/green pair must be rejected", !ColorSafePalette.stateIsColourBlindSafe(redGreen))
    }

    @Test
    fun customThemeIsIgnoredWithoutTheEntitlement(): Unit {
        val settings = AppSettings(theme = ThemeChoice.LIGHT, customTheme = "high-contrast")
        val resolved = ColorSafePalette.resolve(settings, FakeEntitlement(false), systemIsDark = false)
        assertEqualsName(ColorSafePalette.LIGHT.name, resolved.name)

        val pro = ColorSafePalette.resolve(settings, FakeEntitlement(true), systemIsDark = false)
        assertEqualsName("high-contrast", pro.name)
    }

    @Test
    fun darkChoiceIsHonouredAndAnUnknownNameFallsBackToTheFreePalette(): Unit {
        val dark = ColorSafePalette.resolve(AppSettings(theme = ThemeChoice.DARK), FakeEntitlement(false), systemIsDark = false)
        assertEqualsName(ColorSafePalette.DARK.name, dark.name)

        val systemInDarkMode = ColorSafePalette.resolve(AppSettings(theme = ThemeChoice.SYSTEM), FakeEntitlement(false), systemIsDark = true)
        assertEqualsName(ColorSafePalette.DARK.name, systemInDarkMode.name)

        val unknown = ColorSafePalette.resolve(
            AppSettings(theme = ThemeChoice.LIGHT, customTheme = "not-a-theme"),
            FakeEntitlement(true),
            systemIsDark = false
        )
        assertEqualsName(ColorSafePalette.LIGHT.name, unknown.name)
    }

    private fun assertEqualsName(expected: String, actual: String) {
        assertTrue("expected $expected but got $actual", expected == actual)
    }
}
