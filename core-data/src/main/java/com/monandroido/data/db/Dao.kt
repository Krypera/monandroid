package com.monandroido.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface MiningProfileDao {
    @Query("SELECT * FROM mining_profiles ORDER BY updatedAt DESC, id DESC")
    fun observeProfiles(): Flow<List<MiningProfileEntity>>

    @Query("SELECT * FROM mining_profiles ORDER BY updatedAt DESC, id DESC")
    suspend fun getAll(): List<MiningProfileEntity>

    @Query("SELECT * FROM mining_profiles WHERE id = :id")
    suspend fun getById(id: Long): MiningProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(profile: MiningProfileEntity): Long

    @Update
    suspend fun update(profile: MiningProfileEntity)

    @Delete
    suspend fun delete(profile: MiningProfileEntity)
}

@Dao
interface BenchmarkResultDao {
    @Query("SELECT * FROM benchmark_results ORDER BY createdAt DESC, id DESC")
    fun observeResults(): Flow<List<BenchmarkResultEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(result: BenchmarkResultEntity): Long

    @Query(
        "DELETE FROM benchmark_results WHERE id NOT IN (" +
            "SELECT id FROM benchmark_results ORDER BY createdAt DESC, id DESC LIMIT :limit" +
            ")",
    )
    suspend fun trimToLatest(limit: Int)

    @Query("DELETE FROM benchmark_results")
    suspend fun clearAll()
}
