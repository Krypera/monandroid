package com.monandroido.data.model

import android.content.Context
import com.monandroido.data.R
import java.util.Locale

data class ProfileValidationResult(
    val nameError: LocalizedText? = null,
    val poolError: LocalizedText? = null,
    val walletError: LocalizedText? = null,
    val backupPoolsError: LocalizedText? = null,
    val maxThreadsHintError: LocalizedText? = null,
    val retryCountError: LocalizedText? = null,
    val retryPauseError: LocalizedText? = null,
) {
    val isValid: Boolean
        get() = listOf(
            nameError,
            poolError,
            walletError,
            backupPoolsError,
            maxThreadsHintError,
            retryCountError,
            retryPauseError,
        ).all { it == null }

    fun firstError(): LocalizedText? = listOf(
        nameError,
        poolError,
        walletError,
        backupPoolsError,
        maxThreadsHintError,
        retryCountError,
        retryPauseError,
    ).firstOrNull()

    fun firstErrorMessage(context: Context): String? = firstError()?.resolve(context)
}

fun ProfileDraft.validate(): ProfileValidationResult =
    validateProfileFields(
        name = name,
        primaryPoolUrl = primaryPoolUrl,
        walletAddress = walletAddress,
        tls = tls,
        advancedSettings = advancedSettings,
    )

fun MiningProfile.validate(): ProfileValidationResult =
    validateProfileFields(
        name = name,
        primaryPoolUrl = primaryPoolUrl,
        walletAddress = walletAddress,
        tls = tls,
        advancedSettings = advancedSettings,
    )

private fun validateProfileFields(
    name: String,
    primaryPoolUrl: String,
    walletAddress: String,
    tls: Boolean,
    advancedSettings: AdvancedMinerSettings,
): ProfileValidationResult {
    val normalizedName = name.trim()
    val normalizedPool = primaryPoolUrl.trim()
    val normalizedWallet = walletAddress.trim()
    val parsedPrimaryPool = parsePoolEndpoint(normalizedPool)
    val primaryPoolIdentity = parsedPrimaryPool?.identity(defaultTls = tls)

    val parsedBackupPools = advancedSettings.backupPools.map { backupPool ->
        val normalizedUrl = backupPool.url.trim()
        ParsedBackupPool(
            rawValue = normalizedUrl,
            parsed = normalizedUrl.takeIf(String::isNotBlank)?.let(::parsePoolEndpoint),
            effectiveTls = normalizedUrl.takeIf(String::isNotBlank)?.let { rawValue ->
                parsePoolEndpoint(rawValue)?.tlsOverride ?: backupPool.tls
            },
        )
    }

    val invalidBackupPool = parsedBackupPools.firstOrNull { backupPool ->
        backupPool.rawValue.isNotBlank() && backupPool.parsed == null
    }
    val duplicateBackupPool = parsedBackupPools
        .mapNotNull { backupPool ->
            backupPool.parsed?.identity(defaultTls = backupPool.effectiveTls ?: false)
                ?.let { identity -> identity to backupPool.rawValue }
        }
        .groupBy({ it.first }, { it.second })
        .entries
        .firstOrNull { (_, values) -> values.size > 1 }
        ?.value
        ?.firstOrNull()
    val backupMatchesPrimary = primaryPoolIdentity?.let { primaryIdentity ->
        parsedBackupPools.firstOrNull { backupPool ->
            val effectiveTls = backupPool.effectiveTls ?: return@firstOrNull false
            backupPool.parsed?.identity(defaultTls = effectiveTls) == primaryIdentity
        }?.rawValue
    }

    return ProfileValidationResult(
        nameError = if (normalizedName.isBlank()) {
            text(R.string.profile_validation_name_required)
        } else {
            null
        },
        poolError = when {
            normalizedPool.isBlank() -> text(R.string.profile_validation_pool_required)
            parsedPrimaryPool == null -> {
                text(R.string.profile_validation_pool_invalid)
            }
            else -> null
        },
        walletError = when {
            normalizedWallet.isBlank() -> text(R.string.profile_validation_wallet_required)
            normalizedWallet.any(Char::isWhitespace) -> text(R.string.profile_validation_wallet_whitespace)
            else -> null
        },
        backupPoolsError = when {
            invalidBackupPool != null -> text(
                R.string.profile_validation_backup_invalid,
                invalidBackupPool.rawValue,
            )
            backupMatchesPrimary != null -> text(
                R.string.profile_validation_backup_duplicates_primary,
                backupMatchesPrimary,
            )
            duplicateBackupPool != null -> text(
                R.string.profile_validation_backup_duplicates_existing,
                duplicateBackupPool,
            )
            else -> null
        },
        maxThreadsHintError = if (advancedSettings.maxThreadsHint !in 1..100) {
            text(R.string.profile_validation_threads_range)
        } else {
            null
        },
        retryCountError = if (advancedSettings.retryCount !in 0..99) {
            text(R.string.profile_validation_retry_count_range)
        } else {
            null
        },
        retryPauseError = if (advancedSettings.retryPauseSeconds !in 0..300) {
            text(R.string.profile_validation_retry_pause_range)
        } else {
            null
        },
    )
}

private data class ParsedBackupPool(
    val rawValue: String,
    val parsed: ParsedPoolEndpoint?,
    val effectiveTls: Boolean?,
)

private fun text(
    resId: Int,
    vararg args: Any,
): LocalizedText = LocalizedText(resId, args.toList())

private fun ParsedPoolEndpoint.identity(defaultTls: Boolean): String =
    "${normalizedUrl.lowercase(Locale.ROOT)}|${tlsOverride ?: defaultTls}"
