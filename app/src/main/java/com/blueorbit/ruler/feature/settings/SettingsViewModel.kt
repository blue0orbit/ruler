package com.blueorbit.ruler.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.blueorbit.ruler.core.calibration.Calibration
import com.blueorbit.ruler.core.calibration.CalibrationStore
import com.blueorbit.ruler.core.entitlement.EntitlementRepository
import com.blueorbit.ruler.core.geometry.MeasurementUnit
import com.blueorbit.ruler.core.settings.AppSettings
import com.blueorbit.ruler.core.settings.SettingsStore
import com.blueorbit.ruler.core.settings.ThemeChoice
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsState(
    val pro: Boolean = false,
    /**
     * Null until the store has been read. It must not default to a fabricated `Calibration`
     * (`pixelsPerMm = 0.0`): `setBezelInset` writes the whole record back, so a placeholder would
     * overwrite a real calibration with 0 px/mm and every later reading would be NaN/Inf
     * (M11R finding 4).
     */
    val calibration: Calibration? = null,
    val settings: AppSettings = AppSettings()
)

/**
 * Settings are written through the store so they persist, and the shared flow is updated so the
 * theme applies without a restart. Font scale is clamped because a label smaller than the tick it
 * labels is not readable — and it only ever reaches the labels, never the physical geometry (rule 1).
 */
class SettingsViewModel(
    private val entitlement: EntitlementRepository,
    private val calibrationStore: CalibrationStore,
    private val settingsStore: SettingsStore,
    private val settings: MutableStateFlow<AppSettings>,
    val deviceKey: String
) : ViewModel() {

    private val _state = MutableStateFlow(
        SettingsState(pro = entitlement.state.value.unlocked, settings = settings.value)
    )
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    init {
        refresh()
        // Followed, not read once: a purchase made from the Pro screen has to reach Settings while it is
        // still open, or the custom-theme chips stay hidden behind "part of Pro" for a user who just paid.
        // Every Pro feature derives from this one unlocked flag (rule 10).
        viewModelScope.launch {
            entitlement.state.collect { entitlementState ->
                _state.update { it.copy(pro = entitlementState.unlocked) }
            }
        }
    }

    /** Re-read on screen entry so a calibration saved elsewhere shows up without a restart. */
    fun refresh() {
        viewModelScope.launch {
            val calibration = calibrationStore.current()
            val stored = settingsStore.current()
            _state.update { it.copy(calibration = calibration, settings = stored) }
            settings.value = stored
        }
    }

    /**
     * Bezel inset is per-device data stored with the calibration (rule 4), not a code default. The text
     * has already been through [parseInsetMm], so a value that reaches here is one the user typed.
     *
     * Returns null when there is nothing to save yet, otherwise whether the write happened. The write
     * runs in `viewModelScope` and the caller only awaits it: leaving the screen mid-write must not
     * cancel a write that is already under way.
     */
    suspend fun saveBezelInset(insetMm: Double): Boolean? {
        // Nothing until the stored calibration has been read: writing from a placeholder would save
        // 0 px/mm over the real value (M11R finding 4).
        val current = _state.value.calibration ?: return null
        val updated = current.copy(bezelInsetMm = insetMm.coerceIn(0.0, MAX_INSET_MM))
        return viewModelScope.async {
            val saved = calibrationStore.save(updated)
            if (saved) _state.update { it.copy(calibration = updated) }
            saved
        }.await()
    }

    /**
     * Resets to the labelled guess and hands back what was there before, so the screen can offer Undo
     * instead of a confirm dialog. Null when the reset did not happen — there is then nothing to undo, and
     * the screen says the calibration could not be changed.
     */
    suspend fun resetCalibration(): Calibration? = viewModelScope.async {
        val previous = _state.value.calibration ?: calibrationStore.current()
        if (!calibrationStore.clear()) return@async null
        _state.update { it.copy(calibration = calibrationStore.current()) }
        previous
    }.await()

    /** Undo for [resetCalibration]: the calibration the user had goes back exactly as it was. */
    suspend fun restoreCalibration(previous: Calibration): Boolean = viewModelScope.async {
        val saved = calibrationStore.save(previous)
        if (saved) _state.update { it.copy(calibration = previous) }
        saved
    }.await()

    fun setUnit(unit: MeasurementUnit) = update(settings.value.copy(unit = unit))

    fun setTheme(choice: ThemeChoice) = update(settings.value.copy(theme = choice))

    /** Stored value only; the gate is applied when the palette is resolved, not here (rule 10). */
    fun setCustomTheme(name: String) = update(settings.value.copy(customTheme = name))

    fun setHaptics(enabled: Boolean) = update(settings.value.copy(haptics = enabled))

    /**
     * While the slider is being dragged the new size is applied in memory only. A drag is dozens of steps
     * a second, and writing the whole settings file for each one is disk work nobody asked for: the value
     * is persisted once, by [commitLabelFontScale], when the finger lifts.
     */
    fun previewLabelFontScale(scale: Double) {
        val updated = settings.value.copy(labelFontScale = scale.coerceIn(0.8, 2.0))
        settings.value = updated
        _state.update { it.copy(settings = updated) }
    }

    fun commitLabelFontScale() {
        val current = settings.value
        viewModelScope.launch { settingsStore.save(current) }
    }

    /**
     * Simple / precise (rule 12). Stored like any other setting; what changes is what the screens show,
     * never what is measured (rule 10).
     */
    fun setSimpleMode(simple: Boolean) = update(settings.value.copy(simpleMode = simple))

    /**
     * Onboarding (DESIGN.md §8): Skip and "Calibrate now" both set the flag, and Settings → "Show
     * intro" clears it. It is a setting like any other, so it goes through the same store.
     */
    fun setOnboardingSeen(seen: Boolean) = update(settings.value.copy(onboardingSeen = seen))

    /**
     * The screen follows the choice at once and the write follows it. Waiting for the file before moving
     * the switch made a tap feel ignored for as long as the disk took, and the store never throws
     * (`SettingsStore.save` swallows a failed write), so there is no outcome to wait for.
     */
    private fun update(updated: AppSettings) {
        settings.value = updated
        _state.update { it.copy(settings = updated) }
        viewModelScope.launch { settingsStore.save(updated) }
    }

    companion object {
        /** A bezel or case wider than this is not a bezel; the field refuses it rather than clamping. */
        const val MAX_INSET_MM = 20.0
    }
}
