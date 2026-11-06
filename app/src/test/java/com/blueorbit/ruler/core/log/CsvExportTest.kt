package com.blueorbit.ruler.core.log

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class CsvExportTest {

    private val createdAt = 1_760_000_000_000L

    private fun row(label: String, note: String, tags: String = "") = Measurement(
        id = 7,
        valueMicrometres = 53_980L,
        unit = "MM",
        label = label,
        note = note,
        source = "card",
        errorBudgetMmPer100Mm = 0.12,
        createdAtMillis = createdAt,
        tags = tags
    )

    @Test
    fun headersAreFixedAndInColumnOrder() {
        val parsed = parseCsv(CsvExport.build(listOf(row("desk", "note"))))
        assertEquals(
            listOf(
                "id",
                "value_micrometres",
                "unit",
                "label",
                "note",
                "source",
                "error_budget_mm_per_100_mm",
                "created_at_iso",
                "tags"
            ),
            parsed[0]
        )
    }

    @Test
    fun fieldWithCommaIsQuoted() {
        assertEquals("\"card edge, short\"", CsvExport.escape("card edge, short"))
    }

    @Test
    fun quoteInsideAFieldIsDoubled() {
        assertEquals("\"she said \"\"54 mm\"\"\"", CsvExport.escape("she said \"54 mm\""))
    }

    @Test
    fun fieldWithNewlineIsQuotedAndPlainFieldIsNot() {
        assertEquals("\"line one\nline two\"", CsvExport.escape("line one\nline two"))
        assertEquals("plain", CsvExport.escape("plain"))
        assertFalse(CsvExport.escape("plain").startsWith("\""))
    }

    @Test
    fun decimalFieldsUseTheDotNotTheDefaultLocaleSeparator() {
        val original = Locale.getDefault()
        Locale.setDefault(Locale.GERMAN)
        try {
            val line = parseCsv(CsvExport.build(listOf(row("desk", "note"))))[1]
            assertEquals("0.120", line[6])
            val wholeFile = CsvExport.build(listOf(row("desk", "note")))
            assertTrue(wholeFile.contains(",0.120,"))
            assertFalse(wholeFile.contains(",0,120,"))
        } finally {
            Locale.setDefault(original)
        }
    }

    @Test
    fun roundTripThroughASpreadsheetParserPreservesEveryField() {
        val original = row("card edge, short", "she said \"54 mm\"", "desk,home")
        val parsed = parseCsv(CsvExport.build(listOf(original)))
        val fields = parsed[1]
        val byName = parsed[0].withIndex().associate { (index, name) -> name to fields[index] }

        assertEquals("7", byName["id"])
        // Values stay in micrometres, so a round-trip does not lose precision through a rounded
        // display value (rule 2).
        assertEquals("53980", byName["value_micrometres"])
        assertEquals(53_980L, byName["value_micrometres"]!!.toLong())
        assertEquals("MM", byName["unit"])
        assertEquals("card edge, short", byName["label"])
        assertEquals("she said \"54 mm\"", byName["note"])
        assertEquals("card", byName["source"])
        assertEquals("0.120", byName["error_budget_mm_per_100_mm"])
        assertEquals("2025-10-09T08:53:20Z", byName["created_at_iso"])
        assertEquals("desk,home", byName["tags"])
    }

    @Test
    fun multilineFieldSurvivesTheRoundTrip() {
        val parsed = parseCsv(CsvExport.build(listOf(row("desk", "line one\nline two"))))
        assertEquals("line one\nline two", parsed[1][4])
    }

    @Test
    fun emptyLogProducesHeaderOnly() {
        val parsed = parseCsv(CsvExport.build(emptyList()))
        assertEquals(1, parsed.size)
        assertEquals(9, parsed[0].size)
    }

    @Test
    fun lineBreakIsCrlfAndTheFileIsWrittenWithABom() {
        val csv = CsvExport.build(listOf(row("desk", "note")))
        assertTrue(csv.endsWith("\r\n"))
        assertTrue(csv.startsWith("id,"))
        val bytes = (CsvExport.byteOrderMark + csv).toByteArray(Charsets.UTF_8)
        assertTrue(bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte())
    }
}
