package com.blueorbit.ruler.core.ui

import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Spacing, shape and size tokens (DESIGN.md §4). One file, so a screen cannot invent its own gap.
 *
 * None of these reach the physical geometry (rule 1): they are layout and ink, never `pxPerMm`.
 */
object RulerSpacing {
    val xs: Dp = 4.dp
    val sm: Dp = 8.dp
    val md: Dp = 12.dp
    val lg: Dp = 16.dp
    val xl: Dp = 24.dp
    val xxl: Dp = 32.dp
}

/**
 * M19 (design-concept/): every corner is 0 dp. The instrument language is machined rectangles —
 * panels are distinguished by fill plus hairline, and a rounded corner would read as a card from
 * another app (DESIGN.md §4, amended at M19).
 */
object RulerShapes {
    val chip = RectangleShape
    val card = RectangleShape
    /** The ruler plate and any panel that holds a drawn instrument. */
    val plate = RectangleShape
    val field = RectangleShape
    val sheet = RectangleShape
    /** Segmented controls and nav indicators are rectangular (DESIGN.md §4). */
    val pill = RectangleShape
}

object RulerDimensions {
    val navBarHeight: Dp = 80.dp
    val navRailWidth: Dp = 112.dp
    /** Panels are separated by fill plus a hairline outline, never by a shadow. */
    val hairline: Dp = 1.dp
    val plateEdge: Dp = 1.dp
    /** Balance spacer so a centred detail title stays centred when there is no trailing chip (M19). */
    val backControlWidth: Dp = 96.dp

    /** Readout pane in the expanded two-pane layouts, fixed so the plate width stays computable. */
    val readoutPaneWidth: Dp = 340.dp
    /** Level / protractor instrument is capped rather than stretched (DESIGN.md §6). */
    val instrumentMaxSize: Dp = 420.dp
}
