package com.blueorbit.ruler.core.di

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.blueorbit.ruler.feature.calibration.CalibrationViewModel
import com.blueorbit.ruler.feature.extend.ExtendViewModel
import com.blueorbit.ruler.feature.level.LevelViewModel
import com.blueorbit.ruler.feature.level.ProtractorViewModel
import com.blueorbit.ruler.feature.log.LogViewModel
import com.blueorbit.ruler.feature.pro.PurchaseViewModel
import com.blueorbit.ruler.feature.ruler.RulerViewModel
import com.blueorbit.ruler.feature.ringsize.RingSizeViewModel
import com.blueorbit.ruler.feature.settings.SettingsViewModel

/**
 * ViewModels are constructed here from the container — no reflection, no framework.
 * Tests construct ViewModels directly with fakes instead of going through this factory.
 */
@Suppress("UNCHECKED_CAST")
class ViewModelFactory(private val container: AppContainer) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(RulerViewModel::class.java)) {
            // The device estimate seeds the screen so it never has to draw geometry from a
            // pixels-per-mm of zero while the stored calibration is still being read.
            return RulerViewModel(
                container.calibrationStore,
                container.settingsStore,
                container.settings,
                container.guess
            ) as T
        }
        if (modelClass.isAssignableFrom(CalibrationViewModel::class.java)) {
            return CalibrationViewModel(container.calibrationStore, container.guess) as T
        }
        if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
            return SettingsViewModel(
                container.entitlementRepository,
                container.calibrationStore,
                container.settingsStore,
                container.settings,
                container.deviceKey
            ) as T
        }
        if (modelClass.isAssignableFrom(LogViewModel::class.java)) {
            return LogViewModel(container.logRepository, container.entitlementRepository) as T
        }
        if (modelClass.isAssignableFrom(ExtendViewModel::class.java)) {
            return ExtendViewModel(
                container.segmentLog,
                container.entitlementRepository,
                container.calibrationStore
            ) as T
        }
        if (modelClass.isAssignableFrom(LevelViewModel::class.java)) {
            return LevelViewModel(container.gravitySource, container.entitlementRepository, container.levelZeroStore) as T
        }
        if (modelClass.isAssignableFrom(ProtractorViewModel::class.java)) {
            return ProtractorViewModel(container.gravitySource, container.entitlementRepository) as T
        }
        if (modelClass.isAssignableFrom(RingSizeViewModel::class.java)) {
            return RingSizeViewModel(
                container.calibrationStore,
                container.logRepository,
                // Seeded with the device estimate for the same reason the ruler screen is: the circle
                // and its slider range are derived by dividing by pixels-per-mm.
                container.guess
            ) as T
        }
        if (modelClass.isAssignableFrom(PurchaseViewModel::class.java)) {
            return PurchaseViewModel(container.entitlementRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel: ${modelClass.name}")
    }
}
