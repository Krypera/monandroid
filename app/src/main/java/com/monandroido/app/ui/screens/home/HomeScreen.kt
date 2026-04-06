package com.monandroido.app.ui.screens.home

import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.TextButton
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.monandroido.app.R
import com.monandroido.app.ui.HomeUiState
import com.monandroido.app.ui.components.MetricTile
import com.monandroido.app.ui.components.SectionCard
import com.monandroido.miner.model.MinerStatus
import com.monandroido.miner.presentation.feeModeLabel
import com.monandroido.miner.presentation.formatHashrateText
import com.monandroido.miner.presentation.minerStatusLabel

@Composable
fun HomeScreen(
    uiState: HomeUiState,
    onToggleMining: () -> Unit,
    onPauseResume: () -> Unit,
    onClearLogs: () -> Unit,
    onCopyLogs: (String) -> Unit,
    onShareLogs: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val sessionStatus = uiState.minerState.status
    val sessionActive = sessionStatus.isActionableSession()
    val activeProfile = uiState.activeProfile
    val canStartSession = activeProfile?.enabled == true
    val fullLogText = uiState.recentLogs.joinToString(separator = "\n")
    val shouldShowWarmupNotice =
        (sessionStatus == MinerStatus.STARTING || sessionStatus == MinerStatus.RUNNING) &&
            uiState.minerState.hashrateHps <= 0.0 &&
            uiState.minerState.acceptedShares == 0 &&
            uiState.minerState.rejectedShares == 0 &&
            uiState.minerState.uptimeMillis < 15_000L

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SectionCard(title = context.getString(R.string.home_section_session)) {
            Text(
                text = activeProfile?.name ?: context.getString(R.string.home_no_active_profile),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = activeProfile?.primaryPoolUrl ?: context.getString(R.string.home_create_profile_to_begin),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
            )
            if (activeProfile?.enabled == false) {
                Text(
                    text = context.getString(R.string.home_profile_disabled),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            Text(
                text = context.getString(
                    R.string.home_status_template,
                    context.minerStatusLabel(sessionStatus),
                ),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(top = 12.dp),
            )
            Text(
                text = context.getString(
                    R.string.home_mode_template,
                    context.feeModeLabel(uiState.minerState.currentFeeMode),
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                modifier = Modifier.padding(top = 4.dp),
            )
            uiState.minerState.poolAddress?.takeIf { it.isNotBlank() }?.let { poolAddress ->
                Text(
                    text = context.getString(R.string.home_connected_pool_template, poolAddress),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    onClick = onToggleMining,
                    modifier = Modifier.weight(1f),
                    enabled = sessionActive || canStartSession,
                ) {
                    Text(
                        when (sessionStatus) {
                            MinerStatus.RUNNING,
                            MinerStatus.PAUSED,
                            MinerStatus.STARTING,
                            MinerStatus.BENCHMARKING -> context.getString(R.string.action_stop)
                            else -> context.getString(R.string.action_start)
                        },
                    )
                }
                OutlinedButton(
                    onClick = onPauseResume,
                    modifier = Modifier.weight(1f),
                    enabled = sessionStatus == MinerStatus.RUNNING || sessionStatus == MinerStatus.PAUSED,
                ) {
                    Text(
                        if (sessionStatus == MinerStatus.PAUSED) {
                            context.getString(R.string.action_resume)
                        } else {
                            context.getString(R.string.action_pause)
                        },
                    )
                }
            }
            if (!sessionActive && activeProfile == null) {
                Text(
                    text = context.getString(R.string.home_create_and_activate_profile),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
            if (!sessionActive && activeProfile?.enabled == false) {
                Text(
                    text = context.getString(R.string.home_enable_active_profile),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
            uiState.minerState.pauseReason?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
            uiState.minerState.lastError?.takeIf { sessionStatus == MinerStatus.ERROR }?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
            if (shouldShowWarmupNotice) {
                Text(
                    text = context.getString(R.string.home_warmup_notice),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        }

        val metricItems = listOf(
            Triple(
                context.getString(R.string.home_metric_hashrate),
                context.formatHashrateText(uiState.minerState.hashrateHps),
                Color(0xFFEA580C),
            ),
            Triple(context.getString(R.string.home_metric_accepted), uiState.minerState.acceptedShares.toString(), Color(0xFF15803D)),
            Triple(context.getString(R.string.home_metric_rejected), uiState.minerState.rejectedShares.toString(), Color(0xFFB91C1C)),
            Triple(
                context.getString(R.string.home_metric_uptime),
                formatElapsedTime(context, uiState.minerState.uptimeMillis),
                Color(0xFF0F766E),
            ),
        )
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            metricItems.chunked(2).forEach { rowItems ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    rowItems.forEach { item ->
                        MetricTile(
                            label = item.first,
                            value = item.second,
                            accent = item.third,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (rowItems.size == 1) {
                        Row(modifier = Modifier.weight(1f)) {}
                    }
                }
            }
        }

        uiState.minerState.lastSessionSummary?.let { summary ->
            SectionCard(title = context.getString(R.string.home_last_session_split)) {
                Text(
                    context.getString(
                        R.string.home_last_session_user_mining,
                        formatElapsedTime(context, summary.userDurationMillis),
                    ),
                )
                Text(
                    context.getString(
                        R.string.home_last_session_developer_fee,
                        formatElapsedTime(context, summary.developerDurationMillis),
                    ),
                )
            }
        }

        SectionCard(title = context.getString(R.string.home_recent_logs)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = if (uiState.recentLogs.isEmpty()) {
                        context.getString(R.string.home_recent_logs_empty_summary)
                    } else {
                        context.resources.getQuantityString(
                            R.plurals.home_recent_logs_stored,
                            uiState.recentLogs.size,
                            uiState.recentLogs.size,
                        )
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(
                        onClick = { onCopyLogs(fullLogText) },
                        enabled = uiState.recentLogs.isNotEmpty(),
                    ) {
                        Text(context.getString(R.string.action_copy))
                    }
                    TextButton(
                        onClick = { onShareLogs(fullLogText) },
                        enabled = uiState.recentLogs.isNotEmpty(),
                    ) {
                        Text(context.getString(R.string.action_share))
                    }
                    TextButton(
                        onClick = onClearLogs,
                        enabled = uiState.recentLogs.isNotEmpty(),
                    ) {
                        Text(context.getString(R.string.action_clear))
                    }
                }
            }
            if (uiState.recentLogs.isEmpty()) {
                Text(
                    text = context.getString(R.string.home_recent_logs_empty_body),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 12.dp),
                )
            } else {
                val visibleLogs = uiState.recentLogs.takeLast(12).asReversed()
                SelectionContainer {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 12.dp),
                    ) {
                        visibleLogs.forEach { line ->
                            Text(
                                text = line,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun MinerStatus.isActionableSession(): Boolean =
    this == MinerStatus.RUNNING ||
        this == MinerStatus.PAUSED ||
        this == MinerStatus.STARTING ||
        this == MinerStatus.BENCHMARKING

private fun formatElapsedTime(
    context: android.content.Context,
    durationMillis: Long,
): String {
    val totalSeconds = (durationMillis / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60

    return when {
        hours > 0 -> context.getString(R.string.elapsed_hours_minutes, hours, minutes)
        minutes > 0 -> context.getString(R.string.elapsed_minutes_seconds, minutes, seconds)
        else -> context.getString(R.string.elapsed_seconds, seconds)
    }
}
