package com.lance.litertchat

import androidx.compose.foundation.background
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
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.FileProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.lance.litertchat.model.ModelRepository
import com.lance.litertchat.ui.AppAccent
import com.lance.litertchat.ui.AppAccentSoft
import com.lance.litertchat.ui.AppBackground
import com.lance.litertchat.ui.AppMuted
import com.lance.litertchat.ui.AppSurface
import com.lance.litertchat.ui.AppText
import com.lance.litertchat.ui.AppTheme
import com.lance.litertchat.ui.AppViewModel
import com.lance.litertchat.ui.ChatScreen
import com.lance.litertchat.ui.DiagnosticsScreen
import com.lance.litertchat.inference.LiteRtChatEngine
import com.lance.litertchat.ui.ModelManagerScreen
import com.lance.litertchat.ui.PillTone
import com.lance.litertchat.ui.SettingsScreen
import com.lance.litertchat.ui.StatusDot

@Composable
fun LiteRtChatApp(appViewModel: AppViewModel = rememberAppViewModel()) {
    val context = LocalContext.current.applicationContext
    val state by appViewModel.state.collectAsState()
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: AppRoute.Chat.route

    AppTheme {
        Surface {
            Scaffold(
                containerColor = AppBackground,
                topBar = {
                    AppTopBar(
                        route = AppRoute.entries.firstOrNull { it.route == currentRoute } ?: AppRoute.Models,
                        state = state
                    )
                },
                bottomBar = {
                    NavigationBar(
                        containerColor = AppSurface,
                        tonalElevation = 0.dp
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
                                icon = { NavGlyph(appRoute = appRoute, selected = currentRoute == appRoute.route) }
                            )
                        }
                    }
                }
            ) { contentPadding ->
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
                            onStreamResponsesChanged = appViewModel::setStreamResponsesEnabled,
                            onGpuBackendChanged = appViewModel::setGpuBackendEnabled,
                            onNpuBackendChanged = appViewModel::setNpuBackendEnabled,
                            onGemmaMtpChanged = appViewModel::setGemmaMtpEnabled
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AppTopBar(route: AppRoute, state: com.lance.litertchat.ui.AppState) {
    val status = when {
        state.isGenerating -> "Model is running"
        state.isDownloading -> "Downloading model"
        state.activeModel != null -> "${state.activeModel.fileName} - Ready"
        else -> "No model installed"
    }
    val tone = when {
        state.errorText != null -> PillTone.Warn
        state.isGenerating || state.isDownloading -> PillTone.Accent
        state.activeModel != null -> PillTone.Good
        else -> PillTone.Neutral
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .background(AppSurface)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = route.label,
                color = AppText,
                fontWeight = FontWeight.ExtraBold
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                modifier = Modifier.padding(top = 4.dp)
            ) {
                StatusDot(tone = tone)
                Text(text = status, color = AppMuted, maxLines = 1)
            }
        }
    }
}

@Composable
private fun NavGlyph(appRoute: AppRoute, selected: Boolean) {
    Box(
        modifier = Modifier
            .background(
                if (selected) AppAccentSoft else AppBackground,
                RoundedCornerShape(999.dp)
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        RouteIcon(appRoute = appRoute, selected = selected)
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
    Diagnostics("diagnostics", "Diag"),
    Settings("settings", "Settings")
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
                    engine = LiteRtChatEngine(
                        npuNativeLibraryDir = context.applicationInfo.nativeLibraryDir.orEmpty()
                    )
                ) as T
            }
        }
    )
}
