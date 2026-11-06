package com.blueorbit.ruler.feature.ruler

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.blueorbit.ruler.core.geometry.RulerScene
import com.blueorbit.ruler.core.ui.LocalRulerColors
import com.blueorbit.ruler.core.ui.RulerColors
import com.blueorbit.ruler.core.ui.RulerDimensions
import com.blueorbit.ruler.core.ui.RulerShapes
import com.blueorbit.ruler.core.ui.TABULAR_FONT_FEATURES
import com.blueorbit.ruler.core.ui.reducedMotion
import com.blueorbit.ruler.core.ui.motionMillis
import com.blueorbit.ruler.core.ui.spanRuleEasing
import com.blueorbit.ruler.core.ui.toCompose

/**
 * The ruler plate (DESIGN.md §5): engraved ink on a matte plate.
 *
 * It renders exactly what the scenes produced — no decision about tick density or labels happens here,
 * so the geometry rules stay testable. The tick *rank* comes from `RulerScene`, so the hierarchy is a
 * property of the measurement, not of the draw code.
 *
 * Rule 1, stated where it is easiest to break: nothing in this file changes `pxPerMm`. Stroke widths
 * are dp (ink), tick lengths are multiples of the label font (ink), the plate padding is ink, and the
 * geometry origin stays the canvas origin so `zeroPixels()` and the viewport width mean exactly what
 * they meant before the restyle.
 *
 * Two edges, as on a physical ruler: the selected unit on the top edge, the other system on the bottom
 * edge. Labels are measured before being drawn so they cannot collide with the opposite edge or wrap
 * onto a second line, and the secondary edge is skipped when there is not room for both label bands.
 */
