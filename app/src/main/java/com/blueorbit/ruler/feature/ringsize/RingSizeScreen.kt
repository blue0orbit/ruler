package com.blueorbit.ruler.feature.ringsize

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.blueorbit.ruler.R
import com.blueorbit.ruler.core.di.ViewModelFactory
import com.blueorbit.ruler.core.geometry.FormatMode
import com.blueorbit.ruler.core.geometry.Formatter
import com.blueorbit.ruler.core.geometry.MeasurementUnit
import com.blueorbit.ruler.core.geometry.Physical
import com.blueorbit.ruler.core.geometry.PixelPerMm
import com.blueorbit.ruler.core.geometry.RingSizeMatch
import com.blueorbit.ruler.core.geometry.RingSizeSystem
import com.blueorbit.ruler.core.geometry.RulerScene
import com.blueorbit.ruler.core.haptics.DeviceHaptics
import com.blueorbit.ruler.core.haptics.Haptics
import com.blueorbit.ruler.core.settings.AppSettings
import com.blueorbit.ruler.core.ui.DetailScaffold
import com.blueorbit.ruler.core.ui.LocalAppMessages
import com.blueorbit.ruler.core.ui.LocalRulerColors
import com.blueorbit.ruler.core.ui.LocalRulerType
import com.blueorbit.ruler.core.ui.RulerCard
import com.blueorbit.ruler.core.ui.RulerDimensions
import com.blueorbit.ruler.core.ui.RulerShapes
import com.blueorbit.ruler.core.ui.RulerSpacing
import com.blueorbit.ruler.core.ui.SectionHeader
import com.blueorbit.ruler.core.ui.TABULAR_FONT_FEATURES
import com.blueorbit.ruler.core.ui.capsRegister
import com.blueorbit.ruler.core.ui.motionMillis
import com.blueorbit.ruler.core.ui.reducedMotion
import com.blueorbit.ruler.core.ui.say
import com.blueorbit.ruler.core.ui.semantics.announceChanges
import com.blueorbit.ruler.core.ui.semantics.labelled
import com.blueorbit.ruler.core.ui.tabular
import com.blueorbit.ruler.core.ui.toCompose
import com.blueorbit.ruler.core.ui.unitLabelRes
import com.blueorbit.ruler.core.ui.useNavigationRail
import com.blueorbit.ruler.feature.ruler.RulerPlate
import com.blueorbit.ruler.feature.ruler.StartEdge
import com.blueorbit.ruler.feature.ruler.rememberStartEdgeLabel
import com.blueorbit.ruler.feature.ruler.spanDrag
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToLong

