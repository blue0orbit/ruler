package com.blueorbit.ruler.feature.ringsize

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.blueorbit.ruler.core.calibration.Calibration
import com.blueorbit.ruler.core.calibration.CalibrationStore
import com.blueorbit.ruler.core.geometry.Physical
import com.blueorbit.ruler.core.geometry.RingSizeMatch
import com.blueorbit.ruler.core.geometry.RingSizeSystem
import com.blueorbit.ruler.core.geometry.RingSizes
import com.blueorbit.ruler.core.log.LogRepository
import com.blueorbit.ruler.core.log.Measurement
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * The two ways M21 measures a ring.
 *
 * RING lays the ring on the plate and sizes a drawn circle to its **inner** edge — the plate gives the
 * diameter directly. FINGER is the paper-strip method: the user wraps a strip round the finger, marks
 * it, and lays that length on the ruler, so the plate measures a **circumference**. Both end at the
 * same inner circle, which is why one screen and one table serve them.
 */
enum class RingMeasureMode { RING, FINGER }

data class RingSizeState(
    /**
     * No default on purpose (the same rule as `RulerState`): the circle, its slider range and the ±
     * buttons are all derived by dividing by `pixelsPerMm`, so a placeholder of 0.0 during the first
     * frames is impossible geometry rather than a wrong number. The screen is seeded with the device
     * estimate and the stored calibration refines it when the read lands.
     */
    val calibration: Calibration,
    val mode: RingMeasureMode = RingMeasureMode.RING,
    /** Which system the headline size, the ± in sizes and the saved label are stated in. */
    val system: RingSizeSystem = RingSizeSystem.US,
    /**
     * What the plate currently draws, in micrometres: the drawn circle's inner diameter in [RING] mode,
     * the paper strip's length in [FINGER] mode. One field, so the drawing, the number, the sizes and
     * Save cannot disagree about which drawing they describe (rule 5).
     */
    val drawnMicrometres: Long = 0L,
    /**
     * False until the user has drawn something. A size read off a prefilled circle would be the M11R
     * finding-3 bug again — a value the user never measured, stamped as a reading.
     */
    val aligned: Boolean = false,
    /**
     * What the window can draw *now*, in micrometres. 0 until the screen has measured its own plate.
     * Kept apart from [drawnMicrometres] for the same reason the ruler screen keeps two values: a
     * rotation makes the plate shorter, and the drawing the user made must survive it rather than be
     * quietly shortened.
     */
    val plateLimitMicrometres: Long = 0L
)

