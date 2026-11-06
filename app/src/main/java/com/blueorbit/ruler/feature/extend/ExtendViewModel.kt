package com.blueorbit.ruler.feature.extend

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.blueorbit.ruler.core.calibration.Calibration
import com.blueorbit.ruler.core.calibration.CalibrationSource
import com.blueorbit.ruler.core.calibration.CalibrationStore
import com.blueorbit.ruler.core.entitlement.EntitlementRepository
import com.blueorbit.ruler.core.geometry.ErrorEstimate
import com.blueorbit.ruler.core.geometry.Segment
import com.blueorbit.ruler.core.geometry.Segments
import com.blueorbit.ruler.core.log.SegmentLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ExtendState(
    /** Segments in the order they were added, each with the log row it was written to (rule 6). */
    val segments: List<Segment> = emptyList(),
    val pro: Boolean = false,
    val calibration: Calibration = Calibration(0.0, CalibrationSource.DEVICE_DEFAULT, 0.0)
)

class ExtendViewModel(
    private val segmentLog: SegmentLog,
    private val entitlement: EntitlementRepository,
    private val calibrationStore: CalibrationStore,
    private val alignmentMmPerSegment: Double = ErrorEstimate.DEFAULT_ALIGNMENT_MM_PER_SEGMENT
) : ViewModel() {

    private val _state = MutableStateFlow(ExtendState())
    val state: StateFlow<ExtendState> = _state.asStateFlow()

    init {
        // The entitlement is followed, not read once. Billing answers asynchronously after launch, and a
        // one-shot read made in the first second showed a paying customer the locked screen until they
        // left and came back.
        viewModelScope.launch {
            entitlement.state.collect { _state.value = _state.value.copy(pro = it.unlocked) }
        }
        refresh()
    }

    /** Re-read on screen entry so a calibration saved elsewhere shows up without a restart. */
    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(calibration = calibrationStore.current())
        }
    }

    /**
     * Mark: the length measured on screen from the start line to the mark, in micrometres. The app
     * cannot know how far the device was slid between segments — the user marks what the screen
     * measured, which is why the bound is an estimate (rule 7).
     */
    fun mark(micrometres: Long) {
        if (micrometres <= 0L) return
        val calibration = _state.value.calibration
        viewModelScope.launch {
            val segment = Segment(micrometres)
            val id = segmentLog.record(
                segment = segment,
                provenance = calibration.source.name,
                errorBudgetMmPer100Mm = calibration.errorBudgetMmPer100Mm,
                label = ""
            )
            _state.value = _state.value.copy(segments = _state.value.segments + segment.copy(logId = id))
        }
    }

    /** Undo removes the last segment only, and its log row with it. */
    fun undo() {
        val last = Segments.last(_state.value.segments) ?: return
        viewModelScope.launch {
            segmentLog.delete(last)
            _state.value = _state.value.copy(segments = Segments.undo(_state.value.segments))
        }
    }

    fun totalMicrometres(): Long = Segments.total(_state.value.segments)

    /** The ± shown on screen. It grows with segment count (rule 7). */
    fun boundMm(): Double = ErrorEstimate.boundMm(
        segmentCount = _state.value.segments.size,
        totalMicrometres = totalMicrometres(),
        calibrationMmPer100Mm = _state.value.calibration.errorBudgetMmPer100Mm,
        alignmentMmPerSegment = alignmentMmPerSegment
    )
}
