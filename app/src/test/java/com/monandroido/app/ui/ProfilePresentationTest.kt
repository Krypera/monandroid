package com.monandroido.app.ui

import com.monandroido.app.ui.screens.profiles.buildProfileMetaSummary
import com.monandroido.app.ui.screens.profiles.maskWalletAddressForList
import com.monandroido.data.model.AdvancedMinerSettings
import com.monandroido.data.model.AlgorithmMode
import com.monandroido.data.model.BackupPool
import com.monandroido.data.model.MiningProfileSummary
import org.junit.Assert.assertEquals
import org.junit.Test

class ProfilePresentationTest {
    @Test
    fun maskWalletAddressForList_masksLongWallets() {
        val masked = maskWalletAddressForList(
            "49BE1LKSDDriFWNb1TSeW98y5GZjbT1HJJ2LdiUGieMVXRo6Bm4gop1dRLr1mvtxCpSNewpAUNNzeBM8RtNiTf4YDucDVCt",
        )

        assertEquals("49BE1LKS...YDucDVCt", masked)
    }

    @Test
    fun buildProfileMetaSummary_includesTlsAlgorithmAndBackupCount() {
        val summary = buildProfileMetaSummary(
            profile = MiningProfileSummary(
                id = 1L,
                name = "Phone",
                primaryPoolUrl = "gulf.moneroocean.stream:10128",
                walletAddress = "49abc",
                rigId = "pixel",
                tls = true,
                enabled = true,
                advancedSettings = AdvancedMinerSettings(
                    algorithmMode = AlgorithmMode.RXWOW,
                    backupPools = listOf(
                        BackupPool(url = "backup-a:3333", tls = true, keepAlive = true),
                        BackupPool(url = "backup-b:3333", tls = true, keepAlive = true),
                    ),
                ),
            ),
            algorithmLabel = "RandomX / Wownero",
            tlsEnabledLabel = "On",
            tlsDisabledLabel = "Off",
            summaryTemplate = "Algorithm: %1\$s | TLS: %2\$s | Backup pools: %3\$d",
        )

        assertEquals("Algorithm: RandomX / Wownero | TLS: On | Backup pools: 2", summary)
    }
}
