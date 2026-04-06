package com.monandroido.miner

import com.google.common.truth.Truth.assertThat
import com.monandroido.miner.policy.DEVELOPER_WALLET_PLACEHOLDER
import com.monandroido.miner.policy.effectiveDeveloperFeePercent
import com.monandroido.miner.policy.normalizedDeveloperWallet
import org.junit.Test

class MiningPolicyTest {
    @Test
    fun missingDeveloperWallet_forcesEffectiveFeeToZero() {
        assertThat(
            effectiveDeveloperFeePercent(
                requestedPercent = 25,
                developerWalletConfigured = false,
            ),
        ).isEqualTo(0)
    }

    @Test
    fun configuredDeveloperWallet_keepsRequestedFeeWithinBounds() {
        assertThat(
            effectiveDeveloperFeePercent(
                requestedPercent = 125,
                developerWalletConfigured = true,
            ),
        ).isEqualTo(100)
    }

    @Test
    fun blankDeveloperWallet_isTreatedAsMissing() {
        assertThat(normalizedDeveloperWallet("   ")).isNull()
    }

    @Test
    fun placeholderDeveloperWallet_isTreatedAsMissing() {
        assertThat(normalizedDeveloperWallet(DEVELOPER_WALLET_PLACEHOLDER)).isNull()
    }

    @Test
    fun validDeveloperWallet_isTrimmedAndReturned() {
        assertThat(normalizedDeveloperWallet("  49abc123  ")).isEqualTo("49abc123")
    }
}
