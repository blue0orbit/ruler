package com.blueorbit.ruler.core.geometry

import org.junit.Assert.assertEquals
import org.junit.Test

class UnitConversionTest {

    @Test
    fun oneInchIsExactly25400Micrometres() {
        assertEquals(25_400L, Physical.MICROMETRES_PER_INCH)
        assertEquals(25.4, MeasurementUnit.MM.value(Physical.MICROMETRES_PER_INCH), 1e-9)
    }

    @Test
    fun wholeMillimetresRoundTripLosslessly() {
        for (mm in 0L..2000L) {
            val micrometres = MeasurementUnit.MM.micrometres(mm.toDouble())
            assertEquals(mm * Physical.MICROMETRES_PER_MM, micrometres)
            assertEquals(mm.toDouble(), MeasurementUnit.MM.value(micrometres), 1e-9)
        }
    }

    @Test
    fun centimetresAreTenMillimetres() {
        assertEquals(12_300L, MeasurementUnit.MM.micrometres(12.3))
        assertEquals(12_300L, MeasurementUnit.CM.micrometres(1.23))
        assertEquals(1.23, MeasurementUnit.CM.value(12_300L), 1e-9)
    }

    @Test
    fun inchesConvertThroughMicrometresNotThroughFloats() {
        // 3/32 inch = 2.38125 mm = 2381.25 um -> stored as an integer, rounding half-up at the boundary.
        val micrometres = MeasurementUnit.INCH.micrometres(3.0 / 32.0)
        assertEquals(2381L, micrometres)
    }

    @Test
    fun aStoredUnitNameIsLookedUpWithoutThrowing() {
        // M11R finding 14: `valueOf` throws on a name written by another build, and this runs inside
        // a list row, so an unknown unit would crash the whole log.
        assertEquals(MeasurementUnit.CM, MeasurementUnit.unitOf("CM"))
        assertEquals(MeasurementUnit.MM, MeasurementUnit.unitOf("PARSEC"))
        assertEquals(MeasurementUnit.MM, MeasurementUnit.unitOf(""))
    }
}
