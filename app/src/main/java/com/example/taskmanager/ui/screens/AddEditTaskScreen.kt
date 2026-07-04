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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.text.SimpleDateFormat
import java.util.Locale

private data class ReminderOption(val label: String, val minutes: Int)

private val reminderOptions = listOf(
    ReminderOption("期限ちょうど", 0),
    ReminderOption("10分前", 10),
    ReminderOption("30分前", 30),
    ReminderOption("1時間前", 60),
    ReminderOption("前日", 1440)
)

private enum class TimeOfDay(val hour: Int, val label: String) {
    MORNING(9, "朝"),
    NOON(12, "昼"),
    EVENING(18, "夕方"),
    NIGHT(21, "夜")
}

private data class DatePreset(val label: String, val resolve: () -> LocalDate)

private val datePresets = listOf(
    DatePreset("今日") { LocalDate.now() },
    DatePreset("明日") { LocalDate.now().plusDays(1) },
    DatePreset("今週末") { nextSaturday(LocalDate.now()) },
    DatePreset("来週") { LocalDate.now().plusDays(7) }
)

private fun nextSaturday(base: LocalDate): LocalDate {
    val diff = (6 - base.dayOfWeek.value + 7) % 7
    return base.plusDays(diff.toLong())
}

private fun localDateToMillis(date: LocalDate, hour: Int): Long =
    date.atTime(hour, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

private fun millisToLocalDate(millis: Long): LocalDate =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()

private fun closestTimeOfDay(millis: Long): TimeOfDay {
    val hour = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).hour
    return TimeOfDay.values().minByOrNull { kotlin.math.abs(it.hour - hour) } ?: TimeOfDay.MORNING
}

