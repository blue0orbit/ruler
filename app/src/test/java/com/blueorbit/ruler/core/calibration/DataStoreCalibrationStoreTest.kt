package com.blueorbit.ruler.core.calibration

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.IOException
import java.nio.file.Files

/**
 * DataStore is tested against a real file on the JVM, so no Robolectric dependency is added
 * (DECISIONS.md: every dependency is a Data Safety and merged-manifest risk).
 */
class DataStoreCalibrationStoreTest {

    private val guess = Calibration(390.0 / 25.4, CalibrationSource.DEVICE_DEFAULT, 5.0)
    private val key = "Pixel_9_Pro_Fold_1080x2424"

    private fun storeFor(dataStore: DataStore<Preferences>, deviceKey: String) =
        DataStoreCalibrationStore(dataStore, deviceKey, guess)

    private fun dataStoreAt(file: File) = PreferenceDataStoreFactory.create(produceFile = { file })

    private fun tempFile(): File {
        val dir = Files.createTempDirectory("ruler-calibration").toFile()
        return File(dir, "calibration.preferences_pb")
    }

    @Test
    fun emptyStoreFallsBackToTheLabelledGuess(): Unit = runBlocking {
        val current = storeFor(dataStoreAt(tempFile()), key).current()
        assertEquals(CalibrationSource.DEVICE_DEFAULT, current.source)
        assertEquals(guess, current)
    }

    @Test
    fun saveThenReadKeepsValueSourceAndBudget(): Unit = runBlocking {
        val store = storeFor(dataStoreAt(tempFile()), key)
        val saved = Calibration(15.40, CalibrationSource.CARD, 0.185)
        assertTrue("a write to a healthy store reported a failure", store.save(saved))
        assertEquals(saved, store.current())
    }

    @Test
    fun bezelInsetSurvivesTheSameRoundTrip(): Unit = runBlocking {
        val store = storeFor(dataStoreAt(tempFile()), key)
        store.save(Calibration(15.40, CalibrationSource.CARD, 0.185, 3.2))
        assertEquals(3.2, store.current().bezelInsetMm, 1e-9)
    }

    @Test
    fun valueSurvivesARestartOverTheSameFile(): Unit = runBlocking {
        val file = tempFile()

        val firstJob = SupervisorJob()
        val firstScope = CoroutineScope(Dispatchers.Default + firstJob)
        storeFor(PreferenceDataStoreFactory.create(scope = firstScope, produceFile = { file }), key)
            .save(Calibration(16.0, CalibrationSource.COIN, 0.4))
        firstJob.cancelAndJoin()

        // A second container: the app restarted, or an upgrade kept the file.
        val current = storeFor(dataStoreAt(file), key).current()
        assertEquals(16.0, current.pixelsPerMm, 1e-9)
        assertEquals(CalibrationSource.COIN, current.source)
        assertEquals(0.4, current.errorBudgetMmPer100Mm, 1e-9)
    }

    @Test
    fun twoDevicesSharingAFileDoNotShareACalibration(): Unit = runBlocking {
        val dataStore = dataStoreAt(tempFile())
        val fold = storeFor(dataStore, key)
        val other = storeFor(dataStore, "Pixel_8_1080x2424")

        fold.save(Calibration(15.40, CalibrationSource.CARD, 0.185))

        assertEquals(15.40, fold.current().pixelsPerMm, 1e-9)
        assertEquals(CalibrationSource.DEVICE_DEFAULT, other.current().source)
    }

    @Test
    fun unreadableStoreReturnsTheGuessInsteadOfInventingANumber(): Unit = runBlocking {
        val store = storeFor(FailingDataStore(), key)
        assertEquals(guess, store.current())
    }

    @Test
    fun anUnknownStoredSourceNameFallsBackToTheGuess(): Unit = runBlocking {
        // M11R finding 16: `CalibrationSource.valueOf` throws on a name written by another build,
        // which crashed app start. A source that cannot be named cannot be trusted.
        val dataStore = dataStoreAt(tempFile())
        dataStore.edit { prefs ->
            prefs[doublePreferencesKey("pxPerMm:$key")] = 15.40
            prefs[stringPreferencesKey("source:$key")] = "SCANNED_BY_ALIENS"
            prefs[doublePreferencesKey("errorBudget:$key")] = 0.185
        }

        assertEquals(guess, storeFor(dataStore, key).current())
    }

    @Test
    fun aCalibrationWithNoErrorBudgetFallsBackToTheGuess(): Unit = runBlocking {
        // M11R finding 17: defaulting the budget to 0.0 would print "±0 mm per 100 mm" — a
        // guarantee no calibration method supports (rule 5).
        val dataStore = dataStoreAt(tempFile())
        dataStore.edit { prefs ->
            prefs[doublePreferencesKey("pxPerMm:$key")] = 15.40
            prefs[stringPreferencesKey("source:$key")] = CalibrationSource.CARD.name
        }

        assertEquals(guess, storeFor(dataStore, key).current())
    }

    /**
     * The read path already fell back on a damaged store; the write path threw, from inside a
     * `viewModelScope.launch`, which closes the app. It now answers false so the screen can say so.
     */
    @Test
    fun aWriteToAnUnwritableStoreReportsFailureInsteadOfThrowing(): Unit = runBlocking {
        val store = storeFor(FailingDataStore(), key)
        assertFalse(store.save(Calibration(15.40, CalibrationSource.CARD, 0.185)))
        assertFalse(store.clear())
    }

    @Test
    fun aCorruptStoreIsAFailedWriteAndNotACrash(): Unit = runBlocking {
        val store = storeFor(CorruptDataStore(), key)
        assertFalse(store.save(Calibration(15.40, CalibrationSource.CARD, 0.185)))
        assertFalse(store.clear())
        assertEquals(guess, store.current())
    }

    @Test
    fun clearReportsSuccessAndReturnsTheGuess(): Unit = runBlocking {
        // One write per file, like every other test here: DataStore replaces its file by rename, and on
        // a Windows JVM a second rename over an existing file fails — a host quirk, not the app's path.
        val store = storeFor(dataStoreAt(tempFile()), key)
        assertTrue(store.clear())
        assertEquals(guess, store.current())
    }

    /** What a damaged file throws: `CorruptionException` is an IOException, so one catch covers both. */
    private class CorruptDataStore : DataStore<Preferences> {
        override val data: Flow<Preferences> = flow { throw CorruptionException("simulated corrupt file") }

        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
            throw CorruptionException("simulated corrupt file")
    }

    private class FailingDataStore : DataStore<Preferences> {
        override val data: Flow<Preferences> = flow { throw IOException("simulated read failure") }

        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
            throw IOException("simulated write failure")
    }
}
