package com.blueorbit.ruler.core.geometry

import org.junit.Assert.assertEquals
import org.junit.Test

class SegmentTest {

    @Test
    fun threeSegmentsSumCorrectly() {
        val segments = listOf(Segment(1_000L), Segment(2_000L), Segment(3_000L))
        assertEquals(6_000L, Segments.total(segments))
    }

    @Test
    fun undoRemovesTheLastSegmentOnly() {
        val segments = listOf(Segment(1_000L), Segment(2_000L), Segment(3_000L))
        val afterUndo = Segments.undo(segments)
        assertEquals(listOf(Segment(1_000L), Segment(2_000L)), afterUndo)
        assertEquals(3_000L, Segments.total(afterUndo))
    }

    @Test
    fun undoOnNoSegmentsLeavesNothingToRemove() {
        assertEquals(emptyList<Segment>(), Segments.undo(emptyList()))
        assertEquals(null, Segments.last(emptyList()))
    }

    @Test
    fun lastIsTheSegmentUndoTakesAway() {
        val segments = listOf(Segment(1_000L, 1L), Segment(2_000L, 2L))
        assertEquals(Segment(2_000L, 2L), Segments.last(segments))
    }
}
