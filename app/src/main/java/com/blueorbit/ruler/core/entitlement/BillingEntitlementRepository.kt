package com.blueorbit.ruler.core.entitlement

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.AcknowledgePurchaseResponseListener
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.ProductDetailsResponseListener
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesResponseListener
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The facts after Play reports an update. A **null** list means "nothing was returned", not "nothing
 * is owned": `PurchasesUpdatedListener` is called with `OK` and a null purchase list when the purchase
 * state did not change, and replacing the stored facts with an empty list re-locked an already-entitled
 * user mid-session (finding 6). A list that is present replaces the facts as before.
 *
 * Pure, so the merge rule is tested on the JVM without Play (`BillingFactsTest`).
 */
internal fun entitlementFactsAfter(
    current: List<PurchaseFacts>,
    reported: List<PurchaseFacts>?
): List<PurchaseFacts> = reported ?: current

/**
 * Play Billing behind `EntitlementRepository`.
 *
 * Written reason for the dependency (DECISIONS.md: no new SDK without a reason): a one-time in-app
 * purchase can only be completed and verified through Play, and the app has no other way to know the
 * entitlement. It adds `com.android.vending.BILLING` to the merged manifest — see
 * `docs/permissions-audit.md`. No ad or analytics SDK is added.
 *
 * Every path that is not "paid for this product" stays locked, so a device without Play or a pending
 * payment never unlocks Pro (rule 10). Purchases are re-read whenever the app returns to the
 * foreground ([refresh] from `MainActivity.onResume`), so a purchase completed outside the app (a
 * pending payment that settled, a promo code redeemed in the Play Store, a refund) is seen without a
 * restart.
 */
class BillingEntitlementRepository(
    private val context: Context,
    private val productId: String = EntitlementRule.PRODUCT_ID
) : EntitlementRepository {

    private val _state = MutableStateFlow(EntitlementState(EntitlementStatus.CHECKING, productId))
    override val state: StateFlow<EntitlementState> = _state.asStateFlow()

    // Written from Play's binder threads and read from the UI thread, so every mutable field is
    // volatile — without it the UI can read a stale `connected` or a stale fact list (finding 7).
    @Volatile
    private var connected = false

    @Volatile
    private var priceLabel = ""

    @Volatile
    private var lastFacts: List<PurchaseFacts> = emptyList()

    /** The product as Play last described it; the purchase sheet cannot be opened without it. */
    @Volatile
    private var productDetails: ProductDetails? = null

    private val client: BillingClient = BillingClient.newBuilder(context)
        // A one-time product can be pending, and a pending purchase must not unlock anything.
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        // Billing 8+: the library re-establishes a dropped connection itself on the next call, instead of
        // every caller having to notice the disconnect and call start() again.
        .enableAutoServiceReconnection()
        .setListener(PurchasesUpdatedListener { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                // The listener reports OK with a *null* list when nothing changed; a null list must
                // leave the stored facts alone rather than re-lock the user (finding 6).
                publish(entitlementFactsAfter(lastFacts, purchases?.map { factFor(it) }))
                purchases?.forEach(::acknowledgeIfNeeded)
            } else if (result.responseCode == BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED) {
                // Bought before (another device, a reinstall): Play refuses the new purchase because it
                // is already owned. That is a restore, not a failure, so read the purchases and unlock.
                queryPurchases()
            } else {
                // The purchase did not complete. Whatever was true before stays true.
                publish(lastFacts)
            }
        })
        .build()

    override fun start() {
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                connected = result.responseCode == BillingClient.BillingResponseCode.OK
                if (connected) {
                    queryPurchases()
                    queryPrice()
                } else {
                    publish(lastFacts)
                }
            }

            override fun onBillingServiceDisconnected() {
                connected = false
                publish(lastFacts)
            }
        })
    }

    override fun refresh(): Boolean {
        if (!client.isReady) {
            start()
            return false
        }
        queryPurchases()
        // A product that could not be read at connection time is retried here, so "Check again"
        // also recovers a missing price and the details the purchase sheet needs.
        if (productDetails == null) queryPrice()
        return true
    }

    /**
     * Opens the Play sheet, and returns true only when Play says it opened.
     *
     * The sheet needs the product details. They are read at connection time ([queryPrice]) and kept,
     * so the launch is synchronous on the tap and its result is the real one. The first version queried
     * the details here and returned true before the query had answered — a missing product or offer
     * then did nothing at all while the screen behaved as if the sheet were on its way. Without the
     * details the answer is now false, and a fresh read is started so the next tap can succeed.
     */
    override fun launchPurchase(activity: Activity): Boolean {
        if (!client.isReady) {
            start()
            return false
        }

        val details = productDetails
        val token = details?.oneTimePurchaseOfferDetails?.offerToken
        if (details == null || token == null) {
            queryPrice()
            return false
        }
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(details)
                        .setOfferToken(token)
                        .build()
                )
            )
            .build()
        return client.launchBillingFlow(activity, params).responseCode == BillingClient.BillingResponseCode.OK
    }

    private fun queryPurchases() {
        client.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build(),
            PurchasesResponseListener { result, purchases ->
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    publish(purchases.map { factFor(it) })
                    purchases.forEach(::acknowledgeIfNeeded)
                } else {
                    publish(lastFacts)
                }
            }
        )
    }

    private fun queryPrice() {
        queryProductDetails { details ->
            productDetails = details
            priceLabel = details.oneTimePurchaseOfferDetails?.formattedPrice.orEmpty()
            publish(lastFacts)
        }
    }

    private fun queryProductDetails(onFound: (ProductDetails) -> Unit) {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(productId)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build()
                )
            )
            .build()

        client.queryProductDetailsAsync(
            params,
            ProductDetailsResponseListener { result, details ->
                if (result.responseCode != BillingClient.BillingResponseCode.OK) return@ProductDetailsResponseListener
                details.productDetailsList.firstOrNull { it.productId == productId }?.let(onFound)
            }
        )
    }

    /**
     * A purchase must be acknowledged or Play refunds it after three days, so the rule only counts a
     * confirmed purchase. Acknowledging is needed once, and only for a purchase that is already paid —
     * a pending purchase cannot be acknowledged.
     */
    private fun acknowledgeIfNeeded(purchase: Purchase) {
        val fact = factFor(purchase)
        if (!fact.purchased || fact.pending || fact.acknowledged) return

        client.acknowledgePurchase(
            AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build(),
            AcknowledgePurchaseResponseListener { result ->
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    markAcknowledged(fact)
                }
            }
        )
    }

    private fun markAcknowledged(fact: PurchaseFacts) {
        publish(lastFacts.filter { it != fact } + fact.copy(acknowledged = true))
    }

    private fun factFor(purchase: Purchase): PurchaseFacts = PurchaseFacts(
        productId = purchase.products.firstOrNull().orEmpty(),
        purchased = purchase.purchaseState == Purchase.PurchaseState.PURCHASED,
        pending = purchase.purchaseState == Purchase.PurchaseState.PENDING,
        acknowledged = purchase.isAcknowledged
    )

    private fun publish(facts: List<PurchaseFacts>) {
        lastFacts = facts
        _state.value = EntitlementRule.stateFor(facts, connected, productId, priceLabel)
    }

    fun close() {
        if (client.isReady) client.endConnection()
    }
}
