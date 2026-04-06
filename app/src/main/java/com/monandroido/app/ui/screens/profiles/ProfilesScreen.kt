package com.monandroido.app.ui.screens.profiles

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.monandroido.app.R
import com.monandroido.app.ui.ProfilesUiState
import com.monandroido.app.ui.components.SectionCard
import com.monandroido.data.model.AlgorithmMode
import com.monandroido.data.model.MiningProfileSummary
import com.monandroido.data.model.ProfileDraft
import com.monandroido.data.model.label
import com.monandroido.data.model.parsePoolEndpoint
import com.monandroido.miner.model.MinerStatus

@Composable
fun ProfilesScreen(
    uiState: ProfilesUiState,
    onActivate: (Long) -> Unit,
    onDuplicate: (Long) -> Unit,
    onExport: (Long, String) -> Unit,
    onExportAll: () -> Unit,
    onImport: () -> Unit,
    onDelete: (Long) -> Unit,
    onEdit: (Long?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var pendingDeleteId by remember { mutableLongStateOf(-1L) }
    val profilePendingDelete = uiState.profiles.firstOrNull { it.id == pendingDeleteId }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 104.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(contentType = "library_actions") {
                SectionCard(
                    title = context.getString(R.string.profiles_library_title),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(context.getString(R.string.profiles_library_description))
                    Text(
                        text = context.getString(R.string.profiles_library_warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    Row(
                        modifier = Modifier.padding(top = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        TextButton(onClick = onImport) {
                            Text(context.getString(R.string.action_import_file))
                        }
                        TextButton(
                            onClick = onExportAll,
                            enabled = uiState.profiles.isNotEmpty(),
                        ) {
                            Text(context.getString(R.string.action_export_all))
                        }
                    }
                }
            }

            if (uiState.profiles.isEmpty()) {
                item(contentType = "empty") {
                    SectionCard(
                        title = context.getString(R.string.profiles_empty_title),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(context.getString(R.string.profiles_empty_description))
                    }
                }
            }

            items(
                items = uiState.profiles,
                key = { it.id },
                contentType = { "profile" },
            ) { profile ->
                val deletionBlocked = profile.id == uiState.activeProfileId && uiState.minerStatus.blocksDeletion()
                SectionCard(
                    title = profile.name,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(profile.primaryPoolUrl, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = context.getString(
                            R.string.profiles_wallet_template,
                            maskWalletAddressForList(profile.walletAddress),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    profile.rigId?.takeIf { it.isNotBlank() }?.let { rigId ->
                        Text(
                            text = context.getString(R.string.profiles_rig_id_template, rigId),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Text(
                        context.getString(
                            R.string.profiles_status_template,
                            if (profile.enabled) {
                                context.getString(R.string.profiles_status_enabled)
                            } else {
                                context.getString(R.string.profiles_status_disabled)
                            },
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (profile.enabled) {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                    )
                    Text(
                        text = buildProfileMetaSummary(
                            profile = profile,
                            algorithmLabel = profile.advancedSettings.algorithmMode.label(context),
                            tlsEnabledLabel = context.getString(R.string.label_on),
                            tlsDisabledLabel = context.getString(R.string.label_off),
                            summaryTemplate = context.getString(R.string.profiles_meta_summary),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        AssistChip(
                            onClick = { onActivate(profile.id) },
                            enabled = profile.id != uiState.activeProfileId,
                            label = {
                                Text(
                                    if (profile.id == uiState.activeProfileId) {
                                        context.getString(R.string.profiles_active_profile)
                                    } else {
                                        context.getString(R.string.profiles_set_active)
                                    },
                                )
                            },
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TextButton(onClick = { onExport(profile.id, profile.name) }) {
                                Text(context.getString(R.string.action_export))
                            }
                            TextButton(onClick = { onDuplicate(profile.id) }) {
                                Text(context.getString(R.string.action_duplicate))
                            }
                            Spacer(modifier = Modifier.weight(1f))
                            IconButton(onClick = { onEdit(profile.id) }) {
                                Icon(
                                    Icons.Outlined.Edit,
                                    contentDescription = context.getString(R.string.profiles_edit_content_description),
                                )
                            }
                            IconButton(
                                onClick = { pendingDeleteId = profile.id },
                                enabled = !deletionBlocked,
                            ) {
                                Icon(
                                    Icons.Outlined.Delete,
                                    contentDescription = context.getString(R.string.profiles_delete_content_description),
                                )
                            }
                        }
                    }
                    if (deletionBlocked) {
                        Text(
                            text = context.getString(R.string.profiles_stop_before_delete),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { onEdit(null) },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
        ) {
            Icon(
                Icons.Outlined.Add,
                contentDescription = context.getString(R.string.profiles_create_content_description),
            )
        }
    }

    if (profilePendingDelete != null) {
        AlertDialog(
            onDismissRequest = { pendingDeleteId = -1L },
            title = { Text(context.getString(R.string.dialog_delete_profile_title)) },
            text = {
                Text(
                    context.getString(
                        R.string.dialog_delete_profile_message,
                        profilePendingDelete.name,
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete(profilePendingDelete.id)
                        pendingDeleteId = -1L
                    },
                ) {
                    Text(context.getString(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteId = -1L }) {
                    Text(context.getString(R.string.action_cancel))
                }
            },
        )
    }
}

fun profileDraftWithAdvancedText(
    draft: ProfileDraft,
    backupPoolsText: String,
    algorithmMode: AlgorithmMode,
    maxThreadsHint: Int,
    retryCount: Int,
    retryPauseSeconds: Int,
    continueWhenScreenOff: Boolean,
    requireCharging: Boolean,
    keepAlive: Boolean,
): ProfileDraft {
    val parsedPrimaryPool = parsePoolEndpoint(draft.primaryPoolUrl)
    val effectiveTls = parsedPrimaryPool?.tlsOverride ?: draft.tls

    return draft.copy(
        primaryPoolUrl = parsedPrimaryPool?.normalizedUrl ?: draft.primaryPoolUrl.trim(),
        tls = effectiveTls,
        advancedSettings = draft.advancedSettings.copy(
            algorithmMode = algorithmMode,
            maxThreadsHint = maxThreadsHint,
            retryCount = retryCount,
            retryPauseSeconds = retryPauseSeconds,
            continueWhenScreenOff = continueWhenScreenOff,
            requireCharging = requireCharging,
            keepAlive = keepAlive,
            backupPools = com.monandroido.app.ui.parseBackupPools(
                multiline = backupPoolsText,
                defaultTls = effectiveTls,
                defaultKeepAlive = keepAlive,
            ),
        ),
    )
}

private fun MinerStatus.blocksDeletion(): Boolean =
    this == MinerStatus.RUNNING ||
        this == MinerStatus.PAUSED ||
        this == MinerStatus.STARTING

internal fun maskWalletAddressForList(walletAddress: String): String {
    val normalized = walletAddress.trim()
    if (normalized.length <= 18) {
        return normalized
    }
    return "${normalized.take(8)}...${normalized.takeLast(8)}"
}

internal fun buildProfileMetaSummary(
    profile: MiningProfileSummary,
    algorithmLabel: String,
    tlsEnabledLabel: String,
    tlsDisabledLabel: String,
    summaryTemplate: String,
): String = summaryTemplate.format(
    algorithmLabel,
    if (profile.tls) tlsEnabledLabel else tlsDisabledLabel,
    profile.advancedSettings.backupPools.size,
)
