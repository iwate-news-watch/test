package com.example.taskmanager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.taskmanager.ui.screens.AddEditTaskScreen
import com.example.taskmanager.ui.screens.TaskListScreen
import com.example.taskmanager.ui.theme.TaskManagerTheme

private object Routes {
    const val LIST = "list"
    const val ADD = "add"
    const val EDIT = "edit/{taskId}"
    fun edit(taskId: Long) = "edit/$taskId"
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TaskManagerTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val navController = rememberNavController()
                    NavHost(navController = navController, startDestination = Routes.LIST) {
                        composable(Routes.LIST) {
                            TaskListScreen(
                                onAddTask = { navController.navigate(Routes.ADD) },
                                onEditTask = { taskId -> navController.navigate(Routes.edit(taskId)) }
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
                            arguments = listOf(navArgument("taskId") { type = NavType.LongType })
                        ) { backStackEntry ->
                            val taskId = backStackEntry.arguments?.getLong("taskId")
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
