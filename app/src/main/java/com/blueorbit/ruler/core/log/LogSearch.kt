package com.blueorbit.ruler.core.log

import java.util.Locale

/** Search matches label and note (PLAN M5). Pure, so the matching rule is testable. */
object LogSearch {

    fun matches(measurement: Measurement, query: String): Boolean {
        if (query.isBlank()) return true
        val needle = query.trim().lowercase(Locale.ROOT)
        return measurement.label.lowercase(Locale.ROOT).contains(needle) ||
            measurement.note.lowercase(Locale.ROOT).contains(needle)
    }
}
