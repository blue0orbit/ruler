package com.blueorbit.ruler.feature.settings

import com.blueorbit.ruler.core.calibration.Calibration
import com.blueorbit.ruler.core.calibration.CalibrationSource
import com.blueorbit.ruler.core.calibration.CalibrationStore
import com.blueorbit.ruler.core.calibration.InMemoryCalibrationStore
import com.blueorbit.ruler.core.entitlement.EntitlementRepository
import com.blueorbit.ruler.core.entitlement.EntitlementRule
import com.blueorbit.ruler.core.entitlement.EntitlementState
import com.blueorbit.ruler.core.entitlement.EntitlementStatus
import com.blueorbit.ruler.core.settings.AppSettings
import com.blueorbit.ruler.core.settings.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Settings decisions that used to be untested: the reset that now offers Undo instead of nothing,
 * the label-size slider that wrote the file on every drag step, the Pro state that was read once, and a
 * calibration write that fails.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val guess = Calibration(15.35, CalibrationSource.DEVICE_DEFAULT, 5.0)
    private val card = Calibration(15.40, CalibrationSource.CARD, 0.185, bezelInsetMm = 1.5)

    private class Entitlement : EntitlementRepository {
        private val flow = MutableStateFlow(EntitlementState(EntitlementStatus.LOCKED, EntitlementRule.PRODUCT_ID))
        override val state: StateFlow<EntitlementState> = flow
        fun unlock() {
            flow.value = EntitlementState(EntitlementStatus.UNLOCKED, EntitlementRule.PRODUCT_ID)
        }
    }

    /** Counts writes, which is what the slider finding is about. */
    private class CountingSettingsStore : SettingsStore {
        var stored = AppSettings()
        var writes = 0
        override suspend fun current(): AppSettings = stored
        override suspend fun save(settings: AppSettings) {
            stored = settings
            writes++
        }
    }

    private fun settingsOnMain(
        calibrationStore: CalibrationStore,
        body: suspend TestScope.(SettingsViewModel, Entitlement, CountingSettingsStore) -> Unit
    ) = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            val entitlement = Entitlement()
            val settingsStore = CountingSettingsStore()
            val vm = SettingsViewModel(
                entitlement,
                calibrationStore,
                settingsStore,
                MutableStateFlow(AppSettings()),
                deviceKey = "test"
            )
            advanceUntilIdle()
            body(vm, entitlement, settingsStore)
        } finally {
            Dispatchers.resetMain()
        }
    }

    private fun storeWith(calibration: Calibration) = InMemoryCalibrationStore(guess)
        .also { kotlinx.coroutines.runBlocking { it.save(calibration) } }

    @Test
    fun resetHandsBackThePreviousCalibrationAndUndoRestoresIt() =
        settingsOnMain(storeWith(card)) { vm, _, _ ->
            assertEquals(card, vm.state.value.calibration)

            val previous = vm.resetCalibration()
            assertEquals(card, previous)
            assertEquals(guess, vm.state.value.calibration)

            assertTrue(vm.restoreCalibration(previous!!))
            assertEquals(card, vm.state.value.calibration)
        }

    @Test
    fun aResetTheStoreRefusesHasNothingToUndo() {
        val refusing = object : CalibrationStore {
            override suspend fun current(): Calibration = card
            override suspend fun save(calibration: Calibration): Boolean = false
            override suspend fun clear(): Boolean = false
        }
        settingsOnMain(refusing) { vm, _, _ ->
            assertNull(vm.resetCalibration())
            assertEquals(card, vm.state.value.calibration)
            assertEquals(false, vm.saveBezelInset(2.0))
            assertEquals("a refused write changed the screen", 1.5, vm.state.value.calibration!!.bezelInsetMm, 1e-9)
        }
    }

    @Test
    fun aSavedInsetKeepsTheRestOfTheCalibration() =
        settingsOnMain(storeWith(card)) { vm, _, _ ->
            assertEquals(true, vm.saveBezelInset(2.5))
            assertEquals(card.copy(bezelInsetMm = 2.5), vm.state.value.calibration)
        }

    @Test
    fun theLabelSizeIsWrittenOnceWhenTheDragEnds() =
        settingsOnMain(storeWith(card)) { vm, _, store ->
            val before = store.writes
            listOf(1.1, 1.2, 1.3, 1.4).forEach { vm.previewLabelFontScale(it) }
            advanceUntilIdle()
            assertEquals("the drag wrote the settings file", before, store.writes)
            assertEquals(1.4, vm.state.value.settings.labelFontScale, 1e-9)

            vm.commitLabelFontScale()
            advanceUntilIdle()
            assertEquals(before + 1, store.writes)
            assertEquals(1.4, store.stored.labelFontScale, 1e-9)
        }

    @Test
    fun aPurchaseReachesTheOpenSettingsScreen() =
        settingsOnMain(storeWith(card)) { vm, entitlement, _ ->
            assertFalse(vm.state.value.pro)
            entitlement.unlock()
            advanceUntilIdle()
            assertTrue("Settings kept the free view after Pro was unlocked", vm.state.value.pro)

            // A refresh (screen re-entry) must not put the stale value back.
            vm.refresh()
            advanceUntilIdle()
            assertTrue(vm.state.value.pro)
        }
}
