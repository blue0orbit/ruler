package com.blueorbit.ruler.feature.extend

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.blueorbit.ruler.R
import com.blueorbit.ruler.core.di.ViewModelFactory
import com.blueorbit.ruler.core.geometry.FormatMode
import com.blueorbit.ruler.core.geometry.Formatter
import com.blueorbit.ruler.core.geometry.MeasurementUnit
import com.blueorbit.ruler.core.geometry.PixelPerMm
import com.blueorbit.ruler.core.geometry.RulerScene
import com.blueorbit.ruler.core.haptics.DeviceHaptics
import com.blueorbit.ruler.core.haptics.Haptics
import com.blueorbit.ruler.core.settings.AppSettings
import com.blueorbit.ruler.core.ui.DetailScaffold
import com.blueorbit.ruler.core.ui.LocalRulerColors
import com.blueorbit.ruler.core.ui.LocalRulerType
import com.blueorbit.ruler.core.ui.OutlinedChip
import com.blueorbit.ruler.core.ui.RulerSpacing
import com.blueorbit.ruler.core.ui.motionMillis
import com.blueorbit.ruler.core.ui.readoutEasing
import com.blueorbit.ruler.core.ui.reducedMotion
import com.blueorbit.ruler.core.ui.semantics.announceChanges
import com.blueorbit.ruler.core.ui.tabular
import com.blueorbit.ruler.core.ui.toCompose
import com.blueorbit.ruler.feature.ruler.RulerPlate
import com.blueorbit.ruler.feature.ruler.StartEdge
import com.blueorbit.ruler.feature.ruler.rememberStartEdgeLabel
import com.blueorbit.ruler.feature.ruler.spanDrag
import kotlinx.coroutines.flow.StateFlow
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.roundToLong

