package com.blueorbit.ruler.core.ui

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

private fun color(value: Rgb): Color = value.toCompose()

/** The one place an authored `Rgb` becomes a Compose colour, so no screen converts it by hand. */
fun Rgb.toCompose(): Color = Color(r, g, b)

/**
 * The derived roles are available to the drawing code through this local, so the canvas reads
 * `tickMajor` / `tickMinor` / `surfaceElevated` rather than guessing at `onSurface` (DESIGN.md §2.1).
 */
val LocalRulerColors = staticCompositionLocalOf<RulerColors> {
    error("RulerTheme must provide LocalRulerColors")
}

/**
 * The palette is applied through the colour scheme *and* the type scale, and the same values are
 * passed to the canvas so the ruler ticks use the checked contrast pair rather than a colour picked
 * in the draw code.
 *
 * `tertiary` is set explicitly: the Material defaults are purple, and DESIGN.md §1 rejects purple —
 * the default purple is the "prototype" look this redesign exists to remove.
 */
@Composable
fun RulerTheme(palette: Palette, content: @Composable () -> Unit) {
    val colors = animatedRulerColors(palette.toRulerColors())
    val type = rememberRulerTypography()

    // The Material 3 surface-container ladder has to be stated. Left unset it falls back to the M3
    // baseline scheme, which is purple-tinted: the bottom navigation bar rendered #F3EDF7 on the light
    // palette and #211F26 on the dark one while the rest of the screen was neutral — the exact
    // "prototype purple" DESIGN.md §1 rejects, arriving through a role nobody looked at. Each rung is
    // another step of the §2.1 blend ladder, so the containers keep the palette's neutrality and stay
    // between two colours the contrast tests already check.
    val surfaceDim = color(blend(colors.background, colors.onSurface, 0.02))
    val surfaceBright = color(blend(colors.background, colors.onSurface, 0.10))
    val surfaceContainer = color(blend(colors.background, colors.onSurface, 0.06))
    val surfaceContainerHigh = color(colors.surfaceElevated)
    val surfaceContainerHighest = color(blend(colors.background, colors.onSurface, 0.12))

    val background = color(colors.background)
    val ink = color(colors.onSurface)
    val surface = color(colors.surface)
    val surfaceVariant = color(colors.surfaceVariant)
    val accent = color(colors.accent)
    val stateA = color(colors.stateA)
    val outline = color(colors.outline)
    val secondaryInk = color(colors.onSurfaceSecondary)
    val danger = color(colors.danger)

    val scheme = if (colors.isDark) {
        darkColorScheme(
            background = background,
            onBackground = ink,
            surface = surface,
            onSurface = ink,
            surfaceVariant = surfaceVariant,
            onSurfaceVariant = secondaryInk,
            outline = outline,
            // The quieter rule (M19): panel hairlines and M3 field borders use the 0.60 rung of the
            // same blend ladder (RulerColors.outlineVariant), one step quieter than `outline`.
            outlineVariant = color(colors.outlineVariant),
            surfaceDim = surfaceDim,
            surfaceBright = surfaceBright,
            surfaceContainerLow = surface,
            surfaceContainer = surfaceContainer,
            surfaceContainerHigh = surfaceContainerHigh,
            surfaceContainerHighest = surfaceContainerHighest,
            primary = accent,
            onPrimary = background,
            primaryContainer = accent,
            onPrimaryContainer = background,
            secondary = accent,
            onSecondary = background,
            secondaryContainer = surfaceVariant,
            onSecondaryContainer = ink,
            tertiary = stateA,
            onTertiary = background,
            tertiaryContainer = surfaceVariant,
            onTertiaryContainer = ink,
            error = danger,
            onError = background,
            errorContainer = color(colors.surfaceElevated),
            onErrorContainer = danger,
            inverseSurface = color(colors.surfaceElevated),
            inverseOnSurface = ink,
            inversePrimary = accent
        )
    } else {
        lightColorScheme(
            background = background,
            onBackground = ink,
            surface = surface,
            onSurface = ink,
            surfaceVariant = surfaceVariant,
            onSurfaceVariant = secondaryInk,
            outline = outline,
            outlineVariant = color(colors.outlineVariant),
            surfaceDim = surfaceDim,
            surfaceBright = surfaceBright,
            surfaceContainerLow = surface,
            surfaceContainer = surfaceContainer,
            surfaceContainerHigh = surfaceContainerHigh,
            surfaceContainerHighest = surfaceContainerHighest,
            primary = accent,
            onPrimary = background,
            primaryContainer = accent,
            onPrimaryContainer = background,
            secondary = accent,
            onSecondary = background,
            secondaryContainer = surfaceVariant,
            onSecondaryContainer = ink,
            tertiary = stateA,
            onTertiary = background,
            tertiaryContainer = surfaceVariant,
            onTertiaryContainer = ink,
            error = danger,
            onError = background,
            errorContainer = color(colors.surfaceElevated),
            onErrorContainer = danger,
            inverseSurface = color(colors.surfaceElevated),
            inverseOnSurface = ink,
            inversePrimary = accent
        )
    }

    // MaterialTheme only provides the colours; nothing paints them unless a surface is drawn. Without
    // this the dark palette resolved to the scheme but the screen still rendered the window background.
    CompositionLocalProvider(
        LocalRulerColors provides colors,
        LocalRulerType provides type
    ) {
        MaterialTheme(colorScheme = scheme, typography = type.toMaterialTypography()) {
            Surface(color = scheme.background) { content() }
        }
    }
}

