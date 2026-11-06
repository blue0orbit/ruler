package com.blueorbit.ruler.core.navigation

import androidx.navigation.NavHostController
import com.blueorbit.ruler.core.ui.AppRoute

/** The routes the bar does not own: the intro and the detail screens reached from Tools or Settings. */
object AppNav {
    const val ONBOARDING = "onboarding"
    const val CALIBRATION = "calibration"
    const val EXTEND = "extend"
    const val LEVEL = "level"
    const val PROTRACTOR = "protractor"
    const val RINGSIZE = "ring-size"
    const val PRO = "pro"
}

/** What a move asks the stack to do before the new route goes on it. */
data class NavOptions(
    val popUpTo: String? = null,
    val inclusive: Boolean = false,
    val saveState: Boolean = false,
    val restoreState: Boolean = false,
    val singleTop: Boolean = false,
    /** Remove every entry first. Used only as the last-resort exit in [NavActions.back]. */
    val clearStack: Boolean = false
)

/**
 * The part of a back stack the navigation rules need, stated as an interface so the rules are testable
 * without a device or a UI (M14). [NavHostRouteStack] is the production implementation; the unit test
 * substitutes a plain list with the same semantics.
 */
interface RouteStack {
    /** The route on screen now. */
    fun currentRoute(): String?

    /** Remove the top entry. Returns false when there is nothing to remove. */
    fun popBackStack(): Boolean

    /**
     * Remove entries until [route] is the one on screen, leaving [route] in place. Returns false when
     * the route is not on the stack at all, which is how a rule can tell "go back to it" from "open it
     * fresh" without knowing the stack's contents.
     */
    fun popTo(route: String): Boolean

    fun go(route: String, options: NavOptions = NavOptions())
}

class NavHostRouteStack(private val nav: NavHostController) : RouteStack {
    override fun currentRoute(): String? = nav.currentDestination?.route

    override fun popBackStack(): Boolean = nav.popBackStack()

    override fun popTo(route: String): Boolean = nav.popBackStack(route, inclusive = false)

    override fun go(route: String, options: NavOptions) {
        nav.navigate(route) {
            if (options.clearStack) {
                popUpTo(nav.graph.id) { inclusive = true }
            } else if (options.popUpTo != null) {
                popUpTo(options.popUpTo) { inclusive = options.inclusive; saveState = options.saveState }
            }
            launchSingleTop = options.singleTop
            restoreState = options.restoreState
        }
    }
}

/**
 * Every navigation decision in one place (M14).
 *
 * Before this existed the rules were inline lambdas in `MainActivity`, and one of them was wrong in a way
 * only a fresh install could show: the intro hands over to calibration while popping itself out of the back
 * stack, which left calibration as the **only** entry on the stack. `popBackStack()` then had nothing to
 * pop, so the in-app "Back" did nothing and the hardware key closed the app — the user had to force-stop it
 * to see the ruler again. The rules live here now so they can be tested instead of re-derived by hand.
 */
class NavActions(private val stack: RouteStack) {

    /**
     * The intro is finished: it is removed so it cannot be returned to, and the ruler is the screen.
     *
     * Two ways in, one result. On a first launch the intro is the only entry, so it is replaced by the
     * ruler. Reopened from Settings → "Show intro" the ruler is already at the bottom of the stack, and
     * pushing another one left `ruler, settings, ruler` behind the user — Back from the "finished"
     * intro walked through a second ruler and then Settings again. Returning to the existing ruler
     * drops the intro and Settings with it, the same way finishing calibration does.
     */
    fun finishIntroToRuler() {
        if (stack.popTo(AppRoute.RULER.route)) return
        stack.go(AppRoute.RULER.route, NavOptions(popUpTo = AppNav.ONBOARDING, inclusive = true, singleTop = true))
    }

    /**
     * A detail screen opened from a bar screen — the bar screen is already underneath it.
     *
     * M17 removed the intro's route to calibration. The intro teaches the ruler, and a user who has not
     * measured anything yet has no reason to be asked to make the ruler exact before they know why.
     */
    fun openDetail(route: String) {
        stack.go(route)
    }

    fun showIntro() {
        stack.go(AppNav.ONBOARDING)
    }

    /**
     * Bar navigation keeps one entry per destination and restores its state, so tapping Ruler from Tools
     * does not stack a second ruler behind it.
     */
    fun openBar(route: AppRoute) {
        stack.go(
            route.route,
            NavOptions(
                popUpTo = AppRoute.RULER.route,
                saveState = true,
                restoreState = true,
                singleTop = true
            )
        )
    }

    /**
     * "Done" at the end of the guided calibration (M16): the task is finished, so the user goes back to
     * the thing they came to fix. Popping to the ruler rather than popping one entry works whether the
     * wizard was opened from Settings or from the intro, and leaves the bar screens behind it rather
     * than a stale calibration screen the Back control would land on.
     */
    fun finishCalibration() {
        if (stack.popTo(AppRoute.RULER.route)) return
        stack.go(AppRoute.RULER.route, NavOptions(clearStack = true, singleTop = true))
    }

    /**
     * Leaving a detail screen. The pop is tried first; if there is nothing to pop — which happens when a
     * detail screen is the only entry on the stack — the ruler is opened over a cleared stack instead.
     * A Back control that silently does nothing is the bug this method exists to make impossible.
     */
    fun back() {
        if (stack.popBackStack()) return
        stack.go(AppRoute.RULER.route, NavOptions(clearStack = true, singleTop = true))
    }

    /** The route on screen now. */
    fun currentRoute(): String? = stack.currentRoute()
}
