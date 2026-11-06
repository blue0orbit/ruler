package com.blueorbit.ruler.core.log

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A stored measurement keeps its provenance (rule 5): the value in micrometres (rule 2), the unit
 * it was displayed in, the method it came from, and the error budget at the time it was saved.
 * A row saved from an unverified guess must still say so when it is read back months later.
 *
 * Indices on createdAt and label (PLAN M5): the log is sorted by date and searched by label.
 */
@Entity(tableName = "measurements", indices = [Index("createdAtMillis"), Index("label")])
data class Measurement(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val valueMicrometres: Long,
    val unit: String,
    val label: String,
    val note: String,
    val source: String,
    val errorBudgetMmPer100Mm: Double,
    val createdAtMillis: Long,
    /**
     * Pro. Extra column, not a fork — the free log is fully usable with it empty (rule 10).
     *
     * `defaultValue` is not cosmetic: the v1→v2 migration adds this column with `DEFAULT ''`, and
     * Room validates the migrated table against the entity's expected DDL. Without the same default
     * here the validation fails on every existing install (M11R finding 1).
     */
    @ColumnInfo(defaultValue = "")
    val tags: String = ""
)
