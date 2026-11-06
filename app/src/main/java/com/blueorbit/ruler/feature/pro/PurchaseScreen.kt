package com.blueorbit.ruler.feature.pro

import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.blueorbit.ruler.R
import com.blueorbit.ruler.core.di.ViewModelFactory
import com.blueorbit.ruler.core.entitlement.EntitlementStatus
import com.blueorbit.ruler.core.ui.DetailScaffold
import com.blueorbit.ruler.core.ui.LocalAppMessages
import com.blueorbit.ruler.core.ui.LocalRulerColors
import com.blueorbit.ruler.core.ui.LocalRulerType
import com.blueorbit.ruler.core.ui.MOTION_DETAIL_SLIDE_MS
import com.blueorbit.ruler.core.ui.MOTION_SWAP_MS
import com.blueorbit.ruler.core.ui.OutlinedChip
import com.blueorbit.ruler.core.ui.RulerSpacing
import com.blueorbit.ruler.core.ui.SectionHeader
import com.blueorbit.ruler.core.ui.motionMillis
import com.blueorbit.ruler.core.ui.reducedMotion
import com.blueorbit.ruler.core.ui.say
import com.blueorbit.ruler.core.ui.semantics.announceChanges
import com.blueorbit.ruler.core.ui.toCompose
import kotlinx.coroutines.launch

/**
 * Pro (DESIGN.md §13): the gate is legible as a two-column Free | Pro table built from the same
 * strings the rest of the app uses, so the table cannot disagree with what the screens actually do.
 * Rule 10: everything in the left column keeps working, and the wording names both halves.
 *
 * The page scrolls: on a landscape phone the table, the price and both buttons are taller than the
 * viewport, and the first build clipped "Check again" off the bottom with no way to reach it (rule 8,
 * rule 14).
 */
