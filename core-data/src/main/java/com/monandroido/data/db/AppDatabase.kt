package com.monandroido.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [MiningProfileEntity::class, BenchmarkResultEntity::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(MonandroidoTypeConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun miningProfileDao(): MiningProfileDao
    abstract fun benchmarkResultDao(): BenchmarkResultDao
}
