package com.blueorbit.ruler.core.calibration

import com.blueorbit.ruler.core.sensors.Gravity
import com.blueorbit.ruler.core.sensors.LevelMath

/**
 * The zero of the level is the surface the user zeroed on, not a code default (rule 4). It is the
 * gravity direction recorded at zeroing, so the reading is the angle to that stored surface —
 * zeroing cannot invent a value, and a level used on a surface that is not truly flat reports the
 * angle to the reference, not a fake "0°".
 */
data class LevelZero(val reference: Gravity? = null) {

    fun isSet(): Boolean = reference != null

    /** Reading after zeroing. With no zero set, the reading is the raw tilt from horizontal. */
    fun angleDeg(current: Gravity): Double =
        if (reference == null) LevelMath.tiltDeg(current) else LevelMath.angleBetweenDeg(current, reference)
}
