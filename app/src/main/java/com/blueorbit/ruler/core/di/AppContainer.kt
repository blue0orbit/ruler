package com.blueorbit.ruler.core.di

import com.blueorbit.ruler.core.ads.AdMobAds
import com.blueorbit.ruler.core.ads.Ads

import android.content.Context
import android.os.Build
import android.os.Vibrator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.datastore.core.DataMigration
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.blueorbit.ruler.core.calibration.Calibration
import com.blueorbit.ruler.core.calibration.CalibrationStore
import com.blueorbit.ruler.core.calibration.DataStoreCalibrationStore
import com.blueorbit.ruler.core.calibration.DeviceDefaults
import com.blueorbit.ruler.core.calibration.DataStoreLevelZeroStore
import com.blueorbit.ruler.core.calibration.DeviceKey
import com.blueorbit.ruler.core.calibration.LevelZeroStore
import com.blueorbit.ruler.BuildConfig
import com.blueorbit.ruler.core.entitlement.BillingEntitlementRepository
import com.blueorbit.ruler.core.entitlement.EntitlementRepository
import com.blueorbit.ruler.core.entitlement.FakeEntitlementRepository
import com.blueorbit.ruler.core.log.AppDatabase
import com.blueorbit.ruler.core.log.LogRepository
import com.blueorbit.ruler.core.log.SegmentLog
import com.blueorbit.ruler.core.haptics.DeviceHaptics
import com.blueorbit.ruler.core.settings.AppSettings
import com.blueorbit.ruler.core.settings.DataStoreSettingsStore
import com.blueorbit.ruler.core.settings.SettingsStore
import com.blueorbit.ruler.core.sensors.GravitySource
import com.blueorbit.ruler.core.sensors.SensorManagerGravitySource
import java.io.File

/**
 * Manual DI (DECISIONS.md): one small object graph, no Hilt, no annotation processor.
 * ViewModels receive interfaces, so tests construct them directly.
 */
class AppContainer(context: Context) {

    private val metrics = context.resources.displayMetrics

    val guess: Calibration = DeviceDefaults.guess(context)

    /** Keyed by Build.MODEL + resolution so a device swap never reuses another device's value. */
    val deviceKey: String = DeviceKey.build(Build.MODEL, metrics.widthPixels, metrics.heightPixels)

