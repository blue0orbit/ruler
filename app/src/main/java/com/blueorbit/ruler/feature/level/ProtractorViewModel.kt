package com.blueorbit.ruler.feature.level

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.blueorbit.ruler.core.entitlement.EntitlementRepository
import com.blueorbit.ruler.core.sensors.Gravity
import com.blueorbit.ruler.core.sensors.GravitySource
import com.blueorbit.ruler.core.sensors.LevelMath
import com.blueorbit.ruler.core.sensors.ProtractorMath
import com.blueorbit.ruler.core.sensors.SensorAccuracy
import com.blueorbit.ruler.core.sensors.TurnTracker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Where the user is in the two-tap measurement. The screen is built around this, not around nulls. */
enum class ProtractorStep {
    /** Nothing set: the plate shows the live tilt so the phone visibly responds before the first tap. */
    FIRST_EDGE,

    /** First edge held: the angle to it is live, and the second tap freezes it. */
    SECOND_EDGE,

    /** Both edges held: a finished reading. */
    DONE
}

data class ProtractorState(
    val current: Gravity = Gravity(0.0, 0.0, 1.0),
    val first: Gravity? = null,
    val second: Gravity? = null,
    val accuracy: SensorAccuracy = SensorAccuracy.UNKNOWN,
    val pro: Boolean = false,
    /** False once the reading flow has closed — the device has no rotation-vector sensor. */
    val sensorAvailable: Boolean = true,
    /** False until the first sensor reading arrives, so the plate never draws the placeholder vector. */
    val hasReading: Boolean = false,
    /**
     * The turn since the first edge, 0–360°, followed continuously ([TurnTracker]) so it keeps counting
     * past 180° and comes back when the user turns back. Frozen at the second tap.
     */
    val turnDeg: Double = 0.0
) {
    val step: ProtractorStep
        get() = when {
            first == null -> ProtractorStep.FIRST_EDGE
            second == null -> ProtractorStep.SECOND_EDGE
            else -> ProtractorStep.DONE
        }

    /** The held reading: how far the phone turned between the two edges, 0–360°. */
    fun angleDeg(): Double = if (first == null || second == null) 0.0 else turnDeg

    /**
     * What the plate and the big number show right now. Before the first tap it is the tilt from lying
     * flat — the instrument visibly responding to the hand. Between the taps it is the live angle to the
     * first edge, so the user can see where they are before they commit. After the second tap it is the
     * frozen reading.
     */
    fun displayedAngleDeg(): Double = when (step) {
        ProtractorStep.FIRST_EDGE -> LevelMath.tiltDeg(current)
        ProtractorStep.SECOND_EDGE -> turnDeg
        ProtractorStep.DONE -> angleDeg()
    }

    fun grads(): Double = ProtractorMath.grads(angleDeg())

    /** The stated assumption for the current accuracy status, not a measurement (rule 5). */
    fun typicalErrorDeg(): Double = LevelMath.typicalErrorDeg(accuracy)
}

class ProtractorViewModel(
    private val source: GravitySource,
    private val entitlement: EntitlementRepository
) : ViewModel() {

    private val _state = MutableStateFlow(ProtractorState())
    val state: StateFlow<ProtractorState> = _state.asStateFlow()

    /** Lives from the first edge to the second; the screen's rotation never touches it. */
    private var tracker: TurnTracker? = null

    init {
        // Finding 5: the entitlement is a flow, not a one-shot read taken before the sensor flow
        // started. A purchase made while this screen is open has to unlock it, and a device with no
        // rotation-vector sensor closes the reading flow immediately — which used to leave the screen
        // locked forever because the reading loop never ran.
        viewModelScope.launch {
            entitlement.state.collect { entitlementState ->
                _state.value = _state.value.copy(pro = entitlementState.unlocked)
            }
        }
        viewModelScope.launch {
            source.readings().collect { reading ->
                val s = _state.value
                val turn = if (s.step == ProtractorStep.SECOND_EDGE) {
                    tracker?.update(reading.gravity) ?: s.turnDeg
                } else {
                    s.turnDeg
                }
                _state.value = s.copy(
                    current = reading.gravity,
                    accuracy = LevelMath.accuracyFromStatus(reading.accuracyStatus),
                    hasReading = true,
                    turnDeg = turn
                )
            }
            _state.value = _state.value.copy(sensorAvailable = false)
        }
    }

    /**
     * The one primary action, whichever step the user is on. A single button that advances means the
     * user can never tap "second edge" before the first, which the old pair of buttons allowed (and
     * which silently produced an angle to the placeholder vector).
     */
    fun capture() {
        val s = _state.value
        if (!s.hasReading) return
        when (s.step) {
            ProtractorStep.FIRST_EDGE, ProtractorStep.DONE -> startFrom(s)
            ProtractorStep.SECOND_EDGE -> {
                tracker = null
                _state.value = s.copy(second = s.current)
            }
        }
    }

    fun setFirstReference() {
        if (!_state.value.hasReading) return
        startFrom(_state.value)
    }

    fun setSecondReference() {
        if (_state.value.step != ProtractorStep.SECOND_EDGE || !_state.value.hasReading) return
        tracker = null
        _state.value = _state.value.copy(second = _state.value.current)
    }

    fun clear() {
        tracker = null
        _state.value = _state.value.copy(first = null, second = null, turnDeg = 0.0)
    }

    private fun startFrom(s: ProtractorState) {
        tracker = TurnTracker(s.current)
        _state.value = s.copy(first = s.current, second = null, turnDeg = 0.0)
    }
}
