package com.blueorbit.ruler.core.log

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LogSearchTest {

    private val row = Measurement(
        id = 1,
        valueMicrometres = 10_000,
        unit = "MM",
        label = "Bookshelf",
        note = "oak frame",
        source = "CARD",
        errorBudgetMmPer100Mm = 0.185,
        createdAtMillis = 1_000
    )

    @Test
    fun blankQueryMatchesEverything() {
        assertTrue(LogSearch.matches(row, ""))
        assertTrue(LogSearch.matches(row, "   "))
    }

    @Test
    fun searchMatchesLabelAndNoteCaseInsensitively() {
        assertTrue(LogSearch.matches(row, "book"))
        assertTrue(LogSearch.matches(row, "SHELF"))
        assertTrue(LogSearch.matches(row, "oak"))
    }

    @Test
    fun searchDoesNotMatchOtherFields() {
        assertFalse(LogSearch.matches(row, "CARD"))
        assertFalse(LogSearch.matches(row, "10000"))
    }

    @Test
    fun trimmedQueryStillMatches() {
        assertTrue(LogSearch.matches(row, "  frame  "))
    }
}
