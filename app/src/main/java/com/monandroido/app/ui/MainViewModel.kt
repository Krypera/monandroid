package com.monandroido.app.ui

import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.core.content.pm.PackageInfoCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.monandroido.app.R
import com.monandroido.app.MonandroidoApplication
import com.monandroido.data.R as DataR
import com.monandroido.data.model.AdvancedMinerSettings
import com.monandroido.data.model.AlgorithmMode
import com.monandroido.data.model.AppSettings
import com.monandroido.data.model.BackupPool
import com.monandroido.data.model.BenchmarkPreset
import com.monandroido.data.model.BenchmarkResult
import com.monandroido.data.model.MiningProfile
import com.monandroido.data.model.MiningProfileSummary
import com.monandroido.data.model.ProfileDraft
import com.monandroido.data.model.ProfileTransferCodec
import com.monandroido.data.model.formatPoolEndpoint
import com.monandroido.data.model.parsePoolEndpoint
import com.monandroido.data.model.validate
import com.monandroido.data.repository.SettingsRepository
import com.monandroido.miner.controller.MinerController
import com.monandroido.miner.model.MinerSessionState
import com.monandroido.miner.model.MinerStatus
import java.io.IOException
import java.time.Instant
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Immutable
data class HomeUiState(
    val activeProfileId: Long? = null,
    val activeProfile: MiningProfileSummary? = null,
    val minerState: MinerSessionState = MinerSessionState(),
    val recentLogs: List<String> = emptyList(),
)

@Immutable
data class ProfilesUiState(
    val profiles: List<MiningProfileSummary> = emptyList(),
    val activeProfileId: Long? = null,
    val minerStatus: MinerStatus = MinerStatus.STOPPED,
)

@Immutable
data class BenchmarkUiState(
    val benchmarkResults: List<BenchmarkResult> = emptyList(),
    val minerState: MinerSessionState = MinerSessionState(),
    val historySummary: BenchmarkHistorySummary = BenchmarkHistorySummary(),
)

@Immutable
data class BenchmarkHistorySummary(
    val totalRuns: Int = 0,
    val averageHashrate: Double? = null,
    val bestResult: BenchmarkResult? = null,
    val latestResult: BenchmarkResult? = null,
    val algorithmsCovered: Int = 0,
)

@Immutable
data class SettingsUiState(
    val requestedDeveloperFeePercent: Int = 10,
    val effectiveDeveloperFeePercent: Int = 10,
    val advancedModeEnabled: Boolean = false,
    val developerWalletConfigured: Boolean = false,
    val appVersionName: String = "",
    val appVersionCode: Long = 0L,
)

data class ProfileImportSummary(
    val importedCount: Int,
    val includesSecrets: Boolean,
    val restoredSettings: Boolean,
)

class MainViewModel(private val app: MonandroidoApplication) : ViewModel() {
    private val profileRepository = app.dataContainer.profileRepository
    private val settingsRepository = app.dataContainer.settingsRepository
    private val benchmarkRepository = app.dataContainer.benchmarkRepository
    val minerController: MinerController = app.minerController
    private val appDiagnosticsMetadata: AppDiagnosticsMetadata = loadAppDiagnosticsMetadata(app)

