package com.blueorbit.ruler.core.sensors

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * How far the phone has turned since the first edge, accumulated sample by sample.
 *
 * The angle *between* two gravity directions is 0–180° and has no direction, so a phone turned 200°
 * read 160° and a full turn read 0°. Adding up the small signed steps between consecutive samples gives
 * a turn that keeps counting past 180° and comes back down when the user turns back.
 *
 * A signed step needs an axis to be signed about:
 * - **Upright** (gravity mostly in the screen plane — the phone stood on its edge against the work) the
 *   axis is the screen normal, so the turn is the one the user sees.
 * - **Flat-ish** (laid on one face, then on another) the axis is unknown until the phone moves, so it is
 *   taken from the first clear movement ([AXIS_LOCK_DEG]) and kept. Until then the plain angle between
 *   the two directions is used, which is the same number for a small turn.
 *
 * Each step is measured between the samples *projected onto the plane of the turn*, so a hand that
 * wobbles out of that plane does not add to the angle.
 *
 * Pure and deterministic, so it is tested on the JVM with fixed vectors.
 */
class TurnTracker(reference: Gravity) {

    private val ref = reference.unit()
    private var previous = ref
    private var axis: Vec? = if (abs(ref.z) < UPRIGHT_MAX_Z) Vec(0.0, 0.0, 1.0) else null

    /** Signed accumulated turn in degrees. Its sign depends on the axis and carries no meaning to the user. */
    var turnDeg: Double = 0.0
        private set

    /** The reading: how far round the dial, 0 to 360. */
    val dialDeg: Double
        get() = (abs(turnDeg) % 360.0)

    fun update(sample: Gravity): Double {
        if (sample.length == 0.0) return dialDeg
        val current = sample.unit()

        val n = axis ?: run {
            val c = ref.cross(current)
            if (c.length > sin(Math.toRadians(AXIS_LOCK_DEG))) {
                // Oriented so the movement so far is positive, so the reading does not jump when the
                // axis locks.
                axis = c.unit()
                axis
            } else {
                null
            }
        }

        if (n == null) {
            turnDeg = LevelMath.angleBetweenDeg(ref.toGravity(), current.toGravity())
        } else {
            val p = previous.minus(n.times(previous.dot(n)))
            val c = current.minus(n.times(current.dot(n)))
            if (p.length > MIN_PROJECTION && c.length > MIN_PROJECTION) {
                turnDeg += Math.toDegrees(atan2(n.dot(p.cross(c)), p.dot(c)))
            }
        }
        previous = current
        return dialDeg
    }

    private data class Vec(val x: Double, val y: Double, val z: Double) {
        val length: Double get() = sqrt(x * x + y * y + z * z)
        fun unit(): Vec = Vec(x / length, y / length, z / length)
        fun dot(o: Vec): Double = x * o.x + y * o.y + z * o.z
        fun cross(o: Vec): Vec = Vec(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x)
        fun minus(o: Vec): Vec = Vec(x - o.x, y - o.y, z - o.z)
        fun times(s: Double): Vec = Vec(x * s, y * s, z * s)
        fun toGravity(): Gravity = Gravity(x, y, z)
    }

    private fun Gravity.unit(): Vec = Vec(x / length, y / length, z / length)

    private companion object {
        /** |z| of unit gravity below this (about 45° from flat or more) counts as upright. */
        const val UPRIGHT_MAX_Z = 0.7071
        /** The flat-ish axis is chosen once the phone has clearly moved, not from sensor noise. */
        const val AXIS_LOCK_DEG = 3.0
        /** A sample lying along the axis has no direction in the plane, so it adds nothing. */
        const val MIN_PROJECTION = 1e-6
    }
}
