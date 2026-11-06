package com.blueorbit.ruler.core.log

import java.time.Instant
import java.util.Locale

/**
 * CSV is written with fixed separators so a spreadsheet app reads the same file in every locale:
 * fields are comma-separated and quoted per RFC 4180, numbers use `.` (never the default locale's
 * decimal separator), and dates are ISO-8601 UTC. Values stay in micrometres (rule 2) so a
 * round-trip does not lose precision through a rounded display value.
 */
object CsvExport {

    /** RFC 4180 line break. Spreadsheet apps are the target reader, not a text editor. */
    val lineBreak = "\r\n"

    /**
     * UTF-8 BOM. Excel reads a UTF-8 CSV as the local Windows code page without it, which garbles
     * any non-ASCII label or note. Written by `Share.writeCsv`, not by `build`, so `build` stays
     * plain text that a test can parse.
     */
    val byteOrderMark = "\uFEFF"

    val headers = listOf(
        "id",
        "value_micrometres",
        "unit",
        "label",
        "note",
        "source",
        "error_budget_mm_per_100_mm",
        "created_at_iso",
        "tags"
    )

    fun build(rows: List<Measurement>): String {
        val builder = StringBuilder()
        builder.append(headers.joinToString(",")).append(lineBreak)
        for (row in rows) {
            builder.append(
                listOf(
                    row.id.toString(),
                    row.valueMicrometres.toString(),
                    row.unit,
                    row.label,
                    row.note,
                    row.source,
                    formatNumber(row.errorBudgetMmPer100Mm),
                    Instant.ofEpochMilli(row.createdAtMillis).toString(),
                    row.tags
                ).joinToString(",") { escape(it) }
            ).append(lineBreak)
        }
        return builder.toString()
    }

    /** RFC 4180: quote only when needed, and double any quote inside the field. */
    fun escape(value: String): String =
        if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }

    /** Locale-independent number formatting — a comma-decimal locale must not corrupt the file. */
    private fun formatNumber(value: Double): String = String.format(Locale.ROOT, "%.3f", value)
}
