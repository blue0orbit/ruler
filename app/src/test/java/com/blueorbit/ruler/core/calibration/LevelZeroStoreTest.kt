package com.blueorbit.ruler.core.calibration

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.blueorbit.ruler.core.sensors.Gravity
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

/**
 * M11R finding 19: the level zero is data the user created on a surface, so it survives the process.
 * Tested against a real DataStore file on the JVM, like the calibration store — no Robolectric.
 */
class LevelZeroStoreTest {

    private val key = "Pixel_9_Pro_Fold_1080x2424"

    private fun dataStoreAt(file: File) = PreferenceDataStoreFactory.create(produceFile = { file })

    private fun tempFile(): File {
        val dir = Files.createTempDirectory("ruler-level-zero").toFile()
        return File(dir, "levelzero.preferences_pb")
    }

    @Test
    fun anEmptyStoreReadsBackAsNotZeroed(): Unit = runBlocking {
        assertEquals(LevelZero(), DataStoreLevelZeroStore(dataStoreAt(tempFile()), key).load())
    }

    @Test
    fun aZeroSurvivesTheSameRoundTrip(): Unit = runBlocking {
        val store = DataStoreLevelZeroStore(dataStoreAt(tempFile()), key)
        store.save(LevelZero(Gravity(0.01, -0.02, 0.99)))
        assertEquals(LevelZero(Gravity(0.01, -0.02, 0.99)), store.load())
    }

    @Test
    fun aZeroSurvivesARestartOverTheSameFile(): Unit = runBlocking {
        val file = tempFile()

        val firstJob = SupervisorJob()
        val firstScope = CoroutineScope(Dispatchers.Default + firstJob)
        DataStoreLevelZeroStore(PreferenceDataStoreFactory.create(scope = firstScope, produceFile = { file }), key)
            .save(LevelZero(Gravity(0.5, 0.5, 0.7)))
        firstJob.cancelAndJoin()

        assertEquals(Gravity(0.5, 0.5, 0.7), DataStoreLevelZeroStore(dataStoreAt(file), key).load().reference)
    }

    @Test
    fun clearingStoresNoReferenceRatherThanAllZeroGravity(): Unit = runBlocking {
        // A null reference must write *nothing*, not a (0,0,0) vector — a zero vector is not "not
        // zeroed" and would compare equal to a real reading of the horizon.
        //
        // One write only: DataStore's file storage renames `x.tmp` over `x`, and the Windows JVM
        // refuses that when the destination already exists, so a second write to the same file fails
        // on this host even though it is fine on Android.
        val store = DataStoreLevelZeroStore(dataStoreAt(tempFile()), key)
        store.save(LevelZero())

        val loaded = store.load()
        assertEquals(LevelZero(), loaded)
        assertEquals(null, loaded.reference)
    }

    @Test
    fun twoDevicesSharingAFileDoNotShareAZero(): Unit = runBlocking {
        val dataStore = dataStoreAt(tempFile())
        val fold = DataStoreLevelZeroStore(dataStore, key)
        val other = DataStoreLevelZeroStore(dataStore, "Pixel_8_1080x2424")

        fold.save(LevelZero(Gravity(0.1, 0.2, 0.3)))

        assertEquals(Gravity(0.1, 0.2, 0.3), fold.load().reference)
        assertEquals(LevelZero(), other.load())
    }

    @Test
    fun anUnreadableStoreReadsAsNotZeroedInsteadOfThrowing(): Unit = runBlocking {
        assertEquals(LevelZero(), DataStoreLevelZeroStore(FailingDataStore(), key).load())
    }

    /** A write to an unreadable store reports failure instead of throwing into the ViewModel's scope. */
    @Test
    fun anUnwritableStoreReportsTheFailureInsteadOfThrowing(): Unit = runBlocking {
        val store = DataStoreLevelZeroStore(FailingDataStore(), key)
        assertEquals(false, store.save(LevelZero(Gravity(0.0, 0.1, 0.99))))
        assertEquals(false, store.save(LevelZero()))
    }

    @Test
    fun aSuccessfulWriteSaysSo(): Unit = runBlocking {
        assertEquals(true, DataStoreLevelZeroStore(dataStoreAt(tempFile()), key).save(LevelZero(Gravity(0.0, 0.0, 1.0))))
    }

    private class FailingDataStore : DataStore<Preferences> {
        override val data: Flow<Preferences> = flow { throw IOException("simulated read failure") }

        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
            throw IOException("simulated write failure")
    }
}
