package com.blueorbit.ruler

import com.blueorbit.ruler.core.entitlement.EntitlementStatus
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.first
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.blueorbit.ruler.core.di.AppContainer
import com.blueorbit.ruler.core.navigation.AppNav
import com.blueorbit.ruler.core.navigation.NavActions
import com.blueorbit.ruler.core.navigation.NavHostRouteStack
import com.blueorbit.ruler.core.ui.AppRoute
import com.blueorbit.ruler.core.ui.AppShell
import com.blueorbit.ruler.core.ui.BarScreen
import com.blueorbit.ruler.core.ui.ColorSafePalette
import com.blueorbit.ruler.core.ui.LocalAppMessages
import com.blueorbit.ruler.core.ui.MOTION_BAR_FADE_MS
import com.blueorbit.ruler.core.ui.MOTION_DETAIL_SLIDE_MS
import com.blueorbit.ruler.core.ui.RulerTheme
import com.blueorbit.ruler.core.ui.readoutEasing
import com.blueorbit.ruler.core.ui.reducedMotion
import com.blueorbit.ruler.feature.calibration.CalibrationWizard
import com.blueorbit.ruler.feature.extend.ExtendScreen
import com.blueorbit.ruler.feature.level.LevelScreen
import com.blueorbit.ruler.feature.level.ProtractorScreen
import com.blueorbit.ruler.feature.log.LogScreen
import com.blueorbit.ruler.feature.onboarding.OnboardingScreen
import com.blueorbit.ruler.feature.pro.PurchaseScreen
import com.blueorbit.ruler.feature.ruler.RulerScreen
import com.blueorbit.ruler.feature.ringsize.RingSizeScreen
import com.blueorbit.ruler.feature.settings.SettingsScreen
import com.blueorbit.ruler.feature.tools.ToolsScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Edge-to-edge before the first frame, with the system's own light/dark as the guess; the
        // palette the user picked replaces it as soon as the settings are read (see `App`). Without the
        // call, Android 14 and older kept an opaque status bar in the window theme's colour, and dark
        // mode drew dark icons on a dark bar.
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val container = (application as RulerApp).container
        setContent { App(container) }
    }

    /**
     * Back in the foreground: re-read purchases, so a payment that settled, a promo code redeemed in
     * the Play Store or a refund is reflected without a restart (Google's recommended place for it).
     */
    override fun onResume() {
        super.onResume()
        (application as RulerApp).container.entitlementRepository.refresh()
    }

    /**
     * The system bars follow the *palette*, not the system setting: a user on a light system theme
     * who picked the dark (or high-contrast) palette still needs light status-bar icons. The bars are
     * transparent either way — the app draws behind them and the shell pads for them.
     */
    private fun applySystemBars(dark: Boolean) {
        val style = if (dark) {
            SystemBarStyle.dark(Color.TRANSPARENT)
        } else {
            SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        }
        enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }

    @Composable
    private fun App(container: AppContainer) {
        val settings by container.settings.collectAsStateWithLifecycle()
        val settingsLoaded by container.settingsLoaded.collectAsStateWithLifecycle()
        // The palette is resolved through the entitlement gate, so a custom theme stored by a Pro
        // user is applied without a restart, and the free build always gets the checked palette.
        val palette = ColorSafePalette.resolve(
            settings = settings,
            entitlement = container.entitlementRepository,
            systemIsDark = isSystemInDarkTheme()
        )
        LaunchedEffect(palette.isDark) { applySystemBars(palette.isDark) }

        RulerTheme(palette) {
            // Nothing is composed until the stored settings have been read: the start destination
            // depends on `onboardingSeen`, and guessing it would show the intro on every launch.
            if (settingsLoaded) {
                val navController = rememberNavController()
                val backStackEntry by navController.currentBackStackEntryAsState()

                // Read once, deliberately. `NavHost` rebuilds its graph when `startDestination`
                // changes, which resets the back stack to the new start — so recomputing this from the
                // settings flow made finishing the intro flip the start destination at the same moment
                // the intro navigated, and "Calibrate now" landed on the ruler instead of the wizard
                // (seen on the test device at M11V). The value is only needed to choose the first
                // destination; "Show intro" navigates explicitly.
                val start = remember {
                    if (settings.onboardingSeen) AppRoute.RULER.route else AppNav.ONBOARDING
                }
                // Before the NavHost has produced its first entry the route is the start route, so the
                // bar does not animate in on launch.
                val currentRoute = backStackEntry?.destination?.route ?: start
                val actions = remember(navController) { NavActions(NavHostRouteStack(navController)) }
                // One message channel for the whole app (AppMessages.kt), created here so a message
                // raised on one screen outlives navigating to the next.
                val messages = remember { SnackbarHostState() }
                val transitions = rememberNavTransitions(reducedMotion())

                // Consent and the first ad load start once the purchase state is known, so a Pro user is
                // never shown a consent form for ads they will never see. Waiting is capped: a store that
                // never answers is treated as free.
                val privacyOptionsRequired by container.ads.privacyOptionsRequired.collectAsStateWithLifecycle()
                LaunchedEffect(Unit) {
                    withTimeoutOrNull(5_000) {
                        container.entitlementRepository.state.first { it.status != EntitlementStatus.CHECKING }
                    }
                    container.ads.start(this@MainActivity)
                }

                CompositionLocalProvider(LocalAppMessages provides messages) {
                    AppShell(
                        currentRoute = currentRoute,
                        showBar = currentRoute in BarRoutes,
                        messages = messages,
                        onNavigate = { target ->
                            // A tab switch is the app's one natural break, the only place the free
                            // version's interstitial may appear (AdPolicy decides whether it does).
                            if (target.route != currentRoute) container.ads.onNaturalBreak(this@MainActivity)
                            actions.openBar(target)
                        }
                    ) {
                        NavHost(
                            navController,
                            startDestination = start,
                            enterTransition = { transitions.enter(this) },
                            exitTransition = { transitions.exit(this) },
                            popEnterTransition = { transitions.popEnter(this) },
                            popExitTransition = { transitions.popExit(this) }
                        ) {
                            composable(AppNav.ONBOARDING) {
                                OnboardingScreen(
                                    onFinish = {
                                        // Saved on the application scope before the intro is popped:
                                        // a save launched in the intro's own ViewModel was cancelled
                                        // with it, and the intro came back on the next launch.
                                        container.markOnboardingSeen()
                                        actions.finishIntroToRuler()
                                    }
                                )
                            }

                            AppBarDestinations.forEach { destination ->
                                composable(destination.route) {
                                    BarScreen {
                                        when (destination) {
                                            AppRoute.RULER -> RulerScreen(
                                                factory = container.viewModelFactory,
                                                settings = container.settings,
                                                haptics = container.haptics,
                                                onOpenCalibration = { actions.openDetail(AppNav.CALIBRATION) }
                                            )
                                            AppRoute.LOG -> LogScreen(
                                                factory = container.viewModelFactory,
                                                onOpenRuler = { actions.openBar(AppRoute.RULER) }
                                            )
                                            AppRoute.TOOLS -> ToolsScreen(
                                                entitlement = container.entitlementRepository,
                                                onOpenExtend = { actions.openDetail(AppNav.EXTEND) },
                                                onOpenLevel = { actions.openDetail(AppNav.LEVEL) },
                                                onOpenProtractor = { actions.openDetail(AppNav.PROTRACTOR) },
                                                onOpenRingSize = { actions.openDetail(AppNav.RINGSIZE) }
                                            )
                                            AppRoute.SETTINGS -> SettingsScreen(
                                                factory = container.viewModelFactory,
                                                privacyOptionsRequired = privacyOptionsRequired,
                                                onPrivacyChoices = { container.ads.showPrivacyOptions(this@MainActivity) },
                                                onOpenCalibration = { actions.openDetail(AppNav.CALIBRATION) },
                                                onOpenPro = { actions.openDetail(AppNav.PRO) },
                                                onShowIntro = { actions.showIntro() }
                                            )
                                        }
                                    }
                                }
                            }

                            // Detail screens bring their own Back control (DESIGN.md §6), so they have no bar.
                            composable(AppNav.CALIBRATION) {
                                CalibrationWizard(
                                    factory = container.viewModelFactory,
                                    settings = container.settings,
                                    haptics = container.haptics,
                                    onBack = { actions.back() },
                                    onDone = { actions.finishCalibration() }
                                )
                            }
                            composable(AppNav.PRO) {
                                PurchaseScreen(
                                    factory = container.viewModelFactory,
                                    onBack = { actions.back() }
                                )
                            }
                            composable(AppNav.EXTEND) {
                                ExtendScreen(
                                    factory = container.viewModelFactory,
                                    settings = container.settings,
                                    haptics = container.haptics,
                                    onBack = { actions.back() },
                                    onOpenPro = { actions.openDetail(AppNav.PRO) }
                                )
                            }
                            composable(AppNav.LEVEL) {
                                LevelScreen(
                                    factory = container.viewModelFactory,
                                    settings = container.settings,
                                    haptics = container.haptics,
                                    onBack = { actions.back() },
                                    onOpenPro = { actions.openDetail(AppNav.PRO) }
                                )
                            }
                            composable(AppNav.PROTRACTOR) {
                                ProtractorScreen(
                                    factory = container.viewModelFactory,
                                    settings = container.settings,
                                    haptics = container.haptics,
                                    onBack = { actions.back() },
                                    onOpenPro = { actions.openDetail(AppNav.PRO) }
                                )
                            }
                            // A free tool, so no Pro gate and no Pro chip (roadmap rule 9). It is a detail
                            // screen, so it never shows the ad (roadmap rule 10).
                            composable(AppNav.RINGSIZE) {
                                RingSizeScreen(
                                    factory = container.viewModelFactory,
                                    settings = container.settings,
                                    haptics = container.haptics,
                                    onBack = { actions.back() },
                                    onOpenCalibration = { actions.openDetail(AppNav.CALIBRATION) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Screen-change motion (Motion.kt budget). Between two bar destinations the content cross-fades in
     * [MOTION_BAR_FADE_MS] — the bar around it is the shell's and does not move. A detail screen slides
     * in a short way from the end side and back out the way it came in [MOTION_DETAIL_SLIDE_MS], so
     * Back visibly undoes the open. `slideIntoContainer` takes Start/End, which mirrors under RTL.
     * With reduced motion every change is a cut.
     */
    private class NavTransitions(private val reduced: Boolean) {
        private fun isBar(entry: NavBackStackEntry) = entry.destination.route in BarRoutes

        /** True when neither side of the change is a detail screen, so it is a tab switch. */
        private fun AnimatedContentTransitionScope<NavBackStackEntry>.betweenBars() =
            isBar(initialState) && isBar(targetState)

        private val fadeIn get() = fadeIn(tween(MOTION_BAR_FADE_MS, easing = readoutEasing))
        private val fadeOut get() = fadeOut(tween(MOTION_BAR_FADE_MS, easing = readoutEasing))
        private val slideSpec get() = tween<androidx.compose.ui.unit.IntOffset>(MOTION_DETAIL_SLIDE_MS, easing = readoutEasing)
        private val slideFadeIn get() = fadeIn(tween(MOTION_DETAIL_SLIDE_MS, easing = readoutEasing))
        private val slideFadeOut get() = fadeOut(tween(MOTION_DETAIL_SLIDE_MS, easing = readoutEasing))

        /** A short travel — a sixth of the width — reads as "opened from here" without sweeping the screen. */
        private val travel: (Int) -> Int = { it / 6 }

        fun enter(scope: AnimatedContentTransitionScope<NavBackStackEntry>): EnterTransition = with(scope) {
            when {
                reduced -> EnterTransition.None
                betweenBars() -> fadeIn
                else -> slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, slideSpec, travel) + slideFadeIn
            }
        }

        fun exit(scope: AnimatedContentTransitionScope<NavBackStackEntry>): ExitTransition = with(scope) {
            when {
                reduced -> ExitTransition.None
                betweenBars() -> fadeOut
                else -> slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Start, slideSpec, travel) + slideFadeOut
            }
        }

        fun popEnter(scope: AnimatedContentTransitionScope<NavBackStackEntry>): EnterTransition = with(scope) {
            when {
                reduced -> EnterTransition.None
                betweenBars() -> fadeIn
                else -> slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.End, slideSpec, travel) + slideFadeIn
            }
        }

        fun popExit(scope: AnimatedContentTransitionScope<NavBackStackEntry>): ExitTransition = with(scope) {
            when {
                reduced -> ExitTransition.None
                betweenBars() -> fadeOut
                else -> slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, slideSpec, travel) + slideFadeOut
            }
        }
    }

    @Composable
    private fun rememberNavTransitions(reduced: Boolean) = remember(reduced) { NavTransitions(reduced) }

    private companion object {
        /** The destinations the bar owns; the detail screens are reached from them. */
        val AppBarDestinations = listOf(AppRoute.RULER, AppRoute.LOG, AppRoute.TOOLS, AppRoute.SETTINGS)

        val BarRoutes: Set<String> = AppBarDestinations.map { it.route }.toSet()
    }
}
