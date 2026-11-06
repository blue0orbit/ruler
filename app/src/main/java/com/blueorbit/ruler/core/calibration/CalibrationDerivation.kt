package com.blueorbit.ruler.core.calibration

/**
 * Pure derivation from a reference reading (DECISIONS.md rule 3: the maths stays pure and tested,
 * separate from storage and from the ViewModel).
 */
object CalibrationDerivation {

    fun sourceFor(kind: ReferenceKind): CalibrationSource = when (kind) {
        ReferenceKind.CARD -> CalibrationSource.CARD
        ReferenceKind.COIN -> CalibrationSource.COIN
        ReferenceKind.MANUAL -> CalibrationSource.MANUAL
    }

    /**
     * Card / coin: the known physical size divided by the pixel span the user aligned with it.
     * Manual: the drawn segment is fixed in pixels, so the reading the user takes with their own ruler
     * decides the millimetres.
     * A zero reading never produces a calibration — it returns the current value unchanged.
     *
     * `aligned` is false until the user has actually moved the slider or typed a reading. Without it
     * the wizard's prefilled prediction was saved as if the user had aligned a card, which stamped
     * `source = CARD` / `verified = true` on a value derived from `densityDpi` (M11R finding 3,
     * reviewer note 2). Nothing is derived from a reading the user never took.
     *
     * The stored bezel inset is carried through rather than reset (M11R finding 9): re-calibrating
     * the density must not move the start line the user already configured.
     */
    fun derive(
        current: Calibration,
        reference: Reference,
        measuredPx: Double,
        measuredMm: Double,
        aligned: Boolean
    ): Calibration {
        if (!aligned) return current

        val pixelsPerMm = when (reference.kind) {
            ReferenceKind.MANUAL ->
                if (measuredMm <= 0.0) null else current.pixelsPerMm * reference.knownMm / measuredMm
            else ->
                if (measuredPx <= 0.0) null else reference.pixelsPerMm(measuredPx)
        } ?: return current

        return Calibration(
            pixelsPerMm = pixelsPerMm,
            source = sourceFor(reference.kind),
            errorBudgetMmPer100Mm = reference.errorBudgetMmPer100Mm(),
            bezelInsetMm = current.bezelInsetMm
        )
    }
}
