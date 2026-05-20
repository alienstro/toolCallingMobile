package com.lance.llamacppchat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.FileProvider
import com.lance.llamacppchat.overlay.OverlayService
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.lance.llamacppchat.model.ModelRepository
import com.lance.llamacppchat.ui.AppAccent
import com.lance.llamacppchat.ui.AppAccentSoft
import com.lance.llamacppchat.ui.AppBackground
import com.lance.llamacppchat.ui.AppBackgroundBrush
import com.lance.llamacppchat.ui.AppBorder
import com.lance.llamacppchat.ui.AppFaint
import com.lance.llamacppchat.ui.AppMuted
import com.lance.llamacppchat.ui.AppSurface
import com.lance.llamacppchat.ui.AppText
import com.lance.llamacppchat.ui.AppTheme
import com.lance.llamacppchat.ui.AppViewModel
import com.lance.llamacppchat.ui.ChatScreen
import com.lance.llamacppchat.ui.DiagnosticsScreen
import com.lance.llamacppchat.inference.LlamaCppChatEngine
import com.lance.llamacppchat.inference.LlamaCppEmbeddingEngine
import com.lance.llamacppchat.ui.ModelManagerScreen
import com.lance.llamacppchat.ui.SettingsScreen
import com.lance.llamacppchat.ui.StatusPill
import com.lance.llamacppchat.ui.chromeRuntimeStatus

