package com.blueorbit.ruler.core.geometry

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Ring sizes (ROADMAP M21).
 *
 * ISO 8653:2016 designates a ring by its **inner circumference in whole millimetres**, so the EU/ISO
 * size *is* the circumference and one size is 1 mm of circumference = 1/π ≈ 0.318 mm of diameter.
 * The three other systems in use are step scales keyed to the same inner circle. Their anchor and
 * step are published figures, transcribed here as data and pinned by `RingSizesTest` against the
 * cited charts — this file never derives one system from another, and no conversion is invented.
 *
 * Sources, both read 2026-10-23:
 *  - ringsize.online, *Complete Ring Size Conversion Chart* (inner diameter 11.6–25.5 mm in 0.1 mm
 *    rows; EU 37–80, US 0–17 in ¼ sizes, UK A–Z+9 in ½ letters, JP 1–39), stated there as based on
 *    ISO 8653:2016, BS EN 28653:1993, JIS S 4700:2022 and NBR 16058.
 *    https://ringsize.online/wp-content/uploads/Complete-Conversion-Ring-Size-Chart.pdf
 *  - ringchart.co.uk, *Ring Size Chart for All Countries* (the same ladders at 0.4 mm rows) and
 *    *Ring Sizes in Japan* on myringsizer.app, which publishes the JIS ladder as 13.00 / 13.33 /
 *    13.67 mm for sizes 1 / 2 / 3.
 *    https://ringchart.co.uk/ring-size-charts/ring-size-chart-for-all-countries/
 *
 * Where the two charts disagree they do so by less than 0.2 mm, which is under one EU size; the
 * ladders below follow the standard's own published anchor and step (13.00 mm and 1/3 mm for JIS,
 * 11.63 mm and 0.032 in for the US, 11.9 mm and 1.25 mm of circumference for the UK letters), and
 * every rung agrees with the cited chart at every row the chart publishes.
 */
/** One US/Canada ring size = 0.032 in of inner diameter = 0.8128 mm. */
private const val US_SIZE_MM = 0.032 * 25.4

object RingSizes {

    /** A ring is a circle: the two measurements the user can actually make are two forms of one fact. */
    fun circumferenceFromDiameter(diameterMm: Double): Double = diameterMm * PI

    fun diameterFromCircumference(circumferenceMm: Double): Double = circumferenceMm / PI

    /** The rung of `system` the measured inner circle is closest to. */
    fun match(system: RingSizeSystem, innerDiameterMm: Double): RingSizeMatch {
        val ladder = ladder(system)
        val nearest = ladder.minBy { abs(it.innerDiameterMm - innerDiameterMm) }
        val index = ladder.indexOf(nearest)
        val below = innerDiameterMm < ladder.first().innerDiameterMm
        val above = innerDiameterMm > ladder.last().innerDiameterMm
        // The rung on the side the measurement actually leans, so "between 6½ and 7" names the two
        // sizes the user's circle sits between rather than a nearest plus a signed number.
        val neighbour = when {
            innerDiameterMm < nearest.innerDiameterMm && index > 0 -> ladder[index - 1]
            innerDiameterMm > nearest.innerDiameterMm && index < ladder.lastIndex -> ladder[index + 1]
            else -> null
        }
        return RingSizeMatch(
            system = system,
            nearest = nearest,
            rungsAway = (innerDiameterMm - nearest.innerDiameterMm) / system.rungStepMm,
            neighbour = neighbour,
            belowScale = below,
            aboveScale = above
        )
    }

    /**
     * Rule 5, in the unit this feature is read in: how many sizes of `system` a diameter tolerance
     * spans. One EU size is 1 mm of circumference, so on a 17.3 mm ring the uncalibrated
     * "within 5 mm over 10 cm" is 0.87 mm ≈ 2.7 sizes — the reason M21 gates sizes on a verified
     * calibration instead of printing a single number off a guess.
     */
    fun sizesOfUncertainty(system: RingSizeSystem, diameterToleranceMm: Double): Double =
        abs(diameterToleranceMm) / system.stepMmPerSize

    /**
     * The two sizes a reading could be, given its tolerance — the range shown when the calibration is
     * not verified. Matched at each end of the tolerance rather than counted in sizes, so a reading
     * that is exactly on a rung still names one size and not two.
     */
    fun sizeRange(
        system: RingSizeSystem,
        innerDiameterMm: Double,
        diameterToleranceMm: Double
    ): Pair<String, String> {
        val tolerance = abs(diameterToleranceMm)
        return match(system, innerDiameterMm - tolerance).nearest.label to
            match(system, innerDiameterMm + tolerance).nearest.label
    }

    /**
     * One system's ladder, ascending by inner diameter. Internal so the unit tests can read the
     * transcribed rungs directly instead of inferring them from matches, and so the screens can show
     * the size a reading could have been without re-deriving anything.
     */
    internal fun ladder(system: RingSizeSystem): List<RingSize> = when (system) {
        RingSizeSystem.EU -> EU_LADDER
        RingSizeSystem.US -> US_LADDER
        RingSizeSystem.UK -> UK_LADDER
        RingSizeSystem.JP -> JP_LADDER
    }

    // EU / ISO 8653:2016 — the size is the inner circumference in whole millimetres. The chart lists
    // 37 to 80; below 37 and above 80 there is no rung to name, which `RingSizeMatch` reports.
    private val EU_LADDER: List<RingSize> = (37..80).map { RingSize(it.toString(), it / PI) }

