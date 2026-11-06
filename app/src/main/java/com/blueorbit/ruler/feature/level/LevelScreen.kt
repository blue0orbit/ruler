package com.blueorbit.ruler.feature.level

import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.view.Surface
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateOffsetAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.blueorbit.ruler.R
import com.blueorbit.ruler.core.di.ViewModelFactory
import com.blueorbit.ruler.core.haptics.DeviceHaptics
import com.blueorbit.ruler.core.settings.AppSettings
import com.blueorbit.ruler.core.ui.DetailScaffold
import com.blueorbit.ruler.core.ui.LocalAppMessages
import com.blueorbit.ruler.core.ui.LocalRulerColors
import com.blueorbit.ruler.core.ui.LocalRulerType
import com.blueorbit.ruler.core.ui.OutlinedChip
import com.blueorbit.ruler.core.ui.RulerDimensions
import com.blueorbit.ruler.core.ui.RulerSpacing
import com.blueorbit.ruler.core.ui.capsRegister
import com.blueorbit.ruler.core.ui.motionMillis
import com.blueorbit.ruler.core.ui.readoutEasing
import com.blueorbit.ruler.core.ui.reducedMotion
import com.blueorbit.ruler.core.ui.say
import com.blueorbit.ruler.core.ui.semantics.announceChanges
import com.blueorbit.ruler.core.ui.tabular
import com.blueorbit.ruler.core.ui.toCompose
import com.blueorbit.ruler.core.ui.useNavigationRail
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** The tilt at which the bubble reaches the edge of the vial. */
private const val FULL_TRAVEL_DEG = 15.0

