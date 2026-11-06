package com.blueorbit.ruler.core.calibration

import android.content.Context

/**
 * Calibration is data, not code defaults (rule 4): these figures are a labelled guess and are
 * never silently trusted. The error budget reflects how far densityDpi can be from the true
 * physical density — typically several mm per 100 mm, which is exactly the competitor complaint.
 */
object DeviceDefaults {

    /** Expected error over a 100 mm span when only the density guess is available. */
    const val GUESS_ERROR_MM_PER_100_MM = 5.0

    fun guess(context: Context): Calibration = Calibration(
        pixelsPerMm = context.resources.displayMetrics.densityDpi / 25.4,
        source = CalibrationSource.DEVICE_DEFAULT,
        errorBudgetMmPer100Mm = GUESS_ERROR_MM_PER_100_MM
    )
}
