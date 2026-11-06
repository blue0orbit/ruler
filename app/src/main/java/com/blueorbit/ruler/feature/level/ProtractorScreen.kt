package com.blueorbit.ruler.feature.level

import android.content.pm.ActivityInfo
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.blueorbit.ruler.R
import com.blueorbit.ruler.core.di.ViewModelFactory
import com.blueorbit.ruler.core.haptics.DeviceHaptics
import com.blueorbit.ruler.core.settings.AppSettings
import com.blueorbit.ruler.core.ui.DetailScaffold
import com.blueorbit.ruler.core.ui.LocalRulerColors
import com.blueorbit.ruler.core.ui.LocalRulerType
import com.blueorbit.ruler.core.ui.OutlinedChip
import com.blueorbit.ruler.core.ui.RulerDimensions
import com.blueorbit.ruler.core.ui.RulerShapes
import com.blueorbit.ruler.core.ui.RulerSpacing
import com.blueorbit.ruler.core.ui.TABULAR_FONT_FEATURES
import com.blueorbit.ruler.core.ui.capsRegister
import com.blueorbit.ruler.core.ui.motionMillis
import com.blueorbit.ruler.core.ui.readoutEasing
import com.blueorbit.ruler.core.ui.reducedMotion
import com.blueorbit.ruler.core.ui.semantics.announceChanges
import com.blueorbit.ruler.core.ui.toCompose
import kotlinx.coroutines.flow.StateFlow
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Protractor (DESIGN.md §13): a detail screen reached from Tools.
 *
 * It is a live instrument with a two-step hold, not two blind snapshots. Before the first tap the plate
 * follows the phone's tilt, so the user can see it is working. After the first tap the angle to that
 * edge is drawn and read out live, so the user can see where they are before they commit. The second tap
 * freezes it. One primary button advances the steps, so the taps cannot happen out of order.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProtractorScreen(
    factory: ViewModelFactory,
    settings: StateFlow<AppSettings>,
    haptics: DeviceHaptics,
    onBack: () -> Unit,
    onOpenPro: (() -> Unit)? = null
) {
    val vm: ProtractorViewModel = viewModel(factory = factory)
    val state by vm.state.collectAsStateWithLifecycle()
    val appSettings by settings.collectAsStateWithLifecycle()
    val type = LocalRulerType.current
    val colors = LocalRulerColors.current
    val reduced = reducedMotion()

    // Portrait only (DECISIONS rule 8, protractor exception). Measuring means turning the phone, and a
    // screen that rotates under the user mid-turn moves the number and the freeze button away from
    // their thumb. Locked while this screen is shown and handed back on leaving, so every other screen
    // keeps following the device. Android ignores the lock on large screens (sw ≥ 600 dp, targetSdk 36)
    // and in multi-window, which is why the side-by-side layout below is kept for wide windows.
    val activity = LocalActivity.current
    DisposableEffect(activity) {
        val previous = activity?.requestedOrientation
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        onDispose {
            if (activity != null && previous != null) activity.requestedOrientation = previous
        }
    }

    fun confirm() {
        if (appSettings.haptics) haptics.confirm()
    }

    DetailScaffold(
        onBack = onBack,
        title = stringResource(R.string.protractor_title),
        // M19 (DESIGN.md §6): the word "Pro" in the header when the gate is open (SPEC 11).
        trailing = if (state.pro) ({ OutlinedChip(stringResource(R.string.settings_pro_title)) }) else null
    ) {
        if (!state.pro) {
            // Rule 14: the gate names its way out.
            Column(verticalArrangement = Arrangement.spacedBy(RulerSpacing.sm)) {
                Text(
                    stringResource(R.string.level_pro_locked),
                    style = type.bodyMedium,
                    color = colors.onSurfaceSecondary.toCompose()
                )
                if (onOpenPro != null) {
                    Button(onClick = onOpenPro) { Text(stringResource(R.string.settings_pro_open)) }
                }
            }
            return@DetailScaffold
        }

        // A device with no rotation-vector sensor closes the reading flow. A protractor reading of
        // 0.0° for that would be fabricated, so the screen says the sensor is missing (rule 5).
        if (!state.sensorAvailable) {
            Text(
                stringResource(R.string.level_sensor_missing),
                style = type.bodyMedium,
                color = colors.danger.toCompose()
            )
            return@DetailScaffold
        }

        val step = state.step
        val displayed = state.displayedAngleDeg()

        val instruction = @Composable {
            // The instruction for the step the user is on, cross-faded when the step changes so the
            // change itself is noticed. One sentence at a time instead of the whole procedure up front.
            AnimatedContent(
                targetState = step,
                transitionSpec = {
                    fadeIn(tween(motionMillis(180, reduced), easing = readoutEasing)) togetherWith
                        fadeOut(tween(motionMillis(120, reduced)))
                },
                label = "protractorStep"
            ) { target ->
                Column(verticalArrangement = Arrangement.spacedBy(RulerSpacing.xs)) {
                    Text(
                        stringResource(stepCounterRes(target)).capsRegister(),
                        style = type.labelCaps,
                        color = colors.accent.toCompose()
                    )
                    Text(stringResource(stepInstructionRes(target)), style = type.bodyMedium)
                }
            }
        }

        val reading = @Composable {
            // The number: live in the first two steps, held in the last. The caption says which, so a
            // live tilt is never mistaken for a measured angle (rule 5).
            Text(
                stringResource(captionRes(step)).capsRegister(),
                style = type.labelCaps,
                color = colors.onSurfaceSecondary.toCompose()
            )
            val numberText = when {
                !state.hasReading -> stringResource(R.string.protractor_waiting)
                step == ProtractorStep.DONE && !appSettings.simpleMode -> stringResource(
                    R.string.protractor_angle_format,
                    formatNumber(state.angleDeg()),
                    formatNumber(state.grads())
                )
                else -> stringResource(R.string.protractor_angle_simple_format, formatNumber(displayed))
            }
            Text(
                text = numberText,
                style = type.readout,
                color = if (step == ProtractorStep.FIRST_EDGE) {
                    colors.onSurfaceSecondary.toCompose()
                } else {
                    colors.onSurface.toCompose()
                },
                // Only the held reading is announced. A live region on a number that changes fifty times
                // a second would make TalkBack talk without stopping.
                modifier = if (step == ProtractorStep.DONE) Modifier.announceChanges() else Modifier
            )
        }

        val actions = @Composable {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(RulerSpacing.md),
                verticalArrangement = Arrangement.spacedBy(RulerSpacing.sm)
            ) {
                Button(
                    onClick = {
                        vm.capture()
                        confirm()
                    },
                    enabled = state.hasReading
                ) {
                    Text(stringResource(actionRes(step)))
                }
                // Rule 14: "Start over" only exists while there is something to start over from. After a
                // finished reading the primary button already says "Measure again".
                if (step == ProtractorStep.SECOND_EDGE) {
                    TextButton(onClick = { vm.clear() }) {
                        Text(stringResource(R.string.protractor_clear))
                    }
                }
            }
        }

        val accuracy = @Composable {
            Text(
                text = stringResource(
                    R.string.level_accuracy_format,
                    stringResource(accuracyLabelRes(state.accuracy)),
                    formatNumber(state.typicalErrorDeg())
                ),
                style = type.meta,
                color = colors.onSurfaceSecondary.toCompose()
            )
        }

        val angleOnDial = if (state.hasReading) displayed else null

        // The screen is locked to portrait above, but a large screen or a multi-window split can still
        // hand this a window wider than tall. Whatever its shape, the number and the button that freezes
        // it must be on screen without scrolling: in a wide window the dial and the controls sit side by
        // side; in a tall one the controls come first and the dial under them. The reading itself lives in the ViewModel and does not
        // depend on the orientation at all (gravity is measured in the phone's own frame).
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val dialMaxHeight = maxHeight * 0.6f
            if (maxWidth > maxHeight) {
                Row(
                    Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(RulerSpacing.lg)
                ) {
                    ProtractorDial(
                        step = step,
                        angleDeg = angleOnDial,
                        modifier = Modifier
                            .fillMaxHeight()
                            .aspectRatio(1f, matchHeightConstraintsFirst = true)
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(RulerSpacing.sm)
                    ) {
                        instruction()
                        reading()
                        actions()
                        accuracy()
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(RulerSpacing.sm)
                ) {
                    instruction()
                    reading()
                    actions()
                    ProtractorDial(
                        step = step,
                        angleDeg = angleOnDial,
                        modifier = Modifier
                            .fillMaxWidth()
                            // Capped so on a tall narrow window the dial cannot outgrow the screen.
                            .heightIn(max = dialMaxHeight)
                            .aspectRatio(1f)
                    )
                    accuracy()
                }
            }
        }
    }
}

