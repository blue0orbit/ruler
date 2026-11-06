package com.blueorbit.ruler.core.geometry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Area maths (ROADMAP M22). The headline case is the inch: 1 in² is 645.16 mm² *exactly*, because
 * 1 in is 25.4 mm exactly. A rounded 645 would make every square-inch row in the log about 0.25 % low,
 * and areas are the readings people check a bill of materials against.
 */
class ShapesTest {

    @Test
    fun oneSquareInchIsExactly645Point16SquareMillimetres() {
        assertEquals(645_160_000L, SquarePhysical.SQUARE_MICROMETRES_PER_SQUARE_INCH)
        assertEquals(645_160_000L, AreaUnit.IN2.squareMicrometres(1.0))
        assertEquals(645.16, AreaUnit.MM2.value(SquarePhysical.SQUARE_MICROMETRES_PER_SQUARE_INCH), 1e-9)
        assertEquals(100.0, AreaUnit.MM2.value(SquarePhysical.SQUARE_MICROMETRES_PER_SQUARE_CM), 1e-9)
    }

    @Test
    fun aOneInchSquareReadsAsOneSquareInchThroughTheAreaUnit() {
        val inch = Rectangle(Physical.MICROMETRES_PER_INCH, Physical.MICROMETRES_PER_INCH)
        assertEquals(645_160_000L, inch.areaSquareMicrometres)
        assertEquals(1.0, AreaUnit.IN2.value(inch.areaSquareMicrometres), 0.0)
    }

    @Test
    fun rectangleAreaAndPerimeterComeFromTheSameTwoSides() {
        // 20 cm × 15 cm: 300 cm², 3 m² is wrong by 100×, so the cm² figure is the one a person expects.
        val top = Rectangle(widthMicrometres = 200_000L, heightMicrometres = 150_000L)
        assertEquals(30_000_000_000L, top.areaSquareMicrometres)
        assertEquals(300.0, AreaUnit.CM2.value(top.areaSquareMicrometres), 1e-9)
        assertEquals(30_000.0, AreaUnit.MM2.value(top.areaSquareMicrometres), 1e-9)
        assertEquals(700_000L, top.perimeterMicrometres)
    }

    @Test
    fun theSameAreaReadsTheSameInEveryAreaUnit() {
        val area = Shapes.rectangleArea(20_000L, 15_000L)
        assertEquals(300.0, AreaUnit.MM2.value(area), 1e-9)
        assertEquals(3.0, AreaUnit.CM2.value(area), 1e-9)
        assertEquals(300.0 / 645.16, AreaUnit.IN2.value(area), 1e-9)
        assertEquals(area, AreaUnit.IN2.squareMicrometres(AreaUnit.IN2.value(area)))
    }

    @Test
    fun nothingDrawnAndADragPastTheStartLineBothGiveNoArea() {
        assertEquals(0L, Shapes.rectangleArea(0L, 15_000L))
        assertEquals(0L, Shapes.rectangleArea(-5_000L, 15_000L))
        assertEquals(0L, Rectangle(-5_000L, 15_000L).areaSquareMicrometres)
        assertEquals(0L, Shapes.circleAreaSquareMicrometres(0L))
        assertEquals(0L, Shapes.circleAreaSquareMicrometres(-1L))
        assertEquals(0L, Shapes.circleCircumferenceMicrometres(-1L))
    }

    @Test
    fun aCircleIsMeasuredFromItsDiameterTheWayARoundObjectIsLaidOnAPlate() {
        // A 10 mm circle: π × 5² = 78.54 mm² and π × 10 = 31.42 mm round the edge.
        assertEquals(78_539_816L, Shapes.circleAreaSquareMicrometres(10_000L))
        assertEquals(31_416L, Shapes.circleCircumferenceMicrometres(10_000L))
        assertEquals(78.5, AreaUnit.MM2.value(Shapes.circleAreaSquareMicrometres(10_000L)), 0.05)
    }

    @Test
    fun aRoomSizedAreaStaysInsideLongWithoutLosingItsLastMicrometre() {
        // A 10 m × 10 m room is 100 m²: 1e14 square micrometres, four orders below Long.MAX_VALUE, so
        // no screen can overflow the stored area into a negative number.
        val room = Shapes.rectangleArea(10_000_000L, 10_000_000L)
        assertEquals(100_000_000_000_000L, room)
        assertEquals(100.0, AreaUnit.CM2.value(room) / 10_000.0, 1e-9)
    }

    @Test
    fun areaDigitsRoundTheWayTheRulerRoundsAndFollowTheReadersLanguage() {
        val area = 12_345_678L
        assertEquals("12.3", AreaFormatter.numeric(area, AreaUnit.MM2, '.'))
        assertEquals("0.12", AreaFormatter.numeric(area, AreaUnit.CM2, '.'))
        assertEquals("0.02", AreaFormatter.numeric(area, AreaUnit.IN2, '.'))
        assertEquals("12,3", AreaFormatter.numeric(area, AreaUnit.MM2, ','))
        // A whole area prints as a whole number, the same rule the length formatter follows: "300" and
        // not "300.0" beside a table top.
        assertEquals("300", AreaFormatter.numeric(300_000_000L, AreaUnit.MM2, '.'))
        assertEquals("3", AreaFormatter.numeric(300_000_000L, AreaUnit.CM2, '.'))
    }

    @Test
    fun anAreaUnitIsRecognisedByNameAndALengthUnitIsNot() {
        // The log row asks this to decide whether its number is a length or an area. A stored name from
        // another build must answer null, not throw inside a row (finding 14).
        assertEquals(AreaUnit.CM2, AreaUnit.of("CM2"))
        assertNull(AreaUnit.of("MM"))
        assertNull(AreaUnit.of("SQUARE_METRES"))
        assertNull(AreaUnit.of(""))
    }

    @Test
    fun eachAreaUnitNamesItsOwnLengthUnitSoSidesAndAreaNeverDisagree() {
        assertEquals(MeasurementUnit.MM, AreaUnit.MM2.lengthUnit)
        assertEquals(MeasurementUnit.CM, AreaUnit.CM2.lengthUnit)
        assertEquals(MeasurementUnit.INCH, AreaUnit.IN2.lengthUnit)
    }
}
