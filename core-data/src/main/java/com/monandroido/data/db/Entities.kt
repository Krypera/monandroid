package com.monandroido.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.monandroido.data.model.AlgorithmMode
import com.monandroido.data.model.BenchmarkPreset

@Entity(tableName = "mining_profiles")
data class MiningProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val primaryPoolUrl: String,
    val walletAddress: String,
    val rigId: String?,
    val tls: Boolean,
    val enabled: Boolean,
    val algorithmMode: AlgorithmMode,
    val maxThreadsHint: Int,
    val retryCount: Int,
    val retryPauseSeconds: Int,
    val continueWhenScreenOff: Boolean,
    val requireCharging: Boolean,
    val keepAlive: Boolean,
    val backupPoolsJson: String,
    val passwordAlias: String,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(tableName = "benchmark_results")
data class BenchmarkResultEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val algorithmMode: AlgorithmMode,
    val preset: BenchmarkPreset,
    val avgHashrate: Double,
    val durationMillis: Long,
    val peakThermal: Int?,
    val batteryDeltaPercent: Int?,
    val createdAt: Long,
)