private fun stepCounterRes(step: ProtractorStep): Int = when (step) {
    ProtractorStep.FIRST_EDGE -> R.string.protractor_step_1_of_2
    ProtractorStep.SECOND_EDGE -> R.string.protractor_step_2_of_2
    ProtractorStep.DONE -> R.string.protractor_step_done
}

private fun stepInstructionRes(step: ProtractorStep): Int = when (step) {
    ProtractorStep.FIRST_EDGE -> R.string.protractor_hint
    ProtractorStep.SECOND_EDGE -> R.string.protractor_hint_second
    ProtractorStep.DONE -> R.string.protractor_hint_done
}

private fun captionRes(step: ProtractorStep): Int = when (step) {
    ProtractorStep.FIRST_EDGE -> R.string.protractor_caption_tilt
    ProtractorStep.SECOND_EDGE -> R.string.protractor_caption_live
    ProtractorStep.DONE -> R.string.protractor_angle_caption
}

private fun actionRes(step: ProtractorStep): Int = when (step) {
    ProtractorStep.FIRST_EDGE -> R.string.protractor_first_reference
    ProtractorStep.SECOND_EDGE -> R.string.protractor_second_reference
    ProtractorStep.DONE -> R.string.protractor_measure_again
}

private fun formatNumber(value: Double): String {
    val format = NumberFormat.getInstance(Locale.getDefault())
    format.maximumFractionDigits = 1
    format.minimumFractionDigits = 1
    return format.format(value)
}

