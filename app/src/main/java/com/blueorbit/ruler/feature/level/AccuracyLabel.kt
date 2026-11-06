package com.blueorbit.ruler.feature.level

import com.blueorbit.ruler.R
import com.blueorbit.ruler.core.sensors.SensorAccuracy

/** Accuracy name shared by the level and the protractor so both tools say the same thing. */
fun accuracyLabelRes(accuracy: SensorAccuracy): Int = when (accuracy) {
    SensorAccuracy.HIGH -> R.string.accuracy_high
    SensorAccuracy.MEDIUM -> R.string.accuracy_medium
    SensorAccuracy.LOW -> R.string.accuracy_low
    SensorAccuracy.UNRELIABLE -> R.string.accuracy_unreliable
    SensorAccuracy.UNKNOWN -> R.string.accuracy_unknown
}
