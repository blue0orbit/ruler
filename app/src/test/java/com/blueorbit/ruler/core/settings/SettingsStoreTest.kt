package com.blueorbit.ruler.core.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.blueorbit.ruler.core.geometry.MeasurementUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.io.IOException
import java.nio.file.Files

/** DataStore on the JVM against a real file — no Robolectric dependency added (DECISIONS.md). */
class SettingsStoreTest {

    private fun dataStoreAt(file: File) = PreferenceDataStoreFactory.create(produceFile = { file })

    private fun tempFile(): File {
        val dir = Files.createTempDirectory("ruler-settings").toFile()
        return File(dir, "settings.preferences_pb")
    }

    @Test
    fun emptyStoreUsesTheDocumentedDefaults(): Unit = runBlocking {
        val current = DataStoreSettingsStore(dataStoreAt(tempFile())).current()
        assertEquals(ThemeChoice.SYSTEM, current.theme)
        assertEquals("", current.customTheme)
        assertEquals(true, current.haptics)
        assertEquals(1.0, current.labelFontScale, 1e-9)
        // DECISIONS rule 12: a new install opens as a ruler, not as a metrology instrument.
        assertEquals(true, current.simpleMode)
    }

    @Test
    fun simpleModeStaysSimpleUntilTheUserTurnsItOff(): Unit = runBlocking {
        val store = DataStoreSettingsStore(dataStoreAt(tempFile()))
        assertEquals(true, store.current().simpleMode)

        store.save(AppSettings(simpleMode = false))
        assertEquals(false, store.current().simpleMode)
    }

    @Test
    fun preciseModeIsStillPreciseAfterARestart(): Unit = runBlocking {
        val file = tempFile()

        val firstJob = SupervisorJob()
        val firstScope = CoroutineScope(Dispatchers.Default + firstJob)
        DataStoreSettingsStore(PreferenceDataStoreFactory.create(scope = firstScope, produceFile = { file }))
            .save(AppSettings(simpleMode = false))
        firstJob.cancelAndJoin()

        // A choice the user made has to survive the app closing. Showing simple mode again after a
        // restart would take back the controls they asked for.
        assertEquals(false, DataStoreSettingsStore(dataStoreAt(file)).current().simpleMode)
    }

    /**
     * M17: the first-run instruction on the ruler is dismissed once and must stay dismissed. A flag kept
     * only in the composition would teach the same sentence to the same user on every launch.
     */
    @Test
    fun aDismissedFirstRunInstructionStaysDismissedAfterARestart(): Unit = runBlocking {
        val file = tempFile()

        val firstJob = SupervisorJob()
        val firstScope = CoroutineScope(Dispatchers.Default + firstJob)
        DataStoreSettingsStore(PreferenceDataStoreFactory.create(scope = firstScope, produceFile = { file }))
            .save(AppSettings(firstRunInstructionSeen = true))
        firstJob.cancelAndJoin()

        assertEquals(true, DataStoreSettingsStore(dataStoreAt(file)).current().firstRunInstructionSeen)
    }

    @Test
    fun settingsPersistThroughARoundTrip(): Unit = runBlocking {
        val store = DataStoreSettingsStore(dataStoreAt(tempFile()))
        val saved = AppSettings(ThemeChoice.DARK, "high-contrast", false, 1.4)
        store.save(saved)
        assertEquals(saved, store.current())
    }

    @Test
    fun settingsSurviveARestartOverTheSameFile(): Unit = runBlocking {
        val file = tempFile()

        val firstJob = SupervisorJob()
        val firstScope = CoroutineScope(Dispatchers.Default + firstJob)
        DataStoreSettingsStore(PreferenceDataStoreFactory.create(scope = firstScope, produceFile = { file }))
            .save(AppSettings(ThemeChoice.LIGHT, "sepia", true, 1.25))
        firstJob.cancelAndJoin()

        val current = DataStoreSettingsStore(dataStoreAt(file)).current()
        assertEquals(ThemeChoice.LIGHT, current.theme)
        assertEquals("sepia", current.customTheme)
        assertEquals(1.25, current.labelFontScale, 1e-9)
    }

    @Test
    fun hapticsCanBeDisabledAndStayDisabled(): Unit = runBlocking {
        val store = DataStoreSettingsStore(dataStoreAt(tempFile()))
        store.save(AppSettings(haptics = false))
        assertEquals(false, store.current().haptics)
    }

    @Test
    fun anUnreadableStoreReturnsDefaultsInsteadOfInventingASetting(): Unit = runBlocking {
        val current = DataStoreSettingsStore(FailingDataStore()).current()
        assertEquals(AppSettings(), current)
    }

    /**
     * M18 review (M17 re-run): `edit` reads the file before it writes, so a store that cannot be read
     * throws on a write too. An exception that escapes a `viewModelScope.launch` reaches the main thread,
     * which would close the app the moment someone tapped a unit.
     */
    @Test
    fun aWriteToAnUnwritableStoreDoesNotThrowOutOfTheApp(): Unit = runBlocking {
        DataStoreSettingsStore(FailingDataStore()).save(AppSettings(unit = MeasurementUnit.INCH))
    }

    @Test
    fun theUnitPersistsThroughARoundTrip(): Unit = runBlocking {
        val store = DataStoreSettingsStore(dataStoreAt(tempFile()))
        store.save(AppSettings(unit = MeasurementUnit.INCH))
        assertEquals(MeasurementUnit.INCH, store.current().unit)
    }

    @Test
    fun anUnknownStoredUnitNameFallsBackToMm(): Unit = runBlocking {
        val dataStore = dataStoreAt(tempFile())
        val unitKey = stringPreferencesKey("unit")

        // A unit name written by a version that has more units must not crash the reader.
        dataStore.edit { prefs -> prefs[unitKey] = "PARSEC" }
        assertEquals(MeasurementUnit.MM, DataStoreSettingsStore(dataStore).current().unit)
    }

    @Test
    fun anUnknownStoredThemeNameFallsBackInsteadOfCrashing(): Unit = runBlocking {
        val dataStore = dataStoreAt(tempFile())
        val store = DataStoreSettingsStore(dataStore)
        val themeKey = stringPreferencesKey("theme")

        // A value written by a newer version of the app must not be read as a theme that does not
        // exist here — it falls back to the documented default rather than throwing.
        dataStore.edit { prefs -> prefs[themeKey] = "SOMETHING_NEW" }
        assertEquals(ThemeChoice.SYSTEM, store.current().theme)
    }

    private class FailingDataStore : DataStore<Preferences> {
        override val data: Flow<Preferences> = flow { throw IOException("simulated read failure") }

        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
            throw IOException("simulated write failure")
    }
}
