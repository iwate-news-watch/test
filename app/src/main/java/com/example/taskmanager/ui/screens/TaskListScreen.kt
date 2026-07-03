package com.example.taskmanager.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.taskmanager.ui.TaskFilter
import com.example.taskmanager.ui.TaskUiModel
import com.example.taskmanager.ui.TaskViewModel
import com.example.taskmanager.ui.components.TaskItem
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskListScreen(
    viewModel: TaskViewModel = viewModel(),
    onAddTask: () -> Unit,
    onEditTask: (Long) -> Unit
) {
    val models by viewModel.uiState.collectAsState()
    val filter by viewModel.filter.collectAsState()
    var quickAddText by remember { mutableStateOf("") }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        viewModel.encouragement.collect { message ->
            scope.launch { snackbarHostState.showSnackbar(message) }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("すべてのタスク") })
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddTask) {
                Icon(Icons.Default.Add, contentDescription = "詳細を入力して追加")
            }
        },
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                Snackbar(snackbarData = data)
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = quickAddText,
                    onValueChange = { quickAddText = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("思いついたらすぐ入力…") },
                    singleLine = true
                )
                IconButton(onClick = {
                    viewModel.quickAddTask(quickAddText)
                    quickAddText = ""
                }) {
                    Icon(Icons.Default.Add, contentDescription = "クイック追加")
                }
            }

            val filters = TaskFilter.values()
            TabRow(selectedTabIndex = filters.indexOf(filter)) {
                filters.forEach { f ->
                    Tab(
                        selected = f == filter,
                        onClick = { viewModel.setFilter(f) },
                        text = { Text(f.label()) }
                    )
                }
            }

            if (models.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "タスクはまだありません。上の欄か + から追加できます。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp)
                ) {
                    items(models, key = { it.task.id }) { model ->
                        SwipeableTaskRow(
                            model = model,
                            onToggleCompleted = { checked -> viewModel.toggleCompleted(model.task, checked) },
                            onClick = { onEditTask(model.task.id) },
                            onDelete = { viewModel.deleteTask(model.task) }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableTaskRow(
    model: TaskUiModel,
    onToggleCompleted: (Boolean) -> Unit,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart || value == SwipeToDismissBoxValue.StartToEnd) {
                onDelete()
                true
            } else {
                false
            }
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = { },
        modifier = Modifier.padding(vertical = 4.dp)
    ) {
        TaskItem(
            task = model.task,
            subtaskTotal = model.subtaskTotal,
            subtaskCompleted = model.subtaskCompleted,
            onToggleCompleted = onToggleCompleted,
            onClick = onClick
        )
    }
}

private fun TaskFilter.label(): String = when (this) {
    TaskFilter.ALL -> "すべて"
    TaskFilter.ACTIVE -> "未完了"
    TaskFilter.COMPLETED -> "完了済み"
}