/**
 * Ring size (ROADMAP M21): a free detail screen reached from Tools.
 *
 * Two ways to measure one thing — the inside of a ring — and one table of published sizes. The plate
 * is the instrument: in the ring method it draws a circle the user sizes against their ring, in the
 * paper method it *is* the ruler plate the strip is laid on. Both write the same state field, so the
 * drawing, the millimetres, the four sizes and the saved log row cannot disagree (rule 5).
 *
 * The accuracy gate is the reason this screen is honest rather than a lookup app: one EU size is
 * 0.32 mm of diameter, and the unverified screen estimate is worth about three sizes on a ring that
 * size. Uncalibrated, the screen says so first and names only a range (rule 5, rule 14).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RingSizeScreen(
    factory: ViewModelFactory,
    settings: StateFlow<AppSettings>,
    haptics: DeviceHaptics,
    onBack: () -> Unit,
    onOpenCalibration: () -> Unit
) {
    val vm: RingSizeViewModel = viewModel(factory = factory)
    val state by vm.state.collectAsStateWithLifecycle()
    val appSettings by settings.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.refresh() }

    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    // A phone on its side is too short for a circle plate above the size table (rule 8), so the split
    // follows the window rather than only its width — the same rule the level screen uses.
    val twoPane = useNavigationRail(configuration.screenWidthDp) ||
        configuration.screenWidthDp > configuration.screenHeightDp

    // Labels scale independently of the plate's geometry (rule 1): this is the only place the stored
    // font scale reaches a drawn label, and `pxPerMm` below never sees it.
    val labelSizePx = with(density) { 14.sp.toPx() } * appSettings.labelFontScale.toFloat()
    val pxPerMm = PixelPerMm(state.calibration.pixelsPerMm)
    val startEdge = StartEdge(state.calibration.bezelInsetMm, pxPerMm)

    // A finger on the plate moves the drawing with no easing; the easing is for the changes the user
    // did not just cause (a ± button, a slider step, a rotation).
    var dragging by remember { mutableStateOf(false) }
    val drawn = vm.drawableMicrometres
    val claimed = vm.drawableIsClaimed

    // Haptics: a tick per size step (ROADMAP M21). Keyed on the length the user set rather than the
    // length this window can draw, so a rotation cannot fire a tick nobody caused — the ruler screen's
    // rule, and the reason `drawn` above and `state.drawnMicrometres` here are different values.
    var previous by remember { mutableLongStateOf(state.drawnMicrometres) }
    LaunchedEffect(state.drawnMicrometres) {
        if (Haptics.shouldFire(appSettings.haptics, previous, state.drawnMicrometres, vm.rungStepMicrometres())) {
            haptics.tick()
        }
        previous = state.drawnMicrometres
    }

    DetailScaffold(
        onBack = onBack,
        title = stringResource(R.string.ring_title)
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val paneWidth = if (twoPane) {
                maxWidth - RulerDimensions.readoutPaneWidth - RulerSpacing.xl
            } else {
                maxWidth
            }
            // The circle is an instrument, not a fill: it is capped rather than stretched across a wide
            // window (DESIGN.md §6), and it only has to be big enough to draw the biggest size the
            // published charts name. A plate that could draw a 100 mm circle for a chart that stops at
            // 26 mm is a plate that shows more empty grey than instrument (seen on the Fold at M21).
            val neededPx = pxPerMm.mmToPixels(LARGEST_PUBLISHED_DIAMETER_MM) + 2.0 * labelSizePx * PLATE_INK_FACTOR
            val availablePx = minOf(
                with(density) { paneWidth.toPx() },
                with(density) { RulerDimensions.instrumentMaxSize.toPx() },
                if (twoPane) with(density) { maxHeight.toPx() } else with(density) { (maxHeight * 0.45f).toPx() }
            ).toDouble()
            // `Density.toDp()` already divides by the density, so the pixel figure becomes dp once, here.
            val circleSide = with(density) { min(neededPx, availablePx).toFloat().toDp() }
            val maxMicrometres = plateLimit(state.mode, circleSide, paneWidth, labelSizePx, pxPerMm, startEdge, density)
            LaunchedEffect(maxMicrometres) { vm.setPlateLimit(maxMicrometres) }

            val intro = @Composable {
                RingIntro(vm, state, appSettings, onOpenCalibration)
            }
            val instrument = @Composable {
                RingInstrument(
                    state = state,
                    drawn = drawn,
                    circleSide = circleSide,
                    paneWidth = paneWidth,
                    labelSizePx = labelSizePx,
                    pxPerMm = pxPerMm,
                    startEdge = startEdge,
                    animate = !dragging,
                    simpleMode = appSettings.simpleMode,
                    onDragging = { dragging = it },
                    onRadiusPixels = { radiusPx ->
                        // A finger's distance from the plate centre is the *radius*; the state field holds
                        // the diameter, so the doubling happens here, once, in the same frame the plate
                        // draws in.
                        vm.setDrawn(radiusToMicrometres(radiusPx, pxPerMm), maxMicrometres)
                    },
                    onSpanEndPixels = { x -> vm.setDrawn(startEdge.measureAt(x.toDouble()), maxMicrometres) }
                )
            }
            val results = @Composable {
                RingResults(
                    vm = vm,
                    state = state,
                    drawn = drawn,
                    claimed = claimed,
                    maxMicrometres = maxMicrometres,
                    appSettings = appSettings,
                    haptics = haptics
                )
            }

            if (twoPane) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(RulerSpacing.xl),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) { instrument() }
                    // Scrolls for the same reason as the level screen: at a large font scale the reading
                    // is taller than a short window, and Save has to stay reachable (rule 14).
                    Column(
                        modifier = Modifier
                            .width(RulerDimensions.readoutPaneWidth)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(RulerSpacing.sm)
                    ) {
                        intro()
                        results()
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(RulerSpacing.sm)
                ) {
                    intro()
                    instrument()
                    results()
                }
            }
        }
    }
}

/** The method choice, what to physically do, and — before calibration — the way out (rule 14). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RingIntro(
    vm: RingSizeViewModel,
    state: RingSizeState,
    appSettings: AppSettings,
    onOpenCalibration: () -> Unit
) {
    val type = LocalRulerType.current
    val colors = LocalRulerColors.current

    Text(
        stringResource(R.string.ring_body),
        style = type.bodyMedium,
        color = colors.onSurfaceSecondary.toCompose()
    )

    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        RingMeasureMode.entries.forEachIndexed { index, mode ->
            SegmentedButton(
                selected = state.mode == mode,
                onClick = { vm.setMode(mode) },
                shape = RulerShapes.chip
            ) {
                Text(
                    stringResource(
                        if (mode == RingMeasureMode.RING) R.string.ring_method_ring else R.string.ring_method_finger
                    )
                )
            }
        }
    }

    Text(
        stringResource(
            if (state.mode == RingMeasureMode.RING) R.string.ring_method_ring_hint else R.string.ring_method_finger_hint
        ),
        style = type.bodyMedium,
        color = colors.onSurfaceSecondary.toCompose()
    )

    // The accuracy gate leads the screen (ROADMAP M21): on a ring, the unverified estimate is worth
    // about three sizes, so naming a size before the ruler is exact would be a made-up reading.
    if (!state.calibration.verified) {
        Column(verticalArrangement = Arrangement.spacedBy(RulerSpacing.xs)) {
            Text(
                stringResource(R.string.ring_needs_calibration),
                style = type.bodyMedium,
                color = colors.onSurface.toCompose()
            )
            TextButton(onClick = onOpenCalibration) {
                Text(
                    stringResource(
                        if (appSettings.simpleMode) R.string.action_make_exact else R.string.action_calibrate
                    )
                )
            }
        }
    }
}

/**
 * The plate. RING mode draws a circle; FINGER mode draws the ruler plate the strip is laid on, which is
 * the same instrument the ruler screen uses — same ink, same start line, same knob.
 */
