package com.blueorbit.ruler.feature.ruler

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.blueorbit.ruler.R
import com.blueorbit.ruler.core.calibration.CalibrationSource
import com.blueorbit.ruler.core.di.ViewModelFactory
import com.blueorbit.ruler.core.geometry.FormatMode
import com.blueorbit.ruler.core.geometry.Formatter
import com.blueorbit.ruler.core.geometry.MeasurementUnit
import com.blueorbit.ruler.core.geometry.Physical
import com.blueorbit.ruler.core.geometry.PixelPerMm
import com.blueorbit.ruler.core.geometry.RulerScene
import com.blueorbit.ruler.core.haptics.DeviceHaptics
import com.blueorbit.ruler.core.ui.LocalAppMessages
import com.blueorbit.ruler.core.ui.say
import kotlinx.coroutines.launch
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import com.blueorbit.ruler.core.haptics.Haptics
import com.blueorbit.ruler.core.log.Measurement
import com.blueorbit.ruler.core.settings.AppSettings
import com.blueorbit.ruler.core.ui.LocalRulerColors
import com.blueorbit.ruler.core.ui.LocalRulerType
import com.blueorbit.ruler.core.ui.OutlinedChip
import com.blueorbit.ruler.core.ui.RulerDimensions
import com.blueorbit.ruler.core.ui.RulerShapes
import com.blueorbit.ruler.core.ui.RulerSpacing
import com.blueorbit.ruler.core.ui.ScreenTitleRow
import com.blueorbit.ruler.core.ui.capsRegister
import com.blueorbit.ruler.core.ui.motionMillis
import com.blueorbit.ruler.core.ui.readoutEasing
import com.blueorbit.ruler.core.ui.reducedMotion
import com.blueorbit.ruler.core.ui.semantics.announceChanges
import com.blueorbit.ruler.core.ui.semantics.labelled
import com.blueorbit.ruler.core.ui.toCompose
import com.blueorbit.ruler.core.ui.unitLabelRes
import com.blueorbit.ruler.core.ui.useNavigationRail
import com.blueorbit.ruler.feature.log.LogViewModel
import kotlinx.coroutines.flow.StateFlow
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.roundToLong