    private val settings: StateFlow<AppSettings> = settingsRepository.settings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AppSettings(),
    )
    private val profiles: StateFlow<List<MiningProfileSummary>> = profileRepository.profiles.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )
    private val activeProfileId: StateFlow<Long?> = profileRepository.activeProfileId.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = null,
    )
    private val benchmarkResults: StateFlow<List<BenchmarkResult>> = benchmarkRepository.results.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )
    private val minerState: StateFlow<MinerSessionState> = minerController.sessionState.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = MinerSessionState(),
    )
    private val recentLogs: StateFlow<List<String>> = minerController.recentLogs.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    val homeUiState: StateFlow<HomeUiState> = combine(
        profiles,
        activeProfileId,
        minerState,
        recentLogs,
    ) { profileList, selectedProfileId, currentMinerState, currentRecentLogs ->
        HomeUiState(
            activeProfileId = selectedProfileId,
            activeProfile = profileList.firstOrNull { it.id == selectedProfileId },
            minerState = currentMinerState,
            recentLogs = currentRecentLogs,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState(),
    )

    val profilesUiState: StateFlow<ProfilesUiState> = combine(
        profiles,
        activeProfileId,
        minerState,
    ) { profileList, selectedProfileId, currentMinerState ->
        ProfilesUiState(
            profiles = profileList,
            activeProfileId = selectedProfileId,
            minerStatus = currentMinerState.status,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ProfilesUiState(),
    )

    val benchmarkUiState: StateFlow<BenchmarkUiState> = combine(
        benchmarkResults,
        minerState,
    ) { savedResults, currentMinerState ->
        BenchmarkUiState(
            benchmarkResults = savedResults,
            minerState = currentMinerState,
            historySummary = buildBenchmarkHistorySummary(savedResults),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = BenchmarkUiState(),
    )

    val settingsUiState: StateFlow<SettingsUiState> = settings.map { currentSettings ->
        val developerWalletConfigured = minerController.developerWalletConfigured
        SettingsUiState(
            requestedDeveloperFeePercent = currentSettings.developerFeePercent,
            effectiveDeveloperFeePercent = calculateEffectiveDeveloperFeePercent(
                requestedPercent = currentSettings.developerFeePercent,
                developerWalletConfigured = developerWalletConfigured,
            ),
            advancedModeEnabled = currentSettings.advancedModeEnabled,
            developerWalletConfigured = developerWalletConfigured,
            appVersionName = appDiagnosticsMetadata.versionName,
            appVersionCode = appDiagnosticsMetadata.versionCode,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsUiState(
            developerWalletConfigured = minerController.developerWalletConfigured,
            appVersionName = appDiagnosticsMetadata.versionName,
            appVersionCode = appDiagnosticsMetadata.versionCode,
        ),
    )

    val advancedModeEnabled: Flow<Boolean> = settings.map { it.advancedModeEnabled }

    fun toggleMining() {
        when (homeUiState.value.minerState.status) {
            MinerStatus.RUNNING,
            MinerStatus.PAUSED,
            MinerStatus.STARTING,
            MinerStatus.BENCHMARKING -> minerController.stopMining()

            else -> minerController.startMining(homeUiState.value.activeProfileId)
        }
    }

    fun togglePauseResume() {
        when (homeUiState.value.minerState.status) {
            MinerStatus.PAUSED -> minerController.resumeMining()
            MinerStatus.RUNNING -> minerController.pauseMining()
            else -> Unit
        }
    }

    fun setActiveProfile(profileId: Long) {
        viewModelScope.launch {
            profileRepository.setActiveProfile(profileId)
        }
    }

    fun deleteProfile(profileId: Long) {
        if (profilesUiState.value.isDeletionBlocked(profileId)) {
            return
        }
        viewModelScope.launch {
            profileRepository.deleteProfile(profileId)
        }
    }

    suspend fun duplicateProfile(profileId: Long): Result<String> = runCatching {
        val profile = profileRepository.getProfile(profileId)
            ?: throw IllegalArgumentException(app.getString(DataR.string.profile_repository_profile_missing))
        val duplicatedDraft = profile.toDuplicateDraft(
            takenNames = profileRepository.profiles.first().map { it.name },
            copyLabel = app.getString(R.string.profile_generated_suffix_copy),
            defaultName = app.getString(R.string.profile_generated_name_default),
        )
        profileRepository.saveProfile(duplicatedDraft)
        duplicatedDraft.name
    }

    suspend fun saveProfile(draft: ProfileDraft): Result<Long> {
        val validation = draft.validate()
        if (!validation.isValid) {
            return Result.failure(
                IllegalArgumentException(
                    validation.firstErrorMessage(app)
                        ?: app.getString(DataR.string.profile_validation_invalid_generic),
                ),
            )
        }

        return runCatching {
            val savedId = profileRepository.saveProfile(draft)
            if (profilesUiState.value.activeProfileId == null || draft.id == profilesUiState.value.activeProfileId) {
                profileRepository.setActiveProfile(savedId)
            }
            savedId
        }
    }

    suspend fun loadDraft(profileId: Long?): ProfileDraft {
        if (profileId == null) return ProfileDraft()
        val profile = profileRepository.getProfile(profileId)
            ?: throw IllegalArgumentException(app.getString(DataR.string.profile_repository_profile_missing))
        return ProfileDraft(
            id = profile.id,
            name = profile.name,
            primaryPoolUrl = profile.primaryPoolUrl,
            walletAddress = profile.walletAddress,
            password = profile.password,
            rigId = profile.rigId.orEmpty(),
            tls = profile.tls,
            enabled = profile.enabled,
            advancedSettings = profile.advancedSettings,
        )
    }

    fun setDeveloperFee(percent: Int) {
        viewModelScope.launch {
            settingsRepository.setDeveloperFeePercent(percent)
        }
    }

    fun setAdvancedModeEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setAdvancedModeEnabled(enabled)
        }
    }

    fun startBenchmark(
        preset: BenchmarkPreset,
        algorithmMode: AlgorithmMode,
    ) {
        minerController.startBenchmark(preset, algorithmMode)
    }

    fun clearBenchmarkHistory() {
        viewModelScope.launch {
            benchmarkRepository.clearResults()
        }
    }

    fun clearRecentLogs() {
        minerController.clearRecentLogs()
    }

    suspend fun exportProfile(
        profileId: Long,
        destinationUri: Uri,
        includeSecrets: Boolean,
    ): Result<String> = runCatching {
        val profile = profileRepository.getProfile(profileId)
            ?: throw IllegalArgumentException(app.getString(DataR.string.profile_repository_profile_missing))
        val exportedJson = ProfileTransferCodec.encode(profile, includeSecrets = includeSecrets)
        withContext(Dispatchers.IO) {
            app.contentResolver.openOutputStream(destinationUri)?.bufferedWriter()?.use { writer ->
                writer.write(exportedJson)
            } ?: throw IOException(app.getString(R.string.error_open_export_destination))
        }
        profile.name
    }

    suspend fun exportAllProfiles(
        destinationUri: Uri,
        includeSecrets: Boolean,
    ): Result<Int> = runCatching {
        val profiles = profileRepository.getAllProfiles()
        if (profiles.isEmpty()) {
            throw IllegalArgumentException(app.getString(R.string.error_profile_library_requires_profile))
        }
        val currentSettings = settingsRepository.settings.first()
        val selectedProfileId = profileRepository.activeProfileId.first()
        val activeProfileIndex = selectedProfileId?.let { activeId ->
            profiles.indexOfFirst { profile -> profile.id == activeId }
                .takeIf { it >= 0 }
        }
        val exportedJson = ProfileTransferCodec.encodeProfiles(
            profiles = profiles,
            settings = currentSettings,
            activeProfileIndex = activeProfileIndex,
            includeSecrets = includeSecrets,
        )
        withContext(Dispatchers.IO) {
            app.contentResolver.openOutputStream(destinationUri)?.bufferedWriter()?.use { writer ->
                writer.write(exportedJson)
            } ?: throw IOException(app.getString(R.string.error_open_export_destination))
        }
        profiles.size
    }

    suspend fun importProfiles(sourceUri: Uri): Result<ProfileImportSummary> = runCatching {
        val importedJson = withContext(Dispatchers.IO) {
            app.contentResolver.openInputStream(sourceUri)?.bufferedReader()?.use { reader ->
                reader.readText()
            } ?: throw IOException(app.getString(R.string.error_open_selected_profile_file))
        }
        val transfer = ProfileTransferCodec.decodeTransfer(importedJson)
        val drafts = transfer.profiles
        if (drafts.isEmpty()) {
            throw IllegalArgumentException(app.getString(DataR.string.profile_transfer_empty))
        }
        drafts.forEach { draft ->
            val validation = draft.validate()
            if (!validation.isValid) {
                throw IllegalArgumentException(
                    validation.firstErrorMessage(app)
                        ?: app.getString(DataR.string.profile_validation_invalid_generic),
                )
            }
        }

        val existingActiveProfileId = profilesUiState.value.activeProfileId
        val hadExistingProfiles = profilesUiState.value.profiles.isNotEmpty()
        val previousSettings = settingsRepository.settings.first()
        val takenNames = profilesUiState.value.profiles.mapTo(mutableSetOf()) { it.name }
        var firstImportedId: Long? = null
        var preferredImportedId: Long? = null
        val importedIds = mutableListOf<Long>()
        try {
            drafts.forEachIndexed { index, draft ->
                val preparedDraft = draft.withImportedName(
                    takenNames = takenNames,
                    importedLabel = app.getString(R.string.profile_generated_suffix_imported),
                    defaultName = app.getString(R.string.profile_generated_name_default),
                )
                takenNames += preparedDraft.name
                val savedId = profileRepository.saveProfile(preparedDraft)
                importedIds += savedId
                if (firstImportedId == null) {
                    firstImportedId = savedId
                }
                if (transfer.activeProfileIndex == index) {
                    preferredImportedId = savedId
                }
            }
            if (!hadExistingProfiles) {
                transfer.settings?.let { importedSettings ->
                    settingsRepository.restoreSettings(importedSettings)
                }
            }
            if (existingActiveProfileId == null) {
                profileRepository.setActiveProfile(preferredImportedId ?: firstImportedId)
            }
            ProfileImportSummary(
                importedCount = drafts.size,
                includesSecrets = transfer.containsSecrets,
                restoredSettings = !hadExistingProfiles && transfer.settings != null,
            )
        } catch (throwable: Throwable) {
            importedIds.asReversed().forEach { importedId ->
                runCatching { profileRepository.deleteProfile(importedId) }
            }
            if (!hadExistingProfiles) {
                runCatching { settingsRepository.restoreSettings(previousSettings) }
            }
            runCatching { profileRepository.setActiveProfile(existingActiveProfileId) }
            throw throwable
        }
    }

    suspend fun exportBenchmarkHistory(destinationUri: Uri): Result<Int> = runCatching {
        val results = benchmarkRepository.results.first()
        if (results.isEmpty()) {
            throw IllegalArgumentException(app.getString(R.string.error_benchmark_history_requires_result))
        }
        val csv = buildBenchmarkHistoryCsv(results)
        withContext(Dispatchers.IO) {
            app.contentResolver.openOutputStream(destinationUri)?.bufferedWriter()?.use { writer ->
                writer.write(csv)
            } ?: throw IOException(app.getString(R.string.error_open_export_destination))
        }
        results.size
    }

    suspend fun exportDiagnostics(destinationUri: Uri): Result<DiagnosticsExportSummary> = runCatching {
        val diagnosticsJson = buildDiagnosticsJson(
            appMetadata = appDiagnosticsMetadata,
            homeUiState = homeUiState.value,
            profilesUiState = profilesUiState.value,
            benchmarkUiState = benchmarkUiState.value,
            settingsUiState = settingsUiState.value,
            exportedAtMillis = System.currentTimeMillis(),
        )
        withContext(Dispatchers.IO) {
            app.contentResolver.openOutputStream(destinationUri)?.bufferedWriter()?.use { writer ->
                writer.write(diagnosticsJson)
            } ?: throw IOException(app.getString(R.string.error_open_diagnostics_destination))
        }
        DiagnosticsExportSummary(
            profileCount = profilesUiState.value.profiles.size,
            benchmarkCount = benchmarkUiState.value.benchmarkResults.size,
            logLineCount = homeUiState.value.recentLogs.size,
        )
    }

    companion object {
        fun factory(app: MonandroidoApplication): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    @Suppress("UNCHECKED_CAST")
                    return MainViewModel(app) as T
                }
            }
    }
}