@Composable
private fun RingInstrument(
    state: RingSizeState,
    drawn: Long,
    circleSide: Dp,
    paneWidth: Dp,
    labelSizePx: Float,
    pxPerMm: PixelPerMm,
    startEdge: StartEdge,
    animate: Boolean,
    simpleMode: Boolean,
    onDragging: (Boolean) -> Unit,
    onRadiusPixels: (Float) -> Unit,
    onSpanEndPixels: (Float) -> Unit
) {
    if (state.mode == RingMeasureMode.RING) {
        RingCirclePlate(
            side = circleSide,
            diameterMicrometres = drawn,
            pxPerMm = pxPerMm,
            labelSizePx = labelSizePx,
            label = Formatter.numeric(drawn, MeasurementUnit.MM, FormatMode.DECIMAL),
            animate = animate,
            description = stringResource(R.string.ring_circle_plate_description),
            onRadiusPixels = onRadiusPixels,
            onDragging = onDragging
        )
        return
    }

    val startLabel = rememberStartEdgeLabel(simpleMode, state.calibration.bezelInsetMm)
    val plateDescription = stringResource(R.string.ring_span_plate_description)
    val density = LocalDensity.current
    RulerPlate(
        primary = RulerScene(pxPerMm, MeasurementUnit.MM, labelSizePx.toDouble(), FormatMode.DECIMAL),
        secondary = RulerScene(pxPerMm, MeasurementUnit.INCH, labelSizePx.toDouble(), FormatMode.FRACTION),
        startEdge = startEdge,
        labelSizePx = labelSizePx,
        startLabel = startLabel,
        // The strip is measured from the start line, exactly as an object is on the ruler screen.
        spanEndPixels = if (drawn > 0L) startEdge.zeroPixels() + pxPerMm.micrometresToPixels(drawn) else -1.0,
        showSpanHandle = drawn > 0L,
        animateSpan = animate,
        modifier = Modifier
            .fillMaxWidth()
            .spanDrag(onSpanEnd = { x -> onSpanEndPixels(x) }, onDragging = onDragging)
            .semantics { contentDescription = plateDescription }
    )
    // The paper method has a limit this screen cannot go past, and the limit is stated with the way
    // round it rather than the line simply stopping (rule 14).
    val spanMm = with(density) { paneWidth.toPx() } / pxPerMm.pixelsPerMm
    if (pxPerMm.pixelsPerMm > 0.0 && spanMm < LARGEST_PUBLISHED_CIRCUMFERENCE_MM) {
        Text(
            stringResource(
                R.string.ring_span_too_short_format,
                formatNumber(spanMm),
                floor(spanMm).toLong().toString()
            ),
            style = LocalRulerType.current.meta,
            color = LocalRulerColors.current.onSurfaceSecondary.toCompose()
        )
    }
}

