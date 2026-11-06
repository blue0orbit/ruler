package com.blueorbit.ruler.feature.ruler

import com.blueorbit.ruler.core.geometry.Physical
import com.blueorbit.ruler.core.geometry.PixelPerMm

/**
 * The zero of the ruler is the start line, not pixel 0. A case or bezel moves the physical edge,
 * so the inset is stored per device (it is data, not a code default — rule 4) and the start line
 * is drawn at the inset. The user has to know which edge they aligned the object against, because
 * aligning against the physical edge while the zero is drawn at the inset shifts every reading by
 * the inset (PLAN M4: documented bezel inset option).
 */
data class StartEdge(
    val insetMm: Double,
    val pxPerMm: PixelPerMm
) {

    /** Where the start line is drawn, in raw device pixels. */
    fun zeroPixels(): Double = pxPerMm.mmToPixels(insetMm)

    /** Length measurable from the start line to the far end of the viewport. */
    fun measurableMicrometres(viewportPixels: Double): Long {
        val usable = viewportPixels - zeroPixels()
        if (usable <= 0.0) return 0L
        return Math.round(usable / pxPerMm.pixelsPerMm * Physical.MICROMETRES_PER_MM)
    }

    /** Reading for an object that starts on the start line and ends at `endPixels`. */
    fun measureAt(endPixels: Double): Long =
        Math.round((endPixels - zeroPixels()) / pxPerMm.pixelsPerMm * Physical.MICROMETRES_PER_MM)
}