fun parseBackupPools(
    multiline: String,
    defaultTls: Boolean,
    defaultKeepAlive: Boolean,
): List<BackupPool> =
    multiline.lines()
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .map { rawValue ->
            val parsed = parsePoolEndpoint(rawValue)
            BackupPool(
                url = parsed?.normalizedUrl ?: rawValue,
                tls = parsed?.tlsOverride ?: defaultTls,
                keepAlive = defaultKeepAlive,
            )
        }

fun backupPoolsAsText(
    settings: AdvancedMinerSettings,
    defaultTls: Boolean,
): String = settings.backupPools.joinToString(separator = "\n") { pool ->
    val normalizedUrl = parsePoolEndpoint(pool.url)?.normalizedUrl ?: pool.url.trim()
    if (pool.tls == defaultTls) {
        normalizedUrl
    } else {
        formatPoolEndpoint(normalizedUrl, pool.tls)
    }
}

private fun ProfilesUiState.isDeletionBlocked(profileId: Long): Boolean =
    profileId == activeProfileId &&
        (
            minerStatus == MinerStatus.RUNNING ||
                minerStatus == MinerStatus.PAUSED ||
                minerStatus == MinerStatus.STARTING
        )

private fun calculateEffectiveDeveloperFeePercent(
    requestedPercent: Int,
    developerWalletConfigured: Boolean,
): Int = if (developerWalletConfigured) {
    requestedPercent.coerceIn(0, 100)
} else {
    0
}

