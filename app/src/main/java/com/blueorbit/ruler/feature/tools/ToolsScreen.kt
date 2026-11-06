package com.blueorbit.ruler.feature.tools

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.blueorbit.ruler.R
import com.blueorbit.ruler.core.entitlement.EntitlementRepository
import com.blueorbit.ruler.core.ui.LocalRulerColors
import com.blueorbit.ruler.core.ui.LocalRulerType
import com.blueorbit.ruler.core.ui.OutlinedChip
import com.blueorbit.ruler.core.ui.RulerCard
import com.blueorbit.ruler.core.ui.RulerSpacing
import com.blueorbit.ruler.core.ui.ScreenTitleRow
import com.blueorbit.ruler.core.ui.paneCount
import com.blueorbit.ruler.core.ui.toCompose
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Tools (DESIGN.md §6, §13, M19): the Pro tools live behind one destination, so the ruler screen no
 * longer carries five navigation buttons.
 *
 * Rule 10: a locked card is still clickable and still leads to the gate message on the tool's own
 * screen. The card names what is Pro in the same breath as what stays free, and the chip carries the
 * word "Pro", so the lock is never colour-only (SPEC 11). M21 adds the first free tool here: a card
 * with no chip at all, because "ring size" is what people search for and gating it would hide the very
 * thing that brings the install (roadmap rule 9).
 *
 * Each card carries the concept's hairline technical diagram (design-concept/ 03): the instrument it
 * opens, drawn with the app's own tick/accent tokens. It is structure, not illustration - no text,
 * no numbers, no claims.
 */
@Composable
fun ToolsScreen(
    entitlement: EntitlementRepository,
    onOpenExtend: () -> Unit,
    onOpenLevel: () -> Unit,
    onOpenProtractor: () -> Unit,
    onOpenRingSize: () -> Unit
) {
    val type = LocalRulerType.current
    val colors = LocalRulerColors.current
    val widthDp = LocalConfiguration.current.screenWidthDp
    val entitlementState by entitlement.state.collectAsStateWithLifecycle()
    val unlocked = entitlementState.unlocked

    val cards = listOf(
        ToolEntry(
            id = ToolDiagramId.RINGSIZE,
            title = stringResource(R.string.ring_title),
            body = stringResource(R.string.tools_ring_body),
            pro = false,
            open = onOpenRingSize
        ),
        ToolEntry(
            id = ToolDiagramId.EXTEND,
            title = stringResource(R.string.extend_title),
            body = stringResource(R.string.tools_extend_body),
            pro = true,
            open = onOpenExtend
        ),
        ToolEntry(
            id = ToolDiagramId.LEVEL,
            title = stringResource(R.string.level_title),
            body = stringResource(R.string.tools_level_body),
            pro = true,
            open = onOpenLevel
        ),
        ToolEntry(
            id = ToolDiagramId.PROTRACTOR,
            title = stringResource(R.string.protractor_title),
            body = stringResource(R.string.tools_protractor_body),
            pro = true,
            open = onOpenProtractor
        )
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(RulerSpacing.md)
    ) {
        ScreenTitleRow(stringResource(R.string.nav_tools))
        Text(
            stringResource(R.string.tools_body),
            style = type.bodyMedium,
            color = colors.onSurfaceSecondary.toCompose()
        )

        if (paneCount(widthDp) > 1) {
            cards.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(RulerSpacing.md)) {
                    row.forEach { entry ->
                        Box(Modifier.weight(1f)) { ToolCard(entry, unlocked) }
                    }
                }
            }
        } else {
            cards.forEach { entry -> ToolCard(entry, unlocked) }
        }
    }
}

private enum class ToolDiagramId { RINGSIZE, EXTEND, LEVEL, PROTRACTOR }

private class ToolEntry(
    val id: ToolDiagramId,
    val title: String,
    val body: String,
    /** False for a tool everyone gets (roadmap rule 9): no chip, no gate, no lock. */
    val pro: Boolean,
    val open: () -> Unit
)

@Composable
private fun ToolCard(entry: ToolEntry, unlocked: Boolean) {
    val type = LocalRulerType.current
    val colors = LocalRulerColors.current

    RulerCard(onClick = entry.open) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(entry.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            // An outlined chip with the word in it: the lock is readable without a colour (SPEC 11).
            // A free tool carries no chip at all, so the chip cannot be read as decoration.
            if (entry.pro && !unlocked) OutlinedChip(stringResource(R.string.settings_pro_title))
        }
        Text(entry.body, style = type.bodyMedium, color = colors.onSurfaceSecondary.toCompose())
        ToolDiagram(entry.id, Modifier.padding(top = RulerSpacing.sm))
    }
}

/**
 * The hairline diagram strip under each card title (DESIGN.md §6 M19). Same ink rules as the ruler
 * plate: `tickMinor` for the graduations, `tickMajor` for the instrument body, `accent` for the one
 * measured thing. Nothing here is a measurement — the diagrams carry no values.
 */