/**
 * Extend mode (DESIGN.md §13): a detail screen reached from Tools.
 *
 * Each added length is chosen on the plate, not fixed to the screen width. The mark starts at the far
 * edge of the screen (a whole screen length, for the middle of a long object) and a touch or drag on
 * the plate moves it to wherever the object actually ends, so the last partial stretch is measured
 * here instead of being read off the ruler by eye. The Add button names the exact length it will add,
 * so what the button does is never a guess.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExtendScreen(
    factory: ViewModelFactory,
    settings: StateFlow<AppSettings>,
    haptics: DeviceHaptics,
    onBack: () -> Unit,
    onOpenPro: (() -> Unit)? = null
) {
    val vm: ExtendViewModel = viewModel(factory = factory)
    val state by vm.state.collectAsStateWithLifecycle()
    val appSettings by settings.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.refresh() }

    val density = LocalDensity.current
    val type = LocalRulerType.current
    val colors = LocalRulerColors.current
    val reduced = reducedMotion()
    val labelSizePx = with(density) { 14.sp.toPx() } * appSettings.labelFontScale.toFloat()

    fun confirm() {
        if (appSettings.haptics) haptics.confirm()
    }

    DetailScaffold(
        onBack = onBack,
        title = stringResource(R.string.extend_title),
        // M19 (DESIGN.md §6): the Pro tools' header carries the word "Pro" when the gate is open —
        // a word, not colour (SPEC 11). While locked the gate sentence below says everything instead.
        trailing = if (state.pro) ({ OutlinedChip(stringResource(R.string.settings_pro_title)) }) else null
    ) {
        if (!state.pro) {
            // Rule 10: the gate is at the UI boundary. Rule 14: the gate names its way out.
            Column(verticalArrangement = Arrangement.spacedBy(RulerSpacing.sm)) {
                Text(
                    stringResource(R.string.extend_pro_locked),
                    style = type.bodyMedium,
                    color = colors.onSurfaceSecondary.toCompose()
                )
                if (onOpenPro != null) {
                    Button(onClick = onOpenPro) { Text(stringResource(R.string.settings_pro_open)) }
                }
            }
            return@DetailScaffold
        }

        BoxWithConstraints(Modifier.fillMaxSize()) {
            val viewportPx = with(density) { maxWidth.toPx() }
            val pxPerMm = PixelPerMm(state.calibration.pixelsPerMm)
            val startEdge = StartEdge(state.calibration.bezelInsetMm, pxPerMm)
            val primary = RulerScene(pxPerMm, MeasurementUnit.MM, labelSizePx.toDouble(), FormatMode.DECIMAL)

            // The most one mark can add: from the start line to the far edge of the screen.
            val screenMicrometres = startEdge.measurableMicrometres(viewportPx.toDouble())

            // The length the next Add will record. Negative means "the screen edge", which follows the
            // screen when it rotates instead of keeping a stale pixel position from the other orientation.
            var chosenMicrometres by rememberSaveable { mutableLongStateOf(-1L) }
            var dragging by remember { mutableStateOf(false) }
            val pending = if (chosenMicrometres < 0L) screenMicrometres else chosenMicrometres.coerceIn(0L, screenMicrometres)
            val pendingPx = startEdge.zeroPixels() + pxPerMm.micrometresToPixels(pending)

            // A tick under the finger, like the ruler: the mark is placed by feel as much as by eye.
            var previousPending by remember { mutableLongStateOf(pending) }
            LaunchedEffect(pending) {
                if (dragging && Haptics.shouldFire(appSettings.haptics, previousPending, pending, primary.tickStepMicrometres())) {
                    haptics.tick()
                }
                previousPending = pending
            }

            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(RulerSpacing.sm)
            ) {
                if (screenMicrometres <= 0L) {
                    // Rule 14: a start line beyond the end of the screen leaves nothing to mark. This is a
                    // real failure of the reading, so it is the red on this screen, and it names the way out.
                    Text(
                        stringResource(R.string.start_edge_off_screen),
                        style = type.bodyMedium,
                        color = colors.danger.toCompose()
                    )
                    return@Column
                }

                Column(
                    Modifier.animateContentSize(tween(motionMillis(200, reduced), easing = readoutEasing)),
                    verticalArrangement = Arrangement.spacedBy(RulerSpacing.sm)
                ) {
                    // After the first length the instruction changes to the step that comes next. The whole
                    // procedure up front is what made the first version unreadable.
                    Text(
                        stringResource(
                            if (state.segments.isEmpty()) R.string.extend_hint else R.string.extend_next_hint
                        ),
                        style = type.bodyMedium
                    )

                    // Nothing added yet: the instruction above is the whole screen's message. A second
                    // "nothing here" paragraph under it only pushed the plate further down.
                    if (state.segments.isNotEmpty()) {
                        TotalBlock(state, vm, appSettings, reduced)
                    }
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(RulerSpacing.md),
                    verticalArrangement = Arrangement.spacedBy(RulerSpacing.sm)
                ) {
                    Button(
                        onClick = {
                            vm.mark(pending)
                            // The next length starts again from a whole screen: the usual next step on a
                            // long object is another full screen, not the partial one just added.
                            chosenMicrometres = -1L
                            confirm()
                        },
                        enabled = pending > 0L
                    ) {
                        Text(
                            stringResource(
                                R.string.extend_add_format,
                                Formatter.numeric(pending, MeasurementUnit.MM, FormatMode.DECIMAL)
                            )
                        )
                    }
                    // Rule 14: only offered when the mark is somewhere other than the edge.
                    if (chosenMicrometres >= 0L && pending != screenMicrometres) {
                        OutlinedButton(onClick = { chosenMicrometres = -1L }) {
                            Text(stringResource(R.string.extend_mark_to_edge))
                        }
                    }
                    if (state.segments.isNotEmpty()) {
                        TextButton(onClick = {
                            vm.undo()
                            confirm()
                        }) {
                            Text(stringResource(R.string.extend_undo))
                        }
                    }
                }

                val plateDescription = stringResource(R.string.extend_plate_description)
                RulerPlate(
                    primary = primary,
                    secondary = RulerScene(pxPerMm, MeasurementUnit.INCH, labelSizePx.toDouble(), FormatMode.FRACTION),
                    startEdge = startEdge,
                    labelSizePx = labelSizePx,
                    startLabel = rememberStartEdgeLabel(appSettings.simpleMode, state.calibration.bezelInsetMm),
                    spanEndPixels = pendingPx,
                    showSpanHandle = true,
                    markPixels = pendingPx,
                    animateSpan = !dragging,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .semantics { contentDescription = plateDescription }
                        .spanDrag(
                            onSpanEnd = { x ->
                                chosenMicrometres = startEdge.measureAt(x.toDouble()).coerceIn(0L, screenMicrometres)
                            },
                            onDragging = { dragging = it }
                        )
                )
            }
        }
    }
}

/**
 * The running total, its imperial twin, the estimate note, and the added lengths. The total counts up
 * to its new value when a length is added, so the addition is seen happening rather than inferred from
 * a changed number.
 */