/**
 * The circle plate: a datum crosshair the ring is centred on, the drawn circle in the accent ink with
 * its inside filled faintly (so "the inside of the ring" is a shape, not a line — SPEC 11), and the
 * diameter engraved across it as a dimension with end caps.
 *
 * Only the circle moves, and only its width (DESIGN.md §10). The datum, the padding and the stroke are
 * ink; nothing here touches `pxPerMm` (rule 1).
 */
@Composable
private fun RingCirclePlate(
    side: Dp,
    diameterMicrometres: Long,
    pxPerMm: PixelPerMm,
    labelSizePx: Float,
    label: String,
    animate: Boolean,
    description: String,
    onRadiusPixels: (Float) -> Unit,
    onDragging: (Boolean) -> Unit
) {
    val colors = LocalRulerColors.current
    val density = LocalDensity.current
    val reduced = reducedMotion()
    val textMeasurer = rememberTextMeasurer()
    // `labelSizePx` is a pixel measurement, so it is converted back to sp before Compose draws it
    // (M11R finding 8: the raw number as sp scaled every glyph a second time by the display density).
    val labelFontSize = with(density) { labelSizePx.toSp() }

    val targetPx = if (pxPerMm.pixelsPerMm > 0.0) {
        pxPerMm.micrometresToPixels(diameterMicrometres).toFloat()
    } else {
        0f
    }
    val diameterPx by animateFloatAsState(
        targetValue = targetPx,
        animationSpec = if (animate) tween(motionMillis(160, reduced)) else snap(),
        label = "ringCircle"
    )

    Box(
        Modifier
            .size(side)
            .clip(RulerShapes.plate)
            .background(colors.surfaceElevated.toCompose(), RulerShapes.plate)
            .border(RulerDimensions.plateEdge, colors.outlineVariant.toCompose(), RulerShapes.plate)
            .ringCircleDrag(onRadiusPixels, onDragging)
            // The plate is where the physical alignment happens and a screen reader cannot see any of
            // it, so this says what the plate is for and stays deliberately static: a description that
            // changed with the circle would re-announce on every step of a drag.
            .semantics { contentDescription = description }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val centre = Offset(size.width / 2f, size.height / 2f)
            val quiet = colors.tickMinor.toCompose()
            val ink = colors.tickMajor.toCompose()
            val accent = colors.accent.toCompose()
            val hairline = 1.dp.toPx()
            val pad = labelSizePx * PLATE_INK_FACTOR
            val maxRadius = (min(size.width, size.height) / 2f - pad).coerceAtLeast(0f)

            // The datum: two hairlines through the centre, so "the middle of the plate" is something the
            // ring can be lined up on rather than a point the user has to guess at.
            drawLine(quiet, Offset(0f, centre.y), Offset(size.width, centre.y), hairline)
            drawLine(quiet, Offset(centre.x, 0f), Offset(centre.x, size.height), hairline)

            if (diameterPx > 0f) {
                val radius = min(diameterPx / 2f, maxRadius)
                drawCircle(accent.copy(alpha = 0.10f), radius = radius, center = centre)
                drawCircle(accent, radius = radius, center = centre, style = Stroke(width = 2.dp.toPx()))
                drawDimension(ink, centre, radius, labelSizePx)

                val layout = textMeasurer.measure(
                    label,
                    TextStyle(fontSize = labelFontSize, color = ink, fontFeatureSettings = TABULAR_FONT_FEATURES),
                    maxLines = 1
                )
                val left = (centre.x - layout.size.width / 2f).coerceIn(pad, (size.width - pad - layout.size.width).coerceAtLeast(pad))
                val top = (centre.y - labelSizePx * 1.2f).coerceAtLeast(pad)
                drawText(layout, color = ink, topLeft = Offset(left, top))
            }
        }
    }
}

