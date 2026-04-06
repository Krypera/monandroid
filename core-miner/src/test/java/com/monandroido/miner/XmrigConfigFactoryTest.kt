package com.monandroido.miner

import com.google.common.truth.Truth.assertThat
import com.monandroido.data.model.AdvancedMinerSettings
import com.monandroido.data.model.AlgorithmMode
import com.monandroido.data.model.BackupPool
import com.monandroido.data.model.MiningProfile
import com.monandroido.miner.config.XmrigConfigFactory
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test

class XmrigConfigFactoryTest {
    private val json = Json

    @Test
    fun miningConfig_containsDeveloperWalletOverride() {
        val config = XmrigConfigFactory().buildMiningConfig(
            profile = MiningProfile(
                id = 1,
                name = "Home",
                primaryPoolUrl = "pool.example.com:3333",
                walletAddress = "user_wallet",
                password = "x",
                rigId = "phone",
                tls = false,
                enabled = true,
                advancedSettings = AdvancedMinerSettings(
                    algorithmMode = AlgorithmMode.RX0,
                    maxThreadsHint = 50,
                ),
            ),
            apiPort = 50080,
            apiToken = "token",
            walletOverride = "dev_wallet",
        )

        assertThat(config).contains("dev_wallet")
        assertThat(config).contains("\"donate-level\": 0")
        assertThat(config).contains("\"algo\": \"rx/0\"")
    }

    @Test
    fun miningConfig_includesBackupPoolsAndApiSettings() {
        val config = XmrigConfigFactory().buildMiningConfig(
            profile = MiningProfile(
                id = 1,
                name = "Home",
                primaryPoolUrl = "pool.example.com:3333",
                walletAddress = "user_wallet",
                password = "secret",
                rigId = "phone",
                tls = true,
                enabled = true,
                advancedSettings = AdvancedMinerSettings(
                    algorithmMode = AlgorithmMode.RXWOW,
                    maxThreadsHint = 80,
                    backupPools = listOf(
                        BackupPool(url = "backup.example.com:4444", tls = true, keepAlive = false),
                    ),
                ),
            ),
            apiPort = 50080,
            apiToken = "token",
        )

        assertThat(config).contains("\"url\": \"backup.example.com:4444\"")
        assertThat(config).contains("\"access-token\": \"token\"")
        assertThat(config).contains("\"algo\": \"rx/wow\"")
    }

    @Test
    fun miningConfig_normalizesPrimaryPoolSchemeIntoTlsFlag() {
        val config = XmrigConfigFactory().buildMiningConfig(
            profile = MiningProfile(
                id = 1,
                name = "Home",
                primaryPoolUrl = "stratum+ssl://pool.example.com:4444",
                walletAddress = "user_wallet",
                password = "x",
                rigId = "phone",
                tls = false,
                enabled = true,
                advancedSettings = AdvancedMinerSettings(
                    algorithmMode = AlgorithmMode.RX0,
                    maxThreadsHint = 50,
                ),
            ),
            apiPort = 50080,
            apiToken = "token",
        )

        val primaryPool = json.parseToJsonElement(config)
            .jsonObject.getValue("pools")
            .jsonArray
            .first()
            .jsonObject

        assertThat(primaryPool.getValue("url").jsonPrimitive.content).isEqualTo("pool.example.com:4444")
        assertThat(primaryPool.getValue("tls").jsonPrimitive.boolean).isTrue()
    }

    @Test
    fun miningConfig_normalizesLegacyBackupPoolSchemeIntoTlsFlag() {
        val config = XmrigConfigFactory().buildMiningConfig(
            profile = MiningProfile(
                id = 1,
                name = "Home",
                primaryPoolUrl = "pool.example.com:3333",
                walletAddress = "user_wallet",
                password = "secret",
                rigId = "phone",
                tls = true,
                enabled = true,
                advancedSettings = AdvancedMinerSettings(
                    algorithmMode = AlgorithmMode.RXWOW,
                    maxThreadsHint = 80,
                    backupPools = listOf(
                        BackupPool(url = "stratum+tcp://backup.example.com:4444", tls = true, keepAlive = false),
                    ),
                ),
            ),
            apiPort = 50080,
            apiToken = "token",
        )

        val backupPool = json.parseToJsonElement(config)
            .jsonObject.getValue("pools")
            .jsonArray[1]
            .jsonObject

        assertThat(backupPool.getValue("url").jsonPrimitive.content).isEqualTo("backup.example.com:4444")
        assertThat(backupPool.getValue("tls").jsonPrimitive.boolean).isFalse()
        assertThat(backupPool.getValue("keepalive").jsonPrimitive.boolean).isFalse()
    }
}
