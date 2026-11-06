package com.blueorbit.ruler.core.log

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Recorded so an existing install is upgraded rather than rebuilt. v1 had no tags column; tags are
 * Pro, and adding a column must not lose rows (the log is the user's data).
 */
object Migrations {

    const val ADD_TAGS_SQL = "ALTER TABLE measurements ADD COLUMN tags TEXT NOT NULL DEFAULT ''"

    val v1ToV2: Migration = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(ADD_TAGS_SQL)
        }
    }

    val all: List<Migration> = listOf(v1ToV2)
}
