package com.blueorbit.ruler.core.calibration

/** Where the pixels-per-mm figure came from. DEVICE_DEFAULT is a guess, never trusted (rule 4). */
enum class CalibrationSource {
    DEVICE_DEFAULT,
    CARD,
    COIN,
    MANUAL
}

data class Calibration(
    val pixelsPerMm: Double,
    val source: CalibrationSource,
    /** Error budget for the current method, expressed per 100 mm — surfaced in the UI (rule 5). */
    val errorBudgetMmPer100Mm: Double,
    /**
     * Bezel inset: how far the drawable edge is from the physical device edge (a case or bezel
     * moves the zero). Stored per device like the density, so it is data and not a code default
     * (rule 4). 0.0 means the start line is the physical edge.
     */
    val bezelInsetMm: Double = 0.0
) {
    val verified: Boolean get() = source != CalibrationSource.DEVICE_DEFAULT
}

/**
 * M0 stub. M2 replaces this with a DataStore implementation keyed by Build.MODEL + resolution.
 */
interface CalibrationStore {
    suspend fun current(): Calibration

    /**
     * Returns false when the write did not happen. A calibration the user just lined up is the one value
     * in the app they spent effort on, so a failed write has to be said out loud rather than swallowed —
     * and it must never throw out of a `viewModelScope.launch`, where it would close the app.
     */
    suspend fun save(calibration: Calibration): Boolean

    /**
     * Settings management (M9): removing the stored value returns the labelled guess, never a silent
     * default. Returns false when the stored value could not be removed, for the same reason as [save].
     */
    suspend fun clear(): Boolean
}

class InMemoryCalibrationStore(private val guess: Calibration) : CalibrationStore {
    private var value = guess

    override suspend fun current(): Calibration = value

    override suspend fun save(calibration: Calibration): Boolean {
        value = calibration
        return true
    }

    override suspend fun clear(): Boolean {
        value = guess
        return true
    }
}
