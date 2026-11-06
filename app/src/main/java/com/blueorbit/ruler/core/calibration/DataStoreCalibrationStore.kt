package com.blueorbit.ruler.core.calibration

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import java.io.IOException

/**
 * Values are stored per device key, so a device swap or a reinstall never silently resets the
 * calibration (top competitor complaint). Read failures fall back to the labelled guess rather
 * than inventing a number.
 */
class DataStoreCalibrationStore(
    private val dataStore: DataStore<Preferences>,
    private val deviceKey: String,
    private val guess: Calibration
) : CalibrationStore {

    private val pxKey = doublePreferencesKey("pxPerMm:$deviceKey")
    private val sourceKey = stringPreferencesKey("source:$deviceKey")
    private val errorKey = doublePreferencesKey("errorBudget:$deviceKey")
    private val insetKey = doublePreferencesKey("bezelInset:$deviceKey")

    override suspend fun current(): Calibration {
        val prefs = try {
            dataStore.data.first()
        } catch (error: IOException) {
            // A corrupt or unreadable store must not invent a number (rule 4).
            return guess
        }

        val pixelsPerMm = prefs[pxKey] ?: return guess
        val source = prefs[sourceKey] ?: return guess
        // Finding 17: a calibration with no error budget is incomplete. Defaulting to 0.0 would print
        // "±0 mm per 100 mm" — a guarantee no calibration method can support (rule 5) — so the
        // labelled guess is returned instead of a value that overstates its own accuracy.
        val budget = prefs[errorKey] ?: return guess
        return Calibration(
            pixelsPerMm = pixelsPerMm,
            // Finding 16: `valueOf` throws on an unknown name, which would crash app start on a
            // store written by a different build. An unrecognised source means the stored calibration
            // cannot be trusted, so the labelled guess is the safe answer.
            source = CalibrationSource.entries.firstOrNull { it.name == source } ?: return guess,
            errorBudgetMmPer100Mm = budget,
            bezelInsetMm = prefs[insetKey] ?: 0.0
        )
    }

    /**
     * `edit` reads the file before it writes it, so the same damaged store that [current] falls back for
     * throws here too — and these are called from `viewModelScope.launch`, where an unhandled exception
     * reaches the main thread and closes the app. `CorruptionException` is a subclass of IOException, so
     * one catch covers both. Unlike `SettingsStore.save` the failure is reported, not swallowed: the
     * caller tells the user their calibration was not kept, because the ruler would otherwise go on
     * claiming a calibration that the next launch will not have (rule 5).
     */
    override suspend fun save(calibration: Calibration): Boolean = write { prefs ->
        prefs[pxKey] = calibration.pixelsPerMm
        prefs[sourceKey] = calibration.source.name
        prefs[errorKey] = calibration.errorBudgetMmPer100Mm
        prefs[insetKey] = calibration.bezelInsetMm
    }

    override suspend fun clear(): Boolean = write { prefs ->
        prefs.remove(pxKey)
        prefs.remove(sourceKey)
        prefs.remove(errorKey)
        prefs.remove(insetKey)
    }

    private suspend fun write(change: (MutablePreferences) -> Unit): Boolean = try {
        dataStore.edit { prefs -> change(prefs) }
        true
    } catch (_: IOException) {
        false
    }
}
