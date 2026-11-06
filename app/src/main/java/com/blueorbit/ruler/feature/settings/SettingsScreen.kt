package com.blueorbit.ruler.feature.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.blueorbit.ruler.BuildConfig
import com.blueorbit.ruler.R
import com.blueorbit.ruler.core.calibration.CalibrationSource
import com.blueorbit.ruler.core.di.ViewModelFactory
import com.blueorbit.ruler.core.geometry.MeasurementUnit
import com.blueorbit.ruler.core.settings.ThemeChoice
import com.blueorbit.ruler.core.ui.LocalAppMessages
import com.blueorbit.ruler.core.ui.LocalRulerColors
import com.blueorbit.ruler.core.ui.LocalRulerType
import com.blueorbit.ruler.core.ui.RulerShapes
import com.blueorbit.ruler.core.ui.RulerSpacing
import com.blueorbit.ruler.core.ui.ScreenTitleRow
import com.blueorbit.ruler.core.ui.SectionHeader
import com.blueorbit.ruler.core.ui.readoutEasing
import com.blueorbit.ruler.core.ui.reducedMotion
import com.blueorbit.ruler.core.ui.say
import com.blueorbit.ruler.core.ui.semantics.labelled
import com.blueorbit.ruler.core.ui.toCompose
import com.blueorbit.ruler.core.ui.unitLabelRes
import com.blueorbit.ruler.core.ui.useNavigationRail
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

/**
 * Settings (DESIGN.md §7). This is where the controls that used to sit on the ruler screen live:
 * label size, the start-line inset, the px/mm value and the error budget.
 *
 * The known M9 gap is closed here: Units and Theme are segmented, so the active choice is visible
 * (fill plus a check icon) and announced, not inferred from which button happened to be filled
 * (SPEC 11).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    factory: ViewModelFactory,
    onOpenCalibration: () -> Unit,
    onOpenPro: () -> Unit,
    onShowIntro: () -> Unit,
    /** Shown only where the law requires a way to change ad consent (EEA, UK, some US states). */
    privacyOptionsRequired: Boolean = false,
    onPrivacyChoices: () -> Unit = {}
) {
    val vm: SettingsViewModel = viewModel(factory = factory)
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.refresh() }

    val widthDp = LocalConfiguration.current.screenWidthDp
    val twoColumns = useNavigationRail(widthDp)

    val reduced = reducedMotion()

    val render: @Composable (SettingsGroup) -> Unit = { group ->
        when (group) {
            SettingsGroup.MODE -> ModeGroup(vm, state)
            SettingsGroup.UNITS -> UnitsGroup(vm, state)
            SettingsGroup.ACCURACY -> CalibrationGroup(vm, state, onOpenCalibration)
            SettingsGroup.THEME -> ThemeGroup(vm, state)
            SettingsGroup.FEEDBACK -> FeedbackGroup(vm, state)
            SettingsGroup.LABEL_SIZE -> LabelSizeGroup(vm, state)
            SettingsGroup.ACCURACY_DETAILS -> AccuracyDetailsGroup(vm, state)
            SettingsGroup.PRO -> ProGroup(state, onOpenPro)
            SettingsGroup.ABOUT -> AboutGroup(vm, onShowIntro, privacyOptionsRequired, onPrivacyChoices)
        }
    }

    // Rule 12: which groups appear is one decision, stated in [settingsGroupsFor] so a unit test can
    // check the gate without a device.
    val shown = settingsGroupsFor(state.settings.simpleMode)
    val groups: List<@Composable () -> Unit> = shown.map { group -> { render(group) } }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(RulerSpacing.md)
    ) {
        ScreenTitleRow(stringResource(R.string.settings_title))

        if (twoColumns) {
            // DESIGN.md §6: Settings is a long list, so at the rail width it becomes two columns
            // rather than one very long one. One scroll container for both, so the page scrolls as a
            // page. The split is by half because the group count changes with the mode.
            val half = (groups.size + 1) / 2
            Row(horizontalArrangement = Arrangement.spacedBy(RulerSpacing.xl)) {
                Box(Modifier.weight(1f)) {
                    Column(verticalArrangement = Arrangement.spacedBy(RulerSpacing.md)) {
                        groups.take(half).forEach { group -> group() }
                    }
                }
                Box(Modifier.weight(1f)) {
                    Column(verticalArrangement = Arrangement.spacedBy(RulerSpacing.md)) {
                        groups.drop(half).forEach { group -> group() }
                    }
                }
            }
        } else {
            // Every group that exists, in page order, each one shown or hidden by the gate. Flipping
            // "Precise tools" used to make two groups appear in one frame and push the rest of the page
            // down under the user's thumb; growing them open says where the new controls came from and
            // that the switch did what it said (rule 12).
            settingsGroupsFor(simpleMode = false).forEach { group ->
                AnimatedVisibility(
                    visible = group in shown,
                    enter = sectionEnter(reduced),
                    exit = sectionExit(reduced)
                ) {
                    render(group)
                }
            }
        }
    }
}