internal fun buildBenchmarkHistorySummary(results: List<BenchmarkResult>): BenchmarkHistorySummary {
    if (results.isEmpty()) return BenchmarkHistorySummary()

    return BenchmarkHistorySummary(
        totalRuns = results.size,
        averageHashrate = results.map { it.avgHashrate }.average(),
        bestResult = results.maxByOrNull { it.avgHashrate },
        latestResult = results.maxWithOrNull(compareBy<BenchmarkResult> { it.createdAt }.thenBy { it.id }),
        algorithmsCovered = results.map { it.algorithmMode }.distinct().size,
    )
}

internal fun buildBenchmarkHistoryCsv(results: List<BenchmarkResult>): String {
    return buildString {
        appendLine("recorded_at,algorithm,preset,avg_hashrate_hps,duration_seconds,battery_delta_percent,peak_thermal")
        results.forEach { result ->
            appendLine(
                listOf(
                    csvValue(Instant.ofEpochMilli(result.createdAt).toString()),
                    csvValue(result.algorithmMode.xmrigValue),
                    csvValue(result.preset.sizeArg),
                    result.avgHashrate.toString(),
                    (result.durationMillis / 1000).toString(),
                    result.batteryDeltaPercent?.toString().orEmpty(),
                    result.peakThermal?.toString().orEmpty(),
                ).joinToString(","),
            )
        }
    }
}

