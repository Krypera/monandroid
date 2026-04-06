package com.monandroido.miner.controller

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.monandroido.data.model.AlgorithmMode
import com.monandroido.data.model.BenchmarkPreset
import com.monandroido.miner.model.MinerSessionState
import com.monandroido.miner.service.MiningForegroundService
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

interface MinerController {
    val sessionState: StateFlow<MinerSessionState>
    val logs: SharedFlow<String>
    val recentLogs: StateFlow<List<String>>
    val developerWalletConfigured: Boolean

    fun startMining(profileId: Long? = null)
    fun stopMining()
    fun pauseMining()
    fun resumeMining()
    fun startBenchmark(preset: BenchmarkPreset, algorithmMode: AlgorithmMode)
    fun clearRecentLogs()
}

class MinerControllerImpl(private val context: Context) : MinerController {
    override val sessionState: StateFlow<MinerSessionState> = MinerRuntimeStore.sessionState
    override val logs: SharedFlow<String> = MinerRuntimeStore.logs
    override val recentLogs: StateFlow<List<String>> = MinerRuntimeStore.recentLogs
    override val developerWalletConfigured: Boolean = MiningForegroundService.isDeveloperWalletConfigured()

    override fun startMining(profileId: Long?) {
        context.launchForegroundService(
            MiningForegroundService.intent(
                context = context,
                action = MiningForegroundService.ACTION_START_MINING,
                profileId = profileId,
            ),
        )
    }

    override fun stopMining() {
        context.launchService(MiningForegroundService.intent(context, MiningForegroundService.ACTION_STOP))
    }

    override fun pauseMining() {
        context.launchService(MiningForegroundService.intent(context, MiningForegroundService.ACTION_PAUSE))
    }

    override fun resumeMining() {
        context.launchService(MiningForegroundService.intent(context, MiningForegroundService.ACTION_RESUME))
    }

    override fun startBenchmark(preset: BenchmarkPreset, algorithmMode: AlgorithmMode) {
        context.launchForegroundService(
            MiningForegroundService.intent(
                context = context,
                action = MiningForegroundService.ACTION_START_BENCHMARK,
                benchmarkPreset = preset,
                benchmarkAlgorithm = algorithmMode,
            ),
        )
    }

    override fun clearRecentLogs() {
        MinerRuntimeStore.clearLogs()
    }

    private fun Context.launchForegroundService(intent: Intent) {
        ContextCompat.startForegroundService(this, intent)
    }

    private fun Context.launchService(intent: Intent) {
        startService(intent)
    }
}

object MinerRuntimeStore {
    private const val MAX_LOG_LINES = 80

    private val mutableSessionState = MutableStateFlow(MinerSessionState())
    private val mutableLogs = MutableSharedFlow<String>(
        extraBufferCapacity = 256,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    private val mutableRecentLogs = MutableStateFlow<List<String>>(emptyList())

    val sessionState: StateFlow<MinerSessionState> = mutableSessionState.asStateFlow()
    val logs: SharedFlow<String> = mutableLogs.asSharedFlow()
    val recentLogs: StateFlow<List<String>> = mutableRecentLogs.asStateFlow()

    fun updateState(transform: (MinerSessionState) -> MinerSessionState) {
        mutableSessionState.update(transform)
    }

    fun setState(state: MinerSessionState) {
        mutableSessionState.value = state
    }

    fun appendLog(line: String) {
        val normalizedLine = line.trimEnd()
        if (normalizedLine.isBlank()) {
            return
        }
        mutableLogs.tryEmit(normalizedLine)
        mutableRecentLogs.update { existingLines ->
            (existingLines + normalizedLine).takeLast(MAX_LOG_LINES)
        }
    }

    fun clearLogs() {
        mutableRecentLogs.value = emptyList()
    }
}