/**
 * The ruler screen (DESIGN.md §5.4, §7, §13).
 *
 * It contains the ruler and nothing else: caption, readout, provenance, the unit and format controls,
 * the plate, and one action. Label size, the start-line inset and the px/mm value all live in
 * Settings — the debug controls left this screen at M11V.
 *
 * Insets and the screen margin come from `AppScaffold`, so this screen cannot forget them (reviewer
 * note 1) and cannot set them differently from another screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RulerScreen(
    factory: ViewModelFactory,
    settings: StateFlow<AppSettings>,
    haptics: DeviceHaptics,
    onOpenCalibration: () -> Unit
) {
    val vm: RulerViewModel = viewModel(factory = factory)
    val logVm: LogViewModel = viewModel(factory = factory)
    val state by vm.state.collectAsStateWithLifecycle()
    val appSettings by settings.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.refresh() }

    val density = LocalDensity.current
    val widthDp = LocalConfiguration.current.screenWidthDp
    val expanded = useNavigationRail(widthDp)

    // Labels scale independently of the physical geometry (rule 1): the stored font scale reaches the
    // label size here and nowhere else, so `pxPerMm` below is untouched by it.
    val labelSizePx = with(density) { 14.sp.toPx() } * appSettings.labelFontScale.toFloat()

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val columnPx = with(density) { maxWidth.toPx() }
        // The plate width is known analytically (the readout pane has a fixed width in the two-pane
        // layout), so the readout and the drawn plate describe the same span. If they disagreed, the
        // number would promise more ruler than the plate actually draws (rule 5).
        val platePx = if (expanded) {
            columnPx - with(density) { (RulerDimensions.readoutPaneWidth + RulerSpacing.lg).toPx() }
        } else {
            columnPx
        }

        val pxPerMm = PixelPerMm(state.calibration.pixelsPerMm)
        val scene = RulerScene(pxPerMm, state.unit, labelSizePx.toDouble(), state.formatMode)
        // The opposite system on the other edge (PLAN M4: both scales visible at once).
        val secondaryUnit = if (state.unit == MeasurementUnit.INCH) MeasurementUnit.MM else MeasurementUnit.INCH
        val secondary = RulerScene(pxPerMm, secondaryUnit, labelSizePx.toDouble(), state.formatMode)
        val startEdge = StartEdge(state.calibration.bezelInsetMm, pxPerMm)
        val maxSpanMicrometres = startEdge.measurableMicrometres(platePx.toDouble())
        // M17: the span the user has drawn. Nothing is measured until the slider moves, so the screen
        // opens as the ruler it is (its own length) rather than as a 0 reading.
        //
        // Two values, on purpose. `requested` is what the user set; `drawn` is what this plate can draw.
        // They differ only when a calibration made a stored span longer than the ruler it was drawn on.
        // The drawn rule, its knob, the number and Save all use `drawn` so they cannot disagree about how
        // long the object is; the haptics use `requested` so a calibration elsewhere cannot fire a tick
        // the user never caused.
        val requestedMicrometres = state.measuredMicrometres
        val drawnMicrometres = requestedMicrometres.coerceIn(0L, maxSpanMicrometres)
        // The span rule is drawn **from the start line**, so the end of a drawn span is the start line
        // plus the span. Measuring the end from pixel 0 instead made the drawn line shorter than the
        // number above it by exactly the bezel inset (found in review).
        val measuredPx = if (drawnMicrometres > 0L) {
            startEdge.zeroPixels() + pxPerMm.micrometresToPixels(drawnMicrometres)
        } else {
            platePx.toDouble()
        }

        val readoutBlock = @Composable {
            ReadoutBlock(
                state = state,
                drawnMicrometres = drawnMicrometres,
                requestedMicrometres = requestedMicrometres,
                maxSpanMicrometres = maxSpanMicrometres,
                scene = scene,
                appSettings = appSettings,
                haptics = haptics,
                logVm = logVm,
                onOpenCalibration = onOpenCalibration
            )
        }

        val plateBlock = @Composable {
            // In simple mode the plate's own label is the instruction the screen is built around: the
            // line is where the object starts. "start 0" is a coordinate; "start here" is an action.
            val startLabel = rememberStartEdgeLabel(appSettings.simpleMode, state.calibration.bezelInsetMm)
            val plateDescription = stringResource(R.string.ruler_plate_description)
            var dragging by remember { mutableStateOf(false) }
            RulerPlate(
                primary = scene,
                secondary = secondary,
                startEdge = startEdge,
                labelSizePx = labelSizePx,
                startLabel = startLabel,
                spanEndPixels = measuredPx,
                // The knob only belongs to a measurement the user has drawn. With nothing measured the
                // rule is the ruler's own length, and a knob on it would invite dragging something that
                // is not yet a reading.
                showSpanHandle = drawnMicrometres > 0L,
                animateSpan = !dragging,
                modifier = Modifier
                    .fillMaxSize()
                    // The plate itself is the control: a finger on it moves the span end there, and a
                    // drag follows the finger. The slider below stays for TalkBack and fine adjustment.
                    .spanDrag(
                        onSpanEnd = { x ->
                            vm.setMeasuredMicrometres(
                                micrometres = startEdge.measureAt(x.toDouble()),
                                maxSpanMicrometres = maxSpanMicrometres
                            )
                        },
                        onDragging = { dragging = it }
                    )
                    // The plate is where the physical alignment happens and a screen reader cannot see
                    // any of it. The live facts are announced by their own controls (the readout is a
                    // live region, the slider is labelled), so this states what the plate is for and is
                    // deliberately static: a description that changed with the span would re-announce on
                    // every step of the drag.
                    .semantics { contentDescription = plateDescription }
            )
        }

        val measureControl = @Composable {
            if (maxSpanMicrometres > 0L) {
                MeasureSpanSlider(vm, maxSpanMicrometres, drawnMicrometres)
            }
        }

        if (expanded) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(RulerSpacing.lg)) {
                // Scrolls: in landscape on a phone this column is taller than the window, and the
                // instruction under the unit buttons was cut off mid-sentence.
                Column(
                    Modifier
                        .width(RulerDimensions.readoutPaneWidth)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                ) {
                    ScreenTitle(state)
                    readoutBlock()
                    UnitAndFormatControls(vm, state)
                    FirstRunInstruction(vm, appSettings)
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(RulerSpacing.sm)
                ) {
                    Box(Modifier.weight(1f)) { plateBlock() }
                    measureControl()
                }
            }
        } else {
            // The text above the plate is capped and scrolls, so the plate always keeps real height.
            // At 200 % font scale (and in German) the readout, unit buttons and instruction used to take
            // the whole column and the ruler itself shrank to nothing.
            val upperMax = maxHeight * 0.55f
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(RulerSpacing.sm)) {
                Column(
                    Modifier
                        .heightIn(max = upperMax)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(RulerSpacing.sm)
                ) {
                    ScreenTitle(state)
                    readoutBlock()
                    UnitAndFormatControls(vm, state)
                    // The instruction sits directly above the thing it describes (M17).
                    FirstRunInstruction(vm, appSettings)
                }
                Box(Modifier.weight(1f)) { plateBlock() }
                measureControl()
            }
        }
    }
}

/**
 * The ruler title row (DESIGN.md §6 M19): the screen name plus the provenance chip. The chip reuses
 * the `source_*` strings verbatim — DECISIONS 5's wording lives here too: an unverified ruler says
 * "estimated from display", never a claim of calibration. It sits on the ruler screen, so the
 * calibration state stays visible there (rule 5) in both simple and precise mode.
 */
