package com.blueorbit.ruler.core.ads

/**
 * When the one interstitial may appear. Pure, so the rules are tested on the JVM.
 *
 * The rules come from AdMob's interstitial guidance (natural transitions only; not on launch or exit;
 * never interrupting a task) and from what ruler-app reviews punish: an ad before the first
 * measurement, or an ad over the ruler while lining something up. So the ad:
 * - never shows to Pro users — removing it is part of the one-time unlock;
 * - never shows in the first session, so a new user meets the ruler before any ad;
 * - waits [MIN_AFTER_LAUNCH_MS] after launch and [MIN_BETWEEN_MS] after the previous one;
 * - only shows at a tab switch (the caller decides that), never during measuring, calibration,
 *   onboarding or a Pro tool;
 * - only when consent allows ads and an ad is already loaded — nothing is fetched while the user waits.
 */
object AdPolicy {

    const val MIN_AFTER_LAUNCH_MS = 2 * 60_000L
    const val MIN_BETWEEN_MS = 5 * 60_000L

    fun mayShow(
        nowMs: Long,
        launchedAtMs: Long,
        lastShownAtMs: Long?,
        firstSession: Boolean,
        pro: Boolean,
        canRequestAds: Boolean,
        adLoaded: Boolean
    ): Boolean {
        if (pro || firstSession || !canRequestAds || !adLoaded) return false
        if (nowMs - launchedAtMs < MIN_AFTER_LAUNCH_MS) return false
        if (lastShownAtMs != null && nowMs - lastShownAtMs < MIN_BETWEEN_MS) return false
        return true
    }
}
