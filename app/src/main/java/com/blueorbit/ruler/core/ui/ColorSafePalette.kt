package com.blueorbit.ruler.core.ui

import com.blueorbit.ruler.core.entitlement.EntitlementRepository
import com.blueorbit.ruler.core.entitlement.ProFeature
import com.blueorbit.ruler.core.settings.AppSettings
import com.blueorbit.ruler.core.settings.ThemeChoice
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.pow

/** sRGB colour kept as plain data so the contrast rules are testable without Compose. */
data class Rgb(val r: Int, val g: Int, val b: Int) {

    /** WCAG 2.1 relative luminance. */
    fun luminance(): Double {
        fun channel(value: Int): Double {
            val c = value / 255.0
            return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(r) + 0.7152 * channel(g) + 0.0722 * channel(b)
    }

    /** Which channel dominates, used to detect a red/green pair. */
    fun dominantChannel(): Char = when {
        r >= g && r >= b -> 'r'
        g >= r && g >= b -> 'g'
        else -> 'b'
    }
}

data class Palette(
    val name: String,
    val background: Rgb,
    val text: Rgb,
    val accent: Rgb,
    val stateA: Rgb,
    val stateB: Rgb,
    /**
     * The colour used for error text and prompts. It is separate from `stateB`: a state token is read
     * as a *graphical object* (3:1 is enough), while the "run calibration" prompt is **text** and needs
     * 4.5:1. Wiring `stateB` to `error` put 2.87:1 text on the light background (finding 15).
     */
    val danger: Rgb,
    val isDark: Boolean
)

/**
 * The free palette and the Pro themes, with the rules that make them safe:
 * WCAG AA contrast for text, and state colours that a reader with protanopia or deuteranopia can
 * still tell apart — which means luminance difference, not hue, and never a red/green pair.
 */
object ColorSafePalette {

    const val MIN_TEXT_CONTRAST = 4.5
    const val MIN_UI_CONTRAST = 3.0
    const val MIN_STATE_LUMINANCE_DIFFERENCE = 0.15

    // M19 (design-concept/): the accent is brass on every palette. The dark substrate moved to the
    // concept's Z-0 floor and its ink to the concept's on-surface value; the light palette takes the
    // deep end of the same brass family because the bright core (#D4AF37) cannot clear 3:1 on paper.
    val LIGHT = Palette(
        name = "light",
        background = Rgb(247, 248, 250),
        text = Rgb(26, 29, 33),
        accent = Rgb(122, 92, 18),
        stateA = Rgb(24, 48, 100),
        stateB = Rgb(192, 128, 12),
        danger = Rgb(176, 24, 36),
        isDark = false
    )

    val DARK = Palette(
        name = "dark",
        background = Rgb(17, 20, 23),
        text = Rgb(225, 226, 231),
        accent = Rgb(242, 202, 80),
        stateA = Rgb(64, 106, 186),
        stateB = Rgb(250, 200, 90),
        danger = Rgb(255, 120, 120),
        isDark = true
    )

    /** Pro themes. Every one has to pass the same checks as the free palette (rule 10). Their accents
     * are already in the brass family (high contrast is the bright end, sepia the brown end), so M19
     * left them as authored. */
    val CUSTOM_THEMES = listOf(
        Palette(
            name = "high-contrast",
            background = Rgb(0, 0, 0),
            text = Rgb(255, 255, 255),
            accent = Rgb(255, 214, 10),
            stateA = Rgb(255, 214, 10),
            stateB = Rgb(120, 200, 255),
            danger = Rgb(255, 120, 120),
            isDark = true
        ),
        Palette(
            name = "sepia",
            background = Rgb(244, 236, 222),
            text = Rgb(60, 52, 44),
            accent = Rgb(120, 86, 46),
            stateA = Rgb(178, 116, 42),
            stateB = Rgb(20, 45, 90),
            danger = Rgb(150, 30, 30),
            isDark = false
        )
    )

    /** WCAG 2.1 contrast ratio between two colours. */
    fun contrastRatio(a: Rgb, b: Rgb): Double {
        val lighter = max(a.luminance(), b.luminance())
        val darker = minOf(a.luminance(), b.luminance())
        return (lighter + 0.05) / (darker + 0.05)
    }

    /**
     * A state is safe only if it survives colour vision deficiency: enough luminance difference to
     * read without hue, and not a red/green pair, which is what protanopia and deuteranopia confuse.
     */
    fun stateIsColourBlindSafe(palette: Palette): Boolean {
        val difference = Math.abs(palette.stateA.luminance() - palette.stateB.luminance())
        val redGreen = (palette.stateA.dominantChannel() == 'r' && palette.stateB.dominantChannel() == 'g') ||
            (palette.stateA.dominantChannel() == 'g' && palette.stateB.dominantChannel() == 'r')
        return difference >= MIN_STATE_LUMINANCE_DIFFERENCE && !redGreen
    }

    /**
     * Every colour the UI can use has to clear its own bar: body text 4.5:1, the error/danger text
     * 4.5:1 as well (it *is* text), the accent and each state token 3:1 as graphical objects, and the
     * state pair safe for colour vision deficiency.
     *
     * The review suggested also requiring 3:1 **between** the two state tokens. That is not achievable
     * for a high-contrast theme whose state A is bright yellow on black (two bright colours can never
     * be 3:1 apart), and it is not the documented rule — SPEC/TECH_NOTES require a *luminance
     * difference* between states, which is what `stateIsColourBlindSafe` enforces.
     */
    fun passesChecks(palette: Palette): Boolean =
        contrastRatio(palette.text, palette.background) >= MIN_TEXT_CONTRAST &&
            contrastRatio(palette.danger, palette.background) >= MIN_TEXT_CONTRAST &&
            contrastRatio(palette.accent, palette.background) >= MIN_UI_CONTRAST &&
            contrastRatio(palette.stateA, palette.background) >= MIN_UI_CONTRAST &&
            contrastRatio(palette.stateB, palette.background) >= MIN_UI_CONTRAST &&
            stateIsColourBlindSafe(palette)

    /**
     * Rule 10: the gate is at the boundary. A custom theme stored by a Pro user is honoured; the same
     * value read from a stale store without the entitlement falls back to the free palette, so the
     * free palette is always the fallback.
     */
    fun resolve(settings: AppSettings, entitlement: EntitlementRepository, systemIsDark: Boolean): Palette {
        val dark = settings.theme == ThemeChoice.DARK || (settings.theme == ThemeChoice.SYSTEM && systemIsDark)
        val free = if (dark) DARK else LIGHT

        if (!entitlement.has(ProFeature.CUSTOM_THEMES) || settings.customTheme.isEmpty()) return free
        return CUSTOM_THEMES.firstOrNull { it.name == settings.customTheme } ?: free
    }
}