@Composable
private fun ScreenTitle(state: RulerState) {
    val calibration = state.calibration
    ScreenTitleRow(
        title = stringResource(R.string.ruler_title),
        trailing = {
            OutlinedChip(
                text = stringResource(sourceRes(calibration.source)),
                emphasized = calibration.verified
            )
        },
        modifier = Modifier.padding(bottom = RulerSpacing.xs)
    )
}

@Composable
private fun ReadoutBlock(
    state: RulerState,
    drawnMicrometres: Long,
    requestedMicrometres: Long,
    maxSpanMicrometres: Long,
    scene: RulerScene,
    appSettings: AppSettings,
    haptics: DeviceHaptics,
    logVm: LogViewModel,
    onOpenCalibration: () -> Unit
) {
    val type = LocalRulerType.current
    val colors = LocalRulerColors.current
    val reduced = reducedMotion()

    // Haptics fire when the reading crosses a tick boundary, and only when they are enabled
    // (rule 10). The crossing test itself is pure and tested. They key on the value the user set rather
    // than the value this plate can draw, so a calibration or an orientation change somewhere else cannot
    // fire a tick the user never caused.
    var previousReading by remember { mutableLongStateOf(requestedMicrometres) }
    LaunchedEffect(requestedMicrometres) {
        if (Haptics.shouldFire(appSettings.haptics, previousReading, requestedMicrometres, scene.tickStepMicrometres())) {
            haptics.tick()
        }
        previousReading = requestedMicrometres
    }

    // Until something is drawn the number is the ruler's own length, which is the fact the caption names.
    // After that it is the object's length (M17).
    val reading = if (drawnMicrometres > 0L) drawnMicrometres else maxSpanMicrometres
    val saveLabel = stringResource(R.string.log_save_reading)
    val budget = formatNumber(state.calibration.errorBudgetMmPer100Mm)
    // Rule 5 in both modes, rule 13 in this one: the reading always says how far it can be trusted.
    // Precise mode names the source and the budget; simple mode states the same fact as a sentence a
    // reader who has never met the words "provenance" or "error budget" can act on.
    val accuracy = if (appSettings.simpleMode) {
        if (state.calibration.verified) {
            stringResource(R.string.ruler_accuracy_calibrated_format, budget)
        } else {
            stringResource(R.string.ruler_accuracy_estimate_format, budget)
        }
    } else {
        stringResource(
            R.string.provenance_format,
            stringResource(sourceRes(state.calibration.source)),
            budget
        )
    }

    // DESIGN.md §5.4: the caption sits on its own full-width line above the number and the save
    // button. Inside the readout column it shared the width with the save button and wrapped to
    // "This screen measures up / to", which left an orphan word under a 44 sp number.
    Column(verticalArrangement = Arrangement.spacedBy(RulerSpacing.xs)) {
        Text(
            // The number is the length of the screen itself, which is not obvious from "Screen span".
            // Once the user has drawn a span the caption names their object instead (M17), because the
            // same big number meaning two different things without saying which is how a reading gets
            // misread.
            stringResource(
                if (drawnMicrometres > 0L) {
                    if (appSettings.simpleMode) {
                        R.string.ruler_measured_caption_simple
                    } else {
                        R.string.ruler_measured_caption
                    }
                } else {
                    if (appSettings.simpleMode) {
                        R.string.ruler_span_caption_simple
                    } else {
                        R.string.ruler_span_caption
                    }
                }
            ).capsRegister(),
            // M19 (DESIGN.md §3): the caption above the number is a caps micro label. The string is
            // unchanged — the caps are the register's, not the copy's.
            style = type.labelCaps,
            color = colors.onSurfaceSecondary.toCompose()
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            val slidePx = with(LocalDensity.current) { 6.dp.roundToPx() }
            val duration = motionMillis(120, reduced)
            AnimatedContent(
                // M17: keyed on *why* the reading changed, not on the number. The measured value now
                // changes with every step of the slider, and animating that turned the readout into a
                // continuous cross-fade under the user's finger. DESIGN.md §10 asks for the animation
                // when the reading changes for a reason the user did not just cause themselves — a unit,
                // format or calibration change. A drag updates the number directly.
                targetState = Triple(state.unit, state.formatMode, state.calibration.pixelsPerMm),
                transitionSpec = {
                    ContentTransform(
                        fadeIn(tween(duration, easing = readoutEasing)) +
                            slideInVertically(tween(duration, easing = readoutEasing)) { slidePx },
                        fadeOut(tween(duration)) +
                            slideOutVertically(tween(duration)) { -slidePx }
                    )
                },
                label = "readout",
                // The number owns the whole row (M17): the save control moved to its own line below.
                modifier = Modifier.weight(1f)
            ) { (targetUnit, targetFormat, _) ->
                // The text is built from the target state, not captured from the screen's current unit.
                // Capturing it meant the frame sliding *out* of a unit change already showed the new
                // unit's wording, so a mm→inch cross-fade showed inches twice (found by lint's
                // UnusedContentLambdaTargetStateParameter, which was right that the parameter was unused).
                Text(
                    text = readoutText(reading, targetUnit, targetFormat, appSettings.simpleMode),
                    style = type.readout,
                    color = colors.onBackground.toCompose(),
                    // PLAN M10: the current measurement is a live region, so TalkBack announces a new
                    // reading when the span changes without stealing focus from another control.
                    modifier = Modifier.announceChanges()
                )
            }
        }
    }

    // Rule 5: how far the reading can be trusted is part of the layout, in secondary ink. It gets its own
    // full-width row rather than sitting inside the readout column next to the save button: squeezed into
    // that column the line wrapped to three narrow lines under a 44 sp number, which hides the error
    // budget by another route. Unverified is a state, not a failure, so it is never red (DESIGN.md §5.4).
    Row(verticalAlignment = Alignment.CenterVertically) {
        // Rule 14: if the start line sits beyond the far end of the plate there is nothing to measure,
        // and a 0 reading with no explanation is the dead end the user should never be left with. The
        // sentence names the way out instead. It is about the plate, not about whether something has
        // been drawn yet.
        val unusable = maxSpanMicrometres == 0L
        Text(
            if (unusable) stringResource(R.string.start_edge_off_screen) else accuracy,
            style = type.meta,
            color = (if (unusable) colors.danger else colors.onSurfaceSecondary).toCompose(),
            modifier = Modifier.weight(1f, fill = false)
        )
        if (!state.calibration.verified) {
            TextButton(onClick = onOpenCalibration) {
                Text(
                    stringResource(
                        if (appSettings.simpleMode) R.string.action_make_exact else R.string.action_calibrate
                    )
                )
            }
        }
    }

    // Save appears once there is something to save (M17). Before that it would have stored the length of
    // the screen and called it a measurement — which is what this button had been doing since M5, and the
    // reason a first-time user could not see how a measurement got into the log at all. It is gated on the
    // span this plate can draw, not on the value the user set, so a span left over from a ruler that has
    // since become shorter cannot save a zero.
    //
    // It is a full-width button on its own line rather than the floating button beside the number: with
    // the button in that Row the reading lost half its width and "29.7 mm" wrapped into two lines under
    // itself, which is what the M17 device screenshot showed. A `Button` also puts its text in the
    // accessibility tree on its own, which `ExtendedFloatingActionButton` does not.
    // The row is always reserved and the button fades in once there is something to save (M17). It
    // used to be inserted, which pushed the plate ~40 dp down the screen at the first touch — under the
    // object the user had just lined up against the start line.
    val canSave = drawnMicrometres > 0L
    val saveAlpha by animateFloatAsState(
        targetValue = if (canSave) 1f else 0f,
        animationSpec = tween(motionMillis(150, reduced)),
        label = "saveButton"
    )
    run {
        val messages = LocalAppMessages.current
        val scope = rememberCoroutineScope()
        val undoLabel = stringResource(R.string.message_undo)
        val savedText = stringResource(
            R.string.ruler_saved_format,
            readoutText(drawnMicrometres, state.unit, state.formatMode, appSettings.simpleMode)
        )
        Button(
            onClick = {
                val measurement = Measurement(
                    valueMicrometres = drawnMicrometres,
                    unit = state.unit.name,
                    label = "",
                    note = "",
                    source = state.calibration.source.name,
                    errorBudgetMmPer100Mm = state.calibration.errorBudgetMmPer100Mm,
                    createdAtMillis = 0L
                )
                if (appSettings.haptics) haptics.confirm()
                // The save used to be silent: the button looked the same before and after, and the only
                // proof a reading was kept was to go and look in the log. Undo is the cheap way back from
                // a tap that came a moment too early.
                scope.launch {
                    val id = logVm.saveAndReturnId(measurement)
                    if (messages.say(savedText, actionLabel = undoLabel)) logVm.deleteSaved(id)
                }
            },
            enabled = canSave,
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { alpha = saveAlpha }
                // Invisible means absent for TalkBack too (rule 14: no control with nothing to do).
                .then(if (canSave) Modifier else Modifier.clearAndSetSemantics { })
        ) {
            Icon(Icons.Default.Check, contentDescription = null)
            Text(saveLabel, modifier = Modifier.padding(start = RulerSpacing.sm))
        }
    }
}

