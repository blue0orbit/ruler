package com.blueorbit.ruler.core.log

import com.blueorbit.ruler.core.entitlement.EntitlementRepository
import com.blueorbit.ruler.core.entitlement.EntitlementRule
import com.blueorbit.ruler.core.entitlement.EntitlementState
import com.blueorbit.ruler.core.entitlement.EntitlementStatus
import com.blueorbit.ruler.core.entitlement.ProFeature
import com.blueorbit.ruler.core.geometry.Segment
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Extend mode writes through `LogRepository`, so the Pro gate is checked here too (rule 10): the
 * segment path must not be a way to store tags without the entitlement.
 */
class SegmentLogTest {

    private class FakeDao : MeasurementDao {
        private val rows = ArrayList<Measurement>()
        private val flow = MutableStateFlow<List<Measurement>>(emptyList())

        override suspend fun insert(measurement: Measurement): Long {
            val row = measurement.copy(id = rows.size + 1L)
            rows += row
            flow.value = rows.sortedByDescending { it.createdAtMillis }
            return row.id
        }

        override suspend fun update(measurement: Measurement): Int = 0
        override suspend fun delete(measurement: Measurement): Int = 0
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

    @Test
    fun segmentKeepsTheProvenanceItWasMeasuredWith(): Unit = runBlocking {
        val dao = FakeDao()
        val log = SegmentLog(LogRepository(dao, FakeEntitlement(false)), clock = { 2_000L })

        val id = log.record(Segment(53_980L), provenance = "CARD", errorBudgetMmPer100Mm = 0.185, label = "segment 1")
        val row = dao.findById(id)!!

        assertEquals(53_980L, row.valueMicrometres)
        assertEquals("CARD", row.source)
        assertEquals(0.185, row.errorBudgetMmPer100Mm, 1e-9)
        assertEquals("segment 1", row.label)
        assertEquals(2_000L, row.createdAtMillis)
    }

    @Test
    fun tagsAreStillGatedOnTheSegmentPath(): Unit = runBlocking {
        val freeDao = FakeDao()
        val free = SegmentLog(LogRepository(freeDao, FakeEntitlement(false)))
        val freeId = free.record(Segment(10_000L), "CARD", 0.185, "segment 1", tags = "kitchen")
        assertEquals("", freeDao.findById(freeId)?.tags)

        val proDao = FakeDao()
        val pro = SegmentLog(LogRepository(proDao, FakeEntitlement(true)))
        val proId = pro.record(Segment(10_000L), "CARD", 0.185, "segment 1", tags = "kitchen")
        assertEquals("kitchen", proDao.findById(proId)?.tags)
    }

    @Test
    fun undoDeletesTheRowForTheSegmentItRemoves(): Unit = runBlocking {
        val dao = FakeDao()
        val log = SegmentLog(LogRepository(dao, FakeEntitlement(false)))

        val first = Segment(10_000L, log.record(Segment(10_000L), "CARD", 0.185, "segment 1"))
        val second = Segment(20_000L, log.record(Segment(20_000L), "CARD", 0.185, "segment 2"))

        assertTrue(log.delete(second))
        assertNull(dao.findById(second.logId))
        assertEquals(10_000L, dao.findById(first.logId)?.valueMicrometres)
        assertEquals(1, dao.count())
    }
}
