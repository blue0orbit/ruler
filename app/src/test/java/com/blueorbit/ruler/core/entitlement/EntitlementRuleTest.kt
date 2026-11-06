package com.blueorbit.ruler.core.entitlement

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The entitlement rule is tested as pure data, with the billing types on the other side of the
 * repository (rule 10). Every path that is not "paid for this product" must stay locked, and a paying
 * customer must never be locked out by a slow acknowledgement or a dropped store connection.
 */
class EntitlementRuleTest {

    private fun fact(
        productId: String = EntitlementRule.PRODUCT_ID,
        purchased: Boolean = true,
        pending: Boolean = false,
        acknowledged: Boolean = true
    ) = PurchaseFacts(productId, purchased, pending, acknowledged)

    @Test
    fun confirmedPurchaseOfThisProductUnlocks(): Unit {
        val state = EntitlementRule.stateFor(listOf(fact()), connected = true)
        assertEquals(EntitlementStatus.UNLOCKED, state.status)
        assertTrue(state.unlocked)
    }

    @Test
    fun pendingPurchaseDoesNotUnlock(): Unit {
        // Money has not settled. Play can still reverse it, so nothing is unlocked.
        val state = EntitlementRule.stateFor(listOf(fact(purchased = false, pending = true)), connected = true)
        assertEquals(EntitlementStatus.PENDING, state.status)
        assertFalse(state.unlocked)
    }

    @Test
    fun purchaseOfADifferentProductDoesNotUnlock(): Unit {
        val state = EntitlementRule.stateFor(listOf(fact(productId = "some_other_product")), connected = true)
        assertEquals(EntitlementStatus.LOCKED, state.status)
        assertFalse(state.unlocked)
    }

    @Test
    fun aPaidPurchaseUnlocksBeforeItIsAcknowledged(): Unit {
        // Google's order: grant on PURCHASED, then acknowledge. A customer who just paid must not wait
        // on the acknowledgement call (which can be slow or fail and is retried later).
        val state = EntitlementRule.stateFor(listOf(fact(acknowledged = false)), connected = true)
        assertEquals(EntitlementStatus.UNLOCKED, state.status)
    }

    @Test
    fun aDroppedConnectionDoesNotReLockAPayingCustomer(): Unit {
        val state = EntitlementRule.stateFor(listOf(fact()), connected = false)
        assertEquals(EntitlementStatus.UNLOCKED, state.status)
    }

    @Test
    fun anUnreachableStoreWithNothingKnownStaysLocked(): Unit {
        val state = EntitlementRule.stateFor(emptyList(), connected = false)
        assertEquals(EntitlementStatus.UNAVAILABLE, state.status)
        assertFalse(state.unlocked)
    }

    @Test
    fun aPendingPurchaseOnADroppedConnectionStaysLocked(): Unit {
        val state = EntitlementRule.stateFor(listOf(fact(purchased = false, pending = true)), connected = false)
        assertFalse(state.unlocked)
    }

    @Test
    fun noPurchasesIsLocked(): Unit {
        assertEquals(EntitlementStatus.LOCKED, EntitlementRule.stateFor(emptyList(), connected = true).status)
    }

    @Test
    fun aConfirmedPurchaseAlongsideAPendingOneStillUnlocks(): Unit {
        val state = EntitlementRule.stateFor(
            listOf(
                fact(purchased = false, pending = true),
                fact()
            ),
            connected = true
        )
        assertEquals(EntitlementStatus.UNLOCKED, state.status)
    }

    @Test
    fun thePriceLabelTravelsThroughTheRule(): Unit {
        val state = EntitlementRule.stateFor(listOf(fact()), connected = true, priceLabel = "€5.99")
        assertEquals("€5.99", state.priceLabel)
        assertEquals(EntitlementRule.PRODUCT_ID, state.productId)
    }

    @Test
    fun theGateIsDerivedFromTheStateForEveryFeature(): Unit {
        val pro = FakeEntitlementRepository(unlocked = true)
        val free = FakeEntitlementRepository(unlocked = false)

        for (feature in ProFeature.entries) {
            assertTrue("${feature.name} should be unlocked", pro.has(feature))
            assertFalse("${feature.name} should stay locked", free.has(feature))
        }
    }

    @Test
    fun freeBuildStaysLockedWithTheStoreStubbed(): Unit {
        // M11 acceptance: the free build works without the billing library. There is no store to read,
        // so refresh reports false and nothing unlocks — the gate is the same code path the billing
        // implementation feeds through the rule.
        val free = FreeEntitlementRepository()
        assertFalse(free.state.value.unlocked)
        assertFalse(free.has(ProFeature.TAGS))
        assertFalse(free.refresh())
    }
}
