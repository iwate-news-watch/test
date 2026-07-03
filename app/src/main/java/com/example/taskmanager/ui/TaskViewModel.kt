package com.example.taskmanager.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.taskmanager.data.Priority
import com.example.taskmanager.data.Task
import com.example.taskmanager.data.TaskDatabase
import com.example.taskmanager.data.TaskRepository
import com.example.taskmanager.data.isOverdue
import com.example.taskmanager.notifications.ReminderScheduler
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class TaskFilter { ALL, ACTIVE, COMPLETED }

data class TaskUiModel(
    val task: Task,
    val subtaskTotal: Int = 0,
    val subtaskCompleted: Int = 0
)

fun sortForFocus(models: List<TaskUiModel>, limit: Int): List<TaskUiModel> =
    models
        .filter { !it.task.isCompleted }
        .sortedWith(
            compareBy<TaskUiModel> { !it.task.isOverdue() }
                .thenBy { it.task.dueDate == null }
                .thenBy { it.task.dueDate }
                .thenByDescending { it.task.priority.ordinal }
        )
        .take(limit)

private val encouragements = listOf(
    "やった、1つ終わった!",
    "その調子!",
    "えらい、進んでる!",
    "ナイス完了!",
    "着実に前に進んでるよ"
)

class TaskViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: TaskRepository = TaskRepository(
        TaskDatabase.getInstance(application).taskDao()
    )

    private val _filter = MutableStateFlow(TaskFilter.ALL)
    val filter: StateFlow<TaskFilter> = _filter

    private val _encouragement = Channel<String>(Channel.BUFFERED)
    val encouragement: Flow<String> = _encouragement.receiveAsFlow()

    private val taskModels: Flow<List<TaskUiModel>> = combine(
        repository.topLevelTasks,
        repository.subtaskCounts
    ) { tasks, counts ->
        val countsByParent = counts.associateBy { it.parentTaskId }
        tasks.map { task ->
            val count = countsByParent[task.id]
            TaskUiModel(task, count?.total ?: 0, count?.completedCount ?: 0)
        }
    }

    val uiState: StateFlow<List<TaskUiModel>> = combine(
        taskModels,
        _filter
    ) { models, filter ->
        val filtered = when (filter) {
            TaskFilter.ALL -> models
            TaskFilter.ACTIVE -> models.filter { !it.task.isCompleted }
            TaskFilter.COMPLETED -> models.filter { it.task.isCompleted }
        }
        filtered.sortedWith(
            compareBy<TaskUiModel> { it.task.isCompleted }
                .thenBy { it.task.dueDate == null }
                .thenBy { it.task.dueDate }
                .thenByDescending { it.task.priority.ordinal }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Focus mode: only the handful of tasks that matter right now, so opening
    // the app never dumps the whole backlog on you at once.
    val focusTasks: StateFlow<List<TaskUiModel>> =
        taskModels.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setFilter(filter: TaskFilter) {
        _filter.value = filter
    }

    fun subtasksFlow(parentId: Long): Flow<List<Task>> = repository.subtasksOf(parentId)

    suspend fun getTaskByIdBlocking(id: Long): Task? = repository.getTaskById(id)

    fun quickAddTask(title: String) {
        if (title.isBlank()) return
        viewModelScope.launch {
            repository.upsertTask(Task(title = title.trim()))
        }
    }

    fun addTask(
        title: String,
        description: String,
        priority: Priority,
        dueDate: Long?,
        reminderOffsetMinutes: Int?
    ) {
        if (title.isBlank()) return
        viewModelScope.launch {
            val id = repository.upsertTask(
                Task(
                    title = title.trim(),
                    description = description.trim(),
                    priority = priority,
                    dueDate = dueDate,
                    reminderOffsetMinutes = reminderOffsetMinutes
                )
            )
            repository.getTaskById(id)?.let { ReminderScheduler.schedule(getApplication<Application>(), it) }
        }
    }

    fun updateTask(
        task: Task,
        title: String,
        description: String,
        priority: Priority,
        dueDate: Long?,
        reminderOffsetMinutes: Int?
    ) {
        if (title.isBlank()) return
        viewModelScope.launch {
            val updated = task.copy(
                title = title.trim(),
                description = description.trim(),
                priority = priority,
                dueDate = dueDate,
                reminderOffsetMinutes = reminderOffsetMinutes,
                snoozedUntil = null
            )
            repository.updateTask(updated)
            ReminderScheduler.schedule(getApplication<Application>(), updated)
        }
    }

    fun addSubtask(parentId: Long, title: String) {
        if (title.isBlank()) return
        viewModelScope.launch {
            repository.upsertTask(Task(title = title.trim(), parentTaskId = parentId))
        }
    }

    fun toggleSubtaskCompleted(subtask: Task, completed: Boolean) {
        viewModelScope.launch {
            repository.setCompleted(subtask, completed)
        }
    }

    fun deleteSubtask(subtask: Task) {
        viewModelScope.launch {
            repository.deleteTask(subtask)
        }
    }

    fun toggleCompleted(task: Task, completed: Boolean) {
        viewModelScope.launch {
            repository.setCompleted(task, completed)
            if (completed) {
                ReminderScheduler.cancel(getApplication<Application>(), task.id)
                _encouragement.send(encouragements.random())
            }
        }
    }

    fun deleteTask(task: Task) {
        viewModelScope.launch {
            ReminderScheduler.cancel(getApplication<Application>(), task.id)
            repository.deleteTask(task)
        }
    }
}
