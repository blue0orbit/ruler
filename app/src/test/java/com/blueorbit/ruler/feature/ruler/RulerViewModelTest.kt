package com.blueorbit.ruler.feature.ruler

import com.blueorbit.ruler.core.calibration.Calibration
import com.blueorbit.ruler.core.calibration.CalibrationSource
import com.blueorbit.ruler.core.calibration.InMemoryCalibrationStore
import com.blueorbit.ruler.core.geometry.FormatMode
import com.blueorbit.ruler.core.geometry.MeasurementUnit
import com.blueorbit.ruler.core.settings.AppSettings
import com.blueorbit.ruler.core.settings.InMemorySettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * M17 put the measurement itself on the ruler screen. Before it, the screen had no way to say where an
 * object's far edge was, so "Save this measurement" saved the length of the screen and called it a
 * reading — which is why a first-time user could not see how a measurement got into the log at all.
 *
 * The two rules these tests hold: nothing counts as measured until the user draws a span, and a dismissed
 * instruction stays dismissed because it is stored rather than remembered.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RulerViewModelTest {

    private val screenGuess = Calibration(
        pixelsPerMm = 15.35,
        source = CalibrationSource.DEVICE_DEFAULT,
        errorBudgetMmPer100Mm = 5.0
    )

    /**
     * `viewModelScope` needs a Main dispatcher and `runTest` does not supply one, so every ViewModel test
     * in this app installs one (M16). The shared settings flow is the one the screen reads, exactly as
     * `AppContainer` hands it out.
     */
    private fun rulerOnMain(
        startSettings: AppSettings = AppSettings(),
        body: suspend TestScope.(RulerViewModel, InMemorySettingsStore, MutableStateFlow<AppSettings>) -> Unit
    ) = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val store = InMemorySettingsStore(startSettings)
        val shared = MutableStateFlow(startSettings)
        try {
            val vm = RulerViewModel(InMemoryCalibrationStore(screenGuess), store, shared, screenGuess)
            advanceUntilIdle()
            body(vm, store, shared)
        } finally {
            Dispatchers.resetMain()
        }
    }

    /** 0 is a state, not a reading of zero length: the ruler screen opens showing the ruler's own span. */
    @Test
    fun theRulerOpensWithNothingMeasuredYet() {
        rulerOnMain { vm, _, _ ->
            assertEquals(0L, vm.state.value.measuredMicrometres)
        }
    }

    /**
     * The screen is seeded with the device estimate. `RulerState.calibration` has no default precisely
     * because a `pixelsPerMm = 0.0` placeholder would survive the first frames, and every tick, span and
     * slider range on the plate is derived by dividing by it.
     */
    @Test
    fun theRulerOpensWithADensityRatherThanWithNone() {
        rulerOnMain { vm, _, _ ->
            assertEquals(screenGuess, vm.state.value.calibration)
        }
    }

    /**
     * A span shorter than the start line clamps to "nothing measured" rather than a negative number, so
     * the readout cannot show a length that does not exist and Save cannot save one.
     */
    @Test
    fun aSpanShorterThanTheStartLineIsNothingMeasuredNotANegativeLength() {
        rulerOnMain { vm, _, _ ->
            vm.setMeasuredMicrometres(-25_000L, maxSpanMicrometres = 65_000L)
            assertEquals(0L, vm.state.value.measuredMicrometres)

            vm.setMeasuredMicrometres(42_000L, maxSpanMicrometres = 65_000L)
            assertEquals(42_000L, vm.state.value.measuredMicrometres)
        }
    }

    /**
     * The slider works in millimetres, which are Float, so its round trip back to micrometres can land one
     * above the ruler it was measured on. A stored span longer than the plate would make the number, the
     * drawn rule, its knob and the saved row disagree about how long the object is — and a span left on a
     * ruler that has since become shorter would be saved as a zero. The ViewModel clamps to the limit the
     * screen reports (rule 5).
     */
    @Test
    fun aSpanLongerThanTheRulerItWasDrawnOnIsStoredAsThatRulerNotAsMore() {
        rulerOnMain { vm, _, _ ->
            vm.setMeasuredMicrometres(65_279L, maxSpanMicrometres = 65_278L)
            assertEquals(65_278L, vm.state.value.measuredMicrometres)

            vm.setMeasuredMicrometres(10_000L, maxSpanMicrometres = 0L)
            assertEquals(0L, vm.state.value.measuredMicrometres)
        }
    }

    /** Dismissing the instruction writes it, and the screen sees it through the shared settings flow. */
    @Test
    fun dismissingTheFirstRunInstructionWritesItToTheStoreAndToTheScreen() {
        rulerOnMain { vm, store, shared ->
            assertEquals(false, store.current().firstRunInstructionSeen)

            vm.dismissFirstRunInstruction()
            advanceUntilIdle()

            assertEquals(true, store.current().firstRunInstructionSeen)
            assertEquals(true, shared.value.firstRunInstructionSeen)
        }
    }

    /**
     * The instruction is stored rather than held in the composition on purpose: remembering it in memory
     * would show the sentence again on the next launch and teach a user who has already measured something.
     */
    @Test
    fun aDismissedInstructionIsNotOfferedAgainToAUserWhoAlreadyDismissedIt() {
        rulerOnMain { vm, store, _ ->
            vm.dismissFirstRunInstruction()
            advanceUntilIdle()

            val nextLaunch = MutableStateFlow(store.current())
            assertEquals(true, nextLaunch.value.firstRunInstructionSeen)
        }
    }

    /**
     * M18 gap closed: the unit chips on the ruler had no test at all. The unit is one stored setting that
     * the ruler, the Log rows and Settings all read, so tapping `cm` here has to reach the store and the
     * shared flow — a unit that only this screen knew would make the same saved row read differently on
     * the next screen.
     */
    @Test
    fun changingTheUnitWritesItToTheStoreTheSharedSettingsAndThisScreen() {
        rulerOnMain { vm, store, shared ->
            vm.setUnit(MeasurementUnit.INCH)
            advanceUntilIdle()

            assertEquals(MeasurementUnit.INCH, vm.state.value.unit)
            assertEquals(MeasurementUnit.INCH, store.current().unit)
            assertEquals(MeasurementUnit.INCH, shared.value.unit)
        }
    }

    /**
     * The other half of the same gap. Decimal versus fraction is how the numbers on this plate are
     * written, not what is measured, and it is deliberately not a stored setting: leaving the ruler in
     * fraction mode must not change what another screen writes down (rule 1 — labels and geometry are
     * separate, and `Formatter` covers the writing itself).
     */
    @Test
    fun changingTheFormatModeChangesThisScreenAndNotTheStoredSettings() {
        rulerOnMain { vm, store, shared ->
            vm.setFormatMode(FormatMode.DECIMAL)

            assertEquals(FormatMode.DECIMAL, vm.state.value.formatMode)
            assertEquals(AppSettings(), store.current())
            assertEquals(AppSettings(), shared.value)
        }
    }
}
