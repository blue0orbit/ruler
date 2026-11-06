package com.blueorbit.ruler.core.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.ripple
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blueorbit.ruler.R

/**
 * The four destinations and their routes (DESIGN.md §6). The bar drives these four; `tools` leads to
 * the Pro tools, so the ruler screen no longer carries five navigation buttons.
 */
enum class AppRoute(val route: String, val labelRes: Int, val icon: ImageVector) {
    RULER("ruler", R.string.nav_ruler, RulerIcons.Ruler),
    LOG("log", R.string.nav_log, Icons.AutoMirrored.Filled.List),
    TOOLS("tools", R.string.nav_tools, Icons.Filled.Build),
    SETTINGS("settings", R.string.nav_settings, Icons.Filled.Settings)
}

/**
 * The app shell (DESIGN.md §6, M19 amendment): the wordmark row, the bottom bar or rail, the one
 * snackbar host, and the inset rule — composed **once, around** the NavHost rather than inside each
 * bar destination. Inside, every tab switch faded the bar out and back in with the screen, so the one
 * part of the app that does not change was the part that moved. Out here the chrome stays still and
 * only [content] changes.
 *
 * Insets are applied here and nowhere else, so reviewer note 1 is structural: a new screen cannot
 * forget them because it does not own them. The shell pads the top and horizontal safe-drawing
 * insets and reserves the navigation-bar inset at the bottom, and it *consumes* both, so a detail
 * screen's own `safeDrawing` padding (DetailScaffold, onboarding's `screenFrame`) only adds what is
 * left — the keyboard, for instance — and never pads twice. In portrait the horizontal inset is zero,
 * so the ruler plate's zero still sits on the physical screen edge.
 *
 * [showBar] is false on the intro and the detail screens: they bring their own header and Back
 * control. The chrome then shrinks away over the same ~220 ms the detail screen slides in with
 * (Motion.kt), or disappears on the spot with reduced motion.
 *
 * The bottom bar is a flat strip: a hairline above it, `labelCaps` labels, and a hairline rule under
 * the selected item instead of a filled pill (SPEC 11: shape + label weight + announced state carry
 * the selection, brass ink only accompanies them).
 */
