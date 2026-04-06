package com.monandroido.miner.policy

internal const val DEVELOPER_WALLET_PLACEHOLDER = "REPLACE_WITH_YOUR_XMR_WALLET"

internal fun effectiveDeveloperFeePercent(
    requestedPercent: Int,
    developerWalletConfigured: Boolean,
): Int = if (developerWalletConfigured) {
    requestedPercent.coerceIn(0, 100)
} else {
    0
}

internal fun normalizedDeveloperWallet(rawWallet: String): String? {
    val normalized = rawWallet.trim()
    return normalized.takeIf {
        it.isNotEmpty() && it != DEVELOPER_WALLET_PLACEHOLDER
    }
}
