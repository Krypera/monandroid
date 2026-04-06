package com.monandroido.app.ui.screens.profiles

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import com.monandroido.app.R
import com.monandroido.app.ui.MainViewModel
import com.monandroido.app.ui.backupPoolsAsText
import com.monandroido.app.ui.components.SectionCard
import com.monandroido.app.ui.userMessage
import com.monandroido.data.model.AlgorithmMode
import com.monandroido.data.model.ProfileDraft
import com.monandroido.data.model.validate
import kotlinx.coroutines.launch

@Composable
fun ProfileEditorScreen(
    profileId: Long?,
    advancedModeEnabled: Boolean,
    viewModel: MainViewModel,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val loadedDraftResult by produceState<Result<ProfileDraft>?>(initialValue = profileId?.let { null } ?: Result.success(ProfileDraft()), key1 = profileId) {
        value = runCatching { viewModel.loadDraft(profileId) }
    }
    val resolvedDraft = loadedDraftResult?.getOrNull()
    val loadError = loadedDraftResult?.exceptionOrNull()?.message
    if (loadedDraftResult == null) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator()
        }
        return
    }
    if (resolvedDraft == null) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            SectionCard(
                title = context.getString(R.string.profile_editor_unavailable_title),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = loadError ?: context.getString(R.string.error_profile_unavailable),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Button(
                    onClick = onDone,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                ) {
                    Text(context.getString(R.string.action_back_to_profiles))
                }
            }
        }
        return
    }

    var draft by remember { mutableStateOf(ProfileDraft()) }
    var backupPoolsText by remember { mutableStateOf("") }
    var algorithmMode by remember { mutableStateOf(AlgorithmMode.RX0) }
    var maxThreadsHintInput by remember { mutableStateOf("75") }
    var retryCountInput by remember { mutableStateOf("5") }
    var retryPauseInput by remember { mutableStateOf("5") }
    var continueWhenScreenOff by remember { mutableStateOf(false) }
    var requireCharging by remember { mutableStateOf(false) }
    var keepAlive by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(resolvedDraft) {
        draft = resolvedDraft
        backupPoolsText = backupPoolsAsText(
            settings = resolvedDraft.advancedSettings,
            defaultTls = resolvedDraft.tls,
        )
        algorithmMode = resolvedDraft.advancedSettings.algorithmMode
        maxThreadsHintInput = resolvedDraft.advancedSettings.maxThreadsHint.toString()
        retryCountInput = resolvedDraft.advancedSettings.retryCount.toString()
        retryPauseInput = resolvedDraft.advancedSettings.retryPauseSeconds.toString()
        continueWhenScreenOff = resolvedDraft.advancedSettings.continueWhenScreenOff
        requireCharging = resolvedDraft.advancedSettings.requireCharging
        keepAlive = resolvedDraft.advancedSettings.keepAlive
        isSaving = false
        saveError = null
    }

    val editorDraft = profileDraftWithAdvancedText(
        draft = draft,
        backupPoolsText = backupPoolsText,
        algorithmMode = algorithmMode,
        maxThreadsHint = maxThreadsHintInput.toIntOrNull() ?: 0,
        retryCount = retryCountInput.toIntOrNull() ?: -1,
        retryPauseSeconds = retryPauseInput.toIntOrNull() ?: -1,
        continueWhenScreenOff = continueWhenScreenOff,
        requireCharging = requireCharging,
        keepAlive = keepAlive,
    )
    val validation = editorDraft.validate()

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SectionCard(
            title = if (profileId == null) {
                context.getString(R.string.profile_editor_create_title)
            } else {
                context.getString(R.string.profile_editor_edit_title)
            },
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = draft.name,
                    onValueChange = { draft = draft.copy(name = it) },
                    label = { Text(context.getString(R.string.field_profile_name)) },
                    isError = validation.nameError != null,
                    supportingText = validation.nameError?.let { error -> { Text(error.resolve(context)) } },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = draft.primaryPoolUrl,
                    onValueChange = { draft = draft.copy(primaryPoolUrl = it) },
                    label = { Text(context.getString(R.string.field_pool_endpoint)) },
                    placeholder = { Text(context.getString(R.string.placeholder_pool_endpoint)) },
                    isError = validation.poolError != null,
                    supportingText = validation.poolError?.let { error -> { Text(error.resolve(context)) } },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = draft.walletAddress,
                    onValueChange = { draft = draft.copy(walletAddress = it) },
                    label = { Text(context.getString(R.string.field_wallet_address)) },
                    isError = validation.walletError != null,
                    supportingText = validation.walletError?.let { error -> { Text(error.resolve(context)) } },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = draft.password,
                    onValueChange = { draft = draft.copy(password = it) },
                    label = { Text(context.getString(R.string.field_password)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = draft.rigId,
                    onValueChange = { draft = draft.copy(rigId = it) },
                    label = { Text(context.getString(R.string.field_rig_id_optional)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(context.getString(R.string.field_enabled), style = MaterialTheme.typography.bodyLarge)
                    Switch(
                        checked = draft.enabled,
                        onCheckedChange = { draft = draft.copy(enabled = it) },
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(context.getString(R.string.field_tls), style = MaterialTheme.typography.bodyLarge)
                    Switch(
                        checked = draft.tls,
                        onCheckedChange = { draft = draft.copy(tls = it) },
                    )
                }
            }
        }

        if (advancedModeEnabled) {
            SectionCard(title = context.getString(R.string.advanced_mode_section_title)) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(context.getString(R.string.field_algorithm), fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        AlgorithmMode.entries.forEach { mode ->
                            if (algorithmMode == mode) {
                                Button(
                                    onClick = { algorithmMode = mode },
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text(mode.xmrigValue)
                                }
                            } else {
                                OutlinedButton(
                                    onClick = { algorithmMode = mode },
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text(mode.xmrigValue)
                                }
                            }
                        }
                    }
                    OutlinedTextField(
                        value = maxThreadsHintInput,
                        onValueChange = { maxThreadsHintInput = it.filter(Char::isDigit) },
                        label = { Text(context.getString(R.string.field_max_threads_hint)) },
                        isError = validation.maxThreadsHintError != null,
                        supportingText = validation.maxThreadsHintError?.let { error -> { Text(error.resolve(context)) } },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = retryCountInput,
                        onValueChange = { retryCountInput = it.filter(Char::isDigit) },
                        label = { Text(context.getString(R.string.field_retry_count)) },
                        isError = validation.retryCountError != null,
                        supportingText = validation.retryCountError?.let { error -> { Text(error.resolve(context)) } },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = retryPauseInput,
                        onValueChange = { retryPauseInput = it.filter(Char::isDigit) },
                        label = { Text(context.getString(R.string.field_retry_pause_seconds)) },
                        isError = validation.retryPauseError != null,
                        supportingText = validation.retryPauseError?.let { error -> { Text(error.resolve(context)) } },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = backupPoolsText,
                        onValueChange = { backupPoolsText = it },
                        label = { Text(context.getString(R.string.field_backup_pools)) },
                        placeholder = { Text(context.getString(R.string.placeholder_backup_pool_endpoint)) },
                        isError = validation.backupPoolsError != null,
                        supportingText = validation.backupPoolsError?.let { error -> { Text(error.resolve(context)) } },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(context.getString(R.string.field_keep_connection_alive))
                        Switch(checked = keepAlive, onCheckedChange = { keepAlive = it })
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(context.getString(R.string.field_continue_screen_off))
                        Switch(checked = continueWhenScreenOff, onCheckedChange = { continueWhenScreenOff = it })
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(context.getString(R.string.field_require_charging))
                        Switch(checked = requireCharging, onCheckedChange = { requireCharging = it })
                    }
                }
            }
        }

        saveError?.let { message ->
            Text(
                text = message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        Button(
            onClick = {
                if (isSaving) return@Button
                isSaving = true
                saveError = null
                coroutineScope.launch {
                    viewModel.saveProfile(editorDraft)
                        .onSuccess { onDone() }
                        .onFailure { throwable ->
                            isSaving = false
                            saveError = throwable.userMessage(context, R.string.error_profile_save_failed)
                        }
                }
            },
            enabled = validation.isValid && !isSaving,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                if (isSaving) {
                    context.getString(R.string.action_saving)
                } else {
                    context.getString(R.string.action_save_profile)
                },
            )
        }
    }
}
