package com.blueorbit.ruler.feature.level

import com.blueorbit.ruler.core.calibration.InMemoryLevelZeroStore
import com.blueorbit.ruler.core.calibration.LevelZero
import com.blueorbit.ruler.core.calibration.LevelZeroStore
import com.blueorbit.ruler.core.entitlement.FakeEntitlementRepository
import com.blueorbit.ruler.core.sensors.Gravity
import com.blueorbit.ruler.core.sensors.GravitySource
import com.blueorbit.ruler.core.sensors.LevelMath
import com.blueorbit.ruler.core.sensors.SensorReading
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

/**
 * The level's production pass: the sensor runs only while someone is looking and Pro is unlocked, there
 * is no reading before the first sample, the zero is an average, and every zero change can be undone.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LevelViewModelTest {

    private fun gravityAt(tiltDegrees: Double, directionDegrees: Double = 0.0): Gravity {
        val tilt = Math.toRadians(tiltDegrees)
        val direction = Math.toRadians(directionDegrees)
        return Gravity(sin(tilt) * cos(direction), sin(tilt) * sin(direction), cos(tilt))
    }

    /** A source that counts its listeners, so a test can see whether the sensor is registered. */
    private class CountingSource(private val upstream: Flow<SensorReading>) : GravitySource {
        var listeners = 0
        var everStarted = false
        override fun readings(): Flow<SensorReading> = upstream
            .onStart {
                listeners++
                everStarted = true
            }
            .onCompletion { listeners-- }
    }

    private class FailingStore : LevelZeroStore {
        override suspend fun load(): LevelZero = LevelZero()
        override suspend fun save(zero: LevelZero): Boolean = false
    }

    /**
     * `viewModelScope` needs a Main dispatcher and `runTest` does not supply one (M16). It is installed
     * for the whole test rather than inside `runTest`: the shared sensor flow keeps a five-second stop
     * timer in `viewModelScope`, and that timer must still find the test Main after the body returns.
     */
    private val main = UnconfinedTestDispatcher()

    @Before
    fun installMain() {
        Dispatchers.setMain(main)
    }

    @After
    fun removeMain() {
        Dispatchers.resetMain()
    }

    private fun levelOnMain(body: suspend TestScope.() -> Unit) = runTest(main) { body() }

    private fun TestScope.watch(vm: LevelViewModel) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
    }

    @Test
    fun beforeTheFirstReadingTheLevelIsWaitingNotLevel() = levelOnMain {
        val sensor = MutableSharedFlow<SensorReading>()
        val vm = LevelViewModel(CountingSource(sensor), FakeEntitlementRepository(true), InMemoryLevelZeroStore())
        watch(vm)

        assertEquals(LevelPhase.WAITING, vm.state.value.phase)
        assertFalse(vm.state.value.hasReading)
        assertFalse(vm.state.value.level)
        assertNull(vm.state.value.announcedDegree)
    }

    @Test
    fun zeroingBeforeTheFirstReadingStoresNothing() = levelOnMain {
        val store = InMemoryLevelZeroStore()
        val vm = LevelViewModel(CountingSource(MutableSharedFlow()), FakeEntitlementRepository(true), store)
        watch(vm)

        vm.zero()
        advanceUntilIdle()

        assertFalse(store.load().isSet())
        assertFalse(vm.state.value.zero.isSet())
    }

    @Test
    fun aLockedLevelNeverStartsTheSensor() = levelOnMain {
        val source = CountingSource(MutableSharedFlow())
        val vm = LevelViewModel(source, FakeEntitlementRepository(false), InMemoryLevelZeroStore())
        watch(vm)

        assertEquals(LevelPhase.LOCKED, vm.state.value.phase)
        assertFalse(source.everStarted)
    }

    @Test
    fun theSensorStopsWhenNobodyIsWatching() = levelOnMain {
        val source = CountingSource(MutableSharedFlow())
        val vm = LevelViewModel(source, FakeEntitlementRepository(true), InMemoryLevelZeroStore())
        assertEquals(0, source.listeners)

        val watcher = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        assertEquals(1, source.listeners)

        watcher.cancel()
        advanceTimeBy(LevelViewModel.STOP_TIMEOUT_MILLIS + 1)
        runCurrent()
        assertEquals(0, source.listeners)
    }

    @Test
    fun aDeviceWithNoSensorSaysSo() = levelOnMain {
        val vm = LevelViewModel(CountingSource(emptyFlow()), FakeEntitlementRepository(true), InMemoryLevelZeroStore())
        watch(vm)

        assertEquals(LevelPhase.MISSING, vm.state.value.phase)
        assertFalse(vm.state.value.hasReading)
    }

    @Test
    fun levelHasHysteresis() = levelOnMain {
        val sensor = MutableSharedFlow<SensorReading>()
        val vm = LevelViewModel(CountingSource(sensor), FakeEntitlementRepository(true), InMemoryLevelZeroStore())
        watch(vm)

        sensor.emit(SensorReading(gravityAt(0.5), 3))
        assertFalse(vm.state.value.level)
        sensor.emit(SensorReading(gravityAt(0.2), 3))
        assertTrue(vm.state.value.level)
        sensor.emit(SensorReading(gravityAt(0.5), 3))
        assertTrue("0.5° is inside the band, so it stays level", vm.state.value.level)
        sensor.emit(SensorReading(gravityAt(0.7), 3))
        assertFalse(vm.state.value.level)
    }

    @Test
    fun theZeroIsTheAverageOfHalfASecondOfSamples() = levelOnMain {
        // The hand wobbles 1° either side of flat; one sample would store a 1° tilt as "flat".
        var i = 0
        val wobbling = flow {
            while (true) {
                emit(SensorReading(gravityAt(1.0, if (i++ % 2 == 0) 0.0 else 180.0), 3))
                delay(20)
            }
        }
        val store = InMemoryLevelZeroStore()
        val vm = LevelViewModel(CountingSource(wobbling), FakeEntitlementRepository(true), store)
        watch(vm)
        val events = mutableListOf<LevelEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.messages.collect { events += it } }

        vm.zero()
        assertTrue(vm.state.value.zeroing)
        advanceTimeBy(LevelViewModel.ZERO_SAMPLE_MILLIS + 50)
        runCurrent()

        val reference = store.load().reference!!
        assertTrue(LevelMath.tiltDeg(reference) < 0.1)
        assertFalse(vm.state.value.zeroing)
        assertEquals(listOf<LevelEvent>(LevelEvent.ZeroSet(LevelZero(), saved = true)), events)
    }

    @Test
    fun aZeroThatCouldNotBeSavedSaysSo() = levelOnMain {
        val sensor = MutableSharedFlow<SensorReading>()
        val vm = LevelViewModel(CountingSource(sensor), FakeEntitlementRepository(true), FailingStore())
        watch(vm)
        sensor.emit(SensorReading(gravityAt(2.0), 3))

        vm.zero()
        advanceTimeBy(LevelViewModel.ZERO_SAMPLE_MILLIS + 50)
        runCurrent()

        val event = vm.messages.first()
        assertEquals(false, (event as LevelEvent.ZeroSet).saved)
        // The zero still applies for this session — only the write failed.
        assertTrue(vm.state.value.zero.isSet())
    }

    @Test
    fun undoPutsThePreviousZeroBack() = levelOnMain {
        val earlier = LevelZero(gravityAt(3.0, 45.0))
        val store = InMemoryLevelZeroStore(earlier)
        val sensor = MutableSharedFlow<SensorReading>()
        val vm = LevelViewModel(CountingSource(sensor), FakeEntitlementRepository(true), store)
        watch(vm)
        sensor.emit(SensorReading(gravityAt(1.0), 3))

        vm.clearZero()
        runCurrent()
        val cleared = vm.messages.first() as LevelEvent.ZeroCleared
        assertFalse(store.load().isSet())

        vm.restoreZero(cleared.previous)
        runCurrent()
        assertEquals(earlier, store.load())
        assertEquals(earlier, vm.state.value.zero)
    }
}
