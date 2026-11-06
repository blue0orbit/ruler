package com.blueorbit.ruler.core.log

import com.blueorbit.ruler.core.geometry.MeasurementUnit
import com.blueorbit.ruler.core.geometry.Segment

/**
 * Each extend segment is written as its own log row so the total can be audited (rule 6): a total
 * with no segments behind it has no provenance. The row carries the calibration method and the error
 * budget at the moment the segment was recorded (rule 5), so a segment measured on an unverified
 * guess still says so when it is read back.
 *
 * The Pro gate is not bypassed here — segments go through `LogRepository`, which is where tags are
 * dropped for free users (rule 10).
 */
class SegmentLog(
    private val log: LogRepository,
    private val clock: () -> Long = { System.currentTimeMillis() }
) {

    /** `provenance` is the calibration method, e.g. `CARD`, as it was when the segment was marked. */
    suspend fun record(
        segment: Segment,
        provenance: String,
        errorBudgetMmPer100Mm: Double,
        label: String,
        note: String = "",
        tags: String = ""
    ): Long {
        val row = Measurement(
            valueMicrometres = segment.lengthMicrometres,
            unit = MeasurementUnit.MM.name,
            label = label,
            note = note,
            source = provenance,
            errorBudgetMmPer100Mm = errorBudgetMmPer100Mm,
            createdAtMillis = clock(),
            tags = tags
        )
        return log.save(row)
    }

    /** Undo deletes the row for the segment it removes, so the log and the total stay in step. */
    suspend fun delete(segment: Segment): Boolean = log.deleteById(segment.logId)
}
