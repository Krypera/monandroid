package com.monandroido.app.ui

import com.monandroido.data.model.BenchmarkResult
import com.monandroido.data.model.MiningProfileSummary
import com.monandroido.miner.BuildConfig as MinerBuildConfig
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

data class DiagnosticsExportSummary(
    val profileCount: Int,
    val benchmarkCount: Int,
    val logLineCount: Int,
)

data class AppDiagnosticsMetadata(
    val applicationId: String,
    val versionName: String,
    val versionCode: Long,
)

internal fun buildDiagnosticsExportFileName(): String = "monandroid-diagnostics.json"

internal fun buildDiagnosticsJson(
    appMetadata: AppDiagnosticsMetadata,
    homeUiState: HomeUiState,
    profilesUiState: ProfilesUiState,
    benchmarkUiState: BenchmarkUiState,
    settingsUiState: SettingsUiState,
    exportedAtMillis: Long,
): String {
    val profileLabels = buildProfileLabels(profilesUiState.profiles)
    val sensitiveTokens = buildSensitiveLogTokens(profilesUiState.profiles)
    val redactedLogs = sanitizeRecentLogsForDiagnostics(
        recentLogs = homeUiState.recentLogs,
        sensitiveTokens = sensitiveTokens,
    )
    val redactedLastError = homeUiState.minerState.lastError?.let { error ->
        sanitizeLogLineForDiagnostics(error, sensitiveTokens)
    }
    val payload = buildJsonObject {
        put("app", "Monandroid")
        put("formatVersion", 2)
        put("exportedAt", exportedAtMillis)
        put("containsSecrets", false)
        put(
            "build",
            buildJsonObject {
                put("applicationId", appMetadata.applicationId)
                put("versionName", appMetadata.versionName)
                put("versionCode", appMetadata.versionCode)
            },
        )
        put(
            "settings",
            buildJsonObject {
                put("requestedDeveloperFeePercent", settingsUiState.requestedDeveloperFeePercent)
                put("effectiveDeveloperFeePercent", settingsUiState.effectiveDeveloperFeePercent)
                put("advancedModeEnabled", settingsUiState.advancedModeEnabled)
                put("developerWalletConfigured", settingsUiState.developerWalletConfigured)
            },
        )
        put(
            "profiles",
            buildJsonObject {
                put("totalCount", profilesUiState.profiles.size)
                putNullable("activeProfileId", homeUiState.activeProfileId?.let(::JsonPrimitive))
                put(
                    "items",
                    JsonArray(
                        profilesUiState.profiles.mapIndexed { index, profile ->
                            profile.toDiagnosticsJson(
                                label = profileLabels[profile.id] ?: buildProfileLabel(index),
                            )
                        },
                    ),
                )
            },
        )
        put(
            "miner",
            buildJsonObject {
                put("status", homeUiState.minerState.status.name)
                putNullable(
                    "activeProfileLabel",
                    homeUiState.minerState.activeProfileId
                        ?.let(profileLabels::get)
                        ?.let(::JsonPrimitive),
                )
                put("currentFeeMode", homeUiState.minerState.currentFeeMode.name)
                put("hashrateHps", homeUiState.minerState.hashrateHps)
                put("acceptedShares", homeUiState.minerState.acceptedShares)
                put("rejectedShares", homeUiState.minerState.rejectedShares)
                put("uptimeMillis", homeUiState.minerState.uptimeMillis)
                putNullable("poolAddress", homeUiState.minerState.poolAddress?.let(::JsonPrimitive))
                putNullable("lastError", redactedLastError?.let(::JsonPrimitive))
                putNullable("pauseReason", homeUiState.minerState.pauseReason?.let(::JsonPrimitive))
                put("developerFeePercent", homeUiState.minerState.developerFeePercent)
                put("sessionUserMillis", homeUiState.minerState.sessionUserMillis)
                put("sessionDeveloperMillis", homeUiState.minerState.sessionDeveloperMillis)
                putNullable(
                    "benchmarkAlgorithm",
                    homeUiState.minerState.benchmarkAlgorithm?.xmrigValue?.let(::JsonPrimitive),
                )
                putNullable(
                    "benchmarkPreset",
                    homeUiState.minerState.benchmarkPreset?.sizeArg?.let(::JsonPrimitive),
                )
                putNullable(
                    "benchmarkHashrateHps",
                    homeUiState.minerState.benchmarkHashrate?.let(::JsonPrimitive),
                )
            },
        )
        put(
            "benchmark",
            buildJsonObject {
                put("totalRuns", benchmarkUiState.historySummary.totalRuns)
                put("algorithmsCovered", benchmarkUiState.historySummary.algorithmsCovered)
                putNullable(
                    "averageHashrateHps",
                    benchmarkUiState.historySummary.averageHashrate?.let(::JsonPrimitive),
                )
                putNullable(
                    "bestHashrateHps",
                    benchmarkUiState.historySummary.bestResult?.avgHashrate?.let(::JsonPrimitive),
                )
                putNullable(
                    "latestRecordedAt",
                    benchmarkUiState.historySummary.latestResult?.createdAt?.let(::JsonPrimitive),
                )
                put(
                    "recentResults",
                    buildJsonArray {
                        benchmarkUiState.benchmarkResults
                            .sortedWith(compareByDescending<BenchmarkResult> { it.createdAt }.thenByDescending { it.id })
                            .take(10)
                            .forEach { result ->
                                add(result.toDiagnosticsJson())
                            }
                    },
                )
            },
        )
        put(
            "recentLogs",
            buildJsonArray {
                redactedLogs.forEach { line ->
                    add(JsonPrimitive(line))
                }
            },
        )
    }

    return Json {
        prettyPrint = true
        explicitNulls = false
    }.encodeToString(JsonObject.serializer(), payload)
}

