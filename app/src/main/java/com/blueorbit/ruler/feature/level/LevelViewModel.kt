package com.blueorbit.ruler.feature.level

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.blueorbit.ruler.core.calibration.LevelZero
import com.blueorbit.ruler.core.calibration.LevelZeroStore
import com.blueorbit.ruler.core.entitlement.EntitlementRepository
import com.blueorbit.ruler.core.sensors.AxisAngles
import com.blueorbit.ruler.core.sensors.Gravity
import com.blueorbit.ruler.core.sensors.GravitySource
import com.blueorbit.ruler.core.sensors.LevelMath
import com.blueorbit.ruler.core.sensors.SensorAccuracy
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** What the level can say about the sensor right now. The screen is built around this, not around nulls. */
enum class LevelPhase {
    /** Pro is not unlocked. The sensor is not running at all. */
    LOCKED,

    /** Unlocked, but no reading has arrived yet. Nothing may be shown as a reading (rule 5). */
    WAITING,

    /** Readings are arriving. */
    LIVE,

    /** The reading flow closed: the device has no rotation-vector sensor. */
    MISSING
}

data class LevelState(
    val phase: LevelPhase = LevelPhase.WAITING,
    /** The latest reading; null until the first real one, so the placeholder vector is never drawn. */
    val gravity: Gravity? = null,
    val zero: LevelZero = LevelZero(),
    val accuracy: SensorAccuracy = SensorAccuracy.UNKNOWN,
    val displayRotationDeg: Double = 0.0,
    /** "Level", with hysteresis (LevelMath.isLevel). */
    val level: Boolean = false,
    /** The whole degree TalkBack announces; null while there is no reading to announce. */
    val announcedDegree: Int? = null,
    /** True for the half second a zero is being averaged. */
    val zeroing: Boolean = false
) {
    val pro: Boolean get() = phase != LevelPhase.LOCKED

    val hasReading: Boolean get() = phase == LevelPhase.LIVE && gravity != null

    /** The reading as if the stored zero were flat, so every number and the bubble share one frame. */
    private fun relative(): Gravity? {
        val current = gravity ?: return null
        val reference = zero.reference ?: return current
        return LevelMath.relativeToReference(current, reference)
    }

    /** Reading after zeroing, in degrees. 0 only as a value to draw with; the screen checks [hasReading]. */
    fun angleDeg(): Double = gravity?.let { zero.angleDeg(it) } ?: 0.0

    /** Bubble direction in the screen frame — the *high* side, which is where a bubble floats. */
    fun bubbleDeg(): Double = relative()?.let { LevelMath.bubbleOnScreenDeg(it, displayRotationDeg) } ?: 0.0

    /** Downhill direction in the screen frame, for the written reading. */
    fun downhillDeg(): Double = relative()?.let { LevelMath.directionOnScreenDeg(it, displayRotationDeg) } ?: 0.0

    /** The tilt split along the screen's two axes, relative to the zero when one is set. */
    fun axisAngles(): AxisAngles =
        relative()?.let { LevelMath.axisAnglesDeg(it, displayRotationDeg) } ?: AxisAngles(0.0, 0.0)

    /** The stated assumption for the current accuracy status, not a measurement (rule 5). */
    fun typicalErrorDeg(): Double = LevelMath.typicalErrorDeg(accuracy)
}

/** One-shot results the screen turns into a snackbar. Each carries what Undo needs to put back. */
sealed interface LevelEvent {
    data class ZeroSet(val previous: LevelZero, val saved: Boolean) : LevelEvent
    data class ZeroCleared(val previous: LevelZero, val saved: Boolean) : LevelEvent
    data object SaveFailed : LevelEvent
}

/** The sensor side of the state: which phase, and the reading when there is one. */
private data class SensorSnapshot(
    val phase: LevelPhase,
    val gravity: Gravity? = null,
    val accuracy: SensorAccuracy = SensorAccuracy.UNKNOWN
)

private data class Controls(val zero: LevelZero, val rotationDeg: Double, val zeroing: Boolean)

