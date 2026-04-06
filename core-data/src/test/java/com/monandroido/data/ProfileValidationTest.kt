package com.monandroido.data

import com.google.common.truth.Truth.assertThat
import com.monandroido.data.R
import com.monandroido.data.model.AdvancedMinerSettings
import com.monandroido.data.model.BackupPool
import com.monandroido.data.model.ProfileDraft
import com.monandroido.data.model.validate
import org.junit.Test

class ProfileValidationTest {
    @Test
    fun blankRequiredFields_areRejected() {
        val result = ProfileDraft().validate()

        assertThat(result.isValid).isFalse()
        assertThat(result.nameError).isNotNull()
        assertThat(result.poolError).isNotNull()
        assertThat(result.walletError).isNotNull()
    }

    @Test
    fun bareHostAndPortEndpoint_isAccepted() {
        val result = ProfileDraft(
            name = "Phone",
            primaryPoolUrl = "pool.example.com:3333",
            walletAddress = "wallet",
        ).validate()

        assertThat(result.isValid).isTrue()
    }

    @Test
    fun sslPoolScheme_isAccepted() {
        val result = ProfileDraft(
            name = "Phone",
            primaryPoolUrl = "STRATUM+SSL://pool.example.com:3333",
            walletAddress = "wallet",
        ).validate()

        assertThat(result.isValid).isTrue()
    }

    @Test
    fun invalidBackupPool_isRejected() {
        val result = ProfileDraft(
            name = "Phone",
            primaryPoolUrl = "stratum+tcp://pool.example.com:3333",
            walletAddress = "wallet",
            advancedSettings = AdvancedMinerSettings(
                backupPools = listOf(BackupPool(url = "missing-port")),
            ),
        ).validate()

        assertThat(result.isValid).isFalse()
        assertThat(result.backupPoolsError?.formatArgs).contains("missing-port")
    }

    @Test
    fun walletWithWhitespace_isRejected() {
        val result = ProfileDraft(
            name = "Phone",
            primaryPoolUrl = "pool.example.com:3333",
            walletAddress = "wallet value",
        ).validate()

        assertThat(result.isValid).isFalse()
        assertThat(result.walletError?.resId).isEqualTo(R.string.profile_validation_wallet_whitespace)
    }

    @Test
    fun duplicateBackupPools_areRejected() {
        val result = ProfileDraft(
            name = "Phone",
            primaryPoolUrl = "pool.example.com:3333",
            walletAddress = "wallet",
            advancedSettings = AdvancedMinerSettings(
                backupPools = listOf(
                    BackupPool(url = "backup.example.com:4444", tls = false),
                    BackupPool(url = "stratum+tcp://backup.example.com:4444", tls = true),
                ),
            ),
        ).validate()

        assertThat(result.isValid).isFalse()
        assertThat(result.backupPoolsError?.resId).isEqualTo(R.string.profile_validation_backup_duplicates_existing)
    }

    @Test
    fun backupPoolMatchingPrimary_isRejected() {
        val result = ProfileDraft(
            name = "Phone",
            primaryPoolUrl = "stratum+ssl://pool.example.com:4444",
            walletAddress = "wallet",
            tls = false,
            advancedSettings = AdvancedMinerSettings(
                backupPools = listOf(
                    BackupPool(url = "pool.example.com:4444", tls = true),
                ),
            ),
        ).validate()

        assertThat(result.isValid).isFalse()
        assertThat(result.backupPoolsError?.resId).isEqualTo(R.string.profile_validation_backup_duplicates_primary)
    }
}