@Composable
fun AppShell(
    currentRoute: String?,
    showBar: Boolean,
    messages: SnackbarHostState,
    onNavigate: (AppRoute) -> Unit,
    content: @Composable () -> Unit
) {
    val colors = LocalRulerColors.current
    val widthDp = LocalConfiguration.current.screenWidthDp
    val rail = useNavigationRail(widthDp)
    val margin = screenMarginDp(widthDp).dp
    val hairlineColor = colors.outlineVariant.toCompose()
    val reduced = reducedMotion()
    val chromeMillis = motionMillis(MOTION_DETAIL_SLIDE_MS, reduced)
    val enterV = if (reduced) EnterTransition.None else expandVertically(tween(chromeMillis)) + fadeIn(tween(chromeMillis))
    val exitV = if (reduced) ExitTransition.None else shrinkVertically(tween(chromeMillis)) + fadeOut(tween(chromeMillis))

    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top))
    ) {
        AnimatedVisibility(visible = showBar, enter = enterV, exit = exitV) {
            WordmarkRow(Modifier.fillMaxWidth().padding(horizontal = margin))
        }

        Row(Modifier.weight(1f)) {
            AnimatedVisibility(
                visible = showBar && rail,
                enter = if (reduced) EnterTransition.None else expandHorizontally(tween(chromeMillis)) + fadeIn(tween(chromeMillis)),
                exit = if (reduced) ExitTransition.None else shrinkHorizontally(tween(chromeMillis)) + fadeOut(tween(chromeMillis))
            ) {
                NavigationRail(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(RulerDimensions.navRailWidth),
                    containerColor = colors.surface.toCompose(),
                    // The shell has already applied every inset around the rail.
                    // No header glyph: the wordmark above already carries the ruler glyph, and a second one
                    // directly over the Ruler item read as a duplicate destination.
                    windowInsets = WindowInsets(0)
                ) {
                    AppRoute.entries.forEach { destination ->
                        val selected = currentRoute == destination.route
                        NavigationRailItem(
                            selected = selected,
                            onClick = { onNavigate(destination) },
                            // The M19 indicator: a flat 3 dp rule down the rail edge of the selected
                            // item, drawn behind it (the stock indicator is set transparent). Shape,
                            // label weight and the announced state say "selected"; the brass ink alone
                            // never has to (SPEC 11).
                            modifier = if (selected) {
                                Modifier.drawBehind {
                                    drawRect(
                                        color = colors.accent.toCompose(),
                                        topLeft = Offset.Zero,
                                        size = Size(3.dp.toPx(), size.height)
                                    )
                                }
                            } else Modifier,
                            // No contentDescription: the label Text is the accessible name, and M3 nav
                            // items announce the selected state, so the selection is never colour-only.
                            icon = { Icon(destination.icon, contentDescription = null) },
                            label = { NavLabel(destination.labelRes, selected) },
                            alwaysShowLabel = true,
                            colors = NavigationRailItemDefaults.colors(
                                selectedIconColor = colors.accent.toCompose(),
                                selectedTextColor = colors.accent.toCompose(),
                                unselectedIconColor = colors.onSurfaceSecondary.toCompose(),
                                unselectedTextColor = colors.onSurfaceSecondary.toCompose(),
                                indicatorColor = Color.Transparent
                            )
                        )
                    }
                }
            }

            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    // The strip at the foot of the shell reserves this inset for every screen, so
                    // nothing inside may pad for it a second time.
                    .consumeWindowInsets(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))
            ) {
                content()
                // One host for the whole app (AppMessages.kt): just above the bar on a bar screen, at
                // the foot of the screen on a detail screen, and never drawn twice.
                SnackbarHost(
                    hostState = messages,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = margin, vertical = RulerSpacing.sm)
                ) { data -> RulerSnackbar(data) }
            }
        }

        AnimatedVisibility(visible = showBar && !rail, enter = enterV, exit = exitV) {
            Column(Modifier.fillMaxWidth()) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(RulerDimensions.hairline)
                        .background(hairlineColor)
                )
                NavigationBar(
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = RulerDimensions.navBarHeight),
                    containerColor = colors.surface.toCompose(),
                    // The bottom inset is the strip below, which stays put while the bar slides away;
                    // padding it here too would lift the labels twice off the gesture area.
                    windowInsets = WindowInsets(0)
                ) {
                    AppRoute.entries.forEach { destination ->
                        val selected = currentRoute == destination.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = { onNavigate(destination) },
                            // The M19 indicator: a flat 3 dp rule along the bottom of the selected item
                            // (the rail gets the same rule on its left edge above).
                            modifier = if (selected) {
                                Modifier.drawBehind {
                                    drawRect(
                                        color = colors.accent.toCompose(),
                                        topLeft = Offset(0f, size.height - 3.dp.toPx()),
                                        size = Size(size.width, 3.dp.toPx())
                                    )
                                }
                            } else Modifier,
                            icon = { Icon(destination.icon, contentDescription = null) },
                            label = { NavLabel(destination.labelRes, selected) },
                            alwaysShowLabel = true,
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = colors.accent.toCompose(),
                                selectedTextColor = colors.accent.toCompose(),
                                unselectedIconColor = colors.onSurfaceSecondary.toCompose(),
                                unselectedTextColor = colors.onSurfaceSecondary.toCompose(),
                                indicatorColor = Color.Transparent
                            )
                        )
                    }
                }
            }
        }

        // The gesture / button area under everything. It takes the bar's fill while the bar is shown,
        // so the bar reads as running to the screen edge; otherwise the screen's own background.
        Box(
            Modifier
                .fillMaxWidth()
                .windowInsetsBottomHeight(WindowInsets.navigationBars)
                .background(if (showBar && !rail) colors.surface.toCompose() else Color.Transparent)
        )
    }
}

/**
 * The content region of a bar destination: the screen margin all round, inside the shell's insets.
 * Every bar screen is composed in this and nothing else, so the margin is the same on every tab.
 */
@Composable
fun BarScreen(content: @Composable () -> Unit) {
    val margin = screenMarginDp(LocalConfiguration.current.screenWidthDp).dp
    Column(Modifier.fillMaxSize().padding(margin)) {
        content()
    }
}

/**
 * The app's snackbar in the M19 panel language: a flat 0 dp panel with a hairline in the checked
 * `outline` ink and no shadow (DESIGN.md §4). The stock Material snackbar is a rounded, shadowed
 * inverse surface — the one panel in the app that would not look machined. The host still owns the
 * timing and the accessibility announcement; this only draws the message and its action.
 */
@Composable
private fun RulerSnackbar(data: SnackbarData) {
    val colors = LocalRulerColors.current
    val type = LocalRulerType.current
    Row(
        Modifier
            .fillMaxWidth()
            .background(colors.surfaceElevated.toCompose(), RulerShapes.sheet)
            .border(RulerDimensions.hairline, colors.outline.toCompose(), RulerShapes.sheet)
            .defaultMinSize(minHeight = 48.dp)
            .padding(start = RulerSpacing.md, end = RulerSpacing.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            data.visuals.message,
            style = type.bodyMedium,
            color = colors.onSurface.toCompose(),
            modifier = Modifier
                .weight(1f)
                .padding(vertical = RulerSpacing.sm)
        )
        val action = data.visuals.actionLabel
        if (action != null) {
            TextButton(onClick = { data.performAction() }) {
                Text(action.capsRegister(), style = type.labelCaps, color = colors.accent.toCompose())
            }
        }
    }
}