private val dateOnlyFormatter = SimpleDateFormat("M月d日", Locale.JAPAN)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditTaskScreen(
    taskId: String?,
    viewModel: TaskViewModel = viewModel(),
    onDone: () -> Unit
) {
    var loadedTask by remember { mutableStateOf<Task?>(null) }
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var priority by remember { mutableStateOf(Priority.MEDIUM) }
    var tag by remember { mutableStateOf("") }
    var dueDate by remember { mutableStateOf<Long?>(null) }
    var timeOfDay by remember { mutableStateOf(TimeOfDay.MORNING) }
    var reminderOffsetsMinutes by remember { mutableStateOf<List<Int>>(emptyList()) }
    var isRecurring by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var subtaskText by remember { mutableStateOf("") }
    var subtaskDueDate by remember { mutableStateOf<Long?>(null) }
    var subtasks by remember { mutableStateOf<List<Task>>(emptyList()) }
    var saveError by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    fun applyDate(date: LocalDate) {
        dueDate = localDateToMillis(date, timeOfDay.hour)
    }

    fun applyTimeOfDay(t: TimeOfDay) {
        timeOfDay = t
        dueDate?.let { dueDate = localDateToMillis(millisToLocalDate(it), t.hour) }
    }

    fun toggleRecurring() {
        isRecurring = !isRecurring
        if (isRecurring && dueDate == null) {
            applyDate(LocalDate.now())
        }
    }

    suspend fun refreshSubtasks() {
        if (taskId != null) subtasks = viewModel.listSubtasks(taskId)
    }

    LaunchedEffect(taskId) {
        if (taskId != null) {
            val task = viewModel.getTaskByIdBlocking(taskId)
            if (task != null) {
                loadedTask = task
                title = task.title
                description = task.description
                priority = task.priority
                tag = task.tag ?: ""
                dueDate = task.dueDate
                task.dueDate?.let { timeOfDay = closestTimeOfDay(it) }
                reminderOffsetsMinutes = task.reminderOffsetsMinutes
                isRecurring = task.isRecurring
            }
            refreshSubtasks()
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
                text = "タグ",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
            )
            OutlinedTextField(
                value = tag,
                onValueChange = { tag = it },
                placeholder = { Text("仕事、プライベートなど(空欄でもOK)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 8.dp)
            ) {
                listOf("仕事", "プライベート").forEach { suggestion ->
                    FilterChip(
                        selected = tag == suggestion,
                        onClick = { tag = suggestion },
                        label = { Text(suggestion) }
                    )
                }
            }

            Text(
                text = "期限日",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                datePresets.forEach { preset ->
                    val presetDate = preset.resolve()
                    FilterChip(
                        selected = dueDate?.let { millisToLocalDate(it) == presetDate } ?: false,
                        onClick = { applyDate(presetDate) },
                        label = { Text(preset.label) }
                    )
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 8.dp)
            ) {
                OutlinedButton(onClick = { showDatePicker = true }) {
                    Text(dueDate?.let { dateOnlyFormatter.format(it) } ?: "日付を選ぶ")
                }
                if (dueDate != null) {
                    TextButton(onClick = {
                        dueDate = null
                        reminderOffsetsMinutes = emptyList()
                        isRecurring = false
                    }) {
                        Text("クリア")
                    }
                }
            }

            Text(
                text = "時間帯",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TimeOfDay.values().forEach { t ->
                    FilterChip(
                        selected = timeOfDay == t,
                        onClick = { applyTimeOfDay(t) },
                        label = { Text(t.label) }
                    )
                }
            }

            Text(
                text = "繰り返し",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = isRecurring,
                    onClick = { toggleRecurring() },
                    label = { Text("🔁 毎日繰り返す") }
                )
            }
            if (isRecurring) {
                Text(
                    text = "完了すると、翌日の分が自動で新しいタスクとして作られます。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            if (dueDate != null) {
                Text(
                    text = "リマインダー通知(複数選択可)",
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
                            selected = reminderOffsetsMinutes.contains(option.minutes),
                            onClick = {
                                reminderOffsetsMinutes = if (reminderOffsetsMinutes.contains(option.minutes)) {
                                    reminderOffsetsMinutes - option.minutes
                                } else {
                                    (reminderOffsetsMinutes + option.minutes).sorted()
                                }
                            },
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
                            onCheckedChange = { checked ->
                                scope.launch {
                                    viewModel.toggleSubtaskCompleted(subtask, checked)
                                    refreshSubtasks()
                                }
                            }
                        )
                        Text(
                            text = subtask.title,
                            modifier = Modifier.weight(1f),
                            textDecoration = if (subtask.isCompleted) TextDecoration.LineThrough else TextDecoration.None
                        )
                        subtask.dueDate?.let {
                            Text(
                                text = "期限: ${dateOnlyFormatter.format(it)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(end = 4.dp)
                            )
                        }
                        IconButton(onClick = {
                            scope.launch {
                                viewModel.deleteSubtask(subtask)
                                refreshSubtasks()
                            }
                        }) {
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
                        scope.launch {
                            viewModel.addSubtask(taskId, subtaskText, subtaskDueDate)
                            subtaskText = ""
                            subtaskDueDate = null
                            refreshSubtasks()
                        }
                    }) {
                        Text("追加")
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    datePresets.take(2).forEach { preset ->
                        val presetDate = preset.resolve()
                        FilterChip(
                            selected = subtaskDueDate?.let { millisToLocalDate(it) == presetDate } ?: false,
                            onClick = { subtaskDueDate = localDateToMillis(presetDate, TimeOfDay.MORNING.hour) },
                            label = { Text(preset.label) }
                        )
                    }
                    if (subtaskDueDate != null) {
                        TextButton(onClick = { subtaskDueDate = null }) {
                            Text("クリア")
                        }
                    }
                }
            }

            if (saveError != null) {
                Text(
                    text = saveError!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Button(
                onClick = {
                    scope.launch {
                        saving = true
                        saveError = null
                        val trimmedTag = tag.trim().ifBlank { null }
                        val success = if (taskId == null) {
                            viewModel.addTask(title, description, priority, trimmedTag, dueDate, reminderOffsetsMinutes, isRecurring)
                        } else {
                            loadedTask?.let {
                                viewModel.updateTask(it, title, description, priority, trimmedTag, dueDate, reminderOffsetsMinutes, isRecurring)
                            } ?: false
                        }
                        saving = false
                        if (success) {
                            onDone()
                        } else {
                            saveError = "保存に失敗しました。通信状況を確認してもう一度お試しください。"
                        }
                    }
                },
                enabled = title.isNotBlank() && !saving,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp, bottom = 8.dp)
            ) {
                Text(if (saving) "保存中…" else "保存")
            }
        }
    }

    if (showDatePicker) {
        val initialUtcMillis = dueDate?.let {
            millisToLocalDate(it).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        }
        val state = rememberDatePickerState(initialSelectedDateMillis = initialUtcMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val selected = state.selectedDateMillis
                    if (selected != null) {
                        val pickedDate = Instant.ofEpochMilli(selected).atZone(ZoneOffset.UTC).toLocalDate()
                        applyDate(pickedDate)
                    }
                    showDatePicker = false
                }) { Text("決定") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("キャンセル") }
            }
        ) {
            DatePicker(state = state)
        }
    }
}

private fun priorityLabel(priority: Priority): String = when (priority) {
    Priority.LOW -> "低"
    Priority.MEDIUM -> "中"
    Priority.HIGH -> "高"
}