/**
 * Bubble level (DESIGN.md §13): a detail screen reached from Tools. The reading is `readout` (44 sp,
 * tabular), the accuracy line is `meta`, and the instrument is capped at 420 dp instead of being
 * stretched across a wide screen.
 *
 * Colour-blind safe (SPEC 11): the bubble is read by shape and by text — ring, offset dot, and a
 * written reading. The accent the ring turns when level is an addition to the word "Level", never the
 * only signal.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LevelScreen(
    factory: ViewModelFactory,
    settings: StateFlow<AppSettings>,
    haptics: DeviceHaptics,
    onBack: () -> Unit,
    onOpenPro: (() -> Unit)? = null
) {
    val vm: LevelViewModel = viewModel(factory = factory)
    val state by vm.state.collectAsStateWithLifecycle()
    val appSettings by settings.collectAsStateWithLifecycle()
    val type = LocalRulerType.current
    val colors = LocalRulerColors.current
    val configuration = LocalConfiguration.current
    // Two panes on a wide window and on any landscape window: a phone on its side is too short for the
    // ring above the reading, and stacking them pushed the reading below the fold (rule 8).
    val twoPane = useNavigationRail(configuration.screenWidthDp) ||
        configuration.screenWidthDp > configuration.screenHeightDp

    // Rule 8: the tilt does not change with orientation, only the direction the bubble points to, so the
    // screen frame follows the display rotation — live, not read once per composition (see below).
    val rotationDeg = rememberDisplayRotationDeg()
    LaunchedEffect(rotationDeg) { vm.setDisplayRotation(rotationDeg) }

    // One confirm when the level *becomes* level, never per frame (DeviceHaptics.confirm). Saved across a
    // rotation so turning the phone while it lies level does not buzz again.
    var confirmedLevel by rememberSaveable { mutableStateOf(false) }
    val hapticsOn by rememberUpdatedState(appSettings.haptics)
    LaunchedEffect(state.level) {
        if (state.level && !confirmedLevel && hapticsOn) haptics.confirm()
        confirmedLevel = state.level
    }

    // Every change to the stored zero says so on the app's one message channel, with Undo instead of a
    // confirm dialog, and a write that failed says it failed rather than pretending (AppMessages.kt).
    val messages = LocalAppMessages.current
    val setText = stringResource(R.string.level_zero_set)
    val clearedText = stringResource(R.string.level_zero_cleared)
    val notSavedText = stringResource(R.string.level_zero_not_saved)
    val undoLabel = stringResource(R.string.level_undo)
    LaunchedEffect(vm, messages) {
        vm.messages.collect { event ->
            // Launched rather than awaited: `say` suspends until the snackbar goes away, and the next
            // message has to replace this one rather than queue behind it.
            launch {
                when (event) {
                    is LevelEvent.ZeroSet ->
                        if (messages.say(if (event.saved) setText else notSavedText, undoLabel)) {
                            vm.restoreZero(event.previous)
                        }
                    is LevelEvent.ZeroCleared ->
                        if (messages.say(if (event.saved) clearedText else notSavedText, undoLabel)) {
                            vm.restoreZero(event.previous)
                        }
                    LevelEvent.SaveFailed -> messages.say(notSavedText)
                }
            }
        }
    }

    // M19 (DESIGN.md §6): the Pro tools' header carries the word "Pro" when the gate is open — a word,
    // not colour (SPEC 11). While locked the gate sentence below says everything instead.
    DetailScaffold(
        onBack = onBack,
        title = stringResource(R.string.level_title),
        trailing = if (state.pro) ({ OutlinedChip(stringResource(R.string.settings_pro_title)) }) else null
    ) {
        when (state.phase) {
            LevelPhase.LOCKED -> {
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
            // A device with no rotation-vector sensor closes the reading flow. Showing 0.0° for that would
            // be a fabricated reading, so the screen says the sensor is missing instead (rule 5). It is a
            // real failure of the reading, so it is the one red line here.
            LevelPhase.MISSING -> {
                Text(
                    stringResource(R.string.level_sensor_missing),
                    style = type.bodyMedium,
                    color = colors.danger.toCompose()
                )
                return@DetailScaffold
            }
            LevelPhase.WAITING, LevelPhase.LIVE -> Unit
        }

        val hasReading = state.hasReading
        val angle = state.angleDeg()

        val instrument = @Composable {
            LevelInstrument(
                state = state,
                // In one column the ring may not take more than about half the height, so the reading under
                // it is still on screen in a short window. In two panes the row already bounds it.
                maxHeight = if (twoPane) null else (configuration.screenHeightDp * 0.55f).dp
            )
        }

        val reading = @Composable {
            // The caption names what the number is measured from (rule 4/5): with no zero set it is the raw
            // tilt from horizontal, not a reading against a surface the user never zeroed on.
            Text(
                stringResource(
                    if (state.zero.isSet()) R.string.level_caption_tilt_zeroed else R.string.level_caption_tilt
                ).capsRegister(),
                style = type.labelCaps,
                color = colors.onSurfaceSecondary.toCompose()
            )
            Text(
                // Before the first reading there is no number at all — "0°" would be a reading of nothing.
                // The degree sign is a symbol both shipped languages write identically after the number.
                text = if (hasReading) "${formatNumber(angle)}°" else stringResource(R.string.level_waiting),
                style = if (hasReading) type.readout else type.bodyLarge,
                color = if (hasReading) colors.onSurface.toCompose() else colors.onSurfaceSecondary.toCompose()
            )
            if (hasReading) {
                Text(
                    text = when {
                        state.zeroing -> stringResource(R.string.level_zeroing)
                        state.level -> stringResource(R.string.level_flat)
                        else -> stringResource(R.string.level_not_flat)
                    },
                    style = type.bodyLarge,
                    color = if (state.level && !state.zeroing) colors.accent.toCompose() else colors.onSurface.toCompose()
                )
                val axes = state.axisAngles()
                Text(
                    text = stringResource(
                        R.string.level_axes_format,
                        formatNumber(axes.sideToSideDeg),
                        formatNumber(axes.topToBottomDeg)
                    ),
                    style = type.meta.tabular(),
                    color = colors.onSurfaceSecondary.toCompose()
                )
            }
            // DESIGN.md §9: not zeroed is an empty state, not a zero reading. With a zero set but no reading
            // yet there is no direction to give, and the line above already says it is waiting.
            if (!state.zero.isSet() || hasReading) {
                Text(
                    if (state.zero.isSet()) {
                        stringResource(R.string.level_direction_format, formatNumber(state.downhillDeg()))
                    } else {
                        stringResource(R.string.level_empty_state_body)
                    },
                    style = type.bodyMedium,
                    color = colors.onSurfaceSecondary.toCompose()
                )
            }
            // Rule 13: a compass bearing for the bubble is precise-mode detail. In simple mode the
            // bubble on the ring already shows which way it floats.
            if (hasReading && !appSettings.simpleMode) {
                Text(
                    // A *direction*, not a magnitude: the bearing the bubble floats to. Labelled "Bubble
                    // offset 50.6°" next to "2.9° from horizontal" it read as two contradictory angles.
                    text = stringResource(R.string.level_bubble_direction_format, formatNumber(state.bubbleDeg())),
                    style = type.meta.tabular(),
                    color = colors.onSurfaceSecondary.toCompose()
                )
            }
            // Rule 5: the sensor reports a status, not an angle. The status is shown as what it is, and
            // the ± next to it is a stated assumption for that status, not a measurement.
            Text(
                text = stringResource(
                    R.string.level_accuracy_format,
                    stringResource(accuracyLabelRes(state.accuracy)),
                    formatNumber(state.typicalErrorDeg())
                ),
                style = type.meta,
                color = colors.onSurfaceSecondary.toCompose()
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(RulerSpacing.md),
                verticalArrangement = Arrangement.spacedBy(RulerSpacing.sm)
            ) {
                // Disabled until the first reading, and the line above says why ("Waiting for the
                // sensor…") — zeroing before it stored the placeholder vector as the user's surface.
                Button(onClick = { vm.zero() }, enabled = hasReading && !state.zeroing) {
                    Text(stringResource(R.string.level_zero))
                }
                // Rule 14: with nothing set there is nothing to undo, so the control is not shown at
                // all rather than shown greyed out.
                if (state.zero.isSet()) {
                    OutlinedButton(onClick = { vm.clearZero() }, enabled = !state.zeroing) {
                        Text(stringResource(R.string.level_clear_zero))
                    }
                }
            }
        }

        if (twoPane) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(RulerSpacing.xl)) {
                Box(Modifier.weight(1f)) { instrument() }
                // A scrollable reading column bounds the Row at the height the screen actually has, so
                // the instrument stops overflowing too. In a short landscape window the reading is taller
                // than the pane: without this the Set-as-flat action was drawn past the bottom edge and
                // could not be reached (M19 expanded screenshot, shots/m19-28).
                Column(
                    modifier = Modifier
                        .width(RulerDimensions.readoutPaneWidth)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(RulerSpacing.sm)
                ) {
                    reading()
                }
            }
        } else {
            // Scrolls: with a large font scale the reading is taller than a phone screen, and the
            // Set-as-flat action must stay reachable (rule 14).
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(RulerSpacing.sm)
            ) {
                instrument()
                reading()
            }
        }
    }
}

/**
 * The vial. The bubble is drawn through a spring rather than at the raw reading: at the sensor rate the
 * raw position moved in visible steps and shivered, and a real bubble glides and settles with a little
 * give. Under the system's "no animations" setting it follows the reading directly.
 */
