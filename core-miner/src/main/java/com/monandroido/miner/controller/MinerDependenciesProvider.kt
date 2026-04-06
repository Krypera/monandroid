package com.monandroido.miner.controller

import com.monandroido.data.repository.BenchmarkRepository
import com.monandroido.data.repository.ProfileRepository
import com.monandroido.data.repository.SettingsRepository

interface MinerDependenciesProvider {
    val profileRepository: ProfileRepository
    val settingsRepository: SettingsRepository
    val benchmarkRepository: BenchmarkRepository
}
