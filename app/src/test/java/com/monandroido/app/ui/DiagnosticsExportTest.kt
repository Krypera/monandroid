package com.monandroido.app.ui

import com.monandroido.data.model.AdvancedMinerSettings
import com.monandroido.data.model.AlgorithmMode
import com.monandroido.data.model.BenchmarkPreset
import com.monandroido.data.model.BenchmarkResult
import com.monandroido.data.model.MiningProfileSummary
import com.monandroido.miner.model.FeeMode
import com.monandroido.miner.model.MinerSessionState
import com.monandroido.miner.model.MinerStatus
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticsExportTest {
    @Test
    fun buildDiagnosticsJson_masksWalletsAndIncludesRecentState() {
        val wallet = "49BE1LKSDDriFWNb1TSeW98y5GZjbT1HJJ2LdiUGieMVXRo6Bm4gop1dRLr1mvtxCpSNewpAUNNzeBM8RtNiTf4YDucDVCt"
        val password = "super-secret"
        val profile = MiningProfileSummary(
            id = 7L,
            name = "Phone",
            primaryPoolUrl = "gulf.moneroocean.stream:10128",
            walletAddress = wallet,
            rigId = "phone-rig",
            tls = false,
            enabled = true,
            advancedSettings = AdvancedMinerSettings(algorithmMode = AlgorithmMode.RX0),
        )
        val benchmarkResult = BenchmarkResult(
            id = 11L,
            algorithmMode = AlgorithmMode.RX0,
            preset = BenchmarkPreset.ONE_MEGA,
            avgHashrate = 742.5,
            durationMillis = 18_000L,
            peakThermal = 3,
            batteryDeltaPercent = 1,
            createdAt = 456_000L,
        )

        val diagnosticsJson = buildDiagnosticsJson(
            appMetadata = AppDiagnosticsMetadata(
                applicationId = "com.monandroido.app",
                versionName = "1.0.0",
                versionCode = 1L,
            ),
            homeUiState = HomeUiState(
                activeProfileId = profile.id,
                activeProfile = profile,
                minerState = MinerSessionState(
                    status = MinerStatus.RUNNING,
                    activeProfileId = profile.id,
                    activeProfileName = profile.name,
                    currentFeeMode = FeeMode.USER,
                    hashrateHps = 512.25,
                    acceptedShares = 2,
                    rejectedShares = 0,
                    uptimeMillis = 12_000L,
                    poolAddress = profile.primaryPoolUrl,
                    lastError = "password=$password user=$wallet",
                ),
                recentLogs = listOf(
                    "user=$wallet",
                    "password=$password",
                ),
            ),
            profilesUiState = ProfilesUiState(
                profiles = listOf(profile),
                activeProfileId = profile.id,
                minerStatus = MinerStatus.RUNNING,
            ),
            benchmarkUiState = BenchmarkUiState(
                benchmarkResults = listOf(benchmarkResult),
                minerState = MinerSessionState(status = MinerStatus.RUNNING),
                historySummary = buildBenchmarkHistorySummary(listOf(benchmarkResult)),
            ),
            settingsUiState = SettingsUiState(
                requestedDeveloperFeePercent = 10,
                effectiveDeveloperFeePercent = 10,
                advancedModeEnabled = true,
                developerWalletConfigured = true,
            ),
            exportedAtMillis = 999_000L,
        )

        val root = Json.parseToJsonElement(diagnosticsJson).jsonObject
        val firstProfile = root.getValue("profiles").jsonObject.getValue("items").jsonArray.first().jsonObject
        val miner = root.getValue("miner").jsonObject
        val logs = root.getValue("recentLogs").jsonArray

        assertEquals("Monandroid", root.getValue("app").jsonPrimitive.content)
        assertEquals("49BE1LKS...YDucDVCt", firstProfile.getValue("walletAddressMasked").jsonPrimitive.content)
        assertEquals("RUNNING", miner.getValue("status").jsonPrimitive.content)
        assertEquals(
            "password=[redacted] user=49BE1LKS...YDucDVCt",
            miner.getValue("lastError").jsonPrimitive.content,
        )
        assertEquals(2, logs.size)
        assertFalse(diagnosticsJson.contains(wallet))
        assertFalse(diagnosticsJson.contains(password))
        assertTrue(diagnosticsJson.contains("49BE1LKS...YDucDVCt"))
        assertTrue(diagnosticsJson.contains("password=[redacted]"))
    }

    @Test
    fun buildDiagnosticsExportFileName_returnsStableJsonName() {
        assertEquals("monandroid-diagnostics.json", buildDiagnosticsExportFileName())
    }
}
