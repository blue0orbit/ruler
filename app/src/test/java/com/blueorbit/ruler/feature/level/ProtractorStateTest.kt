package com.blueorbit.ruler.feature.level

import com.blueorbit.ruler.core.sensors.Gravity
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class ProtractorStateTest {

    private fun tilted(degrees: Double): Gravity {
        val r = Math.toRadians(degrees)
        return Gravity(sin(r), 0.0, cos(r))
    }

    @Test
    fun beforeTheFirstEdgeTheNumberIsTheLiveTiltFromFlat(): Unit {
        val state = ProtractorState(current = tilted(20.0), hasReading = true)
        assertEquals(ProtractorStep.FIRST_EDGE, state.step)
        assertEquals(20.0, state.displayedAngleDeg(), 1e-6)
    }

    @Test
    fun betweenTheTapsTheNumberIsTheLiveAngleToTheFirstEdge(): Unit {
        val state = ProtractorState(current = tilted(50.0), first = tilted(20.0), hasReading = true, turnDeg = 30.0)
        assertEquals(ProtractorStep.SECOND_EDGE, state.step)
        assertEquals(30.0, state.displayedAngleDeg(), 1e-6)
    }

    @Test
    fun afterTheSecondTapTheNumberIsHeldWhateverThePhoneDoes(): Unit {
        val state = ProtractorState(
            current = tilted(80.0),
            first = tilted(10.0),
            second = tilted(55.0),
            hasReading = true,
            turnDeg = 45.0
        )
        assertEquals(ProtractorStep.DONE, state.step)
        assertEquals(45.0, state.displayedAngleDeg(), 1e-6)
    }
}