    /**
     * Per-device values only: calibration and the level zero, both keyed by [deviceKey]. The file is
     * excluded from backup and device transfer (`res/xml/backup_rules.xml`,
     * `res/xml/data_extraction_rules.xml`) — a calibration describes one physical screen, and
     * restoring it onto another phone would show a "measured" value that was never measured there
     * (rule 4).
     */
    private val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
        produceFile = {
            val dir = File(context.filesDir, "datastore")
            dir.mkdirs()
            File(dir, CALIBRATION_FILE)
        }
    )

    /**
     * The user's preferences, in their own file so they *are* backed up while the calibration is not.
     * Until this split both shared the calibration file, which made "keep the settings, drop the
     * calibration" impossible to say in the backup rules. Existing installs keep their choices: the
     * first read copies them across ([SettingsSplitMigration]).
     */
    private val settingsDataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
        migrations = listOf(SettingsSplitMigration(dataStore)),
        produceFile = {
            val dir = File(context.filesDir, "datastore")
            dir.mkdirs()
            File(dir, SETTINGS_FILE)
        }
    )

    val calibrationStore: CalibrationStore = DataStoreCalibrationStore(dataStore, deviceKey, guess)

    /**
     * M11R finding 19: the level zero is data the user created on a surface, so it survives the
     * process instead of resetting to "not zeroed" on every launch.
     */
    val levelZeroStore: LevelZeroStore = DataStoreLevelZeroStore(dataStore, deviceKey)

    /**
     * M11: the entitlement implementation is chosen in exactly one place. The shipped build uses Play
     * Billing; a debug build gets the fake so the Pro screens can be exercised without a purchase
     * (HUMAN_CHECKS.md). `BuildConfig.DEBUG` is false in the release build, so the fake is never
     * reachable in a shipped APK, and the gate logic itself is tested on the JVM without billing.
     */
    val entitlementRepository: EntitlementRepository =
        if (BuildConfig.DEBUG) FakeEntitlementRepository(unlocked = true)
        else BillingEntitlementRepository(context)

    private val database: AppDatabase = AppDatabase.create(context)
    val logRepository: LogRepository = LogRepository(database.measurements(), entitlementRepository)
    val segmentLog: SegmentLog = SegmentLog(logRepository)

    /** Sensor access is not an Android permission, so the core app still declares none. */
    val gravitySource: GravitySource = SensorManagerGravitySource(context)

    val settingsStore: SettingsStore = DataStoreSettingsStore(settingsDataStore)

    /**
     * Settings are shared between the screen that writes them and the screens that read them, so the
     * container holds the flow rather than each ViewModel keeping its own copy.
     */
    val settings: MutableStateFlow<AppSettings> = MutableStateFlow(AppSettings())

    /**
     * `AppSettings()` is the value before the store has been read, so a screen that decides *which*
     * thing to show from a setting (onboarding vs the ruler) must wait for the real value. Without
     * this the first frame always looked un-onboarded and the intro reappeared on every launch.
     */
    val settingsLoaded: MutableStateFlow<Boolean> = MutableStateFlow(false)

    /** VIBRATE is the only permission added in M9; the reason is recorded in PROGRESS.md. */
    val haptics: DeviceHaptics = DeviceHaptics(context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator?)

    /**
     * Application-lifetime work: anything that must finish even though the screen that asked for it
     * is already gone. A `viewModelScope` is cancelled the moment its destination is popped.
     */
    private val scope = CoroutineScope(SupervisorJob())

    init {
        scope.launch {
            settings.value = settingsStore.current()
            firstSession = !settings.value.onboardingSeen
            settingsLoaded.value = true
        }
        // Connects to Play once at app start. The fake implementation does nothing here.
        entitlementRepository.start()
    }

    val viewModelFactory = ViewModelFactory(this)

    /**
     * Whether this process started before the intro was ever finished — the user's first session, which
     * has no ad and no consent form (AdPolicy). Captured from the first settings read, so finishing the
     * intro now does not turn the rest of this session into an ad session.
     */
    @Volatile
    var firstSession: Boolean = true
        private set

    /** The one interstitial (owner decision 2026-10-20). Pro users never see it. */
    val ads: Ads = AdMobAds(
        context = context,
        isPro = { entitlementRepository.state.value.unlocked },
        isFirstSession = { firstSession }
    )

    /**
     * The intro is finished. The in-memory flow changes at once and the write runs on the application
     * scope: the intro's ViewModel is popped together with the intro in the same frame, and a save
     * launched there was cancelled before it reached the disk — so the intro came back on the next
     * launch. `DataStoreSettingsStore.save` absorbs an unwritable store itself, so a failed write
     * cannot reach the main thread from here.
     */
    fun markOnboardingSeen() {
        val updated = settings.value.copy(onboardingSeen = true)
        settings.value = updated
        scope.launch { settingsStore.save(updated) }
    }

    private companion object {
        const val CALIBRATION_FILE = "calibration.preferences_pb"
        const val SETTINGS_FILE = "settings.preferences_pb"
    }
}

/**
 * Moves the settings out of the shared calibration file on the first read after the split.
 *
 * Every per-device key is written as `name:deviceKey` (calibration, level zero) and no settings key
 * contains a colon, so "the keys without a colon" is exactly the settings — including any added later —
 * without this class having to know their names. The old copies stay in the calibration file: nothing
 * reads them, and deleting from a second store inside this one's migration would let a failed write
 * there fail the settings read too.
 */
internal class SettingsSplitMigration(private val legacy: DataStore<Preferences>) : DataMigration<Preferences> {

    override suspend fun shouldMigrate(currentData: Preferences): Boolean = currentData.asMap().isEmpty()

    override suspend fun migrate(currentData: Preferences): Preferences {
        val old = try {
            legacy.data.first()
        } catch (_: java.io.IOException) {
            // An unreadable legacy file has nothing to carry over; the defaults apply, as they did on a
            // failed read of that file before the split.
            return currentData
        }
        val moved = currentData.toMutablePreferences()
        old.asMap().forEach { (key, value) ->
            if (!key.name.contains(':')) {
                @Suppress("UNCHECKED_CAST")
                moved[key as Preferences.Key<Any>] = value
            }
        }
        return moved.toPreferences()
    }

    override suspend fun cleanUp() = Unit
}
