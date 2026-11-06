package com.blueorbit.ruler.core.di

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * Settings moved out of the calibration file so backup can keep one and drop the other. An existing
 * install must keep its settings across that move, and calibration must not follow them: a
 * calibration restored onto another phone would be a wrong ruler that claims to be exact.
 */
class SettingsSplitMigrationTest {

    @Test
    fun settingsMoveAcrossAndCalibrationStaysBehind(): Unit = runBlocking {
        val dir = Files.createTempDirectory("ruler-split").toFile()
        val legacy = PreferenceDataStoreFactory.create(produceFile = { File(dir, "calibration.preferences_pb") })
        legacy.edit {
            it[stringPreferencesKey("unit")] = "INCH"
            it[booleanPreferencesKey("onboardingSeen")] = true
            it[doublePreferencesKey("pxPerMm:Pixel_1080x2424")] = 15.35
            it[doublePreferencesKey("levelZeroX:Pixel_1080x2424")] = 0.01
        }

        val settings = PreferenceDataStoreFactory.create(
            migrations = listOf(SettingsSplitMigration(legacy)),
            produceFile = { File(dir, "settings.preferences_pb") }
        )
        val moved = settings.data.first()

        assertEquals("INCH", moved[stringPreferencesKey("unit")])
        assertEquals(true, moved[booleanPreferencesKey("onboardingSeen")])
        assertNull(moved[doublePreferencesKey("pxPerMm:Pixel_1080x2424")])
        assertNull(moved[doublePreferencesKey("levelZeroX:Pixel_1080x2424")])
    }

    @Test
    fun aSettingsFileThatAlreadyHasDataIsNotOverwritten(): Unit = runBlocking {
        val dir = Files.createTempDirectory("ruler-split").toFile()
        val legacy = PreferenceDataStoreFactory.create(produceFile = { File(dir, "calibration.preferences_pb") })
        legacy.edit { it[stringPreferencesKey("unit")] = "INCH" }

        val migration = SettingsSplitMigration(legacy)
        val existing = androidx.datastore.preferences.core.preferencesOf(stringPreferencesKey("unit") to "MM")
        assertEquals(false, migration.shouldMigrate(existing))
    }
}