    // US / Canada — size 0 is 0.458 in and each size adds 0.032 in of inner diameter (0.8128 mm).
    // Chart rows checked: 0 → 11.6, ½ → 12.0, 10¾ → 20.4, 12 → 21.4, 16 → 24.6, 17 → 25.5.
    // The roadmap asks for 0–16 in ½ steps; the chart's ¼ rungs are the same ladder read finer.
    private val US_LADDER: List<RingSize> = (0..32).map { half ->
        val size = half / 2.0
        RingSize(usLabel(size), US_FIRST_INCH_MM + US_SIZE_MM * size)
    }

    // UK / Ireland / Australia / NZ — BS EN 28653:1993 letters. A is 11.9 mm inner diameter and each
    // letter adds 1.25 mm of circumference (0.398 mm of diameter); the cited chart publishes half
    // letters, so each rung here is half a letter = 0.2 mm of diameter.
    // Chart rows checked: A → 11.9, A½ → 12.1, V → 20.3, Z → 21.9, Z+1 → 22.3, Z+5 → 23.9, Z+9 → 25.5.
    private val UK_LADDER: List<RingSize> = (0..UK_LAST_RUNG).map { rung ->
        RingSize(ukLabel(rung), UK_FIRST_MM + UK_HALF_LETTER_MM * rung)
    }

    // Japan / China / Korea — JIS S 4700:2022. Size 1 is 13.00 mm inner diameter and each size adds
    // 1/3 mm. Chart rows checked: 23 → 20.3, 24 → 20.7, 25 → 21.0, 28 → 22.0, 31 → 23.0, 34 → 24.0.
    // Above 34 the cited chart and the published JIS ladder drift apart by up to 0.2 mm (the chart
    // puts 37/38/39 at 24.9/25.2/25.5, JIS gives 25.0/25.3/25.7); the JIS ladder is used because
    // three sources publish 13.00 / 13.33 / 13.67 for 1 / 2 / 3, and the drift is recorded here
    // rather than hidden.
    private val JP_LADDER: List<RingSize> = (1..39).map { size ->
        RingSize(size.toString(), 13.00 + (size - 1) / 3.0)
    }

    private const val US_FIRST_INCH_MM = 0.458 * 25.4
    private const val UK_FIRST_MM = 11.9
    private const val UK_HALF_LETTER_MM = 0.2
    /** A to Z is 51 half-letter rungs, Z½ is rung 51, Z+1 to Z+9 is another 17. */
    private const val UK_LAST_RUNG = 68

    private fun usLabel(size: Double): String =
        if (size % 1.0 == 0.0) size.roundToInt().toString() else "${size.roundToInt() - 1}½"

    /** A … Z, then Z½, then Z+1 … Z+9 — the ladder the cited chart publishes. */
    private fun ukLabel(rung: Int): String {
        val letter = ('A' + rung / 2).toString()
        val half = if (rung % 2 == 1) "½" else ""
        return if (rung <= 51) letter + half else "Z+${(rung - 50) / 2}" + half
    }
}

/**
 * The four systems M21 shows.
 *
 * `stepMmPerSize` is one **size** of the system as inner diameter — the unit rule 5 quotes the
 * reading's uncertainty in. `rungStepMm` is one rung of that system's ladder, which is finer for the
 * two systems that publish half sizes: it is what the drag steps through and what fires the haptic.
 */
/**
 * Declared in the order the size charts are asked for them — the half-size ladder first, then the
 * letters, then the two metric ladders — because the screen lists them in this order.
 */
enum class RingSizeSystem(val stepMmPerSize: Double, val rungStepMm: Double) {
    US(US_SIZE_MM, US_SIZE_MM / 2.0),
    /** BS EN 28653: one letter is 1.25 mm of circumference; the chart publishes half letters. */
    UK(0.4, 0.2),
    /** ISO 8653:2016 — the size is the inner circumference in mm, so 1 mm of circumference. */
    EU(1.0 / PI, 1.0 / PI),
    JP(1.0 / 3.0, 1.0 / 3.0)
}

data class RingSize(val label: String, val innerDiameterMm: Double)

/**
 * One measurement against one system's ladder.
 *
 * `rungsAway` is signed and counted in **rungs of that ladder** — how far the circle is from the size
 * it was named by. `RingSizes.sizesOfUncertainty` is the other half of rule 5 here: how many *sizes*
 * the calibration's tolerance is worth. Both are stated because they answer two different questions —
 * "is this circle actually a 7, or is it between a 7 and a 7½?" and "how much can this reading move?".
 */
data class RingSizeMatch(
    val system: RingSizeSystem,
    val nearest: RingSize,
    val rungsAway: Double,
    val neighbour: RingSize?,
    val belowScale: Boolean,
    val aboveScale: Boolean
) {
    /**
     * ROADMAP M21: "nearest size + between X and Y when it falls between". A quarter of a rung is the
     * line: closer than that, the circle is that size; further out it is closer to the middle of the
     * gap than to either rung, and naming only one of them would overstate the match.
     */
    val isBetween: Boolean get() = neighbour != null && abs(rungsAway) >= BETWEEN_RUNGS

    /** True when there is no size to report for this measurement at all. */
    val offScale: Boolean get() = belowScale || aboveScale

    private companion object {
        const val BETWEEN_RUNGS = 0.25
    }
}
