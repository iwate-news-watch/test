package com.example.taskmanager

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.taskmanager.auth.AuthViewModel
import com.example.taskmanager.ui.screens.AddEditTaskScreen
import com.example.taskmanager.ui.screens.FocusScreen
import com.example.taskmanager.ui.screens.LoginScreen
import com.example.taskmanager.ui.screens.RegisterScreen
import com.example.taskmanager.ui.screens.TaskListScreen
import com.example.taskmanager.ui.theme.TaskManagerTheme

private object Routes {
    const val LOGIN = "login"
    const val REGISTER = "register"
    const val FOCUS = "focus"
    const val LIST = "list"
    const val ADD = "add"
    const val EDIT = "edit/{taskId}"
    fun edit(taskId: String) = "edit/$taskId"
}

class MainActivity : ComponentActivity() {

    private var pendingTaskId by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pendingTaskId = intent.extractTaskId()

        setContent {
            TaskManagerTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        RequestNotificationPermission()
                    }

                    val authViewModel: AuthViewModel = viewModel()
                    val navController = rememberNavController()
                    val backStackEntry by navController.currentBackStackEntryAsState()
                    val currentRoute = backStackEntry?.destination?.route

                    LaunchedEffect(pendingTaskId) {
                        pendingTaskId?.let { id ->
                            navController.navigate(Routes.edit(id))
                            pendingTaskId = null
                        }
                    }

                    fun clearBackStackTo(route: String) {
                        navController.navigate(route) {
                            popUpTo(navController.graph.findStartDestination().id) { inclusive = true }
                        }
                    }

                    Scaffold(
                        bottomBar = {
                            if (currentRoute == Routes.FOCUS || currentRoute == Routes.LIST) {
                                NavigationBar {
                                    NavigationBarItem(
                                        selected = currentRoute == Routes.FOCUS,
                                        onClick = {
                                            navController.navigate(Routes.FOCUS) {
                                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        },
                                        icon = { Icon(Icons.Default.CenterFocusStrong, contentDescription = null) },
                                        label = { Text("フォーカス") }
                                    )
                                    NavigationBarItem(
                                        selected = currentRoute == Routes.LIST,
                                        onClick = {
                                            navController.navigate(Routes.LIST) {
                                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        },
                                        icon = { Icon(Icons.Default.List, contentDescription = null) },
                                        label = { Text("すべて") }
                                    )
                                }
                            }
                        }
                    ) { padding ->
                        NavHost(
                            navController = navController,
                            startDestination = if (authViewModel.isLoggedIn.value) Routes.FOCUS else Routes.LOGIN,
                            modifier = Modifier.padding(padding)
                        ) {
                            composable(Routes.LOGIN) {
                                LoginScreen(
                                    authViewModel = authViewModel,
                                    onLoggedIn = { clearBackStackTo(Routes.FOCUS) },
                                    onGoToRegister = { navController.navigate(Routes.REGISTER) }
                                )
                            }
                            composable(Routes.REGISTER) {
                                RegisterScreen(
                                    authViewModel = authViewModel,
                                    onRegistered = { clearBackStackTo(Routes.FOCUS) },
                                    onGoToLogin = { navController.popBackStack() }
                                )
                            }
                            composable(Routes.FOCUS) {
                                FocusScreen(
                                    onAddTask = { navController.navigate(Routes.ADD) },
                                    onEditTask = { taskId -> navController.navigate(Routes.edit(taskId)) },
                                    onLogout = {
                                        authViewModel.logout()
                                        clearBackStackTo(Routes.LOGIN)
                                    }
                                )
                            }
                            composable(Routes.LIST) {
                                TaskListScreen(
                                    onAddTask = { navController.navigate(Routes.ADD) },
                                    onEditTask = { taskId -> navController.navigate(Routes.edit(taskId)) },
                                    onLogout = {
                                        authViewModel.logout()
                                        clearBackStackTo(Routes.LOGIN)
                                    }
                                )
                            }
                            composable(Routes.ADD) {
                                AddEditTaskScreen(
                                    taskId = null,
                                    onDone = { navController.popBackStack() }
                                )
                            }
                            composable(
                                route = Routes.EDIT,
                                arguments = listOf(navArgument("taskId") { type = NavType.StringType })
                            ) { entry ->
                                val taskId = entry.arguments?.getString("taskId")
                                AddEditTaskScreen(
                                    taskId = taskId,
                                    onDone = { navController.popBackStack() }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingTaskId = intent.extractTaskId()
    }

    private fun Intent.extractTaskId(): String? = getStringExtra(EXTRA_OPEN_TASK_ID)

    companion object {
        const val EXTRA_OPEN_TASK_ID = "extra_open_task_id"
    }
}

@Composable
private fun RequestNotificationPermission() {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
