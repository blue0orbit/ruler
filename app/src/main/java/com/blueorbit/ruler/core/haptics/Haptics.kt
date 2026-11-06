package com.blueorbit.ruler.core.haptics

/**
 * Tick-crossing detection, kept pure so it is testable. The reading changes when the measured value
 * changes; a haptic fires only when that change crosses a tick boundary, not on every recomposition,
 * and it can be disabled in settings (rule 10: the preference is respected, not overridden).
 */
object Haptics {

    fun crossedTick(previousMicrometres: Long, currentMicrometres: Long, tickStepMicrometres: Long): Boolean {
        if (tickStepMicrometres <= 0L) return false
        val previousTick = Math.floorDiv(previousMicrometres, tickStepMicrometres)
        val currentTick = Math.floorDiv(currentMicrometres, tickStepMicrometres)
        return previousTick != currentTick
    }

    fun shouldFire(enabled: Boolean, previousMicrometres: Long, currentMicrometres: Long, tickStepMicrometres: Long): Boolean =
        enabled && crossedTick(previousMicrometres, currentMicrometres, tickStepMicrometres)
}

/** The device side of haptics. `VIBRATE` is the only permission this needs — reason recorded in
 * PROGRESS.md (M9): a physical tick feedback needs the vibrator, and it is a normal permission that
 * the user is never prompted for. */
class DeviceHaptics(private val vibrator: android.os.Vibrator?) {

    /**
     * `EFFECT_TICK` and `createPredefined` are API 29, so the call is guarded rather than assumed —
     * minSdk is 26, and a device between 26 and 28 has no predefined effects.
     */
    fun tick() {
        val device = vibrator ?: return
        if (!device.hasVibrator()) return

        if (android.os.Build.VERSION.SDK_INT >= 29) {
            device.vibrate(android.os.VibrationEffect.createPredefined(android.os.VibrationEffect.EFFECT_TICK))
        } else {
            device.vibrate(android.os.VibrationEffect.createOneShot(10, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }

    /**
     * Confirmation for a discrete action — save reading, mark, undo, save calibration (DESIGN.md §10).
     * Guarded exactly like [tick]: `EFFECT_CLICK` is API 29, minSdk is 26.
     *
     * It never fires on a continuous value: a confirmation haptic that fires every frame is noise, and
     * the caller gates it on `AppSettings.haptics` like the tick does (rule 10).
     */
    fun confirm() {
        val device = vibrator ?: return
        if (!device.hasVibrator()) return

        if (android.os.Build.VERSION.SDK_INT >= 29) {
            device.vibrate(android.os.VibrationEffect.createPredefined(android.os.VibrationEffect.EFFECT_CLICK))
        } else {
            device.vibrate(android.os.VibrationEffect.createOneShot(10, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }
}