@Composable
fun RulerPlate(
    primary: RulerScene,
    secondary: RulerScene,
    startEdge: StartEdge,
    labelSizePx: Float,
    startLabel: String,
    modifier: Modifier = Modifier,
    /** End of the measured span, in canvas pixels. Negative draws no span rule. */
    spanEndPixels: Double = -1.0,
    /**
     * Draw the span end as something a finger can grab (M17). It is ink — a fixed dp radius at the end of
     * the rule — so it cannot move the geometry, and Extend mode leaves it off because its end is the
     * screen edge, not something the user drags.
     */
    showSpanHandle: Boolean = false,
    /** Extend mode mark. Negative means no mark, so the ruler screen is unaffected. */
    markPixels: Double = -1.0,
    /**
     * False while a finger is moving the span: the line then follows the finger exactly. The easing is
     * for changes the user did not just cause (a unit, a calibration, a reset to the screen edge).
     */
    animateSpan: Boolean = true
) {
    val colors = LocalRulerColors.current
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val reduced = reducedMotion()

    // `labelSizePx` is a **pixel** measurement — it comes from a sp value converted with `toPx()` and
    // the scene uses it for the label-step maths. Compose text sizes are in Sp, so the pixel value has
    // to be converted back; handing the raw number over as Sp scaled every glyph a second time by the
    // display density (≈3.5× on the test device), which is why the canvas had to skip so many labels
    // to avoid collisions (M11R finding 8). `toSp()` divides by density *and* font scale, and the
    // measurer multiplies both back, so the label keeps its exact pixel size.
    val labelFontSize = with(density) { labelSizePx.toSp() }
    // Tabular figures on the tick labels too (DESIGN.md §3): a label must not change width as the
    // value changes, or the label-step maths and the drawn label would disagree.
    val tickStyle = remember(labelFontSize, colors.tickMajor) {
        TextStyle(
            fontSize = labelFontSize,
            color = colors.tickMajor.toCompose(),
            fontFeatureSettings = TABULAR_FONT_FEATURES
        )
    }
    // Tick labels measured once per style, not once per frame. A drag redraws the plate every frame,
    // and the measurer's own cache holds eight layouts — far fewer than a plate has labels — so without
    // this every frame of a drag re-laid out every number on both edges.
    val labelCache = remember(tickStyle) { HashMap<String, TextLayoutResult>() }

    // Only the span rule moves, and only its width (DESIGN.md §10).
    val spanEnd by animateFloatAsState(
        targetValue = spanEndPixels.toFloat(),
        animationSpec = if (animateSpan) {
            tween(motionMillis(160, reduced), easing = spanRuleEasing)
        } else {
            snap()
        },
        label = "spanRule"
    )

    Box(
        modifier = modifier
            .heightIn(min = with(density) { (labelSizePx * PLATE_MIN_HEIGHT_FACTOR).toDp() })
            .clip(RulerShapes.plate)
            .background(colors.surfaceElevated.toCompose(), RulerShapes.plate)
            // M19 (DESIGN.md §5.3): the plate edge is the quiet panel hairline, not the control outline.
            .border(RulerDimensions.plateEdge, colors.outlineVariant.toCompose(), RulerShapes.plate)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val accent = colors.accent.toCompose()
            val zero = startEdge.zeroPixels().toFloat()

            // DESIGN.md §5.3 asks for inner padding on all four sides. It is applied **vertically
            // only**: the tick positions are geometry, `zeroPixels()` is the bezel inset measured from
            // the plate's left edge, and padding the left side would move the zero away from the
            // physical device edge — which is the one thing rule 1 forbids. Vertical padding is ink.
            val platePad = labelSizePx * 0.4f
            // Both edges put their labels above their ticks, so each edge needs
            // label band + gap + longest tick (1.9 label sizes) before its baseline. The first draft
            // used 2.2 for the primary edge, which is shorter than the longest tick plus a label, so
            // the centimetre numbers printed on top of the ticks they belong to.
            val edgeBand = labelSizePx * 3.2f
            val topLabelTop = platePad
            val topBaseline = platePad + edgeBand
            val bottomBaseline = size.height - platePad
            val bottomLabelTop = bottomBaseline - edgeBand

            // The span is drawn first so the ticks print over it: it is the surface the ink sits on,
            // not a highlight laid on top of the measurement.
            if (spanEnd >= 0f) {
                drawSpanRule(accent, zero, spanEnd, topBaseline)
                if (showSpanHandle) drawSpanHandle(accent, spanEnd, zero, topBaseline)
            }

            drawEdge(primary, startEdge, labelSizePx, topLabelTop, topBaseline, textMeasurer, tickStyle, colors, labelCache)

            if (bottomLabelTop > topBaseline) {
                drawEdge(secondary, startEdge, labelSizePx, bottomLabelTop, bottomBaseline, textMeasurer, tickStyle, colors, labelCache)
            }

            // Start line: the zero of the ruler, drawn at the bezel inset, with a triangle stop so the
            // zero reads as the edge an object is aligned against rather than as an extra line.
            drawStartLine(accent, zero)
            val startLayout = textMeasurer.measure(
                startLabel,
                TextStyle(fontSize = labelFontSize, color = accent, fontFeatureSettings = TABULAR_FONT_FEATURES),
                maxLines = 1
            )
            // The label sits under the primary baseline, against the start line. It used to sit at the
            // vertical centre of the plate, which is the one place on the plate that is not next to
            // anything: the word "start" with no line beside it read as a stray label.
            if (zero + 4f + startLayout.size.width <= size.width) {
                drawText(
                    startLayout,
                    color = accent,
                    topLeft = Offset(zero + 4f, topBaseline + labelSizePx * 0.8f)
                )
            }

            if (markPixels >= 0.0) drawMark(accent, markPixels.toFloat())
        }
    }
}

/**
 * Minimum plate height: two edge bands (labels above ticks, 3.2 label sizes each) plus the plate
 * padding, with room left over for the start line's label. Below this the secondary edge is skipped,
 * which is the tested rule, but the primary edge still has to fit.
 */
private const val PLATE_MIN_HEIGHT_FACTOR = 8f

/** Rank decides length, weight and ink (DESIGN.md §5.2). Rank 3 is the printed step. */
private fun tickLengthFactor(rank: Int): Float = when (rank) {
    0 -> 0.90f
    1 -> 1.15f
    2 -> 1.45f
    else -> 1.90f
}

private fun DrawScope.tickStroke(rank: Int): Float = when (rank) {
    0 -> 0.6.dp.toPx()
    1 -> 0.8.dp.toPx()
    2 -> 1.0.dp.toPx()
    else -> 1.4.dp.toPx()
}