/** The diameter as an engineering dimension: a rule across the circle with a cap at each end. */
private fun DrawScope.drawDimension(ink: Color, centre: Offset, radius: Float, labelSizePx: Float) {
    val cap = labelSizePx * 0.5f
    drawLine(ink, Offset(centre.x - radius, centre.y), Offset(centre.x + radius, centre.y), 1.5.dp.toPx())
    drawLine(ink, Offset(centre.x - radius, centre.y - cap / 2f), Offset(centre.x - radius, centre.y + cap / 2f), 1.dp.toPx())
    drawLine(ink, Offset(centre.x + radius, centre.y - cap / 2f), Offset(centre.x + radius, centre.y + cap / 2f), 1.dp.toPx())
}

/**
 * A finger on the plate sizes the circle: a tap puts the radius where the finger landed, a drag follows
 * the finger. Only horizontal drags are claimed, so a vertical scroll that starts on the plate still
 * scrolls the screen — the same rule the ruler plate follows, and the reason the circle is sized by the
 * finger's horizontal distance from the centre rather than by a pinch.
 */
@Composable
private fun Modifier.ringCircleDrag(
    onRadiusPixels: (Float) -> Unit,
    onDragging: (Boolean) -> Unit
): Modifier {
    val radius by rememberUpdatedState(onRadiusPixels)
    val dragging by rememberUpdatedState(onDragging)
    return this
        .pointerInput(Unit) {
            detectTapGestures { offset -> radius(abs(offset.x - size.width / 2f)) }
        }
        .pointerInput(Unit) {
            detectHorizontalDragGestures(
                onDragStart = { offset ->
                    dragging(true)
                    radius(abs(offset.x - size.width / 2f))
                },
                onDragEnd = { dragging(false) },
                onDragCancel = { dragging(false) },
                onHorizontalDrag = { change, _ ->
                    change.consume()
                    radius(abs(change.position.x - size.width / 2f))
                }
            )
        }
}

