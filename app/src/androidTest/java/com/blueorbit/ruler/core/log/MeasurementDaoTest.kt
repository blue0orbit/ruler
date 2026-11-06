package com.blueorbit.ruler.core.log

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented test for the Room layer (DECISIONS.md: instrumented test for Room). It needs a
 * device, so it cannot run in this build session — see PROGRESS.md. The repository rules that do
 * not need a database are covered by LogRepositoryTest on the JVM.
 */
@RunWith(AndroidJUnit4::class)
class MeasurementDaoTest {

    private lateinit var database: AppDatabase

    @Before
    fun open() {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            AppDatabase::class.java
        ).build()
    }

    @After
    fun close() {
        database.close()
    }

    private fun row(id: Long, micrometres: Long, label: String, note: String, at: Long) = Measurement(
        id = id,
        valueMicrometres = micrometres,
        unit = "MM",
        label = label,
        note = note,
        source = "CARD",
        errorBudgetMmPer100Mm = 0.185,
        createdAtMillis = at
    )

    @Test
    fun insertReadEditDelete(): Unit = runBlocking {
        val dao = database.measurements()
        val saved = row(0, 100_000, "shelf", "oak", 1_000)
        val id = dao.insert(saved)

        assertEquals(1, dao.count())
        assertEquals("shelf", dao.findById(id)?.label)

        assertEquals(1, dao.update(saved.copy(id = id, label = "bookcase")))
        assertEquals("bookcase", dao.findById(id)?.label)

        assertEquals(1, dao.delete(saved.copy(id = id)))
        assertEquals(0, dao.count())
    }

    @Test
    fun rowsComeBackNewestFirst(): Unit = runBlocking {
        val dao = database.measurements()
        dao.insert(row(0, 10_000, "old", "", 1_000))
        dao.insert(row(0, 20_000, "new", "", 2_000))
        assertEquals(listOf("new", "old"), dao.observeAll().first().map { it.label })
    }

    @Test
    fun logSurvivesReopeningTheDatabase(): Unit = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val first = Room.databaseBuilder(context, AppDatabase::class.java, "instrumented-log.db").build()
        first.measurements().insert(row(0, 50_000, "shelf", "", 1_000))
        first.close()

        val reopened = Room.databaseBuilder(context, AppDatabase::class.java, "instrumented-log.db").build()
        assertEquals(1, reopened.measurements().count())
        reopened.close()
    }
}
