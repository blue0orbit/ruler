package com.blueorbit.ruler.core.calibration

import com.blueorbit.ruler.core.sensors.Gravity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

/** Rule 4: the zero is data the user created on a surface, never a code default. */
class LevelZeroTest {

    private fun gravityAt(tiltDegrees: Double): Gravity {
        val tilt = Math.toRadians(tiltDegrees)
        return Gravity(sin(tilt), 0.0, cos(tilt))
    }

    @Test
    fun withoutAZeroTheReadingIsTheRawTiltFromHorizontal() {
        val unset = LevelZero()
        assertFalse(unset.isSet())
        assertEquals(1.5, unset.angleDeg(gravityAt(1.5)), 1e-6)
    }

    @Test
    fun zeroingMeasuresFromTheSurfaceItWasSetOnNotFromHorizontal() {
        val zero = LevelZero(gravityAt(3.0))
        assertTrue(zero.isSet())
        assertEquals(0.0, zero.angleDeg(gravityAt(3.0)), 1e-6)
        assertEquals(2.0, zero.angleDeg(gravityAt(5.0)), 1e-6)
    }

    @Test
    fun aZeroSetOnATiltedSurfaceDoesNotClaimTheSurfaceIsFlat() {
        // A phone zeroed on a surface that is 3° off horizontal still reports the true angle to the
        // reference, so the UI never implies more accuracy than the method supports (rule 5).
        val zero = LevelZero(gravityAt(3.0))
        assertEquals(3.0, zero.angleDeg(Gravity(0.0, 0.0, 1.0)), 1e-6)
    }
}