/** The instrument's identity row (DESIGN.md §6 M19): glyph + wordmark, hairline under. */
@Composable
fun WordmarkRow(modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
    val colors = LocalRulerColors.current
    val type = LocalRulerType.current
    Column(modifier) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = RulerSpacing.sm, bottom = RulerSpacing.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                RulerIcons.Ruler,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = colors.onSurface.toCompose()
            )
            Text(
                stringResource(R.string.app_name).capsRegister(),
                style = type.barTitle,
                color = colors.onSurface.toCompose(),
                modifier = Modifier.padding(start = RulerSpacing.sm)
            )
            if (trailing != null) {
                Spacer(Modifier.weight(1f))
                trailing()
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(RulerDimensions.hairline)
                .background(colors.outlineVariant.toCompose())
        )
    }
}

/**
 * Nav labels take the `labelCaps` register — 11 sp, Medium when selected and Normal otherwise so the
 * weight says "selected" beside the announced state (SPEC 11) — but they are the one place the caps
 * register is not applied (DESIGN.md §3 M19): "EINSTELLUNGEN" in caps is ~103 dp wide and a four-item
 * bar on a phone gives its label about 79 dp, so the word would be cut. Sentence case is the fit
 * exception, not a style exception.
 */
@Composable
private fun NavLabel(labelRes: Int, selected: Boolean) {
    val type = LocalRulerType.current
    // One line, always. At large font scales German labels ("Einstellungen", "Werkzeuge") broke
    // mid-word onto a second line; an ellipsis is the standard nav-bar behaviour and the icon and
    // the accessibility label still carry the full name.
    Text(
        stringResource(labelRes),
        style = if (selected) type.labelCaps else type.labelCaps.copy(fontWeight = FontWeight.Normal),
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis
    )
}

/**
 * The screen title row every bar screen starts under the wordmark (DESIGN.md §6 M19): the screen's
 * own name in `barTitle`, and on the right only *real* data — on the ruler screen that is the
 * provenance chip, which reuses the `source_*` strings verbatim (rule 5 wording unchanged).
 */
@Composable
fun ScreenTitleRow(title: String, trailing: (@Composable () -> Unit)? = null, modifier: Modifier = Modifier) {
    val colors = LocalRulerColors.current
    val type = LocalRulerType.current
    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title.capsRegister(), style = type.barTitle, color = colors.onSurface.toCompose())
        if (trailing != null) {
            Spacer(Modifier.weight(1f))
            trailing()
        }
    }
}

/** A hairline-bordered flat chip (DESIGN.md §4 M19: 0 dp, outline, word — never colour alone, SPEC 11). */
@Composable
fun OutlinedChip(text: String, emphasized: Boolean = false, modifier: Modifier = Modifier) {
    val colors = LocalRulerColors.current
    val type = LocalRulerType.current
    val ink = if (emphasized) colors.accent.toCompose() else colors.onSurfaceSecondary.toCompose()
    val edge = if (emphasized) colors.accent.toCompose() else colors.outline.toCompose()
    Box(
        modifier
            .clip(RulerShapes.chip)
            .border(RulerDimensions.hairline, edge, RulerShapes.chip)
            .padding(horizontal = RulerSpacing.sm, vertical = RulerSpacing.xs)
    ) {
        Text(text.capsRegister(), style = type.labelMicro, color = ink)
    }
}

/**
 * Detail screens reached from Tools or Settings (DESIGN.md §6, M19): no bottom bar; a Back control,
 * the title in `barTitle` centred, an optional trailing chip (the Pro tools show a `Pro` chip when
 * unlocked — a word, not colour, SPEC 11), all above a hairline, with the same insets as the bar
 * screens.
 */
