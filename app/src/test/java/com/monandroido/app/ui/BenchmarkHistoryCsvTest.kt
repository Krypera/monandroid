package com.monandroido.app.ui

import com.monandroido.data.model.AlgorithmMode
import com.monandroido.data.model.BenchmarkPreset
import com.monandroido.data.model.BenchmarkResult
import org.junit.Assert.assertTrue
import org.junit.Test

class BenchmarkHistoryCsvTest {
    @Test
    fun buildBenchmarkHistoryCsv_outputsHeaderAndRows() {
        val csv = buildBenchmarkHistoryCsv(
            listOf(
                BenchmarkResult(
                    id = 7,
                    algorithmMode = AlgorithmMode.RX0,
                    preset = BenchmarkPreset.ONE_MEGA,
                    avgHashrate = 512.4,
                    durationMillis = 30_000,
                    peakThermal = 2,
                    batteryDeltaPercent = 1,
                    createdAt = 1_700_000_000_000,
                ),
            ),
        )

        assertTrue(csv.contains("recorded_at,algorithm,preset,avg_hashrate_hps,duration_seconds,battery_delta_percent,peak_thermal"))
        assertTrue(csv.contains("\"rx/0\""))
        assertTrue(csv.contains("\"1M\""))
        assertTrue(csv.contains("512.4"))
        assertTrue(csv.contains(",30,1,2"))
    }
}