/**
 * The readout sentence for one value under one unit and format. It takes them as arguments rather than
 * reading the screen's current state, because `AnimatedContent` renders the outgoing frame with the
 * *previous* target state — a captured unit would put the new unit's wording in the frame that is
 * sliding away.
 *
 * Rule 13: the ≈ glyph is the same statement simple mode makes in words. Both modes say the number has
 * been rounded; only the symbol is dropped.
 */
@Composable
private fun readoutText(
    valueMicrometres: Long,
    unit: MeasurementUnit,
    formatMode: FormatMode,
    simpleMode: Boolean
): String {
    val readout = Formatter.format(valueMicrometres, unit, formatMode)
    val unitLabel = stringResource(unitLabelRes(unit))
    return if (readout.approximate) {
        stringResource(
            if (simpleMode) R.string.ruler_readout_approx_simple_format else R.string.ruler_readout_approx_format,
            readout.text,
            unitLabel
        )
    } else {
        stringResource(R.string.ruler_readout_format, readout.text, unitLabel)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UnitAndFormatControls(vm: RulerViewModel, state: RulerState) {
    val units = MeasurementUnit.entries
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        units.forEachIndexed { index, unit ->
            SegmentedButton(
                selected = state.unit == unit,
                onClick = { vm.setUnit(unit) },
                // M19 (DESIGN.md §4): flat 0 dp segments, brass fill on the selected one.
                shape = RulerShapes.chip
            ) {
                // No icon here: `SegmentedButton` already draws a leading check on the selected
                // segment. Adding one by hand put a second check on top of the label text, which is
                // what the first build of this screen looked like on the device.
                Text(stringResource(unitLabelRes(unit)))
            }
        }
    }

    if (state.unit == MeasurementUnit.INCH) {
        val modes = FormatMode.entries
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            modes.forEachIndexed { index, mode ->
                SegmentedButton(
                    selected = state.formatMode == mode,
                    onClick = { vm.setFormatMode(mode) },
                    // M19 (DESIGN.md §4): flat 0 dp segments.
                    shape = RulerShapes.chip
                ) {
                    Text(stringResource(modeLabelRes(mode)))
                }
            }
        }
    }
}

