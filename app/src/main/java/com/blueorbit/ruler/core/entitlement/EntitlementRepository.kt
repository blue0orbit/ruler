package com.blueorbit.ruler.core.entitlement

import android.app.Activity
import kotlinx.coroutines.flow.StateFlow

/** Pro = extend mode, level & protractor, custom themes, tags. Everything else is free. */
enum class ProFeature {
    EXTEND_MODE,
    LEVEL_AND_PROTRACTOR,
    CUSTOM_THEMES,
    TAGS
}

/**
 * Entitlement is a gate, not a fork (rule 10): the free build must work with no billing library, so
 * the default implementation is the free one and Play Billing is wired only in M11. Every Pro feature
 * comes from the same one-time unlock, which is why `has` is derived from the state rather than
 * decided per feature in each screen.
 */
interface EntitlementRepository {

    val state: StateFlow<EntitlementState>

    fun has(feature: ProFeature): Boolean = state.value.unlocked

    /** Connect to the store. Implementations that do not need a connection do nothing. */
    fun start() {}

    /** Re-read the store. Returns false when the store cannot be reached. */
    fun refresh(): Boolean = false

    /** Opens the Play purchase sheet. Returns false when this build cannot purchase. */
    fun launchPurchase(activity: Activity): Boolean = false
}

/** The implementation the free build uses: nothing is unlocked, and there is no billing dependency. */
class FreeEntitlementRepository : EntitlementRepository {

    private val locked = kotlinx.coroutines.flow.MutableStateFlow(
        EntitlementState(EntitlementStatus.LOCKED, EntitlementRule.PRODUCT_ID)
    )

    override val state: StateFlow<EntitlementState> = locked

    // Explicit, not inherited: a free build must stay locked even if a store were reachable.
    override fun has(feature: ProFeature): Boolean = false
}

/**
 * Fake used by tests, and by debug builds only. It is not a way to bypass the gate in a shipped
 * build — `AppContainer` reaches it only when `BuildConfig.DEBUG` is true, and the release build
 * always uses `BillingEntitlementRepository`.
 */
class FakeEntitlementRepository(private val unlocked: Boolean) : EntitlementRepository {

    private val flow = kotlinx.coroutines.flow.MutableStateFlow(
        EntitlementState(
            if (unlocked) EntitlementStatus.UNLOCKED else EntitlementStatus.LOCKED,
            EntitlementRule.PRODUCT_ID
        )
    )

    override val state: StateFlow<EntitlementState> = flow
}
