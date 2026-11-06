package com.blueorbit.ruler.core.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import java.util.Locale

/**
 * The type scale (DESIGN.md §3).
 *
 * No new font file: tabular figures come from the `"tnum"` OpenType feature on the family the system
 * already renders, so a readout cannot reflow while the value changes — which matters because the
 * readout is the thing the user is reading off a moving device.
 *
 * Every size is `sp`, so the system font scale still scales the UI. The ruler's *tick labels* are the
 * one exception and they keep their pixel pipeline in `RulerPlate` (M11R finding 8): they feed the
 * label-step maths, so expressing them in sp would break rule 1.
 */
data class RulerTypography(
    /** The number the screen exists to show. */
    val readout: TextStyle,
    val readoutSecondary: TextStyle,
    val numberLarge: TextStyle,
    val titleLarge: TextStyle,
    val titleMedium: TextStyle,
    val bodyLarge: TextStyle,
    val bodyMedium: TextStyle,
    /** Provenance lines, dates, captions. */
    val meta: TextStyle,
    val labelLarge: TextStyle,
    /**
     * The wordmark in the app bar (M19, design-concept/ headline-sm): 16 sp, quiet tracking.
     * It sets the instrument's identity, not a screen title — screen titles stay sentence case.
     */
    val barTitle: TextStyle,
    /** Micro labels: section headers, nav labels, captions above a readout (label-caps in the concept). */
    val labelCaps: TextStyle,
    /** The smallest register: chips, provenance tags, plate captions (label-micro in the concept). */
    val labelMicro: TextStyle
)

/**
 * The caps register of the M19 roles (DESIGN.md §3).
 *
 * Compose UI 1.10 has no `TextStyle.textCase`, so the caps are applied by the caller, where the role
 * is used — never in the style. That keeps the promise the same either way: the strings in resources
 * stay sentence case (rule 9), so German stays a real translation and `LocalisationTest` is untouched.
 * `Locale.ROOT` so the mapping does not depend on the device locale (an `i` in Turkish would otherwise
 * lose its dot).
 */
fun String.capsRegister(): String = uppercase(Locale.ROOT)

/** The OpenType feature that makes digits fixed-width. One name so no screen guesses at it. */
const val TABULAR_FONT_FEATURES = "tnum"

/** Applies the tabular-figure feature without touching anything else about the style. */
fun TextStyle.tabular(): TextStyle = copy(fontFeatureSettings = TABULAR_FONT_FEATURES)

@Composable
fun rememberRulerTypography(): RulerTypography {
    val base = MaterialTheme.typography
    return RulerTypography(
        readout = base.displaySmall.copy(
            fontSize = 44.sp,
            lineHeight = 52.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = (-0.2).sp
        ).tabular(),
        readoutSecondary = base.headlineSmall.copy(
            fontSize = 28.sp,
            lineHeight = 36.sp,
            fontWeight = FontWeight.Medium
        ).tabular(),
        numberLarge = base.headlineMedium.copy(
            fontSize = 32.sp,
            lineHeight = 40.sp,
            fontWeight = FontWeight.Medium
        ).tabular(),
        titleLarge = base.titleLarge.copy(
            fontSize = 22.sp,
            lineHeight = 28.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.sp
        ),
        titleMedium = base.titleMedium.copy(
            fontSize = 16.sp,
            lineHeight = 24.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.1.sp
        ),
        bodyLarge = base.bodyLarge.copy(
            fontSize = 16.sp,
            lineHeight = 24.sp,
            fontWeight = FontWeight.Normal,
            letterSpacing = 0.25.sp
        ),
        bodyMedium = base.bodyMedium.copy(
            fontSize = 14.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.Normal,
            letterSpacing = 0.25.sp
        ),
        meta = base.bodySmall.copy(
            fontSize = 12.sp,
            lineHeight = 16.sp,
            fontWeight = FontWeight.Normal,
            letterSpacing = 0.3.sp
        ).tabular(),
        labelLarge = base.labelLarge.copy(
            fontSize = 14.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.1.sp
        ),
        barTitle = base.titleMedium.copy(
            fontSize = 16.sp,
            lineHeight = 24.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.8.sp
        ),
        // The concept's label-caps: 11 sp / 500. The tracking is 0.8 sp rather than the concept's
        // 0.12 em because the widest label this role carries is the nav bar, and the nav bar is the
        // one place that keeps sentence case (see `AppScaffold.NavLabel`).
        labelCaps = base.labelLarge.copy(
            fontSize = 11.sp,
            lineHeight = 14.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.8.sp
        ),
        labelMicro = base.labelSmall.copy(
            fontSize = 9.sp,
            lineHeight = 12.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.4.sp
        )
    )
}

/**
 * The same styles pushed into the Material scales, so the stock components (buttons, fields, nav
 * items) pick up the app's type instead of the default purple-era defaults.
 */
fun RulerTypography.toMaterialTypography(): Typography {
    val default = Typography()
    return default.copy(
        displaySmall = readout,
        headlineSmall = readoutSecondary,
        headlineMedium = numberLarge,
        titleLarge = titleLarge,
        titleMedium = titleMedium,
        bodyLarge = bodyLarge,
        bodyMedium = bodyMedium,
        bodySmall = meta,
        labelLarge = labelLarge
    )
}

val LocalRulerType = staticCompositionLocalOf<RulerTypography> {
    error("RulerTheme must provide LocalRulerType")
}
