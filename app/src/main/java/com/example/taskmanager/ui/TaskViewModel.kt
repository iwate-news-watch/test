package com.example.taskmanager.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.taskmanager.data.Priority
import com.example.taskmanager.data.Task
import com.example.taskmanager.data.TaskDatabase
import com.example.taskmanager.data.TaskRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class TaskFilter { ALL, ACTIVE, COMPLETED }

class TaskViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: TaskRepository = TaskRepository(
        TaskDatabase.getInstance(application).taskDao()
    )

    private val _filter = MutableStateFlow(TaskFilter.ALL)
    val filter: StateFlow<TaskFilter> = _filter

    val uiState: StateFlow<List<Task>> = combine(
        repository.allTasks,
        _filter
    ) { tasks, filter ->
        val filtered = when (filter) {
            TaskFilter.ALL -> tasks
            TaskFilter.ACTIVE -> tasks.filter { !it.isCompleted }
            TaskFilter.COMPLETED -> tasks.filter { it.isCompleted }
        }
        filtered.sortedWith(
            compareBy<Task> { it.isCompleted }
                .thenBy { it.dueDate == null }
                .thenBy { it.dueDate }
                .thenByDescending { it.priority.ordinal }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setFilter(filter: TaskFilter) {
        _filter.value = filter
    }

    suspend fun getTaskByIdBlocking(id: Long): Task? = repository.getTaskById(id)

    fun addTask(title: String, description: String, priority: Priority, dueDate: Long?) {
        if (title.isBlank()) return
        viewModelScope.launch {
            repository.upsertTask(
                Task(title = title.trim(), description = description.trim(), priority = priority, dueDate = dueDate)
            )
        }
    }

    fun updateTask(task: Task, title: String, description: String, priority: Priority, dueDate: Long?) {
        if (title.isBlank()) return
        viewModelScope.launch {
            repository.updateTask(
                task.copy(title = title.trim(), description = description.trim(), priority = priority, dueDate = dueDate)
            )
        }
    }

    fun toggleCompleted(task: Task, completed: Boolean) {
        viewModelScope.launch {
            repository.setCompleted(task, completed)
        }
    }

    fun deleteTask(task: Task) {
        viewModelScope.launch {
            repository.deleteTask(task)
        }
    }
}
