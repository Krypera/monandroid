package com.monandroido.data.model

import com.monandroido.data.R
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

object ProfileTransferCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    fun encode(
        profile: MiningProfile,
        includeSecrets: Boolean = true,
    ): String =
        json.encodeToString(
            ExportedProfileFile.serializer(),
            ExportedProfileFile(
                formatVersion = CURRENT_FORMAT_VERSION,
                containsSecrets = includeSecrets,
                profile = ExportedProfile(
                    name = profile.name,
                    primaryPoolUrl = profile.primaryPoolUrl,
                    walletAddress = profile.walletAddress,
                    password = profile.password.takeIf { includeSecrets }.orEmpty(),
                    rigId = profile.rigId.takeIf { includeSecrets }.orEmpty(),
                    tls = profile.tls,
                    enabled = profile.enabled,
                    advancedSettings = profile.advancedSettings,
                ),
            ),
        )

    fun encodeProfiles(
        profiles: List<MiningProfile>,
        settings: AppSettings? = null,
        activeProfileIndex: Int? = null,
        includeSecrets: Boolean = true,
    ): String {
        if (profiles.isEmpty()) {
            throw ProfileTransferException(LocalizedText(R.string.profile_transfer_export_requires_profile))
        }
        if (activeProfileIndex != null && activeProfileIndex !in profiles.indices) {
            throw ProfileTransferException(LocalizedText(R.string.profile_transfer_active_index_invalid))
        }
        return json.encodeToString(
            ExportedProfileFile.serializer(),
            ExportedProfileFile(
                formatVersion = CURRENT_FORMAT_VERSION,
                containsSecrets = includeSecrets,
                settings = settings?.toExportedSettings(),
                activeProfileIndex = activeProfileIndex,
                profiles = profiles.map { profile ->
                    ExportedProfile(
                        name = profile.name,
                        primaryPoolUrl = profile.primaryPoolUrl,
                        walletAddress = profile.walletAddress,
                        password = profile.password.takeIf { includeSecrets }.orEmpty(),
                        rigId = profile.rigId.takeIf { includeSecrets }.orEmpty(),
                        tls = profile.tls,
                        enabled = profile.enabled,
                        advancedSettings = profile.advancedSettings,
                    )
                },
            ),
        )
    }

    fun decode(rawJson: String): ProfileDraft =
        decodeTransfer(rawJson).profiles.singleOrNull()
            ?: throw ProfileTransferException(LocalizedText(R.string.profile_transfer_multiple_profiles))

    fun decodeProfiles(rawJson: String): List<ProfileDraft> =
        decodeTransfer(rawJson).profiles

    fun decodeTransfer(rawJson: String): DecodedProfileTransfer {
        val exported = json.decodeFromString(ExportedProfileFile.serializer(), rawJson)
        if (exported.formatVersion !in SUPPORTED_FORMAT_VERSIONS) {
            throw ProfileTransferException(LocalizedText(R.string.profile_transfer_unsupported_version))
        }

        val exportedProfiles = when {
            exported.profile != null -> listOf(exported.profile)
            exported.profiles.isNotEmpty() -> exported.profiles
            else -> throw ProfileTransferException(LocalizedText(R.string.profile_transfer_empty))
        }

        return DecodedProfileTransfer(
            profiles = exportedProfiles.map { profile ->
                ProfileDraft(
                    name = profile.name,
                    primaryPoolUrl = profile.primaryPoolUrl,
                    walletAddress = profile.walletAddress,
                    password = profile.password,
                    rigId = profile.rigId,
                    tls = profile.tls,
                    enabled = profile.enabled,
                    advancedSettings = profile.advancedSettings,
                )
            },
            settings = exported.settings?.toAppSettings(),
            activeProfileIndex = exported.activeProfileIndex?.takeIf { it in exportedProfiles.indices },
            containsSecrets = exported.containsSecrets,
        )
    }

    private const val CURRENT_FORMAT_VERSION = 2
    private val SUPPORTED_FORMAT_VERSIONS = 1..CURRENT_FORMAT_VERSION
}

data class DecodedProfileTransfer(
    val profiles: List<ProfileDraft>,
    val settings: AppSettings? = null,
    val activeProfileIndex: Int? = null,
    val containsSecrets: Boolean = true,
)

class ProfileTransferException(
    localizedText: LocalizedText,
) : LocalizedException(localizedText)

@Serializable
private data class ExportedProfileFile(
    val formatVersion: Int = 2,
    val app: String = "Monandroid",
    val exportedAt: Long = System.currentTimeMillis(),
    val containsSecrets: Boolean = true,
    val settings: ExportedAppSettings? = null,
    val activeProfileIndex: Int? = null,
    val profile: ExportedProfile? = null,
    val profiles: List<ExportedProfile> = emptyList(),
)

@Serializable
private data class ExportedProfile(
    val name: String,
    val primaryPoolUrl: String,
    val walletAddress: String,
    val password: String,
    val rigId: String = "",
    val tls: Boolean,
    val enabled: Boolean,
    val advancedSettings: AdvancedMinerSettings,
)

@Serializable
private data class ExportedAppSettings(
    val developerFeePercent: Int = 10,
    val advancedModeEnabled: Boolean = false,
)

private fun AppSettings.toExportedSettings(): ExportedAppSettings =
    ExportedAppSettings(
        developerFeePercent = developerFeePercent,
        advancedModeEnabled = advancedModeEnabled,
    )

private fun ExportedAppSettings.toAppSettings(): AppSettings =
    AppSettings(
        developerFeePercent = developerFeePercent,
        advancedModeEnabled = advancedModeEnabled,
    )