@Composable
fun DetailScaffold(
    onBack: () -> Unit,
    title: String,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = LocalRulerColors.current
    val type = LocalRulerType.current
    val margin = screenMarginDp(LocalConfiguration.current.screenWidthDp).dp
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    // What actually squeezes the title is the Back control's own width, and that is a different width in
    // every language and at every font scale, so the bar measures itself instead of guessing.
    var barWidth by remember { mutableStateOf(0) }
    var backWidth by remember { mutableStateOf(0) }

    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = margin, vertical = RulerSpacing.xs)
                .onGloballyPositioned { coords -> barWidth = coords.size.width },
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = onBack,
                modifier = Modifier.onGloballyPositioned { coords -> backWidth = coords.size.width }
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    stringResource(R.string.action_back).capsRegister(),
                    style = type.labelCaps,
                    modifier = Modifier.padding(start = RulerSpacing.xs)
                )
            }
            // Caps by role (M19) — but the screen's own name comes first. At 200 % font the caps title
            // broke in the middle of a word on the Fold at M21 ("RINGGRÖSS / E"), so the bar gives up the
            // caps and the tracking before it gives up the word: the same order the nav labels already
            // follow (DECISIONS M19: "EINSTELLUNGEN in caps does not fit a four-item bar").
            val titleSpace = barWidth - backWidth -
                with(density) { RulerDimensions.backControlWidth.toPx() }.toInt()
            val capsFit = titleSpace <= 0 ||
                textMeasurer.measure(title.capsRegister(), type.barTitle, maxLines = 1).size.width <= titleSpace
            Text(
                if (capsFit) title.capsRegister() else title,
                style = if (capsFit) type.barTitle else type.barTitle.copy(letterSpacing = 0.sp),
                color = colors.onSurface.toCompose(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f, fill = true)
            )
            if (trailing != null) trailing() else Spacer(Modifier.width(RulerDimensions.backControlWidth))
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(RulerDimensions.hairline)
                .background(colors.outlineVariant.toCompose())
        )
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(margin),
            content = content
        )
    }
}

/**
 * The same inset and margin rule as a modifier, for a screen that has no bar and no Back control —
 * onboarding, which is closed by its own buttons (DESIGN.md §8). Insets stay in the scaffold layer for
 * every screen, including this one; a screen that applied them by hand is how the first draft of
 * onboarding ended up with its title under the punch hole.
 */
@Composable
fun Modifier.screenFrame(): Modifier = this
    .windowInsetsPadding(WindowInsets.safeDrawing)
    .padding(screenMarginDp(LocalConfiguration.current.screenWidthDp).dp)

/** Panels are a fill plus a hairline outline, never a shadow (DESIGN.md §4). */
@Composable
fun RulerCard(
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = LocalRulerColors.current
    val fill = if (selected) colors.surfaceVariant.toCompose() else colors.surface.toCompose()
    // A selected panel has to be recognisable at a glance, or the editor under the list does not say
    // which row it belongs to. Fill alone is not: on the dark theme surface and surfaceVariant look
    // identical side by side (M18 walkthrough, shots/M18d-04). The resting edge is the M19 panel
    // hairline (`outlineVariant`); the selected edge stays the 2 dp accent rule.
    // Press feedback on a tappable panel: the ripple fills it, and the hairline steps up to the checked
    // `outline` ink while the finger is down, so the press reads on the edge as well as in the fill
    // (the brass ripple alone is faint on the sepia and high-contrast palettes).
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val edge = when {
        selected -> colors.accent.toCompose()
        pressed && onClick != null -> colors.outline.toCompose()
        else -> colors.outlineVariant.toCompose()
    }
    val edgeWidth = if (selected) RulerDimensions.hairline * 2 else RulerDimensions.hairline

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RulerShapes.card)
            .background(fill, RulerShapes.card)
            .border(edgeWidth, edge, RulerShapes.card)
            .then(
                if (onClick != null) {
                    Modifier.clickable(interactionSource = interaction, indication = ripple(), onClick = onClick)
                } else {
                    Modifier
                }
            )
            .padding(RulerSpacing.md),
        content = content
    )
}

/** Section headers (DESIGN.md §3 M19): a hairline rule then the label in `labelCaps`. */
@Composable
fun SectionHeader(label: String, rule: Boolean = true, modifier: Modifier = Modifier) {
    val colors = LocalRulerColors.current
    val type = LocalRulerType.current
    Column(modifier.padding(top = RulerSpacing.sm)) {
        if (rule) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(RulerDimensions.hairline)
                    .background(colors.outlineVariant.toCompose())
            )
        }
        Text(
            label.capsRegister(),
            style = type.labelCaps,
            color = colors.onSurface.toCompose(),
            modifier = Modifier.padding(top = if (rule) RulerSpacing.xs else 0.dp)
        )
    }
}

/** Empty states (DESIGN.md §9): body copy in secondary ink plus the control that ends the state. */
@Composable
fun EmptyState(body: String, action: @Composable (() -> Unit)? = null) {
    val colors = LocalRulerColors.current
    Column(verticalArrangement = Arrangement.spacedBy(RulerSpacing.xs)) {
        Text(body, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceSecondary.toCompose())
        if (action != null) action()
    }
}