@Composable
private fun LevelInstrument(state: LevelState, maxHeight: androidx.compose.ui.unit.Dp?) {
    val colors = LocalRulerColors.current
    val reduced = reducedMotion()

    // The target as a fraction of the bubble's travel, so the spring does not depend on the canvas size.
    // No snap to the centre near zero: the hysteresis decides "Level", and the spring settles the bubble.
    val target = if (state.hasReading) {
        val fraction = min(1.0, state.angleDeg() / FULL_TRAVEL_DEG)
        val r = Math.toRadians(state.bubbleDeg())
        Offset((fraction * cos(r)).toFloat(), (fraction * sin(r)).toFloat())
    } else {
        Offset.Zero
    }
    val bubble by animateOffsetAsState(
        targetValue = target,
        // The default visibility threshold for an Offset is half a pixel; in travel fractions that would
        // stop the spring half a vial short, so the threshold is stated in the units used here.
        animationSpec = if (reduced) {
            snap()
        } else {
            spring(
                dampingRatio = 0.7f,
                stiffness = Spring.StiffnessMediumLow,
                visibilityThreshold = Offset(0.0005f, 0.0005f)
            )
        },
        label = "bubble"
    )

    val emphasis = tween<androidx.compose.ui.graphics.Color>(motionMillis(220, reduced), easing = readoutEasing)
    val ringColor by animateColorAsState(
        if (state.level) colors.accent.toCompose() else colors.tickMajor.toCompose(),
        emphasis,
        label = "ring"
    )
    val bubbleColor by animateColorAsState(
        if (state.level) colors.accent.toCompose() else colors.accent.toCompose().copy(alpha = 0.6f),
        emphasis,
        label = "bubbleColour"
    )
    // The ring also thickens when level, so the change is a shape as well as a colour (SPEC 11).
    val ringWidth by animateFloatAsState(
        if (state.level) 3f else 2f,
        tween(motionMillis(220, reduced), easing = readoutEasing),
        label = "ringWidth"
    )

    // Finding 5 (TalkBack): this label is the only live region on the screen, and it only changes when
    // the status or the announced whole degree does — a region tied to the 0.1° reading spoke at
    // sensor rate and drowned out everything else.
    val description = when {
        !state.hasReading -> stringResource(R.string.level_waiting)
        state.level -> stringResource(R.string.level_announce_level_format, (state.announcedDegree ?: 0).toString())
        else -> stringResource(R.string.level_announce_not_level_format, (state.announcedDegree ?: 0).toString())
    }

    // Centred by an outer Box rather than `ColumnScope.align`, so the same block works in the column
    // layout and in the two-pane row layout.
    Box(Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .align(Alignment.Center)
                .widthIn(max = RulerDimensions.instrumentMaxSize)
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (maxHeight != null) Modifier.heightIn(max = maxHeight) else Modifier)
                    // Height first, so a wide-but-short window sizes the ring to the height it has instead
                    // of drawing a 420 dp square through the bottom edge.
                    .aspectRatio(1f, matchHeightConstraintsFirst = true)
                    .announceChanges(description)
            ) {
                val centre = Offset(size.width / 2f, size.height / 2f)
                val radius = min(size.width, size.height) / 2f - 24f
                drawCircle(color = ringColor, radius = radius, center = centre, style = Stroke(width = ringWidth.dp.toPx()))
                drawCircle(color = ringColor, radius = 3f, center = centre)

                // M19 (design-concept/ level): the datum crosshair — four short hairline ticks at the
                // cardinal points, so "the middle" is a shape the bubble has to reach rather than a dot
                // the reader has to spot (SPEC 11).
                val crosshairTick = radius * 0.18f
                for (deg in 0 until 360 step 90) {
                    val r = Math.toRadians(deg.toDouble())
                    val cosDeg = cos(r).toFloat()
                    val sinDeg = sin(r).toFloat()
                    drawLine(
                        ringColor,
                        Offset(centre.x + (radius - crosshairTick) * cosDeg, centre.y + (radius - crosshairTick) * sinDeg),
                        Offset(centre.x + radius * cosDeg, centre.y + radius * sinDeg),
                        1.dp.toPx()
                    )
                }

                // Before the first reading there is no bubble to draw: a centred bubble would read as
                // "level" about a sensor that has said nothing yet (rule 5).
                if (state.hasReading) {
                    // The bubble must stay inside the ring: its travel is the ring radius minus its own
                    // radius, so at full travel it touches the ring instead of being half outside it.
                    val bubbleRadius = radius * 0.22f
                    val maxTravel = radius - bubbleRadius - 2.dp.toPx()
                    // The spring can overshoot a little; the vial wall does not move.
                    val travel = bubble.getDistance()
                    val scale = if (travel > 1f) 1f / travel else 1f
                    drawCircle(
                        color = bubbleColor,
                        radius = bubbleRadius,
                        center = Offset(
                            centre.x + bubble.x * scale * maxTravel,
                            centre.y + bubble.y * scale * maxTravel
                        )
                    )
                }
            }
        }
    }
}

