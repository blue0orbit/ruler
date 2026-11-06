package com.blueorbit.ruler.core.ads

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdPolicyTest {

    private val minute = 60_000L

    private fun may(
        now: Long = 10 * minute,
        launched: Long = 0L,
        last: Long? = null,
        firstSession: Boolean = false,
        pro: Boolean = false,
        consent: Boolean = true,
        loaded: Boolean = true
    ) = AdPolicy.mayShow(now, launched, last, firstSession, pro, consent, loaded)

    @Test
    fun aFreeUserAtABreakWithEverythingAllowedSeesTheAd(): Unit = assertTrue(may())

    @Test
    fun proUsersNeverSeeAnAd(): Unit = assertFalse(may(pro = true))

    @Test
    fun theFirstSessionHasNoAd(): Unit = assertFalse(may(firstSession = true))

    @Test
    fun noAdWithoutConsent(): Unit = assertFalse(may(consent = false))

    @Test
    fun noAdIsFetchedWhileTheUserWaits(): Unit = assertFalse(may(loaded = false))

    @Test
    fun notTooSoonAfterLaunch(): Unit {
        assertFalse(may(now = AdPolicy.MIN_AFTER_LAUNCH_MS - 1))
        assertTrue(may(now = AdPolicy.MIN_AFTER_LAUNCH_MS))
    }

    @Test
    fun atMostOneEveryFiveMinutes(): Unit {
        val last = 10 * minute
        assertFalse(may(now = last + AdPolicy.MIN_BETWEEN_MS - 1, last = last))
        assertTrue(may(now = last + AdPolicy.MIN_BETWEEN_MS, last = last))
    }
}
