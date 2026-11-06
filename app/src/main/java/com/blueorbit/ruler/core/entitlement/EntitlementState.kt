package com.blueorbit.ruler.core.entitlement

/**
 * What the gate knows about the purchase. Kept as plain data, with the billing types on the other
 * side of the repository, so the rule can be tested on the JVM without Play (rule 10).
 *
 * PLAN M10/M11 lists this file under `feature/pro`; it lives here because the gate is checked at the
 * repository boundary and a core package must not import a feature package.
 */
enum class EntitlementStatus {
    /** Connected, but the purchase has not been read yet. */
    CHECKING,
    /** Paid and confirmed. */
    UNLOCKED,
    /** Connected, no purchase for this product. */
    LOCKED,
    /** Paid but pending — the money has not settled, so nothing is unlocked. */
    PENDING,
    /** Play could not be reached, so the build stays free rather than guessing. */
    UNAVAILABLE
}

/** A purchase reduced to the facts the rule needs. */
data class PurchaseFacts(
    val productId: String,
    val purchased: Boolean,
    val pending: Boolean,
    val acknowledged: Boolean
)

data class EntitlementState(
    val status: EntitlementStatus,
    val productId: String,
    /** Price as Play reports it, for the purchase screen. Empty until the product is read. */
    val priceLabel: String = ""
) {
    val unlocked: Boolean get() = status == EntitlementStatus.UNLOCKED
}

object EntitlementRule {

    /** One-time managed product. Create it in Play Console — see HUMAN_CHECKS.md. */
    const val PRODUCT_ID = "pro_one_time"

    /**
     * The rule, in one place:
     * - a **paid** purchase of this product unlocks, whether or not it has been acknowledged yet. This is
     *   Google's order of operations: grant the entitlement once the purchase is PURCHASED, then
     *   acknowledge it. Waiting for the acknowledgement left a customer who had just paid locked out
     *   whenever that call was slow or failed. Acknowledgement is still sent (and retried on every
     *   query); a purchase that is never acknowledged is refunded by Play after three days and then
     *   disappears from the query, which locks again.
     * - a pending purchase does not unlock; a purchase of a different product does not unlock.
     * - when the store connection drops, what this process already knows stays true: a customer who was
     *   unlocked a moment ago is not re-locked because Play's service restarted. With nothing known,
     *   an unreachable store stays locked.
     */
    fun stateFor(
        facts: List<PurchaseFacts>,
        connected: Boolean,
        productId: String = PRODUCT_ID,
        priceLabel: String = ""
    ): EntitlementState {
        val ours = facts.filter { it.productId == productId }
        if (ours.any { it.purchased && !it.pending }) {
            return EntitlementState(EntitlementStatus.UNLOCKED, productId, priceLabel)
        }
        if (!connected) return EntitlementState(EntitlementStatus.UNAVAILABLE, productId, priceLabel)
        if (ours.any { it.pending }) {
            return EntitlementState(EntitlementStatus.PENDING, productId, priceLabel)
        }
        return EntitlementState(EntitlementStatus.LOCKED, productId, priceLabel)
    }
}
