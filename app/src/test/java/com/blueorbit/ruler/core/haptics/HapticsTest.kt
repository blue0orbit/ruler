package com.blueorbit.ruler.core.haptics

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** PLAN M9 acceptance: haptic fires on tick crossings and can be disabled. */
class HapticsTest {

    private val step = 10_000L // 10 mm ticks

    @Test
    fun crossingATickBoundaryFires(): Unit {
        assertTrue(Haptics.crossedTick(9_999L, 10_001L, step))
        assertTrue(Haptics.crossedTick(25_000L, 30_001L, step))
        assertFalse(Haptics.crossedTick(20_000L, 25_000L, step)) // both inside tick 2
    }

    @Test
    fun stayingInsideTheSameTickDoesNotFire(): Unit {
        assertFalse(Haptics.crossedTick(10_100L, 10_900L, step))
        assertFalse(Haptics.crossedTick(10_100L, 10_100L, step))
    }

    @Test
    fun crossingBackwardsAcrossATickAlsoFires(): Unit {
        assertTrue(Haptics.crossedTick(30_000L, 25_000L, step))
    }

    @Test
    fun aDisabledPreferenceIsRespected(): Unit {
        assertFalse(Haptics.shouldFire(false, 9_999L, 10_001L, step))
        assertTrue(Haptics.shouldFire(true, 9_999L, 10_001L, step))
    }

    @Test
    fun anInvalidStepCannotProduceAFire(): Unit {
        assertFalse(Haptics.crossedTick(0L, 100_000L, 0L))
        assertFalse(Haptics.crossedTick(0L, 100_000L, -1L))
    }
}
