package com.blueorbit.ruler.core.ui

import android.provider.Settings
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * Motion (DESIGN.md §10). A tool app must not delay the tool, so motion is short and purposeful: it
 * marks a *state change* — a readout updating, a screen opening or closing, the theme being swapped,
 * the Pro status turning to "Unlocked" — and never decorates a screen that is standing still. Every
 * duration goes through [motionMillis] (or a check of [reducedMotion]), so the system's "remove
 * animations" setting turns all of it off, with no exceptions.
 *
 * The budget, so new motion has a reference: 100–150 ms for something that changes in place (a fade
 * between bar destinations, a readout, a button swap), ~220 ms for something that moves (a detail
 * screen sliding in or out, the palette cross-fade). Nothing longer.
 *
 * `ANIMATOR_DURATION_SCALE == 0` is the system's own "no animations" setting (Developer options /
 * accessibility), so honouring it needs no new permission and no new dependency.
 */
@Composable
fun reducedMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
}

/** A duration that collapses to zero when the user has asked for no animation. */
fun motionMillis(milliseconds: Int, reduced: Boolean): Int = if (reduced) 0 else milliseconds

/** The curves the app uses. Anything else is decoration. */
val readoutEasing = FastOutSlowInEasing
val spanRuleEasing = LinearEasing

/** A fade between two bar destinations: the content changes in place, the bar around it does not move. */
const val MOTION_BAR_FADE_MS = 150

/** A detail screen sliding in from, or back out towards, the side it was opened from. */
const val MOTION_DETAIL_SLIDE_MS = 220

/** The palette swap when the theme changes in Settings. */
const val MOTION_THEME_MS = 220

/** A control or a status line replaced in place (Next → Start, Locked → Unlocked). */
const val MOTION_SWAP_MS = 150
