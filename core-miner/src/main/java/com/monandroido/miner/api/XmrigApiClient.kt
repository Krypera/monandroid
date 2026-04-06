package com.monandroido.miner.api

import com.monandroido.miner.model.XmrigSummarySnapshot
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class XmrigApiClient(
    private val apiPort: Int,
    private val apiToken: String,
    private val client: OkHttpClient = defaultClient(),
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun pause(): Boolean {
        return postJsonRpc("pause")
    }

    fun resume(): Boolean {
        return postJsonRpc("resume")
    }

    fun applyConfig(configJson: String): Boolean {
        val request = Request.Builder()
            .url("http://127.0.0.1:$apiPort/2/config")
            .put(configJson.toRequestBody(JSON_MEDIA_TYPE))
            .header("Authorization", "Bearer $apiToken")
            .header("Content-Type", "application/json")
            .build()
        return runCatching { client.newCall(request).execute().use { it.isSuccessful } }.getOrDefault(false)
    }

    fun fetchSummary(): XmrigSummarySnapshot {
        val request = Request.Builder()
            .url("http://127.0.0.1:$apiPort/2/summary")
            .header("Authorization", "Bearer $apiToken")
            .build()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("Unexpected summary response ${response.code}")
            parseSummary(response.body?.string().orEmpty())
        }
    }

    private fun postJsonRpc(method: String): Boolean {
        val requestBody = buildJsonObject {
            put("method", method)
            put("id", 1)
        }.toString()
        val request = Request.Builder()
            .url("http://127.0.0.1:$apiPort/json_rpc")
            .post(requestBody.toRequestBody(JSON_MEDIA_TYPE))
            .header("Authorization", "Bearer $apiToken")
            .header("Content-Type", "application/json")
            .build()
        return runCatching {
            client.newCall(request).execute().use { it.isSuccessful }
        }.getOrDefault(false)
    }

    private fun parseSummary(rawJson: String): XmrigSummarySnapshot {
        val root = json.parseToJsonElement(rawJson) as? JsonObject ?: return XmrigSummarySnapshot()
        val results = root["results"] as? JsonObject
        val connection = root["connection"] as? JsonObject
        val hashrate = root["hashrate"] as? JsonObject

        val totalHashrate = hashrate?.arrayValue("total")?.firstOrNull()?.primitiveOrNull()?.doubleOrNull
            ?: hashrate?.primitiveValue("total")?.doubleOrNull

        val accepted = results?.primitiveValue("shares_good")?.intOrNull
            ?: results?.primitiveValue("accepted")?.intOrNull

        val rejectedDirect = results?.primitiveValue("rejected")?.intOrNull
        val rejected = rejectedDirect ?: run {
            val total = results?.primitiveValue("shares_total")?.intOrNull
            val good = results?.primitiveValue("shares_good")?.intOrNull
            if (total != null && good != null) total - good else null
        }

        val uptimeMillis = connection?.primitiveValue("uptime_ms")?.longOrNull
            ?: connection?.primitiveValue("uptime")?.longOrNull?.times(1000)

        return XmrigSummarySnapshot(
            hashrateHps = totalHashrate,
            acceptedShares = accepted,
            rejectedShares = rejected,
            uptimeMillis = uptimeMillis,
            poolAddress = connection?.primitiveValue("pool")?.content,
            lastError = connection?.primitiveValue("error_log")?.content,
        )
    }

    private fun JsonObject.primitiveValue(key: String): JsonPrimitive? =
        this[key]?.primitiveOrNull()

    private fun JsonObject.arrayValue(key: String): JsonArray? =
        this[key] as? JsonArray

    private fun JsonElement.primitiveOrNull(): JsonPrimitive? = this as? JsonPrimitive

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        private fun defaultClient(): OkHttpClient =
            OkHttpClient.Builder()
                .connectTimeout(2, TimeUnit.SECONDS)
                .readTimeout(2, TimeUnit.SECONDS)
                .writeTimeout(2, TimeUnit.SECONDS)
                .callTimeout(5, TimeUnit.SECONDS)
                .build()
    }
}
