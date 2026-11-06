package com.blueorbit.ruler.core.geometry

import java.text.DecimalFormatSymbols
import kotlin.math.PI
import kotlin.math.roundToLong

/**
 * Area is stored in **square** micrometres (ROADMAP M22) for the same reason lengths are stored in
 * micrometres (rule 2): an exact integer that never carries float drift, converted to a display unit
 * only at the display boundary.
 *
 * The inch figure is exact rather than approximate because 1 inch is 25.4 mm exactly, so
 * 25.4 × 25.4 = 645.16 mm² exactly. Rounding it to 645 mm² would make every square-inch reading in the
 * log about 0.25 % wrong, and an area is the one reading people check against a bill of materials.
 */
object SquarePhysical {
    const val SQUARE_MICROMETRES_PER_SQUARE_MM = 1_000_000L
    const val SQUARE_MICROMETRES_PER_SQUARE_CM = 100_000_000L
    const val SQUARE_MICROMETRES_PER_SQUARE_INCH = 645_160_000L
}

/**
 * Area units are a separate enum from [MeasurementUnit] on purpose. `MeasurementUnit` is the ruler's
 * own setting and drives tick labels, the readout and the fraction rule; an area unit in that enum
 * would be offered as a way to read a length. The lookup is the same safe one `unitOf` uses (finding
 * 14): an unknown name is simply not an area row.
 */
enum class AreaUnit {
    MM2,
    CM2,
    IN2;

    fun squareMicrometres(valueInUnit: Double): Long = Math.round(valueInUnit * multiplier)

    fun value(squareMicrometres: Long): Double = squareMicrometres.toDouble() / multiplier

    /** The length unit the sides of this area are named in, so "cm²" and "cm" never disagree. */
    val lengthUnit: MeasurementUnit
        get() = when (this) {
            MM2 -> MeasurementUnit.MM
            CM2 -> MeasurementUnit.CM
            IN2 -> MeasurementUnit.INCH
        }

    private val multiplier: Double
        get() = when (this) {
            MM2 -> SquarePhysical.SQUARE_MICROMETRES_PER_SQUARE_MM.toDouble()
            CM2 -> SquarePhysical.SQUARE_MICROMETRES_PER_SQUARE_CM.toDouble()
            IN2 -> SquarePhysical.SQUARE_MICROMETRES_PER_SQUARE_INCH.toDouble()
        }

    companion object {
        /** Null for a length unit name, which is how a log row tells the two kinds apart. */
        fun of(name: String): AreaUnit? = entries.firstOrNull { it.name == name }
    }
}

/**
 * The rectangle the plate drew. Sides are lengths in micrometres, the area is square micrometres and
 * the perimeter is a length, so one type holds every number the area screen shows and none of them can
 * be computed twice in two places (rule 5).
 */
data class Rectangle(val widthMicrometres: Long, val heightMicrometres: Long) {

    val areaSquareMicrometres: Long
        get() = Shapes.rectangleArea(widthMicrometres, heightMicrometres)

    val perimeterMicrometres: Long
        get() = Shapes.rectanglePerimeter(widthMicrometres, heightMicrometres)
}

object Shapes {

    /**
     * A side the user has not drawn yet is zero, and a negative side is a drag that went past the start
     * line — neither is a shape, so both give an area of zero rather than a negative area the log could
     * store.
     */
    fun rectangleArea(widthMicrometres: Long, heightMicrometres: Long): Long =
        widthMicrometres.coerceAtLeast(0L) * heightMicrometres.coerceAtLeast(0L)

    fun rectanglePerimeter(widthMicrometres: Long, heightMicrometres: Long): Long =
        2L * (widthMicrometres.coerceAtLeast(0L) + heightMicrometres.coerceAtLeast(0L))

    /** Circle from its **diameter**, the way a round object is measured against a plate. */
    fun circleAreaSquareMicrometres(diameterMicrometres: Long): Long {
        val radius = diameterMicrometres.coerceAtLeast(0L) / 2.0
        return (PI * radius * radius).roundToLong()
    }

    fun circleCircumferenceMicrometres(diameterMicrometres: Long): Long =
        (PI * diameterMicrometres.coerceAtLeast(0L)).roundToLong()
}

/**
 * The numeric part of an area. The unit label is a resource string (rule 9) and the `≈` marker is the
 * screen's, so this returns digits only — the same split [Formatter] makes for lengths.
 */
object AreaFormatter {

    /** Square areas need more decimals than lengths: a 20 cm × 15 cm top is 0.046 m² of precision. */
    fun numeric(
        squareMicrometres: Long,
        unit: AreaUnit,
        separator: Char = DecimalFormatSymbols.getInstance().decimalSeparator
    ): String {
        val decimals = if (unit == AreaUnit.MM2) 1 else 2
        val rounded = RulerScene.roundHalfUp(unit.value(squareMicrometres), decimals)
        return if (rounded == rounded.toLong().toDouble()) rounded.toLong().toString()
        else rounded.toString().replace('.', separator)
    }
}