@Composable
private fun TotalBlock(state: ExtendState, vm: ExtendViewModel, appSettings: AppSettings, reduced: Boolean) {
    val type = LocalRulerType.current
    val colors = LocalRulerColors.current
    val total = vm.totalMicrometres()
    val animatedTotal by animateFloatAsState(
        targetValue = total.toFloat(),
        animationSpec = tween(motionMillis(260, reduced), easing = readoutEasing),
        label = "extendTotal"
    )
    // While counting, the bound and the inch figure use the real total — only the big number animates.
    val shownTotal = if (reduced) total else animatedTotal.toDouble().roundToLong()

    Text(
        // Rule 7 says the bound is an estimate that widens with segment count; rule 13 says simple mode
        // states it as "give or take" rather than as the ± symbol.
        text = stringResource(
            if (appSettings.simpleMode) R.string.extend_total_simple_format else R.string.extend_total_format,
            Formatter.numeric(shownTotal, MeasurementUnit.MM, FormatMode.DECIMAL),
            stringResource(com.blueorbit.ruler.core.ui.unitLabelRes(MeasurementUnit.MM)),
            formatNumber(vm.boundMm())
        ),
        // `readoutSecondary`, not `numberLarge`: the sentence carries its bound, and at the large size
        // "Total: 29.9 mm, give or take 2 mm" wrapped to two lines on a phone.
        style = type.readoutSecondary,
        color = colors.onSurface.toCompose(),
        modifier = Modifier.announceChanges()
    )
    Text(
        text = stringResource(
            R.string.extend_imperial_format,
            Formatter.numeric(total, MeasurementUnit.INCH, FormatMode.FRACTION)
        ),
        style = type.bodyMedium.tabular(),
        color = colors.onSurface.toCompose()
    )
    Text(
        // Plurals (PLAN M10) plus the rule-7 note on one line, so the readout block stays short enough
        // to leave the plate real height on a small phone (HUMAN_CHECKS, M18).
        text = pluralStringResource(R.plurals.extend_segments, state.segments.size, state.segments.size) +
            " · " + stringResource(R.string.extend_estimate_note),
        style = type.meta,
        color = colors.onSurfaceSecondary.toCompose()
    )

    // The newest length first, and it slides in, so the row that just appeared is the one the eye lands
    // on. Capped so a long run cannot push the plate off the screen.
    val listState = rememberLazyListState()
    LaunchedEffect(state.segments.size) { listState.animateScrollToItem(0) }
    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 96.dp),
        verticalArrangement = Arrangement.spacedBy(RulerSpacing.xs)
    ) {
        val newestFirst = state.segments.withIndex().reversed()
        itemsIndexed(newestFirst, key = { _, item -> item.index }) { _, item ->
            // `animateItem` fades a new row in and slides the older ones down to make room.
            Text(
                text = stringResource(
                    R.string.extend_segment_format,
                    item.index + 1,
                    Formatter.numeric(item.value.lengthMicrometres, MeasurementUnit.MM, FormatMode.DECIMAL)
                ),
                style = type.bodyMedium.tabular(),
                color = colors.onSurface.toCompose(),
                modifier = Modifier.animateItem()
            )
        }
    }
}

private fun formatNumber(value: Double): String {
    val format = NumberFormat.getInstance(Locale.getDefault())
    // One decimal, like the total it sits beside. "195.8 mm ± 11.29 mm" reads as if the bound were
    // known to a hundredth of a millimetre, which rule 7 says it is not.
    format.maximumFractionDigits = 1
    return format.format(value)
}
