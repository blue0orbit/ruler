package com.blueorbit.ruler.core.geometry

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.pow

/**
 * Pure scene model: it decides which ticks exist, how far apart they are, and which carry a label.
 * The canvas only renders what this produces, so tick density and label placement stay testable.
 *
 * Label size never changes the physical spacing (DECISIONS.md rule 1): magnifying the drawing
 * would make the on-screen ruler physically wrong, which is the competitor complaint this app
 * exists to fix.
 *
 * Steps come from a readable ladder — metric (1, 2, 5, 10, ...) and imperial
 * (1/32, 1/16, 1/8, 1/4, 1/2, 1) — so labels always land on fractions a ruler would print.
 */
class RulerScene(
    private val pxPerMm: PixelPerMm,
    private val unit: MeasurementUnit,
    private val labelSizePx: Double,
    private val mode: FormatMode = FormatMode.FRACTION,
    private val minTickSpacingPx: Double = 6.0,
    /** Horizontal room a label needs, as a multiple of its font size. */
    private val labelSpaceFactor: Double = 6.0
) {

    /** Physical size of one display unit, in mm. */
    val unitSizeMm: Double = when (unit) {
        MeasurementUnit.MM -> 1.0
        MeasurementUnit.CM -> 10.0
        MeasurementUnit.INCH -> 25.4
    }

    fun pixelsPerUnit(): Double = pxPerMm.mmToPixels(unitSizeMm)

    /** Steps a ruler would actually print, smallest first. */
    private fun ladder(): List<Double> = when (unit) {
        MeasurementUnit.INCH -> listOf(1.0 / 32, 1.0 / 16, 1.0 / 8, 1.0 / 4, 1.0 / 2, 1.0)
        // A centimetre ruler prints millimetres and half-centimetres between its numbers. Starting the
        // ladder at 1 cm drew a centimetre scale with nothing between the numbers, which is not a
        // ruler anyone has seen.
        MeasurementUnit.CM -> listOf(0.1, 0.5, 1.0, 2.0, 5.0, 10.0)
        MeasurementUnit.MM -> listOf(1.0, 2.0, 5.0, 10.0, 20.0, 50.0, 100.0)
    }

    /**
     * The rank test needs exact division. Inch steps are binary fractions and millimetre steps are
     * integers, so both divide exactly in `Double`; 0.1 cm does not, so centimetre positions are
     * compared in whole tenths instead.
     */
    private val rankScale: Double = if (unit == MeasurementUnit.CM) 10.0 else 1.0

    /**
     * Tick hierarchy (DESIGN.md §5.1): the index of the largest ladder step that exactly divides the
     * tick position, capped at [MAX_RANK] so a 50 mm tick is not taller than a centimetre one.
     *
     * Exactness needs no epsilon: every ladder value is an integer or a binary fraction (1/32 … 1), so
     * `position / step` is exact in `Double` and a whole quotient means a real division. The rank
     * depends only on the position and the ladder, never on `labelSizePx` or the font scale (rule 1).
     */
    fun rankOf(positionUnits: Double): Int {
        val steps = ladder()
        var largest = 0
        if (rankScale != 1.0) {
            val position = Math.round(positionUnits * rankScale)
            for (index in steps.indices) {
                if (position % Math.round(steps[index] * rankScale) == 0L) largest = index
            }
            return largest.coerceAtMost(MAX_RANK)
        }
        for (index in steps.indices) {
            val quotient = positionUnits / steps[index]
            if (quotient == Math.round(quotient).toDouble()) largest = index
        }
        return largest.coerceAtMost(MAX_RANK)
    }

    private fun smallestStepAtLeast(requiredUnits: Double): Double {
        for (step in ladder()) if (step >= requiredUnits) return step
        return ladder().last()
    }

    /** Smallest ladder step that is still visually separable, in display units. */
    fun tickStepUnits(): Double {
        val perUnit = pixelsPerUnit()
        if (perUnit <= 0.0) return ladder().last()
        return smallestStepAtLeast(minTickSpacingPx / perUnit)
    }

    /**
     * Ladder step between two labels. A label needs roughly `labelSizePx * labelSpaceFactor` of
     * horizontal room, so the step grows with the label font — this is the re-spacing the
     * acceptance check looks for, and it never changes the physical geometry.
     */
    fun labelStepUnits(): Double {
        val perUnit = pixelsPerUnit()
        if (perUnit <= 0.0) return ladder().last()
        return smallestStepAtLeast(labelSizePx * labelSpaceFactor / perUnit)
    }

    fun tickStepMicrometres(): Long = Math.round(tickStepUnits() * unitSizeMm * Physical.MICROMETRES_PER_MM)

    /** Ticks for a viewport, measured from the start line (pixel 0 = physical device edge). */
    fun build(startMicrometres: Long, viewportPixels: Double): List<Tick> {
        val stepUnits = tickStepUnits()
        val stepPx = pixelsPerUnit() * stepUnits
        if (stepPx <= 0.0) return emptyList()

        val ticksPerLabel = Math.round(labelStepUnits() / stepUnits).toInt().coerceAtLeast(1)
        val ticks = ArrayList<Tick>()
        var index = 0L
        while (index * stepPx <= viewportPixels) {
            val micrometres = startMicrometres +
                Math.round(index * stepUnits * unitSizeMm * Physical.MICROMETRES_PER_MM)
            val label = if (index % ticksPerLabel == 0L)
                Formatter.numeric(micrometres - startMicrometres, unit, mode)
            else null
            ticks += Tick(
                micrometres = micrometres,
                pixels = index * stepPx,
                label = label,
                rank = rankOf(index * stepUnits)
            )
            index++
        }
        return ticks
    }

    companion object {
        /** Four levels of tick, no more (DESIGN.md §5.2). */
        const val MAX_RANK = 3

        /** Rounding rule is round-half-up and is tested (PLAN M3 acceptance). */
        fun roundHalfUp(value: Double, decimals: Int): Double {
            val factor = 10.0.pow(decimals)
            return floor(value * factor + 0.5) / factor
        }
    }
}

/**
 * One tick. `rank` is the visual weight (0 = quietest … [RulerScene.MAX_RANK] = the printed step), and
 * it is decided here rather than in the canvas so the hierarchy is testable without a UI.
 */
data class Tick(
    val micrometres: Long,
    val pixels: Double,
    val label: String?,
    val rank: Int = 0
)