/**
 * The protractor dial (DESIGN.md §13): `surfaceElevated` with a hairline, a datum ray to the right, a
 * full 0-360° scale (5° minor, 15° mid, 30° numbered), and the angle ray.
 *
 * A full circle rather than a half: the turn is followed continuously, so a phone turned past 180° has
 * somewhere to point instead of folding back. The datum is the first edge. Before it is set the ray is
 * the live tilt from flat, dashed, so it reads as "the instrument is listening" rather than as a
 * reading; between the taps the ray and wedge are live; after the second tap they are held.
 */
@Composable
private fun ProtractorDial(step: ProtractorStep, angleDeg: Double?, modifier: Modifier = Modifier) {
    val colors = LocalRulerColors.current
    val tickMinor = colors.tickMinor.toCompose()
    val tickMajor = colors.tickMajor.toCompose()
    val accent = colors.accent.toCompose()
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontSize = 11.sp, color = tickMajor, fontFeatureSettings = TABULAR_FONT_FEATURES)
    val reduced = reducedMotion()

    // The held state is an emphasis change, not a jump: the arc thickens and the ray turns solid over a
    // short fade, so the second tap is visibly "caught" on the dial.
    val held by animateFloatAsState(
        targetValue = if (step == ProtractorStep.DONE) 1f else 0f,
        animationSpec = tween(motionMillis(220, reduced), easing = readoutEasing),
        label = "held"
    )
    val description = stringResource(R.string.protractor_plate_description)

    // The caller always gives this a definite size: a `heightIn(min)` canvas inside a scrolling column
    // measured to zero height and drew the scale upside down (M20).
    Canvas(
        modifier
            .clip(RulerShapes.plate)
            .background(colors.surfaceElevated.toCompose(), RulerShapes.plate)
            .border(RulerDimensions.hairline, colors.outline.toCompose(), RulerShapes.plate)
            .semantics { contentDescription = description }
    ) {
        val labelRoom = 22.dp.toPx()
        val cx = size.width / 2f
        val cy = size.height / 2f
        val radius = min(size.width, size.height) / 2f - labelRoom - 6.dp.toPx()
        if (radius <= 0f) return@Canvas

        drawCircle(tickMinor, radius = radius, center = Offset(cx, cy), style = Stroke(width = 1.dp.toPx()))

        var deg = 0
        while (deg < 360) {
            val r = Math.toRadians(deg.toDouble())
            val length = when {
                deg % 30 == 0 -> 12.dp.toPx()
                deg % 15 == 0 -> 9.dp.toPx()
                else -> 5.dp.toPx()
            }
            val c = cos(r).toFloat()
            val s = sin(r).toFloat()
            drawLine(
                if (deg % 15 == 0) tickMajor else tickMinor,
                Offset(cx + (radius - length) * c, cy - (radius - length) * s),
                Offset(cx + radius * c, cy - radius * s),
                if (deg % 30 == 0) 1.5.dp.toPx() else 1.dp.toPx()
            )
            if (deg % 30 == 0) {
                val layout = textMeasurer.measure(deg.toString(), labelStyle, maxLines = 1)
                val labelR = radius + 4.dp.toPx() + layout.size.width / 2f
                drawText(
                    layout,
                    topLeft = Offset(
                        cx + labelR * c - layout.size.width / 2f,
                        cy - labelR * s - layout.size.height / 2f
                    )
                )
            }
            deg += 5
        }

        // Datum: the first edge, from the centre to 0°.
        drawLine(tickMajor, Offset(cx, cy), Offset(cx + radius, cy), 2.dp.toPx())

        if (angleDeg != null) {
            val shown = ((angleDeg % 360.0) + 360.0) % 360.0
            if (step == ProtractorStep.FIRST_EDGE) {
                drawRay(tickMajor.copy(alpha = 0.55f), cx, cy, radius, shown, dashed = true)
            } else {
                val arcR = radius * 0.62f
                drawArc(
                    color = accent.copy(alpha = 0.18f + 0.12f * held),
                    startAngle = (-shown).toFloat(),
                    sweepAngle = shown.toFloat(),
                    useCenter = true,
                    topLeft = Offset(cx - arcR, cy - arcR),
                    size = Size(arcR * 2f, arcR * 2f)
                )
                drawArc(
                    color = accent,
                    startAngle = (-shown).toFloat(),
                    sweepAngle = shown.toFloat(),
                    useCenter = false,
                    topLeft = Offset(cx - arcR, cy - arcR),
                    size = Size(arcR * 2f, arcR * 2f),
                    style = Stroke(width = (2f + 2f * held).dp.toPx(), cap = StrokeCap.Round)
                )
                drawRay(if (held > 0.5f) tickMajor else accent, cx, cy, radius, shown, dashed = false)
            }
        }

        drawCircle(tickMajor, radius = 4.dp.toPx(), center = Offset(cx, cy))
    }
}

private fun DrawScope.drawRay(color: Color, cx: Float, cy: Float, radius: Float, deg: Double, dashed: Boolean) {
    val r = Math.toRadians(deg)
    drawLine(
        color = color,
        start = Offset(cx, cy),
        end = Offset(cx + radius * cos(r).toFloat(), cy - radius * sin(r).toFloat()),
        strokeWidth = 2.dp.toPx(),
        cap = StrokeCap.Round,
        pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 6.dp.toPx())) else null
    )
}