/** The reading, the four published sizes, the fine buttons, the slider and Save. */
@Composable
private fun RingResults(
    vm: RingSizeViewModel,
    state: RingSizeState,
    drawn: Long,
    claimed: Boolean,
    maxMicrometres: Long,
    appSettings: AppSettings,
    haptics: DeviceHaptics
) {
    val type = LocalRulerType.current
    val colors = LocalRulerColors.current
    val calibrated = state.calibration.verified
    val selectedMatch = vm.match()
    // The circle the current drawing implies, as the length the log stores it: the paper method measured
    // a circumference, and the row that reaches the log is the ring, not the strip.
    val diameterMicrometres = vm.plateMicrometresForDiameter(vm.innerDiameterMm(), RingMeasureMode.RING)
    val diameterText = stringResource(
        R.string.ruler_readout_format,
        Formatter.numeric(diameterMicrometres, MeasurementUnit.MM, FormatMode.DECIMAL),
        stringResource(unitLabelRes(MeasurementUnit.MM))
    )

    // The number is the thing the finger matched: the inside of the ring in one method, the length of
    // the paper in the other. The circle it implies is named separately, because "54.4 mm" beside a
    // ring size is not a diameter and reading it as one is how a size gets wrong.
    val plateCaption = if (state.mode == RingMeasureMode.RING) R.string.ring_diameter_caption else R.string.ring_span_caption
    val plateValue = Formatter.format(drawn, MeasurementUnit.MM, FormatMode.DECIMAL)
    val plateText = stringResource(R.string.ruler_readout_format, plateValue.text, stringResource(unitLabelRes(MeasurementUnit.MM)))

    Column(verticalArrangement = Arrangement.spacedBy(RulerSpacing.xs)) {
        Text(
            stringResource(plateCaption).capsRegister(),
            style = type.labelCaps,
            color = colors.onSurfaceSecondary.toCompose()
        )
        Text(
            // Nothing drawn is nothing claimed: the row stays empty rather than showing a 0 that would
            // read as a measurement of a zero-length ring (M11R finding 3).
            text = if (claimed) plateText else stringResource(R.string.ring_waiting),
            style = if (claimed) type.readout else type.bodyLarge,
            color = if (claimed) colors.onSurface.toCompose() else colors.onSurfaceSecondary.toCompose(),
            modifier = Modifier.announceChanges()
        )
        if (claimed && state.mode == RingMeasureMode.FINGER) {
            // The paper length is the thing the finger matched; the ring it implies is the number the
            // sizes are read from, so it gets its own caption rather than a second meaning for the big
            // number above it.
            Text(
                stringResource(R.string.ring_diameter_caption).capsRegister(),
                style = type.labelCaps,
                color = colors.onSurfaceSecondary.toCompose()
            )
            Text(
                diameterText,
                style = type.readoutSecondary,
                color = colors.onSurface.toCompose()
            )
        }
        if (claimed && calibrated) {
            Text(
                stringResource(
                    R.string.ring_accuracy_calibrated_format,
                    formatNumber(vm.sizesOfUncertainty(state.system))
                ),
                style = type.meta,
                color = colors.onSurfaceSecondary.toCompose()
            )
        }
        // Rule 14: with nothing drawn there is nothing to step, so the buttons are not shown greyed out.
        if (claimed) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(RulerSpacing.sm)
            ) {
                OutlinedButton(
                    onClick = { vm.stepRung(-1, maxMicrometres) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.ring_step_down))
                }
                OutlinedButton(
                    onClick = { vm.stepRung(+1, maxMicrometres) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.ring_step_up))
                }
            }
        }
    }

    SectionHeader(stringResource(R.string.ring_sizes_caption))
    SizeTable(vm, state, claimed, calibrated)

    if (selectedMatch?.offScale == true) {
        Text(
            stringResource(
                if (selectedMatch.belowScale) R.string.ring_off_scale_below else R.string.ring_off_scale_above
            ),
            style = type.meta,
            color = colors.onSurfaceSecondary.toCompose()
        )
    }

    // The slider is the TalkBack route and the fine adjustment; the plate is the physical one. Its range
    // is what this plate can draw, so the control cannot ask for a circle the screen does not have.
    if (maxMicrometres > 0L) {
        val maxMm = maxMicrometres.toDouble() / Physical.MICROMETRES_PER_MM
        Slider(
            value = (drawn.toDouble() / Physical.MICROMETRES_PER_MM).toFloat().coerceIn(0f, maxMm.toFloat()),
            onValueChange = { valueMm ->
                vm.setDrawn((valueMm.toDouble() * Physical.MICROMETRES_PER_MM).roundToLong(), maxMicrometres)
            },
            valueRange = 0f..maxMm.toFloat(),
            modifier = Modifier.labelled(
                stringResource(
                    if (state.mode == RingMeasureMode.RING) R.string.ring_circle_slider_label else R.string.ring_span_slider_label
                )
            )
        )
    }

    val messages = LocalAppMessages.current
    val scope = rememberCoroutineScope()
    val undoLabel = stringResource(R.string.message_undo)
    val headline = headlineFor(vm, state, claimed, calibrated)
    // Built at composition: `stringResource` is a composable call, and building the label inside the
    // click lambda is the kind of thing that only fails when the button is actually pressed.
    val logLabel = stringResource(
        R.string.ring_log_label_format,
        "${stringResource(systemLabelRes(state.system))} $headline",
        diameterText
    )
    val savedText = stringResource(R.string.ring_saved_format, headline)
    Button(
        onClick = {
            if (appSettings.haptics) haptics.confirm()
            scope.launch {
                val id = vm.save(logLabel)
                if (messages.say(savedText, actionLabel = undoLabel)) vm.deleteSaved(id)
            }
        },
        enabled = claimed,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(Icons.Default.Check, contentDescription = null)
        Text(stringResource(R.string.ring_save), modifier = Modifier.padding(start = RulerSpacing.sm))
    }

    Text(
        stringResource(R.string.ring_note),
        style = type.meta,
        color = colors.onSurfaceSecondary.toCompose()
    )
}

/**
 * The four published sizes in one hairline panel. Tapping one makes it the system the ± and the saved
 * label are stated in; the selection is carried by the announced state and by the size of the number,
 * never by colour alone (SPEC 11).
 */
@Composable
private fun SizeTable(vm: RingSizeViewModel, state: RingSizeState, claimed: Boolean, calibrated: Boolean) {
    val type = LocalRulerType.current
    val colors = LocalRulerColors.current

    RulerCard {
        RingSizeSystem.entries.forEachIndexed { index, system ->
            if (index > 0) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(RulerDimensions.hairline)
                        .background(colors.outlineVariant.toCompose())
                )
            }
            val selected = state.system == system
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = selected,
                        onClick = { vm.setSystem(system) },
                        role = Role.RadioButton
                    )
                    .padding(vertical = RulerSpacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(systemLabelRes(system)),
                    style = type.bodyMedium,
                    color = colors.onSurface.toCompose(),
                    modifier = Modifier.weight(1f)
                )
                // The emphasis is only for a size that exists. A big accent dash in the selected row read
                // as a reading the screen has not made (seen on the Fold at M21).
                val size = sizeText(vm, system, claimed, calibrated)
                Text(
                    text = size ?: NO_SIZE,
                    style = if (selected && size != null) type.numberLarge else type.bodyLarge.tabular(),
                    color = when {
                        size == null -> colors.onSurfaceSecondary.toCompose()
                        selected -> colors.accent.toCompose()
                        else -> colors.onSurface.toCompose()
                    }
                )
            }
        }
    }
}

