package com.blueorbit.ruler.feature.area

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.blueorbit.ruler.core.calibration.Calibration
import com.blueorbit.ruler.core.calibration.CalibrationStore
import com.blueorbit.ruler.core.geometry.AreaUnit
import com.blueorbit.ruler.core.geometry.Rectangle
import com.blueorbit.ruler.core.geometry.Shapes
import com.blueorbit.ruler.core.log.LogRepository
import com.blueorbit.ruler.core.log.Measurement
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The two shapes M22 draws on the plate (ROADMAP M22). A circle is the second shape because a round
 * object is measured the same way — laid against the start line and sized against a drawn outline — and
 * it needs only one of the two dimensions, which is why it shares the plate and the drag rather than
 * bringing a third instrument of its own.
 */
enum class AreaShape { RECTANGLE, CIRCLE }

data class AreaState(
    /** No default on purpose, the same rule as `RulerState` and `RingSizeState`: every number below is
     *  derived by dividing by `pixelsPerMm`, so a placeholder of 0.0 is impossible geometry. */
    val calibration: Calibration,
    val shape: AreaShape = AreaShape.RECTANGLE,
    /** The unit the area is read out and saved in. The sides follow it ([AreaUnit.lengthUnit]). */
    val unit: AreaUnit = AreaUnit.MM2,
    /**
     * What the plate drew along the ruler: the width of a rectangle, the **diameter** of a circle. One
     * field for one drawn thing, so the drawing, the number, the area and the saved row cannot disagree
     * about which drawing they describe (rule 5).
     */
    val widthMicrometres: Long = 0L,
    /** What the plate drew down the plate. Unused for a circle. */
    val heightMicrometres: Long = 0L,
    /**
     * False until the user has drawn something. An area read off a prefilled rectangle would be the
     * M11R finding-3 bug again — a value nobody measured, stamped as a reading.
     */
    val aligned: Boolean = false,
    /**
     * What this window can draw *now*. Kept apart from the drawing for the M21 reason: a rotation or a
     * fold makes the plate shorter, and the drawing the user made must survive that rather than be
     * quietly shortened.
     */
    val plateLimitWidthMicrometres: Long = 0L,
    val plateLimitHeightMicrometres: Long = 0L
)

