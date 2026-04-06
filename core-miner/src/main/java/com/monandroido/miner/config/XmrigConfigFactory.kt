package com.monandroido.miner.config

import com.monandroido.data.model.MiningProfile
import com.monandroido.data.model.parsePoolEndpoint
import com.monandroido.miner.model.BenchmarkRequest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

class XmrigConfigFactory {
    private val json = Json { prettyPrint = true }

    fun buildMiningConfig(
        profile: MiningProfile,
        apiPort: Int,
        apiToken: String,
        walletOverride: String? = null,
    ): String {
        val pools = buildList {
            add(
                buildPool(
                    url = profile.primaryPoolUrl,
                    user = walletOverride ?: profile.walletAddress,
                    password = profile.password,
                    rigId = profile.rigId,
                    tls = profile.tls,
                    keepAlive = profile.advancedSettings.keepAlive,
                    algorithm = profile.advancedSettings.algorithmMode.xmrigValue,
                ),
            )
            profile.advancedSettings.backupPools.forEach { backup ->
                add(
                    buildPool(
                        url = backup.url,
                        user = walletOverride ?: profile.walletAddress,
                        password = profile.password,
                        rigId = profile.rigId,
                        tls = backup.tls,
                        keepAlive = backup.keepAlive,
                        algorithm = profile.advancedSettings.algorithmMode.xmrigValue,
                    ),
                )
            }
        }

        val config = buildJsonObject {
            putJsonObject("api") {
                put("id", JsonNull)
                put("worker-id", JsonNull)
            }
            putJsonObject("http") {
                put("enabled", JsonPrimitive(true))
                put("host", JsonPrimitive("127.0.0.1"))
                put("port", JsonPrimitive(apiPort))
                put("access-token", JsonPrimitive(apiToken))
                put("restricted", JsonPrimitive(false))
            }
            put("autosave", JsonPrimitive(false))
            put("background", JsonPrimitive(false))
            put("colors", JsonPrimitive(false))
            put("title", JsonPrimitive(false))
            putJsonObject("randomx") {
                put("init", JsonPrimitive(-1))
                put("mode", JsonPrimitive("auto"))
                put("1gb-pages", JsonPrimitive(false))
                put("rdmsr", JsonPrimitive(false))
                put("wrmsr", JsonPrimitive(false))
                put("cache_qos", JsonPrimitive(false))
                put("numa", JsonPrimitive(false))
            }
            putJsonObject("cpu") {
                put("enabled", JsonPrimitive(true))
                put("huge-pages", JsonPrimitive(false))
                put("huge-pages-jit", JsonPrimitive(false))
                put("yield", JsonPrimitive(true))
                put("priority", JsonPrimitive(0))
                put("max-threads-hint", JsonPrimitive(profile.advancedSettings.maxThreadsHint))
                put("asm", JsonPrimitive(true))
                put("argon2-impl", JsonNull)
            }
            put("opencl", disabledBackend())
            put("cuda", disabledBackend())
            put("donate-level", JsonPrimitive(0))
            put("donate-over-proxy", JsonPrimitive(0))
            put("pools", JsonArray(pools))
            put("print-time", JsonPrimitive(60))
            put("health-print-time", JsonPrimitive(60))
            put("retries", JsonPrimitive(profile.advancedSettings.retryCount))
            put("retry-pause", JsonPrimitive(profile.advancedSettings.retryPauseSeconds))
            put("syslog", JsonPrimitive(false))
            put("watch", JsonPrimitive(false))
            put("verbose", JsonPrimitive(0))
            put("pause-on-battery", JsonPrimitive(false))
            put("pause-on-active", JsonPrimitive(false))
        }

        return json.encodeToString(JsonObject.serializer(), config)
    }

    fun buildBenchmarkArgs(request: BenchmarkRequest): List<String> = listOf(
        "--bench=${request.preset.sizeArg}",
        "--algo=${request.algorithmMode.xmrigValue}",
        "--no-color",
    )

    private fun buildPool(
        url: String,
        user: String,
        password: String,
        rigId: String?,
        tls: Boolean,
        keepAlive: Boolean,
        algorithm: String,
    ): JsonObject = buildJsonObject {
        val resolvedPool = resolvePool(url = url, tls = tls)
        put("algo", JsonPrimitive(algorithm))
        put("coin", JsonNull)
        put("url", JsonPrimitive(resolvedPool.url))
        put("user", JsonPrimitive(user))
        put("pass", JsonPrimitive(password))
        put("rig-id", rigId?.let(::JsonPrimitive) ?: JsonNull)
        put("nicehash", JsonPrimitive(false))
        put("keepalive", JsonPrimitive(keepAlive))
        put("enabled", JsonPrimitive(true))
        put("tls", JsonPrimitive(resolvedPool.tls))
        put("tls-fingerprint", JsonNull)
        put("daemon", JsonPrimitive(false))
        put("socks5", JsonNull)
        put("self-select", JsonNull)
        put("submit-to-origin", JsonPrimitive(false))
    }

    private fun disabledBackend(): JsonObject = buildJsonObject {
        put("enabled", JsonPrimitive(false))
    }

    private fun resolvePool(
        url: String,
        tls: Boolean,
    ): ResolvedPool {
        val parsed = parsePoolEndpoint(url)
        return ResolvedPool(
            url = parsed?.normalizedUrl ?: url.trim(),
            tls = parsed?.tlsOverride ?: tls,
        )
    }

    private data class ResolvedPool(
        val url: String,
        val tls: Boolean,
    )
}
