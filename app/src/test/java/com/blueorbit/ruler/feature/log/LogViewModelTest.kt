package com.blueorbit.ruler.feature.log

import com.blueorbit.ruler.core.entitlement.EntitlementRepository
import com.blueorbit.ruler.core.entitlement.EntitlementRule
import com.blueorbit.ruler.core.entitlement.EntitlementState
import com.blueorbit.ruler.core.entitlement.EntitlementStatus
import com.blueorbit.ruler.core.log.LogRepository
import com.blueorbit.ruler.core.log.Measurement
import com.blueorbit.ruler.core.log.MeasurementDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * M18 (DECISIONS rule 14): the export controls are offered only when the stored log has rows, and
 * "the stored log" is not the same thing as "the rows on screen".
 *
 * The first cut of that gate read the filtered view, so its own review found the hole it left: type a
 * search word over an empty log, and Export came back. These tests hold the two halves of the rule —
 * a search that hides rows must not hide an export that still has rows to write, and a search typed
 * over an empty log must not invent an export that has none.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LogViewModelTest {

    private class RowsDao : MeasurementDao {
        private val rows = ArrayList<Measurement>()
        private val flow = MutableStateFlow<List<Measurement>>(emptyList())
        private var nextId = 0L

        override suspend fun insert(measurement: Measurement): Long {
            // A counter, not `rows.size + 1`: after a delete, a size-based id can hand out an id that a
            // row still in the log already has.
            // A row that carries its id (an Undo putting a deleted row back) keeps it, as Room does.
            val row = if (measurement.id != 0L) measurement else measurement.copy(id = ++nextId)
            if (row.id > nextId) nextId = row.id
            rows += row
            publish()
            return row.id
        }

        override suspend fun update(measurement: Measurement): Int {
            val index = rows.indexOfFirst { it.id == measurement.id }
            if (index < 0) return 0
            rows[index] = measurement
            publish()
            return 1
        }

        override suspend fun delete(measurement: Measurement): Int {
            val removed = rows.removeAll { it.id == measurement.id }
            publish()
            return if (removed) 1 else 0
        }

        override suspend fun deleteById(id: Long): Int {
            val removed = rows.removeAll { it.id == id }
            publish()
            return if (removed) 1 else 0
        }

        override fun observeAll(): Flow<List<Measurement>> = flow
        override suspend fun count(): Int = rows.size
        override suspend fun findById(id: Long): Measurement? = rows.firstOrNull { it.id == id }

        private fun publish() {
            flow.value = rows.sortedByDescending { it.createdAtMillis }
        }
    }

    /**
     * The entitlement the log is built with. `unlock()` is what the M18 review's `pro` finding is about:
     * the screen has to follow the store, not the next row written.
     */
    private class Entitlement : EntitlementRepository {
        private val flow = MutableStateFlow(
            EntitlementState(EntitlementStatus.LOCKED, EntitlementRule.PRODUCT_ID)
        )

        override val state: StateFlow<EntitlementState> = flow

        fun unlock() {
            flow.value = EntitlementState(EntitlementStatus.UNLOCKED, EntitlementRule.PRODUCT_ID)
        }
    }

    private fun row(micrometres: Long, label: String) = Measurement(
        valueMicrometres = micrometres,
        unit = "MM",
        label = label,
        note = "",
        source = "CARD",
        errorBudgetMmPer100Mm = 0.12,
        createdAtMillis = 0L
    )

    /**
     * `viewModelScope` needs a Main dispatcher and `runTest` does not supply one, so this installs one
     * the way every other ViewModel test in the app does (M16). The clock is a counter rather than the
     * wall clock, so the newest-first order the log promises is decided by the test, not by the machine.
     */
    private fun logOnMain(body: suspend TestScope.(LogViewModel, Entitlement) -> Unit) = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        var stamped = 0L
        try {
            val entitlement = Entitlement()
            val vm = LogViewModel(
                repository = LogRepository(RowsDao(), entitlement),
                entitlement = entitlement,
                clock = { ++stamped * 1_000L }
            )
            advanceUntilIdle()
            body(vm, entitlement)
        } finally {
            Dispatchers.resetMain()
        }
    }

    /**
     * `stateIn(SharingStarted.Lazily)` starts collecting on the first collector and, being `Lazily`,
     * never stops. One read is therefore enough to keep `state.value` live for the rest of a test.
     */
    private suspend fun TestScope.snapshot(vm: LogViewModel): LogState {
        vm.state.first()
        advanceUntilIdle()
        return vm.state.value
    }

    @Test
    fun aSearchTypedOverAnEmptyLogDoesNotOfferAnExport(): Unit = logOnMain { vm, _ ->
        snapshot(vm)

        vm.setQuery("spoon")
        advanceUntilIdle()

        assertTrue(vm.state.value.entries.isEmpty())
        assertEquals(0, vm.state.value.totalEntries)
        assertFalse(
            "export was offered over a log with nothing in it because a search was typed",
            exportOffered(vm.state.value.totalEntries)
        )
    }

    @Test
    fun aSearchThatMatchesNothingStillKnowsTheLogHasRows(): Unit = logOnMain { vm, _ ->
        vm.save(row(10_000L, "Bookshelf"))
        vm.save(row(20_000L, "Desk"))
        advanceUntilIdle()
        snapshot(vm)

        vm.setQuery("nothing matches this")
        advanceUntilIdle()

        assertTrue("the search hid every row", vm.state.value.entries.isEmpty())
        assertEquals(2, vm.state.value.totalEntries)
        assertTrue(
            "a filtered view hid an export that still had rows to write",
            exportOffered(vm.state.value.totalEntries)
        )
    }

    @Test
    fun theExportStopsBeingOfferedWhenTheLastRowGoes(): Unit = logOnMain { vm, _ ->
        val saved = row(10_000L, "Bookshelf")
        vm.save(saved)
        advanceUntilIdle()
        val before = snapshot(vm)
        assertTrue(exportOffered(before.totalEntries))

        vm.delete(before.entries.first())
        advanceUntilIdle()

        assertEquals(0, vm.state.value.totalEntries)
        assertFalse(exportOffered(vm.state.value.totalEntries))
    }

    /** The search box filters the screen; the file it writes is always the whole log (finding 13). */
    @Test
    fun exportAlwaysReadsTheWholeLogAndNotTheSearchResults(): Unit = logOnMain { vm, _ ->
        vm.save(row(10_000L, "Bookshelf"))
        vm.save(row(20_000L, "Desk"))
        advanceUntilIdle()
        snapshot(vm)

        vm.setQuery("shelf")
        advanceUntilIdle()
        assertEquals(1, vm.state.value.entries.size)

        assertEquals(2, vm.exportRows().size)
    }

    /**
     * Tags are the one field the log gates on Pro, so the gate has to follow the store. Reading
     * `entitlement.has` inside the log's own map chain only re-read it when a row changed, which would
     * leave a screen the user is already looking at showing the free view after a purchase (M18 review).
     */
    @Test
    fun theLogFollowsTheEntitlementAndNotOnlyTheNextRowWritten(): Unit = logOnMain { vm, entitlement ->
        vm.save(row(10_000L, "Bookshelf"))
        advanceUntilIdle()

        assertFalse("a free log showed the tags field", snapshot(vm).pro)

        entitlement.unlock()
        advanceUntilIdle()

        assertTrue("the log kept the free view after Pro was unlocked", vm.state.value.pro)
    }

    /** Delete is immediate now; Undo has to put back the same row, not a copy with a new id and date. */
    @Test
    fun undoPutsTheDeletedRowBackWithEveryField(): Unit = logOnMain { vm, entitlement ->
        entitlement.unlock()
        vm.save(row(10_000L, "Bookshelf").copy(note = "oak", tags = "kitchen"))
        vm.save(row(20_000L, "Desk"))
        advanceUntilIdle()
        val before = snapshot(vm).entries
        val shelf = before.first { it.label == "Bookshelf" }

        assertTrue(vm.deleteNow(shelf))
        advanceUntilIdle()
        assertEquals(1, vm.state.value.totalEntries)

        assertTrue(vm.restore(shelf))
        advanceUntilIdle()
        assertEquals("the log is not back the way it was", before, vm.state.value.entries)
    }

    @Test
    fun deletingTheRowUnderTheEditorClosesTheEditor(): Unit = logOnMain { vm, _ ->
        vm.save(row(10_000L, "Bookshelf"))
        advanceUntilIdle()
        val shelf = snapshot(vm).entries.first()
        vm.openEditor(shelf)

        vm.deleteNow(shelf)
        advanceUntilIdle()

        assertNull(vm.editor)
    }

    /** The ruler screen offers Undo on its own save, so it needs the id of the row it just wrote. */
    @Test
    fun saveAndReturnIdGivesTheRowThatUndoRemoves(): Unit = logOnMain { vm, _ ->
        val id = vm.saveAndReturnId(row(10_000L, "Bookshelf"))
        advanceUntilIdle()
        assertEquals(id, snapshot(vm).entries.single().id)

        assertTrue(vm.deleteSaved(id))
        advanceUntilIdle()
        assertEquals(0, vm.state.value.totalEntries)
        assertFalse("a second undo found a row that was already gone", vm.deleteSaved(id))
    }

    /**
     * The editor's fields live in the ViewModel (so a rotation keeps them) and a save answers whether it
     * wrote — the screen turns that into "Saved" or the reason it could not.
     */
    @Test
    fun theEditorWritesItsFieldsAndFollowsTheStoredRow(): Unit = logOnMain { vm, _ ->
        vm.save(row(10_000L, "Bookshelf"))
        advanceUntilIdle()
        val shelf = snapshot(vm).entries.first()

        vm.openEditor(shelf)
        vm.updateEditor(label = "Shelf", note = "top")
        assertTrue(vm.saveEditor())
        advanceUntilIdle()

        val stored = vm.state.value.entries.first()
        assertEquals("Shelf", stored.label)
        assertEquals("top", stored.note)
        assertEquals(stored, vm.editor?.entry)

        vm.closeEditor()
        assertNull(vm.editor)
    }

    @Test
    fun savingAnEditToARowThatWentAwayReportsIt(): Unit = logOnMain { vm, _ ->
        vm.save(row(10_000L, "Bookshelf"))
        advanceUntilIdle()
        val shelf = snapshot(vm).entries.first()
        vm.openEditor(shelf)
        vm.delete(shelf)
        advanceUntilIdle()

        assertFalse(vm.saveEditor())
    }
}
