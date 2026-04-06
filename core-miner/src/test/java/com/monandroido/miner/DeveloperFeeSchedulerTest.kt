package com.monandroido.miner

import com.google.common.truth.Truth.assertThat
import com.monandroido.miner.model.FeeMode
import com.monandroido.miner.scheduler.DeveloperFeeScheduler
import org.junit.Test

class DeveloperFeeSchedulerTest {
    @Test
    fun zeroPercent_neverLeavesUserMode() {
        val scheduler = DeveloperFeeScheduler(initialFeePercent = 0, minSliceMillis = 120_000)

        repeat(20) { scheduler.onRunningTick(60_000) }

        assertThat(scheduler.currentMode()).isEqualTo(FeeMode.USER)
    }

    @Test
    fun hundredPercent_neverLeavesDeveloperMode() {
        val scheduler = DeveloperFeeScheduler(initialFeePercent = 100, minSliceMillis = 120_000)

        repeat(20) { scheduler.onRunningTick(60_000) }

        assertThat(scheduler.currentMode()).isEqualTo(FeeMode.DEVELOPER)
    }

    @Test
    fun tenPercent_tracksTargetWithinSliceTolerance() {
        val scheduler = DeveloperFeeScheduler(initialFeePercent = 10, minSliceMillis = 120_000)

        repeat(240) { scheduler.onRunningTick(60_000) }
        val snapshot = scheduler.onRunningTick(0)

        assertThat(snapshot.developerDurationMillis).isAtLeast(snapshot.targetDeveloperMillis - 120_000)
        assertThat(snapshot.developerDurationMillis).isAtMost(snapshot.targetDeveloperMillis + 120_000)
    }
}
