package com.blueorbit.ruler.core.navigation

import com.blueorbit.ruler.core.ui.AppRoute
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The navigation rules (M14), tested against a stack with the same semantics as the real one.
 *
 * The bug these tests exist for only showed on a fresh install: the intro handed over to calibration while
 * popping itself out of the back stack, which left the wizard as the only entry there. `popBackStack()` has
 * nothing to pop in that position, so the in-app "Back" did nothing and the only way out was to force-stop
 * the app. A stack of routes is exactly what that bug turns on, so a list models it faithfully.
 */
class NavActionsTest {

    /** A back stack that behaves like one: one entry cannot be popped, and `popUpTo` truncates above it. */
    private class FakeStack(start: String) : RouteStack {
        private val entries = mutableListOf(start)

        fun depth() = entries.size

        fun contains(route: String) = entries.contains(route)

        /** The route directly under the one on screen — the screen a Back control would land on. */
        fun routeUnderTop(): String? = entries.getOrNull(entries.lastIndex - 1)

        override fun currentRoute(): String = entries.last()

        override fun popBackStack(): Boolean {
            if (entries.size <= 1) return false
            entries.removeAt(entries.lastIndex)
            return true
        }

        override fun popTo(route: String): Boolean {
            val index = entries.lastIndexOf(route)
            if (index < 0) return false
            while (entries.size > index + 1) entries.removeAt(entries.lastIndex)
            return true
        }

        override fun go(route: String, options: NavOptions) {
            if (options.clearStack) {
                entries.clear()
            } else if (options.popUpTo != null) {
                val index = entries.lastIndexOf(options.popUpTo)
                if (index >= 0) {
                    val keep = if (options.inclusive) index else index + 1
                    while (entries.size > keep) entries.removeAt(entries.lastIndex)
                }
            }
            if (options.singleTop && entries.lastOrNull() == route) return
            entries.add(route)
        }
    }

    private fun actions(start: String): Pair<NavActions, FakeStack> {
        val stack = FakeStack(start)
        return NavActions(stack) to stack
    }

    @Test
    fun theIntroHandsOverToTheRulerAndNeverToCalibration() {
        val (nav, stack) = actions(AppNav.ONBOARDING)

        nav.finishIntroToRuler()
        assertEquals(AppRoute.RULER.route, nav.currentRoute())
        assertEquals(1, stack.depth())
        // M17 removed the intro's route to calibration entirely. A user who has not measured anything yet
        // has no reason to be asked to make the ruler exact, and the wizard can no longer be reached in a
        // stack that has nothing under it.
        assertEquals(false, stack.contains(AppNav.CALIBRATION))
    }

    /** The intro is popped out inclusive, so it cannot be fallen back into. */
    @Test
    fun finishedIntroIsNotLeftBehindOnTheStack() {
        val (nav, stack) = actions(AppNav.ONBOARDING)

        nav.finishIntroToRuler()
        assertEquals(AppRoute.RULER.route, nav.currentRoute())
        assertEquals(1, stack.depth())

        nav.back()
        assertEquals(AppRoute.RULER.route, nav.currentRoute())
    }

    /**
     * Settings → "Show intro" → Start. The ruler is already at the bottom of the stack, so finishing
     * must return to it rather than push a second one: the old rule left `ruler, settings, ruler`, and
     * Back from the finished intro landed on a duplicate ruler and then on Settings again.
     */
    @Test
    fun finishingAnIntroReopenedFromSettingsLeavesOneRuler() {
        val (nav, stack) = actions(AppRoute.RULER.route)
        nav.openBar(AppRoute.SETTINGS)
        nav.showIntro()
        assertEquals(AppNav.ONBOARDING, nav.currentRoute())

        nav.finishIntroToRuler()
        assertEquals(AppRoute.RULER.route, nav.currentRoute())
        assertEquals(1, stack.depth())
        assertEquals(false, stack.contains(AppNav.ONBOARDING))
        assertEquals(false, stack.contains(AppRoute.SETTINGS.route))
    }

    /**
     * The trap itself: a detail screen that is the only entry on the stack. Popping fails there, and a Back
     * control that fails silently is what closed the app on the test device.
     */
    @Test
    fun aDetailScreenThatIsTheOnlyEntryCanStillBeLeft() {
        val (nav, _) = actions(AppNav.CALIBRATION)

        nav.back()
        assertEquals(AppRoute.RULER.route, nav.currentRoute())
    }

    @Test
    fun barNavigationKeepsOneEntryPerDestination() {
        val (nav, stack) = actions(AppRoute.RULER.route)

        nav.openBar(AppRoute.TOOLS)
        nav.openBar(AppRoute.LOG)
        // Tapping the same destination twice must not stack a second copy behind the first.
        nav.openBar(AppRoute.LOG)
        assertEquals(AppRoute.LOG.route, nav.currentRoute())
        assertEquals(2, stack.depth())

        nav.back()
        assertEquals(AppRoute.RULER.route, nav.currentRoute())
    }

    @Test
    fun openingADetailScreenFromABarScreenKeepsTheBarScreenUnderneath() {
        val (nav, _) = actions(AppRoute.TOOLS.route)

        nav.openDetail(AppNav.EXTEND)
        assertEquals(AppNav.EXTEND, nav.currentRoute())

        nav.back()
        assertEquals(AppRoute.TOOLS.route, nav.currentRoute())
    }

    /**
     * M16: "Done" at the end of the guided calibration. The user came to fix the ruler, so finishing
     * has to put them on it — from Settings, from the intro, or from a stack that has no ruler under
     * the wizard at all.
     */
    @Test
    fun finishingTheCalibrationLandsOnTheRulerFromEveryWayIn() {
        // Opened from Settings: the wizard, Settings and the bar screens above the ruler all go.
        val (fromSettings, settingsStack) = actions(AppRoute.RULER.route)
        fromSettings.openBar(AppRoute.SETTINGS)
        fromSettings.openDetail(AppNav.CALIBRATION)
        fromSettings.finishCalibration()
        assertEquals(AppRoute.RULER.route, fromSettings.currentRoute())
        assertEquals(1, settingsStack.depth())

        // Opened straight from the ruler: the wizard goes and the ruler stays.
        val (fromRuler, rulerStack) = actions(AppRoute.RULER.route)
        fromRuler.openDetail(AppNav.CALIBRATION)
        fromRuler.finishCalibration()
        assertEquals(AppRoute.RULER.route, fromRuler.currentRoute())
        assertEquals(1, rulerStack.depth())
    }

    /**
     * The same trap as [aDetailScreenThatIsTheOnlyEntryCanStillBeLeft], from the other control: Done
     * with no ruler underneath must still land on the ruler, and must not leave the finished wizard
     * behind it where the Back control would fall back into it.
     */
    @Test
    fun finishingTheCalibrationWithNothingUnderItClearsTheStack() {
        val (nav, stack) = actions(AppNav.CALIBRATION)

        nav.finishCalibration()
        assertEquals(AppRoute.RULER.route, nav.currentRoute())
        assertEquals(1, stack.depth())
    }

    /** Every detail route the app owns must be reachable from the ruler and reversible. */
    @Test
    fun everyDetailRouteIsReversible() {
        listOf(AppNav.CALIBRATION, AppNav.EXTEND, AppNav.LEVEL, AppNav.PROTRACTOR, AppNav.PRO).forEach { route ->
            val (nav, _) = actions(AppRoute.RULER.route)
            nav.openDetail(route)
            assertEquals(route, nav.currentRoute())
            nav.back()
            assertEquals(AppRoute.RULER.route, nav.currentRoute())
        }
    }
}