private fun MiningProfile.toDuplicateDraft(
    takenNames: Collection<String>,
    copyLabel: String,
    defaultName: String,
): ProfileDraft =
    ProfileDraft(
        name = duplicateProfileName(
            name = name,
            takenNames = takenNames,
            copyLabel = copyLabel,
            defaultName = defaultName,
        ),
        primaryPoolUrl = primaryPoolUrl,
        walletAddress = walletAddress,
        password = password,
        rigId = rigId.orEmpty(),
        tls = tls,
        enabled = enabled,
        advancedSettings = advancedSettings,
    )

internal fun duplicateProfileName(
    name: String,
    takenNames: Collection<String> = emptyList(),
    copyLabel: String,
    defaultName: String,
): String = buildGeneratedProfileName(
    originalName = name,
    takenNames = takenNames,
    label = copyLabel,
    defaultName = defaultName,
    preferOriginalNameWhenAvailable = false,
)

private fun ProfileDraft.withImportedName(
    takenNames: Collection<String>,
    importedLabel: String,
    defaultName: String,
): ProfileDraft = copy(
    name = importedProfileName(
        name = name,
        takenNames = takenNames,
        importedLabel = importedLabel,
        defaultName = defaultName,
    ),
)

internal fun importedProfileName(
    name: String,
    takenNames: Collection<String> = emptyList(),
    importedLabel: String,
    defaultName: String,
): String = buildGeneratedProfileName(
    originalName = name,
    takenNames = takenNames,
    label = importedLabel,
    defaultName = defaultName,
    preferOriginalNameWhenAvailable = true,
)