class AreaViewModel(
    private val calibrationStore: CalibrationStore,
    private val logRepository: LogRepository,
    deviceGuess: Calibration,
    private val clock: () -> Long = { System.currentTimeMillis() }
) : ViewModel() {

    private val _state = MutableStateFlow(AreaState(calibration = deviceGuess))
    val state: StateFlow<AreaState> = _state.asStateFlow()

    init {
        refresh()
    }

    /** Re-read on entry, so a calibration made on the wizard shows here without a restart. */
    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(calibration = calibrationStore.current())
        }
    }

    /**
     * Switching shape changes what the drawn width *means* — 200 mm of rectangle is not a 200 mm circle —
     * so the drawing is cleared rather than reinterpreted, exactly as the ring screen clears it between
     * the ring method and the paper method.
     */
    fun setShape(shape: AreaShape) {
        _state.value = _state.value.copy(shape = shape, widthMicrometres = 0L, heightMicrometres = 0L, aligned = false)
    }

    fun setUnit(unit: AreaUnit) {
        _state.value = _state.value.copy(unit = unit)
    }

    /** The plate drew a corner at (`widthMicrometres`, `heightMicrometres`). Clamped here, once. */
    fun setCorner(widthMicrometres: Long, heightMicrometres: Long, maxW: Long, maxH: Long) {
        val width = widthMicrometres.coerceIn(0L, maxW)
        val height = heightMicrometres.coerceIn(0L, maxH)
        _state.value = _state.value.copy(
            widthMicrometres = width,
            heightMicrometres = height,
            aligned = isClaimed(width, height),
            plateLimitWidthMicrometres = maxW,
            plateLimitHeightMicrometres = maxH
        )
    }

    /** One slider moved on its own; the other side keeps whatever the finger last drew. */
    fun setWidth(micrometres: Long, maxW: Long, maxH: Long) =
        setCorner(micrometres, _state.value.heightMicrometres, maxW, maxH)

    fun setHeight(micrometres: Long, maxW: Long, maxH: Long) =
        setCorner(_state.value.widthMicrometres, micrometres, maxW, maxH)

    /**
     * The plate measured itself. Called whenever the window changes, so the drawing, the area and the
     * saved row describe a shape this screen can actually draw, while what the user set stays as it was.
     */
    fun setPlateLimits(maxW: Long, maxH: Long) {
        val current = _state.value
        if (current.plateLimitWidthMicrometres == maxW && current.plateLimitHeightMicrometres == maxH) return
        _state.value = current.copy(plateLimitWidthMicrometres = maxW, plateLimitHeightMicrometres = maxH)
    }

    val drawableWidthMicrometres: Long
        get() = _state.value.widthMicrometres.coerceIn(0L, _state.value.plateLimitWidthMicrometres)

    val drawableHeightMicrometres: Long
        get() = _state.value.heightMicrometres.coerceIn(0L, _state.value.plateLimitHeightMicrometres)

    val isClaimed: Boolean
        get() = isClaimed(drawableWidthMicrometres, drawableHeightMicrometres)

    private fun isClaimed(width: Long, height: Long): Boolean =
        if (_state.value.shape == AreaShape.CIRCLE) width > 0L else width > 0L && height > 0L

    val areaSquareMicrometres: Long
        get() = if (_state.value.shape == AreaShape.CIRCLE) {
            Shapes.circleAreaSquareMicrometres(drawableWidthMicrometres)
        } else {
            Shapes.rectangleArea(drawableWidthMicrometres, drawableHeightMicrometres)
        }

    /** The edge all the way round: the perimeter of the rectangle, the circumference of the circle. */
    val edgeMicrometres: Long
        get() = if (_state.value.shape == AreaShape.CIRCLE) {
            Shapes.circleCircumferenceMicrometres(drawableWidthMicrometres)
        } else {
            Shapes.rectanglePerimeter(drawableWidthMicrometres, drawableHeightMicrometres)
        }

    /** The rectangle the plate drew, for anything that needs both sides and their area at once. */
    fun rectangle(): Rectangle = Rectangle(drawableWidthMicrometres, drawableHeightMicrometres)

    /**
     * Rule 5 for an area. Both sides carry the ruler's own error budget, and an area is a product of two
     * measured lengths, so its relative error is about **twice** the side's — the reason a screen that is
     * 0.2 % off on a side is 0.4 % off on the area. It is stated as a percentage of the area rather than
     * a millimetre, because an area has no millimetres.
     */
    fun areaUncertaintyPercent(): Double = 2.0 * _state.value.calibration.errorBudgetMmPer100Mm

    /**
     * Save the **area** with the shape and the sides in its name (ROADMAP M22: "save to log with the unit
     * and the shape"). The value is square micrometres and the unit names that, so a row read back months
     * later says what kind of number it is rather than looking like a length (see [AreaUnit]).
     */
    suspend fun save(label: String): Long {
        if (!isClaimed) return 0L
        val calibration = _state.value.calibration
        return logRepository.save(
            Measurement(
                valueMicrometres = areaSquareMicrometres,
                unit = _state.value.unit.name,
                label = label,
                note = "",
                source = calibration.source.name,
                errorBudgetMmPer100Mm = calibration.errorBudgetMmPer100Mm,
                // Stamped here, not left at 0: the log sorts by date, and a row dated 1 January 1970
                // reads as a broken row rather than a new one (seen on the Fold at M21).
                createdAtMillis = clock()
            )
        )
    }

    /** Undo on the saved row, the same way the ruler and ring screens undo their own save. */
    suspend fun deleteSaved(id: Long) {
        logRepository.deleteById(id)
    }
}
