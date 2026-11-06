package com.blueorbit.ruler.core.geometry

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

/** Display numbers follow the reader's language; German readers see a decimal comma. */
class FormatterLocaleTest {

    private val original = Locale.getDefault()

    @After
    fun restore() = Locale.setDefault(original)

    @Test
    fun germanReadersSeeADecimalComma(): Unit {
        Locale.setDefault(Locale.GERMANY)
        assertEquals("6,5", Formatter.numeric(65_000L, MeasurementUnit.CM, FormatMode.DECIMAL))
    }

    @Test
    fun englishReadersSeeADecimalPoint(): Unit {
        Locale.setDefault(Locale.US)
        assertEquals("6.5", Formatter.numeric(65_000L, MeasurementUnit.CM, FormatMode.DECIMAL))
    }

    @Test
    fun wholeNumbersHaveNoSeparatorInEitherLanguage(): Unit {
        Locale.setDefault(Locale.GERMANY)
        assertEquals("20", Formatter.numeric(20_000L, MeasurementUnit.MM, FormatMode.DECIMAL))
    }
}