private fun MiningProfileSummary.toDiagnosticsJson(label: String): JsonObject = buildJsonObject {
    put("id", id)
    put("label", label)
    put("primaryPoolUrl", primaryPoolUrl)
    put("walletAddressMasked", maskWalletForDiagnostics(walletAddress))
    put("hasRigId", !rigId.isNullOrBlank())
    put("tls", tls)
    put("enabled", enabled)
    put("algorithm", advancedSettings.algorithmMode.xmrigValue)
    put("keepAlive", advancedSettings.keepAlive)
    put("backupPoolCount", advancedSettings.backupPools.size)
}

private fun buildProfileLabels(
    profiles: List<MiningProfileSummary>,
): Map<Long, String> = profiles.mapIndexed { index, profile ->
    profile.id to buildProfileLabel(index)
}.toMap()

private fun buildProfileLabel(index: Int): String = "Profile ${index + 1}"

private fun BenchmarkResult.toDiagnosticsJson(): JsonObject = buildJsonObject {
    put("recordedAt", createdAt)
    put("algorithm", algorithmMode.xmrigValue)
    put("preset", preset.sizeArg)
    put("avgHashrateHps", avgHashrate)
    put("durationMillis", durationMillis)
    putNullable("batteryDeltaPercent", batteryDeltaPercent?.let(::JsonPrimitive))
    putNullable("peakThermal", peakThermal?.let(::JsonPrimitive))
}

private fun maskWalletForDiagnostics(walletAddress: String): String {
    val normalized = walletAddress.trim()
    if (normalized.length <= 18) {
        return normalized
    }
    return "${normalized.take(8)}...${normalized.takeLast(8)}"
}

private fun sanitizeRecentLogsForDiagnostics(
    recentLogs: List<String>,
    sensitiveTokens: Set<String>,
): List<String> = recentLogs.map { line ->
        sanitizeLogLineForDiagnostics(line, sensitiveTokens)
    }

private fun buildSensitiveLogTokens(
    profiles: List<MiningProfileSummary>,
): Set<String> = buildSet {
    profiles
        .map { it.walletAddress.trim() }
        .filter { it.isNotEmpty() }
        .forEach(::add)
    normalizedDeveloperWallet()?.let(::add)
}

private fun sanitizeLogLineForDiagnostics(
    line: String,
    sensitiveTokens: Set<String>,
): String {
    var sanitized = line
    sensitiveTokens
        .sortedByDescending { it.length }
        .forEach { token ->
            sanitized = sanitized.replace(token, maskWalletForDiagnostics(token))
        }
    sanitized = JSON_SECRET_REGEX.replace(sanitized) { match ->
        "${match.groupValues[1]}[redacted]${match.groupValues[3]}"
    }
    sanitized = KEY_VALUE_SECRET_REGEX.replace(sanitized) { match ->
        "${match.groupValues[1]}[redacted]"
    }
    sanitized = LONG_BASE58_TOKEN_REGEX.replace(sanitized) { match ->
        maskWalletForDiagnostics(match.value)
    }
    return sanitized
}

private fun normalizedDeveloperWallet(): String? {
    val normalized = MinerBuildConfig.DEVELOPER_WALLET.trim()
    return normalized.takeIf {
        it.isNotEmpty() && it != "REPLACE_WITH_YOUR_XMR_WALLET"
    }
}

private val JSON_SECRET_REGEX = Regex(
    """("(?:pass|password)"\s*:\s*")([^"]*)(")""",
    RegexOption.IGNORE_CASE,
)

private val KEY_VALUE_SECRET_REGEX = Regex(
    """\b((?:pass|password)\s*=\s*)([^\s,;]+)""",
    RegexOption.IGNORE_CASE,
)

private val LONG_BASE58_TOKEN_REGEX = Regex("""\b[1-9A-HJ-NP-Za-km-z]{80,120}\b""")

private fun kotlinx.serialization.json.JsonObjectBuilder.putNullable(
    key: String,
    value: JsonElement?,
) {
    if (value != null) {
        put(key, value)
    }
}