/**
 * The control that sets how long the drawn span is (M17). A Material `Slider` rather than a drag on the
 * plate: it is the same control the calibration wizard uses, it carries a TalkBack label and an adjustable
 * value, and a drag on a canvas carries neither. Its range is the ruler's own measurable length, so the
 * control cannot ask for more ruler than the plate draws (rule 5).
 */
@Composable
private fun MeasureSpanSlider(
    vm: RulerViewModel,
    maxSpanMicrometres: Long,
    measuredMicrometres: Long
) {
    val label = stringResource(R.string.ruler_object_slider_label)
    val maxMm = (maxSpanMicrometres.toDouble() / Physical.MICROMETRES_PER_MM).toFloat()
    val measuredMm = (measuredMicrometres.toDouble() / Physical.MICROMETRES_PER_MM).toFloat()

    Slider(
        value = measuredMm.coerceIn(0f, maxMm),
        // The slider works in millimetres, which are Float, so the round trip back to micrometres can land
        // one above the ruler's own length. The ViewModel is handed the limit and clamps to it: that is what
        // makes the slider, the drawn rule, its knob, the number and Save state one span (rule 5).
        onValueChange = { valueMm ->
            vm.setMeasuredMicrometres(
                micrometres = (valueMm.toDouble() * Physical.MICROMETRES_PER_MM).roundToLong(),
                maxSpanMicrometres = maxSpanMicrometres
            )
        },
        valueRange = 0f..maxMm,
        modifier = Modifier.labelled(label)
    )
}

