package com.blueorbit.ruler.core.calibration

/**
 * Calibration is keyed by Build.MODEL + resolution (DECISIONS.md): a reinstall keeps it, and a
 * device swap never silently reuses another device's density.
 *
 * The two dimensions are stored in a fixed order so that reading the metrics while the device is
 * in landscape produces the same key as in portrait — otherwise rotating the phone at startup
 * would look like a different device and the calibration would be lost (rule 8).
 */
object DeviceKey {

    fun build(model: String, widthPx: Int, heightPx: Int): String {
        val safeModel = model.map { if (it.isLetterOrDigit()) it else '_' }.joinToString("")
        val short = minOf(widthPx, heightPx)
        val long = maxOf(widthPx, heightPx)
        return "${safeModel}_${short}x${long}"
    }
}