@Composable
private fun ToolDiagram(id: ToolDiagramId, modifier: Modifier = Modifier) {
    val colors = LocalRulerColors.current
    val quiet = colors.tickMinor.toCompose()
    val ink = colors.tickMajor.toCompose()
    val accent = colors.accent.toCompose()

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
    ) {
        val midY = size.height / 2f
        when (id) {
            // Ring size: the datum, the circle and the diameter across it - the same three marks the
            // ring plate draws, so the card shows the instrument the card opens.
            ToolDiagramId.RINGSIZE -> {
                val centre = Offset(size.width / 2f, midY)
                val radius = min(size.width, size.height) / 2f - 6.dp.toPx()
                // The datum cross pokes the same little way past the circle on every side. It used to
                // run further along one axis than the other, and the card diagram read as a line that
                // had slipped sideways out of the ring.
                val datum = radius * 1.18f
                drawLine(quiet, Offset(centre.x - datum, midY), Offset(centre.x + datum, midY), 1.dp.toPx())
                drawLine(quiet, Offset(centre.x, midY - datum), Offset(centre.x, midY + datum), 1.dp.toPx())
                drawCircle(accent, radius = radius, center = centre, style = Stroke(width = 1.5.dp.toPx()))
                val cap = 8.dp.toPx()
                drawLine(ink, Offset(centre.x - radius, midY), Offset(centre.x + radius, midY), 1.dp.toPx())
                drawLine(ink, Offset(centre.x - radius, midY - cap / 2f), Offset(centre.x - radius, midY + cap / 2f), 1.dp.toPx())
                drawLine(ink, Offset(centre.x + radius, midY - cap / 2f), Offset(centre.x + radius, midY + cap / 2f), 1.dp.toPx())
            }

            // Extend: a span rule with end caps and the screen-length ticks it chains across.
            ToolDiagramId.EXTEND -> {
                val left = 8.dp.toPx()
                val right = size.width - 8.dp.toPx()
                drawLine(quiet, Offset(left, midY), Offset(right, midY), 1.dp.toPx())
                var x = left
                while (x < right) {
                    drawLine(quiet, Offset(x, midY - 5.dp.toPx()), Offset(x, midY + 5.dp.toPx()), 1.dp.toPx())
                    x += 28.dp.toPx()
                }
                drawLine(accent, Offset(left, midY), Offset(right - 30.dp.toPx(), midY), 2.dp.toPx())
                drawEndCap(accent, left, midY)
                drawEndCap(accent, right - 30.dp.toPx(), midY)
            }

            // Bubble level: the ring, the crosshair and the bubble off centre.
            ToolDiagramId.LEVEL -> {
                val centre = Offset(size.width / 2f, midY)
                val radius = min(size.width, size.height) / 2f - 6.dp.toPx()
                drawCircle(quiet, radius = radius, center = centre, style = Stroke(width = 1.dp.toPx()))
                drawLine(quiet, Offset(centre.x - radius, centre.y), Offset(centre.x + radius, centre.y), 1.dp.toPx())
                drawLine(quiet, Offset(centre.x, centre.y - radius), Offset(centre.x, centre.y + radius), 1.dp.toPx())
                val bubble = Offset(
                    centre.x + radius * 0.45f * cos(Math.toRadians(-35.0)).toFloat(),
                    centre.y + radius * 0.45f * sin(Math.toRadians(-35.0)).toFloat()
                )
                drawCircle(accent, radius = radius * 0.22f, center = bubble)
            }

            // Protractor: the datum, the 15° graduation ring and one measured ray with its arc.
            ToolDiagramId.PROTRACTOR -> {
                val cx = size.width / 2f
                val cy = size.height - 8.dp.toPx()
                val radius = min(size.width / 2f - 8.dp.toPx(), size.height - 16.dp.toPx())
                drawLine(quiet, Offset(cx - radius, cy), Offset(cx + radius, cy), 1.dp.toPx())
                drawArc(
                    color = quiet,
                    startAngle = 180f,
                    sweepAngle = 180f,
                    useCenter = false,
                    topLeft = Offset(cx - radius, cy - radius),
                    size = Size(radius * 2f, radius * 2f),
                    style = Stroke(width = 1.dp.toPx())
                )
                var deg = 0.0
                while (deg <= 180.0) {
                    val r = Math.toRadians(deg)
                    drawLine(
                        quiet,
                        Offset(cx + (radius - 4.dp.toPx()) * cos(r).toFloat(), cy - (radius - 4.dp.toPx()) * sin(r).toFloat()),
                        Offset(cx + radius * cos(r).toFloat(), cy - radius * sin(r).toFloat()),
                        1.dp.toPx()
                    )
                    deg += 30.0
                }
                val measured = Math.toRadians(50.0)
                drawLine(
                    ink,
                    Offset(cx, cy),
                    Offset(cx + radius * cos(measured).toFloat(), cy - radius * sin(measured).toFloat()),
                    1.5.dp.toPx()
                )
                val arcR = radius * 0.55f
                drawArc(
                    color = accent,
                    startAngle = (-50.0).toFloat(),
                    sweepAngle = 50f,
                    useCenter = false,
                    topLeft = Offset(cx - arcR, cy - arcR),
                    size = Size(arcR * 2f, arcR * 2f),
                    style = Stroke(width = 2.dp.toPx())
                )
            }
        }
    }
}

private fun DrawScope.drawEndCap(accent: androidx.compose.ui.graphics.Color, x: Float, y: Float) {
    val cap = 8.dp.toPx()
    drawLine(accent, Offset(x, y - cap / 2f), Offset(x, y + cap / 2f), 2.dp.toPx())
}
