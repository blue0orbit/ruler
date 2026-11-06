package com.blueorbit.ruler.core.ui

import com.blueorbit.ruler.R
import com.blueorbit.ruler.core.geometry.MeasurementUnit

/** Unit labels live in resources (PLAN M10), so both screens read the same mapping. */
fun unitLabelRes(unit: MeasurementUnit): Int = when (unit) {
    MeasurementUnit.MM -> R.string.unit_mm
    MeasurementUnit.CM -> R.string.unit_cm
    MeasurementUnit.INCH -> R.string.unit_inch
}
