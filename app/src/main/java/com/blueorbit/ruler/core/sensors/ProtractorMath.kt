package com.blueorbit.ruler.core.sensors

/**
 * Protractor maths. Degrees and grads are both offered (SPEC v1.1), and the reference-line mode is
 * the angle between two stored directions rather than an absolute bearing, which is what a
 * protractor actually measures and what a phone can measure without a magnetometer.
 */
object ProtractorMath {

    fun grads(degrees: Double): Double = degrees * 10.0 / 9.0

    /** Reference-line mode: first reference, then the second; the reading is the angle between them. */
    fun angleBetweenReferences(first: Gravity, second: Gravity): Double =
        LevelMath.angleBetweenDeg(first, second)
}
