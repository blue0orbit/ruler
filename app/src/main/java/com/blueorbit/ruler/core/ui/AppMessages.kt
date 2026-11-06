package com.blueorbit.ruler.core.ui

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * The app's one confirmation channel: a single [SnackbarHostState] hoisted above the NavHost, so a
 * message raised on one screen ("Saved 65.3 mm") survives navigating to another and is never drawn twice.
 *
 * Every action that changes stored data says so here — save, delete, calibration saved, reset — and the
 * destructive ones offer Undo instead of a confirm dialog. The default is a fresh host so a screen
 * previewed or tested on its own still composes; the shell provides the real one.
 */
val LocalAppMessages = staticCompositionLocalOf { SnackbarHostState() }

/**
 * Shows [message] and returns true when the user tapped the action. Any message already showing is
 * dismissed first: a stale "Saved" under a fresh "Deleted" reads as two events in the wrong order.
 */
suspend fun SnackbarHostState.say(
    message: String,
    actionLabel: String? = null,
    duration: SnackbarDuration = if (actionLabel != null) SnackbarDuration.Long else SnackbarDuration.Short
): Boolean {
    currentSnackbarData?.dismiss()
    return showSnackbar(message, actionLabel, withDismissAction = false, duration = duration) ==
        SnackbarResult.ActionPerformed
}