class RingSizeViewModel(
    private val calibrationStore: CalibrationStore,
    private val logRepository: LogRepository,
    deviceGuess: Calibration,
    private val clock: () -> Long = { System.currentTimeMillis() }
) : ViewModel() {

    private val _state = MutableStateFlow(RingSizeState(calibration = deviceGuess))
    val state: StateFlow<RingSizeState> = _state.asStateFlow()

    init {
        refresh()
    }

    /** Re-read on entry, so a calibration made on the wizard shows here without a restart. */
    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(calibration = calibrationStore.current())
        }
    }

    /** Switching method changes what the plate draws, so the old drawing cannot carry over. */
    fun setMode(mode: RingMeasureMode) {
        _state.value = _state.value.copy(mode = mode, drawnMicrometres = 0L, aligned = false)
    }

    fun setSystem(system: RingSizeSystem) {
        _state.value = _state.value.copy(system = system)
    }

    /** The plate drew `micrometres`. Clamped here, so the slider, the drawing and the number agree. */
    fun setDrawn(micrometres: Long, maxMicrometres: Long) {
        val drawn = micrometres.coerceIn(0L, maxMicrometres)
        _state.value = _state.value.copy(
            drawnMicrometres = drawn,
            aligned = drawn > 0L,
            plateLimitMicrometres = maxMicrometres
        )
    }

    /**
     * The plate measured itself. Called whenever the window changes (a rotation, a fold, a pane
     * change) so the drawing, the number and the sizes describe a circle this screen can actually
     * draw, while the length the user set stays as it was.
     */
    fun setPlateLimit(maxMicrometres: Long) {
        if (_state.value.plateLimitMicrometres == maxMicrometres) return
        _state.value = _state.value.copy(plateLimitMicrometres = maxMicrometres)
    }

    /**
     * The drawing as this plate can show it: what the user set, capped by what the window can draw.
     * Every read below goes through this, so the circle, the millimetres, the sizes and the saved row
     * cannot disagree about which drawing they describe (rule 5).
     */
    val drawableMicrometres: Long
        get() = _state.value.drawnMicrometres.coerceIn(0L, _state.value.plateLimitMicrometres)

    /** The same drawing as the plate drew it, for a screen that has to redraw it. */
    val drawableIsClaimed: Boolean
        get() = drawableMicrometres > 0L

    /**
     * The ± fine buttons. They move to the **next rung** of the selected system rather than by a fixed
     * millimetre, so a finger on the button lands the circle exactly on a published size — which is
     * what makes the size claim true instead of approximate.
     *
     * @param direction +1 for the next size up, -1 for the next size down.
     */
    fun stepRung(direction: Int, maxMicrometres: Long) {
        val system = _state.value.system
        val ladder = RingSizes.ladder(system)
        val current = innerDiameterMm()
        if (current <= 0.0) return
        val nearest = ladder.minBy { abs(it.innerDiameterMm - current) }
        val index = (ladder.indexOf(nearest) + direction).coerceIn(0, ladder.lastIndex)
        val rung = ladder[index]
        val drawn = plateMicrometresForDiameter(rung.innerDiameterMm, _state.value.mode)
        // A rung the plate cannot draw is not reachable by a button either (rule 5: the control cannot
        // ask for more instrument than the screen has).
        if (drawn > maxMicrometres) return
        _state.value = _state.value.copy(drawnMicrometres = drawn, aligned = true)
    }

    /** The inner circle the current drawing describes, in millimetres. 0.0 when nothing is drawn. */
    fun innerDiameterMm(): Double = when (_state.value.mode) {
        RingMeasureMode.RING -> drawableMicrometres.toDouble() / Physical.MICROMETRES_PER_MM
        RingMeasureMode.FINGER -> drawableMicrometres.toDouble() /
            (Physical.MICROMETRES_PER_MM * PI)
    }

    /** The same circle as the length the plate has to draw. */
    fun plateMicrometresForDiameter(diameterMm: Double, mode: RingMeasureMode = _state.value.mode): Long =
        if (mode == RingMeasureMode.RING) {
            (diameterMm * Physical.MICROMETRES_PER_MM).roundToLong()
        } else {
            (RingSizes.circumferenceFromDiameter(diameterMm) * Physical.MICROMETRES_PER_MM).roundToLong()
        }

    /** The size for one system, or null when nothing has been drawn yet. */
    fun match(system: RingSizeSystem = _state.value.system): RingSizeMatch? {
        if (!drawableIsClaimed) return null
        return RingSizes.match(system, innerDiameterMm())
    }

    /**
     * Rule 5, in millimetres of diameter: what the stored calibration's error budget is worth on a
     * circle this size. It is what decides whether the screen may name one size or only a range.
     */
    fun diameterToleranceMm(): Double =
        innerDiameterMm() * _state.value.calibration.errorBudgetMmPer100Mm / 100.0

    /** Sizes of uncertainty for one system — the ± the calibrated screen shows beside the size. */
    fun sizesOfUncertainty(system: RingSizeSystem): Double =
        RingSizes.sizesOfUncertainty(system, diameterToleranceMm())

    fun sizeRange(system: RingSizeSystem): Pair<String, String> =
        RingSizes.sizeRange(system, innerDiameterMm(), diameterToleranceMm())

    /**
     * The rung step for the haptic tick while dragging (ROADMAP M21: a tick per size step), stated in
     * the units the *plate* is drawn in. A rung is a step of inner **diameter**, so in the paper method
     * — where the plate draws a circumference — one rung is π times as much length. Kept in micrometres
     * so the screen can hand it to the same tested crossing rule the ruler uses.
     */
    fun rungStepMicrometres(): Long {
        val rungMm = _state.value.system.rungStepMm
        val plateMm = if (_state.value.mode == RingMeasureMode.FINGER) rungMm * PI else rungMm
        return (plateMm * Physical.MICROMETRES_PER_MM).roundToLong()
    }

    /**
     * Save the **diameter** with the size in its name (ROADMAP M21), so a row read back months later
     * says both what was measured and what it meant. The log stores lengths in micrometres like every
     * other row, so it stays searchable and sortable (rule 10: a gate, not a fork).
     */
    suspend fun save(label: String): Long {
        val calibration = _state.value.calibration
        if (!drawableIsClaimed) return 0L
        return logRepository.save(
            Measurement(
                valueMicrometres = plateMicrometresForDiameter(innerDiameterMm(), RingMeasureMode.RING),
                unit = "MM",
                label = label,
                note = "",
                source = calibration.source.name,
                errorBudgetMmPer100Mm = calibration.errorBudgetMmPer100Mm,
                // Stamped here, not left at 0: the log sorts by date (M5), and a row that arrives dated
                // 1 January 1970 sorts to the bottom of the ledger and reads as a broken row (seen on the
                // Fold at M21, where the saved ring size appeared under every other row).
                createdAtMillis = clock()
            )
        )
    }

    /** Undo on the saved row, the same way the ruler screen undoes its own save. */
    suspend fun deleteSaved(id: Long) {
        logRepository.deleteById(id)
    }
}
