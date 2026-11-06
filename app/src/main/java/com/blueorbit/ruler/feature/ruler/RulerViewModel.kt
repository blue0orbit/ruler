package com.blueorbit.ruler.feature.ruler

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.blueorbit.ruler.core.calibration.Calibration
import com.blueorbit.ruler.core.calibration.CalibrationStore
import com.blueorbit.ruler.core.geometry.FormatMode
import com.blueorbit.ruler.core.geometry.MeasurementUnit
import com.blueorbit.ruler.core.settings.AppSettings
import com.blueorbit.ruler.core.settings.SettingsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class RulerState(
    val unit: MeasurementUnit = MeasurementUnit.MM,
    /** Only affects labels, never the physical geometry (rule 1). */
    val formatMode: FormatMode = FormatMode.FRACTION,
    /**
     * The span the user has drawn on the plate, in micrometres. 0 means nothing has been measured yet,
     * which is a state, not a reading of zero length (M17). It is UI state, not stored data: a measurement
     * only becomes data when it is saved to the log.
     */
    val measuredMicrometres: Long = 0L,
    /**
     * No default on purpose. A placeholder of `pixelsPerMm = 0.0` survives the first frames while the
     * stored calibration is still being read, and every tick, span and slider range on the plate is
     * derived by dividing by it — which is impossible geometry rather than a wrong number. The screen is
     * seeded with the device estimate instead, and the stored value refines it when the read lands.
     */
    val calibration: Calibration
)

class RulerViewModel(
    private val calibrationStore: CalibrationStore,
    private val settingsStore: SettingsStore,
    private val settings: MutableStateFlow<AppSettings>,
    deviceGuess: Calibration
) : ViewModel() {

    private val _state = MutableStateFlow(RulerState(calibration = deviceGuess))
    val state: StateFlow<RulerState> = _state.asStateFlow()

    init {
        refresh()
    }

    /** Re-read on screen entry so a calibration or unit saved elsewhere shows up without a restart. */
    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(
                calibration = calibrationStore.current(),
                unit = settings.value.unit
            )
        }
    }

    /** The unit is a setting (PLAN M9), so it is stored per device rather than held only in this VM. */
    fun setUnit(unit: MeasurementUnit) {
        // The screen changes first and the disk follows: waiting for the DataStore write before the
        // readout moved made every unit tap feel like it had been missed.
        _state.value = _state.value.copy(unit = unit)
        val updated = settings.value.copy(unit = unit)
        settings.value = updated
        viewModelScope.launch { settingsStore.save(updated) }
    }

    /**
     * Label size is not held here. It has exactly one source — `AppSettings.labelFontScale` — so the
     * ruler screen and the Settings slider cannot disagree about how big the labels are (DESIGN.md §7).
     */
    fun setFormatMode(mode: FormatMode) {
        _state.value = _state.value.copy(formatMode = mode)
    }

    /**
     * The end of the drawn span, set by the slider under the plate (M17), clamped to the ruler that drew
     * it. The screen supplies the limit because only the screen knows how wide the plate is: the stored
     * value must never be a length the plate cannot draw, or the number, the drawn rule and its knob would
     * disagree about how long the object is. A negative value is "nothing measured", not a negative length,
     * so the readout can never show one and Save can never store one.
     */
    fun setMeasuredMicrometres(micrometres: Long, maxSpanMicrometres: Long) {
        _state.value = _state.value.copy(measuredMicrometres = micrometres.coerceIn(0L, maxSpanMicrometres))
    }

    /**
     * The first-run instruction is stored (M17). Remembering it in the composition would bring the sentence
     * back on the next launch and teach a user who has already learned the action.
     */
    fun dismissFirstRunInstruction() {
        viewModelScope.launch {
            val updated = settings.value.copy(firstRunInstructionSeen = true)
            settingsStore.save(updated)
            settings.value = updated
        }
    }
}