private fun buildGeneratedProfileName(
    originalName: String,
    takenNames: Collection<String>,
    label: String,
    defaultName: String,
    preferOriginalNameWhenAvailable: Boolean,
): String {
    val normalized = originalName.trim().ifBlank { defaultName }
    if (preferOriginalNameWhenAvailable && !takenNames.containsName(normalized)) {
        return normalized
    }
    val baseName = stripGeneratedSuffix(normalized, label)

    val firstCandidate = "$baseName ($label)"
    if (!takenNames.containsName(firstCandidate)) {
        return firstCandidate
    }

    var suffix = 2
    while (true) {
        val candidate = "$baseName ($label $suffix)"
        if (!takenNames.containsName(candidate)) {
            return candidate
        }
        suffix += 1
    }
}

private fun stripGeneratedSuffix(name: String, label: String): String {
    val suffixRegex = Regex("""^(.*?)(?: \($label(?: \d+)?\))?$""")
    return suffixRegex.matchEntire(name)
        ?.groupValues
        ?.getOrNull(1)
        ?.trim()
        ?.ifBlank { name }
        ?: name
}

private fun Collection<String>.containsName(candidate: String): Boolean =
    any { existingName -> existingName.equals(candidate, ignoreCase = true) }

private suspend fun SettingsRepository.restoreSettings(settings: AppSettings) {
    setDeveloperFeePercent(settings.developerFeePercent)
    setAdvancedModeEnabled(settings.advancedModeEnabled)
}

internal fun buildProfileExportFileName(profileName: String): String {
    val sanitized = profileName
        .trim()
        .ifBlank { "profile" }
        .replace(Regex("[^A-Za-z0-9._-]+"), "-")
        .trim('-')
        .ifBlank { "profile" }
        .lowercase(Locale.ROOT)
    return "monandroid-$sanitized.json"
}

internal fun buildProfileExportFileName(
    profileName: String,
    includeSecrets: Boolean,
): String {
    val baseName = buildProfileExportFileName(profileName).removeSuffix(".json")
    return if (includeSecrets) {
        "$baseName.json"
    } else {
        "$baseName-safe.json"
    }
}

internal fun buildProfileLibraryExportFileName(includeSecrets: Boolean): String =
    if (includeSecrets) {
        "monandroid-app-backup.json"
    } else {
        "monandroid-app-backup-safe.json"
    }

internal fun buildBenchmarkExportFileName(): String = "monandroid-benchmark-history.csv"

private fun csvValue(rawValue: String): String {
    val escaped = rawValue.replace("\"", "\"\"")
    return "\"$escaped\""
}

private fun loadAppDiagnosticsMetadata(app: MonandroidoApplication): AppDiagnosticsMetadata {
    val packageInfo = app.packageManager.getPackageInfo(app.packageName, 0)
    return AppDiagnosticsMetadata(
        applicationId = app.packageName,
        versionName = packageInfo.versionName ?: app.getString(R.string.error_unknown),
        versionCode = PackageInfoCompat.getLongVersionCode(packageInfo),
    )
}