@Composable
fun PurchaseScreen(factory: ViewModelFactory, onBack: () -> Unit) {
    val vm: PurchaseViewModel = viewModel(factory = factory)
    val state by vm.state.collectAsStateWithLifecycle()
    val problem by vm.problem.collectAsStateWithLifecycle()
    val activity = LocalActivity.current
    val type = LocalRulerType.current
    val colors = LocalRulerColors.current
    val messages = LocalAppMessages.current
    val scope = rememberCoroutineScope()
    val reduced = reducedMotion()

    LaunchedEffect(Unit) { vm.refresh() }

    val statusText = stringResource(statusRes(state.status))
    val locked = state.status == EntitlementStatus.LOCKED

    DetailScaffold(
        onBack = onBack,
        title = stringResource(R.string.pro_title),
        // M19 (DESIGN.md §6): the header says which half of the gate the user is in — a word, not a
        // colour (SPEC 11). While locked the table below and the purchase button say the same thing.
        trailing = if (state.status == EntitlementStatus.UNLOCKED) {
            ({ OutlinedChip(stringResource(R.string.settings_pro_title), emphasized = true) })
        } else {
            null
        }
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(RulerSpacing.sm)
        ) {
            // The status changes when a purchase completes, so it is a polite live region: TalkBack reads
            // the new status without interrupting the user on every reconnect. The change itself is a
            // short cross-fade, so "Not unlocked" turning into "Unlocked" is seen to happen rather than
            // being a word that is suddenly different (Motion.kt: motion marks a state change).
            AnimatedContent(
                targetState = statusText,
                transitionSpec = {
                    fadeIn(tween(motionMillis(MOTION_SWAP_MS, reduced))) togetherWith
                        fadeOut(tween(motionMillis(MOTION_SWAP_MS, reduced)))
                },
                label = "proStatus"
            ) { text ->
                Text(
                    text = text,
                    style = type.readoutSecondary,
                    color = colors.onSurface.toCompose(),
                    modifier = Modifier.announceChanges(stringResource(R.string.pro_live_format, text))
                )
            }

            if (state.priceLabel.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.pro_price_format, state.priceLabel),
                    style = type.numberLarge,
                    color = colors.onSurface.toCompose()
                )
            } else if (locked) {
                Text(
                    stringResource(R.string.pro_price_unknown),
                    style = type.bodyMedium,
                    color = colors.onSurfaceSecondary.toCompose()
                )
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(RulerSpacing.xl)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(RulerSpacing.xs)) {
                    SectionHeader(stringResource(R.string.pro_column_free))
                    listOf(
                        R.string.nav_ruler,
                        R.string.settings_units,
                        R.string.calibration_title,
                        R.string.nav_log,
                        R.string.log_export
                    ).forEach { res ->
                        Text(
                            stringResource(res),
                            style = type.bodyMedium,
                            color = colors.onSurface.toCompose()
                        )
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(RulerSpacing.xs)) {
                    SectionHeader(stringResource(R.string.settings_pro_title))
                    // The protractor is its own tool on the Tools screen and is named in `pro_features`
                    // below, so it has its own row here too: the table must not list less than the
                    // sentence under it.
                    listOf(
                        R.string.nav_extend,
                        R.string.level_title,
                        R.string.protractor_title,
                        R.string.settings_custom_theme,
                        R.string.log_tags
                    ).forEach { res ->
                        Text(
                            stringResource(res),
                            style = type.bodyMedium,
                            color = colors.onSurface.toCompose()
                        )
                    }
                }
            }

            // Kept as prose too: the table lists the features, this sentence states the rule.
            Text(stringResource(R.string.pro_features), style = type.bodyMedium)

            // The purchase controls leave with a short collapse when the status turns to Unlocked, so
            // the page visibly settles into its unlocked form instead of jumping.
            AnimatedVisibility(
                visible = locked,
                enter = if (reduced) EnterTransition.None else expandVertically(tween(MOTION_DETAIL_SLIDE_MS)) + fadeIn(tween(MOTION_DETAIL_SLIDE_MS)),
                exit = if (reduced) ExitTransition.None else shrinkVertically(tween(MOTION_DETAIL_SLIDE_MS)) + fadeOut(tween(MOTION_DETAIL_SLIDE_MS))
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(RulerSpacing.xs)) {
                    Button(
                        onClick = { if (activity != null) vm.purchase(activity) },
                        enabled = activity != null
                    ) {
                        Text(stringResource(R.string.pro_purchase))
                    }
                    val problemRes = problem?.let(::problemRes)
                    if (problemRes != null) {
                        // Said in words, under the button that failed, and announced: a purchase tap
                        // that silently did nothing is the bug this line exists for.
                        val problemText = stringResource(problemRes)
                        Text(
                            problemText,
                            style = type.bodyMedium,
                            color = colors.onSurfaceSecondary.toCompose(),
                            modifier = Modifier.announceChanges(problemText)
                        )
                    }
                }
            }

            // Resolved here, in composition, so a language change is picked up (a click handler that
            // read the strings through the Context would keep the old configuration's text).
            val checkMessages = CheckResult.entries.associateWith { stringResource(checkRes(it)) }
            OutlinedButton(
                onClick = {
                    val message = checkMessages.getValue(vm.checkAgain())
                    scope.launch { messages.say(message) }
                }
            ) {
                Text(stringResource(R.string.pro_check_again))
            }

            Text(
                stringResource(R.string.pro_play_note),
                style = type.meta,
                color = colors.onSurfaceSecondary.toCompose()
            )
        }
    }
}

private fun statusRes(status: EntitlementStatus): Int = when (status) {
    EntitlementStatus.CHECKING -> R.string.pro_status_checking
    EntitlementStatus.UNLOCKED -> R.string.pro_status_unlocked
    EntitlementStatus.LOCKED -> R.string.pro_status_locked
    EntitlementStatus.PENDING -> R.string.pro_status_pending
    EntitlementStatus.UNAVAILABLE -> R.string.pro_status_unavailable
}

/**
 * "This build cannot open the Play purchase sheet" is kept for the one case it is true — the debug
 * build's fake, which never talks to Play. A release build that cannot reach Play says that instead.
 */
private fun problemRes(problem: PurchaseProblem): Int = when (problem) {
    PurchaseProblem.BUILD_HAS_NO_PLAY -> R.string.pro_purchase_unsupported
    PurchaseProblem.PLAY_UNREACHABLE -> R.string.pro_problem_play_unreachable
    PurchaseProblem.OFFER_NOT_LOADED -> R.string.pro_problem_offer_not_loaded
}

private fun checkRes(result: CheckResult): Int = when (result) {
    CheckResult.ASKED_PLAY -> R.string.pro_check_asked
    CheckResult.BUILD_HAS_NO_PLAY -> R.string.pro_check_no_play
    CheckResult.PLAY_UNREACHABLE -> R.string.pro_problem_play_unreachable
}
