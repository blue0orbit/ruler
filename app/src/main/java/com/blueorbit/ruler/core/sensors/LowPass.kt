package com.blueorbit.ruler.core.sensors

/**
 * Exponential smoothing for the gravity stream. The rotation vector is already fused, but at a 0.1°
 * readout its last digit flickers on a phone lying still, and the level bubble and the protractor's live
 * ray shiver with it. A short low-pass removes the flicker without the lag a moving average would add.
 *
 * `alpha` is the weight of the new sample: 1 is no smoothing, smaller is smoother and slower. Kept pure
 * so the step response is tested on the JVM.
 */
class LowPass(private val alpha: Double = DEFAULT_ALPHA) {

    init {
        require(alpha > 0.0 && alpha <= 1.0) { "alpha must be in (0, 1]" }
    }

    private var last: Gravity? = null

    fun filter(sample: Gravity): Gravity {
        val previous = last
        val next = if (previous == null) {
            sample
        } else {
            Gravity(
                previous.x + alpha * (sample.x - previous.x),
                previous.y + alpha * (sample.y - previous.y),
                previous.z + alpha * (sample.z - previous.z)
            )
        }
        last = next
        return next
    }

    companion object {
        /** At SENSOR_DELAY_GAME (~50 Hz) this settles 95 % of a step in about 0.25 s. */
        const val DEFAULT_ALPHA = 0.2
    }
}
