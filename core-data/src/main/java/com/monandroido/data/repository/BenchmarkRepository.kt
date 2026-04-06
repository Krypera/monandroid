package com.monandroido.data.repository

import com.monandroido.data.db.AppDatabase
import com.monandroido.data.db.BenchmarkResultEntity
import com.monandroido.data.model.BenchmarkResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class BenchmarkRepository(private val database: AppDatabase) {
    val results: Flow<List<BenchmarkResult>> =
        database.benchmarkResultDao().observeResults().map { entities ->
            entities.map { entity ->
                BenchmarkResult(
                    id = entity.id,
                    algorithmMode = entity.algorithmMode,
                    preset = entity.preset,
                    avgHashrate = entity.avgHashrate,
                    durationMillis = entity.durationMillis,
                    peakThermal = entity.peakThermal,
                    batteryDeltaPercent = entity.batteryDeltaPercent,
                    createdAt = entity.createdAt,
                )
            }
        }

    suspend fun addResult(result: BenchmarkResult) {
        database.benchmarkResultDao().insert(
            BenchmarkResultEntity(
                algorithmMode = result.algorithmMode,
                preset = result.preset,
                avgHashrate = result.avgHashrate,
                durationMillis = result.durationMillis,
                peakThermal = result.peakThermal,
                batteryDeltaPercent = result.batteryDeltaPercent,
                createdAt = result.createdAt,
            ),
        )
        database.benchmarkResultDao().trimToLatest(MAX_SAVED_RESULTS)
    }

    suspend fun clearResults() {
        database.benchmarkResultDao().clearAll()
    }

    companion object {
        private const val MAX_SAVED_RESULTS = 50
    }
}
