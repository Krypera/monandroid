package com.monandroido.data

import com.google.common.truth.Truth.assertThat
import com.monandroido.data.model.AdvancedMinerSettings
import com.monandroido.data.model.AlgorithmMode
import com.monandroido.data.model.AppSettings
import com.monandroido.data.model.BackupPool
import com.monandroido.data.model.MiningProfile
import com.monandroido.data.model.ProfileTransferCodec
import org.junit.Test

class ProfileTransferCodecTest {
    @Test
    fun encodeThenDecode_preservesProfileSettings() {
        val rawJson = ProfileTransferCodec.encode(
            MiningProfile(
                id = 42,
                name = "Phone Rig",
                primaryPoolUrl = "pool.example.com:443",
                walletAddress = "wallet-address",
                password = "x",
                rigId = "phone-rig",
                tls = true,
                enabled = true,
                advancedSettings = AdvancedMinerSettings(
                    algorithmMode = AlgorithmMode.RXWOW,
                    maxThreadsHint = 65,
                    retryCount = 7,
                    retryPauseSeconds = 11,
                    continueWhenScreenOff = true,
                    requireCharging = true,
                    keepAlive = false,
                    backupPools = listOf(
                        BackupPool("backup.example.com:3333", tls = false, keepAlive = false),
                    ),
                ),
            ),
        )

        val importedDraft = ProfileTransferCodec.decode(rawJson)

        assertThat(importedDraft.id).isNull()
        assertThat(importedDraft.name).isEqualTo("Phone Rig")
        assertThat(importedDraft.primaryPoolUrl).isEqualTo("pool.example.com:443")
        assertThat(importedDraft.walletAddress).isEqualTo("wallet-address")
        assertThat(importedDraft.password).isEqualTo("x")
        assertThat(importedDraft.rigId).isEqualTo("phone-rig")
        assertThat(importedDraft.tls).isTrue()
        assertThat(importedDraft.advancedSettings.algorithmMode).isEqualTo(AlgorithmMode.RXWOW)
        assertThat(importedDraft.advancedSettings.backupPools).hasSize(1)
        assertThat(importedDraft.advancedSettings.keepAlive).isFalse()
    }

    @Test
    fun encodeProfilesThenDecodeProfiles_preservesMultipleEntries() {
        val importedTransfer = ProfileTransferCodec.decodeTransfer(
            ProfileTransferCodec.encodeProfiles(
                listOf(
                    MiningProfile(
                        id = 1,
                        name = "Phone",
                        primaryPoolUrl = "pool-a.example.com:3333",
                        walletAddress = "wallet-a",
                        password = "x",
                        rigId = "phone",
                        tls = false,
                        enabled = true,
                        advancedSettings = AdvancedMinerSettings(),
                    ),
                    MiningProfile(
                        id = 2,
                        name = "Tablet",
                        primaryPoolUrl = "pool-b.example.com:4444",
                        walletAddress = "wallet-b",
                        password = "secret",
                        rigId = "tablet",
                        tls = true,
                        enabled = false,
                        advancedSettings = AdvancedMinerSettings(algorithmMode = AlgorithmMode.RXWOW),
                    ),
                ),
                settings = AppSettings(
                    developerFeePercent = 25,
                    advancedModeEnabled = true,
                ),
                activeProfileIndex = 1,
            ),
        )
        val importedDrafts = importedTransfer.profiles

        assertThat(importedDrafts).hasSize(2)
        assertThat(importedDrafts[0].name).isEqualTo("Phone")
        assertThat(importedDrafts[1].name).isEqualTo("Tablet")
        assertThat(importedDrafts[1].tls).isTrue()
        assertThat(importedDrafts[1].enabled).isFalse()
        assertThat(importedDrafts[1].advancedSettings.algorithmMode).isEqualTo(AlgorithmMode.RXWOW)
        assertThat(importedTransfer.settings).isEqualTo(
            AppSettings(
                developerFeePercent = 25,
                advancedModeEnabled = true,
            ),
        )
        assertThat(importedTransfer.activeProfileIndex).isEqualTo(1)
        assertThat(importedTransfer.containsSecrets).isTrue()
    }

    @Test
    fun decodeTransfer_supportsLegacyVersionOneFiles() {
        val importedTransfer = ProfileTransferCodec.decodeTransfer(
            """
            {
              "formatVersion": 1,
              "app": "Monandroido",
              "profile": {
                "name": "Legacy",
                "primaryPoolUrl": "pool.example.com:3333",
                "walletAddress": "wallet",
                "password": "x",
                "rigId": "legacy-phone",
                "tls": false,
                "enabled": true,
                "advancedSettings": {
                  "algorithmMode": "RX0",
                  "maxThreadsHint": 75,
                  "retryCount": 5,
                  "retryPauseSeconds": 5,
                  "continueWhenScreenOff": false,
                  "requireCharging": false,
                  "keepAlive": true,
                  "backupPools": []
                }
              }
            }
            """.trimIndent(),
        )

        assertThat(importedTransfer.profiles).hasSize(1)
        assertThat(importedTransfer.profiles.single().name).isEqualTo("Legacy")
        assertThat(importedTransfer.settings).isNull()
        assertThat(importedTransfer.activeProfileIndex).isNull()
    }

    @Test
    fun encodeProfiles_withoutSecrets_marksBackupAndStripsPasswordsAndRigId() {
        val importedTransfer = ProfileTransferCodec.decodeTransfer(
            ProfileTransferCodec.encodeProfiles(
                profiles = listOf(
                    MiningProfile(
                        id = 7,
                        name = "Phone",
                        primaryPoolUrl = "pool.example.com:3333",
                        walletAddress = "wallet-a",
                        password = "super-secret",
                        rigId = "phone",
                        tls = false,
                        enabled = true,
                        advancedSettings = AdvancedMinerSettings(),
                    ),
                ),
                includeSecrets = false,
            ),
        )

        assertThat(importedTransfer.containsSecrets).isFalse()
        assertThat(importedTransfer.profiles.single().password).isEmpty()
        assertThat(importedTransfer.profiles.single().rigId).isEmpty()
    }
}