/**
 * The size for one system, or null when this screen may not name one: nothing drawn yet, or a circle
 * outside the published chart. Naming the nearest chart entry for a jar lid would be a size claim the
 * chart does not make.
 */
@Composable
private fun sizeText(vm: RingSizeViewModel, system: RingSizeSystem, claimed: Boolean, calibrated: Boolean): String? {
    val match: RingSizeMatch = vm.match(system) ?: return null
    if (match.offScale) return null
    if (!calibrated) {
        val (low, high) = vm.sizeRange(system)
        return if (low == high) low else stringResource(R.string.ring_size_range_format, low, high)
    }
    val neighbour = match.neighbour
    return if (neighbour != null && match.isBetween) {
        stringResource(R.string.ring_between_format, match.nearest.label, neighbour.label)
    } else {
        match.nearest.label
    }
}

/** The size the saved row and the confirmation name. */
@Composable
private fun headlineFor(vm: RingSizeViewModel, state: RingSizeState, claimed: Boolean, calibrated: Boolean): String =
    sizeText(vm, state.system, claimed, calibrated) ?: NO_SIZE

private fun systemLabelRes(system: RingSizeSystem): Int = when (system) {
    RingSizeSystem.US -> R.string.ring_system_us
    RingSizeSystem.UK -> R.string.ring_system_uk
    RingSizeSystem.EU -> R.string.ring_system_eu
    RingSizeSystem.JP -> R.string.ring_system_jp
}

/**
 * The longest drawing this plate can show, in micrometres. The circle is limited by the smaller side of
 * its square minus the same ink padding the plate draws with; the paper strip is limited by the ruler's
 * own measurable length from the start line.
 */
private fun plateLimit(
    mode: RingMeasureMode,
    circleSide: Dp,
    paneWidth: Dp,
    labelSizePx: Float,
    pxPerMm: PixelPerMm,
    startEdge: StartEdge,
    density: Density
): Long {
    if (pxPerMm.pixelsPerMm <= 0.0) return 0L
    return if (mode == RingMeasureMode.RING) {
        val sidePx = with(density) { circleSide.toPx() }
        val drawableDiameterPx = (sidePx - 2f * labelSizePx * PLATE_INK_FACTOR).coerceAtLeast(0f)
        Math.round(drawableDiameterPx.toDouble() / pxPerMm.pixelsPerMm * Physical.MICROMETRES_PER_MM)
    } else {
        startEdge.measurableMicrometres(with(density) { paneWidth.toPx() }.toDouble())
    }
}

private fun radiusToMicrometres(radiusPx: Float, pxPerMm: PixelPerMm): Long =
    if (pxPerMm.pixelsPerMm <= 0.0) 0L
    else Math.round(2.0 * radiusPx / pxPerMm.pixelsPerMm * Physical.MICROMETRES_PER_MM)

/** The largest inner circumference any of the four published ladders names (EU 80 mm). */
private const val LARGEST_PUBLISHED_CIRCUMFERENCE_MM = 80.0

/**
 * The largest inner *diameter* the four ladders name — JP 39 is 25.67 mm, EU 80 is 25.46 mm, UK Z+9 is
 * 25.5 and US 16 is 24.64 — rounded up. The circle plate is sized from this, so it is never bigger
 * than the chart its sizes come from.
 */
private const val LARGEST_PUBLISHED_DIAMETER_MM = 26.0

/** The plate's inner padding as a multiple of the tick label size, shared by the drawing and the limit. */
private const val PLATE_INK_FACTOR = 0.9f

/** Nothing to name yet: an em dash, a mark rather than a word, so it needs no translation. */
private const val NO_SIZE = "\u2014"

/** One decimal, the same rule the ruler screen's accuracy line uses. */
private fun formatNumber(value: Double): String {
    val format = NumberFormat.getInstance(Locale.getDefault())
    format.maximumFractionDigits = 1
    return format.format(value)
}
