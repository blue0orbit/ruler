package com.blueorbit.ruler.core.log

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.Locale

class ShareTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val createdAt = 1_760_000_000_000L

    private val rows = listOf(
        Measurement(
            id = 1,
            valueMicrometres = 53_980L,
            unit = "MM",
            label = "card edge, short",
            note = "she said \"54 mm\"",
            source = "card",
            errorBudgetMmPer100Mm = 0.12,
            createdAtMillis = createdAt,
            tags = ""
        )
    )

    @Test
    fun fileNameUsesTheUtcDateAndNoLocaleSeparators() {
        assertEquals("ruler-log-2025-10-09.csv", Share.fileName(createdAt))
    }

    @Test
    fun fileNameIsTheSameInACommaDecimalLocale() {
        val original = Locale.getDefault()
        Locale.setDefault(Locale.GERMAN)
        try {
            assertEquals("ruler-log-2025-10-09.csv", Share.fileName(createdAt))
        } finally {
            Locale.setDefault(original)
        }
    }

    /**
     * A device whose clock is set before 1970 is odd but possible, and the day must still be the UTC
     * day the timestamp belongs to: plain division rounds toward zero, so −1 ms would be named as the
     * day after the one it is on.
     */
    @Test
    fun fileNameGivesTheDayBeforeForATimestampBeforeTheEpoch() {
        assertEquals("ruler-log-1969-12-31.csv", Share.fileName(-1L))
        assertEquals("ruler-log-1970-01-01.csv", Share.fileName(0L))
    }

    @Test
    fun exportWritesTheFileToTheExportDirectoryWithABomAndSurvivesAReread() {
        val directory = folder.newFolder("exports")
        val file = Share.writeCsv(directory, rows, createdAt)

        assertEquals("ruler-log-2025-10-09.csv", file.name)
        val bytes = file.readBytes()
        assertTrue(bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte())

        val parsed = parseCsv(String(bytes, Charsets.UTF_8).removePrefix(CsvExport.byteOrderMark))
        assertEquals(CsvExport.headers, parsed[0])
        // Quoted fields come back as single fields, which is what a spreadsheet app does.
        assertEquals(
            listOf(
                "1",
                "53980",
                "MM",
                "card edge, short",
                "she said \"54 mm\"",
                "card",
                "0.120",
                "2025-10-09T08:53:20Z",
                ""
            ),
            parsed[1]
        )
    }

    /**
     * "Save as file" writes into a document the user picked, through a stream; "Send as file" writes a
     * cache file. They are the same export, so they must be the same bytes, BOM included.
     */
    @Test
    fun theDocumentTheUserPickedGetsTheSameBytesAsTheSharedFile() {
        val file = Share.writeCsv(folder.newFolder("exports"), rows, createdAt)
        val stream = java.io.ByteArrayOutputStream()

        Share.writeCsv(stream, rows)

        assertTrue(file.readBytes().contentEquals(stream.toByteArray()))
    }
}
