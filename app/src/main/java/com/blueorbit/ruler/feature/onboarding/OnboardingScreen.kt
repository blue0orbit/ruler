package com.blueorbit.ruler.feature.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.graphics.lerp
import com.blueorbit.ruler.R
import com.blueorbit.ruler.core.ui.LocalRulerColors
import com.blueorbit.ruler.core.ui.MOTION_SWAP_MS
import com.blueorbit.ruler.core.ui.motionMillis
import com.blueorbit.ruler.core.ui.reducedMotion
import com.blueorbit.ruler.core.ui.LocalRulerType
import com.blueorbit.ruler.core.ui.RulerDimensions
import com.blueorbit.ruler.core.ui.RulerShapes
import com.blueorbit.ruler.core.ui.RulerSpacing
import com.blueorbit.ruler.core.ui.WordmarkRow
import com.blueorbit.ruler.core.ui.capsRegister
import com.blueorbit.ruler.core.ui.screenFrame
import com.blueorbit.ruler.core.ui.toCompose
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Onboarding (DESIGN.md §8): three pages, no dialogs, no permission prompts, closable at any point.
 *
 * M17: the pages **instruct** — lay the object down, make the line as long as it is, read the number —
 * instead of describing the product. There is no page about error budgets and no route to calibration:
 * a user who has not measured anything yet cannot be asked to make the ruler exact before they have a
 * reason to want it. The unverified state is still on the ruler screen (rule 5), where the measurement
 * they have just made is the thing it qualifies.
 *
 * [onFinish] both records that the intro was seen and leaves it. The record is not made here: this
 * screen is popped in the same frame, and a save launched from its own ViewModel was cancelled with it
 * (the intro then returned on the next launch). The shell saves it on the application scope.
 */
