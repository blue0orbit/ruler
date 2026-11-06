package com.blueorbit.ruler.core.entitlement

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EntitlementGateTest {

    @Test
    fun freeBuildDeniesEveryProFeature() {
        val repo = FreeEntitlementRepository()
        for (feature in ProFeature.entries) {
            assertFalse("free tier must deny ${feature.name}", repo.has(feature))
        }
    }

    @Test
    fun gateIsTestableWithoutTheBillingLibrary(): Unit {
        // A fake repository proves gating can be tested without a purchase (M11 acceptance).
        val pro = FakeEntitlementRepository(unlocked = true)
        for (feature in ProFeature.entries) {
            assert(pro.has(feature))
        }
    }
}
