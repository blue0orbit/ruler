package com.blueorbit.ruler.core.calibration

/** A physical reference the user can trust: its size is a published spec, not a guess. */
enum class ReferenceKind { CARD, COIN, MANUAL }

/**
 * How the measured span is drawn (M16). The guided path asks the user to make a drawn shape match a
 * real object, so the drawing has to be that object's shape: lining the outline of a card up with a
 * card is something anyone can do, lining up a line of unstated length is not.
 */
enum class ReferenceShape { BAR, RECTANGLE, CIRCLE }

data class Reference(
    val id: String,
    val kind: ReferenceKind,
    val knownMm: Double,
    /** Tolerance of the reference itself, in mm. */
    val toleranceMm: Double,
    val shape: ReferenceShape = ReferenceShape.BAR,
    /**
     * The measured edge divided by the other edge, so the drawn outline is `measuredPx / aspectRatio`
     * deep: 85.60 / 53.98 when the long edge is the one being matched, its reciprocal when the short
     * edge is. 1.0 for a circle, where there is no other edge.
     */
    val aspectRatio: Double = 1.0
) {
    /** Error budget per 100 mm contributed by this reference. */
    fun errorBudgetMmPer100Mm(): Double = toleranceMm / knownMm * 100.0

    /** Calibration derived from a measured pixel span that matches this reference. */
    fun pixelsPerMm(measuredPx: Double): Double = measuredPx / knownMm

    /**
     * How deep the drawn outline has to be so it is the same shape as the real object at the same
     * scale. A bar has no depth of its own and takes the span.
     */
    fun outlineDepthPx(measuredPx: Double): Double =
        if (aspectRatio <= 0.0) measuredPx else measuredPx / aspectRatio
}

object References {

    /** ISO/IEC 7810 ID-1 payment card: 85.60 x 53.98 mm. */
    val cardLongEdge = Reference(
        id = "card_long",
        kind = ReferenceKind.CARD,
        knownMm = 85.60,
        toleranceMm = 0.10,
        shape = ReferenceShape.RECTANGLE,
        aspectRatio = 85.60 / 53.98
    )
    val cardShortEdge = Reference(
        id = "card_short",
        kind = ReferenceKind.CARD,
        knownMm = 53.98,
        toleranceMm = 0.10,
        shape = ReferenceShape.RECTANGLE,
        aspectRatio = 53.98 / 85.60
    )

    /** Coin table, diameters in mm from published mint specs. A coin is matched as a circle. */
    val coins: List<Reference> = listOf(
        coin("eur_1c", 16.25),
        coin("eur_5c", 18.03),
        coin("eur_10c", 19.75),
        coin("eur_20c", 22.25),
        coin("eur_50c", 24.25),
        coin("eur_1", 23.25),
        coin("eur_2", 25.75),
        coin("gbp_1p", 20.30),
        coin("gbp_5p", 18.00),
        coin("gbp_10p", 24.50),
        coin("gbp_1", 23.43),
        coin("usd_penny", 19.05),
        coin("usd_nickel", 21.21),
        coin("usd_dime", 17.91),
        coin("usd_quarter", 24.26)
    )

    /** Manual path: the user measures a drawn segment with a steel ruler. */
    val manual = Reference("manual", ReferenceKind.MANUAL, 100.0, 0.5)

    private fun coin(id: String, diameterMm: Double) =
        Reference(id, ReferenceKind.COIN, diameterMm, 0.10, ReferenceShape.CIRCLE)

    /**
     * The reference the guided path opens with (M16): the longest card edge that still fits the width
     * the wizard has to draw in, at the current density estimate. A card's long edge is 1 314 px on a
     * 1 080-px panel, so opening with it makes the first sentence the user reads "your card is too
     * long for this screen" — a dead end (rule 14) before a single reading has been taken.
     */
    fun bestCardEdgeFor(pixelsPerMm: Double, viewportPx: Double): Reference {
        if (pixelsPerMm <= 0.0 || viewportPx <= 0.0) return cardShortEdge
        return if (cardLongEdge.knownMm * pixelsPerMm <= viewportPx) cardLongEdge else cardShortEdge
    }

    /**
     * The manual path draws a segment and asks the user to measure *that drawing* with a steel ruler,
     * so the drawing has to fit the screen it is drawn on. With the reference fixed at 100 mm the
     * segment was 1 535 px on a 1 080-px panel: the wizard reported "too long", drew nothing, and the
     * manual path could not be completed on any phone (M16). The span is therefore the nicest round
     * number of millimetres that still fits, and the reference carries that span so the derivation,
     * the error budget and the drawing all agree.
     */
    fun manualSpanFor(pixelsPerMm: Double, viewportPx: Double): Reference {
        if (pixelsPerMm <= 0.0 || viewportPx <= 0.0) return manual
        val span = MANUAL_SPANS.firstOrNull { it * pixelsPerMm <= viewportPx } ?: MANUAL_SPANS.last()
        return manual.copy(knownMm = span)
    }

    private val MANUAL_SPANS = listOf(100.0, 80.0, 60.0, 50.0, 40.0, 25.0)
}
