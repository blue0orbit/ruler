package com.blueorbit.ruler.core.sensors

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

/**
 * The production pass on the level: the word "Level" must not flicker, TalkBack must not be told every
 * 0.1°, a zero is an average, and the numbers after zeroing are measured from the user's surface.
 */
class LevelReadingTest {

    private fun gravityAt(tiltDegrees: Double, directionDegrees: Double = 0.0): Gravity {
        val tilt = Math.toRadians(tiltDegrees)
        val direction = Math.toRadians(directionDegrees)
        return Gravity(sin(tilt) * cos(direction), sin(tilt) * sin(direction), cos(tilt))
    }

    @Test
    fun becomingLevelNeedsToGetBelowTheEnterThreshold() {
        assertFalse(LevelMath.isLevel(0.45, wasLevel = false))
        assertFalse(LevelMath.isLevel(0.3, wasLevel = false))
        assertTrue(LevelMath.isLevel(0.29, wasLevel = false))
    }

    @Test
    fun stayingLevelHoldsUntilPastTheLeaveThreshold() {
        // The band between the two thresholds is where a single threshold used to flicker.
        assertTrue(LevelMath.isLevel(0.45, wasLevel = true))
        assertTrue(LevelMath.isLevel(0.6, wasLevel = true))
        assertFalse(LevelMath.isLevel(0.61, wasLevel = true))
    }

    @Test
    fun aReadingJitteringInsideTheBandNeverChangesTheWord() {
        var level = LevelMath.isLevel(0.2, wasLevel = false)
        for (angle in listOf(0.35, 0.55, 0.4, 0.58, 0.31, 0.5)) {
            level = LevelMath.isLevel(angle, level)
            assertTrue(level)
        }
    }

    @Test
    fun theAnnouncedDegreeIgnoresJitterAroundTheHalf() {
        val first = LevelMath.announcedWholeDegree(2.45, null)
        assertEquals(2, first)
        assertEquals(2, LevelMath.announcedWholeDegree(2.55, first))
        assertEquals(2, LevelMath.announcedWholeDegree(2.7, first))
        assertEquals(3, LevelMath.announcedWholeDegree(2.8, first))
        assertEquals(5, LevelMath.announcedWholeDegree(5.1, first))
    }

    @Test
    fun theAverageOfSamplesIsANormalisedMeanDirection() {
        val mean = LevelMath.averageGravity(listOf(gravityAt(1.0, 0.0), gravityAt(1.0, 180.0)))!!
        assertEquals(0.0, LevelMath.tiltDeg(mean), 1e-9)
        assertEquals(1.0, mean.length, 1e-9)
        assertEquals(2.0, LevelMath.tiltDeg(LevelMath.averageGravity(listOf(gravityAt(2.0, 30.0)))!!), 1e-9)
    }

    @Test
    fun nothingToAverageGivesNoReference() {
        assertNull(LevelMath.averageGravity(emptyList()))
        assertNull(LevelMath.averageGravity(listOf(Gravity(0.0, 0.0, 0.0))))
    }

    @Test
    fun theReferenceItselfIsFlatRelativeToItself() {
        val reference = gravityAt(4.0, 70.0)
        val relative = LevelMath.relativeToReference(reference, reference)
        assertEquals(0.0, LevelMath.tiltDeg(relative), 1e-6)
    }

    @Test
    fun theRelativeTiltIsTheAngleToTheReference() {
        val reference = gravityAt(3.0, 40.0)
        val current = gravityAt(5.0, 200.0)
        assertEquals(
            LevelMath.angleBetweenDeg(current, reference),
            LevelMath.tiltDeg(LevelMath.relativeToReference(current, reference)),
            1e-6
        )
    }

    @Test
    fun withAFlatReferenceNothingChanges() {
        val current = gravityAt(5.0, 120.0)
        val relative = LevelMath.relativeToReference(current, Gravity(0.0, 0.0, 1.0))
        assertEquals(current.x, relative.x, 1e-12)
        assertEquals(current.y, relative.y, 1e-12)
        assertEquals(current.z, relative.z, 1e-12)
    }

    @Test
    fun theAxesSplitFollowsTheDisplayRotation() {
        val tipAlongX = gravityAt(5.0, 0.0)
        val portrait = LevelMath.axisAnglesDeg(tipAlongX, 0.0)
        assertEquals(5.0, portrait.sideToSideDeg, 1e-6)
        assertEquals(0.0, portrait.topToBottomDeg, 1e-6)

        val landscape = LevelMath.axisAnglesDeg(tipAlongX, 90.0)
        assertEquals(0.0, landscape.sideToSideDeg, 1e-6)
        assertEquals(5.0, landscape.topToBottomDeg, 1e-6)
    }

    @Test
    fun theAxesAreFlatOnAFlatPhone() {
        val flat = LevelMath.axisAnglesDeg(Gravity(0.0, 0.0, 1.0), 0.0)
        assertEquals(0.0, flat.sideToSideDeg, 1e-9)
        assertEquals(0.0, flat.topToBottomDeg, 1e-9)
    }
}
