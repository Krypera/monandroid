package com.monandroido.app.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.monandroido.app.R
import com.monandroido.app.ui.SettingsUiState
import com.monandroido.app.ui.components.SectionCard
import com.monandroido.miner.BuildConfig as MinerBuildConfig
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    uiState: SettingsUiState,
    onDeveloperFeeChanged: (Int) -> Unit,
    onAdvancedModeChanged: (Boolean) -> Unit,
    onCopyDeveloperWallet: (String) -> Unit,
    onExportDiagnostics: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var sliderValue by remember { mutableFloatStateOf(uiState.requestedDeveloperFeePercent.toFloat()) }

    LaunchedEffect(uiState.requestedDeveloperFeePercent, uiState.developerWalletConfigured) {
        sliderValue = if (uiState.developerWalletConfigured) {
            uiState.requestedDeveloperFeePercent.toFloat()
        } else {
            uiState.effectiveDeveloperFeePercent.toFloat()
        }
    }

    Column(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SectionCard(title = context.getString(R.string.settings_developer_fee_title)) {
            val roundedSliderValue = if (uiState.developerWalletConfigured) {
                sliderValue.roundToInt()
            } else {
                uiState.effectiveDeveloperFeePercent
            }
            Text(
                text = context.getString(R.string.settings_developer_fee_percent, roundedSliderValue),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = if (uiState.developerWalletConfigured) {
                    context.getString(
                        R.string.settings_developer_fee_enabled,
                        100 - roundedSliderValue,
                    )
                } else {
                    context.getString(R.string.settings_developer_fee_disabled)
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = context.getString(R.string.settings_developer_fee_note),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 6.dp),
            )
            Slider(
                value = sliderValue,
                onValueChange = { sliderValue = it.roundToInt().toFloat() },
                onValueChangeFinished = {
                    val committedValue = sliderValue.roundToInt()
                    if (committedValue != uiState.requestedDeveloperFeePercent) {
                        onDeveloperFeeChanged(committedValue)
                    }
                },
                valueRange = 0f..100f,
                steps = 99,
                enabled = uiState.developerWalletConfigured,
                modifier = Modifier.padding(top = 12.dp),
            )
            if (uiState.developerWalletConfigured) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = context.getString(R.string.settings_developer_wallet_title),
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Text(
                            text = maskWalletAddress(MinerBuildConfig.DEVELOPER_WALLET),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                        )
                    }
                    TextButton(onClick = { onCopyDeveloperWallet(MinerBuildConfig.DEVELOPER_WALLET) }) {
                        Text(context.getString(R.string.action_copy))
                    }
                }
            } else {
                Text(
                    text = context.getString(R.string.settings_developer_wallet_placeholder),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }

        SectionCard(title = context.getString(R.string.settings_advanced_mode_title)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        context.getString(R.string.settings_advanced_mode_toggle_title),
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        context.getString(R.string.settings_advanced_mode_toggle_description),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(
                    checked = uiState.advancedModeEnabled,
                    onCheckedChange = onAdvancedModeChanged,
                )
            }
        }

        SectionCard(title = context.getString(R.string.settings_support_title)) {
            Text(
                text = context.getString(
                    R.string.settings_version,
                    uiState.appVersionName,
                    uiState.appVersionCode,
                ),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = context.getString(R.string.settings_support_description),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 6.dp),
            )
            TextButton(
                onClick = onExportDiagnostics,
                modifier = Modifier.padding(top = 8.dp),
            ) {
                Text(context.getString(R.string.action_export_diagnostics))
            }
        }
    }
}

private fun maskWalletAddress(walletAddress: String): String {
    val normalized = walletAddress.trim()
    if (normalized.length <= 18) {
        return normalized
    }
    return "${normalized.take(10)}...${normalized.takeLast(10)}"
}
