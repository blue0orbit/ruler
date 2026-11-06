package com.blueorbit.ruler.feature.pro

import android.app.Activity
import androidx.lifecycle.ViewModel
import com.blueorbit.ruler.core.entitlement.BillingEntitlementRepository
import com.blueorbit.ruler.core.entitlement.EntitlementRepository
import com.blueorbit.ruler.core.entitlement.EntitlementState
import com.blueorbit.ruler.core.entitlement.EntitlementStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Why the last purchase tap did not open the Play sheet, so the screen can say something true about
 * it. Each value maps to its own sentence; none of them is a blanket "cannot purchase".
 */
enum class PurchaseProblem {
    /** This build has no Play connection at all (the debug fake, or a free build). */
    BUILD_HAS_NO_PLAY,
    /** Play could not be reached right now. */
    PLAY_UNREACHABLE,
    /** Play is reachable but has not described the product yet (no details, no offer). */
    OFFER_NOT_LOADED
}

/** What "Check again" found, reported once as a message. */
enum class CheckResult { ASKED_PLAY, BUILD_HAS_NO_PLAY, PLAY_UNREACHABLE }

/**
 * A thin view over the entitlement gate (rule 10). The screen reads the state the repository publishes
 * and asks the repository to purchase; no unlock decision is made here, so the rule stays in one place
 * and is testable on the JVM with the billing library stubbed.
 *
 * What it does add is the *reason* a request failed, because the repository only answers yes or no
 * and the right sentence differs: a debug build that never talks to Play, a phone that cannot reach
 * Play right now, and a product Play has not described yet are three different situations for the
 * user.
 */
class PurchaseViewModel(private val entitlement: EntitlementRepository) : ViewModel() {

    val state: StateFlow<EntitlementState> = entitlement.state

    /**
     * Only the Play implementation can ever open the sheet. The debug fake and the free implementation
     * answer false by design, and saying "Play isn't reachable" about them would be untrue.
     */
    private val buildTalksToPlay: Boolean = entitlement is BillingEntitlementRepository

    private val _problem = MutableStateFlow<PurchaseProblem?>(null)
    val problem: StateFlow<PurchaseProblem?> = _problem.asStateFlow()

    /** Re-read the store quietly (on opening the screen). Returns false when the store cannot be reached. */
    fun refresh(): Boolean = entitlement.refresh()

    /** "Check again": re-read the store and say what happened. */
    fun checkAgain(): CheckResult {
        val reached = entitlement.refresh()
        if (reached) _problem.value = null
        return when {
            reached -> CheckResult.ASKED_PLAY
            !buildTalksToPlay -> CheckResult.BUILD_HAS_NO_PLAY
            else -> CheckResult.PLAY_UNREACHABLE
        }
    }

    /** Opens the Play sheet. On failure [problem] says why; on success it is cleared. */
    fun purchase(activity: Activity) {
        _problem.value = when {
            entitlement.launchPurchase(activity) -> null
            !buildTalksToPlay -> PurchaseProblem.BUILD_HAS_NO_PLAY
            state.value.status == EntitlementStatus.UNAVAILABLE -> PurchaseProblem.PLAY_UNREACHABLE
            else -> PurchaseProblem.OFFER_NOT_LOADED
        }
    }
}
