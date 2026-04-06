package com.monandroido.data.repository

import android.content.Context
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import com.monandroido.data.R
import com.monandroido.data.db.AppDatabase
import com.monandroido.data.db.MiningProfileEntity
import com.monandroido.data.model.AdvancedMinerSettings
import com.monandroido.data.model.MiningProfile
import com.monandroido.data.model.MiningProfileSummary
import com.monandroido.data.model.ProfileDraft
import com.monandroido.data.security.SecureSecretStore
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.profileDataStore by preferencesDataStore(name = "profile_session")
private val profileJson = Json { ignoreUnknownKeys = true }

class ProfileRepository(
    private val context: Context,
    private val database: AppDatabase,
    private val secureSecretStore: SecureSecretStore,
) {
    val profiles: Flow<List<MiningProfileSummary>> =
        database.miningProfileDao().observeProfiles().map { entities ->
            entities.map { it.toSummary() }
        }

    val activeProfileId: Flow<Long?> = context.profileDataStore.data
        .catch { throwable ->
            if (throwable is IOException) {
                emit(emptyPreferences())
            } else {
                throw throwable
            }
        }
        .map { it[ACTIVE_PROFILE_KEY] }

    suspend fun setActiveProfile(profileId: Long?) {
        context.profileDataStore.edit { prefs ->
            if (profileId == null) {
                prefs.remove(ACTIVE_PROFILE_KEY)
            } else {
                prefs[ACTIVE_PROFILE_KEY] = profileId
            }
        }
    }

    suspend fun ensureDefaultActiveProfile() {
        if (activeProfileId.first() != null) return
        val first = profiles.first().firstOrNull() ?: return
        setActiveProfile(first.id)
    }

    suspend fun getProfile(profileId: Long): MiningProfile? {
        val entity = database.miningProfileDao().getById(profileId) ?: return null
        val secret = withContext(Dispatchers.IO) {
            secureSecretStore.getSecret(entity.passwordAlias)
        }
        return entity.toProfile(secret = secret)
    }

    suspend fun getAllProfiles(): List<MiningProfile> =
        database.miningProfileDao().getAll().map { entity ->
            val secret = withContext(Dispatchers.IO) {
                secureSecretStore.getSecret(entity.passwordAlias)
            }
            entity.toProfile(secret = secret)
        }

    suspend fun saveProfile(draft: ProfileDraft): Long {
        val now = System.currentTimeMillis()
        val existingEntity = draft.id?.let { database.miningProfileDao().getById(it) }
        if (draft.id != null && existingEntity == null) {
            throw IllegalArgumentException(context.getString(R.string.profile_repository_profile_missing))
        }

        val passwordAlias = existingEntity?.passwordAlias ?: "profile_password_${UUID.randomUUID()}"
        val sanitizedBackupPools = draft.advancedSettings.backupPools
            .map { it.copy(url = it.url.trim()) }
            .filter { it.url.isNotBlank() }
        val entity = MiningProfileEntity(
            id = draft.id ?: 0,
            name = draft.name.trim(),
            primaryPoolUrl = draft.primaryPoolUrl.trim(),
            walletAddress = draft.walletAddress.trim(),
            rigId = draft.rigId.trim().ifBlank { null },
            tls = draft.tls,
            enabled = draft.enabled,
            algorithmMode = draft.advancedSettings.algorithmMode,
            maxThreadsHint = draft.advancedSettings.maxThreadsHint,
            retryCount = draft.advancedSettings.retryCount,
            retryPauseSeconds = draft.advancedSettings.retryPauseSeconds,
            continueWhenScreenOff = draft.advancedSettings.continueWhenScreenOff,
            requireCharging = draft.advancedSettings.requireCharging,
            keepAlive = draft.advancedSettings.keepAlive,
            backupPoolsJson = profileJson.encodeToString(
                ListSerializerProvider.backupPoolSerializer,
                sanitizedBackupPools,
            ),
            passwordAlias = passwordAlias,
            createdAt = existingEntity?.createdAt ?: now,
            updatedAt = now,
        )
        val savedId = if (draft.id == null) {
            database.miningProfileDao().insert(entity)
        } else {
            database.miningProfileDao().update(entity)
            draft.id
        }

        val secretWriteResult = runCatching {
            withContext(Dispatchers.IO) {
                secureSecretStore.putSecret(passwordAlias, draft.password)
            }
        }
        if (secretWriteResult.isFailure) {
            if (draft.id == null) {
                database.miningProfileDao().getById(savedId)?.let { insertedEntity ->
                    database.miningProfileDao().delete(insertedEntity)
                }
            } else if (existingEntity != null) {
                database.miningProfileDao().update(existingEntity)
            }
            throw secretWriteResult.exceptionOrNull()
                ?: IllegalStateException(context.getString(R.string.profile_repository_secret_save_failed))
        }
        ensureDefaultActiveProfile()
        return savedId
    }

    suspend fun deleteProfile(profileId: Long) {
        val entity = database.miningProfileDao().getById(profileId) ?: return
        val wasActiveProfile = activeProfileId.first() == profileId
        database.miningProfileDao().delete(entity)
        if (wasActiveProfile) {
            val replacement = profiles.first().firstOrNull()
            setActiveProfile(replacement?.id)
        }
        runCatching {
            withContext(Dispatchers.IO) {
                secureSecretStore.removeSecret(entity.passwordAlias)
            }
        }
    }
    companion object {
        private val ACTIVE_PROFILE_KEY = longPreferencesKey("active_profile_id")
    }
}

private object ListSerializerProvider {
    val backupPoolSerializer = kotlinx.serialization.builtins.ListSerializer(
        com.monandroido.data.model.BackupPool.serializer(),
    )
}

private fun MiningProfileEntity.toSummary(): MiningProfileSummary =
    MiningProfileSummary(
        id = id,
        name = name,
        primaryPoolUrl = primaryPoolUrl,
        walletAddress = walletAddress,
        rigId = rigId,
        tls = tls,
        enabled = enabled,
        advancedSettings = AdvancedMinerSettings(
            algorithmMode = algorithmMode,
            maxThreadsHint = maxThreadsHint,
            retryCount = retryCount,
            retryPauseSeconds = retryPauseSeconds,
            continueWhenScreenOff = continueWhenScreenOff,
            requireCharging = requireCharging,
            keepAlive = keepAlive,
            backupPools = decodeBackupPools(backupPoolsJson),
        ),
    )

private fun MiningProfileEntity.toProfile(secret: String): MiningProfile =
    MiningProfile(
        id = id,
        name = name,
        primaryPoolUrl = primaryPoolUrl,
        walletAddress = walletAddress,
        password = secret,
        rigId = rigId,
        tls = tls,
        enabled = enabled,
        advancedSettings = toSummary().advancedSettings,
    )

private fun decodeBackupPools(rawJson: String): List<com.monandroido.data.model.BackupPool> =
    runCatching {
        profileJson.decodeFromString(
            ListSerializerProvider.backupPoolSerializer,
            rawJson.ifBlank { "[]" },
        )
    }.getOrDefault(emptyList())
