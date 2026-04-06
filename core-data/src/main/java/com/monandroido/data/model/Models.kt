package com.monandroido.data.model

import android.content.Context
import androidx.annotation.StringRes
import com.monandroido.data.R
import kotlinx.serialization.Serializable

@Serializable
enum class AlgorithmMode(
    val xmrigValue: String,
    @StringRes internal val labelResId: Int,
) {
    RX0("rx/0", R.string.algorithm_label_rx0),
    RXWOW("rx/wow", R.string.algorithm_label_rxwow),
}

@Serializable
enum class BenchmarkPreset(
    val sizeArg: String,
    @StringRes internal val labelResId: Int,
) {
    ONE_MEGA("1M", R.string.benchmark_preset_label_one_mega),
    TEN_MEGA("10M", R.string.benchmark_preset_label_ten_mega),
}

@Serializable
data class BackupPool(
    val url: String,
    val tls: Boolean = false,
    val keepAlive: Boolean = true,
)

@Serializable
data class AdvancedMinerSettings(
    val algorithmMode: AlgorithmMode = AlgorithmMode.RX0,
    val maxThreadsHint: Int = 75,
    val retryCount: Int = 5,
    val retryPauseSeconds: Int = 5,
    val continueWhenScreenOff: Boolean = false,
    val requireCharging: Boolean = false,
    val keepAlive: Boolean = true,
    val backupPools: List<BackupPool> = emptyList(),
)

data class MiningProfileSummary(
    val id: Long,
    val name: String,
    val primaryPoolUrl: String,
    val walletAddress: String,
    val rigId: String?,
    val tls: Boolean,
    val enabled: Boolean,
    val advancedSettings: AdvancedMinerSettings,
)

data class MiningProfile(
    val id: Long,
    val name: String,
    val primaryPoolUrl: String,
    val walletAddress: String,
    val password: String,
    val rigId: String?,
    val tls: Boolean,
    val enabled: Boolean,
    val advancedSettings: AdvancedMinerSettings,
)

data class BenchmarkResult(
    val id: Long = 0,
    val algorithmMode: AlgorithmMode,
    val preset: BenchmarkPreset,
    val avgHashrate: Double,
    val durationMillis: Long,
    val peakThermal: Int?,
    val batteryDeltaPercent: Int?,
    val createdAt: Long,
)

data class AppSettings(
    val developerFeePercent: Int = 10,
    val advancedModeEnabled: Boolean = false,
)

data class ProfileDraft(
    val id: Long? = null,
    val name: String = "",
    val primaryPoolUrl: String = "",
    val walletAddress: String = "",
    val password: String = "",
    val rigId: String = "",
    val tls: Boolean = false,
    val enabled: Boolean = true,
    val advancedSettings: AdvancedMinerSettings = AdvancedMinerSettings(),
)

fun AlgorithmMode.label(context: Context): String = context.getString(labelResId)

fun BenchmarkPreset.label(context: Context): String = context.getString(labelResId)
