package com.blueorbit.ruler.core.sensors

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Gravity direction in the device frame, normalised. The rotation vector gives this without a
 * camera permission — sensor access is not an Android permission (DECISIONS.md).
 */
data class Gravity(val x: Double, val y: Double, val z: Double) {

    val length: Double get() = sqrt(x * x + y * y + z * z)
}

/** Accuracy status reported by the sensor, as Android defines it. */
enum class SensorAccuracy { UNRELIABLE, LOW, MEDIUM, HIGH, UNKNOWN }

/**
 * Level maths, kept out of the ViewModel so it is testable. A level is only as good as the sensor,
 * so the reading is always shown with the confidence the sensor reports (rule 5).
 */
object LevelMath {

    /** Angle between the device normal and gravity: 0° when the device lies flat. */
    fun tiltDeg(gravity: Gravity): Double {
        if (gravity.length == 0.0) return 0.0
        return Math.toDegrees(acos(gravity.z / gravity.length))
    }

    /** Downhill direction in the device frame, 0° along the device x axis. */
    fun directionDeg(gravity: Gravity): Double {
        if (gravity.x == 0.0 && gravity.y == 0.0) return 0.0
        return (Math.toDegrees(atan2(gravity.y, gravity.x)) + 360.0) % 360.0
    }

    /**
     * Angle between two directions — the reading after zeroing, and the protractor reading between
     * two reference lines. This is the whole level: the zero is a stored direction, not a default.
     */
    fun angleBetweenDeg(a: Gravity, b: Gravity): Double {
        if (a.length == 0.0 || b.length == 0.0) return 0.0
        val dot = (a.x * b.x + a.y * b.y + a.z * b.z) / (a.length * b.length)
        return Math.toDegrees(acos(dot.coerceIn(-1.0, 1.0)))
    }

    /**
     * The bubble direction in the screen frame. Rule 8: both orientations have to work, so the
     * device-frame direction is rotated by the display rotation. The tilt itself does not change —
     * a flat phone is flat in either orientation — only the direction the bubble points to.
     */
    fun directionOnScreenDeg(gravity: Gravity, displayRotationDeg: Double): Double =
        (directionDeg(gravity) - displayRotationDeg + 360.0) % 360.0

    /**
     * The direction the bubble floats to. `directionOnScreenDeg` is the *downhill* direction — gravity
     * projected onto the screen — while a bubble floats to the high side, which is 180° from it.
     * Using the downhill direction for the drawing made the bubble run the wrong way (finding 10), so
     * the one number is not reused for both the drawing and the written "downhill direction".
     */
    fun bubbleOnScreenDeg(gravity: Gravity, displayRotationDeg: Double): Double =
        (directionOnScreenDeg(gravity, displayRotationDeg) + 180.0) % 360.0

    /**
     * The accuracy status Android reports for the rotation vector. It is a status code, not an angle, so
     * it is mapped rather than converted.
     */
    fun accuracyFromStatus(status: Int): SensorAccuracy = when (status) {
        3 -> SensorAccuracy.HIGH
        2 -> SensorAccuracy.MEDIUM
        1 -> SensorAccuracy.LOW
        0 -> SensorAccuracy.UNRELIABLE
        else -> SensorAccuracy.UNKNOWN
    }

    /**
     * The typical angular error at each accuracy status. This is a stated assumption, not a measurement:
     * the sensor reports a status, not an angle, so the UI must not present this as the sensor's own
     * confidence (rule 5). At high accuracy it matches the SPEC claim of ±0.5° typical, and it widens as
     * the reported status worsens — the number must never get better when the sensor says it is worse.
     */
    fun typicalErrorDeg(accuracy: SensorAccuracy): Double = when (accuracy) {
        SensorAccuracy.HIGH -> 0.5
        SensorAccuracy.MEDIUM -> 1.0
        SensorAccuracy.LOW -> 2.0
        SensorAccuracy.UNRELIABLE -> 5.0
        SensorAccuracy.UNKNOWN -> 5.0
    }

    /** Below this the level becomes "Level". */
    const val LEVEL_ENTER_DEG = 0.3

    /** Above this a level that was "Level" stops being level. */
    const val LEVEL_LEAVE_DEG = 0.6

