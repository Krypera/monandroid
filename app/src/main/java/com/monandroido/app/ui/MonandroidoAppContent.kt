package com.monandroido.app.ui

import android.annotation.SuppressLint
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.monandroido.app.R
import com.monandroido.app.MonandroidoApplication
import com.monandroido.app.navigation.AppDestination
import com.monandroido.app.ui.screens.benchmark.BenchmarkScreen
import com.monandroido.app.ui.screens.home.HomeScreen
import com.monandroido.app.ui.screens.profiles.ProfileEditorScreen
import com.monandroido.app.ui.screens.profiles.ProfilesScreen
import com.monandroido.app.ui.screens.settings.SettingsScreen
import kotlinx.coroutines.launch

@SuppressLint("InlinedApi")
@Composable
fun MonandroidoAppContent(application: MonandroidoApplication) {
    val viewModel: MainViewModel = viewModel(factory = MainViewModel.factory(application))
    val navController = rememberNavController()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var pendingForegroundAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var pendingExportRequest by remember { mutableStateOf<PendingExportRequest?>(null) }
    var pendingExportIncludesSecrets by remember { mutableStateOf(true) }
    val navHome = stringResource(R.string.nav_home)
    val navProfiles = stringResource(R.string.nav_profiles)
    val navBenchmark = stringResource(R.string.nav_benchmark)
    val navSettings = stringResource(R.string.nav_settings)
    val exportBackupTitle = stringResource(R.string.dialog_export_backup_title)
    val exportBackupMessage = stringResource(R.string.dialog_export_backup_message)
    val includeSecretsLabel = stringResource(R.string.action_include_secrets)
    val stripSecretsLabel = stringResource(R.string.action_strip_secrets)
    val generatedProfileName = stringResource(R.string.profile_generated_name_default)

    fun showMessage(message: String) {
        coroutineScope.launch {
            snackbarHostState.showSnackbar(message)
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val action = pendingForegroundAction
        pendingForegroundAction = null
        if (granted) {
            action?.invoke()
        } else if (action != null) {
            showMessage(context.getString(R.string.message_notification_permission_required))
        }
    }
    val exportProfileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        val exportRequest = pendingExportRequest
        val includeSecrets = pendingExportIncludesSecrets
        pendingExportRequest = null
        if (uri == null) return@rememberLauncherForActivityResult
        coroutineScope.launch {
            if (exportRequest?.exportAll == true) {
                viewModel.exportAllProfiles(uri, includeSecrets = includeSecrets)
                    .onSuccess { profileCount ->
                        showMessage(
                            if (includeSecrets) {
                                context.resources.getQuantityString(
                                    R.plurals.message_app_backup_export_success,
                                    profileCount,
                                    profileCount,
                                )
                            } else {
                                context.resources.getQuantityString(
                                    R.plurals.message_app_backup_export_success_safe,
                                    profileCount,
                                    profileCount,
                                )
                            },
                        )
                    }
                    .onFailure { throwable ->
                        showMessage(throwable.userMessage(context, R.string.message_profile_library_export_failed))
                    }
            } else if (exportRequest?.profileId != null) {
                viewModel.exportProfile(
                    profileId = exportRequest.profileId,
                    destinationUri = uri,
                    includeSecrets = includeSecrets,
                )
                    .onSuccess { profileName ->
                        showMessage(
                            if (includeSecrets) {
                                context.getString(R.string.message_profile_export_success, profileName)
                            } else {
                                context.getString(R.string.message_profile_export_success_safe, profileName)
                            },
                        )
                    }
                    .onFailure { throwable ->
                        showMessage(throwable.userMessage(context, R.string.message_profile_export_failed))
                    }
            }
        }
    }
    val importProfileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        coroutineScope.launch {
            viewModel.importProfiles(uri)
                .onSuccess { result ->
                    val summary = context.resources.getQuantityString(
                        R.plurals.message_profiles_imported_from_backup,
                        result.importedCount,
                        result.importedCount,
                    )
                    val suffix = buildList {
                        if (result.restoredSettings) {
                            add(context.getString(R.string.message_import_settings_restored))
                        }
                        if (!result.includesSecrets) {
                            add(context.getString(R.string.message_import_passwords_not_included))
                        }
                    }.joinToString(separator = " ")
                    showMessage(
                        listOfNotNull(summary, suffix.takeIf { it.isNotBlank() }).joinToString(" "),
                    )
                }
                .onFailure { throwable ->
                    showMessage(throwable.userMessage(context, R.string.message_profile_import_failed))
                }
        }
    }
    val exportDiagnosticsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        coroutineScope.launch {
            viewModel.exportDiagnostics(uri)
                .onSuccess { result ->
                    showMessage(
                        context.getString(
                            R.string.message_diagnostics_export_success,
                            result.profileCount,
                            result.benchmarkCount,
                            result.logLineCount,
                        ),
                    )
                }
                .onFailure { throwable ->
                    showMessage(throwable.userMessage(context, R.string.message_diagnostics_export_failed))
                }
        }
    }

    fun requestNotificationPermissionIfNeeded(block: () -> Unit) {
        if (!requiresNotificationPermission() || hasNotificationPermission(context)) {
            block()
            return
        }
        pendingForegroundAction = block
        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    val destinations = listOf(
        Triple(AppDestination.Home.route, navHome, Icons.Outlined.Home),
        Triple(AppDestination.Profiles.route, navProfiles, Icons.Outlined.Storage),
        Triple(AppDestination.Benchmark.route, navBenchmark, Icons.Outlined.Bolt),
        Triple(AppDestination.Settings.route, navSettings, Icons.Outlined.Settings),
    )
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = currentRoute?.startsWith("profile_editor") != true

    fun navigateToTopLevel(route: String) {
        if (currentRoute == route) return
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    Scaffold(
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState)
        },
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    destinations.forEach { (route, label, icon) ->
                        NavigationBarItem(
                            selected = currentRoute == route,
                            onClick = { navigateToTopLevel(route) },
                            icon = { Icon(icon, contentDescription = label) },
                            label = { Text(label) },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = AppDestination.Home.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(AppDestination.Home.route) {
                val uiState by viewModel.homeUiState.collectAsStateWithLifecycle()
                HomeScreen(
                    uiState = uiState,
                    onToggleMining = {
                        if (uiState.minerState.status.isForegroundActionInProgressOrRunning()) {
                            viewModel.toggleMining()
                        } else {
                            requestNotificationPermissionIfNeeded {
                                viewModel.toggleMining()
                            }
                        }
                    },
                    onPauseResume = viewModel::togglePauseResume,
                    onClearLogs = viewModel::clearRecentLogs,
                    onCopyLogs = { fullLogText ->
                        clipboardManager.setText(AnnotatedString(fullLogText))
                        showMessage(context.getString(R.string.message_recent_logs_copied))
                    },
                    onShareLogs = { fullLogText ->
                        shareLogs(context, fullLogText)
                    },
                )
            }
            composable(AppDestination.Profiles.route) {
                val uiState by viewModel.profilesUiState.collectAsStateWithLifecycle()
                ProfilesScreen(
                    uiState = uiState,
                    onActivate = viewModel::setActiveProfile,
                    onDuplicate = { profileId ->
                        coroutineScope.launch {
                            viewModel.duplicateProfile(profileId)
                                .onSuccess { profileName ->
                                    showMessage(context.getString(R.string.message_profile_created, profileName))
                                }
                                .onFailure { throwable ->
                                    showMessage(throwable.userMessage(context, R.string.message_profile_duplication_failed))
                                }
                        }
                    },
                    onExport = { profileId, profileName ->
                        pendingExportRequest = PendingExportRequest(
                            profileId = profileId,
                            profileName = profileName,
                            exportAll = false,
                        )
                    },
                    onExportAll = {
                        pendingExportRequest = PendingExportRequest(exportAll = true)
                    },
                    onImport = {
                        importProfileLauncher.launch(arrayOf("application/json", "text/plain"))
                    },
                    onDelete = viewModel::deleteProfile,
                    onEdit = { navController.navigate(AppDestination.ProfileEditor.route(it)) },
                )
            }
            composable(
                route = AppDestination.ProfileEditor.route,
                arguments = listOf(
                    navArgument("profileId") {
                        type = NavType.LongType
                        defaultValue = -1L
                    },
                ),
            ) { entry ->
                val profileId = entry.arguments?.getLong("profileId")?.takeIf { it > 0 }
                val advancedModeEnabled by viewModel.advancedModeEnabled.collectAsStateWithLifecycle(false)
                ProfileEditorScreen(
                    profileId = profileId,
                    advancedModeEnabled = advancedModeEnabled,
                    viewModel = viewModel,
                    onDone = { navController.popBackStack() },
                )
            }
            composable(AppDestination.Benchmark.route) {
                val uiState by viewModel.benchmarkUiState.collectAsStateWithLifecycle()
                val exportBenchmarkHistoryLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.CreateDocument("text/csv"),
                ) { uri ->
                    if (uri == null) return@rememberLauncherForActivityResult
                    coroutineScope.launch {
                        viewModel.exportBenchmarkHistory(uri)
                            .onSuccess { exportedCount ->
                                showMessage(
                                    context.resources.getQuantityString(
                                        R.plurals.message_benchmark_results_exported,
                                        exportedCount,
                                        exportedCount,
                                    ),
                                )
                            }
                            .onFailure { throwable ->
                                showMessage(throwable.userMessage(context, R.string.message_benchmark_export_failed))
                            }
                    }
                }
                BenchmarkScreen(
                    uiState = uiState,
                    onStartBenchmark = { preset, algorithmMode ->
                        requestNotificationPermissionIfNeeded {
                            viewModel.startBenchmark(preset, algorithmMode)
                        }
                    },
                    onExportHistory = {
                        exportBenchmarkHistoryLauncher.launch(buildBenchmarkExportFileName())
                    },
                    onClearHistory = viewModel::clearBenchmarkHistory,
                )
            }
            composable(AppDestination.Settings.route) {
                val uiState by viewModel.settingsUiState.collectAsStateWithLifecycle()
                SettingsScreen(
                    uiState = uiState,
                    onDeveloperFeeChanged = viewModel::setDeveloperFee,
                    onAdvancedModeChanged = viewModel::setAdvancedModeEnabled,
                    onCopyDeveloperWallet = { wallet ->
                        clipboardManager.setText(AnnotatedString(wallet))
                        showMessage(context.getString(R.string.message_developer_wallet_copied))
                    },
                    onExportDiagnostics = {
                        exportDiagnosticsLauncher.launch(buildDiagnosticsExportFileName())
                    },
                )
            }
        }
    }

    pendingExportRequest?.let { exportRequest ->
        AlertDialog(
            onDismissRequest = { pendingExportRequest = null },
            title = { Text(exportBackupTitle) },
            text = {
                Text(exportBackupMessage)
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingExportIncludesSecrets = true
                        exportProfileLauncher.launch(
                            if (exportRequest.exportAll) {
                                buildProfileLibraryExportFileName(includeSecrets = true)
                            } else {
                                buildProfileExportFileName(
                                    profileName = exportRequest.profileName ?: generatedProfileName,
                                    includeSecrets = true,
                                )
                            },
                        )
                    },
                ) {
                    Text(includeSecretsLabel)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        pendingExportIncludesSecrets = false
                        exportProfileLauncher.launch(
                            if (exportRequest.exportAll) {
                                buildProfileLibraryExportFileName(includeSecrets = false)
                            } else {
                                buildProfileExportFileName(
                                    profileName = exportRequest.profileName ?: generatedProfileName,
                                    includeSecrets = false,
                                )
                            },
                        )
                    },
                ) {
                    Text(stripSecretsLabel)
                }
            },
        )
    }
}

private fun requiresNotificationPermission(): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

@SuppressLint("InlinedApi")
private fun hasNotificationPermission(context: android.content.Context): Boolean =
    ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.POST_NOTIFICATIONS,
    ) == PackageManager.PERMISSION_GRANTED

private fun com.monandroido.miner.model.MinerStatus.isForegroundActionInProgressOrRunning(): Boolean =
    this == com.monandroido.miner.model.MinerStatus.RUNNING ||
        this == com.monandroido.miner.model.MinerStatus.PAUSED ||
        this == com.monandroido.miner.model.MinerStatus.STARTING ||
        this == com.monandroido.miner.model.MinerStatus.BENCHMARKING

private fun shareLogs(context: Context, fullLogText: String) {
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.share_recent_logs_subject))
        putExtra(Intent.EXTRA_TEXT, fullLogText)
    }
    context.startActivity(
        Intent.createChooser(
            shareIntent,
            context.getString(R.string.share_recent_logs_chooser),
        ),
    )
}

private data class PendingExportRequest(
    val profileId: Long? = null,
    val profileName: String? = null,
    val exportAll: Boolean = false,
)
