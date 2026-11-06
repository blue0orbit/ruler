package com.blueorbit.ruler.feature.calibration

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.blueorbit.ruler.R
import com.blueorbit.ruler.core.calibration.CalibrationSource
import com.blueorbit.ruler.core.calibration.Reference
import com.blueorbit.ruler.core.calibration.ReferenceKind
import com.blueorbit.ruler.core.calibration.ReferenceShape
import com.blueorbit.ruler.core.calibration.References
import com.blueorbit.ruler.core.di.ViewModelFactory
import com.blueorbit.ruler.core.haptics.DeviceHaptics
import com.blueorbit.ruler.core.settings.AppSettings
import com.blueorbit.ruler.core.ui.DetailScaffold
import com.blueorbit.ruler.core.ui.LocalAppMessages
import com.blueorbit.ruler.core.ui.LocalRulerColors
import com.blueorbit.ruler.core.ui.LocalRulerType
import com.blueorbit.ruler.core.ui.RulerDimensions
import com.blueorbit.ruler.core.ui.RulerShapes
import com.blueorbit.ruler.core.ui.RulerSpacing
import com.blueorbit.ruler.core.ui.readoutEasing
import com.blueorbit.ruler.core.ui.reducedMotion
import com.blueorbit.ruler.core.ui.say
import com.blueorbit.ruler.core.ui.semantics.labelled
import com.blueorbit.ruler.core.ui.toCompose
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Calibration, as a task rather than as an instrument (M16, DECISIONS rule 12).
 *
 * Simple mode is three steps and millimetres only: it says what the ruler is tied to right now, it
 * draws the shape of the object you are matching, and it ends by going back to the ruler. The reference
 * kind, the reference chips, the pixel span and the "saves as" derivation are behind "Other sizes", and
 * precise mode shows all of them. Both modes drive the same `CalibrationViewModel`, the same
 * `CalibrationDerivation` and the same stored `Calibration` (rule 10) — only what is on screen differs.
 *
 * The reading rule is unchanged (M11R finding 3): Done stays disabled until the user has actually moved
 * the slider or typed a reading, so a prefilled prediction can never be stamped as a card measurement.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CalibrationWizard(
    factory: ViewModelFactory,
    settings: StateFlow<AppSettings>,
    haptics: DeviceHaptics,
    onBack: () -> Unit,
    onDone: () -> Unit
) {
    val vm: CalibrationViewModel = viewModel(factory = factory)
    val state by vm.state.collectAsStateWithLifecycle()
    val appSettings by settings.collectAsStateWithLifecycle()
    val type = LocalRulerType.current
    val colors = LocalRulerColors.current
    val simple = appSettings.simpleMode

    val reduced = reducedMotion()
    val messages = LocalAppMessages.current
    val messageScope = rememberMessageScope()
    val savedMessage = stringResource(R.string.message_calibration_saved)
    val failedMessage = stringResource(R.string.message_calibration_save_failed)
    val currentSimple by rememberUpdatedState(simple)
    val currentOnDone by rememberUpdatedState(onDone)

    // Every Save/Done gets an answer, in both modes. Precise mode used to save in silence: the "current"
    // line changing was the only sign, and it reads the same as the derived line under it. The guided path
    // ends by leaving the screen, and "Done" has to mean the ruler is already using the new calibration,
    // so it waits for the write instead of guessing — and a write that failed keeps the user here, with
    // their reading, instead of sending them to a ruler that is not using it.
    LaunchedEffect(vm) {
        vm.saveOutcomes.collect { saved ->
            if (saved) {
                messageScope.launch { messages.say(savedMessage) }
                if (currentSimple) currentOnDone()
            } else {
                messageScope.launch { messages.say(failedMessage) }
            }
        }
    }

    val derived = vm.derivedCalibration()
    // Saveable: a rotation half-way through the guided path must not fold the reference table back up.
    var showOtherSizes by rememberSaveable { mutableStateOf(false) }
    // The manual field holds the text as it is typed, not the number it parses to. It is keyed to the
    // reference so switching reference clears it: a field still showing "60" next to a Done that knows
    // nothing was typed is the greyed control with no reason that rule 14 forbids. Saveable, so a rotation
    // keeps what was typed (the ViewModel keeps the parsed number, and the two would otherwise disagree).
    var typedMm by rememberSaveable(state.reference.id, state.reference.knownMm) { mutableStateOf("") }

    // The span drawn on the plate. A new reference moves it a long way at once (card short edge to long
    // edge is ~400 px), and the plate jumping in one frame read as a glitch; easing it there shows the
    // shape growing into the new size. A drag is never animated — the drawing has to follow the finger.
    val referenceKey = state.reference.id to state.reference.knownMm
    val shownPx = remember { Animatable(state.measuredPx.toFloat()) }
    var lastReferenceKey by remember { mutableStateOf(referenceKey) }
    LaunchedEffect(state.measuredPx, referenceKey) {
        val target = state.measuredPx.toFloat()
        val referenceChanged = referenceKey != lastReferenceKey
        lastReferenceKey = referenceKey
        if (referenceChanged && !reduced && shownPx.value > 0f) {
            shownPx.animateTo(target, tween(PLATE_MILLIS, easing = readoutEasing))
        } else {
            shownPx.snapTo(target)
        }
    }
    // Outside an animation the plate reads the state directly: going through the snap would draw one
    // frame behind the finger.
    val drawnPx = if (shownPx.isRunning) shownPx.value else state.measuredPx.toFloat()
    val showKinds = !simple || showOtherSizes
    val manual = state.reference.kind == ReferenceKind.MANUAL

    DetailScaffold(onBack = onBack, title = stringResource(R.string.calibration_title)) {
        // The coin list is long, so the content scrolls while Done stays reachable.
        // `animateContentSize` after the scroll measures the content, not the weighted viewport, so a
        // panel opening below eases the rest of the page down rather than shoving it in one frame.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .then(if (reduced) Modifier else Modifier.animateContentSize(tween(PLATE_MILLIS))),
            verticalArrangement = Arrangement.spacedBy(RulerSpacing.sm)
        ) {
            if (simple) {
                Text(
                    text = stringResource(statusRes(state.current.source)),
                    style = type.bodyLarge,
                    color = colors.onSurface.toCompose()
                )
                // Rule 5 with rule 13: the accuracy is stated here too, in the words the ruler screen
                // uses, so the user knows what completing this task changes.
                Text(
                    text = stringResource(
                        if (state.current.verified) {
                            R.string.ruler_accuracy_calibrated_format
                        } else {
                            R.string.ruler_accuracy_estimate_format
                        },
                        formatMm(state.current.errorBudgetMmPer100Mm)
                    ),
                    style = type.bodyMedium,
                    color = colors.onSurfaceSecondary.toCompose()
                )
            } else {
                Text(
                    text = stringResource(
                        R.string.calibration_current_format,
                        formatNumber(state.current.pixelsPerMm),
                        stringResource(sourceRes(state.current.source)),
                        formatNumber(state.current.errorBudgetMmPer100Mm)
                    ),
                    style = type.bodyLarge,
                    color = colors.onSurface.toCompose()
                )
            }

            AnimatedVisibility(
                visible = showKinds,
                enter = panelEnter(reduced),
                exit = panelExit(reduced)
            ) {
                // The kinds and their sizes come as one panel, so "Other sizes" opens one thing.
                Column(verticalArrangement = Arrangement.spacedBy(RulerSpacing.sm)) {
                    val kinds = listOf(
                        ReferenceKind.CARD to R.string.calibration_card,
                        ReferenceKind.COIN to R.string.calibration_coin,
                        ReferenceKind.MANUAL to R.string.calibration_manual
                    )
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        kinds.forEachIndexed { index, (kind, labelRes) ->
                            SegmentedButton(
                                selected = state.reference.kind == kind,
                                onClick = { vm.selectKind(kind) },
                                // M19 (DESIGN.md §4): flat 0 dp segments.
                                shape = RulerShapes.chip
                            ) {
                                Text(stringResource(labelRes))
                            }
                        }
                    }

                    // Only the references for the selected kind are listed, and the selected one is marked
                    // with a check icon as well as a fill (SPEC 11). The manual path has none to list, so it
                    // gets no row at all rather than an empty gap.
                    val references = referencesFor(state.reference.kind)
                    if (references.isNotEmpty()) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(RulerSpacing.sm),
                            verticalArrangement = Arrangement.spacedBy(RulerSpacing.sm)
                        ) {
                            references.forEach { reference ->
                                val selected = state.reference.id == reference.id &&
                                    state.reference.knownMm == reference.knownMm
                                FilterChip(
                                    selected = selected,
                                    onClick = { vm.selectReference(reference) },
                                    shape = RulerShapes.chip,
                                    leadingIcon = if (selected) {
                                        { Icon(Icons.Default.Check, contentDescription = null) }
                                    } else {
                                        null
                                    },
                                    label = {
                                        Text(
                                            stringResource(
                                                R.string.coin_label_format,
                                                formatMm(reference.knownMm),
                                                stringResource(R.string.unit_mm)
                                            )
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }

            if (!simple) {
                Text(
                    text = stringResource(
                        R.string.calibration_derived_format,
                        formatNumber(derived.pixelsPerMm),
                        stringResource(sourceRes(derived.source)),
                        formatNumber(derived.errorBudgetMmPer100Mm)
                    ),
                    style = type.meta,
                    color = colors.onSurfaceSecondary.toCompose()
                )
            }

            // The prefilled position is not a reading. Done is disabled until the user moves something,
            // and rule 14 says a disabled control has to say why rather than just being greyed out.
            if (manual) {
                Text(stringResource(R.string.calibration_hint_manual), style = type.bodyMedium)
                // The text the user is typing, not the number the app has stored: feeding the field from
                // the stored value turned every keystroke into a reformatted one ("6" became "6.0" became
                // "6.05"), which makes a length impossible to type. A comma is accepted because that is how
                // the locale writes a decimal point.
                OutlinedTextField(
                    value = typedMm,
                    onValueChange = {
                        typedMm = it
                        vm.setMeasuredMm(it.trim().replace(',', '.').toDoubleOrNull() ?: 0.0)
                    },
                    label = { Text(stringResource(R.string.calibration_measured_mm)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = RulerShapes.field
                )
                // The hint leaves as the reading arrives, instead of blinking out mid-keystroke.
                AnimatedVisibility(
                    visible = !state.aligned,
                    enter = panelEnter(reduced),
                    exit = panelExit(reduced)
                ) {
                    Text(
                        stringResource(R.string.calibration_type_first),
                        style = type.bodyMedium,
                        color = colors.onSurfaceSecondary.toCompose()
                    )
                }
            } else {
                AnimatedVisibility(
                    visible = !state.aligned,
                    enter = panelEnter(reduced),
                    exit = panelExit(reduced)
                ) {
                    Text(
                        stringResource(
                            if (simple) R.string.calibration_align_first_simple else R.string.calibration_align_first
                        ),
                        style = type.bodyMedium,
                        color = colors.onSurfaceSecondary.toCompose()
                    )
                }

                Text(
                    stringResource(
                        if (state.reference.shape == ReferenceShape.CIRCLE) {
                            R.string.calibration_instruction_coin
                        } else {
                            R.string.calibration_instruction_card
                        }
                    ),
                    style = type.bodyMedium,
                    color = colors.onSurface.toCompose()
                )
                if (!simple) {
                    Text(
                        text = stringResource(R.string.calibration_measured_px, formatPixels(state.measuredPx)),
                        style = type.numberLarge,
                        color = colors.onSurface.toCompose()
                    )
                }
            }

            // The drawing is in raw device pixels: what the user lines up is the thing the calibration
            // is derived from. Its own width is therefore the limit of what can be lined up at all.
            val barColor = colors.accent.toCompose()
            val plateColor = colors.surfaceElevated.toCompose()
            val edgeColor = colors.outline.toCompose()
            val predicted = state.current.pixelsPerMm * state.reference.knownMm

            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val viewportPx = with(LocalDensity.current) { maxWidth.toPx() }
                // The reference has to fit the width it is drawn in: a card's long edge is 1 314 px on a
                // 1 080-px panel, and opening with it made the first sentence of the wizard "your card
                // is too long" with nothing to line up (rule 14).
                LaunchedEffect(viewportPx) { vm.fitReferenceToViewport(viewportPx.toDouble()) }

                val low = (predicted * 0.9).toFloat()
                // The slider cannot ask for a shape longer than the space it is drawn in.
                val high = minOf((predicted * 1.1).toFloat(), viewportPx)
                val lineUp = predicted > 0.0 && high > low

                // `BoxWithConstraints` composes its content into one layout node, so its children are
                // not laid out as column siblings — they stack on top of each other. The children need a
                // Column of their own inside it.
                Column(verticalArrangement = Arrangement.spacedBy(RulerSpacing.sm)) {
                    if (predicted > 0.0 && !lineUp) {
                        Text(
                            text = if (simple) {
                                stringResource(
                                    R.string.calibration_too_long_simple_format,
                                    formatMm(viewportPx / state.current.pixelsPerMm)
                                )
                            } else {
                                stringResource(
                                    R.string.calibration_too_long_format,
                                    formatMm(state.reference.knownMm),
                                    formatMm(viewportPx / state.current.pixelsPerMm)
                                )
                            },
                            style = type.bodyMedium,
                            color = colors.onSurfaceSecondary.toCompose()
                        )
                    }

                    if (lineUp) {
                        val minPlatePx = with(LocalDensity.current) { PLATE_MIN_HEIGHT.toPx() }
                        val maxPlatePx = with(LocalDensity.current) { PLATE_MAX_HEIGHT.toPx() }
                        val bandPx = with(LocalDensity.current) { BAND_HEIGHT.toPx() }
                        val bar = state.reference.shape == ReferenceShape.BAR
                        val naturalDepth = if (bar) {
                            minPlatePx
                        } else {
                            state.reference.outlineDepthPx(high.toDouble()).toFloat()
                        }
                        // A card lined up on its short edge is 85.6 mm tall, and no wizard on this phone
                        // has that much room left after the words and the slider. When the full outline
                        // cannot be drawn the plate becomes a band wider than it is tall, and the top and
                        // bottom edges are drawn dashed — see `ReferencePlate`.
                        val platePx = (if (naturalDepth > maxPlatePx) bandPx else naturalDepth)
                            .coerceIn(minPlatePx, maxPlatePx)
                        val outlinePx = if (bar) {
                            0f
                        } else {
                            state.reference.outlineDepthPx(drawnPx.toDouble()).toFloat()
                        }
                        val tallerThanPlate = !bar && outlinePx > platePx

                        AnimatedVisibility(
                            visible = tallerThanPlate,
                            enter = panelEnter(reduced),
                            exit = panelExit(reduced)
                        ) {
                            Text(
                                stringResource(R.string.calibration_taller_than_plate),
                                style = type.bodyMedium,
                                color = colors.onSurfaceSecondary.toCompose()
                            )
                        }

                        ReferencePlate(
                            shape = state.reference.shape,
                            measuredPx = drawnPx.coerceAtMost(viewportPx),
                            outlinePx = outlinePx,
                            accent = barColor,
                            plate = plateColor,
                            edge = edgeColor,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(with(LocalDensity.current) { platePx.toDp() })
                        )
                        // The manual path has no slider: the drawn line is fixed and the reading is the
                        // number the user's own ruler says about it. A slider next to it would change the
                        // drawing without changing the derivation, which is a control with nothing to do.
                        if (!manual) {
                            Slider(
                                value = state.measuredPx.toFloat().coerceIn(low, high),
                                onValueChange = { vm.setMeasuredPx(it.toDouble()) },
                                valueRange = low..high,
                                // The shape has no visible text of its own, so TalkBack needs a stated label.
                                modifier = Modifier.labelled(
                                    stringResource(
                                        if (simple) R.string.calibration_bar_label_simple
                                        else R.string.calibration_bar_label
                                    )
                                )
                            )
                        }
                    }
                }
            }

            // The rest of the reference table is real and reachable, it is just not what a first
            // measurement needs. Saying so is what keeps it out of the guided path without hiding it.
            AnimatedVisibility(
                visible = simple && !showOtherSizes,
                enter = panelEnter(reduced),
                exit = panelExit(reduced)
            ) {
                TextButton(onClick = { showOtherSizes = true }) {
                    Text(stringResource(R.string.calibration_other_sizes))
                }
            }
        }

        Button(
            onClick = {
                vm.save()
                if (appSettings.haptics) haptics.confirm()
            },
            // Disabled until the user has taken a reading: a Save that stamps a source the user never
            // used is exactly the "Calibrated (card)" bug (M11R finding 3).
            enabled = state.aligned,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(if (simple) R.string.calibration_done else R.string.calibration_save))
        }
    }
}

/**
 * The scope a "Calibration saved" message runs in. The guided path leaves this screen the moment the
 * write lands, and a message launched from the screen's own scope would be cancelled — and so dismissed —
 * by that same navigation. The snackbar host lives above the NavHost for the life of the activity, so the
 * message is given the activity's scope too. Outside an activity (a preview) the screen's scope is used.
 */
@Composable
private fun rememberMessageScope(): CoroutineScope {
    val context = LocalContext.current
    val fallback = rememberCoroutineScope()
    return remember(context) { context.findActivity()?.lifecycleScope ?: fallback }
}

private tailrec fun Context.findActivity(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** Panels and hints grow open and shrink shut; with animations turned off they simply appear. */
private fun panelEnter(reduced: Boolean): EnterTransition =
    if (reduced) {
        EnterTransition.None
    } else {
        expandVertically(tween(PLATE_MILLIS, easing = readoutEasing)) + fadeIn(tween(PLATE_MILLIS))
    }

private fun panelExit(reduced: Boolean): ExitTransition =
    if (reduced) {
        ExitTransition.None
    } else {
        shrinkVertically(tween(PLATE_MILLIS, easing = readoutEasing)) + fadeOut(tween(PLATE_MILLIS))
    }

/** Short enough not to delay the task (DESIGN.md §10), long enough to be read as movement. */
private const val PLATE_MILLIS = 220

/**
 * The thing being lined up, drawn at the scale the calibration says it should be (M16).
 *
 * Two accent lines — the left one the object's near edge sits on, the right one its far edge has to
 * reach — always. When the object's other edge also fits the plate, its top and bottom edges are drawn
 * too, so the drawing is the object's outline rather than a span with a number next to it. A coin is a
 * circle, the manual segment stays a bar, and a card whose full outline is taller than the plate is drawn
 * as the two edges it can show with the other two drawn dashed.
 */
@Composable
private fun ReferencePlate(
    shape: ReferenceShape,
    measuredPx: Float,
    outlinePx: Float,
    accent: Color,
    plate: Color,
    edge: Color,
    modifier: Modifier
) {
    Box(
        modifier = modifier
            .clip(RulerShapes.plate)
            .background(plate, RulerShapes.plate)
            .border(RulerDimensions.hairline, edge, RulerShapes.plate)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = 4.dp.toPx()
            val cap = 18.dp.toPx()
            val corner = CornerRadius(12.dp.toPx(), 12.dp.toPx())
            // The near edge is the one the object is lined up against, so it has to be seen. Drawn at
            // x = 0 half of its stroke fell outside the canvas and the line the instruction names was
            // invisible. The span between the two lines is still `measuredPx`, which is the number the
            // calibration is derived from (rule 1).
            val pad = 14.dp.toPx()
            val span = measuredPx.coerceAtMost(size.width - pad - pad)

            when (shape) {
                ReferenceShape.CIRCLE -> {
                    val radius = (span / 2f).coerceAtMost(size.height / 2f)
                    val centre = Offset(pad + radius, size.height / 2f)
                    drawCircle(color = accent.copy(alpha = 0.12f), radius = radius, center = centre)
                    // `drawCircle` has no stroke width of its own, so the coin outline is drawn the way
                    // every other edge in this app is drawn: as lines. 96 segments is smooth at any size
                    // a coin can be on any screen, and it is redrawn only while the slider is moving.
                    val segments = 96
                    for (index in 0 until segments) {
                        val from = index * 2.0 * PI / segments
                        val to = (index + 1) * 2.0 * PI / segments
                        drawLine(
                            color = accent,
                            start = Offset(
                                centre.x + (radius * cos(from)).toFloat(),
                                centre.y + (radius * sin(from)).toFloat()
                            ),
                            end = Offset(
                                centre.x + (radius * cos(to)).toFloat(),
                                centre.y + (radius * sin(to)).toFloat()
                            ),
                            strokeWidth = stroke
                        )
                    }
                }

                ReferenceShape.BAR -> {
                    val mid = size.height / 2f
                    drawLine(accent, Offset(pad, mid), Offset(pad + span, mid), stroke)
                    drawLine(accent, Offset(pad, mid - cap / 2f), Offset(pad, mid + cap / 2f), 2.dp.toPx())
                    drawLine(accent, Offset(pad + span, mid - cap / 2f), Offset(pad + span, mid + cap / 2f), 2.dp.toPx())
                }

                ReferenceShape.RECTANGLE -> {
                    val inset = stroke / 2f + 1.dp.toPx()
                    val width = pad + span
                    val fullOutline = outlinePx > 0f && outlinePx <= size.height
                    val height = if (fullOutline) outlinePx else size.height - inset - inset
                    val top = (size.height - height) / 2f
                    val bottom = top + height

                    drawRoundRect(
                        color = accent.copy(alpha = 0.12f),
                        topLeft = Offset(pad, top),
                        size = Size(span, height),
                        cornerRadius = corner
                    )
                    drawLine(accent, Offset(pad, top), Offset(pad, bottom), stroke)
                    drawLine(accent, Offset(width, top), Offset(width, bottom), stroke)
                    if (fullOutline) {
                        drawLine(accent, Offset(pad, top), Offset(width, top), stroke)
                        drawLine(accent, Offset(pad, bottom), Offset(width, bottom), stroke)
                    } else {
                        // Dashed says "the object carries on past here". A card lined up on its short edge
                        // is 85.6 mm tall and cannot be drawn whole on any phone this app runs on, so the
                        // honest drawing is its two edges plus a hint that the shape continues rather than
                        // a cropped rectangle that reads as "the card should fit in this box".
                        val faint = accent.copy(alpha = 0.55f)
                        drawDashedLine(faint, Offset(pad, top), Offset(width, top), stroke)
                        drawDashedLine(faint, Offset(pad, bottom), Offset(width, bottom), stroke)
                    }
                }
            }
        }
    }
}

/**
 * `drawLine` has no dash pattern, so the "this shape carries on" edges are drawn as short segments.
 */
private fun DrawScope.drawDashedLine(color: Color, from: Offset, to: Offset, strokeWidth: Float) {
    val dash = 10.dp.toPx()
    val gap = 7.dp.toPx()
    var x = from.x
    while (x < to.x) {
        val end = minOf(x + dash, to.x)
        drawLine(color, Offset(x, from.y), Offset(end, to.y), strokeWidth)
        x = end + gap
    }
}

private fun referencesFor(kind: ReferenceKind): List<Reference> = when (kind) {
    ReferenceKind.CARD -> listOf(References.cardLongEdge, References.cardShortEdge)
    ReferenceKind.COIN -> References.coins
    // The manual path has one length and it is the one the screen can draw, so there is no table to
    // pick from: the user types the number their own ruler says. A "100 mm" chip next to a drawing that
    // was fitted to 60 mm would only contradict the drawing.
    ReferenceKind.MANUAL -> emptyList()
}

/** The plain sentence that says what the ruler is tied to at the moment. */
private fun statusRes(source: CalibrationSource): Int = when (source) {
    CalibrationSource.DEVICE_DEFAULT -> R.string.calibration_status_estimate
    CalibrationSource.CARD -> R.string.calibration_status_card
    CalibrationSource.COIN -> R.string.calibration_status_coin
    CalibrationSource.MANUAL -> R.string.calibration_status_manual
}

private fun sourceRes(source: CalibrationSource): Int = when (source) {
    CalibrationSource.DEVICE_DEFAULT -> R.string.source_default
    CalibrationSource.CARD -> R.string.source_card
    CalibrationSource.COIN -> R.string.source_coin
    CalibrationSource.MANUAL -> R.string.source_manual
}

private fun formatNumber(value: Double): String {
    val format = NumberFormat.getInstance(Locale.getDefault())
    format.maximumFractionDigits = 2
    return format.format(value)
}

/** Millimetres only, one decimal: the guided path quotes a size and an accuracy, never a ratio. */
private fun formatMm(value: Double): String {
    val format = NumberFormat.getInstance(Locale.getDefault())
    format.maximumFractionDigits = 1
    return format.format(value)
}

/**
 * The measured span is a length the user lines up by eye, not a statistic. Grouping separators and two
 * decimals ("1,314.33 px") made it read like a measurement result and made it harder to compare against
 * the shape it describes, and grouping is locale-dependent on top of that.
 */
private fun formatPixels(value: Double): String {
    val format = NumberFormat.getInstance(Locale.getDefault())
    format.setGroupingUsed(false)
    format.maximumFractionDigits = 1
    return format.format(value)
}

private val PLATE_MIN_HEIGHT = 88.dp
/**
 * The plate is a band the object lies across, not a box the object has to fit inside: a card matched on
 * its short edge is taller than the bracket that holds its two edges. 420 dp made the plate a tall empty
 * rectangle with two lines in it (seen on the device), which reads as "the card should fit in here".
 */
private val PLATE_MAX_HEIGHT = 320.dp
/**
 * The fallback plate for a shape whose full outline does not fit: a band clearly wider than it is tall,
 * so it reads as the gap between the object's two edges instead of a box the object has to fit in.
 */
private val BAND_HEIGHT = 240.dp