/**
 * The display rotation, kept current while the screen is shown. Finding 4: it used to be read during
 * composition only, and turning the phone from 90° straight to 270° is not a configuration change —
 * the size and orientation are the same — so nothing recomposed and the bubble ran the mirrored way.
 * A display listener sees the rotation itself change.
 */
@Composable
private fun rememberDisplayRotationDeg(): Double {
    val view = LocalView.current
    val context = LocalContext.current
    var rotation by remember { mutableIntStateOf(view.display?.rotation ?: Surface.ROTATION_0) }
    DisposableEffect(view, context) {
        val manager = context.getSystemService(DisplayManager::class.java)
        val listener = object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) = Unit
            override fun onDisplayRemoved(displayId: Int) = Unit
            override fun onDisplayChanged(displayId: Int) {
                val display = view.display ?: return
                if (displayId == display.displayId) rotation = display.rotation
            }
        }
        view.display?.let { rotation = it.rotation }
        manager?.registerDisplayListener(listener, Handler(Looper.getMainLooper()))
        onDispose { manager?.unregisterDisplayListener(listener) }
    }
    return rotation * 90.0
}

/** One decimal, always, so the readout does not change width between "1" and "1.2". */
private fun formatNumber(value: Double): String {
    val format = NumberFormat.getInstance(Locale.getDefault())
    format.maximumFractionDigits = 1
    format.minimumFractionDigits = 1
    return format.format(value)
}
