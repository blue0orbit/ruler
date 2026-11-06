package com.blueorbit.ruler.core.ui

/**
 * Width classes (DESIGN.md §6), from the Material 3 adaptive breakpoints. Pure integers so the
 * boundaries are unit-tested rather than eyeballed in a screenshot.
 *
 * No `androidx.window` and no `material3-adaptive` (DESIGN.md §0): one integer from
 * `LocalConfiguration.screenWidthDp` is the whole mechanism, which keeps the dependency list as it is.
 */
enum class LayoutClass { COMPACT, MEDIUM, EXPANDED }

const val MEDIUM_WIDTH_DP = 600
const val EXPANDED_WIDTH_DP = 840

fun layoutClassFor(widthDp: Int): LayoutClass = when {
    widthDp < MEDIUM_WIDTH_DP -> LayoutClass.COMPACT
    widthDp < EXPANDED_WIDTH_DP -> LayoutClass.MEDIUM
    else -> LayoutClass.EXPANDED
}

/** Bottom bar below 840 dp, side rail at or above it (DESIGN.md §6). */
fun useNavigationRail(widthDp: Int): Boolean = layoutClassFor(widthDp) == LayoutClass.EXPANDED

/** 1 pane below 840 dp, 2 panes at or above it. */
fun paneCount(widthDp: Int): Int = if (useNavigationRail(widthDp)) 2 else 1

/** Screen margin: 16 dp compact, 24 dp medium and expanded. */
fun screenMarginDp(widthDp: Int): Int =
    if (layoutClassFor(widthDp) == LayoutClass.COMPACT) 16 else 24
