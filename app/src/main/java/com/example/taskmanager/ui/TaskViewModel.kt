package com.example.taskmanager.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.taskmanager.data.Priority
import com.example.taskmanager.data.Task
import com.example.taskmanager.data.TaskRepository
import com.example.taskmanager.data.isOverdue
import com.example.taskmanager.notifications.ReminderScheduler
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class TaskFilter { ALL, ACTIVE, COMPLETED }

private const val POLL_INTERVAL_MS = 10_000L

fun sortForFocus(tasks: List<Task>, limit: Int): List<Task> =
    tasks
        .filter { !it.isCompleted }
        .sortedWith(
            compareBy<Task> { !it.isOverdue() }
                .thenBy { it.dueDate == null }
                .thenBy { it.dueDate }
                .thenByDescending { it.priority.ordinal }
        )
        .take(limit)

data class PriorityGroup(val priority: Priority, val tasks: List<Task>)

fun groupByPriority(tasks: List<Task>): List<PriorityGroup> =
    listOf(Priority.HIGH, Priority.MEDIUM, Priority.LOW)
        .map { PriorityGroup(it, tasks.filter { t -> t.priority == it }) }
        .filter { it.tasks.isNotEmpty() }

fun uniqueTags(tasks: List<Task>): List<String> =
    tasks.mapNotNull { it.tag }.distinct().sorted()

private val encouragements = listOf(
    "やった、1つ終わった!",
    "その調子!",
    "えらい、進んでる!",
    "ナイス完了!",
    "着実に前に進んでるよ"
)

class TaskViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = TaskRepository(application)

    private val _filter = MutableStateFlow(TaskFilter.ALL)
    val filter: StateFlow<TaskFilter> = _filter

    private val _tagFilter = MutableStateFlow<String?>(null)
    val tagFilter: StateFlow<String?> = _tagFilter

    private val _tasks = MutableStateFlow<List<Task>>(emptyList())

    private val _encouragement = Channel<String>(Channel.BUFFERED)
    val encouragement: Flow<String> = _encouragement.receiveAsFlow()

    private val _errors = Channel<String>(Channel.BUFFERED)
    val errors: Flow<String> = _errors.receiveAsFlow()

    val availableTags: StateFlow<List<String>> = _tasks
        .map { uniqueTags(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val uiState: StateFlow<List<Task>> = combine(
        _tasks,
        _filter,
        _tagFilter
    ) { tasks, filter, tagFilter ->
        val filtered = when (filter) {
            TaskFilter.ALL -> tasks
            TaskFilter.ACTIVE -> tasks.filter { !it.isCompleted }
            TaskFilter.COMPLETED -> tasks.filter { it.isCompleted }
        }.let { list -> if (tagFilter != null) list.filter { it.tag == tagFilter } else list }
        filtered.sortedWith(
            compareBy<Task> { it.isCompleted }
                .thenBy { it.dueDate == null }
                .thenBy { it.dueDate }
                .thenByDescending { it.priority.ordinal }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Focus mode: only the handful of tasks that matter right now, so opening
    // the app never dumps the whole backlog on you at once.
    val focusTasks: StateFlow<List<Task>> = _tasks

    init {
        viewModelScope.launch {
            while (isActive) {
                refresh()
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    suspend fun refresh() {
        try {
            _tasks.value = repository.listTasks()
        } catch (e: Exception) {
            _errors.send(e.message ?: "タスクを読み込めませんでした")
        }
    }

    fun setFilter(filter: TaskFilter) {
        _filter.value = filter
    }

    fun setTagFilter(tag: String?) {
        _tagFilter.value = tag
    }

    suspend fun listSubtasks(parentId: String): List<Task> = try {
        repository.listSubtasks(parentId)
    } catch (e: Exception) {
        _errors.send(e.message ?: "サブタスクを読み込めませんでした")
        emptyList()
    }

    suspend fun getTaskByIdBlocking(id: String): Task? = try {
        repository.getTask(id)
    } catch (e: Exception) {
        _errors.send(e.message ?: "タスクを読み込めませんでした")
        null
    }

    fun quickAddTask(title: String) {
        if (title.isBlank()) return
        viewModelScope.launch {
            runCatching { repository.createTask(title = title.trim()) }
                .onFailure { _errors.send(it.message ?: "追加に失敗しました") }
            refresh()
        }
    }

    /** Returns true on success. Callers should only navigate away once this completes. */
    suspend fun addTask(
        title: String,
        description: String,
        priority: Priority,
        tag: String?,
        dueDate: Long?,
        reminderOffsetsMinutes: List<Int>
    ): Boolean {
        if (title.isBlank()) return false
        val result = runCatching {
            val created = repository.createTask(
                title = title.trim(),
                description = description.trim(),
                priority = priority,
                tag = tag,
                dueDate = dueDate,
                reminderOffsetsMinutes = reminderOffsetsMinutes
            )
            ReminderScheduler.schedule(getApplication<Application>(), created)
        }
        result.onFailure { _errors.send(it.message ?: "追加に失敗しました") }
        refresh()
        return result.isSuccess
    }

    /** Returns true on success. Callers should only navigate away once this completes. */
    suspend fun updateTask(
        task: Task,
        title: String,
        description: String,
        priority: Priority,
        tag: String?,
        dueDate: Long?,
        reminderOffsetsMinutes: List<Int>
    ): Boolean {
        if (title.isBlank()) return false
        val result = runCatching {
            val updated = repository.updateTask(
                id = task.id,
                title = title.trim(),
                description = description.trim(),
                priority = priority,
                tag = tag,
                dueDate = dueDate,
                reminderOffsetsMinutes = reminderOffsetsMinutes
            )
            ReminderScheduler.schedule(getApplication<Application>(), updated)
        }
        result.onFailure { _errors.send(it.message ?: "更新に失敗しました") }
        refresh()
        return result.isSuccess
    }

    suspend fun addSubtask(parentId: String, title: String) {
        if (title.isBlank()) return
        try {
            repository.createTask(title = title.trim(), parentTaskId = parentId)
        } catch (e: Exception) {
            _errors.send(e.message ?: "追加に失敗しました")
        }
    }

    suspend fun toggleSubtaskCompleted(subtask: Task, completed: Boolean) {
        try {
            repository.setCompleted(subtask.id, completed)
        } catch (e: Exception) {
            _errors.send(e.message ?: "更新に失敗しました")
        }
    }

    suspend fun deleteSubtask(subtask: Task) {
        try {
            repository.deleteTask(subtask.id)
        } catch (e: Exception) {
            _errors.send(e.message ?: "削除に失敗しました")
        }
    }

    fun toggleCompleted(task: Task, completed: Boolean) {
        viewModelScope.launch {
            runCatching { repository.setCompleted(task.id, completed) }
                .onSuccess {
                    if (completed) {
                        ReminderScheduler.cancelAll(getApplication<Application>(), task.id)
                        _encouragement.send(encouragements.random())
                    }
                }
                .onFailure { _errors.send(it.message ?: "更新に失敗しました") }
            refresh()
        }
    }

    fun deleteTask(task: Task) {
        viewModelScope.launch {
            runCatching {
                ReminderScheduler.cancelAll(getApplication<Application>(), task.id)
                repository.deleteTask(task.id)
            }.onFailure { _errors.send(it.message ?: "削除に失敗しました") }
            refresh()
        }
    }
}
