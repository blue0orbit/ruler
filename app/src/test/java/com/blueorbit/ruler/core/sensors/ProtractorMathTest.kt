package com.blueorbit.ruler.core.sensors

import org.junit.Assert.assertEquals
import kotlin.math.cos
import kotlin.math.sin
import org.junit.Test

class ProtractorMathTest {

    private fun gravityAt(tiltDegrees: Double): Gravity {
        val tilt = Math.toRadians(tiltDegrees)
        return Gravity(sin(tilt), 0.0, cos(tilt))
    }

    @Test
    fun degreesConvertToGradsExactly(): Unit {
        assertEquals(100.0, ProtractorMath.grads(90.0), 1e-9)
        assertEquals(0.0, ProtractorMath.grads(0.0), 1e-9)
        assertEquals(400.0, ProtractorMath.grads(360.0), 1e-9)
    }

    @Test
    fun referenceLineModeMeasuresTheAngleBetweenTwoReferences(): Unit {
        val first = gravityAt(0.0)
        val second = gravityAt(30.0)
        assertEquals(30.0, ProtractorMath.angleBetweenReferences(first, second), 1e-6)
    }

    @Test
    fun theAngleBetweenReferencesIsTheSameInBothOrientations(): Unit {
        // Rule 8: the protractor measures an angle between two lines, which does not depend on how
        // the phone is held, so portrait and landscape must agree.
        val first = Gravity(0.0, 0.0, 1.0)
        val second = Gravity(sin(Math.toRadians(45.0)), 0.0, cos(Math.toRadians(45.0)))
        val inPortrait = ProtractorMath.angleBetweenReferences(first, second)
        val inLandscape = ProtractorMath.angleBetweenReferences(second, first)
        assertEquals(inPortrait, inLandscape, 1e-9)
        assertEquals(45.0, inPortrait, 1e-6)
    }
}
