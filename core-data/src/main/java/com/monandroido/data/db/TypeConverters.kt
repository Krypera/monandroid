package com.monandroido.data.db

import androidx.room.TypeConverter
import com.monandroido.data.model.AlgorithmMode
import com.monandroido.data.model.BackupPool
import com.monandroido.data.model.BenchmarkPreset
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class MonandroidoTypeConverters {
    private val json = Json { ignoreUnknownKeys = true }

    @TypeConverter
    fun fromAlgorithmMode(value: AlgorithmMode): String = value.name

    @TypeConverter
    fun toAlgorithmMode(value: String): AlgorithmMode = AlgorithmMode.valueOf(value)

    @TypeConverter
    fun fromBenchmarkPreset(value: BenchmarkPreset): String = value.name

    @TypeConverter
    fun toBenchmarkPreset(value: String): BenchmarkPreset = BenchmarkPreset.valueOf(value)

    @TypeConverter
    fun fromBackupPools(value: List<BackupPool>): String = json.encodeToString(value)

    @TypeConverter
    fun toBackupPools(value: String): List<BackupPool> =
        if (value.isBlank()) emptyList() else json.decodeFromString(value)
}