private fun DrawScope.drawSpanRule(accent: Color, zero: Float, end: Float, baseline: Float) {
    if (end <= zero) return
    val spanEnd = end.coerceAtMost(size.width)
    val rule = baseline + 3f
    drawLine(
        color = accent,
        start = Offset(zero, rule),
        end = Offset(spanEnd, rule),
        strokeWidth = 3.dp.toPx()
    )
    // A short cap at the end of the span. Without it the rule is a line that stops in the middle of
    // the plate, and the reading it describes is 65.3 mm *long* — the cap is what turns the rule into
    // a length with two ends. It is drawn below the baseline so it never crosses a tick.
    val cap = 10.dp.toPx()
    drawLine(
        color = accent,
        start = Offset(spanEnd, rule - cap / 2f),
        end = Offset(spanEnd, rule + cap / 2f),
        strokeWidth = 2.dp.toPx()
    )
}

/**
 * The end of the span drawn as a knob (M17). `drawCircle` has no stroke width in this Compose version
 * (M16), so the ring is a stroked path. The knob is ink: its radius is dp and its centre is clamped
 * inside the plate so it is never half-off-screen, while the span rule itself keeps its exact end.
 */
private fun DrawScope.drawSpanHandle(accent: Color, end: Float, zero: Float, baseline: Float) {
    val rule = baseline + 3f
    val radius = 13.dp.toPx()
    val minCentre = zero + radius
    val maxCentre = size.width - radius
    // A plate narrower than the knob itself has nowhere for it to sit between the start line and the
    // far edge. `coerceIn` throws when its minimum is above its maximum, so this is a return rather
    // than a clamp — the span rule still shows its exact end either way.
    if (minCentre > maxCentre) return

    val centre = Offset(end.coerceIn(minCentre, maxCentre), rule)
    drawCircle(color = accent.copy(alpha = 0.16f), radius = radius, center = centre)
    drawPath(
        path = Path().apply {
            addOval(Rect(centre.x - radius, centre.y - radius, centre.x + radius, centre.y + radius))
        },
        color = accent,
        style = Stroke(width = 2.dp.toPx())
    )
}

private fun DrawScope.drawStartLine(accent: Color, zero: Float) {
    drawLine(
        color = accent,
        start = Offset(zero, 0f),
        end = Offset(zero, size.height),
        strokeWidth = 2.dp.toPx()
    )
    val stop = 6.dp.toPx()
    // `DrawScope` has no triangle primitive, so the stop is a three-point path. One path per frame,
    // not one per tick — the tick loop stays allocation-free apart from the text layouts.
    drawPath(
        path = Path().apply {
            moveTo(zero - stop / 2f, 0f)
            lineTo(zero + stop / 2f, 0f)
            lineTo(zero, stop)
            close()
        },
        color = accent
    )
}

private fun DrawScope.drawMark(accent: Color, mark: Float) {
    drawLine(
        color = accent,
        start = Offset(mark, 0f),
        end = Offset(mark, size.height),
        strokeWidth = 2.dp.toPx()
    )
    val cap = 10.dp.toPx()
    drawLine(
        color = accent,
        start = Offset(mark - cap / 2f, 2.dp.toPx()),
        end = Offset(mark + cap / 2f, 2.dp.toPx()),
        strokeWidth = 2.dp.toPx()
    )
}

private fun DrawScope.drawEdge(
    scene: RulerScene,
    startEdge: StartEdge,
    labelSizePx: Float,
    labelTop: Float,
    baseline: Float,
    textMeasurer: TextMeasurer,
    style: TextStyle,
    colors: RulerColors,
    labelCache: MutableMap<String, TextLayoutResult>
) {
    val zero = startEdge.zeroPixels().toFloat()
    val ticks = scene.build(0L, size.width.toDouble() - startEdge.zeroPixels())

    for (tick in ticks) {
        val x = zero + tick.pixels.toFloat()
        val length = labelSizePx * tickLengthFactor(tick.rank)
        drawLine(
            color = if (tick.rank >= 2) colors.tickMajor.toCompose() else colors.tickMinor.toCompose(),
            start = Offset(x, baseline - length),
            end = Offset(x, baseline),
            strokeWidth = tickStroke(tick.rank)
        )
        val label = tick.label
        if (label != null) {
            val layout = labelCache.getOrPut(label) { textMeasurer.measure(label, style, maxLines = 1) }
            // Skip a label that would be clipped by the right edge instead of wrapping it.
            if (x + 4f + layout.size.width <= size.width) {
                drawText(layout, color = colors.tickMajor.toCompose(), topLeft = Offset(x + 4f, labelTop))
            }
        }
    }
}
