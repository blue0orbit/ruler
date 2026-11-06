package com.blueorbit.ruler.feature.calibration

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.blueorbit.ruler.core.calibration.Calibration
import com.blueorbit.ruler.core.calibration.CalibrationDerivation
import com.blueorbit.ruler.core.calibration.CalibrationStore
import com.blueorbit.ruler.core.calibration.Reference
import com.blueorbit.ruler.core.calibration.ReferenceKind
import com.blueorbit.ruler.core.calibration.References
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

data class WizardState(
    val current: Calibration,
    /**
     * The short edge is the default because it is the card edge that fits a phone screen at any
     * density a phone has; the long edge is 1 314 px on a 1 080-px panel. [fitReferenceToViewport]
     * upgrades to the long edge when the screen is wide enough for it, because a longer reference is
     * a better one (M16).
     */
    val reference: Reference = References.cardShortEdge,
    /** Pixel span the user aligned with the reference (card / coin path). */
    val measuredPx: Double = 0.0,
    /** Manual path: what a steel ruler says the drawn segment measures, in mm. */
    val measuredMm: Double = 0.0,
    /**
     * False until the user moves the slider or types a reading. The prefilled values are only the
     * starting position of the slider, so pressing Save on a pristine wizard must not stamp a
     * calibration source the user never used (M11R finding 3, reviewer note 2).
     */
    val aligned: Boolean = false
)

class CalibrationViewModel(
    private val store: CalibrationStore,
    private val guess: Calibration
) : ViewModel() {

    private val _state = MutableStateFlow(WizardState(current = guess))
    val state: StateFlow<WizardState> = _state.asStateFlow()

    /**
     * Set once the calibration has been written. The guided path ends by leaving the screen, so the
     * screen needs to know the write has happened rather than guessing when it will have.
     */
    private val _justSaved = MutableStateFlow<Calibration?>(null)
    val justSaved: StateFlow<Calibration?> = _justSaved.asStateFlow()

    /**
     * One answer per press of Save/Done: true when the write happened, false when the store refused it.
     * A channel rather than a state, so a rotation does not replay "Calibration saved" (or, in the guided
     * path, leave the screen a second time), and an answer that arrives while the screen is being rebuilt
     * waits for it instead of being lost.
     */
    private val _saveOutcomes = Channel<Boolean>(Channel.BUFFERED)
    val saveOutcomes: Flow<Boolean> = _saveOutcomes.receiveAsFlow()

    /** A reference the user chose is never replaced by the one the app thinks fits. */
    private var userChoseReference = false

    /**
     * The width the screen last had to draw in. The reference has to fit it, and the ViewModel needs the
     * width to hand a reference out again: picking another kind changes the reference, and a reference
     * handed out without the width in mind is how the manual path ended up offering a 100 mm drawing on a
     * screen that can only draw 62.7 mm across.
     */
    private var viewportPx = 0.0

    init {
        viewModelScope.launch {
            val current = store.current()
            _state.value = _state.value.copy(
                current = current,
                measuredPx = predictedPx(current, _state.value.reference),
                measuredMm = initialMm(_state.value.reference)
            )
        }
    }

    /** Selecting a kind picks that kind's default reference so the screen stays compact. */
    fun selectKind(kind: ReferenceKind) {
        val reference = when (kind) {
            ReferenceKind.CARD -> References.cardShortEdge
            ReferenceKind.COIN -> References.coins.first()
            ReferenceKind.MANUAL -> References.manual
        }
        // Choosing a kind is not choosing a size. The kind's default is the app's guess again, so it is
        // offered to the fitter rather than marked as the user's own choice.
        userChoseReference = false
        apply(reference)
        fitCurrentReference()
    }

    fun selectReference(reference: Reference) {
        userChoseReference = true
        apply(reference)
    }

    private fun apply(reference: Reference) {
        val current = _state.value
        // A new reference has not been aligned yet, so the flag goes back to false: the prefill is
        // the slider's starting position, not a reading the user took (M11R finding 3).
        _state.value = current.copy(
            reference = reference,
            measuredPx = predictedPx(current.current, reference),
            measuredMm = initialMm(reference),
            aligned = false
        )
    }

    /**
     * Called by the screen with the width it has to draw in. Until the user has picked a reference,
     * the wizard uses the longest card edge that actually fits that width (rule 14: the guided path
     * must not open by telling the user their card is too long). The manual span is fitted whatever
     * the user picked, because a drawing that does not fit the screen cannot be measured at all.
     */
    fun fitReferenceToViewport(viewportPx: Double) {
        this.viewportPx = viewportPx
        fitCurrentReference()
    }

    private fun fitCurrentReference() {
        if (viewportPx <= 0.0) return
        val current = _state.value
        val density = current.current.pixelsPerMm
        val fitted = when (current.reference.kind) {
            // A coin is 16 - 26 mm across, so it always fits. A card edge depends on the screen.
            ReferenceKind.COIN -> current.reference
            ReferenceKind.CARD ->
                if (userChoseReference) current.reference else References.bestCardEdgeFor(density, viewportPx)
            ReferenceKind.MANUAL -> References.manualSpanFor(density, viewportPx)
        }
        if (fitted.id == current.reference.id && fitted.knownMm == current.reference.knownMm) return
        apply(fitted)
    }

    /**
     * The manual path has nothing to prefill: the number in that field is what the user's steel ruler
     * says, and starting it on a number the app guessed invites the user to agree with the guess instead
     * of measuring. Every other path starts its slider on the predicted span, which is guidance.
     */
    private fun initialMm(reference: Reference): Double =
        if (reference.kind == ReferenceKind.MANUAL) 0.0 else reference.knownMm

    fun setMeasuredPx(px: Double) {
        _state.value = _state.value.copy(measuredPx = px, aligned = true)
    }

    /**
     * A typed number is a reading only when there is one in the field: an empty field, or text that is
     * not a number, is not a measurement, and a Done that saves nothing is a dead end.
     */
    fun setMeasuredMm(mm: Double) {
        _state.value = _state.value.copy(measuredMm = mm, aligned = mm > 0.0)
    }

    /** Pure derivation, kept in core/calibration so the maths is testable on its own. */
    fun derivedCalibration(): Calibration = CalibrationDerivation.derive(
        _state.value.current,
        _state.value.reference,
        _state.value.measuredPx,
        _state.value.measuredMm,
        _state.value.aligned
    )

    fun save() {
        val calibration = derivedCalibration()
        // Nothing to save until the user has taken a reading. Saving the untouched prediction would
        // label a density-derived value as a card measurement.
        if (!_state.value.aligned) return
        viewModelScope.launch {
            // A write that failed leaves the wizard where it was, reading still taken, so the user can
            // press Save again — the screen says the calibration was not kept (rule 14).
            val saved = store.save(calibration)
            if (saved) {
                _state.value = _state.value.copy(current = calibration, aligned = false)
                _justSaved.value = calibration
            }
            _saveOutcomes.send(saved)
        }
    }

    private fun predictedPx(calibration: Calibration, reference: Reference): Double =
        calibration.pixelsPerMm * reference.knownMm
}
