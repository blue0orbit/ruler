package com.blueorbit.ruler.core.geometry

/**
 * Physical geometry is computed in pixels (DECISIONS.md rule 1): px_per_mm = dpi_true / 25.4,
 * where dpi_true comes from calibration — never from densityDpi.
 *
 * Font scale and display size must not change this value; labels scale separately from geometry.
 */
class PixelPerMm(val pixelsPerMm: Double) {

    fun mmToPixels(mm: Double): Double = mm * pixelsPerMm

    fun micrometresToPixels(micrometres: Long): Double =
        micrometres * pixelsPerMm / Physical.MICROMETRES_PER_MM

    fun pixelsToMm(pixels: Double): Double = pixels / pixelsPerMm
}
