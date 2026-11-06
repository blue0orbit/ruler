package com.blueorbit.ruler.core.log

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Version 2 because v1 had no tags column. The migration is listed explicitly so an existing
 * install is upgraded rather than rebuilt — the log is the user's data.
 *
 * The schema is exported (M11R finding 2) so the generated DDL can be read back and compared with
 * the hand-written migration SQL; that is what makes the migration verifiable instead of asserted
 * against itself. The JSON is committed under `app/schemas/`.
 */
@Database(entities = [Measurement::class], version = AppDatabase.VERSION, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {

    abstract fun measurements(): MeasurementDao

    companion object {
        const val NAME = "ruler-log.db"
        const val VERSION = 2

        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, NAME)
                .addMigrations(*Migrations.all.toTypedArray())
                .build()
    }
}
