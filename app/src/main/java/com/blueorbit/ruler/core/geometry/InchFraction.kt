package com.blueorbit.ruler.core.geometry

/**
 * Fraction rounding is a pure, tested function (DECISIONS.md rule 3): nearest 1/32, reduced,
 * with a flag for "approximate". The UI shows `≈` from a resource string when the flag is set,
 * so a rounded reading is never presented as an exact one (rule 5).
 */
data class InchFraction(
    val wholeInches: Long,
    val numerator: Long,
    val denominator: Long,
    /** True when the displayed fraction is a rounded value, not the measured one. */
    val approximate: Boolean
) {

    /** Numeric part only — display strings live in resources (rule 9). */
    fun format(): String = when {
        wholeInches == 0L && numerator == 0L -> "0"
        numerator == 0L -> wholeInches.toString()
        wholeInches == 0L -> "$numerator/$denominator"
        else -> "$wholeInches $numerator/$denominator"
    }
}

object InchFractions {

    const val MAX_DENOMINATOR = 32

    fun fromMicrometres(micrometres: Long, maxDenominator: Int = MAX_DENOMINATOR): InchFraction {
        val scaled = micrometres * maxDenominator.toDouble() / Physical.MICROMETRES_PER_INCH.toDouble()
        val rounded = RulerScene.roundHalfUp(scaled, 0).toLong()
        val approximate = rounded * Physical.MICROMETRES_PER_INCH != micrometres * maxDenominator

        val whole = rounded / maxDenominator
        val remainder = rounded % maxDenominator
        val divisor = gcd(remainder, maxDenominator.toLong())

        return InchFraction(
            wholeInches = whole,
            numerator = if (divisor > 0) remainder / divisor else 0,
            denominator = if (divisor > 0) maxDenominator.toLong() / divisor else 1,
            approximate = approximate
        )
    }

    private fun gcd(a: Long, b: Long): Long {
        var x = a
        var y = b
        while (y != 0L) {
            val t = y
            y = x % y
            x = t
        }
        return x
    }
}
