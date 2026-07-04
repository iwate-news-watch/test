package com.example.taskmanager.data

import android.content.Context
import com.example.taskmanager.data.remote.ApiClient
import com.example.taskmanager.data.remote.CompletedRequest
import com.example.taskmanager.data.remote.CreateTaskRequest
import com.example.taskmanager.data.remote.SnoozeRequest
import com.example.taskmanager.data.remote.UpdateTaskRequest

class TaskRepository(context: Context) {
    private val taskApi = ApiClient.taskApi(context)

    suspend fun listTasks(): List<Task> = taskApi.listTasks()

    suspend fun getTask(id: String): Task = taskApi.getTask(id)

    suspend fun listSubtasks(parentId: String): List<Task> = taskApi.listSubtasks(parentId)

    suspend fun createTask(
        title: String,
        description: String = "",
        priority: Priority = Priority.MEDIUM,
        tag: String? = null,
        dueDate: Long? = null,
        reminderOffsetsMinutes: List<Int> = emptyList(),
        isRecurring: Boolean = false,
        parentTaskId: String? = null
    ): Task = taskApi.createTask(
        CreateTaskRequest(
            title = title,
            description = description,
            priority = priority,
            tag = tag,
            dueDate = dueDate,
            reminderOffsetsMinutes = reminderOffsetsMinutes,
            isRecurring = isRecurring,
            parentTaskId = parentTaskId
        )
    )

    suspend fun updateTask(
        id: String,
        title: String,
        description: String,
        priority: Priority,
        tag: String?,
        dueDate: Long?,
        reminderOffsetsMinutes: List<Int>,
        isRecurring: Boolean
    ): Task = taskApi.updateTask(id, UpdateTaskRequest(title, description, priority, tag, dueDate, reminderOffsetsMinutes, isRecurring))

    suspend fun setCompleted(id: String, completed: Boolean): Task =
        taskApi.setCompleted(id, CompletedRequest(completed))

    suspend fun snooze(id: String, snoozedUntil: Long?): Task =
        taskApi.snooze(id, SnoozeRequest(snoozedUntil))

    suspend fun deleteTask(id: String) = taskApi.deleteTask(id)
}
