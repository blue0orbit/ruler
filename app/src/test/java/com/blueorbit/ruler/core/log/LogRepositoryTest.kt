package com.blueorbit.ruler.core.log

import com.blueorbit.ruler.core.entitlement.EntitlementRepository
import com.blueorbit.ruler.core.entitlement.EntitlementRule
import com.blueorbit.ruler.core.entitlement.EntitlementState
import com.blueorbit.ruler.core.entitlement.EntitlementStatus
import com.blueorbit.ruler.core.entitlement.ProFeature
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The repository is tested against a fake DAO, so the search, edit, delete and Pro-tag rules are
 * checked on the JVM. Room itself is covered by the instrumented DAO test.
 */
class LogRepositoryTest {

    private class FakeDao : MeasurementDao {
        private val rows = ArrayList<Measurement>()
        private val flow = MutableStateFlow<List<Measurement>>(emptyList())
        // A counter, not `rows.size + 1`: a delete followed by an insert would hand the next row the id of
        // one that is already gone, and the test would pass while the id it checks is meaningless.
        private var nextId = 0L

        override suspend fun insert(measurement: Measurement): Long {
            val row = measurement.copy(id = ++nextId)
            rows += row
            flow.value = rows.sortedByDescending { it.createdAtMillis }
            return row.id
        }

        override suspend fun update(measurement: Measurement): Int {
            val index = rows.indexOfFirst { it.id == measurement.id }
            if (index < 0) return 0
            rows[index] = measurement
            flow.value = rows.sortedByDescending { it.createdAtMillis }
            return 1
        }

        override suspend fun delete(measurement: Measurement): Int {
            val removed = rows.removeAll { it.id == measurement.id }
            flow.value = rows.sortedByDescending { it.createdAtMillis }
            return if (removed) 1 else 0
        }

        override fun observeAll(): Flow<List<Measurement>> = flow
        override suspend fun count(): Int = rows.size
        override suspend fun findById(id: Long): Measurement? = rows.firstOrNull { it.id == id }
        override suspend fun deleteById(id: Long): Int {
            val removed = rows.removeAll { it.id == id }
            flow.value = rows.sortedByDescending { it.createdAtMillis }
            return if (removed) 1 else 0
        }
    }

    private class FakeEntitlement(private val pro: Boolean) : EntitlementRepository {
        override val state: StateFlow<EntitlementState> = MutableStateFlow(
            EntitlementState(if (pro) EntitlementStatus.UNLOCKED else EntitlementStatus.LOCKED, EntitlementRule.PRODUCT_ID)
        )

        override fun has(feature: ProFeature): Boolean = pro
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

    /** The rows one search shows, which is what `LogView.entries` gives the Log screen. */
    private suspend fun LogRepository.rowsFor(query: String): List<Measurement> =
        observeView(query).first().entries

    @Test
    fun saveThenObserveReturnsTheRow(): Unit = runBlocking {
        val repository = LogRepository(FakeDao(), FakeEntitlement(false))
        repository.save(row(0, 100_000, "shelf", "", 1_000))
        val entries = repository.rowsFor("")
        assertEquals(1, entries.size)
        assertEquals(100_000L, entries.first().valueMicrometres)
    }

    @Test
    fun entriesComeBackNewestFirst(): Unit = runBlocking {
        val repository = LogRepository(FakeDao(), FakeEntitlement(false))
        repository.save(row(0, 10_000, "old", "", 1_000))
        repository.save(row(0, 20_000, "new", "", 2_000))
        val entries = repository.rowsFor("")
        assertEquals(listOf("new", "old"), entries.map { it.label })
    }

    @Test
    fun searchMatchesLabelAndNote(): Unit = runBlocking {
        val repository = LogRepository(FakeDao(), FakeEntitlement(false))
        repository.save(row(0, 10_000, "Bookshelf", "oak", 1_000))
        repository.save(row(0, 20_000, "Desk", "book height", 2_000))

        val byLabel = repository.rowsFor("shelf")
        assertEquals(listOf("Bookshelf"), byLabel.map { it.label })

        val byNote = repository.rowsFor("BOOK HEIGHT")
        assertEquals(listOf("Desk"), byNote.map { it.label })

        assertEquals(2, repository.rowsFor("").size)
        assertTrue(repository.rowsFor("nothing").isEmpty())
    }

    /**
     * The Log screen needs both numbers from one read: what the search shows, and whether there is a log
     * at all. Export reads the stored rows directly rather than through a blank search, so the two can
     * never disagree about how many rows a file should hold (M18).
     */
    @Test
    fun aSearchThatMatchesNothingStillReportsTheWholeLog(): Unit = runBlocking {
        val repository = LogRepository(FakeDao(), FakeEntitlement(false))
        repository.save(row(0, 10_000, "Bookshelf", "oak", 1_000))
        repository.save(row(0, 20_000, "Desk", "", 2_000))

        val view = repository.observeView("nothing matches").first()
        assertTrue(view.entries.isEmpty())
        assertEquals(2, view.totalEntries)
        assertEquals(2, repository.allRows().size)

        val empty = LogRepository(FakeDao(), FakeEntitlement(false)).observeView("anything").first()
        assertTrue(empty.entries.isEmpty())
        assertEquals(0, empty.totalEntries)
        assertTrue(repository.save(row(0, 30_000, "Third", "", 3_000)) > 0)
        assertEquals(3, repository.allRows().size)
    }

    @Test
    fun editAndDeleteWork(): Unit = runBlocking {
        val dao = FakeDao()
        val repository = LogRepository(dao, FakeEntitlement(false))
        val saved = row(0, 10_000, "shelf", "oak", 1_000)
        val id = repository.save(saved)

        assertTrue(repository.edit(saved.copy(id = id, label = "bookcase")))
        assertEquals("bookcase", repository.rowsFor("bookcase").first().label)

        assertTrue(repository.delete(saved.copy(id = id)))
        assertFalse(repository.delete(saved.copy(id = id)))
        assertEquals(0, repository.count())
    }

    @Test
    fun tagsAreProAndAreDroppedForFreeUsersAtTheBoundary(): Unit = runBlocking {
        val free = LogRepository(FakeDao(), FakeEntitlement(false))
        free.save(row(0, 10_000, "shelf", "", 1_000).copy(tags = "kitchen"))
        assertEquals("", free.rowsFor("").first().tags)

        val pro = LogRepository(FakeDao(), FakeEntitlement(true))
        pro.save(row(0, 10_000, "shelf", "", 1_000).copy(tags = "kitchen"))
        assertEquals("kitchen", pro.rowsFor("").first().tags)
    }
}
