package com.monandroido.app.ui.screens.benchmark

import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.monandroido.app.R
import com.monandroido.app.ui.BenchmarkUiState
import com.monandroido.app.ui.components.SectionCard
import com.monandroido.data.model.AlgorithmMode
import com.monandroido.data.model.BenchmarkPreset
import com.monandroido.data.model.BenchmarkResult
import com.monandroido.data.model.label
import com.monandroido.miner.model.MinerStatus
import com.monandroido.miner.presentation.formatHashrateText
import java.text.DateFormat
import java.util.Date

@Composable
fun BenchmarkScreen(
    uiState: BenchmarkUiState,
    onStartBenchmark: (BenchmarkPreset, AlgorithmMode) -> Unit,
    onExportHistory: () -> Unit,
    onClearHistory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val canStartBenchmark = uiState.minerState.status == MinerStatus.STOPPED ||
        uiState.minerState.status == MinerStatus.ERROR
    var confirmClearHistory by remember { mutableStateOf(false) }
    val sortedResults = sortBenchmarkResultsForDisplay(uiState.benchmarkResults)

    LazyColumn(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            SectionCard(title = context.getString(R.string.benchmark_run_title)) {
                Text(
                    text = context.getString(R.string.benchmark_run_description),
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (!canStartBenchmark) {
                    Text(
                        text = context.getString(R.string.benchmark_stop_session_warning),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 14.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = { onStartBenchmark(BenchmarkPreset.ONE_MEGA, AlgorithmMode.RX0) },
                            enabled = canStartBenchmark,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(
                                context.getString(
                                    R.string.benchmark_button_label,
                                    BenchmarkPreset.ONE_MEGA.label(context),
                                    AlgorithmMode.RX0.xmrigValue,
                                ),
                            )
                        }
                        OutlinedButton(
                            onClick = { onStartBenchmark(BenchmarkPreset.TEN_MEGA, AlgorithmMode.RX0) },
                            enabled = canStartBenchmark,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(
                                context.getString(
                                    R.string.benchmark_button_label,
                                    BenchmarkPreset.TEN_MEGA.label(context),
                                    AlgorithmMode.RX0.xmrigValue,
                                ),
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { onStartBenchmark(BenchmarkPreset.ONE_MEGA, AlgorithmMode.RXWOW) },
                            enabled = canStartBenchmark,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(
                                context.getString(
                                    R.string.benchmark_button_label,
                                    BenchmarkPreset.ONE_MEGA.label(context),
                                    AlgorithmMode.RXWOW.xmrigValue,
                                ),
                            )
                        }
                        OutlinedButton(
                            onClick = { onStartBenchmark(BenchmarkPreset.TEN_MEGA, AlgorithmMode.RXWOW) },
                            enabled = canStartBenchmark,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(
                                context.getString(
                                    R.string.benchmark_button_label,
                                    BenchmarkPreset.TEN_MEGA.label(context),
                                    AlgorithmMode.RXWOW.xmrigValue,
                                ),
                            )
                        }
                    }
                }
                uiState.minerState.benchmarkHashrate?.let {
                    Text(
                        text = context.getString(
                            R.string.benchmark_latest_running_result,
                            context.formatHashrateText(it),
                        ),
                        modifier = Modifier.padding(top = 12.dp),
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }

        if (uiState.benchmarkResults.isEmpty()) {
            item {
                SectionCard(title = context.getString(R.string.benchmark_saved_results_title)) {
                    Text(context.getString(R.string.benchmark_no_results))
                }
            }
        } else {
            item {
                val summary = uiState.historySummary
                SectionCard(title = context.getString(R.string.benchmark_history_summary_title)) {
                    Text(
                        text = context.getString(
                            R.string.benchmark_best_result,
                            summary.bestResult?.let { context.formatHashrateText(it.avgHashrate) }
                                ?: context.getString(R.string.label_not_applicable),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        context.getString(
                            R.string.benchmark_average_hashrate,
                            summary.averageHashrate?.let { context.formatHashrateText(it) }
                                ?: context.getString(R.string.label_not_applicable),
                        ),
                    )
                    Text(
                        context.getString(
                            R.string.benchmark_latest_preset,
                            summary.latestResult?.let {
                                "${it.preset.label(context)} | ${it.algorithmMode.label(context)}"
                            } ?: context.getString(R.string.label_not_applicable),
                        ),
                    )
                    Text(
                        context.getString(
                            R.string.benchmark_latest_run,
                            summary.latestResult?.let { formatRecordedAt(it.createdAt) }
                                ?: context.getString(R.string.label_not_applicable),
                        ),
                    )
                    Text(context.getString(R.string.benchmark_algorithms_tested, summary.algorithmsCovered))
                }
            }
            item {
                SectionCard(title = context.getString(R.string.benchmark_saved_results_title)) {
                    Text(
                        text = context.resources.getQuantityString(
                            R.plurals.benchmark_saved_results_count,
                            uiState.historySummary.totalRuns,
                            uiState.historySummary.totalRuns,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = context.getString(R.string.benchmark_saved_results_description),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    OutlinedButton(
                        onClick = { confirmClearHistory = true },
                        modifier = Modifier.padding(top = 12.dp),
                    ) {
                        Text(context.getString(R.string.action_clear_history))
                    }
                    TextButton(
                        onClick = onExportHistory,
                        modifier = Modifier.padding(top = 4.dp),
                    ) {
                        Text(context.getString(R.string.action_export_csv))
                    }
                }
            }
        }

        items(
            items = sortedResults,
            key = { it.id },
        ) { result ->
            SectionCard(title = "${result.algorithmMode.label(context)} | ${result.preset.label(context)}") {
                Text(
                    context.formatHashrateText(result.avgHashrate),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    context.getString(
                        R.string.benchmark_duration,
                        formatDuration(context, result.durationMillis),
                    ),
                )
                Text(
                    context.getString(
                        R.string.benchmark_battery_delta,
                        result.batteryDeltaPercent?.let { "$it%" }
                            ?: context.getString(R.string.label_not_applicable),
                    ),
                )
                Text(
                    context.getString(
                        R.string.benchmark_thermal_peak,
                        result.peakThermal?.toString()
                            ?: context.getString(R.string.label_not_applicable),
                    ),
                )
                Text(
                    context.getString(
                        R.string.benchmark_recorded,
                        formatRecordedAt(result.createdAt),
                    ),
                )
            }
        }
    }

    if (confirmClearHistory) {
        AlertDialog(
            onDismissRequest = { confirmClearHistory = false },
            title = { Text(context.getString(R.string.dialog_clear_benchmark_history_title)) },
            text = { Text(context.getString(R.string.dialog_clear_benchmark_history_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onClearHistory()
                        confirmClearHistory = false
                    },
                ) {
                    Text(context.getString(R.string.action_clear))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearHistory = false }) {
                    Text(context.getString(R.string.action_cancel))
                }
            },
        )
    }
}

private fun formatRecordedAt(createdAt: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(createdAt))

private fun formatDuration(
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

internal fun sortBenchmarkResultsForDisplay(results: List<BenchmarkResult>): List<BenchmarkResult> =
    results.sortedWith(compareByDescending<BenchmarkResult> { it.createdAt }.thenByDescending { it.id })
