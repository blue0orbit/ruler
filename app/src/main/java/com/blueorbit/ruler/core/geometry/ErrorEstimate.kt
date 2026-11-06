package com.blueorbit.ruler.core.geometry

/**
 * The ± shown for extend mode (rule 7). It is an estimate, never a guarantee, and it must widen as
 * segments accumulate: `n × e_segment` plus the calibration term scaled to the accumulated length.
 *
 * The alignment term is the dominant one — each segment depends on how accurately the user placed
 * the mark and slid the device, and that error does not cancel out over segments. The calibration
 * term is the device's own error budget, which grows with the total length measured.
 */
object ErrorEstimate {

    /**
     * How accurately a mark can be placed and the device slid in one segment. This is a stated
     * assumption, not a measurement — the UI labels the bound as an estimate.
     */
    const val DEFAULT_ALIGNMENT_MM_PER_SEGMENT = 0.5

    /** Bound in millimetres for `segmentCount` segments totalling `totalMicrometres`. */
    fun boundMm(
        segmentCount: Int,
        totalMicrometres: Long,
        calibrationMmPer100Mm: Double,
        alignmentMmPerSegment: Double = DEFAULT_ALIGNMENT_MM_PER_SEGMENT
    ): Double {
        val alignment = segmentCount * alignmentMmPerSegment
        val calibration = totalMicrometres.toDouble() / Physical.MICROMETRES_PER_MM / 100.0 * calibrationMmPer100Mm
        return alignment + calibration
    }
}
