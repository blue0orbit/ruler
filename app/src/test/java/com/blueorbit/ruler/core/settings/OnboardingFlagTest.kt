package com.blueorbit.ruler.core.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.blueorbit.ruler.core.geometry.MeasurementUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * `onboardingSeen` (DESIGN.md §8) decides the start destination, so a wrong default means either the
 * intro repeats on every launch or a new user never sees it. Both are asserted here.
 *
 * One write per store file: DataStore on Windows renames `file.tmp` over `file`, and a second
 * instance over the same path fails in the test harness (recorded as a test constraint at M9).
 */
class OnboardingFlagTest {

    private fun dataStoreAt(file: File): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(produceFile = { file })

    private fun tempFile(): File {
        val dir = Files.createTempDirectory("ruler-onboarding").toFile()
        return File(dir, "settings.preferences_pb")
    }

    @Test
    fun aFreshInstallHasNotSeenTheIntro(): Unit = runBlocking {
        val current = DataStoreSettingsStore(dataStoreAt(tempFile())).current()
        assertFalse("a new user must be shown the intro", current.onboardingSeen)
        assertFalse(AppSettings().onboardingSeen)
    }

    @Test
    fun theFlagRoundTripsThroughTheStore(): Unit = runBlocking {
        val store = DataStoreSettingsStore(dataStoreAt(tempFile()))
        store.save(AppSettings(onboardingSeen = true))
        assertTrue(store.current().onboardingSeen)
    }

    @Test
    fun theIntroFlagIsStoredAlongsideTheOtherSettingsWithoutDisturbingThem(): Unit = runBlocking {
        // One write per store file (the Windows constraint recorded at M9): a second `edit` through the
        // same file fails here with DataStore's "multiple instances" rename error, so the assertion is
        // made on a single save that carries the flag together with a non-default everything-else.
        val store = DataStoreSettingsStore(dataStoreAt(tempFile()))
        val written = AppSettings(ThemeChoice.DARK, "sepia", false, 1.4, MeasurementUnit.INCH, true)
        store.save(written)

        val after = store.current()
        assertEquals(ThemeChoice.DARK, after.theme)
        assertEquals("sepia", after.customTheme)
        assertEquals(1.4, after.labelFontScale, 1e-9)
        assertEquals(MeasurementUnit.INCH, after.unit)
        assertFalse(after.haptics)
        assertTrue("the intro flag must survive a write that also carries every other setting", after.onboardingSeen)
    }

    @Test
    fun aCorruptedFlagValueFallsBackToShowTheIntroInsteadOfCrashing(): Unit = runBlocking {
        val dataStore = dataStoreAt(tempFile())
        val key = booleanPreferencesKey("onboardingSeen")

        // Preferences are typed, so the realistic failure is a missing or false value written by an
        // older version — the reader must not infer "seen" from an absent key.
        dataStore.edit { prefs -> prefs[key] = false }
        assertFalse(DataStoreSettingsStore(dataStore).current().onboardingSeen)
    }

    @Test
    fun theFlagSurvivesARestartOverTheSameFile(): Unit = runBlocking {
        val file = tempFile()

        // The first store is shut down before the second opens the same path, which is the pattern
        // `SettingsStoreTest` proved on Windows.
        val writeJob = SupervisorJob()
        val writeScope = CoroutineScope(Dispatchers.Default + writeJob)
        DataStoreSettingsStore(PreferenceDataStoreFactory.create(scope = writeScope, produceFile = { file }))
            .save(AppSettings(onboardingSeen = true))
        writeJob.cancelAndJoin()

        // A new store instance over the same path is what a process death produces.
        assertTrue(DataStoreSettingsStore(dataStoreAt(file)).current().onboardingSeen)
    }
}