/**
 * The one instruction a new install gets (M17): what to physically do, shown on the screen it has to be
 * done on. Dismissing it is stored rather than remembered in the composition, so it teaches once and does
 * not come back for a user who already knows it.
 */
@Composable
private fun FirstRunInstruction(vm: RulerViewModel, appSettings: AppSettings) {
    if (appSettings.firstRunInstructionSeen) return

    val type = LocalRulerType.current
    val colors = LocalRulerColors.current

    Column(verticalArrangement = Arrangement.spacedBy(RulerSpacing.xs)) {
        Text(
            stringResource(R.string.ruler_first_run_instruction),
            style = type.bodyMedium,
            color = colors.onSurface.toCompose()
        )
        TextButton(onClick = { vm.dismissFirstRunInstruction() }) {
            Text(stringResource(R.string.ruler_first_run_dismiss))
        }
    }
}

private fun formatNumber(value: Double): String {
    val format = NumberFormat.getInstance(Locale.getDefault())
    format.maximumFractionDigits = 1
    return format.format(value)
}

/**
 * The label on the plate's start line, shared by the ruler and Extend mode so one line is named one
 * way (rule 13). Simple mode states the action the line asks for; precise mode states the inset the
 * line sits at, which is the number that produced it.
 */
@Composable
internal fun rememberStartEdgeLabel(simpleMode: Boolean, insetMm: Double): String {
    val inset = remember(insetMm) { formatNumber(insetMm) }
    return when {
        simpleMode && insetMm > 0.0 ->
            stringResource(R.string.start_edge_label_simple_inset_format, inset)
        simpleMode -> stringResource(R.string.start_edge_label_simple)
        else -> stringResource(R.string.start_edge_label, inset)
    }
}

private fun modeLabelRes(mode: FormatMode): Int = when (mode) {
    FormatMode.FRACTION -> R.string.mode_fraction
    FormatMode.DECIMAL -> R.string.mode_decimal
}

private fun sourceRes(source: CalibrationSource): Int = when (source) {
    CalibrationSource.DEVICE_DEFAULT -> R.string.source_default
    CalibrationSource.CARD -> R.string.source_card
    CalibrationSource.COIN -> R.string.source_coin
    CalibrationSource.MANUAL -> R.string.source_manual
}
