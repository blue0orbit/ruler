package com.blueorbit.ruler.core.entitlement

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * M11R finding 6: Play calls `PurchasesUpdatedListener` with `OK` and a **null** purchase list when
 * the purchase state did not change. Treating a null list as "nothing is owned" re-locked an
 * already-entitled user mid-session. The merge rule is pure, so it is tested without Play.
 */
class BillingFactsTest {

    private val owned = PurchaseFacts(EntitlementRule.PRODUCT_ID, purchased = true, pending = false, acknowledged = true)

    @Test
    fun aNullReportLeavesTheStoredFactsAlone() {
        val current = listOf(owned)
        assertSame(current, entitlementFactsAfter(current, null))
    }

    @Test
    fun aNullReportDoesNotUnlockFromAnEmptyState() {
        assertEquals(emptyList<PurchaseFacts>(), entitlementFactsAfter(emptyList(), null))
    }

    @Test
    fun aReportedListReplacesTheStoredFacts() {
        val reported = listOf(PurchaseFacts(EntitlementRule.PRODUCT_ID, purchased = false, pending = true, acknowledged = false))
        assertEquals(reported, entitlementFactsAfter(listOf(owned), reported))
    }

    @Test
    fun anEmptyReportedListIsARealAnswerAndClearsTheFacts() {
        // A non-null, empty list is Play saying "you own nothing", which is different from null.
        assertEquals(emptyList<PurchaseFacts>(), entitlementFactsAfter(listOf(owned), emptyList()))
    }
}
