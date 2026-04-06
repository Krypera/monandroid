package com.monandroido.miner.notification

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.monandroido.data.model.label
import com.monandroido.miner.R
import com.monandroido.miner.model.MinerSessionState
import com.monandroido.miner.model.MinerStatus
import com.monandroido.miner.presentation.feeModeLabel
import com.monandroido.miner.presentation.formatHashrateText
import com.monandroido.miner.presentation.minerStatusLabel
import com.monandroido.miner.service.MiningForegroundService

class MinerNotificationFactory(private val context: Context) {
    fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.miner_notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.getString(R.string.miner_notification_channel_description)
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    fun build(state: MinerSessionState): Notification {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?.apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP }
        val openAppIntent = launchIntent?.let {
            PendingIntent.getActivity(
                context,
                99,
                it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_monandroido)
            .setContentTitle(buildContentTitle(state))
            .setContentText(buildContentText(state))
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(buildExpandedText(state))
                    .setSummaryText(context.getString(R.string.miner_app_name)),
            )
            .setContentIntent(openAppIntent)
            .setOngoing(state.status.isForegroundOngoing())
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)

        when (state.status) {
            MinerStatus.RUNNING -> {
                builder.addAction(action(R.string.miner_action_pause, 101, MiningForegroundService.ACTION_PAUSE))
                builder.addAction(action(R.string.miner_action_stop, 100, MiningForegroundService.ACTION_STOP))
            }

            MinerStatus.PAUSED -> {
                builder.addAction(action(R.string.miner_action_resume, 102, MiningForegroundService.ACTION_RESUME))
                builder.addAction(action(R.string.miner_action_stop, 100, MiningForegroundService.ACTION_STOP))
            }

            MinerStatus.STARTING,
            MinerStatus.BENCHMARKING -> {
                builder.addAction(action(R.string.miner_action_stop, 100, MiningForegroundService.ACTION_STOP))
            }

            MinerStatus.ERROR -> {
                builder.addAction(action(R.string.miner_action_dismiss, 100, MiningForegroundService.ACTION_STOP))
            }

            MinerStatus.STOPPED -> Unit
        }

        return builder.build()
    }

    @SuppressLint("MissingPermission")
    fun notify(state: MinerSessionState) {
        if (!canPostNotifications()) return
        runCatching {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, build(state))
        }
    }

    private fun canPostNotifications(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED

    private fun action(
        labelResId: Int,
        requestCode: Int,
        serviceAction: String,
    ): NotificationCompat.Action = NotificationCompat.Action(
        0,
        context.getString(labelResId),
        PendingIntent.getService(
            context,
            requestCode,
            MiningForegroundService.intent(context, serviceAction),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        ),
    )

    private fun buildContentTitle(state: MinerSessionState): String = when (state.status) {
        MinerStatus.RUNNING,
        MinerStatus.PAUSED,
        MinerStatus.STARTING -> state.activeProfileName ?: context.getString(R.string.miner_app_name)
        MinerStatus.BENCHMARKING -> context.getString(R.string.miner_notification_benchmark_title)
        MinerStatus.ERROR -> context.getString(R.string.miner_notification_error_title)
        MinerStatus.STOPPED -> context.getString(R.string.miner_app_name)
    }

    private fun buildContentText(state: MinerSessionState): String = when (state.status) {
        MinerStatus.RUNNING -> context.getString(
            R.string.miner_notification_running_text,
            context.formatHashrateText(state.hashrateHps),
            context.feeModeLabel(state.currentFeeMode),
        )
        MinerStatus.PAUSED -> state.pauseReason ?: context.getString(R.string.miner_notification_paused_text)
        MinerStatus.BENCHMARKING -> state.benchmarkAlgorithm?.let { algorithm ->
            context.getString(
                R.string.miner_notification_benchmarking_text,
                algorithm.label(context),
            )
        } ?: context.minerStatusLabel(MinerStatus.BENCHMARKING)
        MinerStatus.STARTING -> context.getString(R.string.miner_notification_starting_text)
        MinerStatus.ERROR -> state.lastError ?: context.getString(R.string.miner_notification_error_text)
        MinerStatus.STOPPED -> context.minerStatusLabel(MinerStatus.STOPPED)
    }

    private fun buildExpandedText(state: MinerSessionState): String = when (state.status) {
        MinerStatus.RUNNING -> buildString {
            appendLine(
                context.getString(
                    R.string.miner_notification_line_status,
                    context.minerStatusLabel(MinerStatus.RUNNING),
                ),
            )
            appendLine(
                context.getString(
                    R.string.miner_notification_line_mode,
                    context.feeModeLabel(state.currentFeeMode),
                ),
            )
            append(
                context.getString(
                    R.string.miner_notification_line_hashrate,
                    context.formatHashrateText(state.hashrateHps),
                ),
            )
            state.poolAddress?.takeIf { it.isNotBlank() }?.let { poolAddress ->
                appendLine()
                append(context.getString(R.string.miner_notification_line_pool, poolAddress))
            }
        }

        MinerStatus.PAUSED -> buildString {
            append(
                context.getString(
                    R.string.miner_notification_line_status,
                    context.minerStatusLabel(MinerStatus.PAUSED),
                ),
            )
            state.pauseReason?.takeIf { it.isNotBlank() }?.let { reason ->
                appendLine()
                append(context.getString(R.string.miner_notification_line_reason, reason))
            }
        }

        MinerStatus.BENCHMARKING -> buildString {
            append(
                context.getString(
                    R.string.miner_notification_line_status,
                    context.minerStatusLabel(MinerStatus.BENCHMARKING),
                ),
            )
            state.benchmarkAlgorithm?.let { algorithm ->
                appendLine()
                append(
                    context.getString(
                        R.string.miner_notification_line_algorithm,
                        algorithm.label(context),
                    ),
                )
            }
            state.benchmarkPreset?.let { preset ->
                appendLine()
                append(
                    context.getString(
                        R.string.miner_notification_line_preset,
                        preset.label(context),
                    ),
                )
            }
            state.benchmarkHashrate?.let { hashrate ->
                appendLine()
                append(
                    context.getString(
                        R.string.miner_notification_line_current_result,
                        context.formatHashrateText(hashrate),
                    ),
                )
            }
        }

        MinerStatus.STARTING -> context.getString(
            R.string.miner_notification_line_status,
            context.minerStatusLabel(MinerStatus.STARTING),
        )
        MinerStatus.ERROR -> buildString {
            append(
                context.getString(
                    R.string.miner_notification_line_status,
                    context.minerStatusLabel(MinerStatus.ERROR),
                ),
            )
            state.lastError?.takeIf { it.isNotBlank() }?.let { error ->
                appendLine()
                append(error)
            }
        }

        MinerStatus.STOPPED -> context.getString(
            R.string.miner_notification_line_status,
            context.minerStatusLabel(MinerStatus.STOPPED),
        )
    }

    companion object {
        const val CHANNEL_ID = "monandroido.mining"
        const val NOTIFICATION_ID = 2001
    }
}

private fun MinerStatus.isForegroundOngoing(): Boolean =
    this == MinerStatus.RUNNING ||
        this == MinerStatus.PAUSED ||
        this == MinerStatus.STARTING ||
        this == MinerStatus.BENCHMARKING
