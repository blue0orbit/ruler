package com.blueorbit.ruler.feature.calibration

import com.blueorbit.ruler.core.calibration.Calibration
import com.blueorbit.ruler.core.calibration.CalibrationSource
import com.blueorbit.ruler.core.calibration.CalibrationStore
import com.blueorbit.ruler.core.calibration.InMemoryCalibrationStore
import com.blueorbit.ruler.core.calibration.ReferenceKind
import com.blueorbit.ruler.core.calibration.References
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The decisions the calibration wizard makes for itself (M16), tested instead of being re-derived on a
 * phone: which reference it opens with on a given screen, that a reference the user chose is not second
 * guessed, and that "Done" only reports a calibration once the write has actually happened.
 *
 * The numbers are the test device's: a 1 080-px panel at 390 dpi, drawn in a 1 041-px column.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CalibrationViewModelTest {

    private val guess = Calibration(
        pixelsPerMm = 15.35,
        source = CalibrationSource.DEVICE_DEFAULT,
        errorBudgetMmPer100Mm = 5.0
    )
    private val columnPx = 1041.0

    private fun store() = InMemoryCalibrationStore(guess)

    /**
     * `viewModelScope` is built on `Dispatchers.Main`, and `runTest` does not supply one — without
     * `setMain` every test here died in the ViewModel's own constructor with "Module with the Main
     * dispatcher had failed to initialize". The dispatcher is tied to this test's scheduler so
     * [advanceUntilIdle] means the store read has really happened before the first assertion.
     */
    private fun wizardOnMain(
        store: CalibrationStore,
        body: suspend TestScope.(CalibrationViewModel) -> Unit
    ) = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            val vm = CalibrationViewModel(store, guess)
            advanceUntilIdle()
            body(vm)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun theWizardOpensWithTheCardEdgeItCanActuallyDraw() {
        wizardOnMain(store()) { vm ->
            // A card's long edge is 1 314 px here, so the guided path has to start with the short edge.
            vm.fitReferenceToViewport(columnPx)
            assertEquals(References.cardShortEdge.id, vm.state.value.reference.id)

            // A screen wide enough for the long edge gets it instead: a longer reference is a better reading.
            vm.fitReferenceToViewport(2000.0)
            assertEquals(References.cardLongEdge.id, vm.state.value.reference.id)
        }
    }

    @Test
    fun aReferenceTheUserPickedIsNotReplacedByTheAppsGuess() {
        wizardOnMain(store()) { vm ->
            vm.selectReference(References.cardLongEdge)
            vm.fitReferenceToViewport(columnPx)
            assertEquals(References.cardLongEdge.id, vm.state.value.reference.id)
        }
    }

    /**
     * The manual span is a drawing the user has to measure, so it is fitted whatever the user picked —
     * a 100 mm drawing does not fit a phone at any phone density, which is what made the manual path
     * impossible to finish.
     */
    @Test
    fun theManualSpanIsFittedToTheScreenItIsDrawnOn() {
        wizardOnMain(store()) { vm ->
            vm.selectKind(ReferenceKind.MANUAL)
            vm.fitReferenceToViewport(columnPx)

            assertEquals(60.0, vm.state.value.reference.knownMm, 1e-9)
            // Nothing is prefilled: the number in that field is what the user's ruler says, not a guess
            // the app invites them to agree with.
            assertEquals(0.0, vm.state.value.measuredMm, 1e-9)
            assertEquals(false, vm.state.value.aligned)
        }
    }

    /**
     * Seen on the test device: picking a kind changed the reference, but only the screen knew the width
     * it had to draw in, so the new reference was never fitted and the wizard offered a 100 mm drawing on
     * a screen that can draw 62.7 mm. The ViewModel now remembers the width and refits on its own.
     */
    @Test
    fun pickingAnotherKindFitsItsReferenceToTheWidthTheScreenReported() {
        wizardOnMain(store()) { vm ->
            vm.fitReferenceToViewport(columnPx)

            vm.selectKind(ReferenceKind.MANUAL)
            assertEquals(60.0, vm.state.value.reference.knownMm, 1e-9)

            vm.selectKind(ReferenceKind.CARD)
            assertEquals(References.cardShortEdge.id, vm.state.value.reference.id)
        }
    }

    /** An empty or unreadable manual field is not a measurement, and Done must not pretend otherwise. */
    @Test
    fun aTypedNumberIsWhatMakesTheManualPathAReading() {
        val store = store()
        wizardOnMain(store) { vm ->
            vm.fitReferenceToViewport(columnPx)
            vm.selectKind(ReferenceKind.MANUAL)

            vm.setMeasuredMm(0.0)
            assertEquals(false, vm.state.value.aligned)
            vm.save()
            advanceUntilIdle()
            assertNull(vm.justSaved.value)

            vm.setMeasuredMm(61.5)
            assertEquals(true, vm.state.value.aligned)
            vm.save()
            advanceUntilIdle()

            assertEquals(CalibrationSource.MANUAL, vm.justSaved.value?.source)
            assertEquals(15.35 * 60.0 / 61.5, vm.justSaved.value?.pixelsPerMm ?: 0.0, 1e-6)
        }
    }

    @Test
    fun doneReportsTheCalibrationOnlyAfterTheWriteHasHappened() {
        val store = store()
        wizardOnMain(store) { vm ->
            vm.fitReferenceToViewport(columnPx)
            vm.setMeasuredPx(829.0)
            assertNull(vm.justSaved.value)

            vm.save()
            advanceUntilIdle()

            val saved = vm.justSaved.value
            assertEquals(CalibrationSource.CARD, saved?.source)
            assertEquals(829.0 / References.cardShortEdge.knownMm, saved?.pixelsPerMm ?: 0.0, 1e-6)
            assertEquals(true, vm.state.value.current.verified)
            assertEquals(CalibrationSource.CARD, store.current().source)
        }
    }

    /** M11R finding 3, kept honest in the guided path too: a prefilled position is not a reading. */
    @Test
    fun nothingIsSavedUntilTheUserHasTakenAReading() {
        val store = store()
        wizardOnMain(store) { vm ->
            vm.fitReferenceToViewport(columnPx)
            vm.save()
            advanceUntilIdle()

            assertNull(vm.justSaved.value)
            assertEquals(false, vm.state.value.current.verified)
            assertEquals(CalibrationSource.DEVICE_DEFAULT, store.current().source)
        }
    }

    /** Changing the reference clears the reading flag: the new prefill is not a reading either. */
    @Test
    fun pickingAnotherReferenceEndsTheReadingInTheOldOne() {
        wizardOnMain(store()) { vm ->
            vm.fitReferenceToViewport(columnPx)
            vm.setMeasuredPx(829.0)
            assertEquals(true, vm.state.value.aligned)

            vm.selectReference(References.cardLongEdge)
            assertEquals(false, vm.state.value.aligned)
            assertEquals(85.60 * 15.35, vm.state.value.measuredPx, 0.01)
        }
    }

    /**
     * A store that cannot write (a damaged file) used to throw out of `viewModelScope.launch` and close
     * the app. Now the press is answered with a failure, the wizard keeps the reading so Save can be
     * pressed again, and nothing claims the calibration was kept.
     */
    @Test
    fun aWriteTheStoreRefusesIsReportedAndKeepsTheReading() {
        val refusing = object : CalibrationStore {
            override suspend fun current(): Calibration = guess
            override suspend fun save(calibration: Calibration): Boolean = false
            override suspend fun clear(): Boolean = false
        }
        wizardOnMain(refusing) { vm ->
            vm.fitReferenceToViewport(columnPx)
            vm.setMeasuredPx(829.0)

            vm.save()
            advanceUntilIdle()

            assertEquals(false, vm.saveOutcomes.first())
            assertNull("a refused write was reported as saved", vm.justSaved.value)
            assertEquals(true, vm.state.value.aligned)
            assertEquals(CalibrationSource.DEVICE_DEFAULT, vm.state.value.current.source)
        }
    }

    /** Precise mode has no navigation to stand in for a confirmation, so the save itself answers. */
    @Test
    fun aSuccessfulSaveAnswersOnce() {
        wizardOnMain(store()) { vm ->
            vm.fitReferenceToViewport(columnPx)
            vm.setMeasuredPx(829.0)

            vm.save()
            advanceUntilIdle()

            assertEquals(true, vm.saveOutcomes.first())
        }
    }
}