@OptIn(ExperimentalCoroutinesApi::class)
class LevelViewModel(
    private val source: GravitySource,
    private val entitlement: EntitlementRepository,
    private val zeroStore: LevelZeroStore
) : ViewModel() {

    private val zero = MutableStateFlow(LevelZero())
    private val displayRotation = MutableStateFlow(0.0)
    private val zeroing = MutableStateFlow(false)

    /** Set once the user touches the zero, so a slow initial load cannot overwrite their choice. */
    private var zeroTouched = false

    /**
     * Raw samples for averaging a zero. A StateFlow would conflate equal readings and repeat one reading
     * whenever an unrelated field changed, which would weight the average; this sees each reading once.
     */
    private val samples = MutableSharedFlow<Gravity>(extraBufferCapacity = 64)

    private val events = Channel<LevelEvent>(Channel.BUFFERED)
    val messages: Flow<LevelEvent> = events.receiveAsFlow()

    /**
     * The sensor runs only while Pro is unlocked. Finding 5 still holds — the entitlement is a flow, so a
     * purchase made on this screen unlocks it — but a locked screen no longer holds the sensor open
     * behind a sentence telling the user it is locked.
     */
    private val sensor: Flow<SensorSnapshot> = entitlement.state
        .map { it.unlocked }
        .distinctUntilChanged()
        .flatMapLatest { unlocked ->
            if (!unlocked) {
                flowOf(SensorSnapshot(LevelPhase.LOCKED))
            } else {
                source.readings()
                    .map { reading ->
                        samples.tryEmit(reading.gravity)
                        SensorSnapshot(
                            LevelPhase.LIVE,
                            reading.gravity,
                            LevelMath.accuracyFromStatus(reading.accuracyStatus)
                        )
                    }
                    .onStart { emit(SensorSnapshot(LevelPhase.WAITING)) }
                    // A normal completion is a device with no rotation-vector sensor — not "flat", and the
                    // screen must say so. Cancellation (leaving the screen, losing Pro) is not a completion.
                    .onCompletion { cause -> if (cause == null) emit(SensorSnapshot(LevelPhase.MISSING)) }
            }
        }

    /**
     * Finding 2: the sensor used to be collected in `viewModelScope`, so it ran for as long as the
     * ViewModel lived — backgrounded, behind another screen, while locked. Sharing it `WhileSubscribed`
     * lets `collectAsStateWithLifecycle` stop it when the screen is not visible; the five seconds ride
     * out a rotation without unregistering and re-registering the listener.
     */
    val state: StateFlow<LevelState> = combine(
        sensor,
        combine(zero, displayRotation, zeroing) { z, r, busy -> Controls(z, r, busy) }
    ) { snapshot, controls ->
        LevelState(
            phase = snapshot.phase,
            gravity = snapshot.gravity,
            zero = controls.zero,
            accuracy = snapshot.accuracy,
            displayRotationDeg = controls.rotationDeg,
            zeroing = controls.zeroing
        )
    }
        // The two values that need memory — the hysteresis and the announced degree — are folded over the
        // stream rather than kept in the screen, so they are tested here with the rest of the state.
        .runningFold(LevelState()) { previous, next ->
            if (!next.hasReading) {
                next
            } else {
                val angle = next.angleDeg()
                next.copy(
                    level = LevelMath.isLevel(angle, previous.level),
                    announcedDegree = LevelMath.announcedWholeDegree(angle, previous.announcedDegree)
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), LevelState())

    init {
        // Finding 19: the zero is stored data, so it is read back rather than reset each launch.
        viewModelScope.launch {
            val stored = zeroStore.load()
            if (!zeroTouched) zero.value = stored
        }
    }

    /**
     * Zero = the surface the user is on, stored as data, never as a code default (rule 4). It is the mean
     * of [ZERO_SAMPLE_MILLIS] of readings rather than one sample, and it cannot run before the first real
     * reading — that used to store the (0, 0, 1) placeholder as the user's surface.
     */
    fun zero() {
        val current = state.value
        val fallback = current.gravity
        if (!current.hasReading || fallback == null || zeroing.value) return
        zeroing.value = true
        viewModelScope.launch {
            val collected = mutableListOf<Gravity>()
            withTimeoutOrNull(ZERO_SAMPLE_MILLIS) {
                samples.collect { collected += it }
            }
            val reference = LevelMath.averageGravity(collected) ?: fallback
            val previous = zero.value
            zeroTouched = true
            zero.value = LevelZero(reference)
            zeroing.value = false
            events.send(LevelEvent.ZeroSet(previous, zeroStore.save(LevelZero(reference))))
        }
    }

    fun clearZero() {
        val previous = zero.value
        if (!previous.isSet()) return
        zeroTouched = true
        zero.value = LevelZero()
        viewModelScope.launch {
            events.send(LevelEvent.ZeroCleared(previous, zeroStore.save(LevelZero())))
        }
    }

    /** Undo from the snackbar: puts back the zero that was in place before the last change. */
    fun restoreZero(previous: LevelZero) {
        zeroTouched = true
        zero.value = previous
        viewModelScope.launch {
            if (!zeroStore.save(previous)) events.send(LevelEvent.SaveFailed)
        }
    }

    fun setDisplayRotation(degrees: Double) {
        displayRotation.value = degrees
    }

    companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
        const val ZERO_SAMPLE_MILLIS = 500L
    }
}
