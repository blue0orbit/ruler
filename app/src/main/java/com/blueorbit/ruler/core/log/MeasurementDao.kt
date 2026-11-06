package com.blueorbit.ruler.core.log

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface MeasurementDao {

    @Insert
    suspend fun insert(measurement: Measurement): Long

    @Update
    suspend fun update(measurement: Measurement): Int

    @Delete
    suspend fun delete(measurement: Measurement): Int

    @Query("SELECT * FROM measurements ORDER BY createdAtMillis DESC")
    fun observeAll(): Flow<List<Measurement>>

    @Query("SELECT COUNT(*) FROM measurements")
    suspend fun count(): Int

    @Query("SELECT * FROM measurements WHERE id = :id")
    suspend fun findById(id: Long): Measurement?

    @Query("DELETE FROM measurements WHERE id = :id")
    suspend fun deleteById(id: Long): Int
}