/**
 * Short and purposeful (DESIGN.md §10): a section grows from where it will sit. With the system's
 * animations turned off it simply appears, as everything else in the app does.
 */
private fun sectionEnter(reduced: Boolean): EnterTransition =
    if (reduced) {
        EnterTransition.None
    } else {
        expandVertically(tween(SECTION_MILLIS, easing = readoutEasing)) + fadeIn(tween(SECTION_MILLIS))
    }

private fun sectionExit(reduced: Boolean): ExitTransition =
    if (reduced) {
        ExitTransition.None
    } else {
        shrinkVertically(tween(SECTION_MILLIS, easing = readoutEasing)) + fadeOut(tween(SECTION_MILLIS))
    }

private const val SECTION_MILLIS = 200

/** The Settings groups, in the order they appear on the page (DESIGN.md §6). */
internal enum class SettingsGroup { MODE, UNITS, ACCURACY, THEME, FEEDBACK, LABEL_SIZE, ACCURACY_DETAILS, PRO, ABOUT }

/**
 * The gate itself (rule 12). Simple mode hides the technical controls and nothing else: the reading,
 * the stored calibration and the maths are identical for every entry in either list, and the accuracy
 * of a reading is stated in both modes — only the words differ (rule 13).
 */
internal fun settingsGroupsFor(simpleMode: Boolean): List<SettingsGroup> = buildList {
    add(SettingsGroup.MODE)
    add(SettingsGroup.UNITS)
    add(SettingsGroup.ACCURACY)
    // The technical groups sit next to the group they answer. Left to the end of the page they landed
    // below Theme and Feedback, so precise mode's "How accurate it is" held two buttons and no number,
    // and the pixels-per-millimetre that answers it was two screens further down.
    if (!simpleMode) {
        add(SettingsGroup.ACCURACY_DETAILS)
        add(SettingsGroup.LABEL_SIZE)
    }
    add(SettingsGroup.THEME)
    add(SettingsGroup.FEEDBACK)
    add(SettingsGroup.PRO)
    add(SettingsGroup.ABOUT)
}

/**
 * The one switch that decides how much of the app you see (rule 12). It is the first group because it
 * explains the rest of the page: everything technical is behind it, and nothing is hidden from the
 * measurement itself.
 */
