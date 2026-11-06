package com.blueorbit.ruler.core.geometry

/**
 * Internal unit is micrometres (Long) — DECISIONS.md rule 2. Formatting happens only at the
 * display boundary so float drift never accumulates in stored measurements.
 */
object Physical {
    const val MICROMETRES_PER_MM = 1_000L
    const val MICROMETRES_PER_CM = 10_000L
    /** 1 inch = 25.4 mm exactly. */
    const val MICROMETRES_PER_INCH = 25_400L
}

/** Unit labels live in resources (rule 9), so the enum carries no display strings. */
enum class MeasurementUnit {
    MM,
    CM,
    INCH;

    /** Round-half-up so float drift cannot turn 12.3 cm into 12299 um. */
    fun micrometres(valueInUnit: Double): Long = Math.round(valueInUnit * multiplier)

    fun value(micrometres: Long): Double = micrometres.toDouble() / multiplier

    private val multiplier: Double
        get() = when (this) {
            MM -> Physical.MICROMETRES_PER_MM.toDouble()
            CM -> Physical.MICROMETRES_PER_CM.toDouble()
            INCH -> Physical.MICROMETRES_PER_INCH.toDouble()
        }

    companion object {
        /**
         * Lookup by stored name that cannot throw. `valueOf` throws `IllegalArgumentException` on an
         * unknown name, and this runs inside a list row — a row written by another build, a
         * hand-edited database or a reused constant would crash the whole log (finding 14). An
         * unrecognised unit falls back to millimetres, the app's default unit.
         */
        fun unitOf(name: String): MeasurementUnit = entries.firstOrNull { it.name == name } ?: MM
    }
}
