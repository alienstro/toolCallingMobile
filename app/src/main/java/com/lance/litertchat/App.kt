package com.lance.litertchat

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.lance.litertchat.model.ModelRepository
import com.lance.litertchat.ui.AppViewModel
import com.lance.litertchat.ui.ChatScreen
import com.lance.litertchat.ui.DiagnosticsScreen
import com.lance.litertchat.ui.ModelManagerScreen

@Composable
fun LiteRtChatApp(appViewModel: AppViewModel = rememberAppViewModel()) {
    val context = LocalContext.current.applicationContext
    val state by appViewModel.state.collectAsState()
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: AppRoute.Models.route

    MaterialTheme {
        Surface {
            Scaffold(
                bottomBar = {
                    NavigationBar {
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
                                icon = {}
                            )
                        }
                    }
                }
            ) { contentPadding ->
                NavHost(
                    navController = navController,
                    startDestination = AppRoute.Models.route
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
                            onSend = appViewModel::sendMessage
                        )
                    }
                    composable(AppRoute.Diagnostics.route) {
                        DiagnosticsScreen(state = state, contentPadding = contentPadding)
                    }
                }
            }
        }
    }
}

private enum class AppRoute(
    val route: String,
    val label: String
) {
    Models("models", "Models"),
    Chat("chat", "Chat"),
    Diagnostics("diagnostics", "Diagnostics")
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
                return AppViewModel(ModelRepository(context.filesDir)) as T
            }
        }
    )
}
