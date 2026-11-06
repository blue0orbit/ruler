package com.blueorbit.ruler.core.sensors

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LowPassTest {

    @Test
    fun theFirstSampleIsPassedThroughUnchanged(): Unit {
        val filter = LowPass(0.2)
        assertEquals(Gravity(0.1, 0.2, 0.9), filter.filter(Gravity(0.1, 0.2, 0.9)))
    }

    @Test
    fun aStepIsFollowedMonotonicallyAndSettles(): Unit {
        val filter = LowPass(0.2)
        filter.filter(Gravity(0.0, 0.0, 1.0))
        var previous = 0.0
        repeat(40) {
            val x = filter.filter(Gravity(1.0, 0.0, 1.0)).x
            assertTrue("smoothing must never overshoot or go backwards", x >= previous && x <= 1.0)
            previous = x
        }
        assertEquals(1.0, previous, 1e-3)
    }

    @Test
    fun alphaOfOneIsNoSmoothing(): Unit {
        val filter = LowPass(1.0)
        filter.filter(Gravity(0.0, 0.0, 1.0))
        assertEquals(Gravity(1.0, 0.0, 0.0), filter.filter(Gravity(1.0, 0.0, 0.0)))
    }
}
