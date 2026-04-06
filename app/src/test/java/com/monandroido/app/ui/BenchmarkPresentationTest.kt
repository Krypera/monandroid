package com.monandroido.app.ui

import com.monandroido.app.ui.screens.benchmark.sortBenchmarkResultsForDisplay
import com.monandroido.data.model.AlgorithmMode
import com.monandroido.data.model.BenchmarkPreset
import com.monandroido.data.model.BenchmarkResult
import org.junit.Assert.assertEquals
import org.junit.Test

class BenchmarkPresentationTest {
    @Test
    fun sortBenchmarkResultsForDisplay_ordersLatestFirstWithIdTieBreaker() {
        val older = BenchmarkResult(
            id = 1L,
            algorithmMode = AlgorithmMode.RX0,
            preset = BenchmarkPreset.ONE_MEGA,
            avgHashrate = 500.0,
            durationMillis = 10_000L,
            peakThermal = null,
            batteryDeltaPercent = null,
            createdAt = 1_000L,
        )
        val sameTimeLowerId = BenchmarkResult(
            id = 2L,
            algorithmMode = AlgorithmMode.RXWOW,
            preset = BenchmarkPreset.TEN_MEGA,
            avgHashrate = 600.0,
            durationMillis = 20_000L,
            peakThermal = null,
            batteryDeltaPercent = null,
            createdAt = 2_000L,
        )
        val sameTimeHigherId = sameTimeLowerId.copy(id = 3L)

        val sorted = sortBenchmarkResultsForDisplay(listOf(older, sameTimeLowerId, sameTimeHigherId))

        assertEquals(listOf(3L, 2L, 1L), sorted.map { it.id })
    }
}
