package com.blueorbit.ruler.core.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathNode
import androidx.compose.ui.unit.dp

/**
 * Icons (DESIGN.md §6). No `material-icons-extended`: the only glyph this app needs that the core
 * set does not have is the ruler itself, and it is drawn here on the same 24 dp grid and 2 dp stroke
 * weight as the core icons. It is the same silhouette as the app icon (§11).
 *
 * Every bar is a closed rectangle rather than a stroked line, because `ImageVector` paints fills: a
 * 2 dp bar is the stroke, drawn as a rect. The plate frame is four bars rather than a rect with a
 * hole so the fill rule cannot change the shape on a renderer that resolves it differently.
 *
 * The fill is stated explicitly (a solid black brush, which is what `addPath` expects and what the
 * generated core icons carry): `Icon` replaces whatever it paints with its own tint through a colour
 * filter, so black is the right authored value and it is what makes the glyph visible at all. Left
 * unset, `addPath` paints nothing and the nav item renders as an empty indicator pill with a label
 * under it — which is exactly what the first build of this screen did on the device.
 */
object RulerIcons {

    val Ruler: ImageVector by lazy {
        ImageVector.Builder(
            name = "Ruler",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            addPath(
                rect(2f, 6f, 22f, 8f) +
                    rect(2f, 16f, 22f, 18f) +
                    rect(2f, 6f, 4f, 18f) +
                    rect(20f, 6f, 22f, 18f),
                fill = SolidColor(Color.Black)
            )
            // Start line plus three ticks of rising height: the tick hierarchy, in icon form.
            addPath(
                rect(4.6f, 8f, 6.6f, 16f) +
                    rect(9.6f, 12.5f, 11.6f, 16f) +
                    rect(13.2f, 10.5f, 15.2f, 16f) +
                    rect(16.8f, 8.5f, 18.8f, 16f),
                fill = SolidColor(Color.Black)
            )
        }.build()
    }

    private fun rect(x0: Float, y0: Float, x1: Float, y1: Float): List<PathNode> = listOf(
        PathNode.MoveTo(x0, y0),
        PathNode.LineTo(x1, y0),
        PathNode.LineTo(x1, y1),
        PathNode.LineTo(x0, y1),
        PathNode.Close
    )
}
