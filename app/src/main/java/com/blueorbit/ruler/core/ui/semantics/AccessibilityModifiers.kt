package com.blueorbit.ruler.core.ui.semantics

import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics

/**
 * TalkBack announces a reading when it changes without stealing focus from the control the user is
 * on, so the region is polite rather than assertive. Readings that update continuously (ruler span,
 * level angle) must not interrupt the user every time they move the device.
 */
fun Modifier.announceChanges(): Modifier = semantics { liveRegion = LiveRegionMode.Polite }

/** A live region that also carries a label, for regions that have no visible text of their own. */
fun Modifier.announceChanges(label: String): Modifier = semantics {
    contentDescription = label
    liveRegion = LiveRegionMode.Polite
}

/** Controls with no visible text (sliders, canvas, checkbox) still need a label TalkBack can read. */
fun Modifier.labelled(label: String): Modifier = semantics { contentDescription = label }
