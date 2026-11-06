package com.blueorbit.ruler.core.ui

/**
 * Derived colour roles (DESIGN.md §2).
 *
 * `Palette` stays the only place a colour is authored. Everything else is derived from it with fixed
 * blend ratios, so the four checked palettes (light, dark, high-contrast, sepia) keep their contrast
 * guarantee by construction instead of by a second table that can drift out of date.
 *
 * Kept as plain data (`Rgb`, no Compose types) for the same reason `Palette` is: the contrast rules
 * are testable on the JVM without a UI.
 */
data class RulerColors(
    val name: String,
    val background: Rgb,
    val surface: Rgb,
    val surfaceElevated: Rgb,
    val surfaceVariant: Rgb,
    val onBackground: Rgb,
    val onSurface: Rgb,
    val onSurfaceSecondary: Rgb,
    val outline: Rgb,
    /**
     * The quieter rule (M19, design-concept/): panel hairlines on an instrument body are drawn with
     * this, so the plate reads as one machined body rather than as a framed box. It is *decorative*
     * and deliberately not in `rulerContrastChecks`: a hairline never carries meaning on its own
     * (fill and ink always say what the panel is), and 0.60 toward the background is below 3:1 on
     * the light palette by construction.
     */
    val outlineVariant: Rgb,
    /** Ink on the plate. Major is the checked text colour; minor is the quieter ladder step. */
    val tickMajor: Rgb,
    val tickMinor: Rgb,
    val accent: Rgb,
    val stateA: Rgb,
    val stateB: Rgb,
    val danger: Rgb,
    val isDark: Boolean
)

/**
 * sRGB linear interpolation: `ratio` 0 returns `from`, 1 returns `toward`. Out-of-range ratios are
 * clamped rather than extrapolated, because a negative ratio would produce a colour that was never
 * checked against the contrast rules.
 */
fun blend(from: Rgb, toward: Rgb, ratio: Double): Rgb {
    val t = ratio.coerceIn(0.0, 1.0)

    fun channel(fromValue: Int, towardValue: Int): Int =
        Math.round(fromValue + (towardValue - fromValue) * t).toInt()

    return Rgb(
        r = channel(from.r, toward.r),
        g = channel(from.g, toward.g),
        b = channel(from.b, toward.b)
    )
}

/**
 * The DESIGN.md §2.1 blend ratios. The surfaces blend *toward the ink* (a plate that is the ink at
 * 4–14 %), the quiet inks blend *toward the background* (text that is 20–40 % background), so every
 * derived role sits between two colours that were already checked.
 */
fun Palette.toRulerColors(): RulerColors = RulerColors(
    name = name,
    background = background,
    surface = blend(background, text, 0.04),
    surfaceElevated = blend(background, text, 0.08),
    surfaceVariant = blend(background, text, 0.14),
    onBackground = text,
    onSurface = text,
    onSurfaceSecondary = blend(text, background, 0.25),
    outline = blend(text, background, 0.40),
    outlineVariant = blend(text, background, 0.60),
    tickMajor = text,
    tickMinor = blend(text, background, 0.20),
    accent = accent,
    stateA = stateA,
    stateB = stateB,
    danger = danger,
    isDark = isDark
)

/**
 * The contrast pairs the UI actually paints, exposed as a function so `RulerColorsTest` asserts the
 * same numbers the screens rely on. A surface is a *fill*, so it is never checked against text here:
 * only the ink drawn on it is.
 */
data class ContrastCheck(val role: String, val ratio: Double, val minRatio: Double) {

    val passed: Boolean get() = ratio >= minRatio
}

fun rulerContrastChecks(colors: RulerColors): List<ContrastCheck> {
    val text = ColorSafePalette.MIN_TEXT_CONTRAST
    val ui = ColorSafePalette.MIN_UI_CONTRAST

    return listOf(
        ContrastCheck("onBackground", ColorSafePalette.contrastRatio(colors.onBackground, colors.background), text),
        ContrastCheck("onSurface", ColorSafePalette.contrastRatio(colors.onSurface, colors.background), text),
        ContrastCheck("onSurfaceSecondary", ColorSafePalette.contrastRatio(colors.onSurfaceSecondary, colors.background), text),
        ContrastCheck("tickMajor", ColorSafePalette.contrastRatio(colors.tickMajor, colors.surfaceElevated), text),
        ContrastCheck("tickMinor", ColorSafePalette.contrastRatio(colors.tickMinor, colors.surfaceElevated), text),
        ContrastCheck("outline", ColorSafePalette.contrastRatio(colors.outline, colors.background), ui),
        ContrastCheck("accent on background", ColorSafePalette.contrastRatio(colors.accent, colors.background), ui),
        // The measured span is a solid accent rule drawn on the plate, not on the background.
        ContrastCheck("accent on plate", ColorSafePalette.contrastRatio(colors.accent, colors.surfaceElevated), ui),
        ContrastCheck("danger", ColorSafePalette.contrastRatio(colors.danger, colors.background), text)
    )
}
