package com.monandroido.miner.scheduler

import com.monandroido.miner.model.FeeMode
import kotlin.math.roundToLong

data class SchedulerSnapshot(
    val currentMode: FeeMode,
    val userDurationMillis: Long,
    val developerDurationMillis: Long,
    val targetDeveloperMillis: Long,
    val developerDebtMillis: Long,
)

class DeveloperFeeScheduler(
    initialFeePercent: Int,
    private val minSliceMillis: Long = 120_000L,
) {
    private var feePercent: Int = initialFeePercent.coerceIn(0, 100)
    private var currentMode: FeeMode = when {
        feePercent <= 0 -> FeeMode.USER
        feePercent >= 100 -> FeeMode.DEVELOPER
        else -> FeeMode.USER
    }

    private var currentSliceMillis: Long = 0L
    private var userDurationMillis: Long = 0L
    private var developerDurationMillis: Long = 0L

    fun updateFeePercent(value: Int) {
        feePercent = value.coerceIn(0, 100)
        if (feePercent == 0) currentMode = FeeMode.USER
        if (feePercent == 100) currentMode = FeeMode.DEVELOPER
    }

    fun currentMode(): FeeMode = currentMode

    fun onRunningTick(deltaMillis: Long): SchedulerSnapshot {
        if (deltaMillis <= 0) return snapshot()

        currentSliceMillis += deltaMillis
        if (currentMode == FeeMode.DEVELOPER) {
            developerDurationMillis += deltaMillis
        } else {
            userDurationMillis += deltaMillis
        }

        when {
            feePercent == 0 -> currentMode = FeeMode.USER
            feePercent == 100 -> currentMode = FeeMode.DEVELOPER
            else -> evaluateSwitch()
        }

        return snapshot()
    }

    private fun evaluateSwitch() {
        if (currentSliceMillis < minSliceMillis) return

        val total = userDurationMillis + developerDurationMillis
        val targetDeveloperMillis = (total * (feePercent / 100.0)).roundToLong()
        val developerDebtMillis = targetDeveloperMillis - developerDurationMillis

        when {
            currentMode == FeeMode.USER && developerDebtMillis >= minSliceMillis -> {
                currentMode = FeeMode.DEVELOPER
                currentSliceMillis = 0L
            }

            currentMode == FeeMode.DEVELOPER && developerDebtMillis <= 0L -> {
                currentMode = FeeMode.USER
                currentSliceMillis = 0L
            }
        }
    }

    private fun snapshot(): SchedulerSnapshot {
        val total = userDurationMillis + developerDurationMillis
        val targetDeveloperMillis = (total * (feePercent / 100.0)).roundToLong()
        return SchedulerSnapshot(
            currentMode = currentMode,
            userDurationMillis = userDurationMillis,
            developerDurationMillis = developerDurationMillis,
            targetDeveloperMillis = targetDeveloperMillis,
            developerDebtMillis = targetDeveloperMillis - developerDurationMillis,
        )
    }
}
