package com.blueorbit.ruler.core.calibration

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import com.blueorbit.ruler.core.sensors.Gravity
import kotlinx.coroutines.flow.first
import java.io.IOException

/**
 * The level zero is data the user created on a surface (rule 4), so it has to survive the process —
 * holding it in the ViewModel alone meant the user re-zeroed on every launch, and a zero that
 * silently resets is worse than no zero at all (M11R finding 19).
 *
 * It is keyed per device like the calibration, because the zero is the direction the device had on
 * that surface, which does not transfer to another device.
 */
interface LevelZeroStore {
    suspend fun load(): LevelZero

    /**
     * Stores [zero] (a null reference clears it). Returns false when the write could not happen, so the
     * screen can say "Couldn't save" instead of claiming a zero that will be gone after a restart.
     */
    suspend fun save(zero: LevelZero): Boolean
}

class InMemoryLevelZeroStore(private var zero: LevelZero = LevelZero()) : LevelZeroStore {
    override suspend fun load(): LevelZero = zero
    override suspend fun save(zero: LevelZero): Boolean {
        this.zero = zero
        return true
    }
}

class DataStoreLevelZeroStore(
    private val dataStore: DataStore<Preferences>,
    private val deviceKey: String
) : LevelZeroStore {

    private val xKey = doublePreferencesKey("levelZeroX:$deviceKey")
    private val yKey = doublePreferencesKey("levelZeroY:$deviceKey")
    private val zKey = doublePreferencesKey("levelZeroZ:$deviceKey")

    /** No stored zero, or an unreadable store, means "not zeroed" — never an invented reference. */
    override suspend fun load(): LevelZero {
        val prefs = try {
            dataStore.data.first()
        } catch (error: IOException) {
            return LevelZero()
        }
        val x = prefs[xKey] ?: return LevelZero()
        val y = prefs[yKey] ?: return LevelZero()
        val z = prefs[zKey] ?: return LevelZero()
        return LevelZero(Gravity(x, y, z))
    }

    /**
     * `edit` reads the file before it writes it, so the same unreadable store [load] falls back for throws
     * here too — and an exception left unhandled in a `viewModelScope.launch` closes the app. Like
     * `DataStoreSettingsStore.save`, the failure is caught (`CorruptionException` is an IOException);
     * unlike it, the failure is reported, because the user just asked for this zero to be kept.
     */
    override suspend fun save(zero: LevelZero): Boolean {
        val reference = zero.reference
        return try {
            dataStore.edit { prefs ->
                if (reference == null) {
                    prefs.remove(xKey)
                    prefs.remove(yKey)
                    prefs.remove(zKey)
                } else {
                    prefs[xKey] = reference.x
                    prefs[yKey] = reference.y
                    prefs[zKey] = reference.z
                }
            }
            true
        } catch (_: IOException) {
            false
        }
    }
}
