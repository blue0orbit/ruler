package com.blueorbit.ruler.core.calibration

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class DeviceKeyTest {

    @Test
    fun keyIncludesModelAndResolution() {
        assertEquals("Pixel_9_Pro_Fold_1080x2424", DeviceKey.build("Pixel 9 Pro Fold", 1080, 2424))
    }

    @Test
    fun punctuationInModelIsReplacedSoTheKeyStaysSafe() {
        assertEquals("SM_G991B_1080x2424", DeviceKey.build("SM-G991B", 1080, 2424))
    }

    @Test
    fun orientationDoesNotChangeTheKey() {
        assertEquals(DeviceKey.build("Pixel 9 Pro Fold", 1080, 2424), DeviceKey.build("Pixel 9 Pro Fold", 2424, 1080))
    }

    @Test
    fun aDifferentDeviceGetsADifferentKey() {
        assertNotEquals(DeviceKey.build("Pixel 9 Pro Fold", 1080, 2424), DeviceKey.build("Pixel 8", 1080, 2424))
    }
}