/**
 * The palette swap (Settings → Theme) as a short blend instead of a cut (Motion.kt budget).
 *
 * Every role is interpolated along the same sRGB line [blend] already uses, so each intermediate frame
 * is the two checked palettes mixed — never a colour from somewhere else. Only the derived `Rgb` roles
 * move; the tree is not duplicated (a `Crossfade` over the content would compose the whole app twice
 * and rebuild the NavHost, losing the back stack). The cost is a recomposition per frame for
 * [MOTION_THEME_MS], once, when the user picks a theme; at rest this is a value comparison.
 *
 * With reduced motion the new palette is applied on the next frame, as before.
 */
@Composable
private fun animatedRulerColors(target: RulerColors): RulerColors {
    val reduced = reducedMotion()
    var shown by remember { mutableStateOf(target) }
    LaunchedEffect(target) {
        if (shown == target) return@LaunchedEffect
        if (reduced) {
            shown = target
            return@LaunchedEffect
        }
        // Starts from whatever is on screen, so a second change mid-blend continues from there
        // instead of jumping back to the first palette.
        val from = shown
        animate(0f, 1f, animationSpec = tween(MOTION_THEME_MS, easing = readoutEasing)) { value, _ ->
            shown = from.towards(target, value.toDouble())
        }
        shown = target
    }
    return shown
}

/** Every role [ratio] of the way to [target]; the name and the light/dark flag are the target's. */
private fun RulerColors.towards(target: RulerColors, ratio: Double): RulerColors = RulerColors(
    name = target.name,
    background = blend(background, target.background, ratio),
    surface = blend(surface, target.surface, ratio),
    surfaceElevated = blend(surfaceElevated, target.surfaceElevated, ratio),
    surfaceVariant = blend(surfaceVariant, target.surfaceVariant, ratio),
    onBackground = blend(onBackground, target.onBackground, ratio),
    onSurface = blend(onSurface, target.onSurface, ratio),
    onSurfaceSecondary = blend(onSurfaceSecondary, target.onSurfaceSecondary, ratio),
    outline = blend(outline, target.outline, ratio),
    outlineVariant = blend(outlineVariant, target.outlineVariant, ratio),
    tickMajor = blend(tickMajor, target.tickMajor, ratio),
    tickMinor = blend(tickMinor, target.tickMinor, ratio),
    accent = blend(accent, target.accent, ratio),
    stateA = blend(stateA, target.stateA, ratio),
    stateB = blend(stateB, target.stateB, ratio),
    danger = blend(danger, target.danger, ratio),
    isDark = target.isDark
)
