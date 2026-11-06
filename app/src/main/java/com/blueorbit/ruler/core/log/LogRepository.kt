package com.blueorbit.ruler.core.log

import com.blueorbit.ruler.core.entitlement.EntitlementRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * What one read of the stored log answers for the Log screen: the rows this search shows, and how many
 * rows there are in total. They are one read rather than two because the export gate needs the second
 * number and the export needs the rows, and two reads of the same table can disagree.
 */
data class LogView(
    val entries: List<Measurement> = emptyList(),
    val totalEntries: Int = 0
)

/**
 * The repository is the seam: the DAO is an interface, so the search, edit, delete and Pro-tag
 * rules are testable without a database. Room itself is covered by the instrumented DAO test.
 */
class LogRepository(
    private val dao: MeasurementDao,
    private val entitlement: EntitlementRepository
) {

    /** The filtered view and the whole-log count, from one subscription to the stored log. */
    fun observeView(query: String): Flow<LogView> =
        dao.observeAll().map { rows ->
            LogView(entries = rows.filter { LogSearch.matches(it, query) }, totalEntries = rows.size)
        }

    /** The stored log, unfiltered. Export is always the whole log, so it never goes through the search. */
    suspend fun allRows(): List<Measurement> = dao.observeAll().first()

    suspend fun save(measurement: Measurement): Long = dao.insert(gated(measurement))

    suspend fun edit(measurement: Measurement): Boolean = dao.update(gated(measurement)) > 0

    suspend fun delete(measurement: Measurement): Boolean = dao.delete(measurement) > 0

    /**
     * Undo for [delete]: the row goes back exactly as it was — same id, same date, same tags — so it lands
     * in the same place in the log and anything holding its id still finds it. Not gated: the row is not a
     * new write but the one the user had a moment ago, and its tags passed the gate when they were written.
     * Stripping them here would make "Undo" quietly lose part of what it promised to put back.
     */
    suspend fun restore(measurement: Measurement): Long = dao.insert(measurement)

    /** Used by extend mode undo, which knows the segment's row id rather than the whole row. */
    suspend fun deleteById(id: Long): Boolean = dao.deleteById(id) > 0

    suspend fun count(): Int = dao.count()

    private fun gated(measurement: Measurement) = TagGate.stripIfNotEntitled(entitlement, measurement)
}