@Composable
private fun ModeGroup(vm: SettingsViewModel, state: SettingsState) {
    val type = LocalRulerType.current
    val colors = LocalRulerColors.current
    val label = stringResource(R.string.settings_precise_tools)

    Column(verticalArrangement = Arrangement.spacedBy(RulerSpacing.sm)) {
        SectionHeader(stringResource(R.string.settings_group_mode), rule = true)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(RulerSpacing.sm)) {
            Switch(
                checked = !state.settings.simpleMode,
                onCheckedChange = { vm.setSimpleMode(!it) },
                modifier = Modifier.labelled(label)
            )
            Text(label, style = MaterialTheme.typography.bodyLarge)
        }
        Text(
            stringResource(R.string.settings_precise_tools_hint),
            style = type.bodyMedium,
            color = colors.onSurfaceSecondary.toCompose()
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UnitsGroup(vm: SettingsViewModel, state: SettingsState) {
    val units = MeasurementUnit.entries
    Column(verticalArrangement = Arrangement.spacedBy(RulerSpacing.sm)) {
        SectionHeader(stringResource(R.string.settings_units), rule = true)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            units.forEachIndexed { index, unit ->
                SegmentedButton(
                    selected = state.settings.unit == unit,
                    onClick = { vm.setUnit(unit) },
                    // M19 (DESIGN.md §4): flat 0 dp segments.
                    shape = RulerShapes.chip
                ) {
                    Text(stringResource(unitLabelRes(unit)))
                }
            }
        }
    }
}

@Composable
private fun LabelSizeGroup(vm: SettingsViewModel, state: SettingsState) {
    val type = LocalRulerType.current
    val colors = LocalRulerColors.current
    // TalkBack needs the label read in the composable scope, not inside the semantics lambda.
    val label = stringResource(R.string.ruler_label_size)

    Column(verticalArrangement = Arrangement.spacedBy(RulerSpacing.sm)) {
        SectionHeader(label, rule = true)
        Slider(
            value = state.settings.labelFontScale.toFloat(),
            // In memory while the finger moves, written once when it lifts: a drag is dozens of steps.
            onValueChange = { vm.previewLabelFontScale(it.toDouble()) },
            onValueChangeFinished = { vm.commitLabelFontScale() },
            valueRange = 0.8f..2.0f,
            // TalkBack needs a label on a control that has no text of its own.
            modifier = Modifier.labelled(label)
        )
        // Rule 1: the slider changes label size only. The physical ruler length is unchanged.
        Text(
            stringResource(R.string.settings_font_scale_hint),
            style = type.bodyMedium,
            color = colors.onSurfaceSecondary.toCompose()
        )
    }
}

/**
 * The path to an exact ruler, in both modes (rule 12). In simple mode the group says how accurate the
 * ruler is now, in words, and offers one action; the numbers behind that decision are in
 * [AccuracyDetailsGroup], which only precise mode shows.
 */
@Composable
private fun CalibrationGroup(vm: SettingsViewModel, state: SettingsState, onOpenCalibration: () -> Unit) {
    val type = LocalRulerType.current
    val colors = LocalRulerColors.current
    val calibration = state.calibration

    Column(verticalArrangement = Arrangement.spacedBy(RulerSpacing.sm)) {
        SectionHeader(stringResource(R.string.settings_group_accuracy), rule = true)

        if (calibration == null) {
            Text(
                stringResource(R.string.settings_calibration_loading),
                style = type.bodyMedium,
                color = colors.onSurfaceSecondary.toCompose()
            )
        } else if (state.settings.simpleMode) {
            Text(
                stringResource(
                    if (calibration.verified) {
                        R.string.ruler_accuracy_calibrated_format
                    } else {
                        R.string.ruler_accuracy_estimate_format
                    },
                    formatNumber(calibration.errorBudgetMmPer100Mm, 1)
                ),
                style = type.bodyMedium,
                color = colors.onSurfaceSecondary.toCompose()
            )
        }

        Button(onClick = onOpenCalibration, modifier = Modifier.fillMaxWidth()) {
            Text(
                stringResource(
                    if (state.settings.simpleMode) R.string.action_make_exact else R.string.calibration_title
                )
            )
        }
        // No confirm dialog: the reset happens at once and the snackbar offers the old calibration back,
        // which is the app's one pattern for a destructive action (core/ui/AppMessages.kt). Before, the
        // button changed nothing visible in simple mode — the press had no answer at all (rule 14).
        val messages = LocalAppMessages.current
        val scope = rememberCoroutineScope()
        val resetMessage = stringResource(R.string.message_calibration_reset)
        val failedMessage = stringResource(R.string.message_calibration_save_failed)
        val undoLabel = stringResource(R.string.message_undo)
        // Offered only when there is something to reset: on the starting estimate already, the button
        // would change nothing and its Undo would undo nothing (rule 14).
        if (calibration != null && (calibration.verified || calibration.bezelInsetMm != 0.0)) {
            TextButton(onClick = {
                scope.launch {
                    val previous = vm.resetCalibration()
                    if (previous == null) {
                        messages.say(failedMessage)
                    } else if (messages.say(resetMessage, undoLabel)) {
                        if (!vm.restoreCalibration(previous)) messages.say(failedMessage)
                    }
                }
            }) {
                Text(stringResource(R.string.settings_reset_calibration))
            }
        }
    }
}

/** The numbers behind the accuracy, precise mode only (rule 12). */
@Composable
private fun AccuracyDetailsGroup(vm: SettingsViewModel, state: SettingsState) {
    val type = LocalRulerType.current
    val colors = LocalRulerColors.current
    // Null until the store has been read — an unloaded state must not be shown as a number
    // (M11R finding 4).
    val calibration = state.calibration ?: return

    Column(verticalArrangement = Arrangement.spacedBy(RulerSpacing.sm)) {
        SectionHeader(stringResource(R.string.settings_group_accuracy_details), rule = true)
        Text(
            stringResource(R.string.settings_px_per_mm_format, formatNumber(calibration.pixelsPerMm)),
            style = type.numberLarge,
            color = colors.onSurface.toCompose()
        )
        Text(
            stringResource(R.string.settings_source_format, stringResource(sourceRes(calibration.source))),
            style = type.meta,
            color = colors.onSurfaceSecondary.toCompose()
        )
        Text(
            stringResource(R.string.error_budget_format, formatNumber(calibration.errorBudgetMmPer100Mm)),
            style = type.meta,
            color = colors.onSurfaceSecondary.toCompose()
        )
        // The start-line value moved here from the ruler screen (DESIGN.md §7): it belongs next to
        // the inset that produces it.
        Text(
            stringResource(R.string.start_edge_format, formatNumber(calibration.bezelInsetMm)),
            style = type.meta,
            color = colors.onSurfaceSecondary.toCompose()
        )
        Text(
            stringResource(R.string.calibration_device_format, vm.deviceKey),
            style = type.meta,
            color = colors.onSurfaceSecondary.toCompose()
        )

        // Keyed on the stored value: a plain `remember {}` never updated, so the field kept
        // showing the initial text even after the calibration loaded (M11R finding 4).
        // Saveable so a rotation does not throw away a half-typed value.
        val storedInset = calibration.bezelInsetMm
        var insetText by rememberSaveable(storedInset) { mutableStateOf(formatNumber(storedInset)) }
        // Shown only after a Save that was refused, and cleared by the next keystroke: an error that
        // appears while the user is still typing "1," would be scolding them for an unfinished number.
        var insetRejected by rememberSaveable { mutableStateOf(false) }
        val messages = LocalAppMessages.current
        val scope = rememberCoroutineScope()
        val savedMessage = stringResource(R.string.message_inset_saved)
        val failedMessage = stringResource(R.string.message_calibration_save_failed)
        OutlinedTextField(
            value = insetText,
            onValueChange = { text ->
                insetText = text
                insetRejected = false
            },
            label = { Text(stringResource(R.string.settings_bezel_inset)) },
            isError = insetRejected,
            // The explanation belongs to the field it describes. As a standalone paragraph it sat
            // between the device key and the field and read as another line of the accuracy list.
            // A refused value replaces it with the reason, in the place the eye already looks.
            supportingText = {
                Text(
                    if (insetRejected) {
                        stringResource(
                            R.string.settings_inset_invalid_format,
                            formatNumber(SettingsViewModel.MAX_INSET_MM, 0)
                        )
                    } else {
                        stringResource(R.string.settings_inset_hint)
                    }
                )
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            shape = RulerShapes.field,
            modifier = Modifier.fillMaxWidth()
        )
        // Outlined, not filled: the filled action in this group is Calibration. Two full-width
        // filled buttons stacked read as two equal-priority actions, and this one is a correction
        // to a value, not the group's purpose.
        OutlinedButton(
            onClick = {
                // A value that is not an inset is refused with a reason. It used to become 0 mm, which
                // is a real inset, so a typo or the German prefill "1,5" silently moved the zero.
                val parsed = parseInsetMm(insetText)
                if (parsed == null) {
                    insetRejected = true
                } else {
                    scope.launch {
                        when (vm.saveBezelInset(parsed)) {
                            true -> messages.say(savedMessage)
                            false -> messages.say(failedMessage)
                            null -> Unit
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.settings_save_inset))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ThemeGroup(vm: SettingsViewModel, state: SettingsState) {
    val type = LocalRulerType.current
    val colors = LocalRulerColors.current
    val choices = listOf(
        ThemeChoice.LIGHT to R.string.theme_light,
        ThemeChoice.DARK to R.string.theme_dark,
        ThemeChoice.SYSTEM to R.string.theme_system
    )

    Column(verticalArrangement = Arrangement.spacedBy(RulerSpacing.sm)) {
        SectionHeader(stringResource(R.string.settings_theme), rule = true)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            choices.forEachIndexed { index, (choice, labelRes) ->
                SegmentedButton(
                    selected = state.settings.theme == choice,
                    onClick = { vm.setTheme(choice) },
                    // M19 (DESIGN.md §4): flat 0 dp segments.
                    shape = RulerShapes.chip
                ) {
                    Text(stringResource(labelRes))
                }
            }
        }

        SectionHeader(stringResource(R.string.settings_custom_theme))
        if (!state.pro) {
            // Rule 10: the gate is at the UI boundary, and the wording says what is Pro in the same
            // sentence that says what stays free. It is guidance, so it is not red (DESIGN.md §13).
            Text(
                stringResource(R.string.settings_custom_theme_pro),
                style = type.bodyMedium,
                color = colors.onSurfaceSecondary.toCompose()
            )
        } else {
            // Longer translations ("Hoher Kontrast", "Sepia") do not fit in one row on a narrow screen.
            FlowRow(horizontalArrangement = Arrangement.spacedBy(RulerSpacing.sm)) {
                FilterChip(
                    selected = state.settings.customTheme.isEmpty(),
                    onClick = { vm.setCustomTheme("") },
                    shape = RulerShapes.chip,
                    leadingIcon = if (state.settings.customTheme.isEmpty()) {
                        { Icon(Icons.Default.Check, contentDescription = null) }
                    } else {
                        null
                    },
                    label = { Text(stringResource(R.string.theme_default)) }
                )
                ColorSafeThemeChip("high-contrast", R.string.theme_high_contrast, state, vm)
                ColorSafeThemeChip("sepia", R.string.theme_sepia, state, vm)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ColorSafeThemeChip(name: String, labelRes: Int, state: SettingsState, vm: SettingsViewModel) {
    val selected = state.settings.customTheme == name
    FilterChip(
        selected = selected,
        onClick = { vm.setCustomTheme(name) },
        shape = RulerShapes.chip,
        // Selection is a check icon plus the label, never a fill alone (SPEC 11).
        leadingIcon = if (selected) {
            { Icon(Icons.Default.Check, contentDescription = null) }
        } else {
            null
        },
        label = { Text(stringResource(labelRes)) }
    )
}

@Composable
private fun FeedbackGroup(vm: SettingsViewModel, state: SettingsState) {
    // Same switch, different words (rule 13). "Haptic tick feedback" is a description of a mechanism;
    // "Buzz when a tick passes" is what the user feels and what the switch changes.
    val label = stringResource(
        if (state.settings.simpleMode) R.string.settings_haptics_simple else R.string.settings_haptics
    )

    Column(verticalArrangement = Arrangement.spacedBy(RulerSpacing.sm)) {
        SectionHeader(stringResource(R.string.settings_group_feedback), rule = true)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(RulerSpacing.sm)) {
            Switch(
                checked = state.settings.haptics,
                onCheckedChange = { vm.setHaptics(it) },
                modifier = Modifier.labelled(label)
            )
            Text(label, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun ProGroup(state: SettingsState, onOpenPro: () -> Unit) {
    val type = LocalRulerType.current
    val colors = LocalRulerColors.current

    Column(verticalArrangement = Arrangement.spacedBy(RulerSpacing.sm)) {
        SectionHeader(stringResource(R.string.settings_pro_title), rule = true)
        if (state.pro) {
            Text(
                stringResource(R.string.settings_pro_unlocked),
                style = type.bodyMedium,
                color = colors.onSurfaceSecondary.toCompose()
            )
        }
        OutlinedButton(onClick = onOpenPro) { Text(stringResource(R.string.settings_pro_open)) }
    }
}

@Composable
private fun AboutGroup(
    vm: SettingsViewModel,
    onShowIntro: () -> Unit,
    privacyOptionsRequired: Boolean = false,
    onPrivacyChoices: () -> Unit = {}
) {
    val type = LocalRulerType.current
    val colors = LocalRulerColors.current

    Column(verticalArrangement = Arrangement.spacedBy(RulerSpacing.sm)) {
        SectionHeader(stringResource(R.string.settings_group_about), rule = true)
        Text(
            stringResource(R.string.settings_about_version_format, BuildConfig.VERSION_NAME),
            style = type.bodyMedium,
            color = colors.onSurfaceSecondary.toCompose()
        )
        // Honest wording: the merged manifest does carry the permissions the billing library adds
        // (docs/permissions-audit.md), so the app must not claim it has no internet access.
        Text(
            stringResource(R.string.settings_about_network_note),
            style = type.bodyMedium,
            color = colors.onSurfaceSecondary.toCompose()
        )
        if (privacyOptionsRequired) {
            TextButton(onClick = onPrivacyChoices) {
                Text(stringResource(R.string.settings_privacy_choices))
            }
        }
        TextButton(onClick = {
            vm.setOnboardingSeen(false)
            // The flag alone is not enough: the start destination is read once, so showing the intro
            // again means navigating to it as well.
            onShowIntro()
        }) {
            Text(stringResource(R.string.settings_show_intro))
        }
    }
}

private fun formatNumber(value: Double, decimals: Int = 2): String {
    val format = NumberFormat.getInstance(Locale.getDefault())
    format.maximumFractionDigits = decimals
    return format.format(value)
}

private fun sourceRes(source: CalibrationSource): Int = when (source) {
    CalibrationSource.DEVICE_DEFAULT -> R.string.source_default
    CalibrationSource.CARD -> R.string.source_card
    CalibrationSource.COIN -> R.string.source_coin
    CalibrationSource.MANUAL -> R.string.source_manual
}
