package com.monandroido.miner.presentation

import android.content.Context
import com.monandroido.miner.R
import com.monandroido.miner.model.FeeMode
import com.monandroido.miner.model.MinerStatus

fun Context.minerStatusLabel(status: MinerStatus): String = getString(
    when (status) {
        MinerStatus.STOPPED -> R.string.miner_status_idle
        MinerStatus.STARTING -> R.string.miner_status_starting
        MinerStatus.RUNNING -> R.string.miner_status_running
        MinerStatus.PAUSED -> R.string.miner_status_paused
        MinerStatus.BENCHMARKING -> R.string.miner_status_benchmarking
        MinerStatus.ERROR -> R.string.miner_status_error
    },
)

fun Context.feeModeLabel(mode: FeeMode): String = getString(
    when (mode) {
        FeeMode.NONE -> R.string.miner_fee_mode_inactive
        FeeMode.USER -> R.string.miner_fee_mode_user
        FeeMode.DEVELOPER -> R.string.miner_fee_mode_developer
    },
)

fun Context.formatHashrateText(hashrateHps: Double): String = when {
    hashrateHps >= 1_000_000 -> getString(R.string.miner_hashrate_mhps, hashrateHps / 1_000_000)
    hashrateHps >= 1_000 -> getString(R.string.miner_hashrate_khps, hashrateHps / 1_000)
    else -> getString(R.string.miner_hashrate_hps, hashrateHps)
}
