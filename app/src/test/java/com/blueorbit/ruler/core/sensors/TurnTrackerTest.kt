package com.blueorbit.ruler.core.sensors

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class TurnTrackerTest {

    /** Phone stood upright on its edge, turned in the screen plane by [deg]. */
    private fun upright(deg: Double): Gravity {
        val r = Math.toRadians(deg)
        return Gravity(sin(r), -cos(r), 0.0)
    }

    /** Phone tilted from lying flat about its long axis by [deg]. */
    private fun rolled(deg: Double): Gravity {
        val r = Math.toRadians(deg)
        return Gravity(sin(r), 0.0, cos(r))
    }

    private fun turn(tracker: TurnTracker, to: (Double) -> Gravity, from: Double, until: Double, step: Double) {
        var d = from
        while (if (step > 0) d <= until else d >= until) {
            tracker.update(to(d))
            d += step
        }
    }

    @Test
    fun anUprightTurnKeepsCountingPast180(): Unit {
        val tracker = TurnTracker(upright(0.0))
        turn(tracker, ::upright, 0.0, 250.0, 2.0)
        assertEquals(250.0, tracker.dialDeg, 1e-6)
    }

    @Test
    fun turningBackComesBackDown(): Unit {
        val tracker = TurnTracker(upright(0.0))
        turn(tracker, ::upright, 0.0, 200.0, 2.0)
        turn(tracker, ::upright, 200.0, 40.0, -2.0)
        assertEquals(40.0, tracker.dialDeg, 1e-6)
    }

    @Test
    fun theOtherDirectionReadsTheSameSize(): Unit {
        val tracker = TurnTracker(upright(0.0))
        turn(tracker, ::upright, 0.0, -120.0, -2.0)
        assertEquals(120.0, tracker.dialDeg, 1e-6)
    }

    @Test
    fun aFullTurnReadsZeroAgainNotSomethingElse(): Unit {
        val tracker = TurnTracker(upright(0.0))
        turn(tracker, ::upright, 0.0, 360.0, 3.0)
        // 0 and 360 are the same place on the dial; floating point may land on either side of it.
        val dial = tracker.dialDeg
        assertEquals(0.0, minOf(dial, 360.0 - dial), 1e-6)
    }

    @Test
    fun aFlatPhoneRolledOverPast180IsTracked(): Unit {
        val tracker = TurnTracker(rolled(0.0))
        turn(tracker, ::rolled, 0.0, 210.0, 1.5)
        assertEquals(210.0, tracker.dialDeg, 1e-6)
    }

    @Test
    fun wobbleOutOfTheTurnPlaneDoesNotAddToTheAngle(): Unit {
        val tracker = TurnTracker(upright(0.0))
        turn(tracker, ::upright, 0.0, 90.0, 2.0)
        // Tip the top of the phone 5° towards the user while holding the 90° turn.
        val r = Math.toRadians(5.0)
        val base = upright(90.0)
        tracker.update(Gravity(base.x * cos(r), base.y * cos(r), sin(r)))
        assertEquals(90.0, tracker.dialDeg, 1e-6)
    }

    @Test
    fun aSmallTurnBeforeTheAxisLocksIsThePlainAngle(): Unit {
        val tracker = TurnTracker(rolled(0.0))
        tracker.update(rolled(2.0))
        assertEquals(2.0, tracker.dialDeg, 1e-6)
    }
}
