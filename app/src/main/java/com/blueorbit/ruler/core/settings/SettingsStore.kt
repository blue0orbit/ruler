package com.blueorbit.ruler.core.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.blueorbit.ruler.core.geometry.MeasurementUnit
import kotlinx.coroutines.flow.first
import java.io.IOException

enum class ThemeChoice { LIGHT, DARK, SYSTEM }

/**
 * Settings are data, not code defaults (rule 4): the stored value is what the app uses, and an
 * unreadable store falls back to the documented defaults instead of inventing a theme.
 *
 * `labelFontScale` scales labels only. It must never reach the physical geometry (rule 1).
 */
data class AppSettings(
    val theme: ThemeChoice = ThemeChoice.SYSTEM,
    /** Pro. Empty means the free palette, so a stored custom theme is not a way to bypass the gate. */
    val customTheme: String = "",
    val haptics: Boolean = true,
    val labelFontScale: Double = 1.0,
    val unit: MeasurementUnit = MeasurementUnit.MM,
    /**
     * One boolean (DESIGN.md §8). Written reason: the app dropped a new user straight onto an
     * unverified ruler with no explanation, which is the weakest moment in the product. Skipping the
     * intro does not make the ruler calibrated, so the unverified provenance stays visible (rule 5).
     */
    val onboardingSeen: Boolean = false,
    /**
     * Simple mode (DECISIONS rule 12, M15). Default **true**: a new install opens as a ruler, not as a
     * metrology instrument. It is a gate on what is *shown* — the measurement, the stored calibration and
     * the maths are identical either way (rule 10) — and the accuracy of a reading is stated in both,
     * only in different words (rule 13).
     */
    val simpleMode: Boolean = true,
    /**
     * The one-line instruction on the ruler screen (M17). Default **false**: a new install is told what to
     * physically do, once. Dismissing it is stored rather than remembered in memory so the sentence does
     * not come back on the next launch and teach a user who has already learned it.
     */
    val firstRunInstructionSeen: Boolean = false
)

interface SettingsStore {
    suspend fun current(): AppSettings
    suspend fun save(settings: AppSettings)
}

class InMemorySettingsStore(private var settings: AppSettings = AppSettings()) : SettingsStore {
    override suspend fun current(): AppSettings = settings
    override suspend fun save(settings: AppSettings) {
        this.settings = settings
    }
}

class DataStoreSettingsStore(private val dataStore: DataStore<Preferences>) : SettingsStore {

    private val themeKey = stringPreferencesKey("theme")
    private val customThemeKey = stringPreferencesKey("customTheme")
    private val hapticsKey = booleanPreferencesKey("haptics")
    private val fontScaleKey = doublePreferencesKey("labelFontScale")
    private val unitKey = stringPreferencesKey("unit")
    private val onboardingKey = booleanPreferencesKey("onboardingSeen")
    private val simpleModeKey = booleanPreferencesKey("simpleMode")
    private val firstRunKey = booleanPreferencesKey("firstRunInstructionSeen")

    override suspend fun current(): AppSettings {
        val prefs = try {
            dataStore.data.first()
        } catch (error: IOException) {
            return AppSettings()
        }

        return AppSettings(
            theme = prefs[themeKey]?.let { value -> ThemeChoice.values().firstOrNull { it.name == value } }
                ?: ThemeChoice.SYSTEM,
            customTheme = prefs[customThemeKey] ?: "",
            haptics = prefs[hapticsKey] ?: true,
            labelFontScale = prefs[fontScaleKey] ?: 1.0,
            unit = prefs[unitKey]?.let { value ->
                MeasurementUnit.entries.firstOrNull { it.name == value }
            } ?: MeasurementUnit.MM,
            onboardingSeen = prefs[onboardingKey] ?: false,
            simpleMode = prefs[simpleModeKey] ?: true,
            firstRunInstructionSeen = prefs[firstRunKey] ?: false
        )
    }

    /**
     * A write that cannot happen does not get to crash the screen the user is standing on: `edit` reads
     * the file before it changes it, so the same unreadable store the read path falls back for (M18 review)
     * throws here too, and an exception that leaves a `viewModelScope.launch` unhandled reaches the main
     * thread. The choice behaves like the read path does — the app keeps running on the value the user
     * just picked, and the next write they make tries again.
     */
    override suspend fun save(settings: AppSettings) {
        try {
            dataStore.edit { prefs ->
                prefs[themeKey] = settings.theme.name
                prefs[customThemeKey] = settings.customTheme
                prefs[hapticsKey] = settings.haptics
                prefs[fontScaleKey] = settings.labelFontScale
                prefs[unitKey] = settings.unit.name
                prefs[onboardingKey] = settings.onboardingSeen
                prefs[simpleModeKey] = settings.simpleMode
                prefs[firstRunKey] = settings.firstRunInstructionSeen
            }
        } catch (_: IOException) {
            // Deliberately silent: there is no logging in this app (DECISIONS.md) and a toast about a
            // failed settings write would tell a user nothing they can act on. `CorruptionException`,
            // which is what a damaged store throws, is a subclass of IOException, so it lands here too.
        }
    }
}
