package com.example.taskmanager.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Event
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.taskmanager.calendar.openInCalendar
import com.example.taskmanager.data.Priority
import com.example.taskmanager.data.Task
import com.example.taskmanager.ui.TaskViewModel
import com.example.taskmanager.ui.components.dueDateFormatter
import kotlinx.coroutines.flow.flowOf
import java.util.Calendar
import java.util.TimeZone

private data class ReminderOption(val label: String, val minutes: Int?)

private val reminderOptions = listOf(
    ReminderOption("通知なし", null),
    ReminderOption("期限ちょうど", 0),
    ReminderOption("10分前", 10),
    ReminderOption("30分前", 30),
    ReminderOption("1時間前", 60),
    ReminderOption("前日", 1440)
)

private fun combineDateAndTime(dateMillisUtc: Long, hour: Int, minute: Int): Long {
    val utcCal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
    utcCal.timeInMillis = dateMillisUtc
    val localCal = Calendar.getInstance()
    localCal.clear()
    localCal.set(utcCal.get(Calendar.YEAR), utcCal.get(Calendar.MONTH), utcCal.get(Calendar.DAY_OF_MONTH), hour, minute, 0)
    return localCal.timeInMillis
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditTaskScreen(
    taskId: Long?,
    viewModel: TaskViewModel = viewModel(),
    onDone: () -> Unit
) {
    var loadedTask by remember { mutableStateOf<Task?>(null) }
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var priority by remember { mutableStateOf(Priority.MEDIUM) }
    var dueDate by remember { mutableStateOf<Long?>(null) }
    var reminderOffsetMinutes by remember { mutableStateOf<Int?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var subtaskText by remember { mutableStateOf("") }

    val context = LocalContext.current

    val subtasksFlow = remember(taskId) {
        if (taskId != null) viewModel.subtasksFlow(taskId) else flowOf(emptyList())
    }
    val subtasks by subtasksFlow.collectAsState(initial = emptyList())

    LaunchedEffect(taskId) {
        if (taskId != null) {
            val task = viewModel.getTaskByIdBlocking(taskId)
            if (task != null) {
                loadedTask = task
                title = task.title
                description = task.description
                priority = task.priority
                dueDate = task.dueDate
                reminderOffsetMinutes = task.reminderOffsetMinutes
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (taskId == null) "新しいタスク" else "タスクを編集") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "戻る")
                    }
                },
                actions = {
                    if (taskId != null) {
                        IconButton(onClick = {
                            loadedTask?.let { viewModel.deleteTask(it) }
                            onDone()
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = "削除")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
                .fillMaxWidth()
        ) {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("タイトル") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("メモ") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                keyboardOptions = KeyboardOptions.Default
            )

            Text(
                text = "優先度",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Priority.values().forEach { p ->
                    FilterChip(
                        selected = priority == p,
                        onClick = { priority = p },
                        label = { Text(priorityLabel(p)) }
                    )
                }
            }

            Text(
                text = "期限日時",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { showDatePicker = true }) {
                    Text(dueDate?.let { dueDateFormatter.format(it) } ?: "日時を設定")
                }
                if (dueDate != null) {
                    TextButton(onClick = {
                        dueDate = null
                        reminderOffsetMinutes = null
                    }) {
                        Text("クリア")
                    }
                }
            }

            if (dueDate != null) {
                Text(
                    text = "リマインダー通知",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                ) {
                    reminderOptions.forEach { option ->
                        FilterChip(
                            selected = reminderOffsetMinutes == option.minutes,
                            onClick = { reminderOffsetMinutes = option.minutes },
                            label = { Text(option.label) }
                        )
                    }
                }

                OutlinedButton(
                    onClick = {
                        openInCalendar(context, title, description, dueDate!!)
                    },
                    modifier = Modifier.padding(top = 12.dp)
                ) {
                    Icon(Icons.Default.Event, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Googleカレンダーに追加")
                }
            }

            if (taskId != null) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 20.dp))
                val completedCount = subtasks.count { it.isCompleted }
                Text(
                    text = if (subtasks.isEmpty()) "サブタスクで作業を分解する" else "サブタスク $completedCount/${subtasks.size}",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                subtasks.forEach { subtask ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = subtask.isCompleted,
                            onCheckedChange = { checked -> viewModel.toggleSubtaskCompleted(subtask, checked) }
                        )
                        Text(
                            text = subtask.title,
                            modifier = Modifier.weight(1f),
                            textDecoration = if (subtask.isCompleted) TextDecoration.LineThrough else TextDecoration.None
                        )
                        IconButton(onClick = { viewModel.deleteSubtask(subtask) }) {
                            Icon(Icons.Default.Close, contentDescription = "サブタスクを削除")
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = subtaskText,
                        onValueChange = { subtaskText = it },
                        placeholder = { Text("小さいステップを追加…") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    TextButton(onClick = {
                        viewModel.addSubtask(taskId, subtaskText)
                        subtaskText = ""
                    }) {
                        Text("追加")
                    }
                }
            }

            Button(
                onClick = {
                    if (taskId == null) {
                        viewModel.addTask(title, description, priority, dueDate, reminderOffsetMinutes)
                    } else {
                        loadedTask?.let {
                            viewModel.updateTask(it, title, description, priority, dueDate, reminderOffsetMinutes)
                        }
                    }
                    onDone()
                },
                enabled = title.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp, bottom = 8.dp)
            ) {
                Text("保存")
            }
        }
    }

    if (showDatePicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = dueDate)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val selected = state.selectedDateMillis
                    if (selected != null) {
                        dueDate = combineDateAndTime(selected, 9, 0)
                        showDatePicker = false
                        showTimePicker = true
                    } else {
                        showDatePicker = false
                    }
                }) { Text("次へ(時刻)") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("キャンセル") }
            }
        ) {
            DatePicker(state = state)
        }
    }

    if (showTimePicker) {
        val initialCal = Calendar.getInstance().apply { dueDate?.let { timeInMillis = it } }
        val timeState = rememberTimePickerState(
            initialHour = initialCal.get(Calendar.HOUR_OF_DAY),
            initialMinute = initialCal.get(Calendar.MINUTE),
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val current = dueDate ?: System.currentTimeMillis()
                    val cal = Calendar.getInstance().apply { timeInMillis = current }
                    cal.set(Calendar.HOUR_OF_DAY, timeState.hour)
                    cal.set(Calendar.MINUTE, timeState.minute)
                    cal.set(Calendar.SECOND, 0)
                    dueDate = cal.timeInMillis
                    showTimePicker = false
                }) { Text("決定") }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("キャンセル") }
            },
            text = {
                TimePicker(state = timeState)
            }
        )
    }
}

private fun priorityLabel(priority: Priority): String = when (priority) {
    Priority.LOW -> "低"
    Priority.MEDIUM -> "中"
    Priority.HIGH -> "高"
}
