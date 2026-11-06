package com.blueorbit.ruler.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * M15 (DECISIONS rule 12): simple mode is a gate on what Settings shows. The gate is a decision, not a
 * layout detail, so it is checked here instead of by eye on a phone.
 *
 * The two properties that make the gate honest:
 * - the technical controls are the only difference between the two lists;
 * - precise mode shows every group that exists, so no group is unreachable and none is dead code.
 */
class SettingsGateTest {

    private val technical = setOf(SettingsGroup.LABEL_SIZE, SettingsGroup.ACCURACY_DETAILS)

    @Test
    fun simpleModeHidesTheTechnicalControls(): Unit {
        val groups = settingsGroupsFor(simpleMode = true)
        assertTrue(
            "simple mode showed a technical control: ${groups.filter { technical.contains(it) }}",
            groups.none { technical.contains(it) }
        )
    }

    @Test
    fun preciseModeShowsEveryGroupThatExists(): Unit {
        val groups = settingsGroupsFor(simpleMode = false)
        assertEquals(
            "a Settings group exists but no mode shows it",
            SettingsGroup.entries.toSet(),
            groups.toSet()
        )
    }

    @Test
    fun theGateChangesNothingExceptTheTechnicalControls(): Unit {
        val simple = settingsGroupsFor(simpleMode = true)
        val precise = settingsGroupsFor(simpleMode = false)

        assertEquals("precise mode is not simply simple mode plus the technical controls",
            simple.toSet() + technical, precise.toSet())
        assertEquals("the gate reordered the page",
            simple, precise.filter { !technical.contains(it) })
    }

    @Test
    fun simpleModeStillCarriesThePathToAnExactRuler(): Unit {
        // Rule 5 with rule 12: hiding the numbers is allowed, hiding the accuracy or the way to improve
        // it is not. The accuracy group is in the simple list, and so is the mode switch itself.
        val simple = settingsGroupsFor(simpleMode = true)
        assertTrue("simple mode hid the accuracy group", simple.contains(SettingsGroup.ACCURACY))
        assertTrue("simple mode hid the mode switch", simple.contains(SettingsGroup.MODE))
        assertTrue("simple mode hid the units", simple.contains(SettingsGroup.UNITS))
    }

    @Test
    fun theModeSwitchIsTheFirstThingOnThePage(): Unit {
        // The switch explains the rest of the page, so it has to be seen before what it changes.
        assertEquals(SettingsGroup.MODE, settingsGroupsFor(simpleMode = true).first())
        assertEquals(SettingsGroup.MODE, settingsGroupsFor(simpleMode = false).first())
    }

    /**
     * The switch's own hint promises "the technical controls — label size, the bezel inset, pixels per
     * millimetre". Left at the end of the page they landed below Theme and Feedback, so precise mode's
     * "How accurate it is" held two buttons and **no number at all** while the pixels per millimetre that
     * answers it sat two screens lower under a different heading (found on the device at M17).
     */
    @Test
    fun preciseModePutsTheTechnicalControlsNextToTheGroupTheyAnswer(): Unit {
        val precise = settingsGroupsFor(simpleMode = false)
        assertEquals(
            "the numbers behind the accuracy were not directly under the accuracy group",
            precise.indexOf(SettingsGroup.ACCURACY) + 1,
            precise.indexOf(SettingsGroup.ACCURACY_DETAILS)
        )
        assertTrue(
            "label size was separated from the accuracy groups it belongs with",
            precise.indexOf(SettingsGroup.ACCURACY_DETAILS) < precise.indexOf(SettingsGroup.LABEL_SIZE)
        )
    }
}
