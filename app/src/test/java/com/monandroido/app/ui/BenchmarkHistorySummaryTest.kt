package com.monandroido.app.ui

import com.monandroido.data.model.AlgorithmMode
import com.monandroido.data.model.BenchmarkPreset
import com.monandroido.data.model.BenchmarkResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class BenchmarkHistorySummaryTest {
    @Test
    fun buildBenchmarkHistorySummary_returnsBestLatestAverageAndCoverage() {
        val summary = buildBenchmarkHistorySummary(
            listOf(
                BenchmarkResult(
                    id = 1,
                    algorithmMode = AlgorithmMode.RX0,
                    preset = BenchmarkPreset.ONE_MEGA,
                    avgHashrate = 515.0,
                    durationMillis = 12_000,
                    peakThermal = 2,
                    batteryDeltaPercent = 1,
                    createdAt = 1_000,
                ),
                BenchmarkResult(
                    id = 2,
                    algorithmMode = AlgorithmMode.RXWOW,
                    preset = BenchmarkPreset.TEN_MEGA,
                    avgHashrate = 640.0,
                    durationMillis = 24_000,
                    peakThermal = 3,
                    batteryDeltaPercent = 2,
                    createdAt = 2_000,
                ),
                BenchmarkResult(
                    id = 3,
                    algorithmMode = AlgorithmMode.RX0,
                    preset = BenchmarkPreset.TEN_MEGA,
                    avgHashrate = 590.0,
                    durationMillis = 18_000,
                    peakThermal = 2,
                    batteryDeltaPercent = 1,
                    createdAt = 3_000,
                ),
            ),
        )

        assertEquals(3, summary.totalRuns)
        assertEquals(581.6666666666666, summary.averageHashrate ?: 0.0, 0.0001)
        assertEquals(640.0, summary.bestResult?.avgHashrate ?: 0.0, 0.0)
        assertEquals(3L, summary.latestResult?.id)
        assertEquals(2, summary.algorithmsCovered)
        assertNotNull(summary.bestResult)
    }
}
