package com.monandroido.miner.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.BatteryManager
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import com.monandroido.data.R as DataR
import com.monandroido.data.model.AlgorithmMode
import com.monandroido.data.model.BenchmarkPreset
import com.monandroido.data.model.MiningProfile
import com.monandroido.data.model.validate
import com.monandroido.data.repository.BenchmarkRepository
import com.monandroido.data.repository.ProfileRepository
import com.monandroido.data.repository.SettingsRepository
import com.monandroido.miner.BuildConfig
import com.monandroido.miner.R
import com.monandroido.miner.api.XmrigApiClient
import com.monandroido.miner.config.XmrigConfigFactory
import com.monandroido.miner.controller.MinerDependenciesProvider
import com.monandroido.miner.controller.MinerRuntimeStore
import com.monandroido.miner.model.BenchmarkRequest
import com.monandroido.miner.model.FeeMode
import com.monandroido.miner.model.LastSessionSummary
import com.monandroido.miner.model.MinerSessionState
import com.monandroido.miner.model.MinerStatus
import com.monandroido.miner.notification.MinerNotificationFactory
import com.monandroido.miner.policy.normalizedDeveloperWallet
import com.monandroido.miner.scheduler.DeveloperFeeScheduler
import com.monandroido.nativebridge.XmrigLaunchRequest
import com.monandroido.nativebridge.XmrigRuntime
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class MiningForegroundService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val sessionMutex = Mutex()

    private lateinit var notificationFactory: MinerNotificationFactory
    private lateinit var runtime: XmrigRuntime
    private lateinit var profileRepository: ProfileRepository
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var benchmarkRepository: BenchmarkRepository
    private val configFactory = XmrigConfigFactory()

    private var apiClient: XmrigApiClient? = null
    private var summaryJob: Job? = null
    private var schedulerJob: Job? = null
    private var constraintJob: Job? = null
    private var settingsJob: Job? = null
    private var currentProfile: MiningProfile? = null
    private var currentBenchmark: BenchmarkRequest? = null
    private var currentFeeMode: FeeMode = FeeMode.NONE
    private var scheduler: DeveloperFeeScheduler? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var currentApiToken: String = UUID.randomUUID().toString()
    private var benchmarkHashrate: Double? = null
    private var benchmarkStartedAt: Long = 0L
    private var startBatteryPercent: Int? = null
    private var peakThermal: Int? = null
    private var lastWakeLockAcquireElapsed: Long = 0L
    private var pausedAutomatically = false
    private var lastRuntimeOutputLine: String? = null

    override fun onCreate() {
        super.onCreate()
        val provider = application as? MinerDependenciesProvider
            ?: error("Application must implement MinerDependenciesProvider")
        profileRepository = provider.profileRepository
        settingsRepository = provider.settingsRepository
        benchmarkRepository = provider.benchmarkRepository
        runtime = XmrigRuntime(this)
        notificationFactory = MinerNotificationFactory(this)
        notificationFactory.createChannel()
        startForeground(
            MinerNotificationFactory.NOTIFICATION_ID,
            notificationFactory.build(MinerRuntimeStore.sessionState.value),
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_MINING -> launchSerialized {
                startMining(intent.getLongExtra(EXTRA_PROFILE_ID, -1L).takeIf { it > 0 })
            }
            ACTION_STOP -> launchSerialized { stopMining(getString(R.string.miner_message_stopped_by_user)) }
            ACTION_PAUSE -> launchSerialized {
                pauseMining(getString(R.string.miner_message_paused_by_user), userInitiated = true)
            }
            ACTION_RESUME -> launchSerialized { resumeMining(userInitiated = true) }
            ACTION_START_BENCHMARK -> launchSerialized {
                val preset = runCatching {
                    BenchmarkPreset.valueOf(
                        intent.getStringExtra(EXTRA_BENCHMARK_PRESET) ?: BenchmarkPreset.ONE_MEGA.name,
                    )
                }.getOrDefault(BenchmarkPreset.ONE_MEGA)
                val algo = runCatching {
                    AlgorithmMode.valueOf(
                        intent.getStringExtra(EXTRA_BENCHMARK_ALGO) ?: AlgorithmMode.RX0.name,
                    )
                }.getOrDefault(AlgorithmMode.RX0)
                startBenchmark(BenchmarkRequest(preset, algo))
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        val summary = currentLastSessionSummary()
        val currentState = MinerRuntimeStore.sessionState.value
        stopRuntimeOnly()
        removeForegroundNotification()
        runtime.close()
        if (currentState.status != MinerStatus.STOPPED) {
            MinerRuntimeStore.setState(
                currentState.copy(
                    status = MinerStatus.STOPPED,
                    lastError = currentState.lastError ?: getString(R.string.miner_message_service_destroyed),
                    lastSessionSummary = currentState.lastSessionSummary ?: summary,
                ),
            )
        }
        serviceScope.cancel()
        super.onDestroy()
    }

    private suspend fun startMining(profileId: Long?) {
        stopRuntimeOnly()
        MinerRuntimeStore.clearLogs()
        profileRepository.ensureDefaultActiveProfile()
        val resolvedId = profileId ?: profileRepository.activeProfileId.first()
        val profile = if (resolvedId != null) profileRepository.getProfile(resolvedId) else null
        if (profile == null) {
            setError(getString(R.string.miner_error_create_and_activate_profile))
            return
        }
        val validation = profile.validate()
        if (!validation.isValid) {
            setError(
                validation.firstErrorMessage(this)
                    ?: getString(DataR.string.profile_validation_invalid_generic),
            )
            return
        }
        if (!profile.enabled) {
            setError(getString(R.string.miner_error_active_profile_disabled))
            return
        }

        currentBenchmark = null
        currentProfile = profile
        currentApiToken = UUID.randomUUID().toString()
        apiClient = XmrigApiClient(API_PORT, currentApiToken)
        lastRuntimeOutputLine = null
        val settings = settingsRepository.settings.first()
        val effectiveFeePercent = effectiveDeveloperFeePercent(
            requestedPercent = settings.developerFeePercent,
            developerWalletConfigured = isDeveloperWalletConfigured(),
        )
        scheduler = DeveloperFeeScheduler(effectiveFeePercent)
        currentFeeMode = when {
            effectiveFeePercent == 0 -> FeeMode.USER
            effectiveFeePercent == 100 -> FeeMode.DEVELOPER
            else -> FeeMode.USER
        }
        startBatteryPercent = readBatteryPercent()
        peakThermal = readThermalStatus()
        benchmarkHashrate = null
        pausedAutomatically = false

        MinerRuntimeStore.setState(
            MinerSessionState(
                status = MinerStatus.STARTING,
                activeProfileId = profile.id,
                activeProfileName = profile.name,
                currentFeeMode = currentFeeMode,
                developerFeePercent = effectiveFeePercent,
            ),
        )
        notificationFactory.notify(MinerRuntimeStore.sessionState.value)

        val started = startRuntimeForCurrentMode()
        if (!started) {
            return
        }
        observeSettingsChanges()
        startSummaryPolling()
        startConstraintLoop()
        startSchedulerLoop()
    }

    private suspend fun startBenchmark(request: BenchmarkRequest) {
        stopRuntimeOnly()
        MinerRuntimeStore.clearLogs()
        currentBenchmark = request
        currentProfile = null
        scheduler = null
        currentFeeMode = FeeMode.NONE
        benchmarkHashrate = null
        benchmarkStartedAt = SystemClock.elapsedRealtime()
        startBatteryPercent = readBatteryPercent()
        peakThermal = readThermalStatus()
        currentApiToken = UUID.randomUUID().toString()
        lastRuntimeOutputLine = null

        MinerRuntimeStore.setState(
            MinerSessionState(
                status = MinerStatus.BENCHMARKING,
                currentFeeMode = FeeMode.NONE,
                benchmarkAlgorithm = request.algorithmMode,
                benchmarkPreset = request.preset,
                developerFeePercent = 0,
            ),
        )
        notificationFactory.notify(MinerRuntimeStore.sessionState.value)

        acquireWakeLock()
        val started = runtime.start(
            XmrigLaunchRequest(
                configPath = null,
                apiPort = API_PORT,
                apiToken = currentApiToken,
                extraArgs = configFactory.buildBenchmarkArgs(request),
            ),
            onOutput = ::handleOutput,
            onExit = ::handleRuntimeExit,
        )
        if (!started) {
            return
        }
    }

    private suspend fun stopMining(reason: String? = null) {
        val summary = currentLastSessionSummary()
        stopRuntimeOnly()
        removeForegroundNotification()
        currentProfile = null
        currentBenchmark = null
        scheduler = null
        currentFeeMode = FeeMode.NONE

        MinerRuntimeStore.setState(
            MinerSessionState(
                status = MinerStatus.STOPPED,
                lastError = reason,
                lastSessionSummary = summary,
            ),
        )
        stopSelf()
    }

    private suspend fun pauseMining(reason: String, userInitiated: Boolean) {
        val client = apiClient
        if (client != null) {
            val paused = runCatching { client.pause() }.getOrDefault(false)
            if (!paused) {
                setError(getString(R.string.miner_error_pause_failed))
                return
            }
        }
        pausedAutomatically = !userInitiated
        wakeLock?.releaseIfHeld()
        wakeLock = null
        MinerRuntimeStore.updateState {
            it.copy(
                status = MinerStatus.PAUSED,
                pauseReason = reason,
            )
        }
        notificationFactory.notify(MinerRuntimeStore.sessionState.value)
    }

    private suspend fun resumeMining(userInitiated: Boolean) {
        val client = apiClient
        if (client != null) {
            val resumed = runCatching { client.resume() }.getOrDefault(false)
            if (!resumed) {
                setError(getString(R.string.miner_error_resume_failed))
                return
            }
        }
        acquireWakeLock()
        if (userInitiated) {
            pausedAutomatically = false
        }
        MinerRuntimeStore.updateState {
            it.copy(
                status = if (currentBenchmark != null) MinerStatus.BENCHMARKING else MinerStatus.RUNNING,
                pauseReason = null,
            )
        }
        notificationFactory.notify(MinerRuntimeStore.sessionState.value)
    }

    private suspend fun startRuntimeForCurrentMode(): Boolean {
        val profile = currentProfile ?: return false
        val developerWallet = configuredDeveloperWallet()
        val walletOverride = if (currentFeeMode == FeeMode.DEVELOPER && developerWallet != null) {
            developerWallet
        } else {
            null
        }
        val configFile = File(filesDir, "xmrig-active.json")
        configFile.writeText(
            configFactory.buildMiningConfig(
                profile = profile,
                apiPort = API_PORT,
                apiToken = currentApiToken,
                walletOverride = walletOverride,
            ),
        )
        acquireWakeLock()
        val started = runtime.start(
            XmrigLaunchRequest(
                configPath = configFile.absolutePath,
                apiPort = API_PORT,
                apiToken = currentApiToken,
                extraArgs = listOf("--no-color"),
            ),
            onOutput = ::handleOutput,
            onExit = ::handleRuntimeExit,
        )
        if (!started) {
            return false
        }
        MinerRuntimeStore.updateState {
            it.copy(
                status = MinerStatus.RUNNING,
                activeProfileId = profile.id,
                activeProfileName = profile.name,
                currentFeeMode = currentFeeMode,
                pauseReason = null,
                lastError = null,
            )
        }
        notificationFactory.notify(MinerRuntimeStore.sessionState.value)
        return true
    }

    private fun startSummaryPolling() {
        summaryJob?.cancel()
        summaryJob = serviceScope.launch {
            var nextDelayMillis = 1_000L
            while (true) {
                delay(nextDelayMillis)
                nextDelayMillis = 5_000L
                refreshWakeLockTimeout()
                val summary = runCatching { apiClient?.fetchSummary() }.getOrNull() ?: continue
                recordCurrentThermalPeak()
                MinerRuntimeStore.updateState {
                    it.copy(
                        hashrateHps = summary.hashrateHps ?: it.hashrateHps,
                        acceptedShares = summary.acceptedShares ?: it.acceptedShares,
                        rejectedShares = summary.rejectedShares ?: it.rejectedShares,
                        uptimeMillis = summary.uptimeMillis ?: it.uptimeMillis,
                        poolAddress = summary.poolAddress ?: it.poolAddress,
                        lastError = summary.lastError ?: it.lastError,
                    )
                }
                notificationFactory.notify(MinerRuntimeStore.sessionState.value)
            }
        }
    }

    private fun startSchedulerLoop() {
        schedulerJob?.cancel()
        schedulerJob = serviceScope.launch {
            var lastTick = SystemClock.elapsedRealtime()
            while (true) {
                delay(5_000)
                val now = SystemClock.elapsedRealtime()
                val delta = now - lastTick
                lastTick = now
                val state = MinerRuntimeStore.sessionState.value
                if (currentBenchmark != null || state.status != MinerStatus.RUNNING) continue
                val scheduler = scheduler ?: continue
                val snapshot = scheduler.onRunningTick(delta)
                MinerRuntimeStore.updateState {
                    it.copy(
                        sessionUserMillis = snapshot.userDurationMillis,
                        sessionDeveloperMillis = snapshot.developerDurationMillis,
                    )
                }
                if (snapshot.currentMode != currentFeeMode) {
                    sessionMutex.withLock {
                        switchFeeMode(snapshot.currentMode)
                    }
                }
            }
        }
    }

    private fun observeSettingsChanges() {
        settingsJob?.cancel()
        settingsJob = serviceScope.launch {
            settingsRepository.settings.collectLatest { settings ->
                val effectiveFeePercent = effectiveDeveloperFeePercent(
                    requestedPercent = settings.developerFeePercent,
                    developerWalletConfigured = isDeveloperWalletConfigured(),
                )
                val scheduler = scheduler
                scheduler?.updateFeePercent(effectiveFeePercent)
                MinerRuntimeStore.updateState {
                    it.copy(developerFeePercent = effectiveFeePercent)
                }
                val targetMode = scheduler?.currentMode()
                if (
                    currentBenchmark == null &&
                    MinerRuntimeStore.sessionState.value.status == MinerStatus.RUNNING &&
                    targetMode != null &&
                    targetMode != currentFeeMode
                ) {
                    sessionMutex.withLock {
                        switchFeeMode(targetMode)
                    }
                }
            }
        }
    }

    private fun startConstraintLoop() {
        constraintJob?.cancel()
        constraintJob = serviceScope.launch {
            while (true) {
                val profile = currentProfile
                if (profile != null && currentBenchmark == null) {
                    val state = MinerRuntimeStore.sessionState.value
                    val reason = currentConstraintReason(profile)
                    when {
                        reason != null && state.status == MinerStatus.RUNNING -> sessionMutex.withLock {
                            pauseMining(reason, userInitiated = false)
                        }
                        reason == null && state.status == MinerStatus.PAUSED && pausedAutomatically -> sessionMutex.withLock {
                            resumeMining(userInitiated = false)
                        }
                    }
                }
                delay(5_000)
            }
        }
    }

    private suspend fun switchFeeMode(targetMode: FeeMode) {
        currentFeeMode = targetMode
        val profile = currentProfile ?: return
        val developerWallet = configuredDeveloperWallet()
        val walletOverride = if (targetMode == FeeMode.DEVELOPER && developerWallet != null) {
            developerWallet
        } else {
            null
        }
        val configJson = configFactory.buildMiningConfig(
            profile = profile,
            apiPort = API_PORT,
            apiToken = currentApiToken,
            walletOverride = walletOverride,
        )
        val applied = apiClient?.applyConfig(configJson) == true
        if (!applied) {
            runtime.stop()
            delay(750)
            startRuntimeForCurrentMode()
        } else {
            File(filesDir, "xmrig-active.json").writeText(configJson)
            MinerRuntimeStore.updateState { it.copy(currentFeeMode = currentFeeMode) }
            notificationFactory.notify(MinerRuntimeStore.sessionState.value)
        }
    }

    private fun handleOutput(line: String) {
        val normalizedLine = line.trim()
        if (normalizedLine.isNotEmpty()) {
            lastRuntimeOutputLine = normalizedLine
        }
        MinerRuntimeStore.appendLog(line)
        if (currentBenchmark != null) {
            parseBenchmarkOutput(line)?.let { hashrate ->
                benchmarkHashrate = hashrate
                MinerRuntimeStore.updateState { it.copy(benchmarkHashrate = hashrate) }
            }
        }
    }

    private fun handleRuntimeExit(exitCode: Int) {
        launchSerialized {
            if (currentBenchmark != null) {
                finalizeBenchmark(exitCode)
            } else if (MinerRuntimeStore.sessionState.value.status != MinerStatus.STOPPED) {
                setError(buildRuntimeExitMessage(exitCode))
            }
        }
    }

    private fun launchSerialized(block: suspend () -> Unit) {
        serviceScope.launch {
            sessionMutex.withLock {
                block()
            }
        }
    }

    private suspend fun finalizeBenchmark(exitCode: Int) {
        val request = currentBenchmark ?: return
        if (exitCode == 0 && benchmarkHashrate != null) {
            benchmarkRepository.addResult(
                com.monandroido.data.model.BenchmarkResult(
                    algorithmMode = request.algorithmMode,
                    preset = request.preset,
                    avgHashrate = benchmarkHashrate ?: 0.0,
                    durationMillis = SystemClock.elapsedRealtime() - benchmarkStartedAt,
                    peakThermal = peakThermal,
                    batteryDeltaPercent = startBatteryPercent?.let { start -> readBatteryPercent()?.let { start - it } },
                    createdAt = System.currentTimeMillis(),
                ),
            )
            MinerRuntimeStore.setState(
                MinerSessionState(
                    status = MinerStatus.STOPPED,
                    benchmarkAlgorithm = request.algorithmMode,
                    benchmarkPreset = request.preset,
                    benchmarkHashrate = benchmarkHashrate,
                ),
            )
            currentBenchmark = null
            stopSelf()
        } else {
            setError(getString(R.string.miner_error_benchmark_failed_with_code, exitCode))
        }
        wakeLock?.releaseIfHeld()
        wakeLock = null
        if (exitCode != 0 || benchmarkHashrate == null) {
            notificationFactory.notify(MinerRuntimeStore.sessionState.value)
            currentBenchmark = null
        }
    }

    private suspend fun setError(message: String) {
        stopRuntimeOnly()
        wakeLock?.releaseIfHeld()
        wakeLock = null
        MinerRuntimeStore.setState(
            MinerSessionState(
                status = MinerStatus.ERROR,
                lastError = message,
            ),
        )
        notificationFactory.notify(MinerRuntimeStore.sessionState.value)
    }

    private fun stopRuntimeOnly() {
        summaryJob?.cancel()
        schedulerJob?.cancel()
        constraintJob?.cancel()
        settingsJob?.cancel()
        summaryJob = null
        schedulerJob = null
        constraintJob = null
        settingsJob = null
        runtime.stop()
        apiClient = null
        lastRuntimeOutputLine = null
        wakeLock?.releaseIfHeld()
        wakeLock = null
        lastWakeLockAcquireElapsed = 0L
    }

    private fun buildRuntimeExitMessage(exitCode: Int): String {
        val lastOutput = lastRuntimeOutputLine?.takeIf { it.isNotBlank() }
        return when {
            exitCode == -1 && lastOutput != null -> lastOutput
            lastOutput != null -> getString(
                R.string.miner_error_runtime_exited_with_last_output,
                exitCode,
                lastOutput,
            )
            else -> getString(R.string.miner_error_runtime_exited_with_code, exitCode)
        }
    }

    private fun acquireWakeLock() {
        val powerManager = getSystemService(PowerManager::class.java)
        wakeLock?.releaseIfHeld()
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$packageName:miner").apply {
            setReferenceCounted(false)
            acquire(WAKE_LOCK_TIMEOUT_MS)
        }
        lastWakeLockAcquireElapsed = SystemClock.elapsedRealtime()
    }

    private fun refreshWakeLockTimeout() {
        val status = MinerRuntimeStore.sessionState.value.status
        if (status != MinerStatus.RUNNING && status != MinerStatus.BENCHMARKING && status != MinerStatus.STARTING) {
            return
        }
        if (wakeLock?.isHeld != true) {
            acquireWakeLock()
            return
        }
        val elapsed = SystemClock.elapsedRealtime() - lastWakeLockAcquireElapsed
        if (elapsed >= WAKE_LOCK_REFRESH_MS) {
            acquireWakeLock()
        }
    }

    private fun currentConstraintReason(profile: MiningProfile): String? {
        val powerManager = getSystemService(PowerManager::class.java)
        val isInteractive = powerManager.isInteractive
        val isPowerSaveMode = powerManager.isPowerSaveMode
        val thermalStatus = readThermalStatus()
        val batteryPercent = readBatteryPercent()
        val charging = isCharging()

        return when {
            thermalStatus != null && thermalStatus >= PowerManager.THERMAL_STATUS_SEVERE -> {
                getString(R.string.miner_constraint_thermal_protection)
            }
            isPowerSaveMode -> getString(R.string.miner_constraint_battery_saver)
            batteryPercent != null && batteryPercent <= 20 -> getString(R.string.miner_constraint_low_battery)
            !profile.advancedSettings.continueWhenScreenOff && !isInteractive -> {
                getString(R.string.miner_constraint_screen_off)
            }
            profile.advancedSettings.requireCharging && !charging -> {
                getString(R.string.miner_constraint_waiting_for_charging)
            }
            else -> null
        }
    }

    private fun readBatteryPercent(): Int? {
        val batteryManager = getSystemService(BatteryManager::class.java)
        val value = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        return value.takeIf { it in 1..100 }
    }

    private fun readThermalStatus(): Int? {
        val powerManager = getSystemService(PowerManager::class.java)
        return powerManager.currentThermalStatus
    }

    private fun isCharging(): Boolean {
        val intent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return false
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        return status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
    }

    private fun parseBenchmarkOutput(line: String): Double? {
        return BENCHMARK_RESULT_REGEX.find(line)?.groupValues?.getOrNull(1)?.toDoubleOrNull()
    }

    private fun currentLastSessionSummary(): LastSessionSummary? {
        val snapshot = scheduler?.onRunningTick(0) ?: return null
        return LastSessionSummary(
            userDurationMillis = snapshot.userDurationMillis,
            developerDurationMillis = snapshot.developerDurationMillis,
        )
    }

    private fun recordCurrentThermalPeak() {
        val currentThermal = readThermalStatus() ?: return
        peakThermal = maxOf(peakThermal ?: currentThermal, currentThermal)
    }

    private fun removeForegroundNotification() {
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    companion object {
        const val ACTION_START_MINING = "com.monandroido.miner.action.START_MINING"
        const val ACTION_STOP = "com.monandroido.miner.action.STOP"
        const val ACTION_PAUSE = "com.monandroido.miner.action.PAUSE"
        const val ACTION_RESUME = "com.monandroido.miner.action.RESUME"
        const val ACTION_START_BENCHMARK = "com.monandroido.miner.action.START_BENCHMARK"

        private const val EXTRA_PROFILE_ID = "extra_profile_id"
        private const val EXTRA_BENCHMARK_PRESET = "extra_benchmark_preset"
        private const val EXTRA_BENCHMARK_ALGO = "extra_benchmark_algo"
        private const val API_PORT = 50080
        private const val WAKE_LOCK_TIMEOUT_MS = 10 * 60 * 1000L
        private const val WAKE_LOCK_REFRESH_MS = 8 * 60 * 1000L
        private val BENCHMARK_RESULT_REGEX = Regex("""benchmark finished in [\d.]+ seconds \(([\d.]+) h/s\)""")

        fun intent(
            context: Context,
            action: String,
            profileId: Long? = null,
            benchmarkPreset: BenchmarkPreset? = null,
            benchmarkAlgorithm: AlgorithmMode? = null,
        ): Intent = Intent(context, MiningForegroundService::class.java).apply {
            this.action = action
            profileId?.let { putExtra(EXTRA_PROFILE_ID, it) }
            benchmarkPreset?.let { putExtra(EXTRA_BENCHMARK_PRESET, it.name) }
            benchmarkAlgorithm?.let { putExtra(EXTRA_BENCHMARK_ALGO, it.name) }
        }

        fun isDeveloperWalletConfigured(): Boolean =
            normalizedDeveloperWallet(BuildConfig.DEVELOPER_WALLET) != null
    }
}

private fun configuredDeveloperWallet(): String? =
    normalizedDeveloperWallet(BuildConfig.DEVELOPER_WALLET)

private fun PowerManager.WakeLock.releaseIfHeld() {
    if (isHeld) {
        release()
    }
}

private fun effectiveDeveloperFeePercent(
    requestedPercent: Int,
    developerWalletConfigured: Boolean,
): Int = if (developerWalletConfigured) {
    requestedPercent.coerceIn(0, 100)
} else {
    0
}
