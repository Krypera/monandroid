package com.monandroido.data.di

import android.content.Context
import androidx.room.Room
import com.monandroido.data.db.AppDatabase
import com.monandroido.data.repository.BenchmarkRepository
import com.monandroido.data.repository.ProfileRepository
import com.monandroido.data.repository.SettingsRepository
import com.monandroido.data.security.SecureSecretStore

class DataContainer private constructor(
    val database: AppDatabase,
    val profileRepository: ProfileRepository,
    val settingsRepository: SettingsRepository,
    val benchmarkRepository: BenchmarkRepository,
) {
    companion object {
        fun create(context: Context): DataContainer {
            val appContext = context.applicationContext
            val database = Room.databaseBuilder(
                appContext,
                AppDatabase::class.java,
                "monandroido.db",
            ).build()
            val secureSecretStore = SecureSecretStore(appContext)
            return DataContainer(
                database = database,
                profileRepository = ProfileRepository(appContext, database, secureSecretStore),
                settingsRepository = SettingsRepository(appContext),
                benchmarkRepository = BenchmarkRepository(database),
            )
        }
    }
}