@Composable
fun LlamaCppChatApp(appViewModel: AppViewModel = rememberAppViewModel()) {
    val context = LocalContext.current.applicationContext
    val state by appViewModel.state.collectAsState()
    val overlayPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (Settings.canDrawOverlays(context)) {
            appViewModel.setOverlayEnabled(true)
            context.startForegroundService(Intent(context, OverlayService::class.java))
        }
    }
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: AppRoute.Chat.route

    AppTheme {
        Surface(color = AppBackground) {
            Scaffold(
                containerColor = Color.Transparent,
                topBar = {
                    AppTopBar(
                        route = AppRoute.entries.firstOrNull { it.route == currentRoute } ?: AppRoute.Models,
                        state = state
                    )
                },
                bottomBar = {
                    NavigationBar(
                        containerColor = AppSurface.copy(alpha = 0.96f),
                        tonalElevation = 0.dp,
                        modifier = Modifier.background(AppSurface.copy(alpha = 0.96f))
                    ) {
                        AppRoute.entries.forEach { appRoute ->
                            NavigationBarItem(
                                selected = currentRoute == appRoute.route,
                                onClick = {
                                    navController.navigate(appRoute.route) {
                                        launchSingleTop = true
                                        restoreState = true
                                        popUpTo(navController.graph.startDestinationId) {
                                            saveState = true
                                        }
                                    }
                                },
                                label = { Text(appRoute.label) },
                                icon = { RouteIcon(appRoute = appRoute, selected = currentRoute == appRoute.route) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = AppAccent,
                                    selectedTextColor = AppAccent,
                                    unselectedIconColor = AppFaint,
                                    unselectedTextColor = AppFaint,
                                    indicatorColor = AppAccentSoft
                                )
                            )
                        }
                    }
                }
            ) { contentPadding ->
                Box(modifier = Modifier.background(AppBackgroundBrush)) {
                    NavHost(
                        navController = navController,
                        startDestination = AppRoute.Chat.route
                    ) {
                        composable(AppRoute.Models.route) {
                            ModelManagerScreen(
                                state = state,
                                contentPadding = contentPadding,
                                onDownload = appViewModel::downloadModel,
                                onDelete = appViewModel::deleteModel,
                                onImport = { uri ->
                                    appViewModel.importModelFromUri(context, uri)
                                }
                            )
                        }
                        composable(AppRoute.Chat.route) {
                            ChatScreen(
                                state = state,
                                contentPadding = contentPadding,
                                onSend = appViewModel::sendMessage,
                                onStop = appViewModel::stopGeneration,
                                onNewChat = appViewModel::startNewChat,
                                onSelectChat = appViewModel::selectChatSession,
                                onDeleteChat = appViewModel::deleteChatSession,
                                onCreateImageCaptureFile = { appViewModel.createChatImageFile(context) },
                                onImageCaptureUri = { file ->
                                    FileProvider.getUriForFile(
                                        context,
                                        "${context.packageName}.fileprovider",
                                        file
                                    )
                                },
                                onImportImage = { uri -> appViewModel.copyChatImageFromUri(context, uri) }
                            )
                        }
                        composable(AppRoute.Diagnostics.route) {
                            DiagnosticsScreen(state = state, contentPadding = contentPadding)
                        }
                        composable(AppRoute.Settings.route) {
                            SettingsScreen(
                                state = state,
                                contentPadding = contentPadding,
                                onCreateFormatter = appViewModel::createPromptFormatter,
                                onUpdateFormatter = appViewModel::updatePromptFormatter,
                                onDeleteFormatter = appViewModel::deletePromptFormatter,
                                onSelectFormatter = appViewModel::selectPromptFormatter,
                                onResetDefaultFormatter = appViewModel::resetDefaultPromptFormatter,
                                onUpsertMemory = appViewModel::upsertMemory,
                                onDeleteMemory = appViewModel::deleteMemory,
                                onStreamResponsesChanged = appViewModel::setStreamResponsesEnabled,
                                onGpuBackendChanged = appViewModel::setGpuBackendEnabled,
                                onNpuBackendChanged = appViewModel::setNpuBackendEnabled,
                                onGemmaMtpChanged = appViewModel::setGemmaMtpEnabled,
                                onOverlayChanged = { enabled ->
                                    if (enabled) {
                                        if (Settings.canDrawOverlays(context)) {
                                            appViewModel.setOverlayEnabled(true)
                                            context.startForegroundService(Intent(context, OverlayService::class.java))
                                        } else {
                                            overlayPermissionLauncher.launch(
                                                Intent(
                                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                                    android.net.Uri.parse("package:${context.packageName}")
                                                )
                                            )
                                        }
                                    } else {
                                        appViewModel.setOverlayEnabled(false)
                                        context.stopService(Intent(context, OverlayService::class.java))
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppTopBar(route: AppRoute, state: com.lance.llamacppchat.ui.AppState) {
    val runtimeStatus = chromeRuntimeStatus(state)
    val modelLine = state.activeModel?.let { "${it.fileName} - Ready" } ?: "No model installed"
    val runtimeRequest = state.runtimeStatus.requested.label

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .background(AppSurface.copy(alpha = 0.94f))
            .border(1.dp, AppBorder)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = route.label,
                color = AppText,
                style = MaterialTheme.typography.headlineSmall,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            StatusPill(runtimeStatus.label, runtimeStatus.tone, withDot = true)
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = modelLine,
                color = AppMuted,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
            StatusPill("$runtimeRequest requested")
        }
    }
}

@Composable
private fun RouteIcon(appRoute: AppRoute, selected: Boolean) {
    val color = if (selected) AppAccent else AppMuted
    Canvas(
        modifier = Modifier
            .size(18.dp)
            .padding(1.dp)
    ) {
        val stroke = Stroke(width = 2.6f, cap = StrokeCap.Round)
        val w = size.width
        val h = size.height
        when (appRoute) {
            AppRoute.Chat -> {
                drawRoundRect(color = color, style = stroke)
                drawLine(color, Offset(w * 0.28f, h * 0.72f), Offset(w * 0.18f, h * 0.92f), strokeWidth = 2.6f, cap = StrokeCap.Round)
            }
            AppRoute.Models -> {
                drawLine(color, Offset(w * 0.18f, h * 0.25f), Offset(w * 0.82f, h * 0.25f), strokeWidth = 2.6f, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.18f, h * 0.50f), Offset(w * 0.82f, h * 0.50f), strokeWidth = 2.6f, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.18f, h * 0.75f), Offset(w * 0.82f, h * 0.75f), strokeWidth = 2.6f, cap = StrokeCap.Round)
                drawCircle(color, radius = 3.2f, center = Offset(w * 0.32f, h * 0.25f))
                drawCircle(color, radius = 3.2f, center = Offset(w * 0.62f, h * 0.50f))
                drawCircle(color, radius = 3.2f, center = Offset(w * 0.42f, h * 0.75f))
            }
            AppRoute.Diagnostics -> {
                drawLine(color, Offset(w * 0.16f, h * 0.82f), Offset(w * 0.86f, h * 0.82f), strokeWidth = 2.6f, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.18f, h * 0.82f), Offset(w * 0.18f, h * 0.18f), strokeWidth = 2.6f, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.24f, h * 0.68f), Offset(w * 0.42f, h * 0.48f), strokeWidth = 2.6f, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.42f, h * 0.48f), Offset(w * 0.58f, h * 0.58f), strokeWidth = 2.6f, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.58f, h * 0.58f), Offset(w * 0.78f, h * 0.28f), strokeWidth = 2.6f, cap = StrokeCap.Round)
            }
            AppRoute.Settings -> {
                drawLine(color, Offset(w * 0.18f, h * 0.32f), Offset(w * 0.82f, h * 0.32f), strokeWidth = 2.6f, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.18f, h * 0.68f), Offset(w * 0.82f, h * 0.68f), strokeWidth = 2.6f, cap = StrokeCap.Round)
                drawCircle(color, radius = 5f, center = Offset(w * 0.40f, h * 0.32f), style = stroke)
                drawCircle(color, radius = 5f, center = Offset(w * 0.64f, h * 0.68f), style = stroke)
            }
        }
    }
}

private enum class AppRoute(
    val route: String,
    val label: String
) {
    Chat("chat", "Chat"),
    Models("models", "Models"),
    Settings("settings", "Settings"),
    Diagnostics("diagnostics", "Diagnosis")
}

@Composable
private fun rememberAppViewModel(): AppViewModel {
    val context = LocalContext.current.applicationContext
    return viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                require(modelClass.isAssignableFrom(AppViewModel::class.java)) {
                    "Unknown ViewModel class ${modelClass.name}"
                }
                return AppViewModel(
                    repository = ModelRepository(context.filesDir),
                    engine = LlamaCppChatEngine(context),
                    embeddingEngine = LlamaCppEmbeddingEngine(context)
                ) as T
            }
        }
    )
}