    /**
     * "Level" with hysteresis. One hard threshold made the word flicker between Level and Not level on a
     * phone lying still on a surface a hair away from it, because the last digit of a fused sensor never
     * settles. Two thresholds mean a reading has to move a clear 0.3° before the word changes back.
     */
    fun isLevel(angleDeg: Double, wasLevel: Boolean): Boolean =
        if (wasLevel) angleDeg <= LEVEL_LEAVE_DEG else angleDeg < LEVEL_ENTER_DEG

    /**
     * The whole degree TalkBack announces. Plain rounding still flips between 2 and 3 on a reading that
     * sits at 2.5°, so the previously announced degree is kept until the reading is clearly past the
     * half-way mark — far enough that the change is real rather than sensor noise.
     */
    fun announcedWholeDegree(angleDeg: Double, previous: Int?): Int {
        val rounded = Math.round(angleDeg).toInt()
        if (previous == null) return rounded
        return if (abs(angleDeg - previous) < ANNOUNCE_HYSTERESIS_DEG) previous else rounded
    }

    private const val ANNOUNCE_HYSTERESIS_DEG = 0.75

    /**
     * The mean direction of several samples, normalised. A zero taken from one sample stored whatever
     * the hand was doing in that one instant; half a second of samples averages that out. Null when there
     * is nothing to average, so a caller cannot store an invented reference (rule 4).
     */
    fun averageGravity(samples: List<Gravity>): Gravity? {
        if (samples.isEmpty()) return null
        var x = 0.0
        var y = 0.0
        var z = 0.0
        for (sample in samples) {
            val length = sample.length
            if (length == 0.0) continue
            x += sample.x / length
            y += sample.y / length
            z += sample.z / length
        }
        val mean = Gravity(x, y, z)
        val length = mean.length
        if (length == 0.0) return null
        return Gravity(x / length, y / length, z / length)
    }

    /**
     * [current] expressed as if [reference] were "flat": the smallest rotation that takes the reference
     * to (0, 0, 1), applied to the current direction. Its tilt is exactly the angle to the reference, and
     * its direction is the way the phone tips *relative to the surface the user zeroed on* — the raw
     * gravity direction drew the bubble toward the room's downhill, not the surface's.
     */
    fun relativeToReference(current: Gravity, reference: Gravity): Gravity {
        val rl = reference.length
        val cl = current.length
        if (rl == 0.0 || cl == 0.0) return current
        val rx = reference.x / rl
        val ry = reference.y / rl
        val rz = reference.z / rl
        val vx = current.x / cl
        val vy = current.y / cl
        val vz = current.z / cl

        // Axis a = r × k with k = (0, 0, 1); |a| = sin of the angle between them, r · k = its cosine.
        val ax = ry
        val ay = -rx
        val sin2 = ax * ax + ay * ay
        val cos = rz
        if (sin2 < 1e-18) {
            // Already flat: nothing to rotate. Upside down: a half turn about x is one valid answer.
            return if (cos > 0) Gravity(vx, vy, vz) else Gravity(vx, -vy, -vz)
        }
        // Rodrigues with an unnormalised axis: v·cos + (a × v) + a(a · v)(1 − cos)/sin².
        val crossX = ay * vz
        val crossY = -ax * vz
        val crossZ = ax * vy - ay * vx
        val dot = ax * vx + ay * vy
        val k = (1 - cos) / sin2
        return Gravity(
            vx * cos + crossX + ax * dot * k,
            vy * cos + crossY + ay * dot * k,
            vz * cos + crossZ
        )
    }

    /**
     * The tilt split along the two screen axes, in degrees, both non-negative: how far the phone tips side
     * to side and top to bottom as the user holds it. Rule 8: the split follows the display rotation, so
     * "side to side" is the screen's left–right in either orientation.
     */
    fun axisAnglesDeg(gravity: Gravity, displayRotationDeg: Double): AxisAngles {
        val length = gravity.length
        if (length == 0.0) return AxisAngles(0.0, 0.0)
        val horizontal = sqrt(gravity.x * gravity.x + gravity.y * gravity.y) / length
        val z = gravity.z / length
        val direction = Math.toRadians(directionOnScreenDeg(gravity, displayRotationDeg))
        return AxisAngles(
            sideToSideDeg = Math.toDegrees(atan2(abs(horizontal * cos(direction)), z)),
            topToBottomDeg = Math.toDegrees(atan2(abs(horizontal * sin(direction)), z))
        )
    }
}

/** The tilt along the screen's two axes, as the user holds the phone. */
data class AxisAngles(val sideToSideDeg: Double, val topToBottomDeg: Double)
