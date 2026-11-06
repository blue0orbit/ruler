package com.blueorbit.ruler.core.geometry

import java.text.DecimalFormatSymbols

/** Decimal or reduced fraction. Both selectable in the UI (PLAN M3 acceptance). */
enum class FormatMode { FRACTION, DECIMAL }

/** Text plus the rounding flag, so the UI can show `≈` (rule 5: never imply more accuracy). */
data class Formatted(val text: String, val approximate: Boolean)

/**
 * Formatting happens only at the display boundary (rule 2). This returns the numeric part; the
 * `≈` marker and unit labels are resource strings (rule 9).
 */
object Formatter {

    fun format(micrometres: Long, unit: MeasurementUnit, mode: FormatMode): Formatted =
        if (unit == MeasurementUnit.INCH && mode == FormatMode.FRACTION) {
            val fraction = InchFractions.fromMicrometres(micrometres)
            Formatted(fraction.format(), fraction.approximate)
        } else {
            Formatted(decimal(micrometres, unit), false)
        }

    /** Tick labels use the text only — a `≈` on every tick would be noise. */
    fun numeric(micrometres: Long, unit: MeasurementUnit, mode: FormatMode): String =
        format(micrometres, unit, mode).text

    /**
     * The decimal separator of the reader's language: "6,5 cm" in German, "6.5 cm" in English. This is
     * display text only — the CSV export has its own formatting and keeps a dot so spreadsheets parse it.
     */
    private fun decimal(
        micrometres: Long,
        unit: MeasurementUnit,
        separator: Char = DecimalFormatSymbols.getInstance().decimalSeparator
    ): String {
        val decimals = if (unit == MeasurementUnit.INCH) 2 else 1
        val rounded = RulerScene.roundHalfUp(unit.value(micrometres), decimals)
        return if (rounded == rounded.toLong().toDouble()) rounded.toLong().toString()
        else rounded.toString().replace('.', separator)
    }
}