@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    val type = LocalRulerType.current
    val colors = LocalRulerColors.current
    val scope = rememberCoroutineScope()
    val reduced = reducedMotion()

    val pageCount = 3
    val pagerState = rememberPagerState(pageCount = { pageCount })
    val lastPage = pagerState.currentPage == pageCount - 1

    // One finish per visit: a double tap on Start must not pop twice.
    var finished by remember { mutableStateOf(false) }
    fun finish() {
        if (finished) return
        finished = true
        onFinish()
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .screenFrame()
    ) {
        // Rule 8: the intro has to work in landscape on a phone too. There the viewport is ~346 dp tall,
        // and the identity row plus the full illustration pushed the Next control below the fold — the
        // one control this screen exists to be pressed. Below that height the illustration and the gaps
        // shrink so the control is on screen.
        val shortViewport = maxHeight < 520.dp
        val artHeight = if (shortViewport) 110.dp else 170.dp
        val gap = if (shortViewport) RulerSpacing.md else RulerSpacing.lg

        Column(Modifier.fillMaxSize()) {
            // M19 (DESIGN.md §6, design-concept/ onboarding): the intro opens with the same identity row
            // the bar screens use, pinned to the top of the frame like theirs, and the way out of the
            // intro sits in that row rather than beside the Next control — "closable at any point" (§8)
            // is easier to see when it is always in the same place. Rule 14: on the last page the Start
            // button below does exactly what Skip would do, so Skip is not shown.
            WordmarkRow(
                modifier = Modifier.fillMaxWidth(),
                trailing = if (!lastPage) {
                    ({
                        TextButton(onClick = { finish() }) {
                            Text(
                                stringResource(R.string.onboarding_skip).capsRegister(),
                                style = type.labelCaps
                            )
                        }
                    })
                } else {
                    null
                }
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                // Spaced *and* centred in the space the identity row leaves: the first build put three
                // short paragraphs at the top of a tall screen and left the whole lower half empty, which
                // reads as an unfinished screen rather than a short intro. `weight(1f)` before
                // `verticalScroll` keeps the column viewport-height, so the centring applies when the
                // content is shorter than the space and the scroll still carries a large system font
                // scale.
                verticalArrangement = Arrangement.spacedBy(gap, Alignment.CenterVertically)
            ) {
                HorizontalPager(state = pagerState, modifier = Modifier.fillMaxWidth()) { page ->
                    val titles = intArrayOf(
                        R.string.onboarding_page1_title,
                        R.string.onboarding_page2_title,
                        R.string.onboarding_page3_title
                    )
                    val bodies = intArrayOf(
                        R.string.onboarding_page1_body,
                        R.string.onboarding_page2_body,
                        R.string.onboarding_page3_body
                    )

                    Column(Modifier.fillMaxWidth()) {
                        // Each page shows the thing it is talking about, drawn with the app's own tokens.
                        // The first build of this screen was three paragraphs of text at the top of an
                        // empty screen, which is the weakest first impression the product can have;
                        // DESIGN.md §9 rules illustrations out for *empty states*, not for the intro.
                        OnboardingArt(page, artHeight)
                        Text(
                            stringResource(titles[page]),
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.padding(top = gap)
                        )
                        Text(
                            stringResource(bodies[page]),
                            style = type.bodyLarge,
                            color = colors.onSurface.toCompose(),
                            modifier = Modifier.padding(top = RulerSpacing.sm)
                        )
                    }
                }

                // The step is written as text as well as shown as dots, so the position is readable
                // without relying on which dot is coloured (SPEC 11).
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.onboarding_step_format, pagerState.currentPage + 1, pageCount),
                        style = type.meta,
                        color = colors.onSurfaceSecondary.toCompose()
                    )
                    // The dots follow the finger, not the settled page: each one widens and inks in by
                    // how close the pager is to it, so a half-swiped page shows two half-lit dots. This
                    // is direct manipulation rather than an animation, so it stays on with reduced
                    // motion (the pager itself still moves with the finger then too). The offset is read
                    // in the draw phase only, so a swipe redraws three dots instead of recomposing the
                    // screen on every frame.
                    val dim = colors.outline.toCompose()
                    val lit = colors.onSurface.toCompose()
                    val dot = 6.dp
                    val wide = 18.dp
                    Canvas(
                        Modifier
                            .padding(start = RulerSpacing.sm)
                            .height(dot)
                            .width(wide + dot * (pageCount - 1) + RulerSpacing.sm * (pageCount - 1))
                    ) {
                        val position = pagerState.currentPage + pagerState.currentPageOffsetFraction
                        val gap = RulerSpacing.sm.toPx()
                        var x = 0f
                        repeat(pageCount) { index ->
                            val nearness = (1f - abs(position - index)).coerceIn(0f, 1f)
                            val w = lerp(dot, wide, nearness).toPx()
                            drawRoundRect(
                                color = lerp(dim, lit, nearness),
                                topLeft = Offset(x, 0f),
                                size = Size(w, size.height),
                                cornerRadius = CornerRadius(size.height / 2f)
                            )
                            x += w + gap
                        }
                    }
                }

                // Rule 14: on the last page "Start measuring" and "Skip intro" would do exactly the same
                // thing, so only one control is shown. On the other two pages the way out is the Skip
                // control in the identity row above (M19), which is why it is not repeated here. The
                // swap is a short cross-fade so the label change reads as the same control changing
                // job, not as a new control appearing (Motion.kt).
                AnimatedContent(
                    targetState = lastPage,
                    transitionSpec = {
                        fadeIn(tween(motionMillis(MOTION_SWAP_MS, reduced))) togetherWith
                            fadeOut(tween(motionMillis(MOTION_SWAP_MS, reduced)))
                    },
                    label = "onboardingPrimary"
                ) { onLastPage ->
                    if (onLastPage) {
                        Button(onClick = { finish() }, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.onboarding_start))
                        }
                    } else {
                        Button(
                            onClick = {
                                scope.launch {
                                    val next = pagerState.currentPage + 1
                                    if (reduced) pagerState.scrollToPage(next) else pagerState.animateScrollToPage(next)
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.onboarding_next))
                        }
                    }
                }
            }
        }
    }
}

/** The object being measured, drawn as an outline so it cannot be read as a filled measurement. */
private fun DrawScope.drawObject(left: Float, right: Float, baseY: Float, height: Float, ink: Color) {
    drawRoundRect(
        color = ink,
        topLeft = Offset(left, baseY - height),
        size = Size(right - left, height),
        cornerRadius = CornerRadius(8.dp.toPx()),
        style = Stroke(width = 2.dp.toPx())
    )
}

/** The start line and its zero stop — the edge an object is laid against. */
private fun DrawScope.drawStartLine(accent: Color, x: Float, top: Float, bottom: Float) {
    drawLine(accent, Offset(x, top), Offset(x, bottom), 2.dp.toPx())
    val stop = 6.dp.toPx()
    drawPath(
        path = Path().apply {
            moveTo(x - stop / 2f, top)
            lineTo(x + stop / 2f, top)
            lineTo(x, top + stop)
            close()
        },
        color = accent
    )
}

