package com.monandroido.miner.model

import com.monandroido.data.model.AlgorithmMode
import com.monandroido.data.model.BenchmarkPreset

enum class MinerStatus {
    STOPPED,
    STARTING,
    RUNNING,
    PAUSED,
    BENCHMARKING,
    ERROR,
}

enum class FeeMode {
    NONE,
    USER,
    DEVELOPER,
}

data class LastSessionSummary(
    val userDurationMillis: Long,
    val developerDurationMillis: Long,
)

data class MinerSessionState(
    val status: MinerStatus = MinerStatus.STOPPED,
    val activeProfileId: Long? = null,
    val activeProfileName: String? = null,
    val currentFeeMode: FeeMode = FeeMode.NONE,
    val hashrateHps: Double = 0.0,
    val acceptedShares: Int = 0,
    val rejectedShares: Int = 0,
    val uptimeMillis: Long = 0L,
    val poolAddress: String? = null,
    val lastError: String? = null,
    val pauseReason: String? = null,
    val developerFeePercent: Int = 10,
    val sessionUserMillis: Long = 0L,
    val sessionDeveloperMillis: Long = 0L,
    val benchmarkAlgorithm: AlgorithmMode? = null,
    val benchmarkPreset: BenchmarkPreset? = null,
    val benchmarkHashrate: Double? = null,
    val lastSessionSummary: LastSessionSummary? = null,
)

data class BenchmarkRequest(
    val preset: BenchmarkPreset,
    val algorithmMode: AlgorithmMode,
)

data class XmrigSummarySnapshot(
    val hashrateHps: Double? = null,
    val acceptedShares: Int? = null,
    val rejectedShares: Int? = null,
    val uptimeMillis: Long? = null,
    val poolAddress: String? = null,
    val lastError: String? = null,
)
