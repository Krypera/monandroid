package com.monandroido.app.ui

import com.monandroido.app.ui.screens.profiles.profileDraftWithAdvancedText
import com.monandroido.data.model.AdvancedMinerSettings
import com.monandroido.data.model.AlgorithmMode
import com.monandroido.data.model.BackupPool
import com.monandroido.data.model.ProfileDraft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileDraftParsingTest {
    @Test
    fun profileDraftWithAdvancedText_normalizesPrimaryPoolScheme() {
        val normalizedDraft = profileDraftWithAdvancedText(
            draft = ProfileDraft(
                name = "Phone",
                primaryPoolUrl = "stratum+ssl://pool.example.com:3333",
                walletAddress = "wallet",
                tls = false,
            ),
            backupPoolsText = "",
            algorithmMode = AlgorithmMode.RX0,
            maxThreadsHint = 75,
            retryCount = 5,
            retryPauseSeconds = 5,
            continueWhenScreenOff = false,
            requireCharging = false,
            keepAlive = true,
        )

        assertEquals("pool.example.com:3333", normalizedDraft.primaryPoolUrl)
        assertTrue(normalizedDraft.tls)
        assertTrue(normalizedDraft.advancedSettings.keepAlive)
    }

    @Test
    fun parseBackupPools_inheritsDefaultsButHonorsExplicitSchemes() {
        val pools = parseBackupPools(
            multiline = "backup-one.example.com:5555\nstratum+tcp://backup-two.example.com:6666",
            defaultTls = true,
            defaultKeepAlive = false,
        )

        assertEquals(2, pools.size)
        assertEquals("backup-one.example.com:5555", pools[0].url)
        assertTrue(pools[0].tls)
        assertFalse(pools[0].keepAlive)
        assertEquals("backup-two.example.com:6666", pools[1].url)
        assertFalse(pools[1].tls)
        assertFalse(pools[1].keepAlive)
    }

    @Test
    fun backupPoolsAsText_preservesTlsOverrides() {
        val text = backupPoolsAsText(
            settings = AdvancedMinerSettings(
                backupPools = listOf(
                    BackupPool(url = "backup-one.example.com:5555", tls = true),
                    BackupPool(url = "backup-two.example.com:6666", tls = false),
                ),
            ),
            defaultTls = true,
        )

        assertEquals(
            "backup-one.example.com:5555\nstratum+tcp://backup-two.example.com:6666",
            text,
        )
    }
}
