package com.blueorbit.ruler.core.sensors

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class LevelMathTest {

    private fun gravityAt(tiltDegrees: Double, directionDegrees: Double = 0.0): Gravity {
        val tilt = Math.toRadians(tiltDegrees)
        val direction = Math.toRadians(directionDegrees)
        return Gravity(sin(tilt) * cos(direction), sin(tilt) * sin(direction), cos(tilt))
    }

    @Test
    fun flatSurfaceReadsZero() {
        assertEquals(0.0, LevelMath.tiltDeg(Gravity(0.0, 0.0, 1.0)), 1e-9)
    }

    @Test
    fun knownTiltIsRecovered(): Unit {
        assertEquals(2.0, LevelMath.tiltDeg(gravityAt(2.0)), 1e-6)
        assertEquals(45.0, LevelMath.tiltDeg(gravityAt(45.0)), 1e-6)
    }

    @Test
    fun aReadingInsideTheSensorAccuracyStaysInsideTheAcceptanceWindow(): Unit {
        // ±0.5° on a flat surface after zeroing (PLAN M8): a perturbation equal to the sensor's
        // accuracy must not move the reading by more than that accuracy.
        val measured = LevelMath.tiltDeg(gravityAt(2.5))
        assertTrue("reading moved by more than the stated accuracy", Math.abs(measured - 2.0) <= 0.5)
    }

    @Test
    fun orientationDoesNotChangeTheTiltOnlyTheDirection(): Unit {
        val gravity = gravityAt(3.0, 90.0)
        val portrait = LevelMath.directionOnScreenDeg(gravity, 0.0)
        val landscape = LevelMath.directionOnScreenDeg(gravity, 90.0)

        assertEquals(3.0, LevelMath.tiltDeg(gravity), 1e-6)
        assertEquals(90.0, portrait, 1e-6)
        assertEquals(0.0, landscape, 1e-6)
    }

    @Test
    fun theAccuracyStatusIsMappedNotConvertedIntoAnAngle(): Unit {
        // SensorEvent.accuracy is a status code (Android SensorStatusAccuracy), not radians. Reading it
        // as radians gave ±171.9° on the test device for a status of 3.
        assertEquals(SensorAccuracy.HIGH, LevelMath.accuracyFromStatus(3))
        assertEquals(SensorAccuracy.MEDIUM, LevelMath.accuracyFromStatus(2))
        assertEquals(SensorAccuracy.LOW, LevelMath.accuracyFromStatus(1))
        assertEquals(SensorAccuracy.UNRELIABLE, LevelMath.accuracyFromStatus(0))
        assertEquals(SensorAccuracy.UNKNOWN, LevelMath.accuracyFromStatus(-1))
    }

    @Test
    fun theAssumedErrorOnlyEverWidensAsTheReportedAccuracyWorsens(): Unit {
        val byAccuracy = listOf(
            SensorAccuracy.HIGH,
            SensorAccuracy.MEDIUM,
            SensorAccuracy.LOW,
            SensorAccuracy.UNRELIABLE
        )
        val errors = byAccuracy.map { LevelMath.typicalErrorDeg(it) }
        for (i in 1 until errors.size) {
            assertTrue(
                "${byAccuracy[i]} must not claim better accuracy than ${byAccuracy[i - 1]}",
                errors[i] > errors[i - 1]
            )
        }
        // The SPEC claim for the level is ±0.5° typical, which is the high-accuracy assumption.
        assertEquals(0.5, LevelMath.typicalErrorDeg(SensorAccuracy.HIGH), 1e-9)
    }

    @Test
    fun theBubbleIsDrawnOppositeTheDownhillDirection(): Unit {
        // M11R finding 10: the bubble floats to the high side, which is the downhill direction
        // rotated by 180 degrees. Drawing it on the downhill side showed the bubble on the low edge.
        val gravity = gravityAt(3.0, 90.0)
        assertEquals(270.0, LevelMath.bubbleOnScreenDeg(gravity, 0.0), 1e-6)
        assertEquals(180.0, LevelMath.bubbleOnScreenDeg(gravity, 90.0), 1e-6)
    }

    @Test
    fun anEmptyVectorProducesNoNumber(): Unit {
        assertEquals(0.0, LevelMath.tiltDeg(Gravity(0.0, 0.0, 0.0)), 1e-9)
        assertEquals(0.0, LevelMath.angleBetweenDeg(Gravity(0.0, 0.0, 0.0), Gravity(0.0, 0.0, 1.0)), 1e-9)
    }
}
