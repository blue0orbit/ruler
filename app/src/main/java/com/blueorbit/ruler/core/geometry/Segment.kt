package com.blueorbit.ruler.core.geometry

/**
 * Extend mode stores segments, not just a total (rule 6): a total on its own cannot be audited, and
 * undo has to take away exactly the last measurement. A segment is the length measured on screen
 * between two marks, in micrometres (rule 2), plus the log row it was written to so the audit trail
 * and the running total stay in step when a segment is undone.
 */
data class Segment(val lengthMicrometres: Long, val logId: Long = 0L)

object Segments {

    fun total(segments: List<Segment>): Long = segments.sumOf { it.lengthMicrometres }

    /** Undo removes the last segment only — the earlier ones stay auditable. */
    fun undo(segments: List<Segment>): List<Segment> = segments.dropLast(1)

    /** The segment undo takes away. */
    fun last(segments: List<Segment>): Segment? = segments.lastOrNull()
}