/** The knob at the end of the drawn span: the same shape the ruler plate draws (M17). */
private fun DrawScope.drawKnob(accent: Color, end: Float, rule: Float) {
    val radius = 13.dp.toPx()
    val minCentre = radius
    val maxCentre = size.width - radius
    // Same guard as the ruler plate's span handle: `coerceIn` throws when its minimum is above its
    // maximum, so a plate narrower than the knob itself has nowhere to put it rather than a crash.
    if (minCentre > maxCentre) return
    val centre = Offset(end.coerceIn(minCentre, maxCentre), rule)
    drawCircle(color = accent.copy(alpha = 0.16f), radius = radius, center = centre)
    drawPath(
        path = Path().apply { addOval(Rect(centre.x - radius, centre.y - radius, centre.x + radius, centre.y + radius)) },
        color = accent,
        style = Stroke(width = 2.dp.toPx())
    )
}

/**
 * The drawing above each intro page, on a plate, using the same tokens and the same rank hierarchy the
 * ruler plate uses: page 1 the instrument and its start line, page 2 a span with its uncertainty drawn
 * as part of the span, page 3 a card edge lined up on the start line.
 *
 * Nothing here is a measurement. It carries no numbers and no claims — it is the shape of the idea, so
 * a first-run user is not reading three paragraphs about a thing they have not seen.
 */
@Composable
private fun OnboardingArt(page: Int, height: Dp) {
    val colors = LocalRulerColors.current
    val ink = colors.tickMajor.toCompose()
    val quiet = colors.tickMinor.toCompose()
    val accent = colors.accent.toCompose()

    Box(
        Modifier
            .fillMaxWidth()
            .height(height)
            .clip(RulerShapes.plate)
            .background(colors.surfaceElevated.toCompose(), RulerShapes.plate)
            .border(RulerDimensions.hairline, colors.outlineVariant.toCompose(), RulerShapes.plate)
    ) {
        Canvas(Modifier.fillMaxSize()) {
            // The drawing is composed for the 170 dp plate it normally gets and scaled with the plate's
            // own height (Rule 8: a landscape phone gives the intro a shorter plate, not a clipped
            // drawing). The knob keeps its fixed size: it is the same knob the ruler plate draws.
            val s = size.height / 170.dp.toPx()
            fun dp(value: Int) = value.dp.toPx() * s

            val pad = dp(22)
            val baseY = size.height - pad
            val left = pad
            val right = size.width - pad
            // Pages 1 and 3 put an object on the plate, so its far edge has to be known before the ticks
            // are drawn: the ticks stop at the object and continue past it, which is what "the object lies
            // on the ruler" looks like. Drawing them through the object made the object look ticked.
            val objectRight = maxOf(left + dp(20), right - dp(40))
                .coerceAtMost(right)
                // A plate narrower than the object itself has no room for it: the far edge is never allowed
                // behind the near one, or the object would be drawn with a negative width.
                .coerceAtLeast(left)
            val objectOnPlate = page == 0 || page == 2

            // Ticks along the bottom edge, in the plate's four-rank hierarchy.
            val step = dp(20)
            var x = left
            var i = 0
            while (x <= right) {
                val insideObject = objectOnPlate && x <= objectRight
                if (!insideObject) {
                    val rank = i % 4
                    val h = when (rank) {
                        0 -> dp(44)
                        2 -> dp(34)
                        else -> dp(24)
                    }
                    drawLine(
                        color = if (rank == 0) ink else quiet,
                        start = Offset(x, baseY - h),
                        end = Offset(x, baseY),
                        strokeWidth = if (rank == 0) 2.dp.toPx() else 1.4.dp.toPx()
                    )
                }
                x += step
                i++
            }

            when (page) {
                0 -> {
                    // Page 1: the object laid on the plate with its left end on the start line, which is
                    // the first thing the ruler screen asks for.
                    drawObject(left, objectRight, baseY, dp(58), ink)
                    drawStartLine(accent, left, pad, baseY)
                }

                1 -> {
                    // Page 2: the drawn span and the knob at its end — the same knob the plate draws, and
                    // the same thing the slider under the plate moves.
                    val rule = baseY - dp(58)
                    val from = left + dp(10)
                    val to = maxOf(from + dp(20), right - dp(70)).coerceAtMost(right)
                    drawLine(accent, Offset(from, rule), Offset(to, rule), 3.dp.toPx())
                    drawKnob(accent, to, rule)
                }

                else -> {
                    // Page 3: the span made exactly as long as the object. This is the action finished,
                    // not a claim about what it measures.
                    val objectTop = baseY - dp(74)
                    drawObject(left, objectRight, baseY, dp(74), ink)
                    val rule = objectTop + dp(22)
                    drawLine(accent, Offset(left, rule), Offset(objectRight, rule), 3.dp.toPx())
                    drawKnob(accent, objectRight, rule)
                    drawStartLine(accent, left, objectTop - dp(14), baseY)
                }
            }
        }
    }
}
